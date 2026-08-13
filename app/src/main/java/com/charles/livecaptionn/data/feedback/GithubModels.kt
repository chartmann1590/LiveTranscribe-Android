package com.charles.livecaptionn.data.feedback

import com.squareup.moshi.Json

data class CreateIssueRequest(
    val title: String,
    val body: String
)

data class GithubIssue(
    val number: Int,
    val title: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    val body: String? = null
)

data class GithubUser(
    val login: String
)

data class GithubComment(
    val id: Long,
    val body: String,
    @Json(name = "created_at") val createdAt: String,
    val user: GithubUser
)

data class PostCommentRequest(
    val body: String
)

data class UploadAssetRequest(
    val filename: String,
    val contentBase64: String
)

data class UploadContentInfo(
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

data class UploadAssetResponse(
    val content: UploadContentInfo? = null
)

data class BugReport(
    val number: Int,
    val title: String,
    val status: String,
    val createdAt: String,
    val htmlUrl: String
)
