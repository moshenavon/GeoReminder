package com.example.geofence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.AppPreferences
import com.example.data.GeoAlertDatabase
import com.example.data.GeoAlertRepository
import com.example.data.LocationMode
import com.example.util.LocationEvaluator
import com.example.util.LocationUtils
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Precise" location mode: a foreground service that takes a high-accuracy GPS fix at the
 * interval configured on the zones (shortest interval among zones whose time window is open)
 * and evaluates all zones. Between samples the GPS is off, and while no zone's window is
 * open the service only wakes once a minute to check the clock.
 *
 * Costs more battery than geofencing and shows a persistent notification, but is much more
 * accurate and reacts within seconds.
 */
class LocationSamplingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Missing permission / OS refused a background start: nothing to monitor with.
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ניטור מיקום מדויק",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "התראה קבועה שמוצגת כל עוד הניטור המדויק (GPS) פועל ברקע"
                    setShowBadge(false)
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("GeoAlert: ניטור מדויק פעיל")
            .setContentText("דגימת GPS ברקע באזורים הפעילים. אפשר לעבור למצב חסכוני בהגדרות.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .build()
    }

    private suspend fun runLoop() {
        val app = applicationContext
        val repository = GeoAlertRepository(GeoAlertDatabase.getInstance(app).geoAlertDao())

        while (scope.isActive) {
            val stillWanted = GeofenceManager.isMonitoringEnabled(app) &&
                    AppPreferences.getLocationMode(app) == LocationMode.PRECISE &&
                    LocationUtils.hasLocationPermission(app)
            val zones = if (stillWanted) repository.getAllZonesOnce().filter { it.isEnabled } else emptyList()
            if (zones.isEmpty()) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()
            val inWindow = zones.filter { it.isWithinTimeWindow(now) }
            if (inWindow.isEmpty()) {
                delay(IDLE_CHECK_MS)
                continue
            }

            val location = LocationUtils.fetchCurrentLocation(
                context = app,
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                timeoutMillis = 20_000L,
                allowLastKnown = false // a stale fix could fake an entry / exit
            )
            if (location != null) {
                try {
                    LocationEvaluator.evaluateSampledLocation(
                        context = app,
                        repository = repository,
                        phoneLat = location.first,
                        phoneLon = location.second
                    )
                } catch (_: Exception) {
                    // keep sampling even if one evaluation fails
                }
            }

            val intervalSec = inWindow.minOf { it.samplingIntervalSeconds }.coerceIn(5, 3600)
            delay(intervalSec * 1000L)
        }
    }

    companion object {
        private const val CHANNEL_ID = "geoalert_precise_monitoring"
        private const val NOTIFICATION_ID = 4101
        private const val IDLE_CHECK_MS = 60_000L

        /** Returns false if the OS refused to start the service. */
        fun start(context: Context): Boolean {
            val app = context.applicationContext
            return try {
                ContextCompat.startForegroundService(app, Intent(app, LocationSamplingService::class.java))
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, LocationSamplingService::class.java))
        }
    }
}
