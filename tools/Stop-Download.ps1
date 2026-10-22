$ErrorActionPreference = 'Stop'
$fitnessRoot = Split-Path -Parent $PSScriptRoot
$fitnessStateFile = Join-Path $fitnessRoot '.local-download\server.json'
if (-not (Test-Path -LiteralPath $fitnessStateFile)) { Write-Output 'No managed download server.'; exit 0 }
$fitnessState = Get-Content -LiteralPath $fitnessStateFile -Raw | ConvertFrom-Json
$fitnessProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$fitnessState.pid)" -ErrorAction SilentlyContinue
$fitnessExpectedScript = Join-Path $PSScriptRoot 'serve_download.py'
if ($fitnessProcess -and $fitnessProcess.Name -match '^python(w)?\.exe$' -and
    $fitnessProcess.CommandLine -and $fitnessProcess.CommandLine.Contains($fitnessExpectedScript)) {
    Stop-Process -Id ([int]$fitnessState.pid)
    Write-Output 'Duolian download server stopped.'
} elseif ($fitnessProcess) { throw 'The saved PID belongs to another process; it was not stopped.' }
Remove-Item -LiteralPath $fitnessStateFile
