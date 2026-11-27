package com.revenge.music.utils

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult

/**
 * Retries YouTube thumbnail requests at progressively smaller resolutions when the largest
 * variant is not published for a video.
 */
class YouTubeThumbnailFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result !is ErrorResult) return result

        val current = chain.request.data as? String ?: return result
        val fallback = fallbackFor(current) ?: return result

        val request = chain.request.newBuilder().data(fallback).build()
        return chain.withRequest(request).proceed()
    }

    private fun fallbackFor(url: String): String? {
        if (!url.contains("i.ytimg.com") && !url.contains("img.youtube.com")) return null
        return when {
            url.contains("maxresdefault") -> url.replace("maxresdefault", "hqdefault")
            url.contains("sddefault") -> url.replace("sddefault", "hqdefault")
            url.contains("hqdefault") -> url.replace("hqdefault", "mqdefault")
            else -> null
        }
    }
}
