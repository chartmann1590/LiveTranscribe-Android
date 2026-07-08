package com.charles.livecaptionn.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

sealed interface PurchaseFlowResult {
    data object Started : PurchaseFlowResult
    data class Failed(val message: String) : PurchaseFlowResult
}

sealed interface ManageAction {
    data object Opened : ManageAction
    data class Failed(val message: String) : ManageAction
}

/**
 * Flavor-agnostic entitlement API. `playstore` and `github` flavors each provide
 * their own implementation (Play Billing vs. Stripe-via-Cloudflare-Worker) behind
 * this interface so the rest of the app never branches on distribution flavor.
 */
interface PremiumRepository {
    val state: Flow<PremiumState>

    /** True only for flavors that support restoring entitlement by email (github/Stripe). */
    val supportsEmailRestore: Boolean

    /**
     * [sessionId], when present, is a just-completed Stripe Checkout Session ID
     * (github flavor only) used to strongly authenticate a fresh purchase — see
     * cloudflare-worker/src/worker.js's /entitlement trust model. Ignored by playstore.
     */
    suspend fun refresh(sessionId: String? = null): Result<PremiumState>

    /**
     * [activity] hosts the Play Billing flow (playstore) or the Custom Tab (github).
     * [email] is required by the github/Stripe flavor to create a Checkout Session;
     * the playstore flavor ignores it.
     */
    suspend fun purchase(activity: Activity, product: PremiumProduct, email: String? = null): PurchaseFlowResult

    suspend fun restore(email: String? = null): Result<PremiumState>

    suspend fun openManageSubscription(activity: Activity): ManageAction
}
