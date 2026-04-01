package com.appcontrol.presentation.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appcontrol.domain.usecase.VerifyPasswordUseCase
import com.appcontrol.presentation.viewmodel.AppListItemUi
import com.appcontrol.presentation.viewmodel.AppViewModel
import com.appcontrol.service.monitor.MonitorService
import com.appcontrol.service.receiver.AppDeviceAdminReceiver
import com.appcontrol.service.receiver.DeviceAdminAuthGate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_SET_PASSWORD = "set_password"
private const val ROUTE_MAIN = "main"
private const val ROUTE_RULE = "rule"

@Composable
fun AppNavGraph(viewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 等待settings加载完成再决定启动路由
    val hasPassword = settings?.passwordHash?.isNotBlank() == true
    val hasUsagePermission = PermissionUtils.hasUsageStatsPermission(context)
    val hasOverlayPermission = PermissionUtils.hasOverlayPermission(context)

    val startDestination = when {
        !hasUsagePermission || !hasOverlayPermission -> ROUTE_ONBOARDING
        hasPassword -> ROUTE_MAIN
        else -> ROUTE_SET_PASSWORD
    }

    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(message!!) }
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        if (!hasPassword) {
                            navController.navigate(ROUTE_SET_PASSWORD) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            }
                        } else {
                            navController.navigate(ROUTE_MAIN) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(ROUTE_SET_PASSWORD) {
                SetPasswordScreen(
                    biometricAvailable = viewModel.biometricAvailable,
                    onSubmit = { password, biometricEnabled ->
                        viewModel.setPassword(password, biometricEnabled) {
                            navController.navigate(ROUTE_MAIN) {
                                popUpTo(ROUTE_SET_PASSWORD) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(ROUTE_MAIN) {
                MainScreen(
                    viewModel = viewModel,
                    onOpenRule = { packageName -> navController.navigate("$ROUTE_RULE/$packageName") }
                )
            }
            composable(
                route = "$ROUTE_RULE/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                AppRuleScreen(viewModel = viewModel, packageName = packageName)
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var adminGranted by remember { mutableStateOf(PermissionUtils.isDeviceAdminActive(context)) }

    val refreshState = {
        usageGranted = PermissionUtils.hasUsageStatsPermission(context)
        overlayGranted = PermissionUtils.hasOverlayPermission(context)
        adminGranted = PermissionUtils.isDeviceAdminActive(context)
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("权限引导", style = MaterialTheme.typography.headlineSmall)
        Text("首次使用请授予必要权限，保证后台监控与锁定能力。")

        PermissionItem(
            title = "使用情况访问权限",
            granted = usageGranted,
            onGrant = {
                settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        PermissionItem(
            title = "悬浮窗权限",
            granted = overlayGranted,
            onGrant = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                settingsLauncher.launch(intent)
            }
        )

        PermissionItem(
            title = "设备管理器权限（强制锁定建议开启）",
            granted = adminGranted,
            onGrant = {
                val component = ComponentName(context, AppDeviceAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "用于防卸载与防绕过")
                }
                settingsLauncher.launch(intent)
            }
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDone,
            enabled = usageGranted && overlayGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("继续")
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(colors = CardDefaults.cardColors()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title)
                Text(if (granted) "已授权" else "未授权", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Button(onClick = onGrant) { Text(if (granted) "去检查" else "去授权") }
        }
    }
}

@Composable
private fun SetPasswordScreen(
    biometricAvailable: Boolean,
    onSubmit: (String, Boolean) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var enableBiometric by rememberSaveable { mutableStateOf(false) }

    val valid = password.length >= 4 && password == confirmPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置管理密码", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（至少4位）") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("确认密码") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (biometricAvailable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用生物识别")
                Spacer(Modifier.width(8.dp))
                Switch(checked = enableBiometric, onCheckedChange = { enableBiometric = it })
            }
        }
        Button(onClick = { onSubmit(password, enableBiometric) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text("完成")
        }
    }
}

private enum class MainTab(val title: String) {
    Apps("应用管理"),
    Stats("使用统计"),
    Settings("设置")
}

@Composable
private fun MainScreen(
    viewModel: AppViewModel,
    onOpenRule: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Apps) }
    var monitorEnabled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val usageGranted = PermissionUtils.hasUsageStatsPermission(context)
    val overlayGranted = PermissionUtils.hasOverlayPermission(context)
    val hasMissingPermission = !usageGranted || !overlayGranted

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.title) },
                        icon = { Text(tab.title.take(1)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            if (hasMissingPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "检测到权限缺失，监控和锁定可能失效",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!usageGranted) {
                                Button(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }) {
                                    Text("去授权使用记录")
                                }
                            }
                            if (!overlayGranted) {
                                Button(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }) {
                                    Text("去授权悬浮窗")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("监控服务")
                Switch(
                    checked = monitorEnabled,
                    onCheckedChange = {
                        monitorEnabled = it
                        if (it) MonitorService.startService(context) else MonitorService.stopService(context)
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            when (selectedTab) {
                MainTab.Apps -> AppListScreen(viewModel, onOpenRule)
                MainTab.Stats -> StatsScreen(viewModel)
                MainTab.Settings -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun AppListScreen(
    viewModel: AppViewModel,
    onOpenRule: (String) -> Unit
) {
    val apps by viewModel.appListUi.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }
    var authTarget by remember { mutableStateOf<AppListItemUi?>(null) }
    var authRulePackage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索应用") },
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                items = apps.filter {
                    it.appName.contains(keyword, ignoreCase = true) || it.packageName.contains(keyword, ignoreCase = true)
                },
                key = { it.packageName }
            ) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.appName, style = MaterialTheme.typography.titleMedium)
                        Text(item.packageName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (item.usageLimitMinutes != null) "时长限制: ${item.usageLimitMinutes} 分钟" else "时长限制: 未设置",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("受控")
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = item.isTarget,
                                    onCheckedChange = {
                                        authTarget = item.copy(isTarget = it)
                                    }
                                )
                            }
                            if (item.isTarget) {
                                Text(
                                    "规则设置",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { authRulePackage = item.packageName }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (authTarget != null) {
        AuthDialog(
            title = "身份验证",
            onDismiss = { authTarget = null },
            onVerified = {
                val target = authTarget
                if (target != null) {
                    viewModel.toggleTargetApp(target, target.isTarget)
                }
                authTarget = null
            },
            verify = viewModel::verifyPassword
        )
    }

    if (authRulePackage != null) {
        AuthDialog(
            title = "验证后进入规则设置",
            onDismiss = { authRulePackage = null },
            onVerified = {
                val targetPackage = authRulePackage
                if (!targetPackage.isNullOrBlank()) {
                    onOpenRule(targetPackage)
                }
                authRulePackage = null
            },
            verify = viewModel::verifyPassword
        )
    }
}

@Composable
private fun AppRuleScreen(
    viewModel: AppViewModel,
    packageName: String
) {
    val targetApps by viewModel.targetApps.collectAsState()
    val target = targetApps.find { it.packageName == packageName }
    val periods by viewModel.allowedPeriods(packageName).collectAsState()

    var limitInput by rememberSaveable(target?.usageLimitMinutes) {
        mutableStateOf(target?.usageLimitMinutes?.toString().orEmpty())
    }
    var startTime by rememberSaveable { mutableStateOf("08:00") }
    var endTime by rememberSaveable { mutableStateOf("22:00") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(target?.appName ?: packageName, style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("白名单")
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = target?.isWhitelisted == true,
                onCheckedChange = { checked ->
                    pendingAction = {
                        viewModel.toggleWhitelist(packageName, checked, target?.appName)
                    }
                }
            )
        }

        OutlinedTextField(
            value = limitInput,
            onValueChange = { limitInput = it.filter(Char::isDigit) },
            label = { Text("每日时长限制（分钟）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val minutes = limitInput.toIntOrNull()
            pendingAction = { viewModel.setUsageLimit(packageName, minutes) }
        }) {
            Text("保存时长限制")
        }

        Text("允许时间段", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text("开始 HH:mm") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endTime,
                onValueChange = { endTime = it },
                label = { Text("结束 HH:mm") },
                modifier = Modifier.weight(1f)
            )
        }
        Button(onClick = {
            pendingAction = { viewModel.addAllowedPeriod(packageName, startTime, endTime) }
        }) {
            Text("添加时间段")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(periods, key = { it.id }) { period ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${period.startTime} - ${period.endTime}")
                        Text(
                            "删除",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable {
                                pendingAction = { viewModel.removeAllowedPeriod(period) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (pendingAction != null) {
        AuthDialog(
            title = "验证后修改规则",
            onDismiss = { pendingAction = null },
            onVerified = {
                pendingAction?.invoke()
                pendingAction = null
            },
            verify = viewModel::verifyPassword
        )
    }
}

@Composable
private fun StatsScreen(viewModel: AppViewModel) {
    val daily by viewModel.dailyStats.collectAsState()
    val weekly by viewModel.weeklyStats.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    var dateInput by rememberSaveable(selectedDate) { mutableStateOf(selectedDate) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("每日统计", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                label = { Text("日期 yyyy-MM-dd") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                runCatching { LocalDate.parse(dateInput, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
                    .onSuccess { viewModel.setDate(dateInput) }
            }) { Text("查看") }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(daily, key = { "${it.packageName}_${it.date}" }) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(record.packageName, style = MaterialTheme.typography.titleSmall)
                        Text("时长: ${record.usageDurationSeconds / 60} 分钟")
                        Text("打开次数: ${record.openCount}")
                    }
                }
            }
        }

        Text("近7天总时长（分钟）", style = MaterialTheme.typography.titleMedium)
        val grouped = weekly.groupBy { it.date }.mapValues { entry ->
            entry.value.sumOf { it.usageDurationSeconds } / 60
        }.toSortedMap()
        grouped.forEach { (date, minutes) ->
            Text("$date: $minutes")
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var showAuthForToggle by remember { mutableStateOf(false) }
    var showAuthForForcedLock by remember { mutableStateOf(false) }
    var pendingForcedLockValue by remember { mutableStateOf<Boolean?>(null) }
    var showAuthForDisableAdmin by remember { mutableStateOf(false) }
    var showChangePwd by remember { mutableStateOf(false) }
    val isDeviceAdminActive = PermissionUtils.isDeviceAdminActive(context)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("生物识别")
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings?.biometricEnabled == true,
                onCheckedChange = { showAuthForToggle = true }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("强制锁定模式")
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings?.forcedLockEnabled == true,
                onCheckedChange = {
                    if (it && !isDeviceAdminActive) {
                        val component = ComponentName(context, AppDeviceAdminReceiver::class.java)
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "强制锁定模式需要设备管理器权限")
                        }
                        context.startActivity(intent)
                    } else {
                        pendingForcedLockValue = it
                        showAuthForForcedLock = true
                    }
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("设备管理器")
            Spacer(Modifier.width(8.dp))
            Text(if (isDeviceAdminActive) "已启用" else "未启用")
            Spacer(Modifier.width(8.dp))
            if (isDeviceAdminActive) {
                Text(
                    "解除授权",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { showAuthForDisableAdmin = true }
                )
            }
        }

        Button(onClick = { showChangePwd = true }) {
            Text("修改管理密码")
        }

        Text("版本: 1.0")
    }

    if (showAuthForToggle) {
        AuthDialog(
            title = "验证后切换生物识别",
            onDismiss = { showAuthForToggle = false },
            onVerified = {
                viewModel.setBiometricEnabled(!(settings?.biometricEnabled ?: false))
                showAuthForToggle = false
            },
            verify = viewModel::verifyPassword
        )
    }

    if (showChangePwd) {
        ChangePasswordDialog(
            onDismiss = { showChangePwd = false },
            onConfirm = { oldPwd, newPwd ->
                viewModel.changePassword(oldPwd, newPwd) { showChangePwd = false }
            }
        )
    }

    if (showAuthForForcedLock) {
        AuthDialog(
            title = "验证后切换强制锁定",
            onDismiss = {
                pendingForcedLockValue = null
                showAuthForForcedLock = false
            },
            onVerified = {
                pendingForcedLockValue?.let { viewModel.setForcedLockEnabled(it) }
                pendingForcedLockValue = null
                showAuthForForcedLock = false
            },
            verify = viewModel::verifyPassword
        )
    }

    if (showAuthForDisableAdmin) {
        AuthDialog(
            title = "验证后解除设备管理器",
            onDismiss = { showAuthForDisableAdmin = false },
            onVerified = {
                DeviceAdminAuthGate.authorizeDisable(context)
                val component = ComponentName(context, AppDeviceAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "已完成身份验证，请在系统页面停用设备管理器")
                }
                context.startActivity(intent)
                showAuthForDisableAdmin = false
            },
            verify = viewModel::verifyPassword
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPwd by rememberSaveable { mutableStateOf("") }
    var newPwd by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPwd,
                    onValueChange = { oldPwd = it },
                    label = { Text("旧密码") },
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = { Text("新密码（至少4位）") },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(oldPwd, newPwd) }) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AuthDialog(
    title: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    verify: (String, (VerifyPasswordUseCase.VerifyResult) -> Unit) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("管理密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                verify(password) { result ->
                    when (result) {
                        is VerifyPasswordUseCase.VerifyResult.Success -> onVerified()
                        is VerifyPasswordUseCase.VerifyResult.WrongPassword -> {
                            error = "密码错误，剩余 ${result.remainingAttempts} 次"
                        }
                        is VerifyPasswordUseCase.VerifyResult.LockedOut -> {
                            error = "已锁定，请稍后重试"
                        }
                    }
                }
            }) {
                Text("验证")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
