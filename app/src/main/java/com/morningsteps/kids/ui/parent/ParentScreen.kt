package com.morningsteps.kids.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.R
import com.morningsteps.kids.data.repository.AppSettings
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.SettingsRepository
import com.morningsteps.kids.domain.routines.Limits
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.ui.common.AppScreen
import com.morningsteps.kids.ui.common.ConfirmDialog
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.common.SectionTitle
import com.morningsteps.kids.ui.common.SettingRow
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.common.rememberSnackbarHostState
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.iconRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParentUiState(
    val routines: List<RoutineDetail> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val historyCount: Int = 0,
    val message: String? = null,
)

class ParentViewModel(
    private val repository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ParentUiState> = combine(
        repository.routines,
        settingsRepository.settings,
        repository.history,
        message,
    ) { routines, settings, history, msg ->
        ParentUiState(routines, settings, history.size, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentUiState())

    fun messageShown() {
        message.value = null
    }

    fun duplicate(id: String) = viewModelScope.launch {
        val newId = repository.duplicateRoutine(id)
        message.value = if (newId == null) "You can keep up to ${Limits.MAX_ROUTINES} routines." else "Routine duplicated."
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.deleteRoutine(id)
        message.value = "Routine deleted. Its finished history stays."
    }

    fun move(id: String, up: Boolean) = viewModelScope.launch { repository.moveRoutine(id, up) }

    fun clearHistory() = viewModelScope.launch {
        repository.clearHistory()
        message.value = "History deleted."
    }

    fun setSound(enabled: Boolean) = viewModelScope.launch { settingsRepository.setTimerSound(enabled) }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch { settingsRepository.setReduceMotion(enabled) }

    fun restoreStarters() = viewModelScope.launch {
        val result = repository.restoreStarters()
        message.value = when {
            result.added == 0 -> "No room for more routines (up to ${Limits.MAX_ROUTINES})."
            result.skipped > 0 -> "Added ${result.added} starter routines. ${result.skipped} did not fit."
            else -> "Starter routines added."
        }
    }

    fun clearAll(then: () -> Unit) = viewModelScope.launch {
        repository.clearAllData()
        settingsRepository.clear()
        then()
    }
}

private sealed interface PendingConfirm {
    data class Delete(val id: String, val name: String) : PendingConfirm
    data object ClearHistory : PendingConfirm
    data object Restore : PendingConfirm
    data object ClearAll : PendingConfirm
}

@Composable
fun ParentScreen(
    state: ParentUiState,
    viewModel: ParentViewModel,
    onBack: () -> Unit,
    onEditRoutine: (String?) -> Unit,
    onOpenPrivacy: () -> Unit,
    onAllCleared: () -> Unit,
) {
    val snackbar: SnackbarHostState = rememberSnackbarHostState()
    var pending by rememberSaveable(stateSaver = PendingSaver) { mutableStateOf<PendingConfirm?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    AppScreen(title = "Parents", onBack = onBack, snackbarHostState = snackbar) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "Set up routines and preferences. Everything stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.SlateSoft,
                )
                SectionTitle("Routines")
            }
            items(state.routines, key = { it.routine.id }) { detail ->
                RoutineManageRow(
                    detail = detail,
                    isFirst = state.routines.firstOrNull()?.routine?.id == detail.routine.id,
                    isLast = state.routines.lastOrNull()?.routine?.id == detail.routine.id,
                    canDuplicate = state.routines.size < Limits.MAX_ROUTINES,
                    onEdit = { onEditRoutine(detail.routine.id) },
                    onDuplicate = { viewModel.duplicate(detail.routine.id) },
                    onMoveUp = { viewModel.move(detail.routine.id, up = true) },
                    onMoveDown = { viewModel.move(detail.routine.id, up = false) },
                    onDelete = { pending = PendingConfirm.Delete(detail.routine.id, detail.routine.name) },
                )
            }
            item {
                val canAdd = state.routines.size < Limits.MAX_ROUTINES
                OutlinedButton(
                    onClick = { onEditRoutine(null) },
                    enabled = canAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 52.dp),
                ) {
                    UiIcon(R.drawable.ic_ui_add, null, Modifier.size(20.dp), tint = Palette.Slate)
                    Spacer(Modifier.width(8.dp))
                    Text("New routine", color = Palette.Slate)
                }
                if (!canAdd) {
                    Text(
                        "You can keep up to ${Limits.MAX_ROUTINES} routines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.SlateSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                SectionTitle("Preferences")
                SettingRow(
                    title = "Timer finished sound",
                    subtitle = "A short, soft chime while the step screen is open. Follows the device volume.",
                ) {
                    Switch(
                        checked = state.settings.timerSoundEnabled,
                        onCheckedChange = { viewModel.setSound(it) },
                        modifier = Modifier.semantics { contentDescription = "Timer finished sound" },
                    )
                }
                SettingRow(
                    title = "Reduce motion",
                    subtitle = "Keep movement to a minimum. The system “remove animations” setting is always respected.",
                ) {
                    Switch(
                        checked = state.settings.reduceMotion,
                        onCheckedChange = { viewModel.setReduceMotion(it) },
                        modifier = Modifier.semantics { contentDescription = "Reduce motion" },
                    )
                }

                SectionTitle("History")
                SettingRow(
                    title = "Delete history",
                    subtitle = if (state.historyCount == 0) "No finished routines saved." else "${state.historyCount} finished routines saved.",
                ) {
                    TextButton(onClick = { pending = PendingConfirm.ClearHistory }, enabled = state.historyCount > 0) {
                        Text("Delete")
                    }
                }

                SectionTitle("Privacy and data")
                SettingRow(title = "Privacy", subtitle = "What this app stores and where.") {
                    TextButton(onClick = onOpenPrivacy) { Text("Open") }
                }
                SettingRow(
                    title = "Restore starter routines",
                    subtitle = "Adds Morning, Evening, Before School and After School again. Your routines stay.",
                ) {
                    TextButton(onClick = { pending = PendingConfirm.Restore }) { Text("Restore") }
                }
                SettingRow(
                    title = "Clear all local data",
                    subtitle = "Removes every routine, all progress, history and preferences, then restores the starter routines.",
                ) {
                    TextButton(onClick = { pending = PendingConfirm.ClearAll }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.heightIn(min = 24.dp))
            }
        }
    }

    when (val p = pending) {
        is PendingConfirm.Delete -> ConfirmDialog(
            title = "Delete “${p.name}”?",
            message = "The routine and any unfinished progress will be removed. Finished routines in History stay.",
            confirmLabel = "Delete",
            onConfirm = {
                pending = null
                viewModel.delete(p.id)
            },
            onDismiss = { pending = null },
        )
        PendingConfirm.ClearHistory -> ConfirmDialog(
            title = "Delete history?",
            message = "All finished routines will be removed from History. Routines and current progress stay.",
            confirmLabel = "Delete history",
            onConfirm = {
                pending = null
                viewModel.clearHistory()
            },
            onDismiss = { pending = null },
        )
        PendingConfirm.Restore -> ConfirmDialog(
            title = "Restore starter routines?",
            message = "The four starter routines will be added. Existing routines, progress and history are kept. Names get a number if they are already used.",
            confirmLabel = "Restore",
            onConfirm = {
                pending = null
                viewModel.restoreStarters()
            },
            onDismiss = { pending = null },
        )
        PendingConfirm.ClearAll -> ConfirmDialog(
            title = "Clear all local data?",
            message = "Every routine, all progress, history and preferences will be removed. The app will start again with the starter routines. This cannot be undone.",
            confirmLabel = "Clear everything",
            onConfirm = {
                pending = null
                viewModel.clearAll(onAllCleared)
            },
            onDismiss = { pending = null },
        )
        null -> Unit
    }
}

