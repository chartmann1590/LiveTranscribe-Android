package com.charles.livecaptionn.billing

enum class PremiumProduct(val entitlement: Entitlement) {
    AD_FREE_MONTHLY(Entitlement.AD_FREE),
    PRO_MONTHLY(Entitlement.PRO),
    /** github/Stripe flavor only: yearly Pro (14-day free trial on the billing server). */
    PRO_YEARLY(Entitlement.PRO),
    /** github/Stripe flavor only: one-time lifetime Pro. */
    PRO_LIFETIME(Entitlement.PRO)
}
