package com.appcontrol.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appcontrol.service.monitor.MonitorPreferences
import com.appcontrol.service.monitor.MonitorService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && MonitorPreferences.isMonitorEnabled(context)) {
            MonitorService.startService(context)
        }
    }
}
