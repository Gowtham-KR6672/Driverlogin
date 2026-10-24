package com.example.drivertracking.data.remote

import com.example.drivertracking.util.ServerStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private val serverStatus = ServerStatus()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(backoffMillis: List<Long> = listOf(10, 10, 10), slowRequestMillis: Long = 5_000) =
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(serverStatus, backoffMillis, slowRequestMillis))
            .build()

    private fun get(policy: String? = null) = Request.Builder().url(server.url("/api/home"))
        .apply { if (policy != null) header(RetryInterceptor.HEADER, policy) }
        .build()

    private fun post(policy: String? = null) = Request.Builder().url(server.url("/api/work-entry/start"))
        .post("{}".toRequestBody("application/json".toMediaType()))
        .apply { if (policy != null) header(RetryInterceptor.HEADER, policy) }
        .build()

    private fun enqueue(vararg codes: Int) = codes.forEach { server.enqueue(MockResponse().setResponseCode(it)) }

    @Test
    fun get_retriesGatewayErrorsUntilTheServerAnswers() {
        enqueue(503, 502, 504, 200)

        client().newCall(get()).execute().use { assertEquals(200, it.code) }

        assertEquals(4, server.requestCount)
        assertFalse(serverStatus.wakingUp.value)
    }

    @Test
    fun get_givesUpAfterTheLastBackoff() {
        enqueue(503, 503, 503, 503)

        client().newCall(get()).execute().use { assertEquals(503, it.code) }

        assertEquals(4, server.requestCount)
        assertFalse(serverStatus.wakingUp.value)
    }

    @Test
    fun get_doesNotRetryOtherErrors() {
        enqueue(500, 200)

        client().newCall(get()).execute().use { assertEquals(500, it.code) }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun post_retriesOnlyServerStarting503() {
        enqueue(503, 200)
        client().newCall(post()).execute().use { assertEquals(200, it.code) }
        assertEquals(2, server.requestCount)

        // A 502 or 504 may come after Flask handled the request, so it isn't sent again.
        enqueue(502, 200)
        client().newCall(post()).execute().use { assertEquals(502, it.code) }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun safePost_retriesGatewayErrorsLikeGet() {
        enqueue(502, 504, 200)

        client().newCall(post(RetryInterceptor.SAFE_TO_REPEAT)).execute().use { assertEquals(200, it.code) }

        assertEquals(3, server.requestCount)
    }

    @Test
    fun noRetry_returnsTheFirstResponseAndDoesNotShowTheBanner() {
        enqueue(503, 200)

        client(slowRequestMillis = 10).newCall(post(RetryInterceptor.NO_RETRY)).execute()
            .use { assertEquals(503, it.code) }

        assertEquals(1, server.requestCount)
        assertFalse(serverStatus.wakingUp.value)
    }

    @Test
    fun policyHeaderIsNotSentToTheServer() {
        enqueue(200)

        client().newCall(post(RetryInterceptor.SAFE_TO_REPEAT)).execute().close()

        assertNull(server.takeRequest().getHeader(RetryInterceptor.HEADER))
    }

    @Test
    fun wakingUpIsShownWhileRetrying() {
        val seenWhileRetrying = mutableListOf<Boolean>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seenWhileRetrying += serverStatus.wakingUp.value
                return MockResponse().setResponseCode(if (seenWhileRetrying.size == 1) 503 else 200)
            }
        }

        client().newCall(get()).execute().close()

        assertEquals(listOf(false, true), seenWhileRetrying)
        assertFalse(serverStatus.wakingUp.value)
    }

    @Test
    fun wakingUpIsShownForASlowRequest() {
        val seenWhileWaiting = mutableListOf<Boolean>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(300)
                seenWhileWaiting += serverStatus.wakingUp.value
                return MockResponse().setResponseCode(200)
            }
        }

        client(slowRequestMillis = 50).newCall(get()).execute().close()

        assertEquals(listOf(true), seenWhileWaiting)
        assertFalse(serverStatus.wakingUp.value)
    }

    @Test
    fun cancelingTheCallStopsTheBackoff() {
        enqueue(503, 200)
        val call = client(backoffMillis = listOf(10_000)).newCall(get())
        Thread { Thread.sleep(300); call.cancel() }.start()

        val started = System.nanoTime()
        try {
            call.execute().close()
            fail("Expected the canceled call to throw")
        } catch (expected: IOException) {
        }

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000)
        assertEquals(1, server.requestCount)
        assertFalse(serverStatus.wakingUp.value)
    }
}
