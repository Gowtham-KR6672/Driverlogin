package com.example.drivertracking.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivertracking.data.remote.LocationApi
import com.example.drivertracking.data.remote.LoginRequest
import com.example.drivertracking.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val locationApi: LocationApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, pin: String) {
        if (username.isBlank() || pin.isBlank()) {
            _uiState.value = LoginUiState.Error("Username and PIN cannot be empty")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val response = locationApi.login(LoginRequest(username, pin))
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null && loginResponse.success) {
                        loginResponse.token?.let { sessionManager.saveAuthToken(it) }
                        loginResponse.driverId?.let { sessionManager.saveDriverId(it) }
                        loginResponse.name?.let { sessionManager.saveUserName(it) }
                        loginResponse.role?.let { sessionManager.saveUserRole(it) }
                        sessionManager.saveAccessLevel(loginResponse.accessLevel)
                        _uiState.value = LoginUiState.Success
                    } else {
                        _uiState.value = LoginUiState.Error(loginResponse?.message ?: "Login failed")
                    }
                } else {
                    _uiState.value = LoginUiState.Error("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("Network error: ${e.message}")
            }
        }
    }
}
