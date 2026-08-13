package com.charles.livecaptionn.data.feedback

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Talks to the cloudflare-worker-feedback/ relay, not api.github.com directly — the
 * Worker holds the GitHub token as a server-side secret and hardcodes this app's own
 * repo, so no owner/repo/credential ever needs to travel through this app. Previously
 * embedded BuildConfig.GITHUB_API_TOKEN client-side as a Bearer header, which shipped a
 * real repo-write PAT in every release build (extractable from the APK). See
 * cloudflare-worker-feedback/src/index.ts.
 */
object GithubClient {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        android.util.Log.d("GithubClient", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://livetranscribe-github-feedback.charles-h-hartmann1.workers.dev/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GithubApi = retrofit.create(GithubApi::class.java)
}
