package com.appcontrol.presentation.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.appcontrol.R
import com.appcontrol.domain.usecase.VerifyPasswordUseCase
import com.appcontrol.presentation.i18n.LocaleManager
import com.appcontrol.presentation.viewmodel.AppListItemUi
import com.appcontrol.presentation.viewmodel.AppViewModel
import com.appcontrol.service.monitor.MonitorPreferences
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

    AppBackgroundScaffold(snackbarHostState = snackbarHostState) { padding ->
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
private fun AppBackgroundScaffold(
    snackbarHostState: SnackbarHostState,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            colors.background,
                            colors.surfaceVariant.copy(alpha = 0.65f),
                            colors.background
                        )
                    )
                )
        ) {
            content(padding)
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge)

        PermissionItem(
            title = stringResource(R.string.permission_usage_title),
            granted = usageGranted,
            onGrant = { settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )

        PermissionItem(
            title = stringResource(R.string.permission_overlay_title),
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
            title = stringResource(R.string.permission_admin_title),
            granted = adminGranted,
            onGrant = {
                val component = ComponentName(context, AppDeviceAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.permission_admin_explanation))
                }
                settingsLauncher.launch(intent)
            }
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            enabled = usageGranted && overlayGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.common_continue))
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) stringResource(R.string.permission_granted) else stringResource(R.string.permission_not_granted),
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onGrant) {
                Text(
                    if (granted) stringResource(R.string.permission_check_again)
                    else stringResource(R.string.permission_go_grant)
                )
            }
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.set_password_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.set_password_input)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(R.string.set_password_confirm)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (biometricAvailable) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_biometric), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = enableBiometric, onCheckedChange = { enableBiometric = it })
                }
            }
        }
        Button(onClick = { onSubmit(password, enableBiometric) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_done))
        }
    }
}

private enum class MainTab(val titleRes: Int) {
    Apps(R.string.tab_apps),
    Stats(R.string.tab_stats),
    Settings(R.string.tab_settings)
}

