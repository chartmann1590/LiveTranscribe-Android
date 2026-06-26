package com.charles.livecaptionn.data.feedback

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageHelper {

    fun uriToBase64(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for URI: $uri")
        return inputStream.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(chunk).also { bytesRead = it } != -1) {
                buffer.write(chunk, 0, bytesRead)
            }
            Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
        }
    }
}
