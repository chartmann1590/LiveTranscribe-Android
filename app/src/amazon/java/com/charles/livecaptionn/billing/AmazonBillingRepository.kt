package com.charles.livecaptionn.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.Receipt
import com.amazon.device.iap.model.RequestId
import com.amazon.device.iap.model.UserDataResponse
import com.charles.livecaptionn.data.PremiumLocalStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Amazon Appstore flavor: Amazon In-App Purchasing (Appstore SDK 3.x) behind the same
 * [PremiumRepository] the playstore (Play Billing) and github (Stripe) flavors implement.
 * Fire devices have no Google Play services, and Amazon requires its own IAP for digital
 * unlocks. Same two monthly subscriptions and prices as the Play build:
 *   Ad-Free  parent [AmazonSkus.AD_FREE_PARENT] / term [AmazonSkus.AD_FREE_TERM]  $1.99/mo
 *   Pro      parent [AmazonSkus.PRO_PARENT]     / term [AmazonSkus.PRO_TERM]      $4.99/mo
 * Amazon is the source of truth (getPurchaseUpdates(reset=true) lists every active
 * subscription of the signed-in Amazon user); [PremiumLocalStore] caches it like the Play flavor.
 */
class AmazonBillingRepository(
    context: Context,
    private val localStore: PremiumLocalStore,
    private val appScope: CoroutineScope
) : PremiumRepository, PurchasingListener {

    private val appContext = context.applicationContext
    override val state: Flow<PremiumState> = localStore.stateFlow
    override val supportsEmailRestore: Boolean = false

    private val pendingUpdates = ConcurrentHashMap<RequestId, CompletableDeferred<Result<PremiumState>>>()
    private val pageEntitlements = ConcurrentHashMap<RequestId, MutableSet<Entitlement>>()
    @Volatile private var registered = false

    init {
        registered = try {
            PurchasingService.registerListener(appContext, this)
            PurchasingService.getUserData()
            PurchasingService.getProductData(AmazonSkus.ALL_TERMS)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Amazon IAP unavailable: ${t.message}")
            false
        }
        if (registered) appScope.launch { refresh() }
    }

    override suspend fun refresh(sessionId: String?): Result<PremiumState> {
        if (!registered) return Result.failure(IllegalStateException("Amazon Appstore purchasing is unavailable"))
        val done = CompletableDeferred<Result<PremiumState>>()
        val id = PurchasingService.getPurchaseUpdates(true)
        pendingUpdates[id] = done
        pageEntitlements[id] = mutableSetOf()
        return withTimeoutOrNull(30_000L) { done.await() }
            ?: Result.failure(IllegalStateException("Amazon Appstore did not answer"))
    }

    override suspend fun purchase(activity: Activity, product: PremiumProduct, email: String?): PurchaseFlowResult {
        if (!registered) return PurchaseFlowResult.Failed("Amazon Appstore purchasing is unavailable on this device")
        val sku = AmazonSkus.termFor(product)
        Log.i(TAG, "purchase($sku)")
        return try {
            PurchasingService.purchase(sku)
            PurchaseFlowResult.Started
        } catch (t: Throwable) {
            PurchaseFlowResult.Failed(t.message ?: "Amazon purchase could not start")
        }
    }

    override suspend fun restore(email: String?): Result<PremiumState> = refresh()

    override suspend fun openManageSubscription(activity: Activity): ManageAction = try {
        // Amazon manages subscriptions in the Appstore / the customer's Amazon account, never on Play.
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.amazon.com/yourmembershipsandsubscriptions")))
        ManageAction.Opened
    } catch (e: Exception) {
        ManageAction.Failed(e.message ?: "Could not open subscriptions")
    }

    private fun entitlementOf(r: Receipt): Entitlement? {
        if (r.productType != ProductType.SUBSCRIPTION || r.isCanceled) return null
        return AmazonSkus.productFor(r.sku)?.entitlement ?: AmazonSkus.productFor(r.termSku)?.entitlement
    }

    private fun saveState(entitlements: Set<Entitlement>): PremiumState {
        val s = PremiumState(entitlements = entitlements, lastVerifiedAtMillis = System.currentTimeMillis(), gracePeriodExpiresAtMillis = null)
        appScope.launch { localStore.save(s) }
        return s
    }

    override fun onUserDataResponse(response: UserDataResponse) {
        Log.i(TAG, "onUserDataResponse: ${response.requestStatus} marketplace=${response.userData?.marketplace}")
    }

    override fun onProductDataResponse(response: ProductDataResponse) {
        Log.i(TAG, "onProductDataResponse: ${response.requestStatus} products=${response.productData?.keys} unavailable=${response.unavailableSkus}")
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        Log.i(TAG, "onPurchaseResponse: ${response.requestStatus}")
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                val r = response.receipt ?: return
                PurchasingService.notifyFulfillment(r.receiptId, FulfillmentResult.FULFILLED)
                appScope.launch { refresh() }
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> appScope.launch { refresh() }
            else -> Unit // FAILED (incl. user cancel), INVALID_SKU, NOT_SUPPORTED, PENDING
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        val id = response.requestId
        val done = pendingUpdates.remove(id)
        val acc = pageEntitlements.remove(id) ?: mutableSetOf()
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
            Log.w(TAG, "onPurchaseUpdatesResponse: ${response.requestStatus}")
            done?.complete(Result.failure(IllegalStateException("Amazon Appstore: ${response.requestStatus}")))
            return
        }
        response.receipts.forEach { r -> entitlementOf(r)?.let { acc.add(it) } }
        if (response.hasMore()) {
            val next = PurchasingService.getPurchaseUpdates(false)
            pageEntitlements[next] = acc
            if (done != null) pendingUpdates[next] = done
            return
        }
        done?.complete(Result.success(saveState(acc))) ?: saveState(acc)
    }

    private companion object {
        const val TAG = "AmazonBillingRepository"
    }
}

/** Amazon Developer Console SKUs (see AMAZON-IAP-SKUS.md). Not secrets. */
object AmazonSkus {
    const val AD_FREE_PARENT = "ad_free_monthly"
    const val AD_FREE_TERM = "ad_free_monthly_1m"
    const val PRO_PARENT = "pro_monthly"
    const val PRO_TERM = "pro_monthly_1m"
    val ALL_TERMS: Set<String> = setOf(AD_FREE_TERM, PRO_TERM)

    fun termFor(p: PremiumProduct): String = when (p) {
        PremiumProduct.AD_FREE_MONTHLY -> AD_FREE_TERM
        PremiumProduct.PRO_MONTHLY -> PRO_TERM
    }

    fun productFor(sku: String?): PremiumProduct? = when (sku) {
        AD_FREE_PARENT, AD_FREE_TERM -> PremiumProduct.AD_FREE_MONTHLY
        PRO_PARENT, PRO_TERM -> PremiumProduct.PRO_MONTHLY
        else -> null
    }
}
