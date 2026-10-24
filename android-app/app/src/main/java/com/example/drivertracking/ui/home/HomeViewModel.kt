package com.example.drivertracking.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivertracking.data.remote.HomeStats
import com.example.drivertracking.data.remote.LocationApi
import com.example.drivertracking.util.SessionManager
import com.example.drivertracking.util.networkErrorMessage
import com.example.drivertracking.util.serverErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val stats: HomeStats? = null,
    val error: String? = null,
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val selectedMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: LocationApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun fetchStats() {
        val driverId = sessionManager.getDriverId()
        if (driverId == -1) {
            _uiState.value = _uiState.value.copy(error = "User not logged in")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val response = api.getHomeStats(driverId, state.selectedYear, state.selectedMonth)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        _uiState.value = _uiState.value.copy(stats = body.stats, isLoading = false, error = null)
                    } else {
                        _uiState.value = _uiState.value.copy(error = body?.message ?: "Unknown error", isLoading = false)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(error = serverErrorMessage(response.code()), isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = networkErrorMessage(e), isLoading = false)
            }
        }
    }
    
    fun changeMonth(offset: Int) {
        val currentState = _uiState.value
        var newMonth = currentState.selectedMonth + offset
        var newYear = currentState.selectedYear
        
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        } else if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        
        _uiState.value = currentState.copy(selectedYear = newYear, selectedMonth = newMonth)
        fetchStats()
    }

    fun logout() {
        sessionManager.clearSession()
    }
}
