package com.example.drivertracking.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        Constants.PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuthToken(token: String) {
        sharedPreferences.edit().putString(Constants.KEY_AUTH_TOKEN, token).apply()
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString(Constants.KEY_AUTH_TOKEN, null)
    }

    fun saveDriverId(driverId: Int) {
        sharedPreferences.edit().putInt(Constants.KEY_DRIVER_ID, driverId).apply()
    }

    fun getDriverId(): Int {
        return sharedPreferences.getInt(Constants.KEY_DRIVER_ID, -1)
    }

    fun saveUserName(name: String) {
        sharedPreferences.edit().putString(Constants.KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return sharedPreferences.getString(Constants.KEY_USER_NAME, null)
    }

    fun saveUserRole(role: String) {
        sharedPreferences.edit().putString(Constants.KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? {
        return sharedPreferences.getString(Constants.KEY_USER_ROLE, null)
    }

    /** Admins only: "full", "edit" or "view". */
    fun saveAccessLevel(level: String?) {
        sharedPreferences.edit().putString(Constants.KEY_ACCESS_LEVEL, level).apply()
    }

    fun canEdit(): Boolean {
        return sharedPreferences.getString(Constants.KEY_ACCESS_LEVEL, null) != "view"
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server rejects the saved token (expired, password reset, or user deleted). */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun expireSession(rejectedToken: String) {
        // A late 401 for a token that a new login has already replaced must not log the user out.
        if (getAuthToken() != rejectedToken) return
        clearSession()
        _sessionExpired.tryEmit(Unit)
    }
}
