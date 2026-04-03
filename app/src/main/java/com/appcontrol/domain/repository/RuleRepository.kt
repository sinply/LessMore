package com.appcontrol.domain.repository

import com.appcontrol.data.db.AllowedPeriod
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    suspend fun setUsageLimit(packageName: String, limitMinutes: Int?)
    fun getAllowedPeriods(packageName: String): Flow<List<AllowedPeriod>>
    suspend fun getAllowedPeriodsSync(packageName: String): List<AllowedPeriod>
    suspend fun addAllowedPeriod(period: AllowedPeriod)
    suspend fun updateAllowedPeriod(period: AllowedPeriod)
    suspend fun removeAllowedPeriod(period: AllowedPeriod)
}
