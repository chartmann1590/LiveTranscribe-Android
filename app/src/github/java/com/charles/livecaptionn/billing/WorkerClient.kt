package com.charles.livecaptionn.billing

import com.charles.livecaptionn.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object WorkerClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = BuildConfig.PREMIUM_WORKER_BASE_URL.isNotBlank()

    val api: WorkerApi? by lazy {
        if (!isConfigured) return@lazy null
        val baseUrl = BuildConfig.PREMIUM_WORKER_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WorkerApi::class.java)
    }
}
