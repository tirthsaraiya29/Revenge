package com.revenge.music.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/**
 * Shared engines for the AI translation services. The regular client allows long completions; the
 * streaming client uses a shorter socket budget so a stalled SSE connection cannot hang forever.
 */
internal val aiHttpClient: HttpClient by lazy {
  HttpClient(OkHttp) {
    install(HttpTimeout) {
      connectTimeoutMillis = 30_000
      socketTimeoutMillis = 90_000
      requestTimeoutMillis = 90_000
    }
  }
}

internal val aiStreamingHttpClient: HttpClient by lazy {
  HttpClient(OkHttp) {
    install(HttpTimeout) {
      connectTimeoutMillis = 30_000
      socketTimeoutMillis = 45_000
      requestTimeoutMillis = 45_000
    }
  }
}
