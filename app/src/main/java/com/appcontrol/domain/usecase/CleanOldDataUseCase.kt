package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.UsageRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class CleanOldDataUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend operator fun invoke() {
        val cutoffDate = LocalDate.now().minusDays(30).format(dateFormatter)
        usageRepository.cleanOldData(cutoffDate)
    }
}
