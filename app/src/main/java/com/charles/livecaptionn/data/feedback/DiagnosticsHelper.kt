package com.charles.livecaptionn.data.feedback

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DiagnosticsHelper {

    fun collect(context: Context): String {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val packageName = context.packageName
        val versionName = packageVersionName(context) ?: "unknown"
        val versionCode = packageVersionCode(context)?.toString() ?: "unknown"
        val brand = Build.BRAND
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val androidVersion = Build.VERSION.RELEASE
        val apiLevel = Build.VERSION.SDK_INT
        val locale = Locale.getDefault().toString()
        val timeZone = TimeZone.getDefault().id
        val storage = storageInfo()
        val memory = memoryInfo()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            .format(Date())

        return buildString {
            appendLine("## Diagnostics")
            appendLine()
            appendLine("- App: $appName")
            appendLine("- Package: $packageName")
            appendLine("- Version: $versionName ($versionCode)")
            appendLine("- Device: $brand $model")
            appendLine("- Manufacturer: $manufacturer")
            appendLine("- Android: $androidVersion / API $apiLevel")
            appendLine("- Locale: $locale")
            appendLine("- Time Zone: $timeZone")
            appendLine("- Storage Free/Total: ${storage.first} / ${storage.second}")
            appendLine("- Memory Free/Total: ${memory.first} / ${memory.second}")
            appendLine("- Timestamp: $now")
        }
    }

    private fun packageVersionName(context: Context): String? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName
        } catch (_: Exception) {
            null
        }
    }

    private fun packageVersionCode(context: Context): Long? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun storageInfo(): Pair<String, String> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val freeBlocks = stat.availableBlocksLong
            val totalBlocks = stat.blockCountLong
            formatSize(freeBlocks * blockSize) to formatSize(totalBlocks * blockSize)
        } catch (_: Exception) {
            "unknown" to "unknown"
        }
    }

    private fun memoryInfo(): Pair<String, String> {
        return try {
            val info = Runtime.getRuntime()
            val free = info.freeMemory()
            val total = info.totalMemory()
            formatSize(free) to formatSize(total)
        } catch (_: Exception) {
            "unknown" to "unknown"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1L shl 40 -> String.format(Locale.US, "%.1f TB", bytes.toDouble() / (1L shl 40))
            bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1L shl 20))
            bytes >= 1L shl 10 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / (1L shl 10))
            else -> "$bytes B"
        }
    }
}
