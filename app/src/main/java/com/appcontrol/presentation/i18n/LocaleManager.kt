package com.appcontrol.presentation.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {
    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_ZH = "zh-CN"

    fun applySavedLocale(context: Context) {
        val saved = getSavedLanguage(context)
        applyLanguage(saved)
    }

    fun getSavedLanguage(context: Context): String {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, LANGUAGE_SYSTEM)
            ?: LANGUAGE_SYSTEM
    }

    fun setLanguage(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
        applyLanguage(languageTag)
    }

    private fun applyLanguage(languageTag: String) {
        val locales = if (languageTag == LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
