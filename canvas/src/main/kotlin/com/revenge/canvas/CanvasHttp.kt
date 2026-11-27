package com.revenge.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.util.concurrent.TimeUnit

internal data class CanvasHttpResponse(val status: Int, val body: String)

internal interface CanvasTransport {
  suspend fun get(
    target: String,
    params: List<Pair<String, String>>,
    headers: Map<String, String>,
  ): CanvasHttpResponse
}

internal fun ktorCanvasTransport(
  connectTimeoutSeconds: Long,
  requestTimeoutSeconds: Long,
  readTimeoutSeconds: Long,
): CanvasTransport =
  object : CanvasTransport {
    private val client =
      HttpClient(OkHttp) {
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
    ): CanvasHttpResponse {
      val response =
        client.get(target) {
          params.forEach { (key, value) -> parameter(key, value) }
          headers.forEach { (key, value) -> header(key, value) }
        }
      return CanvasHttpResponse(response.status.value, response.bodyAsText())
    }
  }
