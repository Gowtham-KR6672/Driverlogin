package com.example.drivertracking.data.repository

import com.example.drivertracking.data.local.LocationDao
import com.example.drivertracking.data.local.LocationEntity
import com.example.drivertracking.data.remote.LocationApi
import com.example.drivertracking.data.remote.LocationPayload
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepository @Inject constructor(
    private val locationApi: LocationApi,
    private val locationDao: LocationDao
) {

    suspend fun saveLocationLocally(entity: LocationEntity) {
        locationDao.insertLocation(entity)
    }

    suspend fun sendLocation(payload: LocationPayload): Boolean {
        return try {
            val response = locationApi.sendLocation(payload)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun syncOfflineLocations(): Boolean {
        val pending = locationDao.getAllPendingLocations()
        if (pending.isEmpty()) return true

        var allSynced = true
        for (location in pending) {
            val payload = LocationPayload(
                driverId = location.driverId,
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed,
                accuracy = location.accuracy,
                bearing = location.bearing,
                timestamp = location.timestamp
            )
            val success = sendLocation(payload)
            if (success) {
                locationDao.deleteLocation(location.id)
            } else {
                allSynced = false
            }
        }
        return allSynced
    }
}
