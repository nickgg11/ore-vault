# Runs while the server is down in the smoke check. Later checks use this slot to damage a file (ADR 0005 section 6).
param([Parameter(Mandatory = $true)][string]$WorldDir)

if (-not (Test-Path (Join-Path $WorldDir 'level.dat'))) {
    Write-Output "no level.dat in $WorldDir"
    exit 3
}
Write-Output "level.dat is in $WorldDir"
exit 0
