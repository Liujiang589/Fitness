$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$errors = @()
Get-ChildItem -LiteralPath $root -Filter '*.ps1' | ForEach-Object {
    $tokens = $null; $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$tokens, [ref]$parseErrors)
    $errors += $parseErrors
}
[xml]$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'app\src\main\AndroidManifest.xml')
[xml]$styles = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'app\src\main\res\values\styles.xml')
$permissions = @($manifest.manifest.'uses-permission' | Where-Object { $_ })
$kt = @(Get-ChildItem -LiteralPath $root -Filter '*.kt' -Recurse)
$testCount = ($kt | Select-String -Pattern '@Test').Count
$result = [ordered]@{
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
    checkScope = 'Source structure only; NOT Kotlin compilation or runtime tests'
    powershellSyntaxErrors = $errors.Count
    xmlParse = 'passed'
    manifestPermissions = $permissions.Count
    kotlinSourceFiles = $kt.Count
    writtenTests = $testCount
    testsExecuted = 0
    apkBuilt = $false
}
$result | ConvertTo-Json
if ($errors.Count -gt 0 -or $permissions.Count -gt 0 -or $testCount -ne 20) { throw 'Source structure verification failed.' }
