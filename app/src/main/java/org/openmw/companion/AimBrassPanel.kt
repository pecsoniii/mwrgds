package org.openmw.companion

import org.openmw.ui.theme.CompanionPalette

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * The HUD tab, "Dwemer brass" over the minimap. It merged the Field tab (the map, effects, cell name,
 * tracked quest, ammo count) into the Aim tab's controls on 2026-11-09. Drag the map to look, tap it
 * for a door's name or the world map. Weapon, Spell, Sneak and Run/Walk sit in an arc the left thumb
 * reaches from the D-pad; Use, Jump and a hold-to-charge Attack under the right thumb.
 * Laid out in dp for the RG DS bottom screen (640 x 480 at density 1, 414 above the tab bar).
 */

private val Iron = CompanionPalette.Iron
private val IronPlate = CompanionPalette.IronPlate
private val IronSolid = CompanionPalette.IronSolid
private val IronDeep = CompanionPalette.IronDeep
private val BrassEdge = CompanionPalette.BrassEdge
private val Brass = CompanionPalette.Brass
private val BrassBright = CompanionPalette.BrassBright
private val BrassFill = CompanionPalette.BrassFill
private val Verdigris = CompanionPalette.Verdigris
private val VerdigrisFill = CompanionPalette.VerdigrisFill
private val IronText = CompanionPalette.IronText
private val IronMuted = CompanionPalette.IronMuted

/** Item categories that are used up, so their favourites show how many are left. */
private val COUNTED_CATEGORIES = setOf("potion", "ingredient", "scroll", "ammo")

