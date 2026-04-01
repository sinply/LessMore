package com.appcontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {

    @Query("SELECT * FROM usage_records WHERE date = :date AND packageName = :packageName")
    suspend fun getByDateAndPackageName(date: String, packageName: String): UsageRecord?

    @Query("SELECT * FROM usage_records WHERE date = :date")
    fun getByDate(date: String): Flow<List<UsageRecord>>

    @Query("SELECT * FROM usage_records WHERE date >= :startDate AND date <= :endDate")
    fun getWeeklyStats(startDate: String, endDate: String): Flow<List<UsageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: UsageRecord)

    @Query("UPDATE usage_records SET usageDurationSeconds = usageDurationSeconds + :additionalSeconds WHERE packageName = :packageName AND date = :date")
    suspend fun updateUsageDuration(packageName: String, date: String, additionalSeconds: Long)

    @Query("UPDATE usage_records SET openCount = openCount + 1 WHERE packageName = :packageName AND date = :date")
    suspend fun incrementOpenCount(packageName: String, date: String)

    @Query("DELETE FROM usage_records WHERE date < :date")
    suspend fun deleteOlderThan(date: String)
}
