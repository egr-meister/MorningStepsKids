package com.morningsteps.kids.ui.path

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningsteps.kids.data.repository.RoutineRepository
import com.morningsteps.kids.data.repository.SettingsRepository
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.ui.common.AppScreen
import com.morningsteps.kids.ui.common.EmptyMessage
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.colors
import com.morningsteps.kids.ui.theme.iconRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickerUiState(val loading: Boolean = true, val routines: List<RoutineDetail> = emptyList(), val selectedId: String? = null)

class RoutinePickerViewModel(repository: RoutineRepository, private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<PickerUiState> = combine(repository.routines, settings.settings) { routines, s ->
        PickerUiState(
            loading = false,
            routines = routines,
            selectedId = routines.firstOrNull { it.routine.id == s.selectedRoutineId }?.routine?.id
                ?: routines.firstOrNull()?.routine?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState())

    /** Switching routines never touches the progress of either routine. */
    fun select(id: String, then: () -> Unit) {
        viewModelScope.launch {
            settings.selectRoutine(id)
            then()
        }
    }
}

@Composable
fun RoutinePickerScreen(state: PickerUiState, onBack: () -> Unit, onSelect: (String) -> Unit) {
    AppScreen(title = "Choose a routine", onBack = onBack) { modifier ->
        if (!state.loading && state.routines.isEmpty()) {
            EmptyMessage("No routines yet. Ask a parent to create one.", modifier)
        } else {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.routines, key = { it.routine.id }) { detail ->
                    RouteSignRow(detail, selected = detail.routine.id == state.selectedId, onSelect = { onSelect(detail.routine.id) })
                }
            }
        }
    }
}

private val RowSignShape = GenericShape { size, _ ->
    val tip = size.height * 0.32f
    val r = 18f
    moveTo(r, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(r, size.height)
    quadraticTo(0f, size.height, 0f, size.height - r)
    lineTo(0f, r)
    quadraticTo(0f, 0f, r, 0f)
    close()
}

@Composable
private fun RouteSignRow(detail: RoutineDetail, selected: Boolean, onSelect: () -> Unit) {
    val colors = detail.routine.theme.colors()
    val count = detail.steps.size
    val stepsText = if (count == 1) "1 step" else "$count steps"
    val description = buildString {
        append("${detail.routine.name}, $stepsText")
        if (detail.inProgress) append(", in progress")
        if (selected) append(", currently shown")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RowSignShape)
            .background(colors.soft)
            .border(BorderStroke(if (selected) 3.dp else 2.dp, Palette.Slate), RowSignShape)
            .clickable(role = Role.Button, onClickLabel = "show this routine", onClick = onSelect)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
                onClick(label = "show this routine") {
                    onSelect()
                    true
                }
            }
            .padding(start = 16.dp, end = 40.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Illustration(detail.routine.theme.iconRes(), null, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(detail.routine.name, style = MaterialTheme.typography.titleLarge, color = Palette.Slate)
            Text(stepsText, style = MaterialTheme.typography.bodyLarge, color = Palette.SlateSoft)
        }
        if (detail.inProgress) {
            Text(
                "In progress",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Slate,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Paper)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
