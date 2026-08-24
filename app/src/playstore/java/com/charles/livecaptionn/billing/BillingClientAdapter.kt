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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around [BillingClient]'s callback API used only by
 * [PlayBillingRepository]. Exists so purchase-processing/entitlement-mapping
 * logic can be unit tested against a fake, since [BillingClient] itself isn't
 * meaningfully mockable/unit-testable.
 */
interface BillingClientAdapter {
    suspend fun connect(): Boolean
    suspend fun queryProductDetails(productIds: List<String>): List<ProductDetails>
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

    override suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        if (client.isReady) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        client.startConnection(object : com.android.billingclient.api.BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: com.android.billingclient.api.BillingResult) {
                if (cont.isActive) cont.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    override suspend fun queryProductDetails(productIds: List<String>): List<ProductDetails> {
        if (!connect()) throw IllegalStateException("Google Play Billing is unavailable")
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
            client.queryProductDetailsAsync(params) { _, productDetailsResult ->
                if (cont.isActive) cont.resume(productDetailsResult.productDetailsList)
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
        if (!connect()) throw IllegalStateException("Google Play Billing is unavailable")
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
}
