package com.appcontrol.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetAppDao {

    @Query("SELECT * FROM target_apps")
    fun getAll(): Flow<List<TargetApp>>

    @Query("SELECT * FROM target_apps WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): TargetApp?

    @Query("SELECT * FROM target_apps WHERE isWhitelisted = 1")
    fun getWhitelistedApps(): Flow<List<TargetApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: TargetApp)

    @Delete
    suspend fun delete(app: TargetApp)

    @Query("DELETE FROM target_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("UPDATE target_apps SET isWhitelisted = :isWhitelisted WHERE packageName = :packageName")
    suspend fun updateWhitelistStatus(packageName: String, isWhitelisted: Boolean)

    @Query("UPDATE target_apps SET usageLimitMinutes = :limitMinutes WHERE packageName = :packageName")
    suspend fun updateUsageLimit(packageName: String, limitMinutes: Int?)
}
