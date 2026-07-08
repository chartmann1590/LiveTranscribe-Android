package com.charles.livecaptionn.billing

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CheckoutRequest(val email: String, val product: String)

@JsonClass(generateAdapter = true)
data class CheckoutResponse(val checkoutUrl: String)

@JsonClass(generateAdapter = true)
data class EntitlementRequest(
    val sessionId: String? = null,
    val email: String? = null,
    val ownerKey: String? = null
)

@JsonClass(generateAdapter = true)
data class EntitlementResponse(
    val email: String,
    val entitlements: List<String>,
    val licenseToken: String,
    val issuedAt: Long,
    val revalidateAfter: Long
)

@JsonClass(generateAdapter = true)
data class PortalResponse(val portalUrl: String)

@JsonClass(generateAdapter = true)
data class WorkerErrorResponse(val error: String?)
