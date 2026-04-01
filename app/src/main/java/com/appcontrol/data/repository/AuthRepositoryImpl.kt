package com.appcontrol.data.repository

import com.appcontrol.data.db.AppSettings
import com.appcontrol.data.db.AppSettingsDao
import com.appcontrol.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val appSettingsDao: AppSettingsDao
) : AuthRepository {

    override fun getSettings(): Flow<AppSettings?> =
        appSettingsDao.getSettings()

    override suspend fun getSettingsSync(): AppSettings? =
        appSettingsDao.getSettingsSync()

    override suspend fun setPassword(passwordHash: String) {
        ensureInitialized()
        appSettingsDao.updatePasswordHash(passwordHash)
    }

    override suspend fun verifyPassword(inputHash: String): Boolean {
        val settings = appSettingsDao.getSettingsSync()
        return settings?.passwordHash == inputHash
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        ensureInitialized()
        appSettingsDao.updateBiometricEnabled(enabled)
    }

    override suspend fun setForcedLockEnabled(enabled: Boolean) {
        ensureInitialized()
        appSettingsDao.updateForcedLockEnabled(enabled)
    }

    override suspend fun updateLockoutState(lockoutUntil: Long?, failedAttempts: Int) {
        ensureInitialized()
        appSettingsDao.updateLockoutState(lockoutUntil, failedAttempts)
    }

    override suspend fun initializeSettings() {
        ensureInitialized()
    }

    private suspend fun ensureInitialized() {
        val existing = appSettingsDao.getSettingsSync()
        if (existing == null) {
            appSettingsDao.insertOrUpdate(AppSettings())
        }
    }
}
