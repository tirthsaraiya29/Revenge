package com.revenge.music.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterStreamingServiceTest {

    @After
    fun tearDown() {
        OpenRouterStreamingService.clientOverride = null
    }

    private fun bodyOf(request: io.ktor.client.request.HttpRequestData): JsonObject =
        aiJson.parseToJsonElement((request.body as TextContent).text).jsonObject

    private fun clientWithBody(body: String): HttpClient = HttpClient(MockEngine {
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
        )
    })

    @Test
    fun `valid frames produce content chunks and a complete list`() = runTest {
        val body = """
            data: {"choices":[{"delta":{"content":"[\"a\","}}]}
            data: {"choices":[{"delta":{"content":"\"b\"]"}}]}
            data: [DONE]
        """.trimIndent()
        OpenRouterStreamingService.clientOverride = clientWithBody(body)

        val chunks = OpenRouterStreamingService.streamTranslation("a\nb", "es", "key", "", "m", "Literal").toList()

        val content = chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Content>().map { it.text }
        assertEquals(listOf("[\"a\",", "\"b\"]"), content)
        assertEquals(
            listOf("a", "b"),
            chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Complete>().single().translatedLines,
        )
    }

    @Test
    fun `non data lines are ignored`() = runTest {
        val body = """
            : keep-alive
            event: message
            data: {"choices":[{"delta":{"content":"[\"a\"]"}}]}

            data: [DONE]
        """.trimIndent()
        OpenRouterStreamingService.clientOverride = clientWithBody(body)

        val chunks = OpenRouterStreamingService.streamTranslation("a", "es", "key", "", "m", "Literal").toList()

        assertEquals(listOf("a"), chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Complete>().single().translatedLines)
    }

    @Test
    fun `malformed frame is skipped`() = runTest {
        val body = """
            data: {"choices":[{"delta":{"content":"[\"a\","}}]}
            data: not-json
            data: {"choices":[{"delta":{"content":"\"b\"]"}}]}
            data: [DONE]
        """.trimIndent()
        OpenRouterStreamingService.clientOverride = clientWithBody(body)

        val chunks = OpenRouterStreamingService.streamTranslation("a\nb", "es", "key", "", "m", "Literal").toList()

        assertEquals(listOf("a", "b"), chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Complete>().single().translatedLines)
    }

    @Test
    fun `http error emits a single error without retrying`() = runTest {
        val calls = AtomicInteger()
        OpenRouterStreamingService.clientOverride = HttpClient(MockEngine {
            calls.incrementAndGet()
            respond(
                """{"error":{"message":"Forbidden"}}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val chunks = OpenRouterStreamingService.streamTranslation("a", "es", "key", "", "m", "Literal").toList()

        assertEquals(1, calls.get())
        assertEquals(
            listOf("Translation failed: Forbidden"),
            chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Error>().map { it.message },
        )
    }

    @Test
    fun `no deltas emits no terminal chunk`() = runTest {
        OpenRouterStreamingService.clientOverride = clientWithBody("data: [DONE]\n")

        val chunks = OpenRouterStreamingService.streamTranslation("a", "es", "key", "", "m", "Literal").toList()

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `unparseable accumulated content reports a parse failure`() = runTest {
        val body = """
            data: {"choices":[{"delta":{"content":"not an array"}}]}
            data: [DONE]
        """.trimIndent()
        OpenRouterStreamingService.clientOverride = clientWithBody(body)

        val chunks = OpenRouterStreamingService.streamTranslation("a", "es", "key", "", "m", "Literal").toList()

        assertEquals(
            listOf("Failed to parse translation"),
            chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Error>().map { it.message },
        )
    }

    @Test
    fun `blank text is rejected without a request`() = runTest {
        val calls = AtomicInteger()
        OpenRouterStreamingService.clientOverride = HttpClient(MockEngine {
            calls.incrementAndGet()
            respond("")
        })

        val chunks = OpenRouterStreamingService.streamTranslation("  ", "es", "key", "", "m", "Literal").toList()

        assertEquals(0, calls.get())
        assertEquals(
            listOf("Input text is empty"),
            chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Error>().map { it.message },
        )
    }

    @Test
    fun `unsafe url is rejected without a request`() = runTest {
        val calls = AtomicInteger()
        OpenRouterStreamingService.clientOverride = HttpClient(MockEngine {
            calls.incrementAndGet()
            respond("")
        })

        val chunks = OpenRouterStreamingService.streamTranslation(
            "a", "es", "key", "file:///etc/passwd", "m", "Literal",
        ).toList()

        assertEquals(0, calls.get())
        assertEquals(
            listOf("Invalid or restricted endpoint destination"),
            chunks.filterIsInstance<OpenRouterStreamingService.StreamChunk.Error>().map { it.message },
        )
    }

    @Test
    fun `request body uses the openai streaming shape`() = runTest {
        var captured: JsonObject? = null
        var authorization: String? = null
        OpenRouterStreamingService.clientOverride = HttpClient(MockEngine { request ->
            captured = bodyOf(request)
            authorization = request.headers[HttpHeaders.Authorization]
            respond(ByteReadChannel("data: [DONE]\n"))
        })

        OpenRouterStreamingService.streamTranslation("a\nb", "es", "key", "", "m", "Literal").toList()

        val body = captured!!
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertEquals("m", body["model"]!!.jsonPrimitive.content)
        assertEquals(2, body["messages"]!!.jsonArray.size)
        assertEquals("Bearer key", authorization)
        assertEquals(200, body["max_tokens"]!!.jsonPrimitive.content.toInt())
    }
}
