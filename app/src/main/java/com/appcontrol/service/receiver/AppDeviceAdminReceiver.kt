package com.appcontrol.service.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.appcontrol.R
import com.appcontrol.domain.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppDeviceAdminReceiver : DeviceAdminReceiver() {
    @Inject lateinit var authRepository: AuthRepository

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val authorized = DeviceAdminAuthGate.consumeDisableAuthorization(context)
        return if (authorized) {
            context.getString(R.string.device_admin_disable_authorized)
        } else {
            context.getString(R.string.device_admin_disable_requires_auth)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, context.getString(R.string.device_admin_enabled), Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, context.getString(R.string.device_admin_disabled), Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authRepository.setForcedLockEnabled(false)
            }.onFailure {
                Log.w("AppDeviceAdminReceiver", "Failed to reset forced lock state", it)
            }
        }
    }
}
