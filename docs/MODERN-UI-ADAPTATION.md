# Modern Morrow UI adaptation: RG DS and Retroid Pocket Classic

Owner-confirmed reference: https://github.com/DurhamG/modern-morrow and the Reddit post linked in the handoff.

## First deliverable: inventory and equipment
- Use the existing RGDS companion inventory/stack/equipment exports, actions and favourites. Keep the same equip/unequip/use behavior, including separate ammunition slots and per-character favourites.
- Offer a readable list with name, quantity, weight and condition; a selected row has persistent details and comparisons instead of hover-only tooltips. Keep a grid option for users who prefer icons.
- Compare the selected item against the actual occupied equipment slot. Rings need hand choice; arrows/bolts need an ammo-slot comparison, not a weapon replacement comparison. Do not guess stat fields absent from the engine payload.
- RG DS: list/details live on the bottom display while the world stays above. Keep all existing HUD gestures, R2 attack and the non-focusable ammo menu.
- Pocket Classic: a full-screen, paged menu with large text and D-pad focus. Details can be a separate page if side-by-side panels are too small. Return to gameplay with the same controls and camera aim state released.

## Second deliverable: journal and dialogue
- Reuse current quest/journal exports and completed/hidden/followed state. Add clear Active/Finished grouping, recent ordering and place filters only where the place is known from real data.
- Modern Morrow records extra journal metadata in its fork. Do not import its save-format changes merely to copy its UI; old quests must remain usable without guessed giver/location data.
- Dialogue should show readable conversation text and selectable topics/services, preserving new-topic and disposition/crime semantics. RG DS can use both screens; Classic uses one full-screen conversation view.

## Controller and touch acceptance checks
- Test D-pad focus, confirm/back, category changes, long holds, scrolling and touch selection on each physical device. Do not copy Steam Deck stick-click/right-stick shortcuts to the Classic unchanged.
- Opening and closing any menu must release synthetic movement/attack, retain the selected ammo and return controller focus to the game.
- On RG DS, popups intended only for touch must not steal the focused display. Explicitly retest R2 after selecting favourites or ammo.
- On Classic, keep the existing separate com.ast.openmw installation and profile until an isolated prototype has proven data paths, save loading, touch aim and physical buttons. The older RPC setup plan in D:/CodexDownloads/rpc-openmw-touch/PLAN.json is research, not proof of an installed touch fix.

## Map and performance boundaries
- Current integration enables the native 3D camera only outdoors; interiors deliberately keep 2D until ceiling/floor cutaway is implemented and verified.
- Begin new UI work independently of a 3D-map speed claim. Measure the map off/on with a fixed saved test scene, camera, time/weather, warm-up and foreground state on both devices.
- A feature is ready only with before/after screenshots, menu/controller tests, save checksums, and a usable rollback APK/config backup.

## Suggested order
1. Finish current map packaging and device checks from the main handoff.
2. Build an inventory/details prototype using existing exports, review it at real physical sizes on both devices.
3. Add journal improvements and then dialogue/services.
4. Add indoor 3D cutaway after the native scene/clip behavior is understood; keep the 2D fallback available.
