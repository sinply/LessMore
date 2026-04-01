package com.appcontrol.data.repository

import com.appcontrol.data.db.TargetApp
import com.appcontrol.data.db.TargetAppDao
import com.appcontrol.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppRepositoryImpl @Inject constructor(
    private val targetAppDao: TargetAppDao
) : AppRepository {

    override fun getAllTargetApps(): Flow<List<TargetApp>> =
        targetAppDao.getAll()

    override suspend fun getTargetApp(packageName: String): TargetApp? =
        targetAppDao.getByPackageName(packageName)

    override fun getWhitelistedApps(): Flow<List<TargetApp>> =
        targetAppDao.getWhitelistedApps()

    override suspend fun addTargetApp(app: TargetApp) =
        targetAppDao.insert(app)

    override suspend fun removeTargetApp(packageName: String) =
        targetAppDao.deleteByPackageName(packageName)

    override suspend fun toggleWhitelist(packageName: String, isWhitelisted: Boolean) =
        targetAppDao.updateWhitelistStatus(packageName, isWhitelisted)
}
