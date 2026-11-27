package com.revenge.lyrics.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceHttpTest {

  @After
  fun tearDown() {
    DeepLService.clientOverride = null
    MistralService.clientOverride = null
    OpenRouterService.clientOverride = null
  }

  private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

  private fun bodyOf(request: io.ktor.client.request.HttpRequestData): JsonObject =
    aiJson.parseToJsonElement((request.body as TextContent).text).jsonObject

  @Test
  fun `deepl blank text fails without a request`() = runTest {
    val calls = AtomicInteger()
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("{}")
        },
      )

    val result = DeepLService.translate("   ", "EN", "key")

    assertEquals("Input text is empty", result.exceptionOrNull()?.message)
    assertEquals(0, calls.get())
  }

  @Test
  fun `deepl success uses the free endpoint for fx keys`() = runTest {
    var requestedUrl = ""
    DeepLService.clientOverride =
      HttpClient(
        MockEngine { request ->
          requestedUrl = request.url.toString()
          respond(
            """{"translations":[{"text":"bonjour","detected_source_language":"EN"}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = DeepLService.translate("Hello", "fr", "key:fx")

    assertEquals(listOf("bonjour"), result.getOrNull())
    assertTrue(requestedUrl.contains("api-free"))
  }

  @Test
  fun `deepl 403 fails once with the body message`() = runTest {
    val calls = AtomicInteger()
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("""{"message":"Forbidden"}""", HttpStatusCode.Forbidden, jsonHeaders())
        },
      )

    val result = DeepLService.translate("Hello", "fr", "key")

    assertEquals(1, calls.get())
    val message = result.exceptionOrNull()?.message.orEmpty()
    assertTrue(message.contains("Translation failed:"))
    assertTrue(message.contains("Forbidden"))
  }

  @Test
  fun `deepl 500 then 403 stops at the second attempt`() = runTest {
    val calls = AtomicInteger()
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          if (calls.getAndIncrement() == 0) {
            respond("{}", HttpStatusCode.InternalServerError)
          } else {
            respond("""{"message":"Forbidden"}""", HttpStatusCode.Forbidden, jsonHeaders())
          }
        },
      )

    val result = DeepLService.translate("Hello", "fr", "key")

    assertEquals(2, calls.get())
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Forbidden"))
  }

  @Test
  fun `deepl retries 5xx with growing backoff`() = runTest {
    val calls = AtomicInteger()
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("{}", HttpStatusCode.InternalServerError)
        },
      )

    val result = DeepLService.translate("Hello", "fr", "key")

    assertEquals(3, calls.get())
    assertEquals("Max retries exceeded", result.exceptionOrNull()?.message)
    assertEquals(6_000L, testScheduler.currentTime)
  }

  @Test
  fun `deepl empty translations are retried then fail`() = runTest {
    val calls = AtomicInteger()
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("""{"translations":[]}""", HttpStatusCode.OK, jsonHeaders())
        },
      )

    val result = DeepLService.translate("Hello", "fr", "key")

    assertEquals(3, calls.get())
    assertEquals("Max retries exceeded", result.exceptionOrNull()?.message)
  }

  @Test
  fun `deepl missing text leaves an empty line`() = runTest {
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          respond(
            """{"translations":[{"text":"a"},{"detected_source_language":"EN"}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = DeepLService.translate("x\ny", "fr", "key")
    assertEquals(listOf("a", ""), result.getOrNull())
  }

  @Test
  fun `deepl language code table`() {
    assertEquals("ZH", DeepLService.deeplLanguageCode("zh-tw"))
    assertEquals("EN-US", DeepLService.deeplLanguageCode("en-US"))
    assertEquals("EN-GB", DeepLService.deeplLanguageCode("en-gb"))
    assertEquals("PT-BR", DeepLService.deeplLanguageCode("pt-BR"))
    assertEquals("JA", DeepLService.deeplLanguageCode("ja-JP"))
    assertEquals("DE", DeepLService.deeplLanguageCode("de"))
  }

  @Test
  fun `mistral success sends the documented request shape`() = runTest {
    var captured: JsonObject? = null
    MistralService.clientOverride =
      HttpClient(
        MockEngine { request ->
          captured = bodyOf(request)
          respond(
            """{"choices":[{"message":{"content":"[\"hola\"]"}}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = MistralService.translate("Hello", "es", "key", "", "Literal")

    assertEquals(listOf("hola"), result.getOrNull())
    val body = captured!!
    assertEquals("mistral-small-latest", body["model"]!!.jsonPrimitive.content)
    assertEquals(1, body["messages"]!!.jsonArray.size)
    assertEquals("user", body["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
    assertEquals(0.3, body["temperature"]!!.jsonPrimitive.content.toDouble(), 0.0001)
    assertEquals(100, body["max_tokens"]!!.jsonPrimitive.int)
  }

  @Test
  fun `mistral 403 surfaces error message`() = runTest {
    val calls = AtomicInteger()
    MistralService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("""{"error":{"message":"bad key"}}""", HttpStatusCode.Forbidden, jsonHeaders())
        },
      )

    val result = MistralService.translate("Hello", "es", "key", "m", "Literal")

    assertEquals(1, calls.get())
    assertEquals("bad key", result.exceptionOrNull()?.message)
  }

  @Test
  fun `mistral unparseable content retries until exhaustion`() = runTest {
    val calls = AtomicInteger()
    MistralService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond(
            """{"choices":[{"message":{"content":"not an array"}}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = MistralService.translate("Hello", "es", "key", "m", "Literal")

    assertEquals(3, calls.get())
    assertEquals("Max retries exceeded", result.exceptionOrNull()?.message)
  }

  @Test
  fun `url safety matrix`() {
    assertTrue(OpenRouterService.isUrlSafe(""))
    assertTrue(OpenRouterService.isUrlSafe("https://openrouter.ai/api/v1/chat/completions"))
    assertTrue(OpenRouterService.isUrlSafe("http://localhost:11434/v1/chat/completions"))
    assertFalse(OpenRouterService.isUrlSafe("file:///etc/passwd"))
    assertFalse(OpenRouterService.isUrlSafe("http://169.254.169.254/latest"))
    assertFalse(OpenRouterService.isUrlSafe("http://metadata.google.internal/"))
    assertFalse(OpenRouterService.isUrlSafe("not a url"))
  }

  @Test
  fun `openrouter unsafe base url makes no request`() = runTest {
    val calls = AtomicInteger()
    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("{}")
        },
      )

    val result =
      OpenRouterService.translate("Hello", "es", "key", "file:///etc/passwd", "m", "Literal")

    assertTrue(result.exceptionOrNull() is SecurityException)
    assertEquals("Invalid or restricted endpoint destination", result.exceptionOrNull()?.message)
    assertEquals(0, calls.get())
  }

  @Test
  fun `openrouter reads choices message content`() = runTest {
    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine {
          respond(
            """{"choices":[{"message":{"content":"[\"hola\"]"}}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = OpenRouterService.translate("Hello", "es", "key", "", "m", "Literal")
    assertEquals(listOf("hola"), result.getOrNull())
  }

  @Test
  fun `anthropic endpoint uses its headers and top level system`() = runTest {
    var capturedBody: JsonObject? = null
    var apiKeyHeader: String? = null
    var versionHeader: String? = null

    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine { request ->
          apiKeyHeader = request.headers["x-api-key"]
          versionHeader = request.headers["anthropic-version"]
          capturedBody = bodyOf(request)
          respond(
            """{"content":[{"type":"text","text":"[\"hola\"]"}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result =
      OpenRouterService.translate(
        "Hello",
        "es",
        "secret",
        "https://api.anthropic.com/v1/messages",
        "claude",
        "Literal",
      )

    assertEquals(listOf("hola"), result.getOrNull())
    assertEquals("secret", apiKeyHeader)
    assertEquals("2023-06-01", versionHeader)
    assertTrue(capturedBody!!.containsKey("system"))
    assertEquals(1, capturedBody!!["messages"]!!.jsonArray.size)
  }

  @Test
  fun `fenced content is recovered`() = runTest {
    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine {
          respond(
            """{"choices":[{"message":{"content":"```json\n[\"a\",\"b\"]\n```"}}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = OpenRouterService.translate("x\ny", "es", "key", "", "m", "Literal")
    assertEquals(listOf("a", "b"), result.getOrNull())
  }

  @Test
  fun `unknown response fields are ignored`() = runTest {
    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine {
          respond(
            """{"unknown":true,"choices":[{"message":{"content":"[\"a\"]","role":"assistant"}}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = OpenRouterService.translate("x", "es", "key", "", "m", "Literal")
    assertEquals(listOf("a"), result.getOrNull())
  }

  @Test
  fun `test connection falls back to a fixed message`() = runTest {
    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine {
          respond("""{"choices":[{"message":{"content":""}}]}""", HttpStatusCode.OK, jsonHeaders())
        },
      )

    val result = OpenRouterService.testConnection("", "key", "m")
    assertEquals("Connection OK", result.getOrNull())
  }

  @Test
  fun `test connection surfaces the error message without retrying`() = runTest {
    val calls = AtomicInteger()
    OpenRouterService.clientOverride =
      HttpClient(
        MockEngine {
          calls.incrementAndGet()
          respond("""{"error":{"message":"nope"}}""", HttpStatusCode.Unauthorized, jsonHeaders())
        },
      )

    val result = OpenRouterService.testConnection("", "key", "m")

    assertEquals(1, calls.get())
    assertEquals("nope", result.exceptionOrNull()?.message)
  }

  @Test
  fun `exceptions are retried up to the maximum`() = runTest {
    val calls = AtomicInteger()
    DeepLService.clientOverride =
      HttpClient(
        MockEngine {
          if (calls.getAndIncrement() == 0) throw IOException("boom")
          respond(
            """{"translations":[{"text":"ok"}]}""",
            HttpStatusCode.OK,
            jsonHeaders(),
          )
        },
      )

    val result = DeepLService.translate("Hello", "fr", "key")
    assertEquals(listOf("ok"), result.getOrNull())
    assertEquals(2, calls.get())
  }
}