@Composable
private fun MainScreen(
    viewModel: AppViewModel,
    onOpenRule: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Apps) }
    val context = LocalContext.current
    var monitorEnabled by remember { mutableStateOf(MonitorPreferences.isMonitorEnabled(context)) }
    val usageGranted = PermissionUtils.hasUsageStatsPermission(context)
    val overlayGranted = PermissionUtils.hasOverlayPermission(context)
    val hasMissingPermission = !usageGranted || !overlayGranted

    LaunchedEffect(monitorEnabled, hasMissingPermission) {
        if (monitorEnabled && !hasMissingPermission) {
            MonitorService.startService(context)
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            NavigationBar(tonalElevation = 4.dp) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(stringResource(tab.titleRes)) },
                        icon = { Text(stringResource(tab.titleRes).take(1)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasMissingPermission) {
                MissingPermissionCard(
                    usageGranted = usageGranted,
                    overlayGranted = overlayGranted,
                    context = context
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.monitor_service_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (monitorEnabled) stringResource(R.string.monitor_service_running) else stringResource(R.string.monitor_service_stopped),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Switch(
                        checked = monitorEnabled,
                        onCheckedChange = {
                            monitorEnabled = it
                            MonitorPreferences.setMonitorEnabled(context, it)
                            if (it && !hasMissingPermission) {
                                MonitorService.startService(context)
                            } else {
                                MonitorService.stopService(context)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                MainTab.Apps -> AppListScreen(viewModel, onOpenRule)
                MainTab.Stats -> StatsScreen(viewModel)
                MainTab.Settings -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun MissingPermissionCard(
    usageGranted: Boolean,
    overlayGranted: Boolean,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_missing_hint),
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
                        Text(stringResource(R.string.permission_grant_usage_action))
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
                        Text(stringResource(R.string.permission_grant_overlay_action))
                    }
                }
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
    val loading by viewModel.loading.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }
    var authTarget by remember { mutableStateOf<AppListItemUi?>(null) }
    var authRulePackage by remember { mutableStateOf<String?>(null) }

    val filteredApps = apps.filter {
        it.appName.contains(keyword, ignoreCase = true) ||
            it.packageName.contains(keyword, ignoreCase = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text(stringResource(R.string.app_search_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (loading) {
            Text(
                stringResource(R.string.app_loading),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!loading && filteredApps.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.app_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = filteredApps, key = { it.packageName }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(item.appName, style = MaterialTheme.typography.titleMedium)
                        Text(item.packageName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (item.usageLimitMinutes != null) {
                                stringResource(R.string.app_limit_minutes, item.usageLimitMinutes)
                            } else {
                                stringResource(R.string.app_limit_unset)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.app_controlled_label))
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = item.isTarget,
                                    onCheckedChange = { authTarget = item.copy(isTarget = it) }
                                )
                            }
                            if (item.isTarget) {
                                Text(
                                    stringResource(R.string.app_rule_action),
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
            title = stringResource(R.string.auth_title),
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
            title = stringResource(R.string.auth_rule_title),
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(target?.appName ?: packageName, style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.rule_whitelist), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = target?.isWhitelisted == true,
                    onCheckedChange = { checked ->
                        pendingAction = { viewModel.toggleWhitelist(packageName, checked, target?.appName) }
                    }
                )
            }
        }

        OutlinedTextField(
            value = limitInput,
            onValueChange = { limitInput = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.rule_limit_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(onClick = {
            val minutes = limitInput.toIntOrNull()
            pendingAction = { viewModel.setUsageLimit(packageName, minutes) }
        }) {
            Text(stringResource(R.string.rule_save_limit))
        }

        Text(stringResource(R.string.rule_period_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text(stringResource(R.string.rule_period_start)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = endTime,
                onValueChange = { endTime = it },
                label = { Text(stringResource(R.string.rule_period_end)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Button(onClick = {
            pendingAction = { viewModel.addAllowedPeriod(packageName, startTime, endTime) }
        }) {
            Text(stringResource(R.string.rule_add_period))
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
                            stringResource(R.string.common_delete),
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
            title = stringResource(R.string.auth_modify_rule_title),
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
        Text(stringResource(R.string.stats_daily_title), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                label = { Text(stringResource(R.string.stats_date_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                runCatching { LocalDate.parse(dateInput, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
                    .onSuccess { viewModel.setDate(dateInput) }
            }) {
                Text(stringResource(R.string.stats_view_action))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(daily, key = { "${it.packageName}_${it.date}" }) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(record.packageName, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.stats_duration_minutes, record.usageDurationSeconds / 60))
                        Text(stringResource(R.string.stats_open_count, record.openCount))
                    }
                }
            }
        }

        Text(stringResource(R.string.stats_weekly_title), style = MaterialTheme.typography.titleMedium)
        val grouped = weekly.groupBy { it.date }.mapValues { entry ->
            entry.value.sumOf { it.usageDurationSeconds } / 60
        }.toSortedMap()
        grouped.forEach { (date, minutes) ->
            Text(stringResource(R.string.stats_weekly_item, date, minutes))
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

    var languageTag by remember { mutableStateOf(LocaleManager.getSavedLanguage(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSwitchRow(
            title = stringResource(R.string.settings_biometric),
            checked = settings?.biometricEnabled == true,
            onCheckedChange = { showAuthForToggle = true }
        )

        SettingSwitchRow(
            title = stringResource(R.string.settings_forced_lock),
            checked = settings?.forcedLockEnabled == true,
            onCheckedChange = {
                if (it && !isDeviceAdminActive) {
                    val component = ComponentName(context, AppDeviceAdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            context.getString(R.string.settings_forced_lock_admin_hint)
                        )
                    }
                    context.startActivity(intent)
                } else {
                    pendingForcedLockValue = it
                    showAuthForForcedLock = true
                }
            }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageButton(
                        selected = languageTag == LocaleManager.LANGUAGE_SYSTEM,
                        label = stringResource(R.string.settings_language_system),
                        onClick = {
                            languageTag = LocaleManager.LANGUAGE_SYSTEM
                            LocaleManager.setLanguage(context, LocaleManager.LANGUAGE_SYSTEM)
                        }
                    )
                    LanguageButton(
                        selected = languageTag == LocaleManager.LANGUAGE_ZH,
                        label = stringResource(R.string.settings_language_zh),
                        onClick = {
                            languageTag = LocaleManager.LANGUAGE_ZH
                            LocaleManager.setLanguage(context, LocaleManager.LANGUAGE_ZH)
                        }
                    )
                    LanguageButton(
                        selected = languageTag == LocaleManager.LANGUAGE_EN,
                        label = stringResource(R.string.settings_language_en),
                        onClick = {
                            languageTag = LocaleManager.LANGUAGE_EN
                            LocaleManager.setLanguage(context, LocaleManager.LANGUAGE_EN)
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_device_admin), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isDeviceAdminActive) stringResource(R.string.settings_enabled)
                    else stringResource(R.string.settings_disabled)
                )
                Spacer(Modifier.width(8.dp))
                if (isDeviceAdminActive) {
                    Text(
                        stringResource(R.string.settings_disable_admin),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { showAuthForDisableAdmin = true }
                    )
                }
            }
        }

        Button(onClick = { showChangePwd = true }) {
            Text(stringResource(R.string.settings_change_password))
        }

        Text(stringResource(R.string.settings_version, "1.0"), style = MaterialTheme.typography.bodyMedium)
    }

    if (showAuthForToggle) {
        AuthDialog(
            title = stringResource(R.string.auth_toggle_biometric_title),
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
            title = stringResource(R.string.auth_toggle_forced_lock_title),
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
            title = stringResource(R.string.auth_disable_admin_title),
            onDismiss = { showAuthForDisableAdmin = false },
            onVerified = {
                DeviceAdminAuthGate.authorizeDisable(context)
                val component = ComponentName(context, AppDeviceAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.auth_disable_admin_explanation)
                    )
                }
                context.startActivity(intent)
                showAuthForDisableAdmin = false
            },
            verify = viewModel::verifyPassword
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LanguageButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
    ) {
        Text(label)
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
        title = { Text(stringResource(R.string.settings_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPwd,
                    onValueChange = { oldPwd = it },
                    label = { Text(stringResource(R.string.change_password_old)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = { Text(stringResource(R.string.change_password_new)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(oldPwd, newPwd) }) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
    val context = LocalContext.current
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
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                            error = context.getString(R.string.auth_wrong_password, result.remainingAttempts)
                        }
                        is VerifyPasswordUseCase.VerifyResult.LockedOut -> {
                            error = context.getString(R.string.auth_locked)
                        }
                    }
                }
            }) {
                Text(stringResource(R.string.auth_verify_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
