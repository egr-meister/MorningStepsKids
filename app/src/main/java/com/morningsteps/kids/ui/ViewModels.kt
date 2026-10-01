package com.morningsteps.kids.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.morningsteps.kids.AppContainer
import com.morningsteps.kids.ui.history.HistoryDetailViewModel
import com.morningsteps.kids.ui.history.HistoryViewModel
import com.morningsteps.kids.ui.parent.EditorViewModel
import com.morningsteps.kids.ui.parent.ParentViewModel
import com.morningsteps.kids.ui.path.FinishViewModel
import com.morningsteps.kids.ui.path.PathViewModel
import com.morningsteps.kids.ui.path.RoutinePickerViewModel
import com.morningsteps.kids.ui.step.StepViewModel

/** Manual-DI helper: builds a factory for a single ViewModel type. */
inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }

/** All ViewModel factories, created outside of composition. */
class ViewModelFactories(private val c: AppContainer) {
    fun path() = simpleFactory { PathViewModel(c.routines, c.settings, c.clock) }
    fun picker() = simpleFactory { RoutinePickerViewModel(c.routines, c.settings) }
    fun step(routineId: String, stepId: String) =
        simpleFactory { StepViewModel(routineId, stepId, c.routines, c.settings, c.clock, c.chime) }
    fun finish(routineId: String) = simpleFactory { FinishViewModel(routineId, c.routines) }
    fun history() = simpleFactory { HistoryViewModel(c.routines) }
    fun historyDetail(id: Long) = simpleFactory { HistoryDetailViewModel(id, c.routines) }
    fun parent() = simpleFactory { ParentViewModel(c.routines, c.settings) }
    fun editor(routineId: String?) = simpleFactory { EditorViewModel(routineId, c.routines) }
}
