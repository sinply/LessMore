package com.appcontrol.data.repository

import com.appcontrol.data.db.AllowedPeriod
import com.appcontrol.data.db.AllowedPeriodDao
import com.appcontrol.data.db.TargetAppDao
import com.appcontrol.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RuleRepositoryImpl @Inject constructor(
    private val targetAppDao: TargetAppDao,
    private val allowedPeriodDao: AllowedPeriodDao
) : RuleRepository {

    override suspend fun setUsageLimit(packageName: String, limitMinutes: Int?) =
        targetAppDao.updateUsageLimit(packageName, limitMinutes)

    override fun getAllowedPeriods(packageName: String): Flow<List<AllowedPeriod>> =
        allowedPeriodDao.getByPackageName(packageName)

    override fun getAllowedPeriodsSync(packageName: String): List<AllowedPeriod> =
        allowedPeriodDao.getByPackageNameSync(packageName)

    override suspend fun addAllowedPeriod(period: AllowedPeriod) =
        allowedPeriodDao.insert(period)

    override suspend fun updateAllowedPeriod(period: AllowedPeriod) =
        allowedPeriodDao.update(period)

    override suspend fun removeAllowedPeriod(period: AllowedPeriod) =
        allowedPeriodDao.delete(period)
}
