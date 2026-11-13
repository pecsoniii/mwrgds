# Native map source provenance

The integration APK uses the reviewed engine in `tools/map3d-engine.json`. This patch preserves Claude's native map implementation; Codex has not changed that renderer.

- OpenMW base version: b6b04ca2284f56b9c8b15115d90240a35de8035c.
- Apply the complete OpenMW-DS 1.4.6 patch set and androidmain.cpp first, ending at local source commit 259a2560e8567b8f371c870faa3f31030f08aa73.
- Apply `companion-map3d.patch` with `git apply --check` followed by `git apply` in that engine source tree. It adds the renderer, JNI bridge, source list and rendering-root accessor.
- Resulting reviewed native source is commit 3b8b6c813287d84d54177f7dec90bf0dde0f1af3. The perf focus-ray experiment is absent (it was reverted).
- Existing dependency/build recipe is in WSL rgds-build: `/build/src`, with `/build/out` as its output. `D:/rgds-native/scripts/build-engine.sh` documents the pinned NDK r29 dependency build. Do not modify Claude's `/build/openmw` while another session is using it.
- Rebuild in an isolated native source/build tree, then check dynamic dependencies, JNI exports and unresolved symbols against the original APK using `D:/rgds-native/scripts/check-lib.sh`.
- The current binary is debug-stripped using NDK llvm-objcopy `--strip-debug`; this does not change its needed libraries or exported JNI entry points. If rebuilt, re-review the binary and update the manifest hashes deliberately.

The Android app is built by `tools/build-map3d.ps1`, with the pinned original APK plus exactly one native-library replacement. That script verifies both inputs and the final APK.

RG DS rendering, indoor fallback, map controls, ammo/R2 and a short timing/log check passed; full results and remaining lifecycle/memory/performance limits are in CLAUDE-HANDOFF.md. Interior rendering intentionally uses the 2D fallback in the app until ceiling/floor cutaway is implemented and tested.

## Patch reconstruction check used
From an isolated worktree at 259a2560e8567b8f371c870faa3f31030f08aa73, verify the patch SHA256 equals 32ea748c3794c20a1bd6a40657469f8d67e006ceac2f2105c4092d23feda8aae (also in tools/map3d-engine.json), then run git apply --check and git apply on native/companion-map3d.patch. The checked worktree /build/codex-map3d-patchcheck was committed as 46445f6a9086036a4625a4020cd10223b89c4a37; git diff 3b8b6c813287d84d54177f7dec90bf0dde0f1af3 HEAD --exit-code was empty. Original /build/openmw was untouched.

Read-only verification of the retained reconstruction (WSL rgds-build):
```sh
cd /build/codex-map3d-patchcheck
git diff 3b8b6c813287d84d54177f7dec90bf0dde0f1af3 HEAD --exit-code
sha256sum /mnt/d/RGDS/OpenMW-DS-map3d-handoff/native/companion-map3d.patch
```
