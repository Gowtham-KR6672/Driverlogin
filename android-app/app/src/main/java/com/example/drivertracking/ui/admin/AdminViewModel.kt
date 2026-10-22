package com.example.drivertracking.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivertracking.data.remote.*
import com.example.drivertracking.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Admin calls are authorized by the Bearer token that AppModule adds to every request.
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: LocationApi,
    sessionManager: SessionManager
) : ViewModel() {

    // View-only admins (e.g. the MD) can see users but not create, reset or delete them.
    val canEdit: Boolean = sessionManager.canEdit()

    // Dashboard state
    private val _dashboardState = MutableStateFlow<AdminDashboardResponse?>(null)
    val dashboardState = _dashboardState.asStateFlow()

    // Users state
    private val _usersState = MutableStateFlow<List<AdminUser>?>(null)
    val usersState = _usersState.asStateFlow()

    // Work state
    private val _workState = MutableStateFlow<List<HistoryEntry>?>(null)
    val workState = _workState.asStateFlow()

    fun fetchDashboard() {
        viewModelScope.launch {
            try {
                val response = api.getAdminDashboard()
                if (response.isSuccessful && response.body()?.success == true) {
                    _dashboardState.value = response.body()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun fetchUsers() {
        viewModelScope.launch {
            try {
                val response = api.getAdminUsers()
                if (response.isSuccessful && response.body()?.success == true) {
                    _usersState.value = response.body()?.users
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun createUser(username: String, pass: String) {
        viewModelScope.launch {
            val payload = mapOf("username" to username, "password" to pass)
            api.createAdminUser(payload)
            fetchUsers()
        }
    }

    fun resetPassword(userId: Int, newPass: String) {
        viewModelScope.launch {
            val payload = mapOf("new_password" to newPass)
            api.resetUserPassword(userId, payload)
        }
    }

    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            api.deleteUser(userId, emptyMap())
            fetchUsers()
        }
    }

    fun fetchWorkEntries(userId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getAdminWork(userId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _workState.value = response.body()?.entries
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
