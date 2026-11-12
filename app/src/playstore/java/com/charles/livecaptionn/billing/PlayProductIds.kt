package com.charles.livecaptionn.billing

import com.charles.livecaptionn.BuildConfig

/** Maps a flavor-agnostic [PremiumProduct] to its Play Console subscription product ID. */
fun PremiumProduct.playProductId(): String = when (this) {
    PremiumProduct.AD_FREE_MONTHLY -> BuildConfig.PLAY_PRODUCT_AD_FREE
    PremiumProduct.PRO_MONTHLY -> BuildConfig.PLAY_PRODUCT_PRO
    // Yearly / lifetime exist only on the github (Stripe) flavor; Play keeps its monthly Pro.
    PremiumProduct.PRO_YEARLY, PremiumProduct.PRO_LIFETIME -> BuildConfig.PLAY_PRODUCT_PRO
}

fun playProductIdToProduct(productId: String): PremiumProduct? = when (productId) {
    BuildConfig.PLAY_PRODUCT_AD_FREE -> PremiumProduct.AD_FREE_MONTHLY
    BuildConfig.PLAY_PRODUCT_PRO -> PremiumProduct.PRO_MONTHLY
    else -> null
}
