@echo off
setlocal enabledelayedexpansion

set GRADLE_VERSION=7.4.2
set DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
set BASE_DIR=%~dp0

REM Allow using a separate GRADLE_USER_HOME to avoid writing into the project folder
if defined GRADLE_USER_HOME (
  set INSTALL_PARENT=%GRADLE_USER_HOME%
) else (
  set INSTALL_PARENT=%BASE_DIR%\.gradle
)

set INSTALL_DIR=%INSTALL_PARENT%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
  echo Gradle %GRADLE_VERSION% not found. Downloading from %DIST_URL% to %INSTALL_PARENT% ...
  set TMP_ZIP=%TEMP%\gradle-%RANDOM%.zip
  powershell -NoProfile -Command "try { (New-Object System.Net.WebClient).DownloadFile('%DIST_URL%','%TMP_ZIP%') } catch { exit 1 }" >nul 2>&1
  if exist "%TMP_ZIP%" (
    if not exist "%INSTALL_PARENT%" mkdir "%INSTALL_PARENT%"
    powershell -NoProfile -Command "Expand-Archive -Path '%TMP_ZIP%' -DestinationPath '%INSTALL_PARENT%' -Force" >nul 2>&1
    del "%TMP_ZIP%"
  ) else (
    echo Error: failed to download Gradle. Ensure PowerShell is available and can access the internet.
    exit /b 1
  )
)

"%GRADLE_BIN%" %*
