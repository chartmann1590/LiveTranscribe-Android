package com.charles.livecaptionn.billing

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.data.PremiumLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Stripe-backed [PremiumRepository] for the github flavor. Talks to the
 * Cloudflare Worker (cloudflare-worker/src/worker.js) and opens Stripe
 * Checkout / the Customer Portal login page via Chrome Custom Tabs. No
 * Stripe SDK, keys, or card UI is ever compiled into this app.
 *
 * Trust model matches the Worker: a just-completed Checkout's `sessionId`
 * strongly authenticates entitlement issuance; a bare email (restore-on-new-
 * device) is rate-limited by the Worker and is not itself proof of ownership.
 */
class StripeBillingRepository(
    private val localStore: PremiumLocalStore
) : PremiumRepository {

    override val state: Flow<PremiumState> = localStore.stateFlow
    override val supportsEmailRestore: Boolean = true

    override suspend fun refresh(sessionId: String?): Result<PremiumState> {
        val api = WorkerClient.api ?: return Result.failure(IllegalStateException("Premium server is not configured"))
        val cachedEmail = readCachedEmail()
        if (sessionId == null && cachedEmail == null) {
            // Nothing to revalidate against yet; not an error, just no entitlement.
            return Result.success(PremiumState.EMPTY)
        }
        return runCatching {
            if (sessionId != null) {
                fetchAndCacheEntitlement(api, sessionId = sessionId, email = null)
            } else {
                fetchAndCacheEntitlement(api, sessionId = null, email = cachedEmail)
            }
        }.recoverCatching { error ->
            applyOfflineGracePeriod(error)
        }
    }

    override suspend fun purchase(activity: Activity, product: PremiumProduct, email: String?): PurchaseFlowResult {
        val api = WorkerClient.api ?: return PurchaseFlowResult.Failed("Premium server is not configured")
        val normalizedEmail = email?.trim().orEmpty()
        if (normalizedEmail.isBlank()) {
            return PurchaseFlowResult.Failed("Enter your email above first")
        }
        val productKey = product.v2Key()
        return try {
            // Shared billing v2 catalog "livecaptionn": Pro monthly + yearly carry a 14-day
            // free trial server-side (one per email per plan); lifetime is a one-time payment.
            val response = api.checkoutV2(V2CheckoutRequest(app = V2_APP, product = productKey, email = normalizedEmail))
            val checkoutUrl = response.body()?.checkoutUrl
            if (!response.isSuccessful || checkoutUrl.isNullOrBlank()) {
                return PurchaseFlowResult.Failed(planLimitNotice(response) ?: "Could not start checkout (${response.code()})")
            }
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(checkoutUrl))
            PurchaseFlowResult.Started
        } catch (e: Exception) {
            PurchaseFlowResult.Failed(e.message ?: "Could not start checkout")
        }
    }

    override suspend fun restore(email: String?): Result<PremiumState> {
        val api = WorkerClient.api ?: return Result.failure(IllegalStateException("Premium server is not configured"))
        val normalizedEmail = email?.trim().orEmpty()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email is required"))
        }
        return runCatching {
            fetchAndCacheEntitlement(api, sessionId = null, email = normalizedEmail)
        }
    }

    override suspend fun openManageSubscription(activity: Activity): ManageAction {
        val api = WorkerClient.api ?: return ManageAction.Failed("Premium server is not configured")
        return try {
            val response = api.portal()
            val portalUrl = response.body()?.portalUrl
            if (!response.isSuccessful || portalUrl.isNullOrBlank()) {
                return ManageAction.Failed("Could not open subscription management (${response.code()})")
            }
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(portalUrl))
            ManageAction.Opened
        } catch (e: Exception) {
            ManageAction.Failed(e.message ?: "Could not open subscription management")
        }
    }

    /**
     * Looks the buyer up on billing v2 (catalog "livecaptionn": AD_FREE, PRO, PRO_YEARLY,
     * PRO_LIFETIME; the server expands PRO_* to PRO + AD_FREE). For email lookups the
     * legacy LiveCaptionN routes are asked too, so anyone who bought before the v2 move
     * keeps their purchase. Either answer is enough; both failing throws the v2 error.
     */
    private suspend fun fetchAndCacheEntitlement(api: WorkerApi, sessionId: String?, email: String?): PremiumState {
        val ownerKey = BuildConfig.OWNER_ACCESS_KEY.ifBlank { null }
        var firstError: String? = null
        val answers = mutableListOf<EntitlementResponse>()
        try {
            val r = api.entitlementV2(V2EntitlementRequest(app = V2_APP, sessionId = sessionId, email = if (sessionId == null) email else null, ownerKey = ownerKey))
            val b = r.body()
            if (r.isSuccessful && b != null) answers += b
            else firstError = planLimitNotice(r) ?: "Entitlement lookup failed (${r.code()})"
        } catch (e: Exception) {
            firstError = e.message ?: "Entitlement lookup failed"
        }
        if (sessionId == null && email != null) {
            try {
                val r = api.entitlement(EntitlementRequest(email = email, ownerKey = ownerKey))
                val b = r.body()
                if (r.isSuccessful && b != null) answers += b
                else if (firstError == null) firstError = planLimitNotice(r)
            } catch (_: Exception) { /* legacy lookup is best-effort */ }
        }
        if (answers.isEmpty()) throw IllegalStateException(firstError ?: "Entitlement lookup failed")
        val body = answers.first()
        val nowMillis = System.currentTimeMillis()
        val state = PremiumState(
            entitlements = answers.flatMap { it.entitlements }.mapNotNull { runCatching { Entitlement.valueOf(it) }.getOrNull() }.toSet(),
            sourceEmail = body.email,
            licenseToken = body.licenseToken,
            lastVerifiedAtMillis = nowMillis,
            gracePeriodExpiresAtMillis = nowMillis + PremiumState.GRACE_PERIOD_MILLIS
        )
        localStore.save(state)
        return state
    }

    /** The server's own sentence when it answers code "plan_limit" (its daily plan limit is hit); shown on Buy and Restore. */
    private fun planLimitNotice(response: retrofit2.Response<*>): String? = runCatching {
        val o = org.json.JSONObject(response.errorBody()?.string().orEmpty())
        if (o.optString("code") == "plan_limit") o.optString("error").takeIf { it.isNotBlank() } else null
    }.getOrNull()

    private fun PremiumProduct.v2Key(): String = when (this) {
        PremiumProduct.AD_FREE_MONTHLY -> "AD_FREE"
        PremiumProduct.PRO_MONTHLY -> "PRO"
        PremiumProduct.PRO_YEARLY -> "PRO_YEARLY"
        PremiumProduct.PRO_LIFETIME -> "PRO_LIFETIME"
    }

    private companion object {
        const val V2_APP = "livecaptionn"
    }

    private suspend fun readCachedEmail(): String? = localStore.stateFlow.first().sourceEmail

    /** Network/Worker failure: keep serving the cached state while inside its grace period. */
    private suspend fun applyOfflineGracePeriod(error: Throwable): PremiumState {
        val cached = localStore.stateFlow.first()
        val now = System.currentTimeMillis()
        if (cached.isWithinGracePeriod(now)) {
            return cached
        }
        throw error
    }
}