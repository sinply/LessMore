package com.appcontrol.domain.usecase

import com.appcontrol.data.db.TargetApp
import com.appcontrol.domain.repository.AppRepository
import javax.inject.Inject

class AddTargetAppUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(app: TargetApp) {
        appRepository.addTargetApp(app)
    }
}
