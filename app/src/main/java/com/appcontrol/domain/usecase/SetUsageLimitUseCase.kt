package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.RuleRepository
import javax.inject.Inject

class SetUsageLimitUseCase @Inject constructor(
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(packageName: String, limitMinutes: Int?) {
        if (limitMinutes != null) {
            require(limitMinutes in 1..1440) {
                "Usage limit must be between 1 and 1440 minutes, got $limitMinutes"
            }
        }
        ruleRepository.setUsageLimit(packageName, limitMinutes)
    }
}
