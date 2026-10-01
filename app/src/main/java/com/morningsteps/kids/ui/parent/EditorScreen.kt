package com.morningsteps.kids.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardOptions
import com.morningsteps.kids.R
import com.morningsteps.kids.domain.routines.DraftError
import com.morningsteps.kids.domain.routines.DraftValidator
import com.morningsteps.kids.domain.routines.Limits
import com.morningsteps.kids.domain.routines.RoutineTheme
import com.morningsteps.kids.domain.routines.StepDraft
import com.morningsteps.kids.domain.routines.StepIcon
import com.morningsteps.kids.ui.common.AppScreen
import com.morningsteps.kids.ui.common.ConfirmDialog
import com.morningsteps.kids.ui.common.EmptyMessage
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.common.SectionTitle
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.common.formatDuration
import com.morningsteps.kids.ui.theme.LocalReduceMotion
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.colors
import com.morningsteps.kids.ui.theme.drawableRes
import com.morningsteps.kids.ui.theme.iconRes
import com.morningsteps.kids.ui.theme.stepIconRes

@Composable
fun EditorScreen(state: EditorUiState, viewModel: EditorViewModel, onClosed: () -> Unit) {
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    // Back: closes the step pane, or asks before discarding unsaved changes. Predictive-back compatible.
    BackHandler(enabled = !state.closed && (state.editingStep != null || state.dirty)) { viewModel.requestClose() }

    val editing = state.editingStep
    if (editing != null) {
        StepPane(
            step = editing,
            isNew = state.editingIsNew,
            errors = state.stepErrors,
            onChange = viewModel::updateEditingStep,
            onApply = viewModel::applyEditingStep,
            onCancel = viewModel::cancelEditingStep,
        )
    } else {
        RoutinePane(state, viewModel)
    }

    if (state.confirmRestart) {
        ConfirmDialog(
            title = "Restart the current session?",
            message = "“${state.original?.name ?: state.draft.name}” has unfinished progress. Saving these changes restarts it, so its checkmarks and timers are cleared. History is not changed.",
            confirmLabel = "Save and restart",
            onConfirm = viewModel::confirmRestartAndSave,
            onDismiss = viewModel::dismissRestart,
        )
    }
    if (state.confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your changes to this routine have not been saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = viewModel::discard,
            onDismiss = viewModel::keepEditing,
        )
    }
}

