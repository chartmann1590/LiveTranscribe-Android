package com.charles.livecaptionn.billing

/**
 * Cached view of the user's entitlements. [licenseToken]/[sourceEmail] are only
 * ever populated on the github/Stripe flavor; Play Billing entitlement has no
 * concept of a license token since Play itself is the source of truth.
 */
data class PremiumState(
    val entitlements: Set<Entitlement> = emptySet(),
    val sourceEmail: String? = null,
    val licenseToken: String? = null,
    val lastVerifiedAtMillis: Long = 0L,
    val gracePeriodExpiresAtMillis: Long? = null
) {
    /** Pro includes the Ad-Free benefit even when the store reports one product. */
    val hasAdFree: Boolean get() = hasPro || Entitlement.AD_FREE in entitlements
    val hasPro: Boolean get() = Entitlement.PRO in entitlements

    /** True while cached entitlements can still be trusted without a successful revalidation. */
    fun isWithinGracePeriod(nowMillis: Long): Boolean {
        val expiresAt = gracePeriodExpiresAtMillis ?: return true
        return nowMillis < expiresAt
    }

    companion object {
        val EMPTY = PremiumState()
        const val GRACE_PERIOD_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
