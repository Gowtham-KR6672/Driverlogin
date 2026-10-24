package com.example.drivertracking.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ErrorMessagesTest {

    @Test
    fun gatewayErrorsSayTheServerIsWakingUp() {
        listOf(502, 503, 504).forEach { assertEquals(SERVER_WAKING_UP_MESSAGE, serverErrorMessage(it)) }
    }

    @Test
    fun otherServerErrorsKeepTheirMessage() {
        assertEquals("Server error: 500", serverErrorMessage(500))
        assertEquals("Server error", serverErrorMessage(500, "Server error"))
    }

    @Test
    fun timeoutsSayTheServerMayBeWakingUp() {
        assertEquals(SERVER_TIMEOUT_MESSAGE, networkErrorMessage(SocketTimeoutException("timeout")))
    }

    @Test
    fun otherNetworkErrorsKeepTheirMessage() {
        assertEquals("Network error: no route", networkErrorMessage(IOException("no route")))
        assertEquals("Network error", networkErrorMessage(IOException("no route"), "Network error"))
    }
}
