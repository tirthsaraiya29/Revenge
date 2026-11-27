package com.revenge.music.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

object LocalFileDownloader {
    private const val CHANNEL_ID = "local_downloads"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        url: String,
        destinationDirUri: String,
        fileName: String,
        mimeType: String,
        userAgent: String? = null,
    ) = withContext(Dispatchers.IO) {
        val notificationId = fileName.hashCode()
        try {
            createChannel(context)
            val directory = DocumentFile.fromTreeUri(context, Uri.parse(destinationDirUri))
            if (directory == null || !directory.exists() || !directory.canWrite()) {
                throw IllegalStateException("Destination directory is not writable")
            }
            val target = directory.findFile(fileName)
                ?: directory.createFile(mimeType, fileName)
                ?: throw IllegalStateException("Could not create $fileName")

            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-")
                .apply { userAgent?.let { header("User-Agent", it) } }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val total = response.body?.contentLength() ?: -1L
                val body = response.body ?: throw IllegalStateException("Empty body")
                val output = context.contentResolver.openOutputStream(target.uri, "wt")
                    ?: throw IllegalStateException("Cannot open destination")
                output.use { sink ->
                    body.byteStream().use { source ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 1_000) {
                                lastUpdate = now
                                notifyProgress(context, notificationId, fileName, copied, total)
                            }
                        }
                    }
                }
            }
            NotificationManagerCompat.from(context).cancel(notificationId)
            showToast(context, "Downloaded $fileName")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Timber.e(t, "Local download failed: $url")
            NotificationManagerCompat.from(context).cancel(notificationId)
            showToast(context, "Download failed: $fileName")
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Local downloads",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun notifyProgress(
        context: Context,
        id: Int,
        fileName: String,
        copied: Long,
        total: Long,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading $fileName")
                .setOngoing(true)
            if (total > 0) {
                builder.setProgress(100, ((copied * 100) / total).toInt(), false)
            } else {
                builder.setProgress(0, 0, true)
            }
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }.onFailure { Timber.w(it, "Cannot post download notification") }
    }

    private fun showToast(context: Context, message: String) {
        runCatching {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
