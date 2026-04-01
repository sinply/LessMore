package com.appcontrol.domain.usecase

import android.content.Context
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CheckBiometricAvailabilityUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
