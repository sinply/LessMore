package com.appcontrol.service.monitor

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.appcontrol.R
import com.appcontrol.domain.model.LockReason
import com.appcontrol.domain.usecase.CheckAppLockStatusUseCase
import com.appcontrol.domain.usecase.CheckUsageWarningUseCase
import com.appcontrol.domain.usecase.CleanOldDataUseCase
import com.appcontrol.domain.usecase.ResetDailyUsageUseCase
import com.appcontrol.domain.usecase.UpdateUsageUseCase
import com.appcontrol.service.overlay.LockOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : LifecycleService() {

    companion object {
        private const val TAG = "MonitorService"
        const val MONITOR_CHANNEL_ID = "monitor_channel"
        const val WARNING_CHANNEL_ID = "warning_channel"
        private const val MONITOR_NOTIFICATION_ID = 1
        private const val WARNING_NOTIFICATION_ID = 2
        private const val MIDNIGHT_ALARM_REQUEST_CODE = 1001
        private const val POLLING_INTERVAL_MS = 1000L
        private const val LOCK_STATE_PREFS = "monitor_lock_state"
        private const val KEY_LOCKED_PACKAGE = "locked_package"
        private const val KEY_LOCK_REASON_TYPE = "lock_reason_type"
        private const val KEY_USED_SECONDS = "used_seconds"
        private const val KEY_LIMIT_MINUTES = "limit_minutes"
        private const val KEY_NEXT_PERIOD_START = "next_period_start"
        private const val KEY_FORCED_MODE = "forced_mode"

        private const val LOCK_REASON_USAGE_LIMIT = "usage_limit"
        private const val LOCK_REASON_ALLOWED_PERIOD = "outside_period"

        fun startService(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                Log.e(TAG, "Failed to start monitor service.", it)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.stopService(intent)
        }
    }

    @Inject lateinit var checkAppLockStatus: CheckAppLockStatusUseCase
    @Inject lateinit var updateUsage: UpdateUsageUseCase
    @Inject lateinit var checkUsageWarning: CheckUsageWarningUseCase
    @Inject lateinit var resetDailyUsage: ResetDailyUsageUseCase
    @Inject lateinit var cleanOldData: CleanOldDataUseCase
    @Inject lateinit var lockOverlayManager: LockOverlayManager
    @Inject lateinit var authRepository: com.appcontrol.domain.repository.AuthRepository

    private var pollingJob: Job? = null
    private val warnedApps = mutableSetOf<String>()
    private var closeSystemDialogsReceiverRegistered = false
    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            val reason = intent.getStringExtra("reason")
            if ((reason == "homekey" || reason == "recentapps") &&
                lockOverlayManager.isShowing() &&
                lockOverlayManager.isInForcedMode()
            ) {
                lockOverlayManager.reassertOverlay()
                lifecycleScope.launch {
                    restoreLockStateIfNeeded()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(MONITOR_NOTIFICATION_ID, buildMonitorNotification())
        registerSystemDialogReceiver()
        lifecycleScope.launch {
            restoreLockStateIfNeeded()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!MonitorPreferences.isMonitorEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startPolling()
        scheduleMidnightAlarm()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, MonitorService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            0,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            pendingIntent
        )
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        pollingJob = null
        unregisterSystemDialogReceiver()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.monitor_channel_description)
        }
        notificationManager.createNotificationChannel(monitorChannel)

        val warningChannel = NotificationChannel(
            WARNING_CHANNEL_ID,
            getString(R.string.warning_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.warning_channel_description)
        }
        notificationManager.createNotificationChannel(warningChannel)
    }

    private fun buildMonitorNotification(): Notification {
        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    pollForegroundApp()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during polling", e)
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollForegroundApp() {
        if (!hasUsageStatsPermission()) return
        val foregroundPackage = getForegroundPackageName() ?: return
        // Skip our own package
        if (foregroundPackage == packageName) return

        val lockReason = checkAppLockStatus(foregroundPackage)

        when (lockReason) {
            is LockReason.NotLocked -> {
                withContext(Dispatchers.Main.immediate) {
                    if (lockOverlayManager.isShowing()) {
                        lockOverlayManager.hideLockScreen()
                    }
                }
                clearPersistedLockState()
                updateUsage(foregroundPackage)
                val shouldWarn = checkUsageWarning(foregroundPackage)
                if (shouldWarn && foregroundPackage !in warnedApps) {
                    sendWarningNotification(foregroundPackage)
                    warnedApps.add(foregroundPackage)
                }
            }
            is LockReason.UsageLimitExceeded,
            is LockReason.OutsideAllowedPeriod -> {
                val isForcedLock = authRepository.getSettingsSync()?.forcedLockEnabled == true
                withContext(Dispatchers.Main.immediate) {
                    lockOverlayManager.showLockScreen(lockReason, isForcedLock)
                }
                persistLockState(foregroundPackage, lockReason, isForcedLock)
                Log.d(TAG, "App $foregroundPackage locked: $lockReason")
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        if (!hasUsageStatsPermission()) return null
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 10_000

        try {
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()
            var packageName: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    packageName = event.packageName
                }
            }
            if (!packageName.isNullOrBlank()) {
                return packageName
            }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Usage stats permission missing when querying foreground app.", securityException)
            return null
        }

        return try {
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                beginTime,
                endTime
            )
            if (usageStats.isNullOrEmpty()) {
                null
            } else {
                usageStats
                    .filter { it.lastTimeUsed > 0 }
                    .maxByOrNull { it.lastTimeUsed }
                    ?.packageName
            }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Usage stats permission missing when querying usage summary.", securityException)
            null
        }
    }

    private fun sendWarningNotification(packageName: String) {
        if (
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle(getString(R.string.warning_notification_title))
            .setContentText(getString(R.string.warning_notification_text, packageName))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(
            WARNING_NOTIFICATION_ID + packageName.hashCode(),
            notification
        )
    }

    private fun scheduleMidnightAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MidnightResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            MIDNIGHT_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun registerSystemDialogReceiver() {
        if (closeSystemDialogsReceiverRegistered) return
        registerReceiver(closeSystemDialogsReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        closeSystemDialogsReceiverRegistered = true
    }

    private fun unregisterSystemDialogReceiver() {
        if (!closeSystemDialogsReceiverRegistered) return
        runCatching { unregisterReceiver(closeSystemDialogsReceiver) }
        closeSystemDialogsReceiverRegistered = false
    }

    private fun persistLockState(
        packageName: String,
        lockReason: LockReason,
        isForcedMode: Boolean
    ) {
        val prefs = getSharedPreferences(LOCK_STATE_PREFS, Context.MODE_PRIVATE)
        val reasonType = when (lockReason) {
            is LockReason.UsageLimitExceeded -> LOCK_REASON_USAGE_LIMIT
            is LockReason.OutsideAllowedPeriod -> LOCK_REASON_ALLOWED_PERIOD
            is LockReason.NotLocked -> null
        }
        val unchanged = when (lockReason) {
            is LockReason.UsageLimitExceeded -> {
                prefs.getString(KEY_LOCKED_PACKAGE, null) == packageName &&
                    prefs.getBoolean(KEY_FORCED_MODE, false) == isForcedMode &&
                    prefs.getString(KEY_LOCK_REASON_TYPE, null) == reasonType &&
                    prefs.getLong(KEY_USED_SECONDS, Long.MIN_VALUE) == lockReason.usedSeconds &&
                    prefs.getInt(KEY_LIMIT_MINUTES, Int.MIN_VALUE) == lockReason.limitMinutes
            }
            is LockReason.OutsideAllowedPeriod -> {
                prefs.getString(KEY_LOCKED_PACKAGE, null) == packageName &&
                    prefs.getBoolean(KEY_FORCED_MODE, false) == isForcedMode &&
                    prefs.getString(KEY_LOCK_REASON_TYPE, null) == reasonType &&
                    prefs.getString(KEY_NEXT_PERIOD_START, null) == lockReason.nextPeriodStart
            }
            is LockReason.NotLocked -> !prefs.contains(KEY_LOCKED_PACKAGE) && !prefs.contains(KEY_LOCK_REASON_TYPE)
        }
        if (unchanged) return

        prefs.edit().apply {
            putString(KEY_LOCKED_PACKAGE, packageName)
            putBoolean(KEY_FORCED_MODE, isForcedMode)
            when (lockReason) {
                is LockReason.UsageLimitExceeded -> {
                    putString(KEY_LOCK_REASON_TYPE, LOCK_REASON_USAGE_LIMIT)
                    putLong(KEY_USED_SECONDS, lockReason.usedSeconds)
                    putInt(KEY_LIMIT_MINUTES, lockReason.limitMinutes)
                    remove(KEY_NEXT_PERIOD_START)
                }
                is LockReason.OutsideAllowedPeriod -> {
                    putString(KEY_LOCK_REASON_TYPE, LOCK_REASON_ALLOWED_PERIOD)
                    putString(KEY_NEXT_PERIOD_START, lockReason.nextPeriodStart)
                    remove(KEY_USED_SECONDS)
                    remove(KEY_LIMIT_MINUTES)
                }
                is LockReason.NotLocked -> {
                    remove(KEY_LOCK_REASON_TYPE)
                    remove(KEY_USED_SECONDS)
                    remove(KEY_LIMIT_MINUTES)
                    remove(KEY_NEXT_PERIOD_START)
                }
            }
        }.apply()
    }

    private fun clearPersistedLockState() {
        val prefs = getSharedPreferences(LOCK_STATE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LOCKED_PACKAGE) && !prefs.contains(KEY_LOCK_REASON_TYPE)) return
        prefs.edit().clear().apply()
    }

    private suspend fun restoreLockStateIfNeeded() {
        val prefs = getSharedPreferences(LOCK_STATE_PREFS, Context.MODE_PRIVATE)
        val lockReasonType = prefs.getString(KEY_LOCK_REASON_TYPE, null) ?: return
        val lockedPackage = prefs.getString(KEY_LOCKED_PACKAGE, null) ?: return
        val isForcedMode = prefs.getBoolean(KEY_FORCED_MODE, false)

        val lockReason = runCatching {
            checkAppLockStatus(lockedPackage)
        }.getOrNull()

        if (lockReason == null || lockReason is LockReason.NotLocked) {
            clearPersistedLockState()
            lockOverlayManager.hideLockScreen()
            return
        }

        if (lockReasonType == LOCK_REASON_USAGE_LIMIT || lockReasonType == LOCK_REASON_ALLOWED_PERIOD) {
            lockOverlayManager.showLockScreen(lockReason, isForcedMode)
            persistLockState(lockedPackage, lockReason, isForcedMode)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
