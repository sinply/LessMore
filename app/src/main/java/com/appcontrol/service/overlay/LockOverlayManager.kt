package com.appcontrol.service.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.appcontrol.domain.model.LockReason
import com.appcontrol.presentation.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LockOverlayManager"
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var currentLockReason: LockReason? = null
    private var isForcedMode: Boolean = false
    private var currentLayoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    fun showLockScreen(lockReason: LockReason, isForcedLockMode: Boolean = false) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission is missing, skip lock screen.")
            return
        }

        if (overlayView != null &&
            currentLockReason == lockReason &&
            isForcedMode == isForcedLockMode
        ) {
            reassertOverlay()
            return
        }

        if (overlayView != null) {
            hideLockScreen()
        }

        currentLockReason = lockReason
        isForcedMode = isForcedLockMode

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (isForcedLockMode) {
                // 强制锁定模式：可聚焦+全屏，尽可能拦截返回/任务切换等输入
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            } else {
                // 普通模式：允许用户返回桌面
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            },
            PixelFormat.TRANSLUCENT
        ).apply {
            // 设置窗口类型为系统警告级别，确保在最上层
            if (isForcedLockMode) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
        }

        val composeView = ComposeView(context).apply {
            val lifecycleOwner = OverlayLifecycleOwner().also { overlayLifecycleOwner = it }
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                AppTheme {
                    LockOverlayContent(
                        lockReason = lockReason,
                        isForcedLock = isForcedLockMode,
                        onGoHome = { if (!isForcedLockMode) goToHome() }
                    )
                }
            }
        }

        // 创建一个容器视图来处理按键拦截
        val containerView = if (isForcedLockMode) {
            createLockContainerView(composeView)
        } else {
            composeView
        }

        overlayView = containerView
        currentLayoutParams = layoutParams
        runCatching {
            windowManager.addView(containerView, layoutParams)
        }.onFailure {
            overlayView = null
            currentLayoutParams = null
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            overlayLifecycleOwner = null
            Log.e(TAG, "Failed to add lock overlay view.", it)
        }
    }

    /**
     * 创建一个可以拦截按键的容器视图
     * 用于强制锁定模式下防止用户绕过锁定界面
     */
    private fun createLockContainerView(composeView: ComposeView): View {
        return object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // 拦截返回键、Home键、最近任务键等
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_HOME,
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_APP_SWITCH,
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE,
                    KeyEvent.KEYCODE_POWER -> {
                        return true // 消费这些按键事件，阻止其传递
                    }
                }
                return super.dispatchKeyEvent(event)
            }

            override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
                return if (isForcedMode) true else super.dispatchKeyEventPreIme(event)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
                // 拦截所有触摸事件
                return isForcedMode
            }

            override fun onTouchEvent(event: MotionEvent?): Boolean {
                // 消费所有触摸事件
                return isForcedMode
            }
        }.apply {
            addView(composeView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // 设置为可获取焦点以接收按键事件
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    fun hideLockScreen() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // View not attached, ignore
            } catch (_: RuntimeException) {
                // Window token changed by system, ignore
            }
            overlayView = null
            currentLockReason = null
            currentLayoutParams = null
            isForcedMode = false
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            overlayLifecycleOwner = null
        }
    }

    fun isShowing(): Boolean = overlayView != null

    fun getCurrentLockReason(): LockReason? = currentLockReason

    fun isInForcedMode(): Boolean = isForcedMode

    fun reassertOverlay() {
        val view = overlayView ?: return
        val params = currentLayoutParams ?: return
        runCatching {
            view.bringToFront()
            view.requestFocus()
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun goToHome() {
        hideLockScreen()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(homeIntent)
    }
}
