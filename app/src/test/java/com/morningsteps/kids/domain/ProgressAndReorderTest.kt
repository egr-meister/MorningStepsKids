package com.morningsteps.kids.domain

import com.morningsteps.kids.domain.routines.DraftError
import com.morningsteps.kids.domain.routines.DraftValidator
import com.morningsteps.kids.domain.routines.Progress
import com.morningsteps.kids.domain.routines.Reorder
import com.morningsteps.kids.domain.routines.RoutineDraft
import com.morningsteps.kids.domain.routines.RoutineNames
import com.morningsteps.kids.domain.routines.RoutineTheme
import com.morningsteps.kids.domain.routines.Step
import com.morningsteps.kids.domain.routines.StepDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAndReorderTest {
    private fun steps(n: Int) = (0 until n).map { Step("s$it", "r", "Step $it", null, "star", it, null) }

    @Test
    fun progressIsCompletedOverTotal() {
        val s = steps(4)
        assertEquals(0f, Progress.fraction(s, emptySet()), 0f)
        assertEquals(0.5f, Progress.fraction(s, setOf("s0", "s2")), 0f)
        assertEquals(1f, Progress.fraction(s, s.map { it.id }.toSet()), 0f)
        assertEquals(0f, Progress.fraction(emptyList(), emptySet()), 0f)
        assertTrue(Progress.allComplete(s, s.map { it.id }.toSet()))
        assertFalse(Progress.allComplete(emptyList(), emptySet()))
    }

    @Test
    fun completedIdsOfDeletedStepsAreIgnored() {
        assertEquals(1, Progress.completedCount(steps(2), setOf("s1", "gone")))
    }

    @Test
    fun nextSuggestedIsFirstIncompleteInOrderEvenWhenLaterStepsAreDone() {
        val s = steps(4)
        assertEquals("s0", Progress.nextSuggested(s, emptySet())?.id)
        assertEquals("s1", Progress.nextSuggested(s, setOf("s0", "s3"))?.id)
        assertEquals("s0", Progress.nextSuggested(s, setOf("s1", "s2", "s3"))?.id)
        assertEquals(null, Progress.nextSuggested(s, s.map { it.id }.toSet()))
    }

    @Test
    fun reorderMovesAndShifts() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), Reorder.move(l, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), Reorder.move(l, 3, 0))
        assertEquals(l, Reorder.move(l, 1, 1))
        assertEquals(l, Reorder.move(l, -1, 2))
        assertEquals(l, Reorder.moveUp(l, 0))
        assertEquals(l, Reorder.moveDown(l, 3))
        assertEquals(listOf("a", "c", "b", "d"), Reorder.moveDown(l, 1))
        assertEquals(listOf("b", "a", "c", "d"), Reorder.moveUp(l, 1))
        assertEquals(listOf("a", "c", "d", "b"), Reorder.moveByKey(l, "b", "d") { it })
    }

    @Test
    fun validationRules() {
        val ok = RoutineDraft(null, "  Weekend  ", RoutineTheme.MORNING, listOf(StepDraft("1", "Wave", "", "star", null)))
        assertEquals(emptyList<DraftError>(), DraftValidator.validate(ok, listOf("Morning"), 1))
        assertTrue(DraftValidator.validate(ok.copy(name = "   "), emptyList(), 0).contains(DraftError.NameBlank))
        assertTrue(DraftValidator.validate(ok.copy(name = "x".repeat(31)), emptyList(), 0).contains(DraftError.NameTooLong))
        assertTrue(DraftValidator.validate(ok.copy(name = "MORNING "), listOf("Morning"), 1).contains(DraftError.NameDuplicate))
        assertTrue(DraftValidator.validate(ok.copy(steps = emptyList()), emptyList(), 0).contains(DraftError.NoSteps))
        val many = (1..21).map { StepDraft("$it", "Step", "", "star", null) }
        assertTrue(DraftValidator.validate(ok.copy(steps = many), emptyList(), 0).contains(DraftError.TooManySteps))
        assertTrue(DraftValidator.validate(ok, emptyList(), 12).contains(DraftError.TooManyRoutines))
        // Repeated step labels are allowed.
        val repeated = ok.copy(steps = listOf(StepDraft("1", "Brush", "", "star", null), StepDraft("2", "Brush", "", "star", null)))
        assertEquals(emptyList<DraftError>(), DraftValidator.validate(repeated, emptyList(), 0))
        // Timer range 15 s .. 30 min.
        assertEquals(1, DraftValidator.validateStep(StepDraft("t", "A", "", "star", 14)).size)
        assertEquals(0, DraftValidator.validateStep(StepDraft("t", "A", "", "star", 15)).size)
        assertEquals(0, DraftValidator.validateStep(StepDraft("t", "A", "", "star", 1800)).size)
        assertEquals(1, DraftValidator.validateStep(StepDraft("t", "A", "", "star", 1801)).size)
        assertEquals(1, DraftValidator.validateStep(StepDraft("t", "A", "x".repeat(121), "star", null)).size)
        assertEquals(1, DraftValidator.validateStep(StepDraft("t", "x".repeat(41), "", "star", null)).size)
    }

    @Test
    fun uniqueNamesIgnoreCaseAndRespectLength() {
        assertEquals("Morning", RoutineNames.unique("Morning", listOf("Evening")))
        assertEquals("Morning (2)", RoutineNames.unique("Morning", listOf("morning")))
        assertEquals("Morning (3)", RoutineNames.unique("Morning", listOf("Morning", "Morning (2)")))
        val long = "x".repeat(30)
        val unique = RoutineNames.unique(long, listOf(long))
        assertTrue(unique.length <= 30)
        assertTrue(unique.endsWith("(2)"))
    }
}
