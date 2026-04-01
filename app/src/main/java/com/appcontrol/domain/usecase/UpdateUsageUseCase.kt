package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.UsageRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class UpdateUsageUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend operator fun invoke(packageName: String) {
        val today = LocalDate.now().format(dateFormatter)
        usageRepository.ensureRecordExists(packageName, today)
        usageRepository.updateUsageDuration(packageName, today, 1)
    }
}
