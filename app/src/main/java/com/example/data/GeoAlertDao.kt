package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoAlertDao {
    @Query("SELECT * FROM geo_zones ORDER BY isEnabled DESC, id ASC")
    fun observeAllZones(): Flow<List<GeoZoneEntity>>

    @Query("SELECT * FROM geo_zones")
    suspend fun getAllZonesOnce(): List<GeoZoneEntity>

    @Query("SELECT * FROM geo_zones WHERE id = :id LIMIT 1")
    suspend fun getZoneById(id: Long): GeoZoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: GeoZoneEntity): Long

    @Update
    suspend fun updateZone(zone: GeoZoneEntity)

    @Delete
    suspend fun deleteZone(zone: GeoZoneEntity)

    @Query("SELECT * FROM geo_event_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeEventLogs(): Flow<List<GeoEventLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventLog(event: GeoEventLogEntity): Long

    @Query("DELETE FROM geo_event_logs")
    suspend fun clearAllEventLogs()
}
