package com.appcontrol.domain.repository

import com.appcontrol.data.db.AppSettings
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getSettings(): Flow<AppSettings?>
    suspend fun getSettingsSync(): AppSettings?
    suspend fun setPassword(passwordHash: String)
    suspend fun verifyPassword(inputHash: String): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setForcedLockEnabled(enabled: Boolean)
    suspend fun updateLockoutState(lockoutUntil: Long?, failedAttempts: Int)
    suspend fun initializeSettings()
}
