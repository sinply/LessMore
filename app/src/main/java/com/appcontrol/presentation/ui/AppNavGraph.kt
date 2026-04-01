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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

    val hasUsagePermission = PermissionUtils.hasUsageStatsPermission(context)
    val hasOverlayPermission = PermissionUtils.hasOverlayPermission(context)
    val hasPassword = settings?.passwordHash?.isNotBlank() == true

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (settings == null && hasUsagePermission && hasOverlayPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        PermissionCard(
            title = stringResource(R.string.permission_usage_title),
            granted = usageGranted,
            onGrant = { settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )

        PermissionCard(
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

        PermissionCard(
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

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            enabled = usageGranted && overlayGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = stringResource(R.string.common_continue),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (granted) stringResource(R.string.permission_granted) else stringResource(R.string.permission_not_granted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (granted) stringResource(R.string.permission_check_again)
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.set_password_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.set_password_input)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(R.string.set_password_confirm)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        if (biometricAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_biometric),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = enableBiometric,
                        onCheckedChange = { enableBiometric = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onSubmit(password, enableBiometric) },
            enabled = valid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.common_done),
                style = MaterialTheme.typography.titleMedium
            )
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
    var monitorEnabled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        monitorEnabled = MonitorPreferences.isMonitorEnabled(context)
    }

    val usageGranted = PermissionUtils.hasUsageStatsPermission(context)
    val overlayGranted = PermissionUtils.hasOverlayPermission(context)
    val hasMissingPermission = !usageGranted || !overlayGranted

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = stringResource(tab.titleRes),
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Text(
                                text = stringResource(tab.titleRes).take(1),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (hasMissingPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permission_missing_hint),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!usageGranted) {
                                Button(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.permission_grant_usage_action))
                                }
                            }
                            if (!overlayGranted) {
                                Button(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            ).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.permission_grant_overlay_action))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.monitor_service_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (monitorEnabled)
                                stringResource(R.string.monitor_service_running)
                            else
                                stringResource(R.string.monitor_service_stopped),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (monitorEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = monitorEnabled,
                        onCheckedChange = {
                            monitorEnabled = it
                            MonitorPreferences.setMonitorEnabled(context, it)
                            if (it) MonitorService.startService(context)
                            else MonitorService.stopService(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

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
    val loading by viewModel.loading.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }
    var authTarget by remember { mutableStateOf<AppListItemUi?>(null) }
    var authRulePackage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text(stringResource(R.string.app_search_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.app_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = apps.filter {
                        it.appName.contains(keyword, ignoreCase = true) ||
                        it.packageName.contains(keyword, ignoreCase = true)
                    },
                    key = { it.packageName }
                ) { item ->
                    AppListItem(
                        item = item,
                        onToggleTarget = { authTarget = item.copy(isTarget = it) },
                        onOpenRule = { authRulePackage = item.packageName }
                    )
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
private fun AppListItem(
    item: AppListItemUi,
    onToggleTarget: (Boolean) -> Unit,
    onOpenRule: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isTarget)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = if (item.usageLimitMinutes != null)
                    stringResource(R.string.app_limit_minutes, item.usageLimitMinutes)
                else
                    stringResource(R.string.app_limit_unset),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_controlled_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = item.isTarget,
                        onCheckedChange = onToggleTarget,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                if (item.isTarget) {
                    Text(
                        text = stringResource(R.string.app_rule_action),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onOpenRule() }
                    )
                }
            }
        }
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = target?.appName ?: packageName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.rule_whitelist),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = target?.isWhitelisted == true,
                    onCheckedChange = { checked ->
                        pendingAction = {
                            viewModel.toggleWhitelist(packageName, checked, target?.appName)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        OutlinedTextField(
            value = limitInput,
            onValueChange = { limitInput = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.rule_limit_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                val minutes = limitInput.toIntOrNull()
                pendingAction = { viewModel.setUsageLimit(packageName, minutes) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.rule_save_limit))
        }

        Text(
            text = stringResource(R.string.rule_period_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = startTime,
                onValueChange = { startTime = it },
                label = { Text(stringResource(R.string.rule_period_start)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = endTime,
                onValueChange = { endTime = it },
                label = { Text(stringResource(R.string.rule_period_end)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        Button(
            onClick = {
                pendingAction = { viewModel.addAllowedPeriod(packageName, startTime, endTime) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.rule_add_period))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(periods, key = { it.id }) { period ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${period.startTime} - ${period.endTime}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.stats_daily_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                label = { Text(stringResource(R.string.stats_date_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    runCatching {
                        LocalDate.parse(dateInput, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    }.onSuccess { viewModel.setDate(dateInput) }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.stats_view_action))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(daily, key = { "${it.packageName}_${it.date}" }) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = record.packageName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.stats_duration_minutes, record.usageDurationSeconds / 60),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.stats_open_count, record.openCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.stats_weekly_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        val grouped = weekly.groupBy { it.date }.mapValues { entry ->
            entry.value.sumOf { it.usageDurationSeconds } / 60
        }.toSortedMap()

        grouped.forEach { (date, minutes) ->
            Text(
                text = stringResource(R.string.stats_weekly_item, date, minutes),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var showAuthForToggle by remember { mutableStateOf(false) }
    var showAuthForForcedLock by remember { mutableStateOf(false) }
    var pendingForcedLockValue by remember { mutableStateOf<Boolean?>(null) }
    var showChangePwd by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_biometric),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = settings?.biometricEnabled == true,
                    onCheckedChange = { showAuthForToggle = true },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_forced_lock),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = settings?.forcedLockEnabled == true,
                        onCheckedChange = {
                            pendingForcedLockValue = it
                            showAuthForForcedLock = true
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                if (!PermissionUtils.isDeviceAdminActive(context)) {
                    Text(
                        text = stringResource(R.string.settings_forced_lock_admin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Language Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        LocaleManager.LANGUAGE_SYSTEM to stringResource(R.string.settings_language_system),
                        LocaleManager.LANGUAGE_ZH to stringResource(R.string.settings_language_zh),
                        LocaleManager.LANGUAGE_EN to stringResource(R.string.settings_language_en)
                    ).forEach { (tag, label) ->
                        Button(
                            onClick = { LocaleManager.setLanguage(context, tag) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (LocaleManager.getSavedLanguage(context) == tag)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        Button(
            onClick = { showChangePwd = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.settings_change_password))
        }

        Text(
            text = stringResource(R.string.settings_version, "1.0"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = { Text(stringResource(R.string.change_password_new)) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(oldPwd, newPwd) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = RoundedCornerShape(16.dp)
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

    val wrongPasswordFormat = context.getString(R.string.auth_wrong_password)
    val lockedText = context.getString(R.string.auth_locked)
    val passwordLabel = context.getString(R.string.auth_password_label)
    val verifyAction = context.getString(R.string.auth_verify_action)
    val cancelAction = context.getString(R.string.common_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(passwordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                verify(password) { result ->
                    when (result) {
                        is VerifyPasswordUseCase.VerifyResult.Success -> onVerified()
                        is VerifyPasswordUseCase.VerifyResult.WrongPassword -> {
                            error = wrongPasswordFormat.format(result.remainingAttempts)
                        }
                        is VerifyPasswordUseCase.VerifyResult.LockedOut -> {
                            error = lockedText
                        }
                    }
                }
            }) {
                Text(verifyAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelAction)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
