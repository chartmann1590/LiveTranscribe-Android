package com.charles.livecaptionn.di

import android.content.Context
import com.charles.livecaptionn.billing.AmazonBillingRepository
import com.charles.livecaptionn.billing.PremiumRepository
import com.charles.livecaptionn.data.PremiumLocalStore
import kotlinx.coroutines.CoroutineScope

/** amazon-flavor counterpart of the playstore (Play Billing) and github (Stripe) factories. */
fun createPremiumRepository(
    context: Context,
    store: PremiumLocalStore,
    scope: CoroutineScope
): PremiumRepository = AmazonBillingRepository(context, store, scope)
