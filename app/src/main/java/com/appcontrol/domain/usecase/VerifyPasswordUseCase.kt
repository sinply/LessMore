package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.AuthRepository
import javax.inject.Inject

class VerifyPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    sealed class VerifyResult {
        data object Success : VerifyResult()
        data class WrongPassword(val remainingAttempts: Int) : VerifyResult()
        data class LockedOut(val lockoutUntilMillis: Long) : VerifyResult()
    }

    suspend operator fun invoke(password: String): VerifyResult {
        val settings = authRepository.getSettingsSync()

        // Check if currently locked out
        val lockoutUntil = settings?.lockoutUntil
        if (lockoutUntil != null && lockoutUntil > System.currentTimeMillis()) {
            return VerifyResult.LockedOut(lockoutUntil)
        }

        val hash = PasswordHashUtil.sha256(password)
        val isCorrect = authRepository.verifyPassword(hash)

        return if (isCorrect) {
            authRepository.updateLockoutState(lockoutUntil = null, failedAttempts = 0)
            VerifyResult.Success
        } else {
            val currentFailed = (settings?.failedAttempts ?: 0) + 1
            if (currentFailed >= 5) {
                val newLockoutUntil = System.currentTimeMillis() + 15 * 60 * 1000L
                authRepository.updateLockoutState(lockoutUntil = newLockoutUntil, failedAttempts = currentFailed)
                VerifyResult.LockedOut(newLockoutUntil)
            } else {
                authRepository.updateLockoutState(lockoutUntil = null, failedAttempts = currentFailed)
                VerifyResult.WrongPassword(5 - currentFailed)
            }
        }
    }
}
