package com.example.geofence

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.GeoAlertDatabase
import com.example.data.GeoAlertRepository
import com.example.data.GeoZoneEntity
import com.example.util.LocationUtils
import com.example.util.awaitSuccess
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.Calendar

/**
 * v2 battery-friendly monitoring.
 *
 * Instead of polling GPS every few seconds, every enabled zone is registered with the
 * Google Play Services Geofencing API. The OS watches the boundaries (mostly via Wi-Fi /
 * cell / motion sensors) and wakes [GeofenceBroadcastReceiver] only on ENTER / EXIT.
 *
 * Time windows: geofence events outside a zone's window are ignored (same as v1). To keep
 * v1 behaviour where an alert fires if the phone is already inside a zone when its window
 * opens, a single inexact alarm is scheduled for the next window start
 * ([WindowStartReceiver]), which takes one location fix and evaluates all zones.
 */
object GeofenceManager {

    /** Google recommends at least 100-150 m for reliable geofences. */
    const val MIN_GEOFENCE_RADIUS_METERS = 100f

    /** Max delay the OS may batch a transition by. Higher = less battery, slower alerts. */
    private const val NOTIFICATION_RESPONSIVENESS_MS = 60_000

    /** Play Services limit per app. */
    private const val MAX_GEOFENCES = 100

    private const val REQUEST_CODE_GEOFENCE = 7001
    private const val REQUEST_CODE_WINDOW_START = 7002

    private const val PREFS = "geoalert_prefs"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"

    fun isMonitoringEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITORING_ENABLED, true)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONITORING_ENABLED, enabled)
            .apply()
    }

    /**
     * Re-registers all geofences from the database. Call after any zone change, after
     * permissions change, on boot, and when location providers are toggled.
     * Returns true if geofences are now active.
     */
    @SuppressLint("MissingPermission")
    suspend fun sync(context: Context): Boolean {
        val app = context.applicationContext
        val client = LocationServices.getGeofencingClient(app)
        val pendingIntent = geofencePendingIntent(app)

        // Always start clean so edited / deleted zones don't linger.
        client.removeGeofences(pendingIntent).awaitSuccess()

        val repository = GeoAlertRepository(GeoAlertDatabase.getInstance(app).geoAlertDao())
        val zones = repository.getAllZonesOnce().filter { it.isEnabled }

        if (!isMonitoringEnabled(app) || zones.isEmpty()) {
            cancelWindowStartCheck(app)
            return false
        }

        scheduleNextWindowStartCheck(app, zones)

        if (!LocationUtils.canUseGeofencing(app)) return false

        val geofences = zones.take(MAX_GEOFENCES).map { zone ->
            Geofence.Builder()
                .setRequestId(zone.id.toString())
                .setCircularRegion(
                    zone.latitude,
                    zone.longitude,
                    zone.radiusMeters.coerceAtLeast(MIN_GEOFENCE_RADIUS_METERS)
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
                .build()
        }

        val request = GeofencingRequest.Builder()
            // If we're already inside a zone at registration time, deliver ENTER.
            // Duplicate alerts are prevented by the stored zone state.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        return try {
            client.addGeofences(request, pendingIntent).awaitSuccess()
        } catch (_: SecurityException) {
            false
        }
    }

    // ---- Window-start check -------------------------------------------------------------

    fun scheduleNextWindowStartCheck(context: Context, zones: List<GeoZoneEntity>) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val next = zones.mapNotNull { nextWindowStartMillis(it, now) }.minOrNull()
        if (next == null) {
            cancelWindowStartCheck(app)
            return
        }
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact but Doze-friendly; may fire a few minutes late, never early.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next + 5_000L, // small buffer so we're safely inside the window
            windowStartPendingIntent(app)
        )
    }

    private fun cancelWindowStartCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(windowStartPendingIntent(context))
    }

    /** Next moment (after [now]) at which [zone]'s time window opens, or null. */
    internal fun nextWindowStartMillis(zone: GeoZoneEntity, now: Long): Long? {
        val activeDays = zone.getActiveDaysSet()
        for (dayOffset in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, zone.startHour)
                set(Calendar.MINUTE, zone.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) continue
            if (activeDays.isNotEmpty() && cal.get(Calendar.DAY_OF_WEEK) !in activeDays) continue
            return cal.timeInMillis
        }
        return null
    }

    // ---- PendingIntents ---------------------------------------------------------------

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // Play Services fills in the event extras, so this one must be mutable on S+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE_GEOFENCE, intent, flags)
    }

    private fun windowStartPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WindowStartReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WINDOW_START,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
