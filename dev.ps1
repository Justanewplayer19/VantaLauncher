<#
    Helper for building and testing Vanta Launcher on the emulator.

    Usage:  .\dev.ps1 <command>

      boot      start the emulator and wait for it
      run       build, install, set as default home, press home
      install   build + install only
      home      set Vanta as the default home app
      reset     hand the home app back to the stock launcher
      restart   force-stop and relaunch (fastest way to see a change)
      log       tail logcat, filtered to this app
      devices   list attached devices/emulators
      wipe      clear our saved dock favourites
#>

param(
    [Parameter(Position = 0)]
    [ValidateSet('boot', 'run', 'install', 'home', 'reset', 'restart', 'log', 'devices', 'wipe')]
    [string]$Command = 'run'
)

# Deliberately NOT 'Stop'. Windows PowerShell turns anything a native exe writes to
# stderr into an ErrorRecord, and gradlew/adb both write harmless warnings there.
# With 'Stop' those abort the script. Exit codes are checked explicitly instead.
$ErrorActionPreference = 'Continue'
$pkg = 'com.maria.homelauncher'
$avd = 'Medium_Phone_API_37.0'
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$activity = "$pkg/.MainActivity"
$root = $PSScriptRoot

function Find-Adb {
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "${env:ProgramFiles}\Android\Android Studio\plugins\android\lib\adb.exe"
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    throw "Could not find adb.exe. Install Android Studio, or set ANDROID_HOME."
}

$adb = Find-Adb

function Assert-Device {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
    if (-not $lines) {
        throw "No device attached. Run '.\dev.ps1 boot' first."
    }
}

function Invoke-Build {
    # gradlew.bat needs a JVM to start at all, before it ever reads gradle.properties,
    # so org.gradle.java.home is not enough on its own.
    if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
    Push-Location $root
    try {
        $gradlew = Join-Path $root 'gradlew.bat'
        if (-not (Test-Path $gradlew)) {
            throw "gradlew.bat is missing. Regenerate it with: gradle wrapper --gradle-version 8.9"
        }
        & $gradlew assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "Build failed." }
    }
    finally { Pop-Location }
}

function Invoke-Install {
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw "APK not found at $apk" }
    & $adb install -r $apk
}

# The stock launcher differs by system image, so try the usual suspects in order.
function Set-StockHome {
    $stock = @(
        'com.google.android.apps.nexuslauncher/.NexusLauncherActivity',
        'com.android.launcher3/.Launcher',
        'com.android.launcher3/com.android.launcher3.Launcher'
    )
    foreach ($s in $stock) {
        & $adb shell cmd package set-home-activity $s 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Home app -> $s"
            return
        }
    }
    Write-Host "Could not set a stock launcher automatically."
    Write-Host "Do it by hand: Settings > Apps > Default apps > Home app"
}

switch ($Command) {
    'boot' {
        $env:JAVA_HOME = $jdk
        $cli = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\android.exe"
        if (-not (Test-Path $cli)) { throw "android CLI not found at $cli" }
        & $cli emulator start $avd --no-metrics
    }
    'devices' {
        & $adb devices -l
    }
    'install' {
        Invoke-Build
        Assert-Device
        Invoke-Install
    }
    'run' {
        Invoke-Build
        Assert-Device
        Invoke-Install
        & $adb shell cmd package set-home-activity $activity
        & $adb shell input keyevent KEYCODE_HOME
        Write-Host "`nRunning. '.\dev.ps1 reset' puts the stock launcher back."
    }
    'home' {
        Assert-Device
        & $adb shell cmd package set-home-activity $activity
        & $adb shell input keyevent KEYCODE_HOME
    }
    'reset' {
        Assert-Device
        Set-StockHome
        & $adb shell input keyevent KEYCODE_HOME
    }
    'restart' {
        Assert-Device
        & $adb shell am force-stop $pkg
        & $adb shell input keyevent KEYCODE_HOME
    }
    'log' {
        Assert-Device
        & $adb logcat --pid=$(& $adb shell pidof -s $pkg)
    }
    'wipe' {
        Assert-Device
        & $adb shell pm clear $pkg
        Write-Host "Dock favourites cleared."
    }
}
