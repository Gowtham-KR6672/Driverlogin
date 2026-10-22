package com.example.drivertracking.ui.work

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivertracking.data.remote.ContinueWorkRequest
import com.example.drivertracking.data.remote.EndWorkRequest
import com.example.drivertracking.data.remote.LocationApi
import com.example.drivertracking.data.remote.StartWorkRequest
import com.example.drivertracking.data.remote.WorkEntry
import com.example.drivertracking.util.CallSummary
import com.example.drivertracking.util.CallLogCounter
import com.example.drivertracking.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WorkUiState(
    val isLoading: Boolean = false,
    val hasActiveWork: Boolean = false,
    val activeEntry: WorkEntry? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class WorkViewModel @Inject constructor(
    private val api: LocationApi,
    private val sessionManager: SessionManager,
    private val callLogCounter: CallLogCounter
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkUiState())
    val uiState: StateFlow<WorkUiState> = _uiState.asStateFlow()

    init {
        fetchActiveWork()
    }

    fun fetchActiveWork() {
        val driverId = sessionManager.getDriverId()
        if (driverId == -1) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val response = api.getActiveWork(driverId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            hasActiveWork = body.hasActiveWork,
                            activeEntry = body.entry
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = body?.message)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Server error")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error: ${e.message}")
            }
        }
    }

    fun startWork(person: String, vehicle: String, remarks: String, location: Location?) {
        val driverId = sessionManager.getDriverId()
        if (driverId == -1) return

        if (location == null) {
            _uiState.value = _uiState.value.copy(error = "Location is required to start work")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val request = StartWorkRequest(
                    driverId = driverId,
                    workGivenPerson = person,
                    vehicleType = vehicle,
                    remarks = remarks,
                    startLat = location.latitude,
                    startLng = location.longitude
                )
                val response = api.startWork(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        fetchActiveWork()
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = body?.message)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Server error")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error")
            }
        }
    }

    // Calls made and received since the active entry started (null if not allowed/known).
    private suspend fun callsForActiveEntry(): CallSummary? = withContext(Dispatchers.IO) {
        callLogCounter.summarizeSince(_uiState.value.activeEntry?.startEpochMs)
    }

    fun endWork(location: Location?) {
        val driverId = sessionManager.getDriverId()
        if (driverId == -1) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val calls = callsForActiveEntry()
                val request = EndWorkRequest(
                    driverId = driverId,
                    endLat = location?.latitude,
                    endLng = location?.longitude,
                    remarks = null,
                    incomingCalls = calls?.counts?.incoming,
                    outgoingCalls = calls?.counts?.outgoing,
                    missedCalls = calls?.counts?.missed,
                    callEvents = calls?.events
                )
                val response = api.endWork(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        fetchActiveWork()
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = body?.message)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Server error")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error")
            }
        }
    }

    /** Ends the current work here and starts the next one for [person] from the same spot. */
    fun continueWork(person: String, remarks: String, location: Location?) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val calls = callsForActiveEntry()
                val request = ContinueWorkRequest(
                    workGivenPerson = person,
                    remarks = remarks,
                    lat = location?.latitude,
                    lng = location?.longitude,
                    accuracy = location?.accuracy,
                    incomingCalls = calls?.counts?.incoming,
                    outgoingCalls = calls?.counts?.outgoing,
                    missedCalls = calls?.counts?.missed,
                    callEvents = calls?.events
                )
                val response = api.continueWork(request)
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    fetchActiveWork()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = body?.message ?: "Could not continue with $person"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error")
            }
        }
    }
}
