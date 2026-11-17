package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Location helpers shared by the UI and the background geofencing receivers.
 * (Previously lived inside LocationSamplingService, which was removed in v2.)
 */
object LocationUtils {

    fun hasLocationPermission(context: Context): Boolean =
        hasFineLocationPermission(context) ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Before Android 10, foreground location permission also covers background use. */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(context)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Geofencing requires FINE location, plus background location on Android 10+. */
    fun canUseGeofencing(context: Context): Boolean =
        hasFineLocationPermission(context) && hasBackgroundLocationPermission(context)

    /**
     * One-shot location fetch. Falls back to the last known location if no fresh fix
     * arrives within [timeoutMillis].
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(
        context: Context,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY,
        timeoutMillis: Long = 20_000L
    ): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setDurationMillis(timeoutMillis)
                .setMaxUpdateAgeMillis(30_000L)
                .build()
            val tokenSource = CancellationTokenSource()
            val fresh = client.getCurrentLocation(request, tokenSource.token).awaitOrNull()
            val location = fresh ?: client.lastLocation.awaitOrNull()
            location?.let { it.latitude to it.longitude }
        } catch (_: Exception) {
            null
        }
    }
}

/** Minimal Task -> coroutine bridge (avoids adding kotlinx-coroutines-play-services). */
suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { if (cont.isActive) cont.resume(null) }
    addOnCanceledListener { if (cont.isActive) cont.resume(null) }
}

/** Like [awaitOrNull] but reports only whether the task succeeded. */
suspend fun Task<*>.awaitSuccess(): Boolean = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(true) }
    addOnFailureListener { if (cont.isActive) cont.resume(false) }
    addOnCanceledListener { if (cont.isActive) cont.resume(false) }
}
