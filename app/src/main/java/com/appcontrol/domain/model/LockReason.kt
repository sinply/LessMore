package com.appcontrol.domain.model

sealed class LockReason {
    data class UsageLimitExceeded(
        val usedSeconds: Long,
        val limitMinutes: Int
    ) : LockReason()

    data class OutsideAllowedPeriod(
        val nextPeriodStart: String?
    ) : LockReason()

    data object NotLocked : LockReason()
}
