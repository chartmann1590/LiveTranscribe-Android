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

/** Shared Hartmann Studios billing v2 (/v2/checkout): product is a catalog key (AD_FREE, PRO, PRO_YEARLY, PRO_LIFETIME). */
@JsonClass(generateAdapter = true)
data class V2CheckoutRequest(val app: String, val product: String, val email: String)

@JsonClass(generateAdapter = true)
data class V2EntitlementRequest(
    val app: String,
    val sessionId: String? = null,
    val email: String? = null,
    val ownerKey: String? = null
)
