# Confirmed 3D map request — 2026-11-13

The owner confirmed this is the desired reference:
https://www.reddit.com/r/Morrowind/comments/1xszu98/modern_morrow_a_controllerfirst_user_interface/

Upstream: https://github.com/DurhamG/modern-morrow
Published v0.1.0 assets checked through GitHub API: Linux x86_64 AppImage, Windows x64 installer/ZIP, Linux update script. No Android or ARM build is published. `ui-rework/android.md` is a port plan, not a working APK.

The 3D map is a live native render-to-texture camera (`apps/openmw/mwrender/map3d.cpp` and `.hpp`, approximately 27 KB together), not a map texture or a Lua mod. It includes perspective view projection, terrain loading, an explored-area fog pass and an interior ceiling cutaway. The map screen must use its world-to-picture and picture-to-ground transforms for markers and panning.

To add only this feature to the existing RGDS interface, native work is needed in the pinned OpenMW b6b04ca2284f56b9c8b15115d90240a35de8035c: renderer integration, GL4ES shader compatibility, a native 2D/3D toggle and map-control/marker handling, and texture delivery to the companion panel. The feature must stop drawing when hidden and retain the existing 2D view.

This machine currently builds the Android UI around a pinned prebuilt v1.4.6 engine APK. Its SDK has no NDK/CMake packages, and WSL has no native CMake or built engine dependency tree available. Adding the 3D renderer requires provisioning and validating that native build first. No map code or new renderer has been installed on the handheld.

Source snapshots for investigation: D:/CodexDownloads/modern-morrow-map-source/. Existing OpenMW custom worktrees are untouched.
