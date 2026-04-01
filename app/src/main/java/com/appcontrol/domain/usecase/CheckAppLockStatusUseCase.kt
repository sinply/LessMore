package com.appcontrol.domain.usecase

import com.appcontrol.domain.model.LockReason
import com.appcontrol.domain.repository.AppRepository
import com.appcontrol.domain.repository.RuleRepository
import com.appcontrol.domain.repository.UsageRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class CheckAppLockStatusUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val ruleRepository: RuleRepository,
    private val usageRepository: UsageRepository
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend operator fun invoke(packageName: String): LockReason {
        // a. Get TargetApp - if null or whitelisted, return NotLocked
        val targetApp = appRepository.getTargetApp(packageName) ?: return LockReason.NotLocked
        if (targetApp.isWhitelisted) return LockReason.NotLocked

        val now = LocalTime.now()

        // b. Check AllowedPeriods - if periods exist and current time is outside all, return OutsideAllowedPeriod
        val periods = ruleRepository.getAllowedPeriodsSync(packageName)
        if (periods.isNotEmpty()) {
            val isWithinAnyPeriod = periods.any { period ->
                val start = LocalTime.parse(period.startTime, timeFormatter)
                val end = LocalTime.parse(period.endTime, timeFormatter)
                isTimeInPeriod(now, start, end)
            }
            if (!isWithinAnyPeriod) {
                val nextStart = findNextPeriodStart(now, periods.map { it.startTime })
                return LockReason.OutsideAllowedPeriod(nextPeriodStart = nextStart)
            }
        }

        // c. Check UsageLimit
        val limitMinutes = targetApp.usageLimitMinutes
        if (limitMinutes != null) {
            val today = LocalDate.now().format(dateFormatter)
            val record = usageRepository.getUsageRecord(packageName, today)
            val usedSeconds = record?.usageDurationSeconds ?: 0L
            if (usedSeconds >= limitMinutes * 60L) {
                return LockReason.UsageLimitExceeded(
                    usedSeconds = usedSeconds,
                    limitMinutes = limitMinutes
                )
            }
        }

        // d. Otherwise not locked
        return LockReason.NotLocked
    }

    private fun isTimeInPeriod(current: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (!start.isAfter(end)) {
            // Normal period, e.g. 08:00 - 18:00
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            // Overnight period, e.g. 22:00 - 06:00
            !current.isBefore(start) || !current.isAfter(end)
        }
    }

    private fun findNextPeriodStart(currentTime: LocalTime, startTimes: List<String>): String? {
        if (startTimes.isEmpty()) return null
        val parsedStarts = startTimes.map { LocalTime.parse(it, timeFormatter) }
        // Find the earliest start time that is after current time
        val futureStarts = parsedStarts.filter { it.isAfter(currentTime) }
        return if (futureStarts.isNotEmpty()) {
            futureStarts.min().format(timeFormatter)
        } else {
            // All periods start before current time, so next is the earliest one (tomorrow)
            parsedStarts.min().format(timeFormatter)
        }
    }
}
