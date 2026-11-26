package com.revenge.artistvideo

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.util.concurrent.TimeUnit

internal data class ArtistVideoHttpResponse(val status: Int, val body: String)

internal fun interface ArtistVideoTransport {
    suspend fun get(
        target: String,
        params: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): ArtistVideoHttpResponse
}

internal fun ktorArtistVideoTransport(): ArtistVideoTransport = object : ArtistVideoTransport {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
                callTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    override suspend fun get(
        target: String,
        params: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): ArtistVideoHttpResponse {
        val response = client.get(target) {
            params.forEach { (key, value) -> parameter(key, value) }
            headers.forEach { (key, value) -> header(key, value) }
        }
        return ArtistVideoHttpResponse(response.status.value, response.bodyAsText())
    }
}
