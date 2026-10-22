param(
    [Parameter(Mandatory=$true)][string]$JdkPath,
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [Parameter(Mandatory=$true)][string]$GradlePath,
    [switch]$DeviceTests,
    [switch]$ConfigureOnly,
    [switch]$UseLocalCache,
    [string[]]$Tasks,
    [switch]$VerboseBuild
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$javaExe = Join-Path $JdkPath 'bin\java.exe'
$gradleExe = Join-Path $GradlePath 'bin\gradle.bat'
if (-not (Test-Path -LiteralPath $javaExe)) { throw 'JDK not found.' }
if (-not (Test-Path -LiteralPath $gradleExe)) { throw 'Gradle not found.' }
if (-not $ConfigureOnly -and -not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms\android-35\android.jar'))) { throw 'Android SDK platform 35 not found.' }
# Process-local values only. No registry or global PATH changes.
$oldJava = $env:JAVA_HOME
$oldSdk = $env:ANDROID_HOME
$oldPath = $env:PATH
$oldGradleUser = $env:GRADLE_USER_HOME
$oldAndroidUser = $env:ANDROID_USER_HOME
$oldMavenRelay = $env:DUOLIAN_MAVEN_RELAY
try {
    $env:JAVA_HOME = $JdkPath
    $env:ANDROID_HOME = $SdkPath
    $env:PATH = (Join-Path $JdkPath 'bin') + [IO.Path]::PathSeparator + $env:PATH
    $env:GRADLE_USER_HOME = Join-Path $root '.gradle-user-home'
    $env:ANDROID_USER_HOME = Join-Path $root '.android-user-home'
    if (-not $ConfigureOnly) {
        $signingDirectory = Join-Path $root '.local-signing'
        $debugStore = Join-Path $signingDirectory 'duolian-debug.keystore'
        if (-not (Test-Path -LiteralPath $debugStore)) {
            New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
            $previousStore = Join-Path $env:ANDROID_USER_HOME 'debug.keystore'
            if (Test-Path -LiteralPath $previousStore) {
                Copy-Item -LiteralPath $previousStore -Destination $debugStore
            } else {
                & (Join-Path $JdkPath 'bin\keytool.exe') -genkeypair -noprompt -keystore $debugStore -storetype JKS -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
                if ($LASTEXITCODE -ne 0) { throw 'Could not generate project-local debug signing key.' }
            }
        }
    }
    if ($UseLocalCache) { $env:DUOLIAN_MAVEN_RELAY = 'http://127.0.0.1:17861' }
    $gradleFlags = @('--no-daemon', '--console=plain', '--stacktrace', '-Pandroid.overridePathCheck=true', '-Dorg.gradle.internal.http.connectionTimeout=30000', '-Dorg.gradle.internal.http.socketTimeout=300000')
    if ($VerboseBuild) { $gradleFlags += '--info' }
    # Reuse the session's existing proxy without changing system networking.
    if ($env:HTTPS_PROXY -and -not $UseLocalCache) {
        $proxyUri = [Uri]$env:HTTPS_PROXY
        if ($proxyUri.Host -and $proxyUri.Port -gt 0) {
            $gradleFlags += "-Dhttps.proxyHost=$($proxyUri.Host)"
            $gradleFlags += "-Dhttps.proxyPort=$($proxyUri.Port)"
            $gradleFlags += "-Dhttp.proxyHost=$($proxyUri.Host)"
            $gradleFlags += "-Dhttp.proxyPort=$($proxyUri.Port)"
        }
    }
    Push-Location -LiteralPath $root
    try {
        if ($ConfigureOnly) {
            & $gradleExe @gradleFlags '--info' 'help'
        } else {
            [string[]]$selectedTasks = @(if ($Tasks) { $Tasks } else { ':app:testDebugUnitTest'; ':app:lintDebug'; ':app:assembleDebug'; ':app:assembleDebugAndroidTest' })
            & $gradleExe @gradleFlags @selectedTasks
        }
        if ($LASTEXITCODE -ne 0) { throw "Build or checks failed (exit $LASTEXITCODE)." }
        if ($DeviceTests) {
            & $gradleExe @gradleFlags ':app:connectedDebugAndroidTest'
            if ($LASTEXITCODE -ne 0) { throw "Device tests failed (exit $LASTEXITCODE)." }
        }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $oldJava
    $env:ANDROID_HOME = $oldSdk
    $env:PATH = $oldPath
    $env:GRADLE_USER_HOME = $oldGradleUser
    $env:ANDROID_USER_HOME = $oldAndroidUser
    $env:DUOLIAN_MAVEN_RELAY = $oldMavenRelay
}
