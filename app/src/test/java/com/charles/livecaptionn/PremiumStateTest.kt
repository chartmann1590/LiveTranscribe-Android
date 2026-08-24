package com.charles.livecaptionn

import com.charles.livecaptionn.billing.Entitlement
import com.charles.livecaptionn.billing.PremiumState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumStateTest {

    @Test
    fun defaults_haveNoEntitlements() {
        assertFalse(PremiumState().hasAdFree)
        assertFalse(PremiumState().hasPro)
    }

    @Test
    fun hasAdFree_trueWhenEntitlementPresent() {
        val state = PremiumState(entitlements = setOf(Entitlement.AD_FREE))
        assertTrue(state.hasAdFree)
        assertFalse(state.hasPro)
    }

    @Test
    fun hasPro_trueWhenEntitlementPresent() {
        val state = PremiumState(entitlements = setOf(Entitlement.PRO))
        assertTrue(state.hasPro)
        assertTrue(state.hasAdFree)
    }

    @Test
    fun isWithinGracePeriod_trueWhenNoExpiryTracked() {
        val state = PremiumState(gracePeriodExpiresAtMillis = null)
        assertTrue(state.isWithinGracePeriod(nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun isWithinGracePeriod_trueBeforeExpiry() {
        val state = PremiumState(gracePeriodExpiresAtMillis = 1_000L)
        assertTrue(state.isWithinGracePeriod(nowMillis = 500L))
    }

    @Test
    fun isWithinGracePeriod_falseAfterExpiry() {
        val state = PremiumState(gracePeriodExpiresAtMillis = 1_000L)
        assertFalse(state.isWithinGracePeriod(nowMillis = 1_001L))
    }
}
