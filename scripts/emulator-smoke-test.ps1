param(
    [string]$Serial = "",
    [string]$PackageName = "com.appcontrol",
    [string]$ActivityName = ".presentation.MainActivity",
    [string]$Password = "1234",
    [string]$ArtifactDir = "qa-artifacts/emulator-smoke",
    [switch]$ResetData,
    [switch]$SkipInstall,
    [switch]$RunGradleChecks
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[emulator-smoke] $Message"
}

function Get-ProjectRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Get-LocalSdkDir {
    param([string]$ProjectRoot)

    $localProperties = Join-Path $ProjectRoot "local.properties"
    if (-not (Test-Path $localProperties)) {
        return $null
    }

    foreach ($line in Get-Content $localProperties) {
        if ($line -match "^sdk\.dir=(.+)$") {
            return ($Matches[1] -replace "\\\\", "\").Trim()
        }
    }

    return $null
}

function Resolve-Adb {
    param([string]$ProjectRoot)

    $candidates = @()

    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools/adb.exe")
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools/adb")
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb.exe")
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb")
    }

    $sdkDir = Get-LocalSdkDir $ProjectRoot
    if ($sdkDir) {
        $candidates += (Join-Path $sdkDir "platform-tools/adb.exe")
        $candidates += (Join-Path $sdkDir "platform-tools/adb")
    }

    $pathAdb = Get-Command adb -ErrorAction SilentlyContinue
    if ($pathAdb) {
        $candidates += $pathAdb.Source
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "adb was not found. Set ANDROID_HOME/ANDROID_SDK_ROOT or sdk.dir in local.properties."
}

function Invoke-Adb {
    param(
        [string]$Adb,
        [string]$Serial,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )

    if ($Serial) {
        & $Adb -s $Serial @Args
    } else {
        & $Adb @Args
    }
}

function Resolve-Serial {
    param([string]$Adb, [string]$RequestedSerial)

    if ($RequestedSerial) {
        return $RequestedSerial
    }

    $devices = & $Adb devices | Select-String "device$" | ForEach-Object {
        ($_ -split "\s+")[0]
    }

    if (-not $devices -or $devices.Count -eq 0) {
        throw "No online adb device found. Start an emulator first, for example: emulator -avd LessMore_API26"
    }

    return $devices[0]
}

function Save-UiDump {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$Path
    )

    $content = Invoke-Adb -Adb $Adb -Serial $Serial -Args @("exec-out", "uiautomator", "dump", "/dev/tty")
    $content | Out-File -Encoding utf8 $Path
}

function Save-Screenshot {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$Path
    )

    $remote = "/sdcard/lessmore-smoke.png"
    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("shell", "screencap", "-p", $remote) | Out-Null
    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("pull", $remote, $Path) | Out-Null
    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("shell", "rm", $remote) | Out-Null
}

function Assert-UiContains {
    param(
        [string]$Path,
        [string[]]$Needles
    )

    $content = Get-Content -Raw $Path
    foreach ($needle in $Needles) {
        if ($content -notlike "*$needle*") {
            throw "Expected UI text not found: $needle. See $Path"
        }
    }
}

