# Ammo hold picker repair

Hold the HUD WEAPON button to open the two ammo favourites. Tap an empty slot, or hold either slot, to choose carried arrows or bolts. Tap an assigned slot to equip it. Assignments stay per character.

Previously the popup was gated by `state.ammo != null`. That payload exists only for an equipped bow/crossbow with compatible ammunition already loaded. This prevented opening the picker with an empty or mismatched ammo slot, and closed it after the last arrow was fired. The picker now remains available regardless of loaded ammunition, including before equipping a bow. The touch-only ammo dropdown is also non-focusable: choosing a favourite previously moved Android controller focus to the companion display, so R2 stopped reaching the engine until the top screen was tapped again. Native engine and Lua payload contracts are unchanged.

Based on the existing uncommitted HUD1 source, preserved in commit 3e6c7d4 in this worktree. Original worktrees were not changed. Build uses the same pinned SergeShack v1.4.6 native libraries.

Validation completed 2026-11-13:
- `:app:assembleDebug :app:testDebugUnitTest` passed; 9 controller tests, 0 failures/errors. Final incremental build: 2m 29s (cold build: 19m 26s).
- All 19 native libraries and the companion Lua script match the previous installed APK byte for byte.
- Installed on the USB-connected RG DS; version `3e6c7d4-rgds-ammo-hold1`.
- Reproduced the old bug with an equipped bow and no ammo: holding WEAPON incorrectly said to equip a bow.
- On RGDSTest, verified empty picker, no-ammo message, long-press choices, two persisted assignments, tap-to-equip, and switching between iron and steel arrows.
- The new dropdown keeps `FocusedDisplayId: 2` while open and after selecting. A full R2 press/release (BTN_TR2 and ABS_GAS) fired selected steel arrows from 3 to 2 to 1 to 0 without touching the top screen again.
- The picker remained open at zero ammo and could be closed and reopened with both stacks exhausted.
- All 91 original Morrowind saves remain byte-identical; R2's shared XML binding is retained. Tests used RGDSTest.
- Evidence and rollback APK/config archives: `C:/Users/SunshineDaydream/Documents/RGDS-update-20261113/`.
- No native 3D-map implementation was installed; see `MODERN-MORROW-MAP-NOTES.md`.
