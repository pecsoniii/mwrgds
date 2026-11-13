# Build and rollback runbook

Updated: 2026-11-13. Retained-input Android build only; this does not rebuild the native engine source.

## Retained inputs and versions

The checked build used the integration worktree `D:\RGDS\OpenMW-DS-map3d-handoff`, the base APK `D:\CodexDownloads\rgds-openmw-performance\openmw-ds-duolite-1.4.6.apk`, native library `D:\rgds-native\codex\libopenmw-map3d.so`, Lua test dependencies `D:\CodexDownloads\rgds-openmw-performance\test-deps`, and JDK `C:\Users\SunshineDaydream\Documents\RGDS-build-tools\jdk\jdk-21.0.12.1+1`. The Gradle wrapper pins Gradle 9.5.1; the version catalog pins AGP 9.2.1 and Kotlin 2.4.0. The app build config uses compile SDK 37 and build tools 36.0.0. The native engine was built with the pinned NDK r29; its source provenance and patch instructions are in [native/README.md](../native/README.md).

`tools/map3d-engine.json` pins the base APK SHA-256 and debug-stripped native library SHA-256. Do not substitute files with different hashes. The build script also fixes Java temporary directories to `D:\rgds-tmp`.

## Build and verify

From PowerShell, run the retained-input build and unit tests:

```powershell
Set-Location 'D:\RGDS\OpenMW-DS-map3d-handoff'
& .\tools\build-map3d.ps1 `
  -BaseApk 'D:\CodexDownloads\rgds-openmw-performance\openmw-ds-duolite-1.4.6.apk' `
  -NativeLibrary 'D:\rgds-native\codex\libopenmw-map3d.so' `
  -LupaPath 'D:\CodexDownloads\rgds-openmw-performance\test-deps' `
  -Jdk 'C:\Users\SunshineDaydream\Documents\RGDS-build-tools\jdk\jdk-21.0.12.1+1'
```

The script checks pinned input hashes and Lua export, runs `:app:assembleDebug` and `:app:testDebugUnitTest`, then verifies the final APK's native/UI bridge and unchanged companion libraries/assets. The completed integration build succeeded in 21m45s with 18 tests passing; the final artifact verifier passed. The APK output is `D:\RGDS\OpenMW-DS-map3d-handoff\app\build\outputs\apk\debug\app-debug.apk`. The final recovery-fix rebuild passed in 2m50s with the same 18 passing tests. Final SHA-256 is 2ff1caeaa22f42c2c232954b56519633f20c45dd8befcc619c06260247c060cb; a retained copy is D:/rgds-native/apk/OpenMW-DS-RGDS-map3d-integration1.apk.

For a standalone final-artifact check after the build:

```powershell
Set-Location 'D:\RGDS\OpenMW-DS-map3d-handoff'
python tools/verify_map3d_artifact.py `
  --base 'D:\CodexDownloads\rgds-openmw-performance\openmw-ds-duolite-1.4.6.apk' `
  --native 'D:\rgds-native\codex\libopenmw-map3d.so' `
  --apk 'D:\RGDS\OpenMW-DS-map3d-handoff\app\build\outputs\apk\debug\app-debug.apk'
```

## Install and rollback APK

The application ID in prebuilt-engine mode is `org.openmw.ds.rgds`. Always target the RG DS serial e4b485770907274c (the Classic may also be connected). With the device connected and authorized, install the verified integration APK with:

```powershell
adb -s e4b485770907274c install -r 'D:\RGDS\OpenMW-DS-map3d-handoff\app\build\outputs\apk\debug\app-debug.apk'
```

To roll back the application binary to the backed-up perf1 build:

```powershell
adb -s e4b485770907274c install -r 'D:\rgds-native\codex\backup-before-integration\installed-perf1.apk'
```

The fresh pre-install backup is `D:\rgds-native\codex\backup-before-integration\`. It contains `installed-perf1.apk`, `config-saves-companion.tgz`, and individually captured `input_v3.xml`, `openmw.cfg`, and `settings.cfg`. The tar archive was checked and contains 130 entries, including 92 saves.

APK installation does not restore configuration or saves. Restore a config file only if troubleshooting shows it is needed, first copy the current device file aside, then extract/copy only the chosen config entry from the backup. Do not unpack the entire tar over the live data directory. Before restoring any save, compare its checksum with the pre-install backup and the current device copy; preserve newer saves and never overwrite them automatically. Keep the backup archive intact. Reinstalling the old APK is the first rollback step; use the captured configs only to address a demonstrated config regression.

If startup fails, JNI linkage fails, or the map repeatedly crashes, stop testing and reinstall the retained perf1 APK. Keep the current logs and preserve current saves before any configuration restoration.
