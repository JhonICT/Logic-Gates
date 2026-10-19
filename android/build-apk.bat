@echo off
setlocal EnableExtensions
cd /d "%~dp0"
if not exist "app\google-services.json" copy "app\google-services.json.example" "app\google-services.json" >nul
set "GRADLE_VERSION=8.13"
set "GRADLE_ROOT=%LOCALAPPDATA%\74LSLogicLab\gradle-%GRADLE_VERSION%"
set "GRADLE_EXE=%GRADLE_ROOT%\bin\gradle.bat"

if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk\platforms\android-36\android.jar" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

if exist "%GRADLE_EXE%" goto :gradle_ready
where gradle >nul 2>nul
if not errorlevel 1 goto :system_gradle

echo Gradle %GRADLE_VERSION% is not installed. Downloading it for this project...
set "DOWNLOAD_ROOT=%LOCALAPPDATA%\74LSLogicLab"
if not exist "%DOWNLOAD_ROOT%" mkdir "%DOWNLOAD_ROOT%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $root=$env:LOCALAPPDATA + '\\74LSLogicLab'; $zip=Join-Path $root 'gradle-8.13-bin.zip'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath $root -Force; Remove-Item -LiteralPath $zip -Force;"
if errorlevel 1 (
  echo Could not download Gradle. Connect to the internet or install Gradle 8.13, then run this file again.
  exit /b 1
)
if not exist "%GRADLE_EXE%" (
  echo Gradle extraction failed: %GRADLE_EXE%
  exit /b 1
)
goto :gradle_ready

:system_gradle
echo Using the Gradle version already installed on PATH.
gradle --no-daemon assembleDebug
if errorlevel 1 exit /b %errorlevel%
goto :success

:gradle_ready
if not exist "%ANDROID_HOME%\platforms\android-36\android.jar" (
  echo Android SDK Platform 36 was not found.
  echo Install Android SDK Platform 36 in Android Studio ^> SDK Manager.
  echo If your SDK is in a non-default folder, set ANDROID_HOME before running this file.
  exit /b 1
)
call "%GRADLE_EXE%" --no-daemon assembleDebug
if errorlevel 1 exit /b %errorlevel%

:success
echo.
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
endlocal
