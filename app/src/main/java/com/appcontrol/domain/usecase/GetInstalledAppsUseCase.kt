package com.appcontrol.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import com.appcontrol.domain.model.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): List<InstalledAppInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { appInfo ->
                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    icon = appInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.appName }
    }
}
