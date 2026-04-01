package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.UsageRepository
import javax.inject.Inject

/**
 * Called at midnight (00:00) to handle daily usage reset.
 * Since usage records are stored per-date, new day records are
 * created on demand via ensureRecordExists. This use case serves
 * as a hook for any future new-day logic (e.g., sending daily
 * summary notifications, archiving data).
 */
class ResetDailyUsageUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    suspend operator fun invoke() {
        // New day records are created on demand via ensureRecordExists.
        // No explicit reset is needed since records are per-date.
    }
}
