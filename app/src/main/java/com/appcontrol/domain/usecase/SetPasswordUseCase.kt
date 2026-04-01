package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.AuthRepository
import javax.inject.Inject

class SetPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(password: String) {
        require(password.length >= 4) { "Password must be at least 4 characters" }
        val hash = PasswordHashUtil.sha256(password)
        authRepository.setPassword(hash)
    }
}
