$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildRoot = Join-Path ([IO.Path]::GetTempPath()) ('ps1-verify-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
$sharedSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'shared/src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })

# Each variant imports the same shared classes. Include them when compiling.
$variantNames = @('variant1-range-immediate', 'variant2-range-buffered', 'variant3-divisor-immediate', 'variant4-divisor-buffered')
$classPaths = @()
foreach ($variantName in $variantNames) {
    $outputRoot = Join-Path $buildRoot $variantName
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot "$variantName/src") -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac -d $outputRoot @sharedSources @sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $variantName" }
    $classPaths += $outputRoot
}

$testRoot = Join-Path $buildRoot 'tests'
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac -d $testRoot @sharedSources @testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
$testClassPath = $testRoot
foreach ($testName in @('SearchConfigTest', 'SearchDivisionSchemeTest', 'DivisorDivisionSchemeTest', 'UInt64BoundaryTest')) {
    & java -cp $testClassPath "ps1.shared.$testName"
    if ($LASTEXITCODE -ne 0) { throw "Test failed: $testName" }
}
& java -cp $testClassPath ps1.shared.VariantEntryPointTest @classPaths
if ($LASTEXITCODE -ne 0) { throw 'Variant entry point checks failed.' }
$auditEvidence = Join-Path $projectRoot 'reports/validation-audit.csv'
& java -cp $testClassPath ps1.shared.ValidationAuditTest $auditEvidence @classPaths
if ($LASTEXITCODE -ne 0) { throw 'Validation audit failed.' }
Write-Output "Build output: $buildRoot"
