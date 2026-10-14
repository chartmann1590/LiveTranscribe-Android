package com.charles.livecaptionn.billing

import com.android.billingclient.api.BillingClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingMessagesTest {

    @Test
    fun ok_isEmpty() {
        assertEquals("", describe(BillingClient.BillingResponseCode.OK))
    }

    @Test
    fun itemUnavailable_mentionsCountry() {
        val msg = describe(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
        assertTrue(msg, msg.contains("country"))
    }

    @Test
    fun billingUnavailable_tellsUserToCheckPlayStore() {
        val msg = describe(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        assertTrue(msg, msg.contains("Play Store"))
    }

    @Test
    fun debugMessage_isAppended() {
        val msg = describe(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, "offline")
        assertTrue(msg, msg.endsWith("(offline)"))
    }

    @Test
    fun unknownCode_isStillReadable() {
        val msg = describe(99)
        assertTrue(msg, msg.contains("99"))
    }
}
