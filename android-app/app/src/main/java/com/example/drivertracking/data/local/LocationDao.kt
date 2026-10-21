package com.example.drivertracking.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Query("SELECT * FROM pending_locations ORDER BY timestamp ASC")
    suspend fun getAllPendingLocations(): List<LocationEntity>

    @Query("DELETE FROM pending_locations WHERE id = :id")
    suspend fun deleteLocation(id: Long)
    
    @Query("DELETE FROM pending_locations")
    suspend fun clearAll()
}
