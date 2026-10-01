package com.morningsteps.kids.domain

import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.domain.timers.ChimePolicy
import com.morningsteps.kids.domain.timers.TimerEngine
import com.morningsteps.kids.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {
    private val clock = FakeClock()
    private val duration = 60_000L

    private fun started() = TimerEngine.start("s", "step", duration, null, clock)

    @Test
    fun remainingIsDerivedFromMonotonicClock() {
        val t = started()
        clock.advance(15_000)
        assertEquals(45_000L, TimerEngine.remainingMillis(t, clock))
        assertEquals(TimerStatus.RUNNING, TimerEngine.effectiveStatus(t, clock))
    }

    @Test
    fun pauseFreezesAndResumeContinues() {
        var t = started()
        clock.advance(20_000)
        t = TimerEngine.pause(t, clock)
        assertEquals(TimerStatus.PAUSED, t.status)
        assertEquals(40_000L, t.remainingAtCheckpointMillis)
        clock.advance(100_000)
        assertEquals(40_000L, TimerEngine.remainingMillis(t, clock))
        t = TimerEngine.start("s", "step", duration, t, clock)
        clock.advance(10_000)
        assertEquals(30_000L, TimerEngine.remainingMillis(t, clock))
    }

    @Test
    fun resetReturnsToFullDuration() {
        var t = started()
        clock.advance(50_000)
        t = TimerEngine.reset(t, duration, clock)
        assertEquals(TimerStatus.IDLE, t.status)
        assertEquals(duration, TimerEngine.remainingMillis(t, clock))
    }

    @Test
    fun expiryIsReportedAsFinishedAndNeverNegative() {
        val t = started()
        clock.advance(61_000)
        assertEquals(0L, TimerEngine.remainingMillis(t, clock))
        assertEquals(TimerStatus.FINISHED, TimerEngine.effectiveStatus(t, clock))
        val paused = TimerEngine.pause(t, clock)
        assertEquals(TimerStatus.FINISHED, paused.status)
    }

    @Test
    fun backgroundRecoveryKeepsTimeElapsingOnSameBoot() {
        val t = started()
        clock.advance(25_000) // app was in the background, no work was running
        assertNull("still running: nothing to persist", TimerEngine.recover(t, clock))
        assertEquals(35_000L, TimerEngine.remainingMillis(t, clock))
    }

    @Test
    fun backgroundExpiryRecoversAsFinished() {
        val t = started()
        clock.advance(10 * 60_000)
        val recovered = TimerEngine.recover(t, clock)!!
        assertEquals(TimerStatus.FINISHED, recovered.status)
        assertEquals(0L, recovered.remainingAtCheckpointMillis)
    }

    @Test
    fun deviceRestartRestoresPausedWithLastPersistedRemaining() {
        var t = started()
        clock.advance(10_000)
        t = TimerEngine.pause(t, clock)
        t = TimerEngine.start("s", "step", duration, t, clock) // persisted: 50s left at this checkpoint
        clock.advance(5_000)
        clock.reboot()
        assertEquals(TimerStatus.PAUSED_AFTER_RESTART, TimerEngine.effectiveStatus(t, clock))
        val recovered = TimerEngine.recover(t, clock)!!
        assertEquals(TimerStatus.PAUSED_AFTER_RESTART, recovered.status)
        assertEquals(50_000L, recovered.remainingAtCheckpointMillis)
        // Resuming after restart continues from the persisted value.
        val resumed = TimerEngine.start("s", "step", duration, recovered, clock)
        clock.advance(1_000)
        assertEquals(49_000L, TimerEngine.remainingMillis(resumed, clock))
    }

    @Test
    fun invalidMonotonicReferenceIsTreatedLikeRestart() {
        val t = started()
        clock.elapsed = t.checkpointElapsedRealtimeMillis - 1 // clock went backwards: reference invalid
        assertFalse(TimerEngine.isReferenceValid(t, clock))
        assertEquals(TimerStatus.PAUSED_AFTER_RESTART, TimerEngine.recover(t, clock)!!.status)
    }

    @Test
    fun wallClockChangesDoNotAffectActiveTimer() {
        val t = started()
        clock.wall += 3 * 3_600_000L // user moved the clock forward
        assertEquals(duration, TimerEngine.remainingMillis(t, clock))
        clock.wall -= 24 * 3_600_000L // and far back
        clock.elapsed += 1_000
        assertEquals(duration - 1_000, TimerEngine.remainingMillis(t, clock))
        assertNull(TimerEngine.recover(t, clock))
    }

    @Test
    fun chimeOnlyForLiveObservedExpiry() {
        // Live: previous tick 200 ms ago showed time left.
        assertTrue(ChimePolicy.shouldChime(true, 150L, 1_000L, 1_200L, 0L))
        // Sound disabled.
        assertFalse(ChimePolicy.shouldChime(false, 150L, 1_000L, 1_200L, 0L))
        // First tick after returning from background: no previous observation.
        assertFalse(ChimePolicy.shouldChime(true, null, null, 1_200L, 0L))
        // Long gap (screen was hidden in between): no delayed sound.
        assertFalse(ChimePolicy.shouldChime(true, 5_000L, 1_000L, 60_000L, 0L))
        // Already finished on the previous tick.
        assertFalse(ChimePolicy.shouldChime(true, 0L, 1_000L, 1_200L, 0L))
        // Not finished yet.
        assertFalse(ChimePolicy.shouldChime(true, 500L, 1_000L, 1_200L, 300L))
    }
}
