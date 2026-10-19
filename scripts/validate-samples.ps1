[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$SourceDirectory = (Join-Path ([Environment]::GetFolderPath([Environment+SpecialFolder]::MyPictures)) 'piggy'),
    [switch]$DirectNetwork
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw 'Sample source must be a directory.' }
$output = Join-Path $projectRoot ('artifacts\sample-validation-' + [guid]::NewGuid().ToString('N'))
$previousSource = $env:STICKER_SAMPLE_DIR
$previousOutput = $env:STICKER_SAMPLE_OUTPUT
Push-Location $projectRoot
try {
    $env:STICKER_SAMPLE_DIR = $source
    $env:STICKER_SAMPLE_OUTPUT = $output
    & (Join-Path $PSScriptRoot 'dev.ps1') -DirectNetwork:$DirectNetwork :composeApp:desktopTest --tests '*RealLibrarySampleTest*' --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Sample validation failed; inspect $output and the Gradle test report." }
    Write-Output "Sample library: $output"
    Get-Content -LiteralPath (Join-Path $output 'validation.md')
} finally {
    Pop-Location
    $env:STICKER_SAMPLE_DIR = $previousSource
    $env:STICKER_SAMPLE_OUTPUT = $previousOutput
}
