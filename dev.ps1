<#
    Helper for building and testing Vanta Launcher on the emulator.

    Usage:  .\dev.ps1 <command>

      boot      start the emulator and wait for it
      run       build, install, set as default home, press home
      install   build + install only
      home      set Vanta as the default home app
      release   build a signed APK and publish it as a GitHub release
      reset     hand the home app back to the stock launcher
      restart   force-stop and relaunch (fastest way to see a change)
      log       tail logcat, filtered to this app
      devices   list attached devices/emulators
      wipe      clear our saved dock favourites
#>

param(
    [Parameter(Position = 0)]
    [ValidateSet('boot', 'run', 'install', 'home', 'reset', 'restart', 'log', 'devices', 'wipe', 'release')]
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
        # Release, always. The debug variant exists because Gradle always has one, but
        # nothing here uses it: a phone can only hold one of the two, since they are
        # signed with different keys, and the one worth testing is the one people get.
        & $gradlew assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "Build failed." }
    }
    finally { Pop-Location }
}

function Invoke-Install {
    $apk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path $apk)) { throw "APK not found at $apk" }

    $out = & $adb install -r $apk 2>&1
    $text = $out -join "`n"
    Write-Host $text

    # Android refuses an install signed with a different key, and says so in a way that
    # does not hint at the cause. Anything already on the device from before this went
    # release-only is debug signed, so this is the first thing anyone will hit.
    if ($text -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
        throw @"
The copy on the device was signed with a different key, so this cannot replace it.
Save a backup first (Settings > Backup > Save setup to a file, then pull it off the
device), then: adb uninstall $pkg
Uninstalling erases the launcher's settings, which is what the backup is for.
"@
    }
    if ($text -notmatch 'Success') { throw "Install failed." }
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
    'release' {
        # The updater finds a new version by reading the release tag and downloading the
        # APK attached to it, so the tag has to be the version and the APK has to be
        # there. Doing it here means neither depends on anyone remembering.
        $gradleFile = Join-Path $PSScriptRoot 'app/build.gradle.kts'
        $versionName = ([regex]::Match((Get-Content $gradleFile -Raw), 'versionName\s*=\s*"([^"]+)"')).Groups[1].Value
        if (-not $versionName) { throw "Could not read versionName from app/build.gradle.kts" }

        $gh = "C:\Program Files\GitHub CLI\gh.exe"
        if (-not (Test-Path $gh)) { throw "GitHub CLI not found at $gh" }

        $status = git -C $PSScriptRoot status --porcelain
        if ($status) { throw "Commit or stash your changes first; a release should match a commit." }

        Write-Host "Building release $versionName..."
        & (Join-Path $PSScriptRoot 'gradlew.bat') assembleRelease --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "Release build failed." }

        $apk = Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release.apk'
        if (-not (Test-Path $apk)) { throw "No APK at $apk" }

        $named = Join-Path $env:TEMP "vanta-$versionName.apk"
        Copy-Item $apk $named -Force

        $tag = "v$versionName"
        Write-Host "Publishing $tag..."
        & $gh release create $tag $named --title $tag --generate-notes
        if ($LASTEXITCODE -ne 0) { throw "Could not create the release." }
        Write-Host "Released $tag. Installed copies check every few hours, and on launch."
    }
}