private val PendingSaver = androidx.compose.runtime.saveable.Saver<PendingConfirm?, String>(
    save = { p ->
        when (p) {
            is PendingConfirm.Delete -> "delete\u0000${p.id}\u0000${p.name}"
            PendingConfirm.ClearHistory -> "history"
            PendingConfirm.Restore -> "restore"
            PendingConfirm.ClearAll -> "clear"
            null -> ""
        }
    },
    restore = { raw ->
        when {
            raw.startsWith("delete\u0000") -> raw.split('\u0000').let { PendingConfirm.Delete(it[1], it.getOrElse(2) { "" }) }
            raw == "history" -> PendingConfirm.ClearHistory
            raw == "restore" -> PendingConfirm.Restore
            raw == "clear" -> PendingConfirm.ClearAll
            else -> null
        }
    },
)

@Composable
private fun RoutineManageRow(
    detail: RoutineDetail,
    isFirst: Boolean,
    isLast: Boolean,
    canDuplicate: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = Palette.Paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Palette.PaperLine),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Illustration(detail.routine.theme.iconRes(), null, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.routine.name, style = MaterialTheme.typography.titleSmall)
                val n = detail.steps.size
                Text(
                    (if (n == 1) "1 step" else "$n steps") + if (detail.inProgress) " · in progress" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.SlateSoft,
                )
            }
            IconButton(onClick = onEdit) { UiIcon(R.drawable.ic_ui_edit, "Edit ${detail.routine.name}") }
            Box {
                IconButton(onClick = { menu = true }) {
                    UiIcon(R.drawable.ic_ui_more_vert, "More actions for ${detail.routine.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_copy, null) },
                        enabled = canDuplicate,
                        onClick = { menu = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_arrow_up, null) },
                        enabled = !isFirst,
                        onClick = { menu = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_arrow_down, null) },
                        enabled = !isLast,
                        onClick = { menu = false; onMoveDown() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Bundled privacy information. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    AppScreen(title = "Privacy", onBack = onBack) { modifier ->
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf(
                "Everything stays on this device" to
                    "Routines, steps, progress, timers, history and preferences are stored only in this app’s private storage on this device.",
                "Nothing is sent anywhere" to
                    "MorningSteps Kids has no account, no servers, no advertising and no analytics. The app does not have internet access, so routines and history are never transmitted.",
                "No backup or transfer" to
                    "App data is excluded from Android cloud backup and from device-to-device transfer. If the app is uninstalled, its data is removed.",
                "No special permissions" to
                    "The app does not use location, contacts, camera, microphone or notifications, and it never asks for permissions.",
                "Removing data" to
                    "Use “Delete history” to remove finished routines, or “Clear all local data” to return the app to its starter routines.",
                "Parent area" to
                    "Holding the Parents button helps prevent accidental changes. It is not a password and does not protect data.",
            ).forEach { (title, body) ->
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.Slate)
                }
            }
        }
    }
}
