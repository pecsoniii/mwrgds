package org.openmw.companion

/** Stateful input routing, independent of Android and the rendering thread. */
internal class GameplayTouchController(private val sink: Sink) {
    interface Sink {
        fun key(code: Int, down: Boolean)
        fun activate()
        fun attack(down: Boolean)
        fun look(x: Float, y: Float, attackHeld: Boolean)
        fun jump()
        /** Auto-run started or stopped, by its button or by anything that cancels it. */
        fun autoRun(on: Boolean) {}
    }
    private val directions = mutableSetOf<Int>()
    // W (walk forward) has three holders: the D-pad's up, the touch Forward button and auto-run.
    // It goes down with the first and up with the last, so letting go of one never stops another.
    private var forwardTouch = false
    private var forwardKeyDown = false
    /** Walking forward on its own until toggled off, like the game's Auto Move. */
    var autoRun = false
        private set
    private val faces = mutableSetOf<Int>()
    private var attackHeld = false
    private var l1Captured = false
    // L2 arrives twice on the RG DS: as KEYCODE_BUTTON_L2 and as the analog left trigger, which
    // OpenMW reads as its Quick Keys menu. Either source counts as one press, so a jump fires once.
    private var l2Key = false
    private var l2Axis = false
    private var shiftHeld = false
    /**
     * Hold Shift while D-pad walking. OpenMW runs when Run XOR Always Run, so holding Shift
     * inverts the Run/Walk toggle; RgdsControls leaves this off so the D-pad follows the toggle.
     */
    var shiftToRun = false
    private companion object {
        const val SHIFT = 59
        const val L1 = 102
        const val L2 = 104
        const val UP = 19
        const val DOWN = 20
        const val W = 51
    }
    var active = false
        set(value) { if (!value) releaseAll(); field = value }
    var walk = false
        set(value) { if (!value) releaseWalking(); field = value }
    var faceLook = false
        set(value) { if (!value) faces.clear(); field = value }
    private val movement = mapOf(19 to 51, 20 to 47, 21 to 29, 22 to 32)
    private val looking = setOf(96, 97, 99, 100)
    fun route(code: Int, down: Boolean): Boolean {
        // Keep consuming the matching release if a menu opens or the mode changes.
        if (code == L1 && (l1Captured || (active && faceLook))) {
            if (down) {
                if (!l1Captured && active && faceLook) sink.activate()
                l1Captured = true
            } else l1Captured = false
            return true
        }
        if (code == L2 && (l2Key || (active && faceLook))) {
            val wasHeld = l2Key || l2Axis
            l2Key = down
            if (down && !wasHeld && active && faceLook) sink.jump()
            return true
        }
        val mapped = movement[code]
        if (mapped != null && ((active && walk) || code in directions)) {
            if (down && active && walk) {
                if (directions.add(code)) {
                    if (shiftToRun && !shiftHeld) { shiftHeld = true; sink.key(SHIFT, true) }
                    // Forward or back ends auto-run, as moving does for the game's own Auto Move.
                    if (code == UP || code == DOWN) setAutoRun(false)
                    if (code == UP) syncForward() else sink.key(mapped, true)
                }
            } else if (directions.remove(code)) {
                if (code == UP) syncForward() else sink.key(mapped, false)
                if (directions.isEmpty()) releaseShift()
            }
            return true
        }
        if (code in looking && ((active && faceLook) || code in faces)) {
            if (down && active && faceLook) faces.add(code) else faces.remove(code)
            return true
        }
        return false
    }
    /**
     * The analog left trigger (0..1). Returns true while it is captured, so the caller zeroes the
     * axis before the engine sees it. Captured from a press in ABXY-look gameplay until release,
     * even if a menu opens in between; otherwise the engine keeps its own L2 binding.
     */
    fun leftTrigger(value: Float): Boolean {
        if (!l2Axis && !(active && faceLook)) return false
        val wasHeld = l2Key || l2Axis
        l2Axis = if (l2Axis) value > 0.3f else value > 0.6f
        if (l2Axis && !wasHeld && active && faceLook) sink.jump()
        return true
    }
    fun setAttack(down: Boolean) {
        val next = down && active
        if (next != attackHeld) { attackHeld = next; sink.attack(next) }
    }
    /** The touch Forward button, held. Pressing it takes over from auto-run, which then stays off. */
    fun setForward(down: Boolean) {
        val next = down && active
        if (next == forwardTouch) return
        forwardTouch = next
        if (next) setAutoRun(false)
        syncForward()
    }
    fun setAutoRun(on: Boolean) {
        val next = on && active
        if (next == autoRun) return
        autoRun = next
        sink.autoRun(next)
        syncForward()
    }
    /** The left stick's forward/back axis: pushed past half way, it ends auto-run. */
    fun stick(y: Float) {
        if (autoRun && (y > 0.5f || y < -0.5f)) setAutoRun(false)
    }
    private fun syncForward() {
        val want = UP in directions || forwardTouch || autoRun
        if (want != forwardKeyDown) { forwardKeyDown = want; sink.key(W, want) }
    }
    fun tap(code: Int) {
        if (active) { sink.key(code, true); sink.key(code, false) }
    }
    fun drag(x: Float, y: Float) {
        if (active && x.isFinite() && y.isFinite()) sink.look(x.coerceIn(-100f, 100f), y.coerceIn(-100f, 100f), attackHeld)
    }
    fun tick(seconds: Float, speed: Float) {
        if (!active || !faceLook || faces.isEmpty()) return
        var x = (if (96 in faces) 1f else 0f) - (if (100 in faces) 1f else 0f)
        var y = (if (97 in faces) 1f else 0f) - (if (99 in faces) 1f else 0f)
        if (x != 0f && y != 0f) { x *= 0.70710677f; y *= 0.70710677f }
        val amount = seconds.coerceIn(0f, 0.05f) * speed
        if (x != 0f || y != 0f) sink.look(x * amount, y * amount, attackHeld)
    }
    private fun releaseWalking() {
        directions.forEach { if (it != UP) sink.key(movement.getValue(it), false) }
        directions.clear()
        syncForward()
        releaseShift()
    }
    private fun releaseShift() { if (shiftHeld) { shiftHeld = false; sink.key(SHIFT, false) } }
    fun releaseAll() {
        forwardTouch = false
        if (autoRun) { autoRun = false; sink.autoRun(false) }
        releaseWalking(); faces.clear(); setAttack(false)
    }
}
