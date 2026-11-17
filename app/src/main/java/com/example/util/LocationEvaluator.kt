package com.example.util

import android.content.Context
import com.example.data.GeoAlertRepository
import com.example.data.GeoEventLogEntity
import com.example.data.GeoZoneEntity
import com.example.data.RingtonePreset
import com.example.data.ZoneOccupancyState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AlertBannerEvent(
    val id: Long = System.currentTimeMillis(),
    val zoneId: Long,
    val zoneName: String,
    val isEntry: Boolean,
    val distanceMeters: Float,
    val radiusMeters: Float,
    val ringtoneName: String,
    val timestamp: Long = System.currentTimeMillis()
)

object LocationEvaluator {

    /**
     * Geofence events, the window-start check and the in-app refresh loop can all evaluate
     * at the same time. Serialising them prevents two of them reading the same previous
     * state and firing the same alert twice.
     */
    private val evaluationMutex = Mutex()

    /**
     * Pure Haversine distance calculation in meters between two WGS84 coordinates.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).toFloat()
    }

    /**
     * Evaluates all enabled zones at the given phone coordinates and timestamp.
     * Only samples & triggers alerts for zones whose time window is currently active.
     */
    suspend fun evaluateSampledLocation(
        context: Context,
        repository: GeoAlertRepository,
        phoneLat: Double,
        phoneLon: Double,
        nowMillis: Long = System.currentTimeMillis(),
        onAlertTriggered: (AlertBannerEvent) -> Unit = {}
    ) = evaluationMutex.withLock {
        val zones = repository.getAllZonesOnce()
        for (zone in zones) {
            if (!zone.isEnabled) continue

            // Check if current time is within the configured sampling window
            if (!zone.isWithinTimeWindow(nowMillis)) {
                continue
            }

            val distance = calculateDistanceMeters(
                phoneLat,
                phoneLon,
                zone.latitude,
                zone.longitude
            )

            val previousState = ZoneOccupancyState.fromName(zone.lastKnownState)
            val currentState = if (distance <= zone.radiusMeters) {
                ZoneOccupancyState.INSIDE
            } else {
                ZoneOccupancyState.OUTSIDE
            }

            val updatedZone = zone.copy(
                lastKnownState = currentState.name,
                lastDistanceMeters = distance,
                lastSampledAt = nowMillis
            )
            repository.updateZone(updatedZone)

            // Detect entry into radius during active time window
            val enteredRadius = currentState == ZoneOccupancyState.INSIDE &&
                    (previousState == ZoneOccupancyState.OUTSIDE || previousState == ZoneOccupancyState.UNKNOWN)

            // Detect exit from radius during active time window
            val exitedRadius = currentState == ZoneOccupancyState.OUTSIDE &&
                    previousState == ZoneOccupancyState.INSIDE

            if (enteredRadius && zone.alertOnEntry) {
                triggerZoneAlert(
                    context = context,
                    repository = repository,
                    zone = updatedZone,
                    isEntry = true,
                    distanceMeters = distance,
                    phoneLat = phoneLat,
                    phoneLon = phoneLon,
                    nowMillis = nowMillis,
                    onAlertTriggered = onAlertTriggered
                )
            } else if (exitedRadius && zone.alertOnExit) {
                triggerZoneAlert(
                    context = context,
                    repository = repository,
                    zone = updatedZone,
                    isEntry = false,
                    distanceMeters = distance,
                    phoneLat = phoneLat,
                    phoneLon = phoneLon,
                    nowMillis = nowMillis,
                    onAlertTriggered = onAlertTriggered
                )
            }
        }
    }

    /**
     * Handles a single ENTER/EXIT reported by the Geofencing API (v2).
     * Uses the same rules as [evaluateSampledLocation]: ignored outside the zone's time
     * window, and an alert fires only on a real state change.
     * Returns true if an alert was triggered.
     */
    suspend fun applyGeofenceTransition(
        context: Context,
        repository: GeoAlertRepository,
        zoneId: Long,
        isEntry: Boolean,
        phoneLat: Double?,
        phoneLon: Double?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = evaluationMutex.withLock {
        val zone = repository.getZoneById(zoneId) ?: return@withLock false
        if (!zone.isEnabled || !zone.isWithinTimeWindow(nowMillis)) return@withLock false

        val previousState = ZoneOccupancyState.fromName(zone.lastKnownState)
        val newState = if (isEntry) ZoneOccupancyState.INSIDE else ZoneOccupancyState.OUTSIDE

        val distance = if (phoneLat != null && phoneLon != null) {
            calculateDistanceMeters(phoneLat, phoneLon, zone.latitude, zone.longitude)
        } else {
            if (isEntry) 0f else zone.radiusMeters
        }

        val updatedZone = zone.copy(
            lastKnownState = newState.name,
            lastDistanceMeters = distance,
            lastSampledAt = nowMillis
        )
        repository.updateZone(updatedZone)

        val shouldAlert = if (isEntry) {
            previousState != ZoneOccupancyState.INSIDE && zone.alertOnEntry
        } else {
            previousState == ZoneOccupancyState.INSIDE && zone.alertOnExit
        }

        if (shouldAlert) {
            triggerZoneAlert(
                context = context,
                repository = repository,
                zone = updatedZone,
                isEntry = isEntry,
                distanceMeters = distance,
                phoneLat = phoneLat ?: zone.latitude,
                phoneLon = phoneLon ?: zone.longitude,
                nowMillis = nowMillis
            )
        }
        shouldAlert
    }

    suspend fun triggerZoneAlert(
        context: Context,
        repository: GeoAlertRepository,
        zone: GeoZoneEntity,
        isEntry: Boolean,
        distanceMeters: Float,
        phoneLat: Double,
        phoneLon: Double,
        nowMillis: Long = System.currentTimeMillis(),
        onAlertTriggered: (AlertBannerEvent) -> Unit = {}
    ) {
        val preset = if (isEntry) {
            RingtonePreset.fromName(zone.entryRingtoneType)
        } else {
            RingtonePreset.fromName(zone.exitRingtoneType)
        }
        val customUri = if (isEntry) zone.entryCustomRingtoneUri else zone.exitCustomRingtoneUri
        val ringtoneLabel = if (isEntry) zone.entryRingtoneDisplayName() else zone.exitRingtoneDisplayName()

        SoundAndAlertManager.playAlertSound(
            context = context,
            preset = preset,
            customUriString = customUri,
            vibrate = zone.vibrate,
            isEntry = isEntry
        )

        SoundAndAlertManager.postZoneTransitionNotification(
            context = context,
            zoneName = zone.name,
            isEntry = isEntry,
            distanceMeters = distanceMeters,
            radiusMeters = zone.radiusMeters,
            ringtoneName = ringtoneLabel
        )

        val logEntry = GeoEventLogEntity(
            zoneId = zone.id,
            zoneName = zone.name,
            eventType = if (isEntry) "ENTRY" else "EXIT",
            timestamp = nowMillis,
            distanceMeters = distanceMeters,
            radiusMeters = zone.radiusMeters,
            ringtoneLabel = ringtoneLabel,
            latitude = phoneLat,
            longitude = phoneLon
        )
        repository.logEvent(logEntry)

        onAlertTriggered(
            AlertBannerEvent(
                zoneId = zone.id,
                zoneName = zone.name,
                isEntry = isEntry,
                distanceMeters = distanceMeters,
                radiusMeters = zone.radiusMeters,
                ringtoneName = ringtoneLabel,
                timestamp = nowMillis
            )
        )
    }
}
