$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $projectRoot 'shared/src/ps1/shared'
$variantNames = @('variant1-range-immediate', 'variant2-range-buffered', 'variant3-divisor-immediate', 'variant4-divisor-buffered')

# Shared source is the edit point. Refresh copies before building or packaging.
for ($index = 0; $index -lt $variantNames.Count; $index++) {
    $variantName = $variantNames[$index]
    $number = $index + 1
    $targetRoot = Join-Path $projectRoot "$variantName/src/ps1/variant$number"
    New-Item -ItemType Directory -Path $targetRoot -Force | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java') {
        $source = Get-Content -LiteralPath $file.FullName -Raw
        $source = $source.Replace('package ps1.shared;', "package ps1.variant$number;")
        Set-Content -LiteralPath (Join-Path $targetRoot $file.Name) -Value $source
    }
}
