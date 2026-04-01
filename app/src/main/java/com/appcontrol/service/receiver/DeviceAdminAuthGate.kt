package com.appcontrol.service.receiver

import android.content.Context

object DeviceAdminAuthGate {
    private const val PREFS_NAME = "device_admin_gate"
    private const val KEY_DISABLE_AUTH_UNTIL = "disable_auth_until"
    private const val DEFAULT_AUTH_WINDOW_MS = 60_000L

    fun authorizeDisable(context: Context, windowMs: Long = DEFAULT_AUTH_WINDOW_MS) {
        val expiresAt = System.currentTimeMillis() + windowMs
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DISABLE_AUTH_UNTIL, expiresAt)
            .apply()
    }

    fun consumeDisableAuthorization(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiresAt = prefs.getLong(KEY_DISABLE_AUTH_UNTIL, 0L)
        val allowed = expiresAt > System.currentTimeMillis()
        prefs.edit().remove(KEY_DISABLE_AUTH_UNTIL).apply()
        return allowed
    }
}
