package com.morningsteps.kids.domain.routines

data class StarterStep(val label: String, val icon: StepIcon)
data class StarterRoutine(val name: String, val theme: RoutineTheme, val steps: List<StarterStep>)

/** The four starter routines. All starter timers are disabled and no instructions are prescribed. */
object StarterRoutines {
    val all: List<StarterRoutine> = listOf(
        StarterRoutine(
            "Morning", RoutineTheme.MORNING,
            listOf(
                StarterStep("Wash your face", StepIcon.WASH_FACE),
                StarterStep("Get dressed", StepIcon.GET_DRESSED),
                StarterStep("Eat breakfast", StepIcon.BREAKFAST),
                StarterStep("Brush your teeth", StepIcon.BRUSH_TEETH),
            ),
        ),
        StarterRoutine(
            "Evening", RoutineTheme.EVENING,
            listOf(
                StarterStep("Put things away", StepIcon.TIDY),
                StarterStep("Put on pajamas", StepIcon.PAJAMAS),
                StarterStep("Brush your teeth", StepIcon.BRUSH_TEETH),
                StarterStep("Prepare tomorrow’s clothes", StepIcon.HANGER),
                StarterStep("Turn off the light", StepIcon.LIGHT_OFF),
            ),
        ),
        StarterRoutine(
            "Before School", RoutineTheme.BEFORE_SCHOOL,
            listOf(
                StarterStep("Check your clothes", StepIcon.CHECK_CLOTHES),
                StarterStep("Pack your bag", StepIcon.PACK_BAG),
                StarterStep("Take your water bottle", StepIcon.WATER_BOTTLE),
                StarterStep("Put on your shoes", StepIcon.SHOES),
            ),
        ),
        StarterRoutine(
            "After School", RoutineTheme.AFTER_SCHOOL,
            listOf(
                StarterStep("Put your bag away", StepIcon.BAG_AWAY),
                StarterStep("Wash your hands", StepIcon.WASH_HANDS),
                StarterStep("Have a snack", StepIcon.SNACK),
                StarterStep("Check your school tasks", StepIcon.SCHOOL_TASKS),
                StarterStep("Choose a quiet activity", StepIcon.QUIET_ACTIVITY),
            ),
        ),
    )
}

object RoutineNames {
    /** Case-insensitive, trimmed comparison key. */
    fun key(name: String): String = name.trim().lowercase()

    /**
     * Returns [base] if unused, otherwise "base (2)", "base (3)", ... trimmed to the name limit.
     */
    fun unique(base: String, existing: Collection<String>): String {
        val taken = existing.map { key(it) }.toSet()
        val trimmed = base.trim().take(Limits.MAX_ROUTINE_NAME)
        if (key(trimmed) !in taken) return trimmed
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = trimmed.take(Limits.MAX_ROUTINE_NAME - suffix.length).trimEnd() + suffix
            if (key(candidate) !in taken) return candidate
            n++
        }
    }
}
