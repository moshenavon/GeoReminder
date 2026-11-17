package com.example.ui

import android.app.Application
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferences
import com.example.data.GeoAlertDatabase
import com.example.data.GeoAlertRepository
import com.example.data.GeoEventLogEntity
import com.example.data.GeoZoneEntity
import com.example.data.LocationMode
import com.example.data.RingtonePreset
import com.example.data.ZoneOccupancyState
import com.example.geofence.GeofenceManager
import com.example.util.AlertBannerEvent
import com.example.util.LocationEvaluator
import com.example.util.LocationUtils
import com.example.util.SoundAndAlertManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RadarUiState(
    val isMonitoringActive: Boolean = true,
    val currentLat: Double = 32.0853,
    val currentLon: Double = 34.7818,
    val hasRealGpsFix: Boolean = false,
    val lastSampleTimeMillis: Long? = null,
    val secondsToNextSample: Int = 15,
    val latestBannerAlert: AlertBannerEvent? = null,
    val statusMessageHe: String = "מוכן לדגימת מיקום לפי טווחי הזמנים"
)

class GeoAlertViewModel(
    application: Application,
    private val repository: GeoAlertRepository
) : AndroidViewModel(application) {

    val zones: StateFlow<List<GeoZoneEntity>> = repository.allZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventLogs: StateFlow<List<GeoEventLogEntity>> = repository.eventLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _radarState = MutableStateFlow(
        RadarUiState(isMonitoringActive = GeofenceManager.isMonitoringEnabled(application))
    )
    val radarState: StateFlow<RadarUiState> = _radarState.asStateFlow()

    private val _locationMode = MutableStateFlow(AppPreferences.getLocationMode(application))
    val locationMode: StateFlow<LocationMode> = _locationMode.asStateFlow()

    private val _onboardingDone = MutableStateFlow(AppPreferences.isOnboardingDone(application))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    val isPlayingSound: StateFlow<Boolean> = SoundAndAlertManager.isPlayingSound
    val activeSoundLabel: StateFlow<String?> = SoundAndAlertManager.activeSoundLabel

    private var samplingLoopJob: Job? = null
    private var isScreenVisible = false

    init {
        SoundAndAlertManager.ensureNotificationChannels(application)
        viewModelScope.launch { GeofenceManager.sync(application) }
    }

    /** Switches between the battery-saving (geofencing) and the precise (GPS service) mode. */
    fun setLocationMode(mode: LocationMode) {
        if (mode == _locationMode.value) return
        AppPreferences.setLocationMode(getApplication<Application>(), mode)
        _locationMode.value = mode
        resyncGeofences()
    }

    /** [done] = true means the first-run guide is not shown again on the next launch. */
    fun setOnboardingDone(done: Boolean) {
        AppPreferences.setOnboardingDone(getApplication<Application>(), done)
        _onboardingDone.value = done
    }

    /**
     * v2: background monitoring is done by the Geofencing API. The live GPS loop below only
     * refreshes the radar UI, so it runs only while the screen is actually visible.
     */
    fun onScreenVisible() {
        isScreenVisible = true
        if (_radarState.value.isMonitoringActive) startSamplingLoop()
    }

    fun onScreenHidden() {
        isScreenVisible = false
        samplingLoopJob?.cancel()
    }

    /** Re-register geofences, e.g. after the user grants location permissions. */
    fun refreshMonitoring() {
        viewModelScope.launch {
            GeofenceManager.sync(getApplication<Application>())
            performLocationSample(useGpsIfAvailable = true)
        }
    }

    private fun resyncGeofences() {
        viewModelScope.launch { GeofenceManager.sync(getApplication<Application>()) }
    }

    fun toggleMonitoring(enabled: Boolean) {
        _radarState.value = _radarState.value.copy(
            isMonitoringActive = enabled,
            statusMessageHe = if (enabled) {
                "דגימת מיקום אוטומטית פעילה"
            } else {
                "דגימת מיקום מושהית"
            }
        )
        GeofenceManager.setMonitoringEnabled(getApplication<Application>(), enabled)
        resyncGeofences()
        if (enabled) {
            if (isScreenVisible) startSamplingLoop()
        } else {
            samplingLoopJob?.cancel()
        }
    }

    private fun startSamplingLoop() {
        samplingLoopJob?.cancel()
        samplingLoopJob = viewModelScope.launch {
            while (isActive && _radarState.value.isMonitoringActive && isScreenVisible) {
                val activeZones = repository.getAllZonesOnce().filter { it.isEnabled }
                val intervalSec = activeZones.minOfOrNull { it.samplingIntervalSeconds }
                    ?.coerceIn(5, 3600) ?: 15

                performLocationSample(useGpsIfAvailable = true)

                for (remaining in intervalSec downTo 1) {
                    if (!_radarState.value.isMonitoringActive) break
                    _radarState.value = _radarState.value.copy(secondsToNextSample = remaining)
                    delay(1000L)
                }
            }
        }
    }

    fun sampleNow() {
        viewModelScope.launch {
            performLocationSample(useGpsIfAvailable = true)
        }
    }

    private suspend fun performLocationSample(useGpsIfAvailable: Boolean) {
        val context = getApplication<Application>().applicationContext
        var lat = _radarState.value.currentLat
        var lon = _radarState.value.currentLon
        var gotRealGps = _radarState.value.hasRealGpsFix

        if (useGpsIfAvailable && LocationUtils.hasLocationPermission(context)) {
            val gpsCoords = LocationUtils.fetchCurrentLocation(context)
            if (gpsCoords != null) {
                lat = gpsCoords.first
                lon = gpsCoords.second
                gotRealGps = true
            }
        }

        val now = System.currentTimeMillis()
        val allZones = repository.getAllZonesOnce()
        val allEnabled = allZones.filter { it.isEnabled }
        val inTimeWindowCount = allEnabled.count { it.isWithinTimeWindow(now) }

        LocationEvaluator.evaluateSampledLocation(
            context = context,
            repository = repository,
            phoneLat = lat,
            phoneLon = lon,
            nowMillis = now,
            onAlertTriggered = { banner ->
                _radarState.value = _radarState.value.copy(latestBannerAlert = banner)
            }
        )

        val statusText = when {
            allZones.isEmpty() -> "עדיין לא הוגדר אזור לניטור"
            allEnabled.isEmpty() -> "כל האזורים מושבתים"
            inTimeWindowCount == 0 -> "ממתין לטווח הזמנים המוגדר (${allEnabled.size} אזורים)"
            gotRealGps -> "נדגם מיקום GPS בזמן אמת ($inTimeWindowCount אזורים בטווח השעות)"
            else -> "נדגם מיקום נוכחי ($inTimeWindowCount אזורים בטווח השעות)"
        }

        _radarState.value = _radarState.value.copy(
            currentLat = lat,
            currentLon = lon,
            hasRealGpsFix = gotRealGps,
            lastSampleTimeMillis = now,
            statusMessageHe = statusText
        )
    }

    fun fetchCurrentGpsCoordinates(onResult: (Double, Double) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val gps = LocationUtils.fetchCurrentLocation(context)
            if (gps != null) {
                _radarState.value = _radarState.value.copy(
                    currentLat = gps.first,
                    currentLon = gps.second,
                    hasRealGpsFix = true
                )
                onResult(gps.first, gps.second)
            } else {
                onResult(_radarState.value.currentLat, _radarState.value.currentLon)
            }
        }
    }

    /**
     * Moves the test/current coordinates inside or outside the selected zone's radius
     * and evaluates the location sample immediately so the user can test Entry and Exit alerts.
     */
    fun simulateMovementRelativeToZone(zone: GeoZoneEntity, moveInside: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            // Ensure previous state is the opposite so transition is guaranteed to fire
            val oppositeState = if (moveInside) ZoneOccupancyState.OUTSIDE else ZoneOccupancyState.INSIDE
            val preppedZone = zone.copy(lastKnownState = oppositeState.name)
            repository.updateZone(preppedZone)

            // 1 degree latitude is ~111,111 meters
            val targetDistanceMeters = if (moveInside) {
                (zone.radiusMeters * 0.35f).coerceAtLeast(10f)
            } else {
                zone.radiusMeters + 180f
            }
            val latOffset = targetDistanceMeters / 111111.0
            val newLat = zone.latitude + latOffset
            val newLon = zone.longitude

            val now = System.currentTimeMillis()
            val withinWindow = preppedZone.isWithinTimeWindow(now)

            _radarState.value = _radarState.value.copy(
                currentLat = newLat,
                currentLon = newLon,
                lastSampleTimeMillis = now,
                statusMessageHe = if (withinWindow) {
                    if (moveInside) "זוהתה כניסה לרדיוס '${zone.name}' בטווח הזמנים!"
                    else "זוהתה יציאה מהרדיוס '${zone.name}' בטווח הזמנים!"
                } else {
                    "שים לב: השעה הנוכחית מחוץ לטווח הזמנים של '${zone.name}' (${zone.formatTimeWindow()})"
                }
            )

            if (withinWindow) {
                LocationEvaluator.evaluateSampledLocation(
                    context = context,
                    repository = repository,
                    phoneLat = newLat,
                    phoneLon = newLon,
                    nowMillis = now,
                    onAlertTriggered = { banner ->
                        _radarState.value = _radarState.value.copy(latestBannerAlert = banner)
                    }
                )
            } else {
                // Still trigger if user explicitly clicked test button, or update distance
                val dist = LocationEvaluator.calculateDistanceMeters(
                    newLat,
                    newLon,
                    zone.latitude,
                    zone.longitude
                )
                LocationEvaluator.triggerZoneAlert(
                    context = context,
                    repository = repository,
                    zone = preppedZone.copy(
                        lastKnownState = if (moveInside) ZoneOccupancyState.INSIDE.name else ZoneOccupancyState.OUTSIDE.name,
                        lastDistanceMeters = dist,
                        lastSampledAt = now
                    ),
                    isEntry = moveInside,
                    distanceMeters = dist,
                    phoneLat = newLat,
                    phoneLon = newLon,
                    nowMillis = now,
                    onAlertTriggered = { banner ->
                        _radarState.value = _radarState.value.copy(latestBannerAlert = banner)
                    }
                )
                repository.updateZone(
                    preppedZone.copy(
                        lastKnownState = if (moveInside) ZoneOccupancyState.INSIDE.name else ZoneOccupancyState.OUTSIDE.name,
                        lastDistanceMeters = dist,
                        lastSampledAt = now
                    )
                )
            }
        }
    }

    fun saveZone(zone: GeoZoneEntity) {
        viewModelScope.launch {
            repository.saveZone(zone)
            GeofenceManager.sync(getApplication<Application>())
            performLocationSample(useGpsIfAvailable = false)
        }
    }

    fun toggleZoneEnabled(zone: GeoZoneEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateZone(zone.copy(isEnabled = enabled))
            GeofenceManager.sync(getApplication<Application>())
        }
    }

    fun deleteZone(zone: GeoZoneEntity) {
        viewModelScope.launch {
            repository.deleteZone(zone)
            GeofenceManager.sync(getApplication<Application>())
        }
    }

    fun clearEventHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun dismissBannerAlert() {
        _radarState.value = _radarState.value.copy(latestBannerAlert = null)
    }

    fun previewRingtone(
        preset: RingtonePreset,
        customUri: String = "",
        isEntry: Boolean = true
    ) {
        val context = getApplication<Application>().applicationContext
        SoundAndAlertManager.playAlertSound(
            context = context,
            preset = preset,
            customUriString = customUri,
            vibrate = true,
            isEntry = isEntry
        )
    }

    fun stopPlayingSound() {
        SoundAndAlertManager.stopCurrentSound()
    }

    fun resolveRingtoneTitle(uri: Uri?): String {
        if (uri == null) return ""
        return try {
            val context = getApplication<Application>().applicationContext
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "צלצול מותאם"
        } catch (_: Exception) {
            "צלצול מותאם"
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = GeoAlertDatabase.getInstance(application)
            val repo = GeoAlertRepository(db.geoAlertDao())
            return GeoAlertViewModel(application, repo) as T
        }
    }
}
