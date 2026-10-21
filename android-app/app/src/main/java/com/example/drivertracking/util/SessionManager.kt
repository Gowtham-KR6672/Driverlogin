package com.example.drivertracking.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }
}
