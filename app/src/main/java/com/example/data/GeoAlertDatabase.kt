package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GeoZoneEntity::class, GeoEventLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GeoAlertDatabase : RoomDatabase() {
    abstract fun geoAlertDao(): GeoAlertDao

    companion object {
        @Volatile
        private var INSTANCE: GeoAlertDatabase? = null

        fun getInstance(context: Context): GeoAlertDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GeoAlertDatabase::class.java,
                    "geo_alert_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
