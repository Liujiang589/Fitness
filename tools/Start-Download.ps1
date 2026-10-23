param(
    [string]$PythonPath,
    [string]$BindAddress,
    [ValidateRange(1024,65535)][int]$Port = 8765
)
$ErrorActionPreference = 'Stop'
$fitnessRoot = Split-Path -Parent $PSScriptRoot
$fitnessStateDir = Join-Path $fitnessRoot '.local-download'
$fitnessDeps = Join-Path $fitnessRoot '.local-download-deps'
$fitnessServer = Join-Path $PSScriptRoot 'serve_download.py'
if (-not $PythonPath) {
    $bundledPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
    if (Test-Path -LiteralPath $bundledPython) { $PythonPath = $bundledPython }
    else { $PythonPath = (Get-Command python -ErrorAction Stop).Source }
}
if (-not (Test-Path -LiteralPath $PythonPath)) { throw 'Python was not found. Pass -PythonPath with a Python 3.9+ executable.' }
if (-not $BindAddress) {
    $fitnessNetwork = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address } |
        Sort-Object @{Expression={if($_.InterfaceAlias -match 'WLAN|Wi-Fi'){0}else{1}}} | Select-Object -First 1
    if (-not $fitnessNetwork) { throw 'No active LAN connection. Connect the computer and phone to the same Wi-Fi.' }
    $BindAddress = ($fitnessNetwork.IPv4Address | Select-Object -First 1).IPAddress
}
$fitnessUrl = "http://${BindAddress}:$Port"
$previous = $null
try { $previous = Invoke-RestMethod -Uri "$fitnessUrl/health" -TimeoutSec 2 } catch {}
if ($previous -and $previous.filename -match '^(?:独练|Duolian)-(?:V[0-9]+\.[0-9]+|[0-9.]+-debug)\.apk$') {
    Write-Output "Download server is already running: $fitnessUrl/"
    exit 0
}
& $PythonPath -c 'import sys; sys.path.insert(0,sys.argv[1]); import qrcode' $fitnessDeps 2>$null
if ($LASTEXITCODE -ne 0) {
    & $PythonPath -m pip install --target $fitnessDeps --no-deps -r (Join-Path $PSScriptRoot 'requirements-download.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the project-local QR dependency.' }
}
New-Item -ItemType Directory -Path $fitnessStateDir -Force | Out-Null
$fitnessArguments = @(('"' + $fitnessServer + '"'), '--bind', $BindAddress, '--port', $Port.ToString(),
    '--output', ('"' + $fitnessStateDir + '"'))
$fitnessProcess = Start-Process -FilePath $PythonPath -ArgumentList $fitnessArguments -WindowStyle Hidden -PassThru `
    -WorkingDirectory $fitnessRoot -RedirectStandardOutput (Join-Path $fitnessStateDir 'server.log') `
    -RedirectStandardError (Join-Path $fitnessStateDir 'server-error.log')
$fitnessState = @{ pid=$fitnessProcess.Id; url=$fitnessUrl; python=$PythonPath; script=$fitnessServer }
$fitnessState | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $fitnessStateDir 'server.json') -Encoding utf8
$fitnessHealthy = $false
for ($attempt=0; $attempt -lt 12; $attempt++) {
    Start-Sleep -Milliseconds 350
    try {
        $fitnessRelease = Invoke-RestMethod -Uri "$fitnessUrl/health" -TimeoutSec 1
        if ($fitnessRelease.filename -match '^(?:独练|Duolian)-(?:V[0-9]+\.[0-9]+|[0-9.]+-debug)\.apk$') { $fitnessHealthy=$true; break }
    } catch {}
    if ($fitnessProcess.HasExited) { break }
}
if (-not $fitnessHealthy) { throw "Download server did not become ready. See $fitnessStateDir\server-error.log" }
Write-Output "Download page: $fitnessUrl/"
Write-Output "Latest APK: $fitnessUrl/latest.apk"
Write-Output "QR image: $fitnessStateDir\download-qr.png"
Write-Output 'Keep this computer on and use the same Wi-Fi on the phone. No router port forwarding is needed.'
