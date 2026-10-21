package com.example.drivertracking.util

object Constants {
    const val BASE_URL = "https://driverlogin-4rif.onrender.com"
    const val PREFS_FILE = "driver_tracking_secure_prefs"
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_DRIVER_ID = "driver_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_ROLE = "user_role"
    
    // Tracking Settings
    const val LOCATION_UPDATE_INTERVAL = 3000L // 3 seconds when moving
    const val LOCATION_FASTEST_INTERVAL = 1000L // 1 second
    const val LOCATION_MAX_WAIT_TIME = 5000L // 5 seconds
    
    // Bubble Overlay
    const val OVERLAY_PREFS = "overlay_prefs"
    const val KEY_OVERLAY_X = "overlay_x"
    const val KEY_OVERLAY_Y = "overlay_y"
}
