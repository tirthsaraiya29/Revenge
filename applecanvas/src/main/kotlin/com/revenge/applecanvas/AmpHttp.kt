package com.revenge.applecanvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.util.concurrent.TimeUnit

internal data class AmpHttpResponse(val status: Int, val body: String)

internal interface AmpTransport {
    suspend fun get(
        target: String,
        params: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): AmpHttpResponse
}

internal fun ktorAmpTransport(
    connectTimeoutSeconds: Long,
    requestTimeoutSeconds: Long,
    readTimeoutSeconds: Long,
): AmpTransport = object : AmpTransport {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        engine {
            config {
                connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                callTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
            }
        }
    }

    override suspend fun get(
        target: String,
        params: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): AmpHttpResponse {
        val response = client.get(target) {
            params.forEach { (key, value) -> parameter(key, value) }
            headers.forEach { (key, value) -> header(key, value) }
        }
        return AmpHttpResponse(response.status.value, response.bodyAsText())
    }
}
