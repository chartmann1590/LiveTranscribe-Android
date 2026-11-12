package com.charles.livecaptionn.billing

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WorkerApi {
    @POST("checkout")
    suspend fun checkout(@Body request: CheckoutRequest): Response<CheckoutResponse>

    @POST("entitlement")
    suspend fun entitlement(@Body request: EntitlementRequest): Response<EntitlementResponse>

    @POST("v2/checkout")
    suspend fun checkoutV2(@Body request: V2CheckoutRequest): Response<CheckoutResponse>

    @POST("v2/entitlement")
    suspend fun entitlementV2(@Body request: V2EntitlementRequest): Response<EntitlementResponse>

    @GET("portal")
    suspend fun portal(): Response<PortalResponse>
}
