package com.appcontrol.domain.usecase

import com.appcontrol.domain.repository.AppRepository
import javax.inject.Inject

class RemoveTargetAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String) {
        appRepository.removeTargetApp(packageName)
    }
}
