package com.example.drivertracking.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether any request is waiting on a server that is starting up. The backend runs on Render's
 * free plan, which sleeps when idle and takes about a minute to start again. AppNavigation shows
 * a "connecting to the server" banner while this is true. Updated by RetryInterceptor.
 */
@Singleton
class ServerStatus @Inject constructor() {

    private var waitingRequests = 0
    private val _wakingUp = MutableStateFlow(false)
    val wakingUp: StateFlow<Boolean> = _wakingUp.asStateFlow()

    @Synchronized
    fun startWaiting() {
        waitingRequests++
        _wakingUp.value = true
    }

    @Synchronized
    fun stopWaiting() {
        waitingRequests--
        _wakingUp.value = waitingRequests > 0
    }
}
