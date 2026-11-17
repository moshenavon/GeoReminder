package com.example

import com.example.data.GeoZoneEntity
import com.example.util.LocationEvaluator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {

    @Test
    fun distanceCalculation_identifiesInsideAndOutsideRadius() {
        val centerLat = 32.0853
        val centerLon = 34.7818

        // Same point -> ~0m
        val zeroDist = LocationEvaluator.calculateDistanceMeters(
            centerLat, centerLon, centerLat, centerLon
        )
        assertTrue(zeroDist < 1f)

        // ~111m north (0.001 deg lat)
        val nearDist = LocationEvaluator.calculateDistanceMeters(
            centerLat, centerLon, centerLat + 0.001, centerLon
        )
        assertTrue(nearDist in 95f..125f)
    }

    @Test
    fun timeWindow_checksActiveHoursCorrectly() {
        val zone = GeoZoneEntity(
            name = "בדיקה",
            latitude = 32.0,
            longitude = 34.0,
            radiusMeters = 200f,
            startHour = 8,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            activeDaysCsv = "1,2,3,4,5,6,7"
        )

        val insideCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(zone.isWithinTimeWindow(insideCal.timeInMillis))

        val outsideCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 15)
        }
        assertFalse(zone.isWithinTimeWindow(outsideCal.timeInMillis))
    }
}
