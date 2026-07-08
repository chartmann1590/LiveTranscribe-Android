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
            val request = if (sessionId != null) {
                EntitlementRequest(sessionId = sessionId)
            } else {
                EntitlementRequest(
                    email = cachedEmail,
                    ownerKey = BuildConfig.OWNER_ACCESS_KEY.ifBlank { null }
                )
            }
            fetchAndCacheEntitlement(api, request)
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
        val productKey = if (product == PremiumProduct.PRO_MONTHLY) "PRO" else "AD_FREE"
        return try {
            val response = api.checkout(CheckoutRequest(email = normalizedEmail, product = productKey))
            val checkoutUrl = response.body()?.checkoutUrl
            if (!response.isSuccessful || checkoutUrl.isNullOrBlank()) {
                return PurchaseFlowResult.Failed("Could not start checkout (${response.code()})")
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
            fetchAndCacheEntitlement(
                api,
                EntitlementRequest(
                    email = normalizedEmail,
                    ownerKey = BuildConfig.OWNER_ACCESS_KEY.ifBlank { null }
                )
            )
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

    private suspend fun fetchAndCacheEntitlement(api: WorkerApi, request: EntitlementRequest): PremiumState {
        val response = api.entitlement(request)
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw IllegalStateException("Entitlement lookup failed (${response.code()})")
        }
        val nowMillis = System.currentTimeMillis()
        val state = PremiumState(
            entitlements = body.entitlements.mapNotNull { runCatching { Entitlement.valueOf(it) }.getOrNull() }.toSet(),
            sourceEmail = body.email,
            licenseToken = body.licenseToken,
            lastVerifiedAtMillis = nowMillis,
            gracePeriodExpiresAtMillis = nowMillis + PremiumState.GRACE_PERIOD_MILLIS
        )
        localStore.save(state)
        return state
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
