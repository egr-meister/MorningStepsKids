package com.morningsteps.kids.domain.routines

/** Visual theme of a routine. The key is persisted, so never rename existing keys. */
enum class RoutineTheme(val key: String, val displayName: String) {
    MORNING("morning", "Morning"),
    EVENING("evening", "Evening"),
    BEFORE_SCHOOL("before_school", "Before School"),
    AFTER_SCHOOL("after_school", "After School");

    companion object {
        fun fromKey(key: String?): RoutineTheme = entries.firstOrNull { it.key == key } ?: MORNING
    }
}

/** Bundled step icons. The key is persisted in steps and history snapshots. */
enum class StepIcon(val key: String, val description: String) {
    WASH_FACE("wash_face", "Washing face"),
    GET_DRESSED("get_dressed", "T-shirt"),
    BREAKFAST("breakfast", "Breakfast bowl"),
    BRUSH_TEETH("brush_teeth", "Toothbrush and tooth"),
    PACK_BAG("pack_bag", "Backpack"),
    BAG_AWAY("bag_away", "Backpack on a hook"),
    SHOES("shoes", "Shoe"),
    TIDY("tidy", "Toy box"),
    LIGHT_OFF("light_off", "Light bulb"),
    PAJAMAS("pajamas", "Moon and stars"),
    HANGER("hanger", "Clothes on a hanger"),
    CHECK_CLOTHES("check_clothes", "Mirror"),
    WATER_BOTTLE("water_bottle", "Water bottle"),
    WASH_HANDS("wash_hands", "Washing hands"),
    SNACK("snack", "Apple"),
    SCHOOL_TASKS("school_tasks", "Notebook and pencil"),
    QUIET_ACTIVITY("quiet_activity", "Open book"),
    STAR("star", "Star");

    companion object {
        fun fromKey(key: String?): StepIcon = entries.firstOrNull { it.key == key } ?: STAR
    }
}

data class Step(
    val id: String,
    val routineId: String,
    val label: String,
    val instruction: String?,
    val iconKey: String,
    val displayOrder: Int,
    val timerDurationSeconds: Int?,
)

data class Routine(
    val id: String,
    val name: String,
    val theme: RoutineTheme,
    val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class TimerStatus {
    /** Configured but not started, or reset. */
    IDLE,
    RUNNING,
    PAUSED,

    /** Paused automatically because the device restarted (or the clock reference became invalid). */
    PAUSED_AFTER_RESTART,
    FINISHED;

    companion object {
        fun fromName(name: String?): TimerStatus = entries.firstOrNull { it.name == name } ?: IDLE
    }
}

/** Persisted timer checkpoint. Remaining time is derived from the monotonic clock, never decremented. */
data class TimerState(
    val sessionId: String,
    val stepId: String,
    val status: TimerStatus,
    val remainingAtCheckpointMillis: Long,
    val checkpointElapsedRealtimeMillis: Long,
    val bootReference: String?,
)

data class Session(
    val id: String,
    val routineId: String,
    val startedAt: Long,
    val updatedAt: Long,
    val completedStepIds: Set<String>,
    val timers: Map<String, TimerState>,
)

/** Everything the UI needs to know about one routine. */
data class RoutineDetail(
    val routine: Routine,
    val steps: List<Step>,
    val session: Session?,
) {
    val completedCount: Int get() = Progress.completedCount(steps, session?.completedStepIds.orEmpty())
    val progress: Float get() = Progress.fraction(steps, session?.completedStepIds.orEmpty())
    val allComplete: Boolean get() = Progress.allComplete(steps, session?.completedStepIds.orEmpty())
    val nextSuggestedStep: Step? get() = Progress.nextSuggested(steps, session?.completedStepIds.orEmpty())
    val inProgress: Boolean get() = session != null
    fun isCompleted(stepId: String): Boolean = session?.completedStepIds?.contains(stepId) == true
}

data class HistoryEntry(
    val id: Long,
    val routineName: String,
    val themeKey: String,
    val completedAt: Long,
    val totalSteps: Int,
)

data class HistoryStep(
    val label: String,
    val instruction: String?,
    val iconKey: String,
    val displayOrder: Int,
)

object Limits {
    const val MAX_ROUTINES = 12
    const val MIN_STEPS = 1
    const val MAX_STEPS = 20
    const val MAX_ROUTINE_NAME = 30
    const val MAX_STEP_LABEL = 40
    const val MAX_INSTRUCTION = 120
    const val MIN_TIMER_SECONDS = 15
    const val MAX_TIMER_SECONDS = 30 * 60
    const val MAX_HISTORY = 100
}
