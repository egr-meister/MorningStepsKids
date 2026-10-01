package com.morningsteps.kids.data.repository

import com.morningsteps.kids.data.local.RoutineEntity
import com.morningsteps.kids.data.local.StepEntity
import com.morningsteps.kids.domain.routines.RoutineNames
import com.morningsteps.kids.domain.routines.StarterRoutines

/** Builds rows for the starter routines, avoiding name clashes with [existingNames]. */
object StarterSeed {
    data class Rows(val routines: List<RoutineEntity>, val steps: List<StepEntity>, val skipped: Int)

    fun build(
        existingNames: Collection<String>,
        firstDisplayOrder: Int,
        now: Long,
        newId: () -> String,
        maxRoutines: Int,
    ): Rows {
        val names = existingNames.toMutableList()
        val routines = mutableListOf<RoutineEntity>()
        val steps = mutableListOf<StepEntity>()
        var skipped = 0
        StarterRoutines.all.forEachIndexed { index, starter ->
            if (routines.size >= maxRoutines) {
                skipped++
                return@forEachIndexed
            }
            val routineId = newId()
            val name = RoutineNames.unique(starter.name, names)
            names += name
            routines += RoutineEntity(
                id = routineId,
                name = name,
                themeKey = starter.theme.key,
                displayOrder = firstDisplayOrder + index,
                createdAt = now,
                updatedAt = now,
            )
            starter.steps.forEachIndexed { stepIndex, s ->
                steps += StepEntity(
                    id = newId(),
                    routineId = routineId,
                    label = s.label,
                    instruction = null,
                    iconKey = s.icon.key,
                    displayOrder = stepIndex,
                    timerDurationSeconds = null,
                )
            }
        }
        return Rows(routines, steps, skipped)
    }
}
