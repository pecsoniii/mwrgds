package org.openmw.companion

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.*
import org.libsdl.app.SDLActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign

/** One owner for every synthetic key/button, so interruption cannot leave movement held. */
internal object RgdsControls {
    private val controller = GameplayTouchController(object : GameplayTouchController.Sink {
        override fun key(code: Int, down: Boolean) {
            if (down) SDLActivity.onNativeKeyDown(code) else SDLActivity.onNativeKeyUp(code)
        }
        override fun attack(down: Boolean) = CompanionActions.rgdsAttack(down)
        override fun activate() = CompanionActions.rgdsActivate()
        override fun look(x: Float, y: Float, attackHeld: Boolean) =
            SDLActivity.onNativeMouse(if (attackHeld) 1 else 0, MotionEvent.ACTION_MOVE, x, y, true)
        override fun jump() = CompanionActions.rgdsTrigger("Jump")
        override fun autoRun(on: Boolean) { autoRunning = on }
    })
    /** Auto-run is on: the Auto button lights. Set only by the controller, which also cancels it. */
    var autoRunning by mutableStateOf(false); private set
    private var prefs: android.content.SharedPreferences? = null
    var walk by mutableStateOf(false); private set
    var faceLook by mutableStateOf(false); private set
    var sensitivity by mutableFloatStateOf(1.5f); private set
    var gameplay by mutableStateOf(false); private set
    var alwaysRun by mutableStateOf(false); private set
    var sneaking by mutableStateOf(false); private set
    var weaponReadied by mutableStateOf(false); private set
    var spellReadied by mutableStateOf(false); private set
    private var foreground = false
    private var gameplayTab = true
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("rgds_touch_controls", Context.MODE_PRIVATE)
        walk = prefs!!.getBoolean("dpad_walk", false)
        faceLook = prefs!!.getBoolean("face_look", false)
        sensitivity = prefs!!.getFloat("sensitivity", 1.5f).coerceIn(0.5f, 3f)
        // No Shift: OpenMW runs when Run XOR Always Run, so a held Shift made the D-pad run in both
        // states of the Run/Walk toggle. Without it the D-pad follows the toggle like every input.
        controller.walk = walk; controller.faceLook = faceLook; controller.shiftToRun = false
        mapZoomLevel = prefs!!.getInt("map_zoom_level", 0).coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL)
        map3d = prefs!!.getBoolean("map_3d", false)
    }
    /** The HUD minimap shows the engine's 3D view of the place instead of the flat local map. */
    var map3d by mutableStateOf(false); private set
    fun toggleMap3d() {
        map3d = !map3d
        prefs?.edit()?.putBoolean("map_3d", map3d)?.apply()
    }
    /** HUD map zoom in x1.25 steps: -8 is ~0.17x (3x wider than the old -3 limit), 5 ~3x. */
    var mapZoomLevel by mutableIntStateOf(0); private set
    val mapZoom: Float get() = Math.pow(1.25, mapZoomLevel.toDouble()).toFloat()
    const val MIN_ZOOM_LEVEL = -8
    const val MAX_ZOOM_LEVEL = 5
    fun zoomMap(steps: Int) {
        mapZoomLevel = (mapZoomLevel + steps).coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL)
        prefs?.edit()?.putInt("map_zoom_level", mapZoomLevel)?.apply()
    }
    fun updateWalk(value: Boolean) { walk = value; controller.walk = value; prefs?.edit()?.putBoolean("dpad_walk", value)?.apply() }
    fun updateFaceLook(value: Boolean) { faceLook = value; controller.faceLook = value; prefs?.edit()?.putBoolean("face_look", value)?.apply(); updateTicker() }
    fun updateSensitivity(value: Float) { sensitivity = value; prefs?.edit()?.putFloat("sensitivity", value)?.apply() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    fun updateGameplayState(value: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateGameplayState(value) }
            return
        }
        android.util.Log.d("RgdsControls", "gameplay=$value foreground=$foreground gameplayTab=$gameplayTab")
        gameplay = value; controller.active = value && foreground && gameplayTab
        updateTicker()
    }
    fun updateMovementModes(run: Boolean, sneak: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateMovementModes(run, sneak) }
            return
        }
        alwaysRun = run; sneaking = sneak
    }
    fun updateReadiedModes(weapon: Boolean, spell: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateReadiedModes(weapon, spell) }
            return
        }
        weaponReadied = weapon; spellReadied = spell
    }
    fun foreground(value: Boolean) {
        foreground = value; controller.active = value && gameplay && gameplayTab
        updateTicker()
    }
    private fun updateTicker() {
        ticker?.cancel(); ticker = null
        if (controller.active && faceLook) ticker = scope.launch {
            var previous = System.nanoTime()
            while (isActive) {
                delay(16)
                val now = System.nanoTime()
                controller.tick((now - previous) / 1_000_000_000f, 260f * sensitivity)
                previous = now
            }
        }
    }
    fun setGameplayTab(value: Boolean) {
        controller.releaseAll()
        gameplayTab = value
        controller.active = foreground && gameplay && value
        updateTicker()
    }
    fun release() = controller.releaseAll()
    fun attack(down: Boolean) = controller.setAttack(down)
    /** Hold-to-walk Forward: the W key while a finger is on the button. */
    fun forward(down: Boolean) = controller.setForward(down)
    /** Auto: walk forward on its own until tapped again (or until forward/back, a menu, or leaving the tab). */
    fun toggleAutoRun() = controller.setAutoRun(!controller.autoRun)
    fun tap(code: Int) {
        if (!controller.active) return
        when (code) {
            62 -> CompanionActions.rgdsActivate()
            34 -> CompanionActions.rgdsTrigger("ToggleWeapon")
            46 -> CompanionActions.rgdsTrigger("ToggleSpell")
            33 -> CompanionActions.rgdsTrigger("Jump")
            113 -> CompanionActions.rgdsTrigger("ToggleSneak")
            115 -> CompanionActions.rgdsTrigger("AlwaysRun")
            else -> controller.tap(code)
        }
    }
    /** Opens the game's Rest/Wait window: the T key, OpenMW's default keyboard binding for Rest. */
    fun restOrWait() = tap(48)
    fun drag(x: Float, y: Float) = controller.drag(x * sensitivity, y * sensitivity)
    fun key(event: KeyEvent): Boolean {
        val source = event.source
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        return gamepad && controller.route(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
    }
    /**
     * Strip the HAT axes while the D-pad walks, and the left trigger while L2 jumps (ABXY look);
     * the sticks and the right trigger always reach the engine untouched.
     */
    fun motion(event: MotionEvent): MotionEvent? {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return null
        controller.stick(event.getAxisValue(MotionEvent.AXIS_Y))
        val trigger = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val stripTrigger = controller.leftTrigger(trigger)
        val stripHat = controller.active && walk
        if (!stripTrigger && !stripHat) return null
        if (stripHat) {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            controller.route(19, y < -0.5f); controller.route(20, y > 0.5f)
            controller.route(21, x < -0.5f); controller.route(22, x > 0.5f)
        }
        val properties = Array(event.pointerCount) { MotionEvent.PointerProperties().also { p -> event.getPointerProperties(it, p) } }
        val coords = Array(event.pointerCount) { MotionEvent.PointerCoords().also { c ->
            event.getPointerCoords(it, c)
            if (stripHat) { c.setAxisValue(MotionEvent.AXIS_HAT_X, 0f); c.setAxisValue(MotionEvent.AXIS_HAT_Y, 0f) }
            if (stripTrigger) { c.setAxisValue(MotionEvent.AXIS_LTRIGGER, 0f); c.setAxisValue(MotionEvent.AXIS_BRAKE, 0f) }
        } }
        return MotionEvent.obtain(event.downTime, event.eventTime, event.action, event.pointerCount,
            properties, coords, event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
            event.deviceId, event.edgeFlags, event.source, event.flags)
    }
}

