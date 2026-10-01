package com.morningsteps.kids.domain.routines

/** Editable copy of a step in the parent editor. [id] is stable across reorders. */
data class StepDraft(
    val id: String,
    val label: String,
    val instruction: String,
    val iconKey: String,
    val timerSeconds: Int?,
)

/** Editable copy of a routine. [id] is null for a routine that has not been saved yet. */
data class RoutineDraft(
    val id: String?,
    val name: String,
    val theme: RoutineTheme,
    val steps: List<StepDraft>,
)

sealed interface DraftError {
    data object NameBlank : DraftError
    data object NameTooLong : DraftError
    data object NameDuplicate : DraftError
    data object NoSteps : DraftError
    data object TooManySteps : DraftError
    data object TooManyRoutines : DraftError
    data class StepLabelBlank(val stepId: String) : DraftError
    data class StepLabelTooLong(val stepId: String) : DraftError
    data class InstructionTooLong(val stepId: String) : DraftError
    data class TimerOutOfRange(val stepId: String) : DraftError
}

object DraftValidator {
    /**
     * @param otherRoutineNames names of all routines except the one being edited.
     * @param routineCountExcludingThis how many routines exist besides this one.
     */
    fun validate(
        draft: RoutineDraft,
        otherRoutineNames: Collection<String>,
        routineCountExcludingThis: Int,
    ): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        val name = draft.name.trim()
        when {
            name.isEmpty() -> errors += DraftError.NameBlank
            name.length > Limits.MAX_ROUTINE_NAME -> errors += DraftError.NameTooLong
            otherRoutineNames.any { RoutineNames.key(it) == RoutineNames.key(name) } -> errors += DraftError.NameDuplicate
        }
        if (draft.id == null && routineCountExcludingThis >= Limits.MAX_ROUTINES) errors += DraftError.TooManyRoutines
        if (draft.steps.size < Limits.MIN_STEPS) errors += DraftError.NoSteps
        if (draft.steps.size > Limits.MAX_STEPS) errors += DraftError.TooManySteps
        draft.steps.forEach { step ->
            errors += validateStep(step)
        }
        return errors
    }

    fun validateStep(step: StepDraft): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        val label = step.label.trim()
        if (label.isEmpty()) errors += DraftError.StepLabelBlank(step.id)
        if (label.length > Limits.MAX_STEP_LABEL) errors += DraftError.StepLabelTooLong(step.id)
        if (step.instruction.trim().length > Limits.MAX_INSTRUCTION) errors += DraftError.InstructionTooLong(step.id)
        val t = step.timerSeconds
        if (t != null && (t < Limits.MIN_TIMER_SECONDS || t > Limits.MAX_TIMER_SECONDS)) {
            errors += DraftError.TimerOutOfRange(step.id)
        }
        return errors
    }

    fun message(error: DraftError): String = when (error) {
        DraftError.NameBlank -> "Please enter a routine name."
        DraftError.NameTooLong -> "Routine names can have up to ${Limits.MAX_ROUTINE_NAME} characters."
        DraftError.NameDuplicate -> "Another routine already has this name."
        DraftError.NoSteps -> "Add at least one step."
        DraftError.TooManySteps -> "A routine can have up to ${Limits.MAX_STEPS} steps."
        DraftError.TooManyRoutines -> "You can keep up to ${Limits.MAX_ROUTINES} routines."
        is DraftError.StepLabelBlank -> "Every step needs a label."
        is DraftError.StepLabelTooLong -> "Step labels can have up to ${Limits.MAX_STEP_LABEL} characters."
        is DraftError.InstructionTooLong -> "Instructions can have up to ${Limits.MAX_INSTRUCTION} characters."
        is DraftError.TimerOutOfRange -> "Timers can be from 15 seconds to 30 minutes."
    }
}
