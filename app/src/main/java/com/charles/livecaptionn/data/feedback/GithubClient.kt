package com.charles.livecaptionn.data.feedback

import com.charles.livecaptionn.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object GithubClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        if (!message.contains("Authorization", ignoreCase = true)) {
            android.util.Log.d("GithubClient", message)
        }
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "LiveCaptionN-Android/0.1")
            val token = BuildConfig.GITHUB_API_TOKEN
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GithubApi = retrofit.create(GithubApi::class.java)

    val isConfigured: Boolean
        get() = BuildConfig.GITHUB_API_TOKEN.isNotBlank()
            && BuildConfig.GITHUB_REPO_OWNER.isNotBlank()
            && BuildConfig.GITHUB_REPO_NAME.isNotBlank()

    val repoOwner: String get() = BuildConfig.GITHUB_REPO_OWNER
    val repoName: String get() = BuildConfig.GITHUB_REPO_NAME
}
