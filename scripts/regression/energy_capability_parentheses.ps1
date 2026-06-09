$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$src = Join-Path $root "src\main\java"
$hull = Join-Path $src "com\gregtechceu\gtceu\common\machine\electric\HullMachine.java"
$hatch = Join-Path $src "com\gregtechceu\gtceu\common\machine\multiblock\part\EnergyHatchPartMachine.java"

$hullText = Get-Content -Raw -LiteralPath $hull
$hatchText = Get-Content -Raw -LiteralPath $hatch

if ($hullText -notmatch "cap\s*==\s*GTCapability\.ENERGY_CONTAINER\s*&&\s*\(side\s*==\s*null\s*\|\|\s*side\s*!=\s*getFrontFacing\(\)\)") {
    throw "HullMachine energy capability side check must be parenthesized"
}
if ($hatchText -notmatch "cap\s*==\s*GTCapability\.ENERGY_CONTAINER\s*&&\s*\(side\s*==\s*null\s*\|\|\s*side\s*==\s*getFrontFacing\(\)\)") {
    throw "EnergyHatchPartMachine energy capability side check must be parenthesized"
}

$unsafe = rg -n "cap\s*==\s*GTCapability\.ENERGY_CONTAINER\s*&&\s*side\s*==\s*null\s*\|\|" $src
if ($LASTEXITCODE -eq 0) {
    throw "Unparenthesized energy capability condition found:`n$unsafe"
}
if ($LASTEXITCODE -ne 1) {
    throw "rg failed while checking capability conditions"
}

Write-Host "Energy capability parentheses regression passed."
