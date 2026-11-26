package com.revenge.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import com.revenge.music.models.MediaMetadata
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * Maps remote artwork URLs to signed, read-only content URIs so other processes (Android Auto,
 * Automotive OS, clusters) can load covers through the exported provider.
 */
object AutomotiveArtwork {

  private const val PREFS = "automotive_artwork_prefs"
  private const val SECRET_KEY = "uri_secret"
  private const val DIR = "automotive_artwork"
  private const val MAX_BYTES = 64L * 1024 * 1024
  private const val TRIM_BYTES = 48L * 1024 * 1024

  private val mappingCache = ConcurrentHashMap<String, Uri>()

  /** Test seam; returns the decoded cover for a URL. */
  internal var fetchBitmap: (String) -> Bitmap? = { url ->
    runBlocking(Dispatchers.IO) { loadWithCoil(url) }
  }

  fun authority(context: Context): String = "${context.packageName}.artwork"

  fun contentUriFor(context: Context, uri: Uri?): Uri? {
    if (uri == null) return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return uri
    val url = uri.toString()
    mappingCache[url]?.let { return it }
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
    val signature = signature(context, url)
    val mapped =
      Uri.Builder()
        .scheme("content")
        .authority(authority(context))
        .appendPath("art")
        .appendPath(encoded)
        .appendPath(signature)
        .build()
    if (mappingCache.size > 1_024) mappingCache.clear()
    mappingCache[url] = mapped
    return mapped
  }

  fun originalUrl(context: Context, uri: Uri): String? {
    if (uri.scheme != "content") return null
    if (uri.authority != authority(context)) return null
    val segments = uri.pathSegments
    if (segments.size != 3 || segments[0] != "art") return null
    val url =
      runCatching {
        String(Base64.getUrlDecoder().decode(segments[1]), Charsets.UTF_8)
      }.getOrNull() ?: return null
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val expected = signature(context, url)
    val matches =
      MessageDigest.isEqual(
        segments[2].toByteArray(Charsets.UTF_8),
        expected.toByteArray(Charsets.UTF_8),
      )
    return if (matches) url else null
  }

  fun artworkFile(context: Context, url: String): File? {
    val directory = File(context.cacheDir, DIR)
    if (!directory.exists() && !directory.mkdirs()) return null
    val file = File(directory, sha256Hex(url) + ".jpg")
    if (file.exists() && file.length() > 0) {
      file.setLastModified(System.currentTimeMillis())
      return file
    }
    applicationContext = context.applicationContext
    val bitmap = runCatching { fetchBitmap(url) }.getOrNull() ?: return null
    val encoded =
      runCatching {
        java.io.ByteArrayOutputStream().use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
          output.toByteArray()
        }
      }.getOrNull() ?: return null
    if (encoded.isEmpty()) return null
    val temp = File(directory, "${file.name}.tmp")
    return try {
      temp.writeBytes(encoded)
      if (file.exists()) file.delete()
      if (!temp.renameTo(file)) {
        temp.copyTo(file, overwrite = true)
        temp.delete()
      }
      file.setLastModified(System.currentTimeMillis())
      trim(directory)
      file
    } catch (e: Throwable) {
      temp.delete()
      null
    }
  }

  private fun trim(directory: File) {
    val files = directory.listFiles()?.filter { it.isFile } ?: return
    var total = files.sumOf { it.length() }
    if (total <= MAX_BYTES) return
    files.sortedBy { it.lastModified() }.forEach { file ->
      if (total <= TRIM_BYTES) return@forEach
      val length = file.length()
      if (file.delete()) total -= length
    }
  }

  private fun signature(context: Context, url: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret(context), "HmacSHA256"))
    val digest = mac.doFinal(url.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }

  private fun secret(context: Context): ByteArray {
    val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val stored = preferences.getString(SECRET_KEY, null)
    if (stored != null) {
      runCatching { return Base64.getDecoder().decode(stored) }
    }
    val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
    preferences.edit().putString(SECRET_KEY, Base64.getEncoder().encodeToString(generated)).apply()
    return generated
  }

  internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  private suspend fun loadWithCoil(url: String): Bitmap? {
    val request =
      coil3.request.ImageRequest.Builder(currentContext())
        .data(url)
        .size(1024)
        .allowHardware(false)
        .build()
    val result =
      withTimeoutOrNull(10_000L) { currentContext().imageLoader.execute(request) }
        ?: return null
    return when (result) {
      is coil3.request.SuccessResult -> runCatching { result.image.toBitmap() }.getOrNull()
      is coil3.request.ErrorResult -> {
        val cached =
          request.newBuilder()
            .networkCachePolicy(coil3.request.CachePolicy.READ_ONLY)
            .build()
        val fallback = withTimeoutOrNull(10_000L) { currentContext().imageLoader.execute(cached) }
        (fallback as? coil3.request.SuccessResult)?.let {
          runCatching { it.image.toBitmap() }.getOrNull()
        }
      }
      else -> null
    }
  }

  /** Set by the provider while it fetches; keeps the Coil call independent of composition. */
  @Volatile internal var applicationContext: Context? = null

  private fun currentContext(): Context =
    applicationContext ?: throw IllegalStateException("Artwork context not initialised")

  internal fun downloadBitmap(context: Context, url: String): Bitmap? =
    runBlocking(Dispatchers.IO) {
      applicationContext = context.applicationContext
      loadWithCoil(url)
    }
}

fun MediaMetadata.withAutomotiveArtworkUri(context: Context): MediaMetadata {
  val original = thumbnailUrl ?: return this
  val mapped = AutomotiveArtwork.contentUriFor(context, Uri.parse(original)) ?: return this
  if (mapped.toString() == original) return this
  return copy(thumbnailUrl = mapped.toString())
}

fun Media3Metadata.withAutomotiveArtworkUri(context: Context): Media3Metadata {
  val artwork = artworkUri ?: return this
  val mapped = AutomotiveArtwork.contentUriFor(context, artwork) ?: return this
  if (mapped == artwork) return this
  return buildUpon().setArtworkUri(mapped).build()
}

fun MediaItem.withAutomotiveArtworkUri(context: Context): MediaItem {
  val artwork = mediaMetadata.artworkUri ?: return this
  val mapped = AutomotiveArtwork.contentUriFor(context, artwork) ?: return this
  if (mapped == artwork) return this
  val metadata: Media3Metadata =
    mediaMetadata.buildUpon().setArtworkUri(mapped).build()
  return buildUpon().setMediaMetadata(metadata).build()
}
