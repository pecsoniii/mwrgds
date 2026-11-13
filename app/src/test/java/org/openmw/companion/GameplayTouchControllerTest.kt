package org.openmw.companion

import org.junit.Assert.*
import org.junit.Test

class GameplayTouchControllerTest {
    private class Recorder : GameplayTouchController.Sink {
        var activations = 0
        var jumps = 0
        override fun activate() { activations++ }
        override fun jump() { jumps++ }
        val keys = mutableListOf<Pair<Int, Boolean>>()
        val attacks = mutableListOf<Boolean>()
        val looks = mutableListOf<Pair<Float, Float>>()
        val lookAttackStates = mutableListOf<Boolean>()
        override fun key(code: Int, down: Boolean) { keys.add(code to down) }
        override fun attack(down: Boolean) { attacks.add(down) }
        override fun look(x: Float, y: Float, attackHeld: Boolean) { looks.add(x to y); lookAttackStates.add(attackHeld) }
        val autoRuns = mutableListOf<Boolean>()
        override fun autoRun(on: Boolean) { autoRuns.add(on) }
    }
    @Test fun autoRunHoldsForwardUntilTappedAgainOrCancelled() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.setAutoRun(true); assertFalse(c.autoRun); assertTrue(sink.keys.isEmpty())
        c.active = true; c.walk = true
        c.setAutoRun(true); c.setAutoRun(true); c.setAutoRun(false)
        assertEquals(listOf(51 to true, 51 to false), sink.keys); assertEquals(listOf(true, false), sink.autoRuns)
        // Stepping back cancels it; walking sideways does not.
        sink.keys.clear(); c.setAutoRun(true); c.route(21, true); c.route(21, false)
        assertTrue(c.autoRun)
        c.route(20, true); c.route(20, false)
        assertFalse(c.autoRun)
        assertEquals(listOf(51 to true, 29 to true, 29 to false, 51 to false, 47 to true, 47 to false), sink.keys)
        // The stick cancels it only when pushed past half way.
        c.setAutoRun(true); c.stick(0.3f); assertTrue(c.autoRun); c.stick(-0.8f); assertFalse(c.autoRun)
        // A menu, leaving the tab or backgrounding ends it and lets W go.
        sink.keys.clear(); c.setAutoRun(true); c.active = false
        assertFalse(c.autoRun); assertEquals(listOf(51 to true, 51 to false), sink.keys)
        assertEquals(false, sink.autoRuns.last())
    }
    @Test fun dpadForwardButtonAndAutoRunShareOneForwardKey() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true; c.walk = true
        c.route(19, true); c.setForward(true); c.route(19, false)
        assertEquals(listOf(51 to true), sink.keys)
        c.setForward(false); assertEquals(listOf(51 to true, 51 to false), sink.keys)
        // Forward or D-pad up takes over from auto-run without a gap, and stops when let go.
        sink.keys.clear(); c.setAutoRun(true); c.setForward(true)
        assertFalse(c.autoRun); assertEquals(listOf(51 to true), sink.keys)
        c.setForward(false); assertEquals(listOf(51 to true, 51 to false), sink.keys)
        sink.keys.clear(); c.setAutoRun(true); c.route(19, true)
        assertFalse(c.autoRun); assertEquals(listOf(51 to true), sink.keys)
        c.route(19, false); assertEquals(listOf(51 to true, 51 to false), sink.keys)
        // Turning D-pad walking off keeps a held Forward button walking.
        sink.keys.clear(); c.setForward(true); c.route(19, true); c.walk = false
        assertEquals(listOf(51 to true), sink.keys)
        c.releaseAll(); assertEquals(listOf(51 to true, 51 to false), sink.keys)
    }
    @Test fun menuEntryReleasesAllAndRestoresNormalInput() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true; c.walk = true; c.faceLook = true
        assertTrue(c.route(19, true)); assertTrue(c.route(96, true))
        c.setAttack(true); c.active = false; c.releaseAll()
        assertEquals(listOf(51 to true, 51 to false), sink.keys)
        assertEquals(listOf(true, false), sink.attacks)
        assertFalse(c.route(19, true)); assertFalse(c.route(96, true))
        c.tick(0.016f, 100f); assertTrue(sink.looks.isEmpty())
    }
    @Test fun repeatAndToggleDoNotStickMovement() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true; c.walk = true
        c.route(21, true); c.route(21, true); c.walk = false
        assertEquals(listOf(29 to true, 29 to false), sink.keys)
        assertFalse(c.route(21, false))
    }
    @Test fun aimingAndAttackAreIndependentAndDiagonalSpeedIsBounded() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true; c.faceLook = true
        c.setAttack(true); c.drag(5f, -3f)
        assertEquals(listOf(5f to -3f), sink.looks)
        assertEquals(listOf(true), sink.attacks)
        assertEquals(listOf(true), sink.lookAttackStates)
        c.route(96, true); c.route(99, true); c.tick(0.02f, 100f)
        val (x,y) = sink.looks.last()
        assertEquals(2f, kotlin.math.sqrt(x*x+y*y), 0.0001f)
        assertTrue(x > 0); assertTrue(y < 0)
        c.setAttack(false); assertEquals(listOf(true,false), sink.attacks)
        c.drag(1f, 1f); assertFalse(sink.lookAttackStates.last())
    }
    @Test fun l1ActivatesOnceOnlyInFaceLookAndConsumesReleaseAcrossMenuEntry() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true
        assertFalse(c.route(102, true)); assertFalse(c.route(102, false))
        c.faceLook = true
        assertTrue(c.route(102, true)); assertTrue(c.route(102, true))
        assertEquals(1, sink.activations)
        c.active = false; c.faceLook = false
        assertTrue(c.route(102, false))
        assertFalse(c.route(102, true))
        assertEquals(1, sink.activations)
        c.active = true; c.faceLook = true
        assertTrue(c.route(102, true)); assertTrue(c.route(102, false))
        assertEquals(2, sink.activations)
    }
    @Test fun l2JumpsOnceInFaceLookFromKeyOrTriggerAndOtherwisePassesThrough() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true
        assertFalse(c.route(104, true)); assertFalse(c.route(104, false))
        assertFalse(c.leftTrigger(1f)); assertEquals(0, sink.jumps)
        c.faceLook = true
        // Key and trigger of one physical press: one jump, and a key repeat adds none.
        assertTrue(c.route(104, true)); assertTrue(c.leftTrigger(0.9f)); assertTrue(c.route(104, true))
        assertEquals(1, sink.jumps)
        assertTrue(c.route(104, false)); assertTrue(c.leftTrigger(0.5f)); assertEquals(1, sink.jumps)
        assertTrue(c.leftTrigger(0f))
        // Trigger alone, with hysteresis: 0.5 is not a press, 0.7 is, 0.4 still holds.
        assertTrue(c.leftTrigger(0.5f)); assertEquals(1, sink.jumps)
        assertTrue(c.leftTrigger(0.7f)); assertTrue(c.leftTrigger(0.4f)); assertEquals(2, sink.jumps)
        // A menu opens mid-press: the release is still swallowed, the next press goes to the engine.
        c.active = false
        assertTrue(c.leftTrigger(0f)); assertFalse(c.leftTrigger(0.9f))
        assertFalse(c.route(104, true)); assertEquals(2, sink.jumps)
    }
    @Test fun dpadDoesNotHoldShiftUnlessAsked() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.active = true; c.walk = true
        c.route(19, true); c.route(19, false)
        assertEquals(listOf(51 to true, 51 to false), sink.keys)
    }
    @Test fun disabledControlsNeverInjectAndInvalidDeltasAreIgnored() {
        val sink = Recorder(); val c = GameplayTouchController(sink)
        c.setAttack(true); c.tap(34); c.drag(4f, 5f)
        assertTrue(sink.keys.isEmpty()); assertTrue(sink.attacks.isEmpty()); assertTrue(sink.looks.isEmpty())
        c.active = true; c.drag(Float.NaN, 1f); assertTrue(sink.looks.isEmpty())
        assertFalse(c.route(96, true))
    }
}
