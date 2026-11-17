package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

enum class RingtonePreset(
    val titleHe: String,
    val subtitleHe: String
) {
    RADAR_CHIME(
        titleHe = "מנגינת רדאר עולה",
        subtitleHe = "צליל אלקטרוני הרמוני ברור (מומלץ לכניסה)"
    ),
    SONAR_PULSE(
        titleHe = "פולס סונאר טקטי",
        subtitleHe = "צפצוף סונאר כפול חד וממוקד"
    ),
    SIREN_ALERT(
        titleHe = "התראת סירנה דחופה",
        subtitleHe = "צליל אזהרה בולט במיוחד (מומלץ ליציאה)"
    ),
    SYSTEM_NOTIFICATION(
        titleHe = "צליל התראה של המכשיר",
        subtitleHe = "צליל ברירת המחדל להתראות באנדרואיד"
    ),
    SYSTEM_ALARM(
        titleHe = "שעון מעורר של המכשיר",
        subtitleHe = "צלצול חזק ומתמשך מתוך שעון המעורר"
    ),
    SYSTEM_RINGTONE(
        titleHe = "צלצול טלפון של המכשיר",
        subtitleHe = "צלצול השיחות הראשי המוגדר במכשיר"
    ),
    CUSTOM_URI(
        titleHe = "צלצול מותאם אישית מהמכשיר",
        subtitleHe = "בחירה מתוך רשימת כל הצלצולים במכשיר"
    );

    companion object {
        fun fromName(name: String): RingtonePreset {
            return entries.find { it.name == name } ?: RADAR_CHIME
        }
    }
}

enum class ZoneOccupancyState(val labelHe: String) {
    UNKNOWN("טרם נדגם"),
    INSIDE("בתוך הרדיוס"),
    OUTSIDE("מחוץ לרדיוס");

    companion object {
        fun fromName(name: String): ZoneOccupancyState {
            return entries.find { it.name == name } ?: UNKNOWN
        }
    }
}

@Entity(tableName = "geo_zones")
data class GeoZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val activeDaysCsv: String = "1,2,3,4,5,6,7", // 1=Sun .. 7=Sat (Calendar.SUNDAY..SATURDAY)
    val samplingIntervalSeconds: Int = 15,
    val alertOnEntry: Boolean = true,
    val alertOnExit: Boolean = true,
    val entryRingtoneType: String = RingtonePreset.RADAR_CHIME.name,
    val entryCustomRingtoneUri: String = "",
    val entryCustomRingtoneTitle: String = "",
    val exitRingtoneType: String = RingtonePreset.SIREN_ALERT.name,
    val exitCustomRingtoneUri: String = "",
    val exitCustomRingtoneTitle: String = "",
    val vibrate: Boolean = true,
    val isEnabled: Boolean = true,
    val lastKnownState: String = ZoneOccupancyState.UNKNOWN.name,
    val lastDistanceMeters: Float? = null,
    val lastSampledAt: Long? = null
) {
    fun getActiveDaysSet(): Set<Int> {
        return activeDaysCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    fun formatTimeWindow(): String {
        return String.format("%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    fun entryRingtoneDisplayName(): String {
        val preset = RingtonePreset.fromName(entryRingtoneType)
        return if (preset == RingtonePreset.CUSTOM_URI && entryCustomRingtoneTitle.isNotBlank()) {
            "מותאם: $entryCustomRingtoneTitle"
        } else {
            preset.titleHe
        }
    }

    fun exitRingtoneDisplayName(): String {
        val preset = RingtonePreset.fromName(exitRingtoneType)
        return if (preset == RingtonePreset.CUSTOM_URI && exitCustomRingtoneTitle.isNotBlank()) {
            "מותאם: $exitCustomRingtoneTitle"
        } else {
            preset.titleHe
        }
    }

    /**
     * Checks whether the given timestamp falls within the configured active days and
     * start/end time window (supports overnight windows like 22:00 - 06:00 as well as standard 08:00 - 17:00).
     */
    fun isWithinTimeWindow(timeMillis: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 (Sun) .. 7 (Sat)
        val activeDays = getActiveDaysSet()
        if (activeDays.isNotEmpty() && !activeDays.contains(dayOfWeek)) {
            return false
        }

        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnight range (e.g., 22:00 to 06:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }
}

@Entity(tableName = "geo_event_logs")
data class GeoEventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zoneId: Long,
    val zoneName: String,
    val eventType: String, // "ENTRY" or "EXIT"
    val timestamp: Long = System.currentTimeMillis(),
    val distanceMeters: Float,
    val radiusMeters: Float,
    val ringtoneLabel: String,
    val latitude: Double,
    val longitude: Double
)
