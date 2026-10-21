package com.example.drivertracking.util

import android.location.Location

object LocationUtils {
    // Haversine distance in meters
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun isValidLocationUpdate(oldLoc: Location?, newLoc: Location?): Boolean {
        if (newLoc == null) return false
        // Ignore extremely inaccurate points (allow up to 2000m for coarse location fallback)
        if (newLoc.accuracy > 2000) return false
        
        if (oldLoc == null) return true
        
        val distance = oldLoc.distanceTo(newLoc)
        // Ignore tiny noise movements less than 2 meters
        if (distance < 2) return false
        
        // Ignore unrealistic jumps (e.g. > 10km in a few seconds)
        val timeDeltaSeconds = (newLoc.time - oldLoc.time) / 1000
        val speedMps = distance / (if (timeDeltaSeconds > 0) timeDeltaSeconds else 1)
        if (speedMps > 100) return false // > 360 km/h is unrealistic

        return true
    }
}
