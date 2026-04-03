package com.appcontrol.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appcontrol.R
import com.appcontrol.data.db.AllowedPeriod
import com.appcontrol.data.db.TargetApp
import com.appcontrol.data.db.UsageRecord
import com.appcontrol.domain.model.InstalledAppInfo
import com.appcontrol.domain.repository.AppRepository
import com.appcontrol.domain.repository.AuthRepository
import com.appcontrol.domain.usecase.AddTargetAppUseCase
import com.appcontrol.domain.usecase.ChangePasswordUseCase
import com.appcontrol.domain.usecase.CheckBiometricAvailabilityUseCase
import com.appcontrol.domain.usecase.GetDailyStatsUseCase
import com.appcontrol.domain.usecase.GetInstalledAppsUseCase
import com.appcontrol.domain.usecase.GetWeeklyStatsUseCase
import com.appcontrol.domain.usecase.ManageAllowedPeriodsUseCase
import com.appcontrol.domain.usecase.SetPasswordUseCase
import com.appcontrol.domain.usecase.SetUsageLimitUseCase
import com.appcontrol.domain.usecase.ToggleBiometricUseCase
import com.appcontrol.domain.usecase.ToggleWhitelistUseCase
import com.appcontrol.domain.usecase.VerifyPasswordUseCase
import com.appcontrol.domain.usecase.RemoveTargetAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appRepository: AppRepository,
    private val authRepository: AuthRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val addTargetAppUseCase: AddTargetAppUseCase,
    private val removeTargetAppUseCase: RemoveTargetAppUseCase,
    private val toggleWhitelistUseCase: ToggleWhitelistUseCase,
    private val setUsageLimitUseCase: SetUsageLimitUseCase,
    private val manageAllowedPeriodsUseCase: ManageAllowedPeriodsUseCase,
    private val getDailyStatsUseCase: GetDailyStatsUseCase,
    private val getWeeklyStatsUseCase: GetWeeklyStatsUseCase,
    private val setPasswordUseCase: SetPasswordUseCase,
    private val verifyPasswordUseCase: VerifyPasswordUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val toggleBiometricUseCase: ToggleBiometricUseCase,
    private val checkBiometricAvailabilityUseCase: CheckBiometricAvailabilityUseCase
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(dateFormatter))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val settings = authRepository.getSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val targetApps = appRepository.getAllTargetApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val appListUi = combine(installedApps, targetApps) { installed, target ->
        val targetMap = target.associateBy { it.packageName }
        installed.map { app ->
            AppListItemUi(
                packageName = app.packageName,
                appName = app.appName,
                isTarget = targetMap.containsKey(app.packageName),
                isWhitelisted = targetMap[app.packageName]?.isWhitelisted ?: false,
                usageLimitMinutes = targetMap[app.packageName]?.usageLimitMinutes
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dailyStats: StateFlow<List<UsageRecord>> = selectedDate
        .flatMapLatest { date -> getDailyStatsUseCase(date) }
        .map { records -> records.sortedByDescending { it.usageDurationSeconds } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val weeklyStats: StateFlow<List<UsageRecord>> = getWeeklyStatsUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val biometricAvailable: Boolean by lazy { checkBiometricAvailabilityUseCase() }

    init {
        viewModelScope.launch {
            runCatching { authRepository.initializeSettings() }
                .onFailure { _message.value = appContext.getString(R.string.error_init_settings_failed) }
            loadInstalledApps()
        }
    }

    fun loadInstalledApps() {
        _loading.update { true }
        viewModelScope.launch {
            runCatching { getInstalledAppsUseCase() }
                .onSuccess { _installedApps.value = it }
                .onFailure { _message.value = appContext.getString(R.string.error_load_apps_failed) }
            _loading.update { false }
        }
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun clearMessage() {
        _message.value = null
    }

    fun toggleTargetApp(item: AppListItemUi, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (enabled) {
                    addTargetAppUseCase(
                        TargetApp(
                            packageName = item.packageName,
                            appName = item.appName
                        )
                    )
                } else {
                    removeTargetAppUseCase(item.packageName)
                }
            }.onFailure {
                _message.value = appContext.getString(R.string.error_update_target_app_failed)
            }
        }
    }

    fun toggleWhitelist(packageName: String, enabled: Boolean, appName: String? = null) {
        viewModelScope.launch {
            runCatching { toggleWhitelistUseCase(packageName, enabled, appName) }
                .onFailure { _message.value = appContext.getString(R.string.error_update_whitelist_failed) }
        }
    }

    fun setUsageLimit(packageName: String, minutes: Int?) {
        viewModelScope.launch {
            runCatching { setUsageLimitUseCase(packageName, minutes) }
                .onFailure { _message.value = appContext.getString(R.string.error_set_usage_limit_failed) }
        }
    }

    fun allowedPeriods(packageName: String): StateFlow<List<AllowedPeriod>> {
        return manageAllowedPeriodsUseCase.getPeriodsForApp(packageName).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun addAllowedPeriod(packageName: String, startTime: String, endTime: String) {
        viewModelScope.launch {
            runCatching {
                manageAllowedPeriodsUseCase.addPeriod(
                    AllowedPeriod(
                        packageName = packageName,
                        startTime = startTime,
                        endTime = endTime
                    )
                )
            }.onFailure { _message.value = appContext.getString(R.string.error_add_period_failed) }
        }
    }

    fun removeAllowedPeriod(period: AllowedPeriod) {
        viewModelScope.launch {
            runCatching { manageAllowedPeriodsUseCase.removePeriod(period) }
                .onFailure { _message.value = appContext.getString(R.string.error_remove_period_failed) }
        }
    }

    fun setPassword(password: String, biometricEnabled: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                setPasswordUseCase(password)
                if (biometricEnabled && biometricAvailable) {
                    toggleBiometricUseCase(true)
                }
            }.onSuccess {
                onSuccess()
            }.onFailure {
                _message.value = appContext.getString(R.string.error_set_password_failed)
            }
        }
    }

    fun verifyPassword(password: String, onResult: (VerifyPasswordUseCase.VerifyResult) -> Unit) {
        viewModelScope.launch {
            runCatching { verifyPasswordUseCase(password) }
                .onSuccess(onResult)
                .onFailure { _message.value = appContext.getString(R.string.error_verify_password_failed) }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { changePasswordUseCase(oldPassword, newPassword) }
                .onSuccess { success ->
                    if (success) {
                        onSuccess()
                    } else {
                        _message.value = appContext.getString(R.string.error_old_password_wrong)
                    }
                }
                .onFailure { _message.value = appContext.getString(R.string.error_change_password_failed) }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { toggleBiometricUseCase(enabled) }
                .onFailure { _message.value = appContext.getString(R.string.error_update_biometric_failed) }
        }
    }

    fun setForcedLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { authRepository.setForcedLockEnabled(enabled) }
                .onFailure { _message.value = appContext.getString(R.string.error_update_forced_lock_failed) }
        }
    }
}

data class AppListItemUi(
    val packageName: String,
    val appName: String,
    val isTarget: Boolean,
    val isWhitelisted: Boolean,
    val usageLimitMinutes: Int?
)