@Composable
internal fun AimBrassPanel(
    state: GameState, splashVisible: Boolean, onSelectTab: (Tab) -> Unit, modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) { onDispose { RgdsControls.release() } }
    val context = LocalContext.current
    remember(context) { AmmoFavourites.init(context); true }
    LaunchedEffect(state.character.name) { AmmoFavourites.useCharacter(state.character.name) }
    val sneakVisible by GameStateRepository.sneakVisible.collectAsState()
    val favs by FavouritesRepository.state.collectAsState()
    var touch by remember { mutableStateOf<Offset?>(null) }
    var touched by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var ammoOpen by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) { if (hint != null) { delay(2500); hint = null } }
    val ranged = state.ammo != null
    LaunchedEffect(ranged) { if (!ranged) ammoOpen = false }
    val font = MwDisplay

    Box(modifier.fillMaxSize().background(Iron)) {
        // The minimap fills the tab. A drag past the touch slop turns the view; a tap shows a door's
        // name or opens the world map, as the Field tab's map did.
        HudMapLayer(
            state, splashVisible, zoom = RgdsControls.mapZoom,
            onLook = { p -> if (p != null) touched = true; touch = p },
            modifier = Modifier.fillMaxSize()
        )
        if (!touched) {
            Text(
                "Drag the map to look", color = IronText.copy(alpha = 0.85f), fontSize = 14.sp, fontFamily = font,
                textAlign = TextAlign.Center, modifier = Modifier.offset(170.dp, 262.dp).width(300.dp)
            )
        }
        touch?.let { p ->
            Box(
                Modifier
                    .offset { IntOffset((p.x - 32.dp.toPx()).roundToInt(), (p.y - 32.dp.toPx()).roundToInt()) }
                    .size(64.dp)
                    .background(Verdigris.copy(alpha = 0.14f), CircleShape)
                    .border(2.dp, Verdigris, CircleShape)
            )
        }

        BrassVitals(state, Modifier.offset(10.dp, 8.dp).width(262.dp))
        // In a fight (with target health set to the bottom screen) the enemy's health, top centre.
        HudTargetHealth(state, Modifier.offset(120.dp, 104.dp).width(400.dp))

        // Up to two favourites from each side, the HUD's own assignments and taps.
        val shown = favs.left.take(2).map { FavSide.LEFT to it } + favs.right.take(2).map { FavSide.RIGHT to it }
        if (shown.isNotEmpty()) {
            Row(Modifier.offset(330.dp, 6.dp).width(244.dp)) {
                shown.forEach { (side, slot) ->
                    BrassFavourite(slot, side, state, onSelectTab, Modifier.weight(1f))
                }
            }
        }
        BrassRound(CogIcon, null, 584.dp, 8.dp, size = 46.dp, description = "HUD controls") { settingsOpen = true }
        // Minimap zoom, under the cog.
        BrassRound(
            PlusIcon, null, 587.dp, 80.dp, size = 40.dp, description = "Zoom the map in",
            dim = RgdsControls.mapZoomLevel >= RgdsControls.MAX_ZOOM_LEVEL
        ) { RgdsControls.zoomMap(1) }
        BrassRound(
            MinusIcon, null, 587.dp, 126.dp, size = 40.dp, description = "Zoom the map out",
            dim = RgdsControls.mapZoomLevel <= RgdsControls.MIN_ZOOM_LEVEL
        ) { RgdsControls.zoomMap(-1) }

        BrassRound(HourglassIcon, "WAIT", 18.dp, 132.dp, size = 56.dp) { RgdsControls.restOrWait() }
        // Weapon: tap readies or lowers it; with a bow or crossbow out it shows the ammo left, and a
        // long-press opens the two ammo favourites.
        BrassRound(
            SwordIcon, "WEAPON", 7.dp, 212.dp, active = RgdsControls.weaponReadied,
            chip = state.ammo?.let { "x${it.count}" },
            onLongClick = {
                if (ranged) ammoOpen = !ammoOpen else hint = "Equip a bow or crossbow to pick ammo"
            }
        ) { RgdsControls.tap(34) }
        BrassRound(SparkIcon, "SPELL", 72.dp, 234.dp, active = RgdsControls.spellReadied, accent = Verdigris) {
            if (state.selectedSpell.isNullOrBlank()) onSelectTab(Tab.MAGIC) else RgdsControls.tap(46)
        }
        BrassRound(SneakIcon, if (sneakVisible) "HIDDEN" else "SNEAK", 124.dp, 279.dp, active = RgdsControls.sneaking) {
            RgdsControls.tap(113)
        }
        BrassRound(GaitIcon, if (RgdsControls.alwaysRun) "RUN" else "WALK", 155.dp, 340.dp, active = RgdsControls.alwaysRun) {
            RgdsControls.tap(115)
        }
        // Bottom-left corner, under the arc: hold Forward to walk, tap Auto to walk on its own.
        BrassForward(14.dp, 300.dp)
        BrassRound(AutoRunIcon, "AUTO", 84.dp, 354.dp, size = 56.dp, active = RgdsControls.autoRunning) {
            RgdsControls.toggleAutoRun()
        }

        // Where you are, and the quest you follow, between WALK and JUMP.
        Column(
            Modifier.offset(222.dp, 336.dp).width(206.dp).height(74.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(IronDeep.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (state.hasData) state.cell.ifEmpty { "Exterior" } else "-",
                    color = IronText, fontSize = 14.sp, fontFamily = font, lineHeight = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                HudFollowedQuestLine(color = Brass)
            }
        }

        BrassRound(UseIcon, "USE", 558.dp, 212.dp, chip = if (RgdsControls.faceLook) "L1" else null) { RgdsControls.tap(62) }
        BrassRound(JumpIcon, "JUMP", 434.dp, 346.dp, chip = if (RgdsControls.faceLook) "L2" else null) { RgdsControls.tap(33) }
        BrassAttack(510.dp, 284.dp)

        hint?.let {
            Text(
                it, color = IronText, fontSize = 12.sp, fontFamily = font, textAlign = TextAlign.Center,
                modifier = Modifier.offset(120.dp, 160.dp).width(400.dp)
                    .background(IronDeep.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(6.dp)
            )
        }
        if (ammoOpen) AmmoStrip(state, onClose = { ammoOpen = false })
        if (settingsOpen) BrassSettings(onClose = { settingsOpen = false })
    }
}

/**
 * The two ammo favourites, under the favourites row. Tap a slot to equip that ammo (as the Inventory
 * tab equips it); long-press to choose or swap what it holds. A tap anywhere else closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmmoStrip(state: GameState, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } })
    val owned = state.inventory.filter { it.category == "ammo" }.distinctBy { it.id }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.offset(318.dp, 76.dp).width(262.dp)
            .background(IronPlate, shape).border(1.dp, BrassEdge, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Ammo  ·  tap to equip, hold to choose", color = IronMuted, fontSize = 11.sp, fontFamily = MwDisplay)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmmoFavourites.slots.forEachIndexed { index, id ->
                var picking by remember { mutableStateOf(false) }
                val item = id?.let { sid -> owned.find { it.id == sid } }
                val equipped = id != null && state.ammo?.id == id
                val icon = rememberFavouriteIcon(id?.let { FavSlot(it, item?.name ?: it, "ammo", FavKind.GEAR) }, state)
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (equipped) BrassFill else IronSolid)
                            .border(if (equipped) 2.dp else 1.dp, if (equipped) BrassBright else BrassEdge, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    if (item != null) CompanionActions.equipItem(item.stackId.ifEmpty { item.id })
                                    else picking = true
                                },
                                onLongClick = { picking = true }
                            )
                            .padding(6.dp)
                            .alpha(if (id != null && item == null) 0.45f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(IronDeep), contentAlignment = Alignment.Center) {
                            if (icon != null) Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(24.dp))
                            else if (id == null) Text("+", color = IronMuted, fontSize = 16.sp, fontFamily = MwDisplay)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item?.name?.ifBlank { id } ?: if (id == null) "Empty" else "None left",
                                color = if (equipped) BrassBright else IronText, fontSize = 11.sp, fontFamily = MwDisplay,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (item != null) "x${item.count}" else if (id == null) "hold to pick" else "x0",
                                color = IronMuted, fontSize = 10.sp, fontFamily = MwDisplay, maxLines = 1
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = picking, onDismissRequest = { picking = false },
                        modifier = Modifier.background(IronSolid)
                    ) {
                        if (owned.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No arrows or bolts carried", color = IronMuted, fontFamily = MwDisplay, fontSize = 12.sp) },
                                onClick = { picking = false }
                            )
                        }
                        owned.forEach { ammo ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${ammo.name.ifBlank { ammo.id }}  x${ammo.count}", color = IronText,
                                        fontFamily = MwDisplay, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = { AmmoFavourites.assign(index, ammo.id); picking = false }
                            )
                        }
                        if (id != null) {
                            DropdownMenuItem(
                                text = { Text("Clear this slot", color = IronMuted, fontFamily = MwDisplay, fontSize = 12.sp) },
                                onClick = { AmmoFavourites.clear(index); picking = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrassVitals(state: GameState, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.background(IronPlate.copy(alpha = 0.94f), shape).border(1.dp, BrassEdge, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BrassVital("Health", state.health, Color(0xFFD0564A), Color(0xFFEDA89F))
        BrassVital("Magicka", state.magicka, Color(0xFF5B8EE6), Color(0xFFA9C4F6))
        BrassVital("Fatigue", state.fatigue, Color(0xFF74B36A), Color(0xFFB4DCA9))
        HudEffectsRow(state, labelColor = Brass, labelWidth = 56.dp)
    }
}

@Composable
private fun BrassVital(label: String, value: Dynamic, bar: Color, text: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = text, fontSize = 12.sp, fontFamily = MwDisplay, maxLines = 1, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(IronDeep)) {
            Box(Modifier.fillMaxWidth(value.ratio).fillMaxHeight().background(bar))
        }
        Text(
            if (value.max > 0f) "${value.current.roundToInt()}/${value.max.roundToInt()}" else "--",
            color = IronText, fontSize = 12.sp, fontFamily = MwDisplay, maxLines = 1,
            textAlign = TextAlign.End, modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
private fun BrassFavourite(slot: FavSlot?, side: FavSide, state: GameState, onSelectTab: (Tab) -> Unit, modifier: Modifier) {
    val icon = rememberFavouriteIcon(slot, state)
    val spell = slot?.kind == FavKind.MAGIC
    val on = slot != null && favouriteHighlighted(slot, state)
    val accent = if (spell) Verdigris else BrassBright
    // Consumables and ammo show how many are left, and dim once none are.
    val counted = slot != null && !spell && slot.category in COUNTED_CATEGORIES
    val count = if (counted) state.inventory.filter { it.id == slot!!.id }.sumOf { it.count } else 0
    Column(
        modifier
            .clickable(role = Role.Button) {
                if (slot == null) onSelectTab(if (side == FavSide.LEFT) Tab.MAGIC else Tab.INVENTORY)
                else favouriteTap(slot, state)
            }
            .padding(vertical = 2.dp)
            .alpha(if (counted && count == 0) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (on) (if (spell) VerdigrisFill else BrassFill) else IronPlate)
                    .border(2.dp, if (on) accent else Color(0xFF3D3427), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(30.dp))
                else if (slot == null) Text("+", color = IronMuted, fontSize = 18.sp, fontFamily = MwDisplay)
            }
            if (counted) BrassChip("x$count", Modifier.align(Alignment.BottomEnd).offset(8.dp, 2.dp))
        }
        Text(
            slot?.name ?: "Empty", color = if (slot == null) IronMuted else IronText, fontSize = 11.sp,
            fontFamily = MwDisplay, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrassRound(
    icon: ImageVector, label: String?, x: Dp, y: Dp, size: Dp = 64.dp, active: Boolean = false,
    accent: Color = BrassBright, chip: String? = null, description: String? = null, dim: Boolean = false,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    Box(Modifier.offset(x, y).size(size)) {
        Column(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(if (active) (if (accent == Verdigris) VerdigrisFill else BrassFill) else IronSolid)
                .border(if (active) 3.dp else 2.dp, if (active) accent else BrassEdge, CircleShape)
                .semantics { selected = active; if (description != null) contentDescription = description }
                .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
                .alpha(if (dim) 0.4f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (active) accent else Brass, modifier = Modifier.size(22.dp))
            if (label != null) {
                Text(label, color = if (active) accent else IronText, fontSize = 10.sp, fontFamily = MwDisplay, maxLines = 1)
            }
        }
        if (chip != null) BrassChip(chip, Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp))
    }
}

/** Hold-to-walk Forward, drawn like the other brass buttons and lit while held. */
@Composable
private fun BrassForward(x: Dp, y: Dp, size: Dp = 64.dp) {
    var held by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { RgdsControls.forward(false) } }
    Column(
        Modifier.offset(x, y).size(size).clip(CircleShape)
            .background(if (held) BrassFill else IronSolid)
            .border(if (held) 3.dp else 2.dp, if (held) BrassBright else BrassEdge, CircleShape)
            .semantics { role = Role.Button; contentDescription = "Forward, hold to walk" }
            .holdToWalk { held = it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(ForwardIcon, contentDescription = null, tint = if (held) BrassBright else Brass, modifier = Modifier.size(22.dp))
        Text("FORWARD", color = if (held) BrassBright else IronText, fontSize = 9.sp, fontFamily = MwDisplay, maxLines = 1)
    }
}

@Composable
private fun BrassChip(text: String, modifier: Modifier) {
    Text(
        text, color = Brass, fontSize = 10.sp, fontFamily = MwDisplay,
        modifier = modifier.background(IronDeep, RoundedCornerShape(6.dp))
            .border(1.dp, BrassEdge, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
private fun BrassAttack(x: Dp, y: Dp) {
    var held by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { RgdsControls.attack(false) } }
    // A visual of the hold only; the engine measures the real swing charge itself.
    val charge by animateFloatAsState(
        if (held) 1f else 0f, tween(if (held) 1000 else 160, easing = LinearEasing), label = "charge"
    )
    Box(Modifier.offset(x, y).size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(IronDeep)
            if (charge > 0f) drawArc(BrassBright, -90f, 360f * charge, useCenter = true)
        }
        Column(
            Modifier.size(112.dp).clip(CircleShape)
                .background(if (held) BrassFill else Color(0xFF2A2219))
                .border(2.dp, Iron, CircleShape)
                .semantics { role = Role.Button; contentDescription = "Attack, hold to charge" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume(); held = true; RgdsControls.attack(true)
                        try {
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                change?.consume()
                            } while (change != null && change.pressed)
                        } finally { held = false; RgdsControls.attack(false) }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(AttackIcon, contentDescription = null, tint = IronText, modifier = Modifier.size(34.dp))
            Text(if (RgdsControls.spellReadied) "CAST" else "ATTACK", color = IronText, fontSize = 14.sp, fontFamily = MwDisplay)
            Text(if (held) "release" else "hold", color = Brass, fontSize = 11.sp, fontFamily = MwDisplay)
        }
        BrassChip("R1", Modifier.align(Alignment.TopEnd).offset((-2).dp, 0.dp))
    }
}

@Composable
private fun BrassSettings(onClose: () -> Unit) {
    // Scrim: a tap outside closes the drawer and never reaches the map.
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(Unit) { detectTapGestures { onClose() } })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier.width(310.dp).fillMaxHeight().background(IronSolid)
                .border(1.dp, Brass)
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HUD controls", color = BrassBright, fontSize = 18.sp, fontFamily = MwDisplay)
                Spacer(Modifier.weight(1f))
                Text(
                    "Done", color = BrassBright, fontSize = 13.sp, fontFamily = MwDisplay,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BrassFill)
                        .border(1.dp, BrassBright, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
            BrassSwitchRow("D-pad walks", "W A S D on the HUD tab", RgdsControls.walk, RgdsControls::updateWalk)
            BrassSwitchRow("ABXY looks", "X up, B down, Y left, A right. L1 uses, L2 jumps",
                RgdsControls.faceLook, RgdsControls::updateFaceLook)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("Look speed", color = IronText, fontSize = 15.sp, fontFamily = MwDisplay)
                    Spacer(Modifier.weight(1f))
                    Text("%.1f".format(RgdsControls.sensitivity), color = BrassBright, fontSize = 14.sp, fontFamily = MwDisplay)
                }
                Slider(
                    RgdsControls.sensitivity, RgdsControls::updateSensitivity, valueRange = 0.5f..3f,
                    colors = SliderDefaults.colors(thumbColor = BrassBright, activeTrackColor = Brass, inactiveTrackColor = IronDeep)
                )
            }
            Text(
                "Drag the map to look; tap it for a door's name or the world map. + and - zoom it.",
                color = IronMuted, fontSize = 12.sp, fontFamily = MwDisplay
            )
            Text(
                "Forward walks while held. Auto walks on its own until you tap it again, step forward or back, or open a menu.",
                color = IronMuted, fontSize = 12.sp, fontFamily = MwDisplay
            )
            Text(
                "Hold WEAPON with a bow or crossbow out for your two ammo favourites. R1 attacks on every tab; Select opens Rest/Wait.",
                color = IronMuted, fontSize = 12.sp, fontFamily = MwDisplay
            )
        }
    }
}

@Composable
private fun BrassSwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = IronText, fontSize = 15.sp, fontFamily = MwDisplay)
            Text(detail, color = IronMuted, fontSize = 12.sp, fontFamily = MwDisplay)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = IronText, checkedTrackColor = Color(0xFF3F6F65), checkedBorderColor = Verdigris,
                uncheckedThumbColor = IronMuted, uncheckedTrackColor = IronDeep, uncheckedBorderColor = BrassEdge
            )
        )
    }
}
