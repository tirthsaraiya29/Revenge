package com.revenge.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Maps remote artwork URLs to signed `content://` URIs that Android Auto can load, and serves the
 * bytes from a bounded on-disk cache. The signature is the security boundary: the provider will
 * only fetch URLs that this process previously signed.
 */
object AutomotiveArtwork {

    private const val PREFERENCES_NAME = "automotive_artwork"
    private const val KEY_SECRET = "uri_secret"
    private const val DIRECTORY_NAME = "automotive_artwork"
    private const val IMAGE_SIZE = 1024
    private const val TIMEOUT_MS = 10_000L
    private const val JPEG_QUALITY = 90
    private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    private const val TARGET_CACHE_BYTES = 48L * 1024L * 1024L
    private const val SIGNATURE_BYTES = 16

    fun authority(context: Context): String = "${context.packageName}.artwork"

    fun contentUriFor(context: Context, uri: Uri?): Uri? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase() ?: return uri
        if (scheme != "http" && scheme != "https") return uri
        val url = uri.toString()
        val encoded = Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val signature = signature(context, url)
        return Uri.parse("content://${authority(context)}/art/$encoded/$signature")
    }

    fun originalUrl(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        if (uri.authority != authority(context)) return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != "art") return null
        val decoded = try {
            String(Base64.decode(segments[1], Base64.URL_SAFE), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        if (!decoded.startsWith("http://") && !decoded.startsWith("https://")) return null
        val expected = signature(context, decoded)
        if (!MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                segments[2].toByteArray(Charsets.UTF_8),
            )
        ) {
            return null
        }
        return decoded
    }

    /**
     * Blocking fetch/encode intended for a provider binder thread. Returns a cached or freshly
     * written JPEG, or null when the image cannot be obtained.
     */
    fun artworkFile(context: Context, url: String): File? {
        val directory = File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }
        val file = File(directory, sha256Hex(url) + ".jpg")
        if (file.exists() && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }

        val bitmap = fetchBitmap(context, url) ?: return null
        val temporary = File(directory, file.name + ".tmp")
        val written = try {
            FileOutputStream(temporary).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        } catch (error: Exception) {
            Timber.w(error, "Automotive artwork encode failed")
            temporary.delete()
            return null
        }
        if (!written) {
            temporary.delete()
            return null
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            return null
        }
        trim(directory)
        return file
    }

    private fun fetchBitmap(context: Context, url: String): Bitmap? {
        val network = runCatching {
            runBlocking { decode(context, url, allowNetwork = true) }
        }.getOrNull()
        if (network != null) return network
        return runCatching {
            runBlocking { decode(context, url, allowNetwork = false) }
        }.getOrNull()
    }

    private suspend fun decode(context: Context, url: String, allowNetwork: Boolean): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(IMAGE_SIZE, IMAGE_SIZE)
            .networkCachePolicy(if (allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result !is SuccessResult) return null
        return result.image.toBitmap(IMAGE_SIZE, IMAGE_SIZE)
    }

    private fun trim(directory: File) {
        val files = directory.listFiles { file -> file.isFile }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        files.sortBy { it.lastModified() }
        for (file in files) {
            if (total <= TARGET_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun signature(context: Context, url: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret(context), "HmacSHA256"))
        val digest = mac.doFinal(url.toByteArray(Charsets.UTF_8))
        return digest.take(SIGNATURE_BYTES).joinToString(separator = "") { "%02x".format(it) }
    }

    private fun secret(context: Context): ByteArray {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.getString(KEY_SECRET, null)?.let { stored ->
            runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull()?.let { return it }
        }
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        preferences.edit()
            .putString(KEY_SECRET, Base64.encodeToString(generated, Base64.NO_WRAP))
            .apply()
        return generated
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}

fun MediaMetadata.withAutomotiveArtworkUri(context: Context): MediaMetadata {
    val mapped = AutomotiveArtwork.contentUriFor(context, artworkUri) ?: return this
    return buildUpon().setArtworkUri(mapped).build()
}

fun MediaItem.withAutomotiveArtworkUri(context: Context): MediaItem {
    val metadata = mediaMetadata
    val mapped = metadata.withAutomotiveArtworkUri(context)
    if (mapped === metadata) return this
    return buildUpon().setMediaMetadata(mapped).build()
}
