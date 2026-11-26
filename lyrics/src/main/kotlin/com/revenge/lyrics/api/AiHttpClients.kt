package com.revenge.lyrics.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal val aiHttpClient: HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 90_000
        requestTimeoutMillis = 90_000
    }
}

internal val aiStreamingHttpClient: HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 45_000
        requestTimeoutMillis = 45_000
    }
}
