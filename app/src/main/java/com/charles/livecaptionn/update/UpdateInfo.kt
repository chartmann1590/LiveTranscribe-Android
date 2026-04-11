package com.charles.livecaptionn.update

/**
 * Snapshot of a GitHub release that's newer than the currently running build.
 */
data class UpdateInfo(
    /** Tag name as published on GitHub, e.g. "v1.0.42". */
    val tagName: String,
    /** User-friendly release title, e.g. "LiveCaptionN v1.0.42". */
    val releaseName: String,
    /** Build number parsed from the tag (`42` from `v1.0.42`). Compared to local versionCode. */
    val buildNumber: Int,
    /** Direct APK download URL when the release has one attached. May be null if only the HTML page is available. */
    val apkDownloadUrl: String?,
    /** Browser URL of the release page, always present. */
    val releasePageUrl: String,
    /** Release notes (truncated by caller before display). */
    val notes: String
)
