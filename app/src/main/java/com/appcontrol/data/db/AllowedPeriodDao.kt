package com.appcontrol.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedPeriodDao {

    @Query("SELECT * FROM allowed_periods WHERE packageName = :packageName")
    fun getByPackageName(packageName: String): Flow<List<AllowedPeriod>>

    @Query("SELECT * FROM allowed_periods WHERE packageName = :packageName")
    fun getByPackageNameSync(packageName: String): List<AllowedPeriod>

    @Insert
    suspend fun insert(period: AllowedPeriod)

    @Update
    suspend fun update(period: AllowedPeriod)

    @Delete
    suspend fun delete(period: AllowedPeriod)

    @Query("DELETE FROM allowed_periods WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
