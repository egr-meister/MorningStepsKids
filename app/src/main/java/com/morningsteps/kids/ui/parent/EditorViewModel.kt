package com.morningsteps.kids.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.SaveResult
import com.morningsteps.kids.domain.routines.DraftError
import com.morningsteps.kids.domain.routines.DraftValidator
import com.morningsteps.kids.domain.routines.Limits
import com.morningsteps.kids.domain.routines.Reorder
import com.morningsteps.kids.domain.routines.RoutineDraft
import com.morningsteps.kids.domain.routines.RoutineTheme
import com.morningsteps.kids.domain.routines.StepDraft
import com.morningsteps.kids.domain.routines.StepIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: RoutineDraft = RoutineDraft(null, "", RoutineTheme.MORNING, emptyList()),
    val original: RoutineDraft? = null,
    val errors: List<DraftError> = emptyList(),
    val showErrors: Boolean = false,
    /** Step being added or edited in the step pane; null when the list is shown. */
    val editingStep: StepDraft? = null,
    val editingIsNew: Boolean = false,
    val stepErrors: List<DraftError> = emptyList(),
    val confirmRestart: Boolean = false,
    val confirmDiscard: Boolean = false,
    val saving: Boolean = false,
    val closed: Boolean = false,
    val notFound: Boolean = false,
) {
    val dirty: Boolean get() = if (original == null) draft != EMPTY else draft != original

    companion object {
        val EMPTY = RoutineDraft(null, "", RoutineTheme.MORNING, emptyList())
    }
}

class EditorViewModel(
    private val routineId: String?,
    private val repository: RoutineRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (routineId == null) {
                _state.value = EditorUiState(loading = false, isNew = true)
            } else {
                val draft = repository.loadDraft(routineId)
                _state.value = if (draft == null) {
                    EditorUiState(loading = false, isNew = false, notFound = true)
                } else {
                    EditorUiState(loading = false, isNew = false, draft = draft, original = draft)
                }
            }
        }
    }

    private fun updateDraft(transform: (RoutineDraft) -> RoutineDraft) {
        _state.update { s ->
            val draft = transform(s.draft)
            s.copy(draft = draft, errors = if (s.showErrors) localErrors(draft) else emptyList())
        }
    }

    /** Local validation (duplicate names are checked against the database when saving). */
    private fun localErrors(draft: RoutineDraft) = DraftValidator.validate(draft, emptyList(), 0)
        .filterNot { it is DraftError.TooManyRoutines }

    fun setName(name: String) = updateDraft { it.copy(name = name.take(Limits.MAX_ROUTINE_NAME + 10)) }

    fun setTheme(theme: RoutineTheme) = updateDraft { it.copy(theme = theme) }

    fun moveByKey(fromId: String, toId: String) = updateDraft { d ->
        d.copy(steps = Reorder.moveByKey(d.steps, fromId, toId) { it.id })
    }

    fun moveUp(stepId: String) = updateDraft { d ->
        d.copy(steps = Reorder.moveUp(d.steps, d.steps.indexOfFirst { it.id == stepId }))
    }

    fun moveDown(stepId: String) = updateDraft { d ->
        d.copy(steps = Reorder.moveDown(d.steps, d.steps.indexOfFirst { it.id == stepId }))
    }

    fun removeStep(stepId: String) = updateDraft { d -> d.copy(steps = d.steps.filterNot { it.id == stepId }) }

    // ------------------------------------------------------------- step pane

    fun addStep() {
        if (_state.value.draft.steps.size >= Limits.MAX_STEPS) return
        _state.update {
            it.copy(
                editingStep = StepDraft(newId(), "", "", StepIcon.STAR.key, null),
                editingIsNew = true,
                stepErrors = emptyList(),
            )
        }
    }

    fun editStep(stepId: String) {
        val step = _state.value.draft.steps.firstOrNull { it.id == stepId } ?: return
        _state.update { it.copy(editingStep = step, editingIsNew = false, stepErrors = emptyList()) }
    }

    fun updateEditingStep(transform: (StepDraft) -> StepDraft) {
        _state.update { s -> s.editingStep?.let { s.copy(editingStep = transform(it), stepErrors = emptyList()) } ?: s }
    }

    fun applyEditingStep() {
        val s = _state.value
        val step = s.editingStep ?: return
        val cleaned = step.copy(label = step.label.trim(), instruction = step.instruction.trim())
        val errors = DraftValidator.validateStep(cleaned)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(stepErrors = errors) }
            return
        }
        updateDraft { d ->
            val exists = d.steps.any { it.id == cleaned.id }
            d.copy(steps = if (exists) d.steps.map { if (it.id == cleaned.id) cleaned else it } else d.steps + cleaned)
        }
        _state.update { it.copy(editingStep = null, stepErrors = emptyList()) }
    }

    fun cancelEditingStep() {
        _state.update { it.copy(editingStep = null, stepErrors = emptyList()) }
    }

    // ------------------------------------------------------------- save / discard

    fun requestSave() {
        val s = _state.value
        if (s.saving) return
        val errors = localErrors(s.draft)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, showErrors = true) }
            return
        }
        val id = s.draft.id
        viewModelScope.launch {
            if (id != null && repository.hasActiveSession(id)) {
                _state.update { it.copy(confirmRestart = true) }
            } else {
                save()
            }
        }
    }

    fun confirmRestartAndSave() {
        _state.update { it.copy(confirmRestart = false) }
        viewModelScope.launch { save() }
    }

    fun dismissRestart() {
        _state.update { it.copy(confirmRestart = false) }
    }

    private suspend fun save() {
        _state.update { it.copy(saving = true) }
        when (val result = repository.saveDraft(_state.value.draft)) {
            is SaveResult.Saved -> _state.update { it.copy(saving = false, closed = true) }
            is SaveResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors, showErrors = true) }
            SaveResult.NotFound -> _state.update { it.copy(saving = false, notFound = true) }
        }
    }

    /** Back / Cancel: asks before throwing away unsaved changes. */
    fun requestClose() {
        val s = _state.value
        when {
            s.editingStep != null -> cancelEditingStep()
            s.dirty -> _state.update { it.copy(confirmDiscard = true) }
            else -> _state.update { it.copy(closed = true) }
        }
    }

    fun discard() {
        _state.update { it.copy(confirmDiscard = false, closed = true) }
    }

    fun keepEditing() {
        _state.update { it.copy(confirmDiscard = false) }
    }
}
