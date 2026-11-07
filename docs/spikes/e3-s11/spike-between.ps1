# E3-S11 (#245) spike, while the server is down. Check 4, second half: the stop sequence wrote nothing of the old
# level into the remade folder. The old Vault had a force-loaded chunk in region r.1.1; the new one never reaches it.
param([Parameter(Mandatory = $true)][string]$WorldDir)
$ErrorActionPreference = 'Stop'
$vault = Join-Path $WorldDir 'dimensions\orevault\vault_0000000a00004000800000000000000a'
$failed = $false
foreach ($rel in @('region\r.1.1.mca', 'entities\r.1.1.mca', 'poi\r.1.1.mca')) {
    $path = Join-Path $vault $rel
    if (Test-Path $path) { Write-Output "FAIL: $path exists"; $failed = $true } else { Write-Output "absent: $rel" }
}
if (-not (Test-Path (Join-Path $vault 'orevault_vault.dat'))) { Write-Output 'FAIL: no type file'; $failed = $true }
if (Test-Path "$vault.deleting") { Write-Output "FAIL: $vault.deleting left behind"; $failed = $true }
Get-ChildItem -Recurse -File $vault | ForEach-Object { Write-Output ("file: {0} {1}" -f $_.FullName.Substring($vault.Length), $_.Length) }
if ($failed) { exit 1 }
exit 0
