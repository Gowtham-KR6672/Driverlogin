package com.example.drivertracking.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val driverId: Int,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val accuracy: Float,
    val bearing: Float,
    val timestamp: String
)
