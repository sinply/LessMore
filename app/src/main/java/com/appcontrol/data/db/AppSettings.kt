package com.appcontrol.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val passwordHash: String? = null,
    val biometricEnabled: Boolean = false,
    val forcedLockEnabled: Boolean = false,
    val lockoutUntil: Long? = null,
    val failedAttempts: Int = 0
)
