package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.AuthRepository
import javax.inject.Inject

class ToggleBiometricUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        authRepository.setBiometricEnabled(enabled)
    }
}
