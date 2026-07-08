package com.charles.livecaptionn.di

import android.content.Context
import com.charles.livecaptionn.billing.PlayBillingRepository
import com.charles.livecaptionn.billing.PremiumRepository
import com.charles.livecaptionn.data.PremiumLocalStore
import kotlinx.coroutines.CoroutineScope

/**
 * Flavor-specific factory resolved at compile time via the playstore source
 * set. Compiling this file into the app is what wires Play Billing in for
 * the playstore flavor; the github flavor has its own implementation of this
 * same function signature that wires Stripe instead.
 */
fun createPremiumRepository(
    context: Context,
    store: PremiumLocalStore,
    scope: CoroutineScope
): PremiumRepository = PlayBillingRepository(context, store, scope)
