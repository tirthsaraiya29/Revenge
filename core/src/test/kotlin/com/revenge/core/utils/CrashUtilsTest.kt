package com.revenge.core.utils

import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashUtilsTest {

    @After
    fun reset() {
        exceptionReporter = null
    }

    @Test
    fun registeredReporterIsInvokedOnce() {
        var calls = 0
        var received: Throwable? = null
        exceptionReporter = { error ->
            calls++
            received = error
        }

        val failure = IOException("boom")
        reportException(failure)

        assertEquals(1, calls)
        assertEquals(failure, received)
    }

    @Test
    fun missingReporterDoesNotThrow() {
        exceptionReporter = null
        reportException(IllegalStateException("ignored"))
    }
}
