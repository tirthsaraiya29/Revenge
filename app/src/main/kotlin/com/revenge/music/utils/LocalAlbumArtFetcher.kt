package com.revenge.music.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil fetcher that reads embedded cover art from a local audio file when the requested data is a
 * media file URI/path rather than an image. Non-audio URIs are left to Coil's default fetchers.
 */
class LocalAlbumArtFetcher(
    private val context: Context,
    private val data: String,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(data)
            if (uri.scheme == "content" || uri.scheme == "file") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(data)
            }
            val bytes = retriever.embeddedPicture ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uri = Uri.parse(data)
            val scheme = uri.scheme?.lowercase()
            val looksLocal = when (scheme) {
                null -> true
                "file" -> true
                "content" -> data.contains("/audio/") || data.contains("/video/")
                else -> false
            }
            return if (looksLocal) LocalAlbumArtFetcher(context, data) else null
        }
    }
}
