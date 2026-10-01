package com.morningsteps.kids.data

import com.morningsteps.kids.data.repository.FinishResult
import com.morningsteps.kids.data.repository.SaveResult
import com.morningsteps.kids.data.repository.StartTimerResult
import com.morningsteps.kids.domain.routines.DraftError
import com.morningsteps.kids.domain.routines.Limits
import com.morningsteps.kids.domain.routines.StepDraft
import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.fakes.TestEnv
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineRepositoryTest {

    private fun env() = TestEnv()

    private suspend fun TestEnv.completeAll(routineId: String) {
        stepsOf(routineId).forEach { repo.completeStep(routineId, it.id) }
    }

    @Test
    fun startersAreSeededWithoutTimers() = runBlocking {
        val e = env()
        val routines = e.repo.routines.first()
        assertEquals(listOf("Morning", "Evening", "Before School", "After School"), routines.map { it.routine.name })
        assertEquals(listOf(4, 5, 4, 5), routines.map { it.steps.size })
        assertTrue(routines.flatMap { it.steps }.all { it.timerDurationSeconds == null })
        assertTrue(routines.none { it.inProgress })
    }

    @Test
    fun completionAndUndoPersistIndependently() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        val (s0, s1, s2) = e.stepsOf(morning.id)
        e.repo.completeStep(morning.id, s0.id)
        e.repo.completeStep(morning.id, s2.id) // order is guidance, not a lock
        var detail = e.repo.routine(morning.id).first()!!
        assertEquals(2, detail.completedCount)
        assertEquals(0.5f, detail.progress, 0f)
        assertEquals(s1.id, detail.nextSuggestedStep?.id)

        e.repo.undoStep(morning.id, s0.id)
        detail = e.repo.routine(morning.id).first()!!
        assertFalse(detail.isCompleted(s0.id))
        assertTrue(detail.isCompleted(s2.id))
        assertEquals(s0.id, detail.nextSuggestedStep?.id)
    }

    @Test
    fun routinesKeepSeparateUnfinishedSessions() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        val evening = e.routineByName("Evening")
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[0].id)
        e.repo.completeStep(evening.id, e.stepsOf(evening.id)[1].id)
        val all = e.repo.routines.first().associateBy { it.routine.name }
        assertEquals(1, all.getValue("Morning").completedCount)
        assertEquals(1, all.getValue("Evening").completedCount)
        assertTrue(all.getValue("Morning").inProgress)
        assertFalse(all.getValue("Before School").inProgress)
    }

    @Test
    fun finishRequiresAllStepsAndIsSavedOnce() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[0].id)
        assertEquals(FinishResult.NotComplete, e.repo.finishRoutine(morning.id))

        e.completeAll(morning.id)
        val first = e.repo.finishRoutine(morning.id)
        val second = e.repo.finishRoutine(morning.id) // duplicate tap
        assertTrue(first is FinishResult.Finished)
        assertEquals(FinishResult.AlreadyFinished, second)
        assertEquals(1, e.repo.history.first().size)
        // The session is closed; no new session starts by itself.
        assertNull(e.repo.routine(morning.id).first()!!.session)
    }

    @Test
    fun concurrentFinishCallsCreateOneEntry() = runBlocking {
        val e = env()
        val evening = e.routineByName("Evening")
        e.completeAll(evening.id)
        val results = (1..5).map { async { e.repo.finishRoutine(evening.id) } }.awaitAll()
        assertEquals(1, results.count { it is FinishResult.Finished })
        assertEquals(1, e.repo.history.first().size)
    }

    @Test
    fun historySnapshotSurvivesRoutineEditsAndDeletion() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        e.completeAll(morning.id)
        val id = (e.repo.finishRoutine(morning.id) as FinishResult.Finished).historyId

        val draft = e.repo.loadDraft(morning.id)!!
        val edited = draft.copy(
            name = "Sunny start",
            steps = draft.steps.drop(1).map { it.copy(label = it.label + "!") } + StepDraft("new", "Smile", "", "star", 60),
        )
        assertTrue(e.repo.saveDraft(edited) is SaveResult.Saved)
        e.repo.deleteRoutine(morning.id)

        val entry = e.repo.historyEntry(id).first()!!
        val steps = e.repo.historySteps(id).first()
        assertEquals("Morning", entry.routineName)
        assertEquals(4, entry.totalSteps)
        assertEquals(listOf("Wash your face", "Get dressed", "Eat breakfast", "Brush your teeth"), steps.map { it.label })
        assertEquals(listOf(0, 1, 2, 3), steps.map { it.displayOrder })
    }

    @Test
    fun startFreshClearsOnlyThatSessionAndKeepsHistory() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        val evening = e.routineByName("Evening")
        e.completeAll(morning.id)
        e.repo.finishRoutine(morning.id)
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[0].id)
        e.repo.completeStep(evening.id, e.stepsOf(evening.id)[0].id)

        e.repo.startFresh(morning.id)
        assertNull(e.repo.routine(morning.id).first()!!.session)
        assertEquals(1, e.repo.routine(evening.id).first()!!.completedCount)
        assertEquals(1, e.repo.history.first().size)
    }

    @Test
    fun interruptedSessionsNeverAppearInHistory() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[0].id)
        e.repo.startFresh(morning.id)
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[1].id)
        e.repo.deleteRoutine(morning.id)
        assertTrue(e.repo.history.first().isEmpty())
    }

    @Test
    fun savingEditRestartsActiveSession() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[0].id)
        assertTrue(e.repo.hasActiveSession(morning.id))
        val draft = e.repo.loadDraft(morning.id)!!
        val reordered = draft.copy(steps = draft.steps.reversed())
        assertTrue(e.repo.saveDraft(reordered) is SaveResult.Saved)
        assertFalse(e.repo.hasActiveSession(morning.id))
        // Stable step ids survive reordering.
        assertEquals(draft.steps.reversed().map { it.id }, e.stepsOf(morning.id).map { it.id })
        assertEquals(listOf(0, 1, 2, 3), e.stepsOf(morning.id).map { it.displayOrder })
    }

    @Test
    fun saveRejectsDuplicateNamesIgnoringCaseAndEnforcesLimits() = runBlocking {
        val e = env()
        val evening = e.repo.loadDraft(e.routineByName("Evening").id)!!
        val clash = e.repo.saveDraft(evening.copy(name = " morning "))
        assertTrue(clash is SaveResult.Invalid && clash.errors.contains(DraftError.NameDuplicate))
        val empty = e.repo.saveDraft(evening.copy(steps = emptyList()))
        assertTrue(empty is SaveResult.Invalid && empty.errors.contains(DraftError.NoSteps))

        repeat(Limits.MAX_ROUTINES - 4) { i ->
            val r = e.repo.saveDraft(evening.copy(id = null, name = "Extra $i", steps = evening.steps.map { it.copy(id = "x$i-${it.id}") }))
            assertTrue(r is SaveResult.Saved)
        }
        val tooMany = e.repo.saveDraft(evening.copy(id = null, name = "One more", steps = evening.steps.map { it.copy(id = "y-${it.id}") }))
        assertTrue(tooMany is SaveResult.Invalid && tooMany.errors.contains(DraftError.TooManyRoutines))
        assertNull(e.repo.duplicateRoutine(e.routineByName("Morning").id))
    }

    @Test
    fun restoreStartersKeepsDataAndMakesUniqueNames() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        e.repo.completeStep(morning.id, e.stepsOf(morning.id)[0].id)
        e.repo.deleteRoutine(e.routineByName("Evening").id)
        val result = e.repo.restoreStarters()
        assertEquals(4, result.added)
        val names = e.repo.routines.first().map { it.routine.name }
        assertTrue(names.containsAll(listOf("Morning", "Morning (2)", "Evening", "Before School (2)", "After School (2)")))
        assertEquals(names.size, names.map { it.lowercase() }.toSet().size)
        assertEquals(1, e.repo.routine(morning.id).first()!!.completedCount)
    }

    @Test
    fun clearAllDataReturnsToStartersWithEmptyHistory() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        e.completeAll(morning.id)
        e.repo.finishRoutine(morning.id)
        e.repo.deleteRoutine(e.routineByName("Evening").id)
        e.repo.clearAllData()
        val routines = e.repo.routines.first()
        assertEquals(listOf("Morning", "Evening", "Before School", "After School"), routines.map { it.routine.name })
        assertTrue(routines.none { it.inProgress })
        assertTrue(e.repo.history.first().isEmpty())
    }

    @Test
    fun historyKeepsLatestHundred() = runBlocking {
        val e = env()
        val morning = e.routineByName("Morning")
        repeat(Limits.MAX_HISTORY + 5) {
            e.clock.advance(60_000)
            e.completeAll(morning.id)
            assertTrue(e.repo.finishRoutine(morning.id) is FinishResult.Finished)
        }
        val history = e.repo.history.first()
        assertEquals(Limits.MAX_HISTORY, history.size)
        assertEquals(history.sortedByDescending { it.completedAt }, history)
        assertEquals(Limits.MAX_HISTORY, e.db.state.value.history.size)
    }

    // ------------------------------------------------------------------ timers

    private suspend fun TestEnv.withTimer(routineName: String, index: Int, seconds: Int): Pair<String, String> {
        val routine = routineByName(routineName)
        val draft = repo.loadDraft(routine.id)!!
        val steps = draft.steps.toMutableList()
        steps[index] = steps[index].copy(timerSeconds = seconds)
        repo.saveDraft(draft.copy(steps = steps))
        return routine.id to steps[index].id
    }

    @Test
    fun onlyOneTimerRunsAtATime() = runBlocking {
        val e = env()
        val (m, mStep) = e.withTimer("Morning", 0, 120)
        val (ev, evStep) = e.withTimer("Evening", 1, 60)
        assertEquals(StartTimerResult.Started, e.repo.startTimer(m, mStep, pauseOthers = false))
        e.clock.advance(30_000)
        val blocked = e.repo.startTimer(ev, evStep, pauseOthers = false)
        assertTrue(blocked is StartTimerResult.OtherRunning)
        assertEquals("Morning", (blocked as StartTimerResult.OtherRunning).info.routineName)

        assertEquals(StartTimerResult.Started, e.repo.startTimer(ev, evStep, pauseOthers = true))
        val morningTimer = e.repo.routine(m).first()!!.session!!.timers.getValue(mStep)
        assertEquals(TimerStatus.PAUSED, morningTimer.status)
        assertEquals(90_000L, morningTimer.remainingAtCheckpointMillis)
        val running = e.db.state.value.timers.values.count { it.status == TimerStatus.RUNNING.name }
        assertEquals(1, running)
    }

    @Test
    fun timerAndCompletionStayIndependent() = runBlocking {
        val e = env()
        val (r, s) = e.withTimer("Morning", 2, 60)
        e.repo.startTimer(r, s, false)
        e.clock.advance(70_000)
        e.repo.recoverTimers()
        val afterExpiry = e.repo.routine(r).first()!!
        assertEquals(TimerStatus.FINISHED, afterExpiry.session!!.timers.getValue(s).status)
        assertFalse("expiry never completes the step", afterExpiry.isCompleted(s))

        e.repo.resetTimer(r, s)
        e.repo.startTimer(r, s, false)
        e.repo.completeStep(r, s) // completing early stops the timer
        val completed = e.repo.routine(r).first()!!
        assertTrue(completed.isCompleted(s))
        assertNull(completed.session!!.timers[s])

        assertEquals(StartTimerResult.StepCompleted, e.repo.startTimer(r, s, false))
        e.repo.undoStep(r, s)
        assertEquals(StartTimerResult.Started, e.repo.startTimer(r, s, false))
        e.repo.resetTimer(r, s)
        e.repo.completeStep(r, s)
        e.repo.resetTimer(r, s) // reset never undoes completion
        assertTrue(e.repo.routine(r).first()!!.isCompleted(s))
    }

    @Test
    fun restartRecoveryPausesStoredTimer() = runBlocking {
        val e = env()
        val (r, s) = e.withTimer("Evening", 0, 300)
        e.repo.startTimer(r, s, false)
        e.clock.advance(100_000)
        e.repo.pauseTimer(r, s)
        e.repo.startTimer(r, s, false) // checkpoint: 200 s left
        e.clock.advance(20_000)
        e.clock.reboot()
        e.repo.recoverTimers()
        val timer = e.repo.routine(r).first()!!.session!!.timers.getValue(s)
        assertEquals(TimerStatus.PAUSED_AFTER_RESTART, timer.status)
        assertEquals(200_000L, timer.remainingAtCheckpointMillis)
        assertNotNull(e.repo.routine(r).first()!!.session)
    }

    @Test
    fun startFreshClearsTimersAndEditRemovesDeletedStepState() = runBlocking {
        val e = env()
        val (r, s) = e.withTimer("Morning", 0, 60)
        e.repo.startTimer(r, s, false)
        e.repo.startFresh(r)
        assertTrue(e.db.state.value.timers.isEmpty())

        e.repo.completeStep(r, s)
        val draft = e.repo.loadDraft(r)!!
        e.repo.saveDraft(draft.copy(steps = draft.steps.filterNot { it.id == s }))
        assertTrue(e.db.state.value.stepStates.isEmpty())
        assertFalse(e.db.state.value.steps.containsKey(s))
    }
}
