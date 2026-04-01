package com.appcontrol.domain.model

import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)
