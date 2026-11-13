# RG DS / Pocket Classic handoff for Claude

Updated 2026-11-13. RG DS integration is installed and checked; devices released to the separate QoL session. No merges or pushes performed.

## Completion plan and result
1. Review Claude's prototype and preserve original work: done. Original D:/RGDS/OpenMW-DS-map3d (claude/rgds-map3d-perf) untouched. Review report: C:/Users/SunshineDaydream/Documents/RGDS-update-20261113/claude-review/review.md.
2. Package the real native 3D renderer with the Android UI: done. Initial available perf1 APK had the stock engine without map hooks. This integration replaces exactly lib/arm64-v8a/libopenmw.so while pinning the original base APK and preserving other native libraries/engine assets.
3. Correct marker elevations, handle interiors and update bundled Lua safely: done. Exterior-only 3D with flat interior fallback; optional door worldZ exported, parsed and used for drawing/hit/popup projection. Lua updates independently of the engine-resource stamp, including missing-file recovery.
4. Build, install, test controller/map behavior and preserve saves/config: done with limits below.
5. Establish Classic wireless access and prepare Modern Morrow UI implementation boundaries: done. Full Classic companion port and Modern Morrow inventory/journal redesign are not implemented by this integration.
6. Commit source, retain rollback and hand off: final source commit is the HEAD of codex/rgds-map3d-handoff; see git log. Root worktree contains this mirrored handoff.

## Locations and ownership
- Integration source: D:/RGDS/OpenMW-DS-map3d-handoff, branch codex/rgds-map3d-handoff; prototype snapshot commit 8cf156f, based on ammo-hold fix a691d15.
- Native original: WSL rgds-build /build/openmw, HEAD 3b8b6c813287d84d54177f7dec90bf0dde0f1af3, untouched by this follow-up. Focus-ray performance experiment was reverted.
- Native patch checked/applied in isolated /build/codex-map3d-patchcheck; commit 46445f6a9086036a4625a4020cd10223b89c4a37 exactly reproduces reviewed tree.
- Canonical handoff: C:/Users/SunshineDaydream/Documents/RGDS-update-20261113/CLAUDE-HANDOFF-map3d.md; mirror: integration root CLAUDE-HANDOFF.md.
- Device evidence: D:/rgds-native/codex/device-checks/. Build logs: D:/rgds-native/logs/codex-map3d-integration-build.log and codex-map3d-final-build.log.
- Separate QoL chat 01a1d70c-65b9-7340-ac49-930375d1753f now owns external mods/content lines on both devices. It was explicitly notified after preservation checks. Release marker: D:/CodexDownloads/morrowind-qol-20261113/DEVICE-RELEASE.txt. Subsequent QoL changes must use that chat's new baseline.

## Installed artifact and rollback
- RG DS serial e4b485770907274c; package org.openmw.ds.rgds.
- Installed version: 8cf156f-rgds-map3d-integration1. Version records build baseline; final source commit includes the integration changes after that snapshot.
- Final APK retained at D:/rgds-native/apk/OpenMW-DS-RGDS-map3d-integration1.apk; build output D:/RGDS/OpenMW-DS-map3d-handoff/app/build/outputs/apk/debug/app-debug.apk.
- Final APK SHA256: 2ff1caeaa22f42c2c232954b56519633f20c45dd8befcc619c06260247c060cb. Device base.apk hash matches. Earlier first integration hash 95b6... is superseded.
- Reviewed debug-stripped native input: D:/rgds-native/codex/libopenmw-map3d.so, SHA256 75ed283ff7dc706a319bb0e581bbcb37dc37c4d38969b6e582c4627d30c64228.
- Fresh pre-install backup: D:/rgds-native/codex/backup-before-integration/. installed-perf1.apk SHA256 160044af2dc5b14825b8809e565805ef63459f3e8b30b8a2a4408610bb88cc9d; config-saves-companion.tgz has 130 entries including 92 saves, generated companion and private preferences. Individual input/settings/openmw copies also retained.
- Exact build/install/rollback commands: docs/BUILD-AND-ROLLBACK.md. Roll back APK first if required; do not unpack old saves wholesale over live data.

