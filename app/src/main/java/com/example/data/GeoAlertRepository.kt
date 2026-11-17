package com.example.data

import kotlinx.coroutines.flow.Flow

class GeoAlertRepository(private val dao: GeoAlertDao) {
    val allZones: Flow<List<GeoZoneEntity>> = dao.observeAllZones()
    val eventLogs: Flow<List<GeoEventLogEntity>> = dao.observeEventLogs()

    suspend fun getAllZonesOnce(): List<GeoZoneEntity> = dao.getAllZonesOnce()

    suspend fun getZoneById(id: Long): GeoZoneEntity? = dao.getZoneById(id)

    suspend fun saveZone(zone: GeoZoneEntity): Long {
        return if (zone.id == 0L) {
            dao.insertZone(zone)
        } else {
            dao.updateZone(zone)
            zone.id
        }
    }

    suspend fun updateZone(zone: GeoZoneEntity) = dao.updateZone(zone)

    suspend fun deleteZone(zone: GeoZoneEntity) = dao.deleteZone(zone)

    suspend fun logEvent(event: GeoEventLogEntity) = dao.insertEventLog(event)

    suspend fun clearHistory() = dao.clearAllEventLogs()
}
