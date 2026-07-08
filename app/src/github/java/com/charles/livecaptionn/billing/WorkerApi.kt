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

    @GET("portal")
    suspend fun portal(): Response<PortalResponse>
}
