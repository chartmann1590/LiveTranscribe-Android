package com.charles.livecaptionn.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around [BillingClient]'s callback API used only by
 * [PlayBillingRepository]. Exists so purchase-processing/entitlement-mapping
 * logic can be unit tested against a fake, since [BillingClient] itself isn't
 * meaningfully mockable/unit-testable.
 */
/** Outcome of a product-details query: the products Play returned plus why any were missing. */
data class ProductDetailsLookup(
    val products: List<ProductDetails>,
    val responseCode: Int,
    val debugMessage: String
)

interface BillingClientAdapter {
    suspend fun connect(): Boolean
    suspend fun queryProductDetails(productIds: List<String>): List<ProductDetails> =
        lookupProductDetails(productIds).products
    suspend fun lookupProductDetails(productIds: List<String>): ProductDetailsLookup
    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, offerToken: String?): Int
    suspend fun queryActivePurchases(): List<Purchase>
    suspend fun acknowledgePurchase(purchaseToken: String): Boolean
}

class RealBillingClientAdapter(
    context: Context,
    onPurchasesUpdated: PurchasesUpdatedListener
) : BillingClientAdapter {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(onPurchasesUpdated)
        // Billing Library 7.x requires this even though this app only sells
        // subscriptions, not one-time products — omitting it throws at construction.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Why the last [connect] failed, for the message shown to the user. */
    @Volatile
    var lastConnectionError: String? = null
        private set

    override suspend fun connect(): Boolean {
        // One retry: the Play Store service occasionally drops the first bind
        // right after install or a Play Store update, and the second attempt
        // succeeds. Without it the user just saw "Billing is unavailable".
        repeat(2) { attempt ->
            if (connectOnce()) return true
            if (attempt == 0) delay(RECONNECT_DELAY_MS)
        }
        return false
    }

    private suspend fun connectOnce(): Boolean = suspendCancellableCoroutine { cont ->
        if (client.isReady) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        client.startConnection(object : com.android.billingclient.api.BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: com.android.billingclient.api.BillingResult) {
                val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                lastConnectionError = if (ok) null else describe(billingResult.responseCode, billingResult.debugMessage)
                if (cont.isActive) cont.resume(ok)
            }

            override fun onBillingServiceDisconnected() {
                lastConnectionError = "Google Play disconnected before billing was ready"
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    override suspend fun lookupProductDetails(productIds: List<String>): ProductDetailsLookup {
        if (!connect()) {
            throw IllegalStateException(lastConnectionError ?: "Google Play Billing is unavailable")
        }
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        return suspendCancellableCoroutine { cont ->
            // Billing 9.x passes a QueryProductDetailsResult as the 2nd arg
            // (was List<ProductDetails> in 7.x/8.x).
            client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                if (cont.isActive) {
                    val missing = productDetailsResult.unfetchedProductList
                        .joinToString { "${it.productId}:${it.statusCode}" }
                    cont.resume(
                        ProductDetailsLookup(
                            products = productDetailsResult.productDetailsList,
                            responseCode = billingResult.responseCode,
                            debugMessage = listOf(billingResult.debugMessage, missing)
                                .filter { it.isNotBlank() }
                                .joinToString("; ")
                        )
                    )
                }
            }
        }
    }

    override fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, offerToken: String?): Int {
        val offerParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        offerToken?.let { offerParamsBuilder.setOfferToken(it) }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(offerParamsBuilder.build()))
            .build()
        return client.launchBillingFlow(activity, flowParams).responseCode
    }

    override suspend fun queryActivePurchases(): List<Purchase> {
        // Do not turn a transient Play connection failure into a false revoke.
        if (!connect()) {
            throw IllegalStateException(lastConnectionError ?: "Google Play Billing is unavailable")
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { _, purchases ->
                if (cont.isActive) cont.resume(purchases)
            }
        }
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        if (!connect()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 750L
    }
}

/** Human-readable text for a Play Billing response code (shown in the Upgrade card). */
fun describe(responseCode: Int, debugMessage: String = ""): String {
    val base = when (responseCode) {
        BillingClient.BillingResponseCode.OK -> return ""
        BillingClient.BillingResponseCode.USER_CANCELED -> "Purchase cancelled"
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "Google Play couldn't be reached. Check your connection and try again"
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play Billing isn't available on this device or account. Make sure you're signed in to the Play Store and it is up to date"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan isn't available for purchase in your Google Play country yet"
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Purchase could not start (billing setup error)"
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already own this plan. Tap Restore to apply it"
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "This plan isn't owned by this Google account"
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Subscriptions aren't supported by the Play Store on this device"
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Google Play disconnected. Please try again"
        else -> "Google Play returned error $responseCode"
    }
    return if (debugMessage.isBlank()) base else "$base ($debugMessage)"
}