function Get-CenterForText {
    param(
        [string]$Path,
        [string]$Text
    )

    $escaped = [regex]::Escape($Text)
    $line = Get-Content -Raw $Path
    $match = [regex]::Match($line, "text=`"$escaped`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
    if (-not $match.Success) {
        return $null
    }

    $x1 = [int]$match.Groups[1].Value
    $y1 = [int]$match.Groups[2].Value
    $x2 = [int]$match.Groups[3].Value
    $y2 = [int]$match.Groups[4].Value

    return @{
        X = [int](($x1 + $x2) / 2)
        Y = [int](($y1 + $y2) / 2)
    }
}

function Tap-Text {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$UiPath,
        [string]$Text
    )

    $center = Get-CenterForText $UiPath $Text
    if (-not $center) {
        throw "Cannot tap missing UI text: $Text"
    }

    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("shell", "input", "tap", "$($center.X)", "$($center.Y)") | Out-Null
}

function Enter-Text {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$Text
    )

    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("shell", "input", "text", $Text) | Out-Null
}

function Press-Back {
    param([string]$Adb, [string]$Serial)
    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("shell", "input", "keyevent", "4") | Out-Null
}

function Tap {
    param([string]$Adb, [string]$Serial, [int]$X, [int]$Y)
    Invoke-Adb -Adb $Adb -Serial $Serial -Args @("shell", "input", "tap", "$X", "$Y") | Out-Null
}

function Verify-PasswordDialog {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$UiPath,
        [string]$Password
    )

    $content = Get-Content -Raw $UiPath
    if ($content -notlike "*Admin password*" -and $content -notlike "*Verify*") {
        return
    }

    Tap-Text $Adb $Serial $UiPath "Admin password"
    Start-Sleep -Milliseconds 300
    Enter-Text $Adb $Serial $Password
    Start-Sleep -Milliseconds 300
    Press-Back $Adb $Serial
    Start-Sleep -Milliseconds 500
    Tap-Text $Adb $Serial $UiPath "Verify"
    Start-Sleep -Seconds 2
}

function Run-Gradle {
    param(
        [string]$ProjectRoot,
        [string[]]$Tasks
    )

    Push-Location $ProjectRoot
    try {
        if (Test-Path (Join-Path $ProjectRoot "gradlew.bat")) {
            & (Join-Path $ProjectRoot "gradlew.bat") @Tasks "--console=plain"
        } else {
            & (Join-Path $ProjectRoot "gradlew") @Tasks "--console=plain"
        }
    } finally {
        Pop-Location
    }
}

$projectRoot = Get-ProjectRoot
$artifactRoot = Join-Path $projectRoot $ArtifactDir
New-Item -ItemType Directory -Force $artifactRoot | Out-Null

$adb = Resolve-Adb $projectRoot
$Serial = Resolve-Serial $adb $Serial

Write-Step "Using adb: $adb"
Write-Step "Using device: $Serial"
Write-Step "Artifacts: $artifactRoot"

if (-not $SkipInstall) {
    Write-Step "Installing debug APK"
    Run-Gradle $projectRoot @(":app:installDebug")
}

if ($RunGradleChecks) {
    Write-Step "Running unit tests and debug assemble"
    Run-Gradle $projectRoot @("testDebugUnitTest", "assembleDebug")
}

Write-Step "Launching app"
Invoke-Adb -Adb $adb -Serial $Serial -Args @("logcat", "-c") | Out-Null
Invoke-Adb -Adb $adb -Serial $Serial -Args @("shell", "am", "force-stop", $PackageName) | Out-Null
if ($ResetData) {
    Invoke-Adb -Adb $adb -Serial $Serial -Args @("shell", "pm", "clear", $PackageName) | Out-Null
}
Invoke-Adb -Adb $adb -Serial $Serial -Args @("shell", "am", "start", "-n", "$PackageName/$ActivityName") | Out-Null
Start-Sleep -Seconds 2

$initialUi = Join-Path $artifactRoot "01-initial.xml"
Save-UiDump $adb $Serial $initialUi
Save-Screenshot $adb $Serial (Join-Path $artifactRoot "01-initial.png")

$initialContent = Get-Content -Raw $initialUi
if ($initialContent -like "*Set Admin Password*") {
    Write-Step "Setting fresh admin password"
    Tap-Text $adb $Serial $initialUi "Password (at least 4 chars)"
    Start-Sleep -Milliseconds 300
    Enter-Text $adb $Serial $Password
    Start-Sleep -Milliseconds 300
    Tap-Text $adb $Serial $initialUi "Confirm password"
    Start-Sleep -Milliseconds 300
    Enter-Text $adb $Serial $Password
    Start-Sleep -Milliseconds 500
    Press-Back $adb $Serial
    Start-Sleep -Milliseconds 800

    $passwordUi = Join-Path $artifactRoot "02-password-ready.xml"
    Save-UiDump $adb $Serial $passwordUi
    Tap-Text $adb $Serial $passwordUi "Done"
    Start-Sleep -Seconds 3
} elseif ($initialContent -notlike "*Monitor service*") {
    throw "Expected Set Admin Password or main screen. Grant Usage Access and Overlay permissions, then rerun."
}

$mainUi = Join-Path $artifactRoot "03-main.xml"
Save-UiDump $adb $Serial $mainUi
Save-Screenshot $adb $Serial (Join-Path $artifactRoot "03-main.png")
Assert-UiContains $mainUi @("Monitor service", "Search apps", "Apps", "Stats", "Settings")

Write-Step "Checking bottom navigation"
Tap-Text $adb $Serial $mainUi "Stats"
Start-Sleep -Seconds 1
$statsUi = Join-Path $artifactRoot "04-stats.xml"
Save-UiDump $adb $Serial $statsUi
Assert-UiContains $statsUi @("Daily stats")

Tap-Text $adb $Serial $statsUi "Settings"
Start-Sleep -Seconds 1
$settingsUi = Join-Path $artifactRoot "05-settings.xml"
Save-UiDump $adb $Serial $settingsUi
Assert-UiContains $settingsUi @("Biometric", "Forced lock mode", "Language")

Tap-Text $adb $Serial $settingsUi "Apps"
Start-Sleep -Seconds 1
$appsUi = Join-Path $artifactRoot "06-apps.xml"
Save-UiDump $adb $Serial $appsUi
Assert-UiContains $appsUi @("Search apps")

$appsContent = Get-Content -Raw $appsUi
if ($appsContent -notlike "*Configure rules*") {
    Write-Step "Enabling the first visible app as Controlled"
    Tap $adb $Serial 260 627
    Start-Sleep -Seconds 1
    $toggleAuthUi = Join-Path $artifactRoot "07-toggle-auth.xml"
    Save-UiDump $adb $Serial $toggleAuthUi
    Verify-PasswordDialog $adb $Serial $toggleAuthUi $Password
}

$controlledUi = Join-Path $artifactRoot "08-controlled.xml"
Save-UiDump $adb $Serial $controlledUi
Assert-UiContains $controlledUi @("Configure rules")

Write-Step "Opening rule screen"
Tap-Text $adb $Serial $controlledUi "Configure rules"
Start-Sleep -Seconds 1
$ruleAuthUi = Join-Path $artifactRoot "09-rule-auth.xml"
Save-UiDump $adb $Serial $ruleAuthUi
Verify-PasswordDialog $adb $Serial $ruleAuthUi $Password

$ruleUi = Join-Path $artifactRoot "10-rule-screen.xml"
Save-UiDump $adb $Serial $ruleUi
Save-Screenshot $adb $Serial (Join-Path $artifactRoot "10-rule-screen.png")
Assert-UiContains $ruleUi @("Daily usage limit", "Save usage limit", "Allowed periods", "Add period")

$logcatPath = Join-Path $artifactRoot "logcat.txt"
Invoke-Adb -Adb $adb -Serial $Serial -Args @("logcat", "-d") | Out-File -Encoding utf8 $logcatPath
$crashPath = Join-Path $artifactRoot "crash-buffer.txt"
Invoke-Adb -Adb $adb -Serial $Serial -Args @("logcat", "-d", "-b", "crash") | Out-File -Encoding utf8 $crashPath

$crashText = Get-Content -Raw $crashPath
if ($crashText -match "FATAL EXCEPTION|ANR") {
    throw "Crash buffer contains a fatal issue. See $crashPath"
}

$appPid = Invoke-Adb -Adb $adb -Serial $Serial -Args @("shell", "pidof", "-s", $PackageName)
if (-not $appPid) {
    throw "App process is not running after smoke test."
}

Write-Step "Smoke test passed. App pid: $appPid"
