package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.AppRepository
import com.appcontrol.domain.repository.UsageRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class CheckUsageWarningUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val usageRepository: UsageRepository
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend operator fun invoke(packageName: String): Boolean {
        val targetApp = appRepository.getTargetApp(packageName) ?: return false
        val limitMinutes = targetApp.usageLimitMinutes ?: return false

        val today = LocalDate.now().format(dateFormatter)
        val record = usageRepository.getUsageRecord(packageName, today)
        val usedSeconds = record?.usageDurationSeconds ?: 0L
        val thresholdSeconds = (limitMinutes * 60 * 0.8).toLong()

        return usedSeconds >= thresholdSeconds
    }
}
