package com.morningsteps.kids.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.R
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.domain.routines.HistoryEntry
import com.morningsteps.kids.domain.routines.HistoryStep
import com.morningsteps.kids.domain.routines.RoutineTheme
import com.morningsteps.kids.ui.common.AppScreen
import com.morningsteps.kids.ui.common.EmptyMessage
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.iconRes
import com.morningsteps.kids.ui.theme.stepIconRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

data class HistoryListState(val loading: Boolean = true, val entries: List<HistoryEntry> = emptyList())

class HistoryViewModel(repository: RoutineRepository) : ViewModel() {
    val state: StateFlow<HistoryListState> = repository.history
        .map { HistoryListState(loading = false, entries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryListState())
}

data class HistoryDetailState(val loading: Boolean = true, val entry: HistoryEntry? = null, val steps: List<HistoryStep> = emptyList())

class HistoryDetailViewModel(id: Long, repository: RoutineRepository) : ViewModel() {
    val state: StateFlow<HistoryDetailState> = combine(repository.historyEntry(id), repository.historySteps(id)) { e, s ->
        HistoryDetailState(loading = false, entry = e, steps = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailState())
}

/** Local date and time, in the user's locale and time zone. */
fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
fun HistoryScreen(state: HistoryListState, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    AppScreen(title = "History", onBack = onBack) { modifier ->
        if (!state.loading && state.entries.isEmpty()) {
            EmptyMessage("Finished routines will appear here.", modifier)
            return@AppScreen
        }
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                Surface(
                    color = Palette.Paper,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Palette.PaperLine),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "show steps") { onOpen(entry.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Illustration(RoutineTheme.fromKey(entry.themeKey).iconRes(), null, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.routineName, style = MaterialTheme.typography.titleMedium)
                            Text(formatDateTime(entry.completedAt), style = MaterialTheme.typography.bodyMedium, color = Palette.SlateSoft)
                            Text(
                                if (entry.totalSteps == 1) "1 step completed" else "${entry.totalSteps} steps completed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.SlateSoft,
                            )
                        }
                        UiIcon(R.drawable.ic_ui_chevron_right, null, Modifier.size(24.dp), tint = Palette.SlateSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(state: HistoryDetailState, onBack: () -> Unit) {
    val entry = state.entry
    AppScreen(title = entry?.routineName ?: "History", onBack = onBack) { modifier ->
        if (entry == null) {
            if (!state.loading) EmptyMessage("This entry was deleted.", modifier)
            return@AppScreen
        }
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Finished ${formatDateTime(entry.completedAt)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "${entry.totalSteps} of ${entry.totalSteps} steps completed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.SlateSoft,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(state.steps, key = { it.displayOrder }) { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Illustration(stepIconRes(step.iconKey), null, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${step.displayOrder + 1}. ${step.label}", style = MaterialTheme.typography.bodyLarge)
                        if (!step.instruction.isNullOrBlank()) {
                            Text(step.instruction, style = MaterialTheme.typography.bodyMedium, color = Palette.SlateSoft)
                        }
                    }
                    UiIcon(R.drawable.ic_ui_check, "completed", Modifier.size(22.dp), tint = Palette.Slate)
                }
            }
        }
    }
}
