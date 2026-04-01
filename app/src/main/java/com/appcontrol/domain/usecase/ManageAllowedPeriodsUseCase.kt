package com.appcontrol.domain.usecase

import com.appcontrol.data.db.AllowedPeriod
import com.appcontrol.domain.repository.RuleRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageAllowedPeriodsUseCase @Inject constructor(
    private val ruleRepository: RuleRepository
) {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)

    fun getPeriodsForApp(packageName: String): Flow<List<AllowedPeriod>> {
        return ruleRepository.getAllowedPeriods(packageName)
    }

    suspend fun addPeriod(period: AllowedPeriod) {
        validatePeriod(period)
        ruleRepository.addAllowedPeriod(period)
    }

    suspend fun updatePeriod(period: AllowedPeriod) {
        validatePeriod(period)
        ruleRepository.updateAllowedPeriod(period)
    }

    suspend fun removePeriod(period: AllowedPeriod) {
        ruleRepository.removeAllowedPeriod(period)
    }

    private fun validatePeriod(period: AllowedPeriod) {
        val start = LocalTime.parse(period.startTime, timeFormatter)
        val end = LocalTime.parse(period.endTime, timeFormatter)
        require(start != end) { "开始时间和结束时间不能相同" }
    }
}
