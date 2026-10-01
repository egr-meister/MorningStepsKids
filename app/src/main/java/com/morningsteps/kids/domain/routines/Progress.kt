package com.morningsteps.kids.domain.routines

/** Progress = completed steps / total steps. No points, streaks or targets. */
object Progress {
    fun completedCount(steps: List<Step>, completed: Set<String>): Int = steps.count { it.id in completed }

    fun fraction(steps: List<Step>, completed: Set<String>): Float =
        if (steps.isEmpty()) 0f else completedCount(steps, completed).toFloat() / steps.size

    fun allComplete(steps: List<Step>, completed: Set<String>): Boolean =
        steps.isNotEmpty() && steps.all { it.id in completed }

    /** The order is guidance: the suggestion is simply the first step (in order) that is not done yet. */
    fun nextSuggested(steps: List<Step>, completed: Set<String>): Step? =
        steps.sortedBy { it.displayOrder }.firstOrNull { it.id !in completed }
}

object Reorder {
    /** Moves the element at [from] to index [to], shifting the others. Out-of-range moves are ignored. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val result = list.toMutableList()
        val item = result.removeAt(from)
        result.add(to, item)
        return result
    }

    /** Moves the element with key [fromKey] to the current position of [toKey]. */
    fun <T, K> moveByKey(list: List<T>, fromKey: K, toKey: K, key: (T) -> K): List<T> {
        val from = list.indexOfFirst { key(it) == fromKey }
        val to = list.indexOfFirst { key(it) == toKey }
        return move(list, from, to)
    }

    fun <T> moveUp(list: List<T>, index: Int): List<T> = move(list, index, index - 1)
    fun <T> moveDown(list: List<T>, index: Int): List<T> = move(list, index, index + 1)
}
