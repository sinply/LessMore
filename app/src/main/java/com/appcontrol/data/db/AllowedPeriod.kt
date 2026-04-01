package com.appcontrol.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "allowed_periods",
    foreignKeys = [
        ForeignKey(
            entity = TargetApp::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class AllowedPeriod(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val startTime: String, // format "HH:mm"
    val endTime: String    // format "HH:mm"
)
