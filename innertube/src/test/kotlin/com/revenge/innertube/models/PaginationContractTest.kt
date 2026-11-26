package com.revenge.innertube.models

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PaginationContractTest {
    private data class Page<T>(val items: List<T>, val token: String?)

    private data class Aggregate<T>(val items: List<T>, val continuation: String?)

    private val maxPages = 50

    private fun <T> walk(first: Page<T>, fetch: (String) -> Page<T>): Result<Aggregate<T>> {
        val collected = first.items.toMutableList()
        val visited = mutableSetOf<String>()
        var token = first.token
        var fetches = 0
        var consecutiveEmpty = 0
        try {
            while (token != null && token !in visited && fetches < maxPages) {
                visited.add(token)
                val page = fetch(token)
                fetches++
                collected.addAll(page.items)
                consecutiveEmpty = if (page.items.isEmpty()) consecutiveEmpty + 1 else 0
                if (consecutiveEmpty >= 2) {
                    token = null
                    break
                }
                token = page.token
            }
            return Result.success(Aggregate(collected, null))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return Result.failure(failure)
        }
    }

    private fun <T> nextContinuation(items: List<T>, wireToken: String?): String? =
        if (items.isEmpty()) null else wireToken

    @Test
    fun p1WalksUntilNullToken() {
        var fetches = 0
        val result = walk(Page(emptyList<String>(), "A")) { token ->
            fetches++
            when (token) {
                "A" -> Page(listOf("s1"), "B")
                "B" -> Page(emptyList(), null)
                else -> error("unexpected token $token")
            }
        }
        assertEquals(listOf("s1"), result.getOrThrow().items)
        assertNull(result.getOrThrow().continuation)
        assertEquals(2, fetches)
    }

    @Test
    fun p2RepeatedTokenStops() {
        var fetches = 0
        val result = walk(Page(emptyList<String>(), "A")) {
            fetches++
            Page(listOf("s1"), "A")
        }
        assertEquals(listOf("s1"), result.getOrThrow().items)
        assertEquals(1, fetches)
    }

    @Test
    fun p3TwoConsecutiveEmptyPagesStop() {
        var fetches = 0
        val result = walk(Page(emptyList<String>(), "A")) { token ->
            fetches++
            when (token) {
                "A" -> Page(emptyList(), "B")
                "B" -> Page(emptyList(), "C")
                else -> error("should not fetch $token")
            }
        }
        assertEquals(2, fetches)
        assertTrue(result.getOrThrow().items.isEmpty())
    }

    @Test
    fun p4EmptyThenNonEmptyResetsCounter() {
        val result = walk(Page(emptyList<String>(), "A")) { token ->
            when (token) {
                "A" -> Page(emptyList(), "B")
                "B" -> Page(listOf("s2"), null)
                else -> error("unexpected token $token")
            }
        }
        assertEquals(listOf("s2"), result.getOrThrow().items)
    }

    @Test
    fun p5AtMostFiftyFetches() {
        var fetches = 0
        val result = walk(Page(emptyList<Int>(), "t0")) { token ->
            fetches++
            val next = "t${token.drop(1).toInt() + 1}"
            Page(listOf(fetches), next)
        }
        assertEquals(maxPages, fetches)
        assertTrue(result.isSuccess)
    }

    @Test
    fun p6TransientFailureFailsAggregate() {
        val result = walk(Page(emptyList<String>(), "A")) {
            throw IllegalStateException("boom")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun p7CancellationIsRethrown() {
        try {
            walk(Page(emptyList<String>(), "A")) {
                throw CancellationException("cancelled")
            }
            fail("expected CancellationException")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun p8NullFirstTokenFetchesNothing() {
        var fetches = 0
        val result = walk(Page(listOf("s0"), null)) {
            fetches++
            error("should not fetch")
        }
        assertEquals(listOf("s0"), result.getOrThrow().items)
        assertNull(result.getOrThrow().continuation)
        assertEquals(0, fetches)
    }

    @Test
    fun p9EmptyItemsDropWireToken() {
        assertNull(nextContinuation(emptyList<String>(), "wire-token"))
        assertEquals("wire-token", nextContinuation(listOf("s"), "wire-token"))
    }
}
