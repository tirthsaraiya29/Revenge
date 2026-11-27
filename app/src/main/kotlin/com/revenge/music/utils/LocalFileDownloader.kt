package com.revenge.music.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.revenge.music.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Streams a remote file into a SAF tree document. Progress is reported through the
 * `local_downloads` notification channel; failures and success are surfaced as toasts.
 */
object LocalFileDownloader {

    private const val CHANNEL_ID = "local_downloads"
    private const val PROGRESS_MAX = 100

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun download(
        context: Context,
        url: String,
        destinationDirUri: Uri,
        fileName: String,
        mimeType: String,
        userAgent: String? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val directoryUri = DocumentsContract.buildDocumentUriUsingTree(
                destinationDirUri,
                DocumentsContract.getTreeDocumentId(destinationDirUri),
            )
            ensureWritable(resolver, directoryUri)
            val fileUri = findChild(context, directoryUri, fileName)
                ?: DocumentsContract.createDocument(resolver, directoryUri, mimeType, fileName)
                ?: throw IOException("Unable to create $fileName")
            downloadInto(context, url, fileUri, fileName, userAgent)
            Toast.makeText(context, R.string.download_completed, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Timber.w(error, "Local download failed for $url")
            Toast.makeText(context, R.string.an_error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureWritable(
        resolver: android.content.ContentResolver,
        directoryUri: Uri,
    ) {
        val flags = resolver.query(
            directoryUri,
            arrayOf(Document.COLUMN_FLAGS),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } ?: 0
        if (flags and Document.FLAG_DIR_SUPPORTS_CREATE == 0) {
            throw IOException("Directory is not writable")
        }
    }

    private fun findChild(
        context: Context,
        directoryUri: Uri,
        fileName: String,
    ): Uri? {
        val directoryId = DocumentsContract.getDocumentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            directoryId,
        )
        context.contentResolver.query(
            childrenUri,
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == fileName) {
                    val documentId = cursor.getString(0)
                    return DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                }
            }
        }
        return null
    }

    private fun downloadInto(
        context: Context,
        url: String,
        fileUri: Uri,
        fileName: String,
        userAgent: String?,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-")
            .apply { userAgent?.let { header("User-Agent", it) } }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = totalBytes(response, body.contentLength())
            val notificationId = fileName.hashCode()
            val notificationManager = NotificationManagerCompat.from(context)
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.download)
                .setContentTitle(fileName)
                .setOnlyAlertOnce(true)
                .setProgress(PROGRESS_MAX, 0, total <= 0L)
                .build()

            context.contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 1_000L) {
                            lastUpdate = now
                            val progress = if (total > 0L) {
                                ((copied * PROGRESS_MAX) / total).toInt().coerceIn(0, PROGRESS_MAX)
                            } else {
                                0
                            }
                            notificationManager.notify(
                                notificationId,
                                NotificationCompat.Builder(context, CHANNEL_ID)
                                    .setSmallIcon(R.drawable.download)
                                    .setContentTitle(fileName)
                                    .setOnlyAlertOnce(true)
                                    .setProgress(PROGRESS_MAX, progress, total <= 0L)
                                    .build(),
                            )
                        }
                    }
                }
            } ?: throw IOException("Unable to open destination")
            notificationManager.cancel(notificationId)
        }
    }

    private fun totalBytes(response: okhttp3.Response, contentLength: Long): Long {
        if (response.code != 206) return contentLength
        val contentRange = response.header("Content-Range") ?: return contentLength
        return contentRange.substringAfterLast('/').toLongOrNull() ?: contentLength
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.downloading),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }
}
