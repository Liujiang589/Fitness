param([string]$JdkPath, [string]$SdkPath, [string]$GradlePath)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$javaExe = if ($JdkPath) { Join-Path $JdkPath 'bin\java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
$gradleExe = if ($GradlePath) { Join-Path $GradlePath 'bin\gradle.bat' } else { (Get-Command gradle -ErrorAction SilentlyContinue).Source }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$checks = [ordered]@{
    java = [bool]($javaExe -and (Test-Path -LiteralPath $javaExe))
    gradle = [bool]($gradleExe -and (Test-Path -LiteralPath $gradleExe))
    sdk35 = Test-Path -LiteralPath (Join-Path $SdkPath 'platforms\android-35\android.jar')
    buildTools35 = Test-Path -LiteralPath (Join-Path $SdkPath 'build-tools\35.0.0\apksigner.bat')
}
$checks | ConvertTo-Json
if ($checks.java) { & $javaExe -version }
if ($checks.gradle -and $checks.java) {
    $previousJava = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
        & $gradleExe --version
    } finally { $env:JAVA_HOME = $previousJava }
}
if ($checks.Values -contains $false) { exit 2 }
