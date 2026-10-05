param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][uri]$ProxyUri,
    [string[]]$Tasks = @('build'),
    [string]$GradleUserHome,
    [string[]]$AdditionalJavaHomes = @()
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$moduleRoot = Join-Path $repoRoot 'mods/1.7.10/zstdnet-forge'
$jdkRoot = (Resolve-Path -LiteralPath $JavaHome).Path
if ($ProxyUri.Scheme -ne 'http' -or $ProxyUri.UserInfo -or -not $ProxyUri.IsAbsoluteUri) {
    throw 'Supply an explicit HTTP CONNECT proxy URI without credentials; direct fallback is not supported.'
}
if (-not (Test-Path -LiteralPath (Join-Path $jdkRoot 'bin/java.exe'))) {
    throw 'JavaHome must identify a JDK installation.'
}
if (-not $GradleUserHome) { $GradleUserHome = Join-Path $repoRoot '.work/1.7.10/gradle-home' }
$savedEnvironment = @{}
foreach ($key in @('JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'GRADLE_USER_HOME', 'HTTP_PROXY', 'HTTPS_PROXY', 'NO_PROXY')) {
    $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
    $env:JAVA_HOME = $jdkRoot
    $env:GRADLE_USER_HOME = $GradleUserHome
    $env:HTTP_PROXY = $ProxyUri.AbsoluteUri
    $env:HTTPS_PROXY = $ProxyUri.AbsoluteUri
    $env:NO_PROXY = 'localhost,127.0.0.1,::1'
    # JAVA_TOOL_OPTIONS reaches the wrapper bootstrap, Gradle daemon, toolchain
    # provisioning and child Java asset downloaders. Only gameplay loopback bypasses it.
    $proxyOptions = "-Dhttp.proxyHost=$($ProxyUri.Host) -Dhttp.proxyPort=$($ProxyUri.Port) -Dhttps.proxyHost=$($ProxyUri.Host) -Dhttps.proxyPort=$($ProxyUri.Port) -Dhttp.nonProxyHosts=localhost|127.*|[::1]"
    $env:JAVA_TOOL_OPTIONS = "$($savedEnvironment['JAVA_TOOL_OPTIONS']) $proxyOptions".Trim()
    $toolchainPaths = (@($jdkRoot) + $AdditionalJavaHomes) -join ','
    & (Join-Path $moduleRoot 'gradlew.bat') -p $moduleRoot @Tasks --no-daemon --console=plain `
        "-Dorg.gradle.java.installations.paths=$toolchainPaths"
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    foreach ($key in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process')
    }
}
