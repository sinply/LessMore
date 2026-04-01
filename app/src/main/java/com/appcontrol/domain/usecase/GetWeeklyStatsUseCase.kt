package com.appcontrol.domain.usecase

import com.appcontrol.data.db.UsageRecord
import com.appcontrol.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class GetWeeklyStatsUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    operator fun invoke(): Flow<List<UsageRecord>> {
        val today = LocalDate.now()
        val startDate = today.minusDays(6).format(dateFormatter)
        val endDate = today.format(dateFormatter)
        return usageRepository.getWeeklyStats(startDate, endDate)
    }
}
