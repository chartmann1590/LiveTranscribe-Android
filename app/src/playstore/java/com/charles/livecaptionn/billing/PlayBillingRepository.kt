package com.charles.livecaptionn.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.data.PremiumLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Play Billing-backed [PremiumRepository]. Entitlement source of truth is Play
 * itself (via [BillingClientAdapter.queryActivePurchases]); [PremiumLocalStore]
 * is just a local cache so the app doesn't need to hit Play on every launch.
 * No backend involved — server-side receipt validation via the Play Developer
 * API is a documented future hardening step, out of scope here.
 */
class PlayBillingRepository(
    context: Context,
    private val localStore: PremiumLocalStore,
    private val appScope: CoroutineScope
) : PremiumRepository {

    private val adapter: BillingClientAdapter = RealBillingClientAdapter(context) { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            appScope.launch { processPurchases(purchases) }
        }
    }

    override val state: Flow<PremiumState> = localStore.stateFlow
    override val supportsEmailRestore: Boolean = false

    init {
        appScope.launch { refresh() }
    }

    override suspend fun refresh(sessionId: String?): Result<PremiumState> {
        return try {
            val purchases = adapter.queryActivePurchases()
            val state = processPurchases(purchases)
            Result.success(state)
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            Result.failure(e)
        }
    }

    override suspend fun purchase(activity: Activity, product: PremiumProduct, email: String?): PurchaseFlowResult {
        val productId = product.playProductId()
        val details = adapter.queryProductDetails(listOf(productId)).firstOrNull()
            ?: return PurchaseFlowResult.Failed("Product unavailable")
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        val responseCode = adapter.launchBillingFlow(activity, details, offerToken)
        return if (responseCode == BillingClient.BillingResponseCode.OK) {
            PurchaseFlowResult.Started
        } else {
            PurchaseFlowResult.Failed("Billing flow error code $responseCode")
        }
    }

    override suspend fun restore(email: String?): Result<PremiumState> = refresh()

    override suspend fun openManageSubscription(activity: Activity): ManageAction {
        return try {
            val uri = Uri.parse(
                "https://play.google.com/store/account/subscriptions?package=${BuildConfig.APPLICATION_ID}"
            )
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            ManageAction.Opened
        } catch (e: Exception) {
            ManageAction.Failed(e.message ?: "Could not open subscriptions")
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>): PremiumState {
        val entitlements = mutableSetOf<Entitlement>()
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (!purchase.isAcknowledged) {
                adapter.acknowledgePurchase(purchase.purchaseToken)
            }
            purchase.products.forEach { productId ->
                playProductIdToProduct(productId)?.let { entitlements.add(it.entitlement) }
            }
        }
        val state = PremiumState(
            entitlements = entitlements,
            lastVerifiedAtMillis = System.currentTimeMillis(),
            gracePeriodExpiresAtMillis = null
        )
        localStore.save(state)
        return state
    }

    private companion object {
        const val TAG = "PlayBillingRepository"
    }
}