@Composable
private fun RoutinePane(state: EditorUiState, viewModel: EditorViewModel) {
    val draft = state.draft
    val title = if (state.isNew) "New routine" else "Edit routine"
    AppScreen(
        title = title,
        onBack = viewModel::requestClose,
        actions = {
            TextButton(onClick = viewModel::requestClose) { Text("Cancel") }
            TextButton(onClick = viewModel::requestSave, enabled = !state.loading && !state.saving && !state.notFound) {
                Text("Save")
            }
        },
    ) { modifier ->
        if (state.notFound) {
            EmptyMessage("This routine no longer exists.", modifier)
            return@AppScreen
        }
        if (state.loading) return@AppScreen

        val listState = rememberLazyListState()
        val stepIds = draft.steps.map { it.id }.toSet()
        val reorder = rememberReorderState(
            listState = listState,
            canMove = { key -> key is String && key in stepIds },
            onMove = { from, to -> viewModel.moveByKey(from as String, to as String) },
        )
        val reduceMotion = LocalReduceMotion.current
        val nameError = state.errors.firstOrNull {
            it is DraftError.NameBlank || it is DraftError.NameTooLong || it is DraftError.NameDuplicate
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Column {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = viewModel::setName,
                        label = { Text("Routine name") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = {
                            Text(nameError?.let(DraftValidator::message) ?: "${draft.name.trim().length} / ${Limits.MAX_ROUTINE_NAME}")
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SectionTitle("Look")
                    ThemeChooser(selected = draft.theme, onSelect = viewModel::setTheme)
                    SectionTitle("Steps (${draft.steps.size} of ${Limits.MAX_STEPS})")
                    Text(
                        "Drag the handle to change the order, or use Move up and Move down.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.SlateSoft,
                    )
                }
            }
            itemsIndexed(draft.steps, key = { _, s -> s.id }) { index, step ->
                val dragging = reorder.isDragging(step.id)
                val itemModifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = reorder.draggingOffset }
                } else {
                    Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = if (reduceMotion) null else androidx.compose.animation.core.spring<IntOffset>(stiffness = 600f),
                    )
                }
                StepEditorRow(
                    step = step,
                    index = index,
                    count = draft.steps.size,
                    dragging = dragging,
                    reorder = reorder,
                    hasError = state.errors.any { e ->
                        (e is DraftError.StepLabelBlank && e.stepId == step.id) ||
                            (e is DraftError.StepLabelTooLong && e.stepId == step.id) ||
                            (e is DraftError.InstructionTooLong && e.stepId == step.id) ||
                            (e is DraftError.TimerOutOfRange && e.stepId == step.id)
                    },
                    onEdit = { viewModel.editStep(step.id) },
                    onMoveUp = { viewModel.moveUp(step.id) },
                    onMoveDown = { viewModel.moveDown(step.id) },
                    onRemove = { viewModel.removeStep(step.id) },
                    modifier = itemModifier,
                )
            }
            item(key = "footer") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val canAdd = draft.steps.size < Limits.MAX_STEPS
                    OutlinedButton(
                        onClick = viewModel::addStep,
                        enabled = canAdd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        UiIcon(R.drawable.ic_ui_add, null, Modifier.size(20.dp), tint = Palette.Slate)
                        Spacer(Modifier.width(8.dp))
                        Text("Add step", color = Palette.Slate)
                    }
                    val listErrors = state.errors.filter {
                        it is DraftError.NoSteps || it is DraftError.TooManySteps || it is DraftError.TooManyRoutines ||
                            it is DraftError.StepLabelBlank || it is DraftError.StepLabelTooLong ||
                            it is DraftError.InstructionTooLong || it is DraftError.TimerOutOfRange
                    }.map(DraftValidator::message).distinct()
                    listErrors.forEach {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.heightIn(min = 32.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeChooser(selected: RoutineTheme, onSelect: (RoutineTheme) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoutineTheme.entries.forEach { theme ->
            val isSelected = theme == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.colors().soft)
                    .border(BorderStroke(if (isSelected) 3.dp else 1.dp, Palette.Slate.copy(alpha = if (isSelected) 1f else 0.3f)), RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(theme) })
                    .heightIn(min = 72.dp)
                    .padding(6.dp),
            ) {
                Illustration(theme.iconRes(), null, 32.dp)
                Text(theme.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun StepEditorRow(
    step: StepDraft,
    index: Int,
    count: Int,
    dragging: Boolean,
    reorder: ReorderState,
    hasError: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val actions = buildList {
        if (index > 0) add(CustomAccessibilityAction("Move up") { onMoveUp(); true })
        if (index < count - 1) add(CustomAccessibilityAction("Move down") { onMoveDown(); true })
        add(CustomAccessibilityAction("Edit") { onEdit(); true })
        add(CustomAccessibilityAction("Remove") { onRemove(); true })
    }
    val timerText = step.timerSeconds?.let { "Timer ${formatDuration(it)}" } ?: "No timer"
    Surface(
        color = if (dragging) Palette.Stone else Palette.Paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (dragging || hasError) 2.dp else 1.dp,
            when {
                hasError -> MaterialTheme.colorScheme.error
                dragging -> Palette.Highlight
                else -> Palette.PaperLine
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(16.dp)) else Modifier)
            .semantics {
                contentDescription = "Step ${index + 1} of $count: ${step.label.ifBlank { "untitled" }}. $timerText."
                customActions = actions
            },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Visible drag handle (the only place a drag can start).
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .reorderHandle(reorder, step.id)
                    .semantics { contentDescription = "Drag to reorder ${step.label}" },
                contentAlignment = Alignment.Center,
            ) {
                UiIcon(R.drawable.ic_ui_drag_handle, null, tint = Palette.SlateSoft)
            }
            Illustration(stepIconRes(step.iconKey), null, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "edit step", onClick = onEdit)
                    .padding(vertical = 8.dp),
            ) {
                Text("${index + 1}. ${step.label.ifBlank { "Untitled step" }}", style = MaterialTheme.typography.titleSmall)
                Text(timerText, style = MaterialTheme.typography.bodySmall, color = Palette.SlateSoft)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    UiIcon(R.drawable.ic_ui_more_vert, "Actions for ${step.label}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_arrow_up, null) },
                        enabled = index > 0,
                        onClick = { menu = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_arrow_down, null) },
                        enabled = index < count - 1,
                        onClick = { menu = false; onMoveDown() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_delete, null) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPane(
    step: StepDraft,
    isNew: Boolean,
    errors: List<DraftError>,
    onChange: ((StepDraft) -> StepDraft) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    AppScreen(
        title = if (isNew) "Add step" else "Edit step",
        onBack = onCancel,
        actions = {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onApply) { Text(if (isNew) "Add" else "Done") }
        },
    ) { modifier ->
        val labelError = errors.firstOrNull { it is DraftError.StepLabelBlank || it is DraftError.StepLabelTooLong }
        val instructionError = errors.firstOrNull { it is DraftError.InstructionTooLong }
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = step.label,
                onValueChange = { v -> onChange { it.copy(label = v.take(Limits.MAX_STEP_LABEL + 10)) } },
                label = { Text("Step label") },
                singleLine = true,
                isError = labelError != null,
                supportingText = {
                    Text(labelError?.let(DraftValidator::message) ?: "${step.label.trim().length} / ${Limits.MAX_STEP_LABEL}")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = step.instruction,
                onValueChange = { v -> onChange { it.copy(instruction = v.take(Limits.MAX_INSTRUCTION + 10)) } },
                label = { Text("Instruction (optional)") },
                minLines = 2,
                isError = instructionError != null,
                supportingText = {
                    Text(instructionError?.let(DraftValidator::message) ?: "${step.instruction.trim().length} / ${Limits.MAX_INSTRUCTION}")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Icon")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepIcon.entries.forEach { icon ->
                    val selected = icon.key == step.iconKey
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Palette.Sky else Palette.Paper)
                            .border(
                                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Palette.Slate else Palette.PaperLine),
                                RoundedCornerShape(14.dp),
                            )
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange { it.copy(iconKey = icon.key) } })
                            .semantics { contentDescription = icon.description },
                        contentAlignment = Alignment.Center,
                    ) {
                        Illustration(icon.drawableRes(), null, 44.dp)
                    }
                }
            }

            SectionTitle("Optional timer")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Use a timer", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A gentle pacing aid. It never completes the step on its own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.SlateSoft,
                    )
                }
                Switch(
                    checked = step.timerSeconds != null,
                    onCheckedChange = { on -> onChange { it.copy(timerSeconds = if (on) 120 else null) } },
                    modifier = Modifier.semantics { contentDescription = "Use a timer" },
                )
            }
            step.timerSeconds?.let { seconds ->
                DurationPicker(seconds = seconds, onChange = { s -> onChange { it.copy(timerSeconds = s) } })
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }
}

