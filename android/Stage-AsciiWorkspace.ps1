param([string]$Destination = 'C:\workspace\Duolian\android-native')
$ErrorActionPreference = 'Stop'
$source = [IO.Path]::GetFullPath($PSScriptRoot)
$target = [IO.Path]::GetFullPath($Destination)
$allowed = [IO.Path]::GetFullPath('C:\workspace\Duolian') + [IO.Path]::DirectorySeparatorChar
if (-not $target.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Destination must stay inside C:\workspace\Duolian.' }
if ($source -eq $target) { throw 'Already in staging directory.' }
if (Test-Path -LiteralPath $target) { throw 'Destination already exists. Refusing to overwrite an existing project.' }
New-Item -ItemType Directory -Path $target -Force | Out-Null
Get-ChildItem -LiteralPath $source -Force | Where-Object { $_.Name -notin '.gradle','.gradle-user-home','local.properties','.idea' } | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse
}
Write-Output $target
