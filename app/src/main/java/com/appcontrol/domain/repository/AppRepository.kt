package com.appcontrol.domain.repository

import com.appcontrol.data.db.TargetApp
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getAllTargetApps(): Flow<List<TargetApp>>
    suspend fun getTargetApp(packageName: String): TargetApp?
    fun getWhitelistedApps(): Flow<List<TargetApp>>
    suspend fun addTargetApp(app: TargetApp)
    suspend fun removeTargetApp(packageName: String)
    suspend fun toggleWhitelist(packageName: String, isWhitelisted: Boolean)
}
