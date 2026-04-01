package com.appcontrol.domain.usecase

import com.appcontrol.data.db.UsageRecord
import com.appcontrol.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDailyStatsUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    operator fun invoke(date: String): Flow<List<UsageRecord>> {
        return usageRepository.getDailyStats(date)
    }
}
