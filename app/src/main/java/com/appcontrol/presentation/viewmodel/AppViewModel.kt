package com.appcontrol.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
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
class AppViewModel @Inject constructor(
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
            authRepository.initializeSettings()
            loadInstalledApps()
        }
    }

    fun loadInstalledApps() {
        _loading.update { true }
        viewModelScope.launch {
            runCatching { getInstalledAppsUseCase() }
                .onSuccess { _installedApps.value = it }
                .onFailure { _message.value = it.message ?: "加载应用列表失败" }
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
                _message.value = it.message ?: "更新受控应用失败"
            }
        }
    }

    fun toggleWhitelist(packageName: String, enabled: Boolean, appName: String? = null) {
        viewModelScope.launch {
            runCatching { toggleWhitelistUseCase(packageName, enabled, appName) }
                .onFailure { _message.value = it.message ?: "更新白名单失败" }
        }
    }

    fun setUsageLimit(packageName: String, minutes: Int?) {
        viewModelScope.launch {
            runCatching { setUsageLimitUseCase(packageName, minutes) }
                .onFailure { _message.value = it.message ?: "设置时长失败" }
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
            }.onFailure { _message.value = it.message ?: "新增时间段失败" }
        }
    }

    fun removeAllowedPeriod(period: AllowedPeriod) {
        viewModelScope.launch {
            runCatching { manageAllowedPeriodsUseCase.removePeriod(period) }
                .onFailure { _message.value = it.message ?: "删除时间段失败" }
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
                _message.value = it.message ?: "设置密码失败"
            }
        }
    }

    fun verifyPassword(password: String, onResult: (VerifyPasswordUseCase.VerifyResult) -> Unit) {
        viewModelScope.launch {
            onResult(verifyPasswordUseCase(password))
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { changePasswordUseCase(oldPassword, newPassword) }
                .onSuccess { success ->
                    if (success) {
                        onSuccess()
                    } else {
                        _message.value = "旧密码不正确"
                    }
                }
                .onFailure { _message.value = it.message ?: "修改密码失败" }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { toggleBiometricUseCase(enabled) }
                .onFailure { _message.value = it.message ?: "更新生物识别设置失败" }
        }
    }

    fun setForcedLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { authRepository.setForcedLockEnabled(enabled) }
                .onFailure { _message.value = it.message ?: "更新强制锁定设置失败" }
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
