package com.morningsteps.kids.ui.path

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.R
import com.morningsteps.kids.data.repository.FinishResult
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.domain.routines.HistoryEntry
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.ui.common.AppScreen
import com.morningsteps.kids.ui.common.EmptyMessage
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.stepIconRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinishUiState(
    val loading: Boolean = true,
    val detail: RoutineDetail? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

/** One-shot outcome of finishing; consumed by the screen to navigate. */
sealed interface FinishEvent {
    data class Saved(val historyId: Long) : FinishEvent
    data object Gone : FinishEvent
}

class FinishViewModel(private val routineId: String, private val repository: RoutineRepository) : ViewModel() {
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val _event = MutableStateFlow<FinishEvent?>(null)
    val event: StateFlow<FinishEvent?> = _event.asStateFlow()

    val state: StateFlow<FinishUiState> = combine(
        repository.routine(routineId),
        saving,
        message,
    ) { detail, isSaving, msg ->
        FinishUiState(loading = false, detail = detail, saving = isSaving, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinishUiState())

    /** Protected against duplicate taps here and, atomically, in the repository transaction. */
    fun finish() {
        if (saving.value || _event.value != null) return
        saving.value = true
        viewModelScope.launch {
            when (val result = repository.finishRoutine(routineId)) {
                is FinishResult.Finished -> _event.value = FinishEvent.Saved(result.historyId)
                FinishResult.AlreadyFinished, FinishResult.NotFound -> _event.value = FinishEvent.Gone
                FinishResult.NotComplete -> {
                    message.value = "Some steps are not done yet."
                    saving.value = false
                }
            }
        }
    }
}

@Composable
fun FinishScreen(
    viewModel: FinishViewModel,
    state: FinishUiState,
    event: FinishEvent?,
    onBackToPath: () -> Unit,
    onSaved: (historyId: Long) -> Unit,
) {
    LaunchedEffect(event) {
        when (event) {
            is FinishEvent.Saved -> onSaved(event.historyId)
            FinishEvent.Gone -> onBackToPath()
            null -> Unit
        }
    }
    val detail = state.detail
    AppScreen(title = "Finish routine", onBack = onBackToPath) { modifier ->
        if (detail == null) {
            if (!state.loading) EmptyMessage("This routine is no longer here.", modifier)
            return@AppScreen
        }
        Column(modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        detail.routine.name,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "${detail.completedCount} of ${detail.steps.size} steps done",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.SlateSoft,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(detail.steps, key = { _, s -> s.id }) { i, step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Illustration(stepIconRes(step.iconKey), null, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("${i + 1}. ${step.label}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (detail.isCompleted(step.id)) {
                            UiIcon(R.drawable.ic_ui_check, "done", Modifier.size(24.dp), tint = Palette.Slate)
                        } else {
                            Text("not done", style = MaterialTheme.typography.bodyMedium, color = Palette.SlateSoft)
                        }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Button(
                    onClick = { viewModel.finish() },
                    enabled = detail.allComplete && !state.saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) { Text("Finish routine") }
                OutlinedButton(
                    onClick = onBackToPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Back to path", color = Palette.Slate) }
            }
        }
    }
}

/** Quiet completion screen shown after a finished routine was saved. */
@Composable
fun CompleteScreen(
    entry: HistoryEntry?,
    onBackToRoutines: () -> Unit,
    onStartAgain: () -> Unit,
) {
    AppScreen(title = "", onBack = onBackToRoutines) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Illustration(R.drawable.ic_launcher_foreground, null, 180.dp)
            Text(
                "Your routine is complete.",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (entry != null) {
                Text(
                    "${entry.routineName} · ${entry.totalSteps} steps",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.SlateSoft,
                )
            }
            Button(
                onClick = onBackToRoutines,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text("Back to routines") }
            TextButton(onClick = onStartAgain, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Start again", color = Palette.Slate)
            }
        }
    }
}
