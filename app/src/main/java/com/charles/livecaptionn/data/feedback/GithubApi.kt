package com.charles.livecaptionn.data.feedback

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Talks to the cloudflare-worker-feedback/ relay, not api.github.com directly. See
 * GithubClient.kt and cloudflare-worker-feedback/src/index.ts.
 */
interface GithubApi {

    @POST("issue")
    suspend fun createIssue(@Body request: CreateIssueRequest): Response<GithubIssue>

    @GET("issue/{number}")
    suspend fun getIssue(@Path("number") number: Int): Response<GithubIssue>

    @GET("issue/{number}/comments")
    suspend fun getComments(@Path("number") number: Int): Response<List<GithubComment>>

    @POST("issue/{number}/comments")
    suspend fun postComment(
        @Path("number") number: Int,
        @Body request: PostCommentRequest
    ): Response<GithubComment>

    @POST("upload-image")
    suspend fun uploadAsset(@Body request: UploadAssetRequest): Response<UploadAssetResponse>
}