private val RgdsPanel = Color(0xFF252017)
private val RgdsGold = Color(0xFFC9A063)
private val RgdsText = Color(0xFFD8CBB0)

private val RgdsInk = Color(0xFF3B2B17)
private val RgdsActive = Color(0xFFF1CA66)

@Composable
internal fun RgdsTouchSettings(showToggles: Boolean = true, showSpeed: Boolean = true,
    parchment: Boolean = false) {
    val context = LocalContext.current
    remember(context) { RgdsControls.init(context); true }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showToggles) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RgdsModeToggle("D-pad move", RgdsControls.walk, Modifier.weight(1f),
                onChange = RgdsControls::updateWalk)
            RgdsModeToggle("ABXY look", RgdsControls.faceLook, Modifier.weight(1f),
                onChange = RgdsControls::updateFaceLook)
            RgdsModeToggle(if (RgdsControls.alwaysRun) "Run" else "Walk", RgdsControls.alwaysRun,
                Modifier.weight(1f), enabled = RgdsControls.gameplay,
                onChange = { RgdsControls.tap(115) })
        }
        if (showSpeed) Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aim speed", color = if (parchment) RgdsInk else RgdsText,
                fontFamily = FontFamily.SansSerif, fontSize = 13.sp)
            Slider(RgdsControls.sensitivity, RgdsControls::updateSensitivity,
                modifier = Modifier.weight(1f).padding(start = 10.dp), valueRange = 0.5f..3f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF6B4725),
                    activeTrackColor = Color(0xFF8F652F), inactiveTrackColor = Color(0xFFB79B65)))
        }
    }
}

@Composable
private fun RgdsModeToggle(label: String, checked: Boolean, modifier: Modifier,
    enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(modifier.height(36.dp).clip(RoundedCornerShape(5.dp))
        .background(if (checked) RgdsActive else RgdsPanel)
        .border(1.dp, RgdsGold, RoundedCornerShape(5.dp))
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(12.dp).background(if (checked) RgdsInk else Color.Transparent, RoundedCornerShape(2.dp))
            .border(1.dp, if (checked) RgdsInk else RgdsGold, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (checked) RgdsInk else RgdsText,
            fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

