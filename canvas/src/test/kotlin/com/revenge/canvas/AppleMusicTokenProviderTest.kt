package com.revenge.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CancellationException

class AppleMusicTokenProviderTest {
    private class FakeTransport : CanvasTransport {
        var handler: suspend (String) -> CanvasHttpResponse = { CanvasHttpResponse(404, "") }
        val targets = java.util.Collections.synchronizedList(mutableListOf<String>())

        override suspend fun get(
            target: String,
            params: List<Pair<String, String>>,
            headers: Map<String, String>,
        ): CanvasHttpResponse {
            targets += target
            return handler(target)
        }
    }

    private lateinit var transport: FakeTransport
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        AppleMusicTokenProvider.resetForTests()
        transport = FakeTransport()
        AppleMusicTokenProvider.transport = transport
        AppleMusicTokenProvider.clock = { now }
    }

    private fun jwt(issuer: String, expSeconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"iss":"$issuer","exp":$expSeconds}""".toByteArray())
        return "$header.$payload.c2ln"
    }

    private fun expSecondsAhead(seconds: Long): Long = now / 1000 + seconds

    private fun installBundle(vararg tokens: String) {
        val bundle = tokens.joinToString(";") { "var t=\"$it\"" }
        transport.handler = { target ->
            when {
                target.endsWith(".js") -> CanvasHttpResponse(200, bundle)
                target.contains("music.apple.com") -> CanvasHttpResponse(
                    200,
                    """<html><script src="/assets/index-abc.js"></script></html>""",
                )
                else -> CanvasHttpResponse(404, "")
            }
        }
    }

    @Test
    fun tk1_picksFurthestValidExpiry() = runBlocking {
        val sooner = jwt("AMPWebPlay", expSecondsAhead(3600))
        val later = jwt("AMPWebPlay", expSecondsAhead(7200))
        installBundle(sooner, later)

        assertEquals(later, AppleMusicTokenProvider.getToken())
    }

    @Test
    fun tk2_tokenInsideHeadroomIsIgnoredForFallback() = runBlocking {
        val nearlyExpired = jwt("AMPWebPlay", expSecondsAhead(120))
        installBundle(nearlyExpired)

        var failure: Throwable? = null
        try {
            AppleMusicTokenProvider.getToken()
        } catch (e: IllegalStateException) {
            failure = e
        }
        assertNotNull("a token inside the headroom must not be served", failure)
    }

    @Test
    fun tk3_foreignIssuerIsIgnored() = runBlocking {
        val foreign = jwt("SomeOtherIssuer", expSecondsAhead(7200))
        installBundle(foreign)

        var failure: Throwable? = null
        try {
            AppleMusicTokenProvider.getToken()
        } catch (e: IllegalStateException) {
            failure = e
        }
        assertNotNull("a foreign-issuer token must not be served", failure)
    }

    @Test
    fun tk4_malformedTokensAreIgnored() = runBlocking {
        transport.handler = { target ->
            if (target.endsWith(".js")) {
                CanvasHttpResponse(200, "eyJabc x.y.z eyJhbGciOiJIUzI1NiJ9.only.two.parts")
            } else {
                CanvasHttpResponse(200, """<script src="/assets/index-abc.js"></script>""")
            }
        }

        var failure: Throwable? = null
        try {
            AppleMusicTokenProvider.getToken()
        } catch (e: IllegalStateException) {
            failure = e
        }
        assertNotNull("malformed-only discovery must not yield a token", failure)
    }

    @Test
    fun tk5_discoveryFailureIsReportedNotFaked() = runBlocking {
        transport.handler = { throw IOException("offline") }

        var failure: Throwable? = null
        try {
            AppleMusicTokenProvider.getToken()
        } catch (e: IllegalStateException) {
            failure = e
        }
        assertNotNull("discovery failure must not return a synthetic token", failure)
    }

    @Test
    fun tk6_concurrentColdCallsShareOneDiscovery() = runBlocking {
        installBundle(jwt("AMPWebPlay", expSecondsAhead(7200)))

        val first = async(Dispatchers.Default) { AppleMusicTokenProvider.getToken() }
        val second = async(Dispatchers.Default) { AppleMusicTokenProvider.getToken() }

        assertEquals(first.await(), second.await())
        assertEquals(1, transport.targets.count { it == "https://music.apple.com/us/browse" })
    }

    @Test
    fun tk7_expiryHeadroomTriggersRediscovery() = runBlocking {
        installBundle(jwt("AMPWebPlay", expSecondsAhead(3600)))
        AppleMusicTokenProvider.getToken()

        now += 3_500_000L
        installBundle(jwt("AMPWebPlay", expSecondsAhead(7200)))
        AppleMusicTokenProvider.getToken()

        assertEquals(2, transport.targets.count { it == "https://music.apple.com/us/browse" })
    }

    @Test
    fun tk8_clearTokenForcesRediscovery() = runBlocking {
        installBundle(jwt("AMPWebPlay", expSecondsAhead(7200)))
        AppleMusicTokenProvider.getToken()

        AppleMusicTokenProvider.clearToken()
        AppleMusicTokenProvider.getToken()

        assertEquals(2, transport.targets.count { it == "https://music.apple.com/us/browse" })
    }

    @Test
    fun tk9_cancellationIsRethrown() = runBlocking {
        val cancelled = CancellationException("stop")
        transport.handler = { throw cancelled }

        var caught: Throwable? = null
        try {
            AppleMusicTokenProvider.getToken()
        } catch (e: CancellationException) {
            caught = e
        }
        assertSame(cancelled, caught)
    }
}
