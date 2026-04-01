package com.appcontrol

import android.app.Application
import com.appcontrol.presentation.i18n.LocaleManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AppControlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleManager.applySavedLocale(this)
    }
}
