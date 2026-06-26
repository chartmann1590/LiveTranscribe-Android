package com.charles.livecaptionn.ui.feedback

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.data.feedback.BugReport
import com.charles.livecaptionn.data.feedback.BugReportRepo
import com.charles.livecaptionn.data.feedback.CreateIssueRequest
import com.charles.livecaptionn.data.feedback.DiagnosticsHelper
import com.charles.livecaptionn.data.feedback.GithubClient
import com.charles.livecaptionn.data.feedback.GithubComment
import com.charles.livecaptionn.data.feedback.GithubIssue
import com.charles.livecaptionn.data.feedback.ImageHelper
import com.charles.livecaptionn.data.feedback.PostCommentRequest
import com.charles.livecaptionn.data.feedback.UploadAssetRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FeedbackUiState(
    val isConfigured: Boolean = GithubClient.isConfigured,
    val configError: String? = null,
    val bugReports: List<BugReport> = emptyList(),

    // Create report dialog
    val showReportDialog: Boolean = false,
    val reportTitle: String = "",
    val reportDescription: String = "",
    val reporterName: String = "",
    val reporterEmail: String = "",
    val includeDiagnostics: Boolean = true,
    val attachmentUri: Uri? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submitSuccess: Boolean = false,

    // Issue details dialog
    val selectedReport: BugReport? = null,
    val showIssueDetails: Boolean = false,
    val issueDetail: GithubIssue? = null,
    val comments: List<GithubComment> = emptyList(),
    val isLoadingIssue: Boolean = false,
    val issueError: String? = null,

    // Comment reply
    val replyText: String = "",
    val replyAttachmentUri: Uri? = null,
    val isPostingReply: Boolean = false,
    val replyError: String? = null
)

