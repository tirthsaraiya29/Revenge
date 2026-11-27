package com.revenge.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small synchronous bitmap loader on top of Coil, used where a `Bitmap` is required directly
 * (notifications, widgets, artwork caches). Results other than hardware bitmaps are memoised.
 */
object CoilBitmapLoader {

    private const val CACHE_ENTRIES = 64

    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    suspend fun load(
        context: Context,
        data: Any?,
        width: Int = 0,
        height: Int = 0,
    ): Bitmap? {
        if (data == null) return null
        val key = "${data}|${width}x$height"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(data)
                .apply {
                    if (width > 0 && height > 0) size(width, height)
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
            val result = runCatching { SingletonImageLoader.get(context).execute(request) }
                .getOrNull() as? SuccessResult ?: return@withContext null
            val bitmap = result.drawable.toBitmap(width.takeIf { it > 0 } ?: 0, height.takeIf { it > 0 } ?: 0)
            val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            cache.put(key, software)
            software
        }
    }

    suspend fun loadBitmap(
        context: Context,
        data: Any?,
        width: Int = 0,
        height: Int = 0,
    ): Bitmap? = load(context, data, width, height)

    fun clear() = cache.evictAll()
}
