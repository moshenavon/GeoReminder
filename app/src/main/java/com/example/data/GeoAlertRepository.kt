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

    suspend fun ensureDefaultZoneIfEmpty() {
        val existing = dao.getAllZonesOnce()
        if (existing.isEmpty()) {
            // Seed two helpful initial zones so the user sees immediate active functionality
            dao.insertZone(
                GeoZoneEntity(
                    name = "אזור עבודה / משרד",
                    latitude = 32.0853,
                    longitude = 34.7818,
                    radiusMeters = 300f,
                    startHour = 7,
                    startMinute = 30,
                    endHour = 20,
                    endMinute = 0,
                    activeDaysCsv = "1,2,3,4,5,6",
                    samplingIntervalSeconds = 15,
                    alertOnEntry = true,
                    alertOnExit = true,
                    entryRingtoneType = RingtonePreset.RADAR_CHIME.name,
                    exitRingtoneType = RingtonePreset.SIREN_ALERT.name,
                    vibrate = true,
                    isEnabled = true
                )
            )
            dao.insertZone(
                GeoZoneEntity(
                    name = "בית / שכונה",
                    latitude = 31.7683,
                    longitude = 35.2137,
                    radiusMeters = 500f,
                    startHour = 0,
                    startMinute = 0,
                    endHour = 23,
                    endMinute = 59,
                    activeDaysCsv = "1,2,3,4,5,6,7",
                    samplingIntervalSeconds = 30,
                    alertOnEntry = true,
                    alertOnExit = true,
                    entryRingtoneType = RingtonePreset.SONAR_PULSE.name,
                    exitRingtoneType = RingtonePreset.SYSTEM_NOTIFICATION.name,
                    vibrate = true,
                    isEnabled = true
                )
            )
        }
    }
}
