package com.charles.livecaptionn.translation

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LibreLanguage(
    @Json(name = "code") val code: String,
    @Json(name = "name") val name: String
)

data class TranslateRequest(
    @Json(name = "q") val q: String,
    @Json(name = "source") val source: String,
    @Json(name = "target") val target: String,
    @Json(name = "format") val format: String = "text"
)

data class TranslateResponse(
    @Json(name = "translatedText") val translatedText: String
)

interface LibreTranslateApi {
    @GET("languages")
    suspend fun languages(): List<LibreLanguage>

    @POST("translate")
    suspend fun translate(@Body request: TranslateRequest): TranslateResponse
}
