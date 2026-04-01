package com.appcontrol.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_apps")
data class TargetApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val iconUri: String? = null,
    val isWhitelisted: Boolean = false,
    val usageLimitMinutes: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
