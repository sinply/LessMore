package com.appcontrol.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["packageName", "date"], unique = true)]
)
data class UsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val date: String, // format "yyyy-MM-dd"
    val usageDurationSeconds: Long = 0,
    val openCount: Int = 0
)
