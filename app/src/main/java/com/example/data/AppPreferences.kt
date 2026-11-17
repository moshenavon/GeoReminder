package com.example.data

import android.content.Context

/** How the phone's location is checked in the background. */
enum class LocationMode {
    /** Google Geofencing API: OS-managed, very low battery use, less precise, slightly delayed. */
    BATTERY_SAVER,

    /** Foreground service sampling the GPS at the interval chosen per zone: precise, uses more battery. */
    PRECISE;

    companion object {
        fun fromName(name: String?): LocationMode =
            entries.find { it.name == name } ?: BATTERY_SAVER
    }
}

/** Small app-wide settings kept in SharedPreferences (readable from receivers and services). */
object AppPreferences {
    private const val PREFS = "geoalert_prefs"
    private const val KEY_LOCATION_MODE = "location_mode"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getLocationMode(context: Context): LocationMode =
        LocationMode.fromName(prefs(context).getString(KEY_LOCATION_MODE, null))

    fun setLocationMode(context: Context, mode: LocationMode) {
        prefs(context).edit().putString(KEY_LOCATION_MODE, mode.name).apply()
    }

    fun isOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }
}
