package com.example.drivertracking.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivertracking.data.remote.HistoryEntry
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

data class HistoryUiState(
    val isLoading: Boolean = false,
    val entries: List<HistoryEntry> = emptyList(),
    val error: String? = null,
    val fromDate: String? = null, // "yyyy-MM-dd"
    val toDate: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val api: LocationApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    private var allEntries: List<HistoryEntry> = emptyList()

    // The route map WebView loads a server page that needs the same token as the API.
    fun authToken(): String? = sessionManager.getAuthToken()

    fun fetchHistory() {
        val driverId = sessionManager.getDriverId()
        if (driverId == -1) return

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val response = api.getHistory(driverId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        allEntries = body.entries ?: emptyList()
                        applyFilters()
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = body?.message)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = serverErrorMessage(response.code(), "Server error"))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = networkErrorMessage(e))
            }
        }
    }
    
    fun setDateFilter(fromDate: String?, toDate: String?) {
        _uiState.value = _uiState.value.copy(fromDate = fromDate, toDate = toDate)
        applyFilters()
    }
    
    private fun applyFilters() {
        var filteredList = allEntries.sortedByDescending { it.id } // Recent to old
        
        val fromDateStr = _uiState.value.fromDate
        val toDateStr = _uiState.value.toDate
        
        if (fromDateStr != null && toDateStr != null) {
            filteredList = filteredList.filter { entry ->
                // Assuming nextStartTime is format "yyyy-MM-dd HH:mm:ss"
                val entryDate = entry.nextStartTime?.take(10) ?: ""
                entryDate in fromDateStr..toDateStr
            }
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            entries = filteredList,
            error = null
        )
    }
}
