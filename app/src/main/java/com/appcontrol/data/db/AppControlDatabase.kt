package com.appcontrol.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TargetApp::class, AllowedPeriod::class, UsageRecord::class, AppSettings::class],
    version = 1,
    exportSchema = false
)
abstract class AppControlDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao
    abstract fun allowedPeriodDao(): AllowedPeriodDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun appSettingsDao(): AppSettingsDao
}
