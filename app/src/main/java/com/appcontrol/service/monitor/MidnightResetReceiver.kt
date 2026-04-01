package com.appcontrol.service.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appcontrol.domain.usecase.CleanOldDataUseCase
import com.appcontrol.domain.usecase.ResetDailyUsageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MidnightResetReceiver : BroadcastReceiver() {

    @Inject lateinit var resetDailyUsage: ResetDailyUsageUseCase
    @Inject lateinit var cleanOldData: CleanOldDataUseCase

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                resetDailyUsage()
                cleanOldData()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
