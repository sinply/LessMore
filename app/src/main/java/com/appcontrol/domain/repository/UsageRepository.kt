package com.appcontrol.domain.repository

import com.appcontrol.data.db.UsageRecord
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    suspend fun getUsageRecord(packageName: String, date: String): UsageRecord?
    fun getDailyStats(date: String): Flow<List<UsageRecord>>
    fun getWeeklyStats(startDate: String, endDate: String): Flow<List<UsageRecord>>
    suspend fun updateUsageDuration(packageName: String, date: String, additionalSeconds: Long)
    suspend fun incrementOpenCount(packageName: String, date: String)
    suspend fun ensureRecordExists(packageName: String, date: String)
    suspend fun cleanOldData(beforeDate: String)
}
