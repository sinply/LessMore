package com.appcontrol.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import com.appcontrol.presentation.i18n.LocaleManager
import com.appcontrol.presentation.theme.AppTheme
import com.appcontrol.presentation.ui.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleManager.applySavedLocale(this)
        setContent {
            AppTheme {
                AppNavGraph()
            }
        }
    }
}
