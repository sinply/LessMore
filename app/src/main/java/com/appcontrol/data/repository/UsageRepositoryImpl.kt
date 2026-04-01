package com.appcontrol.data.repository

import com.appcontrol.data.db.UsageRecord
import com.appcontrol.data.db.UsageRecordDao
import com.appcontrol.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UsageRepositoryImpl @Inject constructor(
    private val usageRecordDao: UsageRecordDao
) : UsageRepository {

    override suspend fun getUsageRecord(packageName: String, date: String): UsageRecord? =
        usageRecordDao.getByDateAndPackageName(date, packageName)

    override fun getDailyStats(date: String): Flow<List<UsageRecord>> =
        usageRecordDao.getByDate(date)

    override fun getWeeklyStats(startDate: String, endDate: String): Flow<List<UsageRecord>> =
        usageRecordDao.getWeeklyStats(startDate, endDate)

    override suspend fun updateUsageDuration(packageName: String, date: String, additionalSeconds: Long) =
        usageRecordDao.updateUsageDuration(packageName, date, additionalSeconds)

    override suspend fun incrementOpenCount(packageName: String, date: String) =
        usageRecordDao.incrementOpenCount(packageName, date)

    override suspend fun ensureRecordExists(packageName: String, date: String) {
        val existing = usageRecordDao.getByDateAndPackageName(date, packageName)
        if (existing == null) {
            usageRecordDao.insertOrUpdate(
                UsageRecord(
                    packageName = packageName,
                    date = date,
                    usageDurationSeconds = 0,
                    openCount = 0
                )
            )
        }
    }

    override suspend fun cleanOldData(beforeDate: String) =
        usageRecordDao.deleteOlderThan(beforeDate)
}
