package com.appcontrol.domain.usecase

import com.appcontrol.data.db.TargetApp
import com.appcontrol.domain.repository.AppRepository
import javax.inject.Inject

class ToggleWhitelistUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, isWhitelisted: Boolean, appName: String? = null) {
        if (isWhitelisted && appRepository.getTargetApp(packageName) == null) {
            appRepository.addTargetApp(
                TargetApp(
                    packageName = packageName,
                    appName = appName ?: packageName,
                    isWhitelisted = true
                )
            )
            return
        }
        appRepository.toggleWhitelist(packageName, isWhitelisted)
    }
}
