package com.example.drivertracking.util

import java.io.InterruptedIOException

// Shown when the server is still starting after RetryInterceptor's retries (about a minute).
const val SERVER_WAKING_UP_MESSAGE = "The server is waking up. Please try again in a minute."
const val SERVER_TIMEOUT_MESSAGE = "The server took too long to respond. It may be waking up, please try again in a minute."

/** Message for an unsuccessful HTTP response; [fallback] unless the server is starting up. */
fun serverErrorMessage(code: Int, fallback: String = "Server error: $code"): String =
    if (code in 502..504) SERVER_WAKING_UP_MESSAGE else fallback

/** Message for a request that got no response; [fallback] unless it timed out. */
fun networkErrorMessage(e: Exception, fallback: String = "Network error: ${e.message}"): String =
    if (e is InterruptedIOException) SERVER_TIMEOUT_MESSAGE else fallback