class FeedbackViewModel(
    private val context: Context,
    private val repo: BugReportRepo
) : ViewModel() {

    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.bugReports.collect { reports ->
                _state.update { it.copy(bugReports = reports) }
            }
        }
        refreshConfig()
    }

    private fun refreshConfig() {
        val configured = GithubClient.isConfigured
        val error = when {
            BuildConfig.GITHUB_API_TOKEN.isBlank() -> "GitHub API token is not configured. Add github.api.token to local.properties."
            BuildConfig.GITHUB_REPO_OWNER.isBlank() -> "GitHub repo owner is not configured. Add github.repo.owner to local.properties."
            BuildConfig.GITHUB_REPO_NAME.isBlank() -> "GitHub repo name is not configured. Add github.repo.name to local.properties."
            else -> null
        }
        _state.update { it.copy(isConfigured = configured, configError = error) }
    }

    fun showReportDialog() {
        _state.update {
            it.copy(
                showReportDialog = true,
                reportTitle = "",
                reportDescription = "",
                reporterName = "",
                reporterEmail = "",
                includeDiagnostics = true,
                attachmentUri = null,
                submitError = null,
                submitSuccess = false
            )
        }
    }

    fun hideReportDialog() {
        _state.update { it.copy(showReportDialog = false) }
    }

    fun updateReportTitle(value: String) {
        _state.update { it.copy(reportTitle = value) }
    }

    fun updateReportDescription(value: String) {
        _state.update { it.copy(reportDescription = value) }
    }

    fun updateReporterName(value: String) {
        _state.update { it.copy(reporterName = value) }
    }

    fun updateReporterEmail(value: String) {
        _state.update { it.copy(reporterEmail = value) }
    }

    fun updateIncludeDiagnostics(value: Boolean) {
        _state.update { it.copy(includeDiagnostics = value) }
    }

    fun updateAttachmentUri(uri: Uri?) {
        _state.update { it.copy(attachmentUri = uri) }
    }

    fun clearAttachment() {
        _state.update { it.copy(attachmentUri = null) }
    }

    fun submitReport() {
        val s = _state.value
        if (s.reportTitle.isBlank() || s.reportDescription.isBlank()) {
            _state.update { it.copy(submitError = "Title and description are required.") }
            return
        }
        if (!s.isConfigured) {
            _state.update { it.copy(submitError = "GitHub configuration is incomplete. Cannot submit.") }
            return
        }
        if (s.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, submitError = null) }

        viewModelScope.launch {
            try {
                val owner = GithubClient.repoOwner
                val repoName = GithubClient.repoName
                val api = GithubClient.api

                val title = "[Feedback] ${s.reportTitle}"
                val bodyBuilder = StringBuilder()
                bodyBuilder.appendLine("## Description")
                bodyBuilder.appendLine()
                bodyBuilder.appendLine(s.reportDescription)

                bodyBuilder.appendLine()
                bodyBuilder.appendLine("## Contact Info")
                bodyBuilder.appendLine()
                bodyBuilder.appendLine("- Name: ${s.reporterName.ifBlank { "Not provided" }}")
                bodyBuilder.appendLine("- Email: ${s.reporterEmail.ifBlank { "Not provided" }}")

                var uploadedUrl: String? = null
                val attachmentUri = s.attachmentUri
                if (attachmentUri != null) {
                    try {
                        val base64 = ImageHelper.uriToBase64(context, attachmentUri)
                        val filename = "feedback-assets/issue-${timestamp()}-${randomHex()}.png"
                        val uploadResponse = api.uploadAsset(
                            owner, repoName,
                            BuildConfig.FEEDBACK_ASSETS_DIR,
                            filename.removePrefix("feedback-assets/"),
                            UploadAssetRequest(
                                message = "Add screenshot for feedback issue",
                                content = base64
                            )
                        )
                        if (uploadResponse.isSuccessful) {
                            uploadedUrl = uploadResponse.body()?.content?.downloadUrl
                        }
                    } catch (_: Exception) {
                        // If upload fails, continue without attachment
                    }
                }

                if (uploadedUrl != null) {
                    bodyBuilder.appendLine()
                    bodyBuilder.appendLine("## Attachment")
                    bodyBuilder.appendLine()
                    bodyBuilder.appendLine("![Screenshot]($uploadedUrl)")
                }

                if (s.includeDiagnostics) {
                    bodyBuilder.appendLine()
                    bodyBuilder.appendLine(DiagnosticsHelper.collect(context))
                }

                val response = api.createIssue(
                    owner, repoName,
                    CreateIssueRequest(title = title, body = bodyBuilder.toString())
                )

                if (response.isSuccessful) {
                    val issue = response.body()
                    if (issue != null) {
                        val report = BugReport(
                            number = issue.number,
                            title = s.reportTitle,
                            status = issue.state,
                            createdAt = issue.createdAt,
                            htmlUrl = issue.htmlUrl
                        )
                        repo.saveBugReport(report)
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                submitSuccess = true,
                                showReportDialog = false
                            )
                        }
                    } else {
                        _state.update { it.copy(isSubmitting = false, submitError = "Empty response from GitHub.") }
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: response.message()
                    _state.update { it.copy(isSubmitting = false, submitError = "GitHub API error: $errorBody") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, submitError = "Network error: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun openIssueDetails(report: BugReport) {
        _state.update {
            it.copy(
                selectedReport = report,
                showIssueDetails = true,
                issueDetail = null,
                comments = emptyList(),
                isLoadingIssue = true,
                issueError = null,
                replyText = "",
                replyAttachmentUri = null,
                replyError = null
            )
        }
        viewModelScope.launch {
            try {
                val owner = GithubClient.repoOwner
                val repoName = GithubClient.repoName
                val api = GithubClient.api

                val issueResponse = api.getIssue(owner, repoName, report.number)
                if (!issueResponse.isSuccessful) {
                    _state.update {
                        it.copy(isLoadingIssue = false, issueError = "Failed to fetch issue: ${issueResponse.message()}")
                    }
                    return@launch
                }
                val issue = issueResponse.body() ?: run {
                    _state.update { it.copy(isLoadingIssue = false, issueError = "Empty issue response.") }
                    return@launch
                }

                val commentsResponse = api.getComments(owner, repoName, report.number)
                val comments = if (commentsResponse.isSuccessful) {
                    commentsResponse.body() ?: emptyList()
                } else {
                    emptyList()
                }

                // Update local status if changed
                if (issue.state != report.status) {
                    repo.saveBugReport(report.copy(status = issue.state))
                }

                _state.update {
                    it.copy(
                        isLoadingIssue = false,
                        issueDetail = issue,
                        comments = comments
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingIssue = false,
                        issueError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun closeIssueDetails() {
        _state.update {
            it.copy(
                showIssueDetails = false,
                selectedReport = null,
                issueDetail = null,
                comments = emptyList()
            )
        }
    }

    fun updateReplyText(value: String) {
        _state.update { it.copy(replyText = value) }
    }

    fun updateReplyAttachmentUri(uri: Uri?) {
        _state.update { it.copy(replyAttachmentUri = uri) }
    }

    fun clearReplyAttachment() {
        _state.update { it.copy(replyAttachmentUri = null) }
    }

    fun postReply() {
        val s = _state.value
        val report = s.selectedReport ?: return
        if (s.replyText.isBlank() && s.replyAttachmentUri == null) {
            _state.update { it.copy(replyError = "Reply text or attachment is required.") }
            return
        }
        if (s.isPostingReply) return

        _state.update { it.copy(isPostingReply = true, replyError = null) }

        viewModelScope.launch {
            try {
                val owner = GithubClient.repoOwner
                val repoName = GithubClient.repoName
                val api = GithubClient.api

                var uploadedUrl: String? = null
                val replyAttachmentUri = s.replyAttachmentUri
                if (replyAttachmentUri != null) {
                    try {
                        val base64 = ImageHelper.uriToBase64(context, replyAttachmentUri)
                        val filename = "feedback-assets/reply-${timestamp()}-${randomHex()}.png"
                        val uploadResponse = api.uploadAsset(
                            owner, repoName,
                            BuildConfig.FEEDBACK_ASSETS_DIR,
                            filename.removePrefix("feedback-assets/"),
                            UploadAssetRequest(
                                message = "Add screenshot for comment",
                                content = base64
                            )
                        )
                        if (uploadResponse.isSuccessful) {
                            uploadedUrl = uploadResponse.body()?.content?.downloadUrl
                        }
                    } catch (_: Exception) { }
                }

                val bodyBuilder = StringBuilder()
                bodyBuilder.appendLine("## Reply")
                bodyBuilder.appendLine()
                bodyBuilder.appendLine(s.replyText.ifBlank { "No text provided." })
                if (uploadedUrl != null) {
                    bodyBuilder.appendLine()
                    bodyBuilder.appendLine("## Attachment")
                    bodyBuilder.appendLine()
                    bodyBuilder.appendLine("![Screenshot]($uploadedUrl)")
                }

                val response = api.postComment(
                    owner, repoName, report.number,
                    PostCommentRequest(body = bodyBuilder.toString())
                )

                if (response.isSuccessful) {
                    // Refresh comments
                    val commentsResponse = api.getComments(owner, repoName, report.number)
                    val comments = if (commentsResponse.isSuccessful) {
                        commentsResponse.body() ?: emptyList()
                    } else {
                        emptyList()
                    }
                    _state.update {
                        it.copy(
                            isPostingReply = false,
                            replyText = "",
                            replyAttachmentUri = null,
                            comments = comments
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: response.message()
                    _state.update { it.copy(isPostingReply = false, replyError = "GitHub API error: $errorBody") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isPostingReply = false, replyError = "Network error: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun clearSubmitSuccess() {
        _state.update { it.copy(submitSuccess = false) }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }

    private fun randomHex(): String {
        return (1..6).map { "0123456789abcdef".random() }.joinToString("")
    }

    class Factory(
        private val context: Context,
        private val repo: BugReportRepo
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedbackViewModel(context, repo) as T
        }
    }
}
