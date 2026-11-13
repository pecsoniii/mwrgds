# Pocket Classic UI: smallest safe next step

## Assessment

The Pocket Classic plan at `D:/CodexDownloads/rpc-openmw-touch/PLAN.json` is still `awaiting_user_saved_game_confirmation_before_restart`. That is a historical status, not a fresh approval requirement: the owner now explicitly authorized continuing implementation. It records no save-file or app-settings changes. Its requested touch aim can use the existing `JoystickRight` relative-motion path and `MouseMode.Touch`; the plan marks this as not requiring an app replacement, but still calls for a live menu test. It also asks for Auto-walk, Inventory and Spells shortcuts, with the on-screen sneak toggle hidden.

The installed RPC app is `com.ast.openmw`. The existing companion code in this worktree is a separate app build (`app/build.gradle.kts` selects `org.openmw.ds.rgds` in prebuilt-engine mode). It cannot update the installed RPC package as-is.

## Reuse boundary

- The UI and gameplay logic are reusable in a source-based RPC build: `CompanionScreen` has Inventory and Spells tabs, and `AimBrassPanel` already provides an Auto button. `RgdsControls.toggleAutoRun()` owns the forward-walk toggle and release behavior.
- The current single-screen profile intentionally does not host that UI or initialize its bridge. `EngineActivity.startCompanionScreen()` returns for `DisplayRoles.singleScreen()` before installing the companion sink or starting companion collectors. The single-screen touch overlay retains only the keyboard entry point. So showing the Compose UI on RPC needs app/native integration and a data/action bridge, not just a launcher preference or button remap.
- The RPC artifacts are a backup APK, screenshots (`before.png`, `current.png`, `menu.png`, `paused.png`, and others), copied control/cursor source (`JoystickRight.kt`, `Joystick.kt`, `JoystickLeft.kt`, `MouseCursor.kt`, `SDLActivity.java`), launcher preferences, and a `ui.py` screenshot helper. `before-input_v3.xml` is zero bytes. There is no edited APK or valid input-layout backup in this directory. These are research artifacts, not evidence of a tested fix.
- The RPC plan notes a fixed native top-bar button set, no dynamic Add button, and unresolved spells-list shortcut semantics. The RPC source copy does not include the top-bar implementation, so a native shortcut-button change cannot be specified or verified from these files.

## Smallest implementation candidate

Build an **isolated RPC companion prototype** in the OpenMW-DS source, launched as a full-screen menu over the game from one explicit entry point. Start with the paged Inventory/Details view from `docs/MODERN-UI-ADAPTATION.md`; reuse `CompanionScreen`'s Inventory panel, current item exports/actions, and existing large-text/controller focus patterns. Add only the minimum single-screen host and bridge wiring needed to keep companion data flowing while this menu is open, and make Back close it and release synthetic controls. Do not add the whole HUD or change the fixed native top bar in this first slice.

This requires an isolated APK/source integration with the native companion bridge and a distinct package ID; it is a separate RPC app port, not a settings-only change to `com.ast.openmw`. Keep the installed package, profile, saves, and input settings untouched while the installed game is active; coordinate a saved-state window with the QoL session before replacing or restarting anything, and give the prototype its own rollback path. The existing touch-aim setup can be assessed separately after preserving current game state and running the live-menu check described in `PLAN.json`.

No device validation is claimed. No ADB commands or builds were run for this assessment.
