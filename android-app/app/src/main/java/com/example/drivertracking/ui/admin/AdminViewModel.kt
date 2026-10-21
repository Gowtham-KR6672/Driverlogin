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

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: LocationApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val adminId: Int get() = sessionManager.getAuthToken()?.toIntOrNull() ?: -1

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
        if (adminId == -1) return
        viewModelScope.launch {
            try {
                val response = api.getAdminDashboard(adminId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _dashboardState.value = response.body()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun fetchUsers() {
        if (adminId == -1) return
        viewModelScope.launch {
            try {
                val response = api.getAdminUsers(adminId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _usersState.value = response.body()?.users
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun createUser(username: String, pass: String) {
        if (adminId == -1) return
        viewModelScope.launch {
            val payload = mapOf("admin_id" to adminId.toString(), "username" to username, "password" to pass)
            api.createAdminUser(payload)
            fetchUsers()
        }
    }

    fun resetPassword(userId: Int, newPass: String) {
        if (adminId == -1) return
        viewModelScope.launch {
            val payload = mapOf("admin_id" to adminId.toString(), "new_password" to newPass)
            api.resetUserPassword(userId, payload)
        }
    }

    fun deleteUser(userId: Int) {
        if (adminId == -1) return
        viewModelScope.launch {
            val payload = mapOf("admin_id" to adminId.toString())
            api.deleteUser(userId, payload)
            fetchUsers()
        }
    }

    fun fetchWorkEntries(userId: Int) {
        if (adminId == -1) return
        viewModelScope.launch {
            try {
                val response = api.getAdminWork(adminId, userId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _workState.value = response.body()?.entries
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
