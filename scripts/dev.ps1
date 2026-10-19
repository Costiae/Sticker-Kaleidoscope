[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$JdkHome,
    [switch]$DirectNetwork,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $JdkHome) { $JdkHome = $env:STICKER_JAVA_HOME }
if (-not $JdkHome) { $JdkHome = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) {
    throw '找不到 JDK。请通过 -JdkHome 或 STICKER_JAVA_HOME 指定 JDK 21。'
}

$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    if (-not $GradleArguments) { $GradleArguments = @(':composeApp:run') }
    $networkArguments = @()
    if ($DirectNetwork) { $networkArguments = @('-Dhttp.proxyHost=', '-Dhttps.proxyHost=') }
    Push-Location $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') "-Porg.gradle.java.installations.paths=$JdkHome" @networkArguments @GradleArguments
        $buildExitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJavaHome }
exit $buildExitCode
