# Runs while the server is down in the older-layout check (#425, ruling 4). Overwrites Vault 0000000c's type file with
# the one a layout-1 build wrote: gzip NBT {data_version: 1, variant: "orevault:ore_vault"}, with no layout field, which
# reads as layout 1 (VaultTypeFile). The bytes are written here, not by NbtIo; if the second lifetime finds no archive
# WARN, check this file first (open it with an NBT viewer).
param([Parameter(Mandatory = $true)][string]$WorldDir)

$folder = Join-Path $WorldDir 'dimensions\orevault\vault_0000000c00004000800000000000000c'
$typeFile = Join-Path $folder 'orevault_vault.dat'
if (-not (Test-Path $typeFile)) {
    Write-Output "no type file at $typeFile; lifetime 1 did not make the Vault"
    exit 3
}
$layoutOne = 'H4sIAAAAAAAC/+NiYGBm4ElJLEmML0stKs7Mz2NgYGDkYGAvSyzKTMwrYRDKL0otSyzNKbECMuLBLAYAyk5NfDUAAAA='
[System.IO.File]::WriteAllBytes($typeFile, [System.Convert]::FromBase64String($layoutOne))
Write-Output "wrote a layout-1 type file to $typeFile"
exit 0
