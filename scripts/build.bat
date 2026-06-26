@echo off
REM =============================================================================
REM AppControl (Lessmore) Android Build Script - Windows
REM =============================================================================
REM Usage (args pass through to gradlew):
REM   build.bat                       Build debug APK
REM   build.bat assembleDebug         Build debug APK
REM   build.bat assembleRelease       Build release APK
REM   build.bat testDebugUnitTest     Run unit tests
REM   build.bat clean                 Clean build outputs
REM
REM Environment Variables (auto-detected if not set):
REM   JAVA_HOME      Java 17+ installation path
REM   ANDROID_HOME   Android SDK path (falls back to local.properties sdk.dir)
REM =============================================================================

setlocal enabledelayedexpansion

for %%I in ("%~dp0..") do set "PROJECT_ROOT=%%~fI"
cd /d "%PROJECT_ROOT%"

REM --- Detect JAVA_HOME ---
if not defined JAVA_HOME (
    for %%P in (
        "C:\Program Files\Java\jdk-17"
        "C:\Program Files\Eclipse Adoptium\jdk-17"
        "D:\programdata\jdk-17\jdk-17"
        "D:\ProgramData\jdk-17\jdk-17"
    ) do (
        if not defined JAVA_HOME if exist "%%~P\bin\java.exe" set "JAVA_HOME=%%~P"
    )
)
if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME not set and JDK 17 not found in common locations.
    echo   Set it manually: set "JAVA_HOME=path\to\jdk-17"
    exit /b 1
)

REM --- Detect ANDROID_HOME ---
if not defined ANDROID_HOME (
    if exist "%PROJECT_ROOT%\local.properties" (
        for /f "tokens=2 delims==" %%A in ('findstr /b /i "sdk.dir" "%PROJECT_ROOT%\local.properties"') do (
            set "ANDROID_HOME=%%A"
            set "ANDROID_HOME=!ANDROID_HOME:\\=\!"
        )
    )
)
if not defined ANDROID_HOME (
    for %%P in (
        "D:\programdata\android-sdk"
        "D:\ProgramData\android-sdk"
        "C:\Android\Sdk"
        "%LOCALAPPDATA%\Android\Sdk"
    ) do (
        if not defined ANDROID_HOME if exist "%%~P" set "ANDROID_HOME=%%~P"
    )
)
if not defined ANDROID_HOME (
    echo [WARNING] ANDROID_HOME not found; relying on local.properties sdk.dir.
)

echo [INFO] JAVA_HOME    = !JAVA_HOME!
echo [INFO] ANDROID_HOME = !ANDROID_HOME!

call "%PROJECT_ROOT%\gradlew.bat" --no-daemon %*
set "EXITCODE=%ERRORLEVEL%"
endlocal & exit /b %EXITCODE%
