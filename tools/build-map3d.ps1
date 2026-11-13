param(
    [string]$BaseApk = 'D:\CodexDownloads\rgds-openmw-performance\openmw-ds-duolite-1.4.6.apk',
    [string]$NativeLibrary = 'D:\rgds-native\codex\libopenmw-map3d.so',
    [string]$LupaPath = 'D:\CodexDownloads\rgds-openmw-performance\test-deps',
    [string]$Jdk = 'C:\Users\SunshineDaydream\Documents\RGDS-build-tools\jdk\jdk-21.0.12.1+1'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$pin = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'map3d-engine.json') | ConvertFrom-Json
$nativeHash = $pin.nativeSha256
Push-Location $repo
try {
    python tools/verify_map3d_artifact.py --base $BaseApk --native $NativeLibrary
    if ($LASTEXITCODE -ne 0) { throw 'Input artifact verification failed' }
    python tools/check_companion_export.py --lupa-path $LupaPath
    if ($LASTEXITCODE -ne 0) { throw 'Companion Lua verification failed' }
    $env:JAVA_HOME = $Jdk
    $env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir=D:\rgds-tmp -Djdk.net.unixdomain.tmpdir=D:\rgds-tmp'
    & .\gradlew.bat --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=1024m -XX:ActiveProcessorCount=2' '-Pkotlin.compiler.execution.strategy=in-process' "-PprebuiltEngineApk=$BaseApk" "-Pmap3dNativeLibrary=$NativeLibrary" "-Pmap3dNativeSha256=$nativeHash" :app:assembleDebug :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw 'Android build or unit tests failed' }
    python tools/verify_map3d_artifact.py --base $BaseApk --native $NativeLibrary --apk app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Final APK verification failed' }
} finally { Pop-Location }
