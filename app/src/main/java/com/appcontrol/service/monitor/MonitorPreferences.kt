package com.appcontrol.service.monitor

import android.content.Context

object MonitorPreferences {
    private const val PREFS_NAME = "monitor_preferences"
    private const val KEY_MONITOR_ENABLED = "monitor_enabled"

    fun isMonitorEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITOR_ENABLED, true)
    }

    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONITOR_ENABLED, enabled)
            .apply()
    }
}