## Verification completed
- Cold Gradle build passed in 21m45s. Reviewer recovery fix rebuilt in 2m50s; both runs passed all 18 tests (9 controller +9 map), zero failures/errors.
- Artifact verifier passed on final APK: exact native library and Android map hooks, door-height Lua export,18 unchanged other native libraries and unchanged original engine assets. Wrong checksum/stock-engine negative checks reject, also under python -O.
- Native DT_NEEDED and original JNI exports unchanged; dependency/symbol check found no missing imports against pinned APK/system. Native patch reconstruction matches original built source tree.
- LuaJIT2.1 compiles the complete modified Lua; real exportDoorMarkers fragment emits expected x/y/z/name.
- Initial integration APK device tests used confirmed RGDSTest level1, not Chooney: real outdoor geometry/player arrow/doors in Balmora; 2D/3D toggle, zoom, movement/drag, six repeated toggles and tab roundtrip worked.
- Balmora Guild of Mages automatically falls back to 2D; door label tap works; return outdoors resumes 3D preference. Other interiors are guarded by the same exterior flag but were not exhaustively visited.
- Ammo: hold WEAPON opens panel, hold slot opens choices with focus retained on game display2. R2 full digital+axis injection draws/fires bow; iron1->0; picker reopens with None left. Selecting steel on lower screen then R2 fires3->2 without another game-screen tap. First injected controller event after keyboard setup was not a shot; subsequent gamepad-mode shots and switch/fire passed.
- Final APK (same map/control feature code plus missing-script recovery fix) installed, checksum confirmed, engine reached title, deliberately missing shared Lua recovered with correct hash. Recovery temporary copy removed after cmp equality. All 18 tests rerun; the entire gameplay matrix was not repeated for this one-line fast-path fix.
- All 92 pre-install .omwsave hashes unchanged, including archived versions. input_v3.xml, settings.cfg, openmw.cfg and shaders.yaml byte-identical to fresh backup. Reports: save-comparison.json and config-comparison.json.
- Original map zoom restored to-2, 2D selected by default. User can tap cube on HUD right to toggle3D outdoors. Original sensitivity/face-look/dpad settings retained; existing perf-log flag retained (it predated this work). No game loaded; launcher left open at device release.

## Performance and remaining checks
- Same stationary scene, seven windows per mode:2D mean25.0fps/work39.2ms,3D mean21.2fps/work48.2ms. Raw perf-phases.json, perf-logcat.txt and perf-summary.json retained. This is exploratory, not a controlled moving-camera benchmark or speed improvement.3D has measurable cost and remains optional.
- Captured logcat had no fatal Java/JNI linkage/segfault or GL_INVALID/OUT_OF_MEMORY signatures. Single observed PSS ~1.87GB; long-session memory growth not tested.
- Not completed: long-session/lifecycle stress; elevated exterior door alignment across varied geometry; moving-scene controlled performance; indoor3D ceiling/floor cutaway. Interior2D fallback remains intentional.
- Do not reintroduce the focus-ray throttle or claim a speed gain from menu/paused samples. Preserve R2 Use/control10 axis5, R1 behavior, two ammo favourites and non-focusable popup.

## Modern Morrow / Pocket Classic
- Owner reference: https://www.reddit.com/r/Morrowind/comments/1xszu98/modern_morrow_a_controllerfirst_user_interface/ and https://github.com/DurhamG/modern-morrow.
- docs/MODERN-UI-ADAPTATION.md gives prioritized inventory/details, journal and dialogue work using existing exports/actions; no save-format changes or guessed metadata.
- docs/POCKET-CLASSIC-UI-NEXT.md identifies existing research artifacts and why single-screen companion UI requires a separate host/bridge port, not a settings toggle on installed com.ast.openmw. The historical saved-game-confirmation note is not a new permission requirement; preserve active play and coordinate with QoL session.
- Classic existing wireless ADB verified at 192.168.12.233:5555, model Retroid_Pocket_Classic/parrot, Android 14, com.ast.openmw 0.51.0-12. docs/POCKET-CLASSIC-WIRELESS.md has tested reconnect and USB fallback. IP/listener persistence across reboot untested. No RPC app/config/save changes made here.
- Smallest next feature is an isolated single-screen Inventory/Details companion host with bridge and explicit Back/control release; separate package/profile/rollback, leaving stock installation and saves intact. Full UI redesign is future implementation, not an installed result.

## Delegation and continuation
Owner explicitly requested lower-cost orchestration. GPT-6 Luna handled independent integration review (found/fixed missing-Lua recovery), build/rollback and wireless audit, and Classic UI architecture assessment. Root handled native packaging, Android build/install, device testing, preservation and integration. No agent touched Quest, merged, pushed or modified Claude's original prototype.
Use native/README.md and tools/map3d-engine.json for provenance; tools/build-map3d.ps1 for pinned-input rebuild. New port/UI work should use another isolated worktree and coordinate device ownership before changing the QoL session's configuration.
