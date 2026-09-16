$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$sourcePath = Join-Path $repoRoot 'src\main\java\com\gregtechceu\gtceu\common\pipelike\item\ItemPipeType.java'
$source = Get-Content -Raw $sourcePath

if ($source -cmatch 'values\(\)\s*\[\s*this\.ordinal\(\)\s*-\s*4\s*\]') {
    throw 'createPipeModel should use the cached VALUES array instead of calling enum values() in texture lambdas.'
}

if ($source -cnotmatch 'VALUES\s*\[\s*ordinal\(\)\s*-\s*4\s*\]\s*:\s*this') {
    throw 'Restrictive item pipe texture lookup should explicitly map through the cached VALUES array.'
}

Write-Host 'ItemPipeType cached values regression check passed.'
