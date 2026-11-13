package org.openmw.companion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/*
 * Shared pieces of the HUD tab (AimBrassPanel.kt): its line icons, drag-to-look on the minimap and
 * hold-to-walk. The Field tab that first held them was merged into the HUD tab on 2026-11-09.
 */

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

// Icons for the HUD tab (AimBrassPanel.kt).
internal val SwordIcon = strokeIcon("sword", "M20 4 L10 14 M20 4 L17 4 M20 4 L20 7 M7 11 L13 17 M10 14 L5 19")
internal val SparkIcon = strokeIcon("spark", "M12 3 L13.8 10.2 L21 12 L13.8 13.8 L12 21 L10.2 13.8 L3 12 L10.2 10.2 Z")
internal val CogIcon = strokeIcon("cog",
    "M15.2 12a3.2 3.2 0 1 1-6.4 0a3.2 3.2 0 1 1 6.4 0Z", "M18.5 12a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0Z",
    "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
internal val SneakIcon = strokeIcon("sneak",
    "M3 12 C6 16 18 16 21 12", "M6 14.5 L4.5 16.5 M10 15.8 L9.5 18 M14 15.8 L14.5 18 M18 14.5 L19.5 16.5")
internal val GaitIcon = strokeIcon("gait",
    "M8 4c1.7 0 2.5 1.6 2.5 3.6S9.6 11 8.3 11 6 9.6 6 7.6 6.6 4 8 4Z",
    "M15.5 10c1.7 0 2.5 1.6 2.5 3.6S17.1 17 15.8 17 13.5 15.6 13.5 13.6 14.1 10 15.5 10Z")
internal val HourglassIcon = strokeIcon("wait",
    "M6 3h12M6 21h12", "M7.5 3c0 4.5 4.5 6 4.5 9s-4.5 4.5-4.5 9M16.5 3c0 4.5-4.5 6-4.5 9s4.5 4.5 4.5 9",
    "M9.5 18.5h5")
internal val JumpIcon = strokeIcon("jump", "M12 18 V5 M7 10 L12 5 L17 10", "M5 21 H19")
internal val UseIcon = strokeIcon("use",
    "M9 11V5.5a1.5 1.5 0 0 1 3 0V11", "M12 10V4.5a1.5 1.5 0 0 1 3 0V11",
    "M15 10.5V6.5a1.5 1.5 0 0 1 3 0V14c0 4-2.5 7-6.5 7S5 18.5 4.5 15.5L3.8 12.5a1.4 1.4 0 0 1 2.5-1.1L9 14")
// A map cursor for Forward and a double chevron for Auto, so neither reads as Jump's arrow.
internal val ForwardIcon = strokeIcon("forward", "M12 4 L19 19 L12 15 L5 19 Z")
internal val PlusIcon = strokeIcon("plus", "M12 5 V19 M5 12 H19")
internal val MinusIcon = strokeIcon("minus", "M5 12 H19")
internal val CubeIcon = strokeIcon("cube", "M12 3 L20 7.5 L20 16.5 L12 21 L4 16.5 L4 7.5 Z", "M4 7.5 L12 12 L20 7.5 M12 12 L12 21")
internal val AutoRunIcon =strokeIcon("autorun", "M6 12 L12 6 L18 12", "M6 19 L12 13 L18 19")
internal val AttackIcon = strokeIcon("attack",
    "M20 4 L10 14 M20 4 L17 4 M20 4 L20 7 M7 11 L13 17 M10 14 L5 19",
    "M4 4 L14 14 M4 4 L7 4 M4 4 L4 7 M11 17 L17 11 M14 14 L19 19")

/**
 * Drag to look. Nothing happens until a finger passes the touch slop, so a tap still reaches the
 * map's own tap handler (door markers, the world map); past it every move turns the view and is
 * consumed, which cancels that tap. Must come AFTER the tap detector in the modifier chain.
 * [onLook] gets the finger's position while it turns the view, and null when it lifts.
 */
internal fun Modifier.fieldLookDrag(enabled: Boolean, onLook: (Offset?) -> Unit = {}): Modifier = composed {
    val report by rememberUpdatedState(onLook)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var dragging = false
            var previous = down.position
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!dragging && (change.position - down.position).getDistance() > slop) {
                        dragging = true
                        previous = change.position
                    }
                    if (dragging) {
                        val delta = change.position - previous
                        previous = change.position
                        if (delta.x != 0f || delta.y != 0f) RgdsControls.drag(delta.x, delta.y)
                        report(change.position)
                        change.consume()
                    }
                }
            } finally {
                if (dragging) report(null)
            }
        }
    }
}

/** Holds Forward from finger down to finger up, whatever happens between (HUD tab). */
internal fun Modifier.holdToWalk(onHeld: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume(); onHeld(true); RgdsControls.forward(true)
        try {
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                change?.consume()
            } while (change != null && change.pressed)
        } finally { onHeld(false); RgdsControls.forward(false) }
    }
}
