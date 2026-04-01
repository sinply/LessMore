package com.appcontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsSync(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettings)

    @Query("UPDATE app_settings SET passwordHash = :passwordHash WHERE id = 1")
    suspend fun updatePasswordHash(passwordHash: String)

    @Query("UPDATE app_settings SET biometricEnabled = :enabled WHERE id = 1")
    suspend fun updateBiometricEnabled(enabled: Boolean)

    @Query("UPDATE app_settings SET forcedLockEnabled = :enabled WHERE id = 1")
    suspend fun updateForcedLockEnabled(enabled: Boolean)

    @Query("UPDATE app_settings SET lockoutUntil = :lockoutUntil, failedAttempts = :failedAttempts WHERE id = 1")
    suspend fun updateLockoutState(lockoutUntil: Long?, failedAttempts: Int)
}
