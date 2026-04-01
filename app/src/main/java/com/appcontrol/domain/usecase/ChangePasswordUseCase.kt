package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.AuthRepository
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): Boolean {
        val oldHash = PasswordHashUtil.sha256(oldPassword)
        val verified = authRepository.verifyPassword(oldHash)
        if (!verified) return false

        require(newPassword.length >= 4) { "New password must be at least 4 characters" }
        val newHash = PasswordHashUtil.sha256(newPassword)
        authRepository.setPassword(newHash)
        return true
    }
}
