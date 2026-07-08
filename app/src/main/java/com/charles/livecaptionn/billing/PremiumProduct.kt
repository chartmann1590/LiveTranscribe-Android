package com.charles.livecaptionn.billing

enum class PremiumProduct(val entitlement: Entitlement) {
    AD_FREE_MONTHLY(Entitlement.AD_FREE),
    PRO_MONTHLY(Entitlement.PRO)
}
