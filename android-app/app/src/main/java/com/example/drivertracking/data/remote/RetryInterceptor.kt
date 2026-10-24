package com.example.drivertracking.data.remote

import com.example.drivertracking.util.ServerStatus
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Retries requests that fail because the server is starting up.
 *
 * The backend runs on Render's free plan: after 15 idle minutes it sleeps, and while it starts
 * again (about a minute) Render answers 502/503/504 or holds the request. Browsers get Render's
 * "waking up" page, so the website looks fine; without this the app showed "Server error: 503".
 *
 * Only repeats what is safe to send twice: GETs and login on 502/503/504, other POSTs only on
 * 503 (Render answers it without passing the request on; Flask itself never returns 503 here).
 * Location uploads are not retried: the tracking service queues failed points offline instead.
 * Endpoints choose another policy with the [HEADER] header, which is removed before sending.
 */
class RetryInterceptor(
    private val serverStatus: ServerStatus,
    private val backoffMillis: List<Long> = DEFAULT_BACKOFF_MILLIS,
    private val slowRequestMillis: Long = SLOW_REQUEST_MILLIS
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val policy = chain.request().header(HEADER)
        val request = chain.request().newBuilder().removeHeader(HEADER).build()

        if (policy == NO_RETRY) {
            // Fail as fast as before the longer timeouts, so the point is queued and tracking goes on.
            return chain
                .withConnectTimeout(QUICK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withReadTimeout(QUICK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withWriteTimeout(QUICK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .proceed(request)
        }

        val retryCodes = if (request.method == "GET" || policy == SAFE_TO_REPEAT) GATEWAY_ERRORS else SERVER_STARTING
        val waiting = WaitingFlag(serverStatus)
        // Render may hold a request until the server is up, so a slow answer also shows the banner.
        val slowTimer = scheduler.schedule(Runnable { waiting.set() }, slowRequestMillis, TimeUnit.MILLISECONDS)
        try {
            var attempt = 0
            while (true) {
                val response = chain.proceed(request)
                if (response.code !in retryCodes || attempt == backoffMillis.size) return response
                response.close()
                waiting.set()
                sleepUnlessCanceled(chain.call(), backoffMillis[attempt++])
            }
        } finally {
            slowTimer.cancel(false)
            waiting.clear()
        }
    }

    // Sleeps in short steps so a canceled call (screen closed, call timeout) stops retrying.
    private fun sleepUnlessCanceled(call: Call, millis: Long) {
        var left = millis
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")
            if (left <= 0) return
            val step = minOf(left, CANCEL_CHECK_MILLIS)
            try {
                Thread.sleep(step)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while waiting to retry")
            }
            left -= step
        }
    }

    /** Counts one request in [ServerStatus] at most once, from the first [set] until [clear]. */
    private class WaitingFlag(private val serverStatus: ServerStatus) {
        private var waiting = false
        private var done = false

        @Synchronized
        fun set() {
            if (done || waiting) return
            waiting = true
            serverStatus.startWaiting()
        }

        @Synchronized
        fun clear() {
            done = true
            if (waiting) {
                waiting = false
                serverStatus.stopWaiting()
            }
        }
    }

    companion object {
        const val HEADER = "X-Retry-Policy"
        /** Location uploads: no retries and short timeouts. */
        const val NO_RETRY = "none"
        /** POSTs without side effects (login): retried on 502/503/504 like GETs. */
        const val SAFE_TO_REPEAT = "safe"

        // About a minute in total, the time a sleeping Render free instance takes to start.
        val DEFAULT_BACKOFF_MILLIS = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
        const val SLOW_REQUEST_MILLIS = 5_000L

        private const val QUICK_TIMEOUT_SECONDS = 10
        private const val CANCEL_CHECK_MILLIS = 250L
        private val GATEWAY_ERRORS = setOf(502, 503, 504)
        private val SERVER_STARTING = setOf(503)

        private val scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "server-wait-timer").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
