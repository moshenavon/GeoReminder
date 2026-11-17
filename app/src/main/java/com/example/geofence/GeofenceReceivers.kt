package com.example.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.example.data.GeoAlertDatabase
import com.example.data.GeoAlertRepository
import com.example.util.LocationEvaluator
import com.example.util.LocationUtils
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Gives alert sounds time to play before the receiver lets the process go idle. */
private const val ALERT_SOUND_GRACE_MS = 6_000L

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (_: Exception) {
        } finally {
            pending.finish()
        }
    }
}

private fun repositoryFor(context: Context) =
    GeoAlertRepository(GeoAlertDatabase.getInstance(context).geoAlertDao())

/** Woken by Play Services when the phone crosses a zone boundary. */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val app = context.applicationContext

        runAsync {
            if (event.hasError()) return@runAsync // e.g. location turned off; resynced later
            if (!GeofenceManager.isMonitoringEnabled(app)) return@runAsync

            val isEntry = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> true
                Geofence.GEOFENCE_TRANSITION_EXIT -> false
                else -> return@runAsync
            }

            val repository = repositoryFor(app)
            val location = event.triggeringLocation
            var anyAlert = false

            event.triggeringGeofences.orEmpty().forEach { geofence ->
                val zoneId = geofence.requestId.toLongOrNull() ?: return@forEach
                val alerted = LocationEvaluator.applyGeofenceTransition(
                    context = app,
                    repository = repository,
                    zoneId = zoneId,
                    isEntry = isEntry,
                    phoneLat = location?.latitude,
                    phoneLon = location?.longitude
                )
                anyAlert = anyAlert || alerted
            }

            if (anyAlert) delay(ALERT_SOUND_GRACE_MS)
        }
    }
}

/**
 * Fires once when the next zone time window opens: takes a single location fix so that a
 * phone that is *already* inside a zone gets the ENTRY alert (matches v1 behaviour), then
 * schedules the next window start.
 */
class WindowStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        runAsync {
            val repository = repositoryFor(app)
            var anyAlert = false

            if (GeofenceManager.isMonitoringEnabled(app) && LocationUtils.canUseGeofencing(app)) {
                val location = LocationUtils.fetchCurrentLocation(
                    context = app,
                    priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    timeoutMillis = 15_000L
                )
                if (location != null) {
                    LocationEvaluator.evaluateSampledLocation(
                        context = app,
                        repository = repository,
                        phoneLat = location.first,
                        phoneLon = location.second,
                        onAlertTriggered = { anyAlert = true }
                    )
                }
            }

            if (GeofenceManager.isMonitoringEnabled(app)) {
                GeofenceManager.scheduleNextWindowStartCheck(
                    app,
                    repository.getAllZonesOnce().filter { it.isEnabled }
                )
            }

            if (anyAlert) delay(ALERT_SOUND_GRACE_MS)
        }
    }
}

/**
 * Geofences are wiped by the OS on reboot, app update, and when location is switched
 * off. This re-registers them.
 */
class GeofenceResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            LocationManager.PROVIDERS_CHANGED_ACTION -> Unit
            else -> return
        }
        val app = context.applicationContext
        runAsync { GeofenceManager.sync(app) }
    }
}