@Composable
private fun DurationPicker(seconds: Int, onChange: (Int) -> Unit) {
    val minutes = seconds / 60
    val secs = seconds % 60
    fun clamp(v: Int) = v.coerceIn(Limits.MIN_TIMER_SECONDS, Limits.MAX_TIMER_SECONDS)
    Surface(color = Palette.Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Palette.PaperLine)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatDuration(seconds),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = "Timer length ${formatDuration(seconds)}" },
            )
            StepperRow(
                label = "Minutes",
                value = minutes.toString(),
                onMinus = { onChange(clamp(seconds - 60)) },
                onPlus = { onChange(clamp(seconds + 60)) },
                minusEnabled = seconds - 60 >= Limits.MIN_TIMER_SECONDS,
                plusEnabled = seconds + 60 <= Limits.MAX_TIMER_SECONDS,
            )
            StepperRow(
                label = "Seconds",
                value = "%02d".format(java.util.Locale.ROOT, secs),
                onMinus = { onChange(clamp(seconds - 15)) },
                onPlus = { onChange(clamp(seconds + 15)) },
                minusEnabled = seconds - 15 >= Limits.MIN_TIMER_SECONDS,
                plusEnabled = seconds + 15 <= Limits.MAX_TIMER_SECONDS,
            )
            Text(
                "From 15 seconds to 30 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.SlateSoft,
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilterChip(
            selected = false,
            onClick = onMinus,
            enabled = minusEnabled,
            label = { Text("−", style = MaterialTheme.typography.titleLarge) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Less $label".lowercase().replaceFirstChar { it.uppercase() } },
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .width(56.dp)
                .padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilterChip(
            selected = false,
            onClick = onPlus,
            enabled = plusEnabled,
            label = { Text("+", style = MaterialTheme.typography.titleLarge) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "More $label".lowercase().replaceFirstChar { it.uppercase() } },
        )
    }
}
