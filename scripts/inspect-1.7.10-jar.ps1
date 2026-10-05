param([Parameter(Mandatory = $true)][string]$Jar)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifact = (Resolve-Path -LiteralPath $Jar).Path
$archive = [IO.Compression.ZipFile]::OpenRead($artifact)
function Require-Entry([string]$Name) {
    if (-not $archive.GetEntry($Name)) { throw "Missing production entry: $Name" }
}
function Get-ClassMajor([string]$Name) {
    Require-Entry $Name
    $stream = $archive.GetEntry($Name).Open()
    try {
        $header = [byte[]]::new(8)
        $offset = 0
        while ($offset -lt 8) {
            $count = $stream.Read($header, $offset, 8 - $offset)
            if ($count -le 0) { throw "Truncated class: $Name" }
            $offset += $count
        }
        return [int]$header[6] * 256 + [int]$header[7]
    } finally { $stream.Dispose() }
}
try {
    foreach ($entry in @(
        'com/github/luben/zstd/util/Native.class',
        'win/amd64/libzstd-jni-1.5.7-7.dll',
        'cn/tohsaka/factory/zstdnet/internal/gson/Gson.class',
        'cn/tohsaka/factory/zstdnet/internal/slf4j/LoggerFactory.class',
        'META-INF/services/cn.tohsaka.factory.zstdnet.internal.slf4j.spi.SLF4JServiceProvider',
        'cn/tohsaka/factory/zstdnet/core/stats/TrafficStatisticsService.class',
        'assets/zstdnet/dashboard/report-template.html',
        'assets/zstdnet/lang/en_US.lang', 'assets/zstdnet/lang/zh_CN.lang',
        'mixins.zstdnet.refmap.json', 'mixins.zstdnet.json', 'mcmod.info', 'LICENSE',
        'META-INF/THIRD-PARTY-NOTICES.txt', 'META-INF/licenses/zstd-jni.txt',
        'META-INF/licenses/zstandard.txt', 'META-INF/licenses/gson.txt', 'META-INF/licenses/slf4j.txt'
    )) { Require-Entry $entry }
    $mainMajor = Get-ClassMajor 'cn/tohsaka/factory/zstdnet/Zstdnet.class'
    $mixinMajor = Get-ClassMajor 'cn/tohsaka/factory/zstdnet/mixin/DedicatedServerMixin.class'
    if ($mainMajor -ne 69 -or $mixinMajor -ne 52) { throw 'Unexpected main/mixin classfile target' }
    if ($archive.Entries.FullName -match '^cn/tohsaka/factory/zstdnet/acceptance/') { throw 'Acceptance fixture leaked into product JAR' }
    $reader = [IO.StreamReader]::new($archive.GetEntry('META-INF/MANIFEST.MF').Open())
    try { $manifest = $reader.ReadToEnd() -replace '\r?\n ', '' } finally { $reader.Dispose() }
    if ($manifest -notmatch 'FMLCorePlugin: cn.tohsaka.factory.zstdnet.coremod.ZstdLoadingPlugin') { throw 'Missing early coremod registration' }
    if ($manifest -match '(?m)^(TweakClass|MixinConfigs):') { throw 'Conflicting production mixin manifest registration' }
    [ordered]@{
        jar = $artifact
        sha256 = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = (Get-Item -LiteralPath $artifact).Length
        mainClassMajor = $mainMajor
        mixinClassMajor = $mixinMajor
        nativeLibraries = @($archive.Entries | Where-Object FullName -Match '\.(dll|so|dylib)$' | ForEach-Object FullName)
        jniPackagePreserved = $true
        privateGsonAndSlf4j = $true
        earlyMixinRegistration = $true
        acceptanceFixtureExcluded = $true
    } | ConvertTo-Json -Depth 4
} finally { $archive.Dispose() }
