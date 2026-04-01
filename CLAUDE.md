# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AppControl (internal name: "Lessmore") is an Android app usage control application that helps users manage and limit app usage time. It monitors target apps via a foreground service and locks them with an overlay when limits are exceeded.

### Core Features

1. **Target App Management**: Select apps to control, whitelist essential apps
2. **Usage Time Limits**: Set daily usage limits per app (1-1440 minutes), with 80% warning notification
3. **Allowed Time Periods**: Define time windows when apps are allowed (e.g., 08:00-12:00)
4. **Lock Screen**: Overlay blocks app when limits exceeded, showing reason and next available time
5. **Usage Statistics**: Daily and weekly usage stats with Vico charts
6. **Password/Biometric Protection**: 4+ digit password or biometric auth protects all settings
7. **Forced Lock Mode**: High-priority overlay prevents bypass (requires Device Admin)

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Clean build
./gradlew clean assembleDebug

# Or use the build script (recommended for WSL environments)
cd scripts && ./build.sh -t debug       # Debug build
./build.sh -t release                   # Release build
./build.sh -T                           # Build with tests
./build.sh -c                           # Clean build
```

APK output: `app/build/outputs/apk/<build_type>/Lessmore-<build_type>.apk`

## Architecture

**Pattern**: MVVM + Clean Architecture with Hilt dependency injection

```
presentation/          # Compose UI, ViewModels, Navigation
├── theme/             # Material 3 theming with Dynamic Color
├── ui/                # Screens, NavGraph, PermissionUtils
└── viewmodel/         # AppViewModel (single ViewModel for all screens)

domain/                # Business logic layer
├── model/             # Domain models (InstalledAppInfo, LockReason)
├── repository/        # Repository interfaces
└── usecase/           # Use cases (one per operation)

data/                  # Data layer
├── db/                # Room entities, DAOs, Database, DatabaseModule
├── preferences/       # EncryptedSharedPreferences for auth settings
└── repository/        # Repository implementations

service/               # Android system services
├── monitor/           # MonitorService, MidnightResetReceiver
├── overlay/           # LockOverlayManager, LockOverlayContent
└── receiver/          # BootReceiver, AppDeviceAdminReceiver
```

### Key Components

- **MonitorService**: Foreground service polling foreground app every second via `UsageStatsManager`. Checks lock status via `CheckAppLockStatusUseCase`, updates usage via `UpdateUsageUseCase`, sends 80% warning via `CheckUsageWarningUseCase`. Implements midnight reset via `AlarmManager` and auto-restart via `START_STICKY` + `onTaskRemoved`.
- **LockOverlayManager**: Manages `TYPE_APPLICATION_OVERLAY` window for lock screen. Shows different content based on `LockReason` (UsageLimitExceeded vs OutsideAllowedPeriod).
- **AppNavGraph**: Navigation routes: `onboarding` → `set_password` → `main` → `rule/{packageName}`. Start destination determined by password existence and permission status.
- **AppViewModel**: Single ViewModel orchestrating all use cases. Exposes `StateFlow` for UI state.

### Database Schema (Room)

| Entity | Key Fields | Purpose |
|--------|-----------|---------|
| **TargetApp** | packageName, appName, isWhitelisted, usageLimitMinutes | Tracked apps with limits |
| **AllowedPeriod** | id, packageName, startTime, endTime | Time windows per app |
| **UsageRecord** | id, packageName, date, usageDurationSeconds, openCount | Daily usage stats |
| **AppSettings** | passwordHash, biometricEnabled, forcedLockEnabled, lockoutUntil, failedAttempts | Auth settings |

### Core Monitoring Flow

```
MonitorService (every 1s)
    → getForegroundPackageName() via UsageStatsManager
    → CheckAppLockStatusUseCase
        → Check whitelist, UsageLimit, AllowedPeriod
        → Return LockReason (NotLocked / UsageLimitExceeded / OutsideAllowedPeriod)
    → If locked: LockOverlayManager.showLockScreen()
    → If not locked: UpdateUsageUseCase (+1 second), check 80% warning
```

## Testing

Uses Kotest with JUnit 5 platform:

```bash
./gradlew testDebugUnitTest
```

Test report: `app/build/reports/tests/testDebugUnitTest/`

Tests use `DescribeSpec` style with fake repository implementations. See `AuthUseCasesTest.kt` for patterns.

### Test Files

- `AuthUseCasesTest.kt`: SetPassword, VerifyPassword, ChangePassword, ToggleBiometric, PasswordHashUtil
- `MonitorUseCasesTest.kt`: CheckAppLockStatus, UpdateUsage, ResetDailyUsage
- `StatsUseCasesTest.kt`: GetDailyStats, GetWeeklyStats, CleanOldData
- `LockOverlayContentTest.kt`: Overlay UI logic

## Permissions Required

| Permission | Purpose | Required |
|------------|---------|----------|
| `PACKAGE_USAGE_STATS` | Track foreground app via UsageStatsManager | Yes |
| `SYSTEM_ALERT_WINDOW` | Show lock overlay | Yes |
| `FOREGROUND_SERVICE` | Background monitoring | Yes |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on boot | Yes |
| `USE_BIOMETRIC` | Fingerprint/face auth | Optional |
| `POST_NOTIFICATIONS` | Warning notifications | Optional |
| Device Admin | Forced lock mode, prevent uninstall | Optional |

## Navigation Flow

```
AppNavGraph startDestination logic:
  if (!usagePermission || !overlayPermission) → onboarding
  else if (!passwordSet) → set_password
  else → main

OnboardingScreen: Request USAGE_STATS, OVERLAY, DEVICE_ADMIN permissions
SetPasswordScreen: Set 4+ digit password, optional biometric enable
MainScreen: Bottom nav with Apps/Stats/Settings tabs, monitor service toggle
AppRuleScreen: UsageLimit slider, AllowedPeriod management (requires auth)
```

## Auth Protection Rules

Operations requiring password verification:
- Add/Remove target app
- Toggle whitelist
- Modify UsageLimit or AllowedPeriod
- Enable/disable biometric
- Enable/disable forced lock mode
- Change password

Password policy:
- Minimum 4 characters
- SHA-256 hash stored in EncryptedSharedPreferences
- 5 consecutive wrong attempts → 15 minute lockout
- Biometric: 3 failures → fallback to password

## Tech Stack

- Kotlin 1.9.22, Java 17
- Android Gradle Plugin 8.2.2
- Jetpack Compose (BOM 2024.01.00) + Material Design 3
- Hilt 2.50, Room 2.6.1
- Kotest 5.8.0 for testing
- Vico 1.13.1 for charts
- AndroidX Biometric 1.1.0
- EncryptedSharedPreferences (security-crypto 1.1.0-alpha06)

## Implementation Status

See `.kiro/specs/app-usage-control/tasks.md` for detailed task tracking.

Completed: Project setup, data layer, domain layer, MonitorService, BootReceiver, basic overlay, navigation, most UI screens.

In progress: Forced lock mode anti-bypass, DeviceAdminReceiver integration.
