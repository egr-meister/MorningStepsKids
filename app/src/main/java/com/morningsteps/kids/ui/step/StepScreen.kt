package com.morningsteps.kids.ui.step

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.morningsteps.kids.R
import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.ui.common.AppScreen
import com.morningsteps.kids.ui.common.EmptyMessage
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.common.formatClock
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.colors
import com.morningsteps.kids.ui.theme.stepIconRes

@Composable
fun StepRoute(
    viewModel: StepViewModel,
    state: StepUiState,
    onBack: () -> Unit,
    onOpenStep: (routineId: String, stepId: String) -> Unit,
    onOpenFinish: (routineId: String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.visibleLoop() }
    }
    StepScreen(
        state = state,
        onBack = onBack,
        onDone = { viewModel.markDone() },
        onUndo = { viewModel.undoDone() },
        onStart = { viewModel.startTimer() },
        onPause = { viewModel.pauseTimer() },
        onReset = { viewModel.resetTimer() },
        onConfirmPauseOther = { viewModel.startTimer(pauseOthers = true) },
        onDismissOther = { viewModel.dismissOtherRunning() },
        onOpenNext = { id -> state.detail?.let { onOpenStep(it.routine.id, id) } },
        onOpenFinish = { state.detail?.let { onOpenFinish(it.routine.id) } },
    )
}

@Composable
fun StepScreen(
    state: StepUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onConfirmPauseOther: () -> Unit,
    onDismissOther: () -> Unit,
    onOpenNext: (String) -> Unit,
    onOpenFinish: () -> Unit,
) {
    val detail = state.detail
    val step = state.step
    AppScreen(title = detail?.routine?.name ?: "", onBack = onBack) { modifier ->
        if (state.missing || detail == null || step == null) {
            if (state.missing) EmptyMessage("This step is no longer part of the routine.", modifier)
            return@AppScreen
        }
        val colors = detail.routine.theme.colors()
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Step ${state.position} of ${state.total}",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.SlateSoft,
            )
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(if (state.completed) colors.soft else Palette.Paper)
                    .border(BorderStroke(3.dp, Palette.Slate), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Illustration(stepIconRes(step.iconKey), null, 112.dp)
                if (state.completed) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Palette.Slate),
                        contentAlignment = Alignment.Center,
                    ) { UiIcon(R.drawable.ic_ui_check, null, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.White) }
                }
            }
            Text(
                step.label,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = Palette.Slate,
                modifier = Modifier.semantics { heading() },
            )
            if (!step.instruction.isNullOrBlank()) {
                Text(
                    step.instruction,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                    textAlign = TextAlign.Center,
                    color = Palette.Slate,
                )
            }
            Text(
                if (state.completed) "Done. Nice and steady." else "Take your time.",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.SlateSoft,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (state.completed) {
                OutlinedButton(
                    onClick = onUndo,
                    border = BorderStroke(1.5.dp, Palette.Slate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) { Text("Undo done", color = Palette.Slate) }
            } else {
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp),
                ) {
                    UiIcon(R.drawable.ic_ui_check, null, Modifier.size(22.dp), tint = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Done", fontSize = 20.sp)
                }
            }

            state.timer?.let { timer ->
                TimerCard(
                    timer = timer,
                    stepCompleted = state.completed,
                    accent = colors.deep,
                    onStart = onStart,
                    onPause = onPause,
                    onReset = onReset,
                )
            }

            if (state.completed) {
                when {
                    state.allComplete -> {
                        Text("All steps are done.", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = onOpenFinish,
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                            modifier = Modifier.heightIn(min = 52.dp),
                        ) { Text("Go to Done") }
                    }
                    state.nextStep != null -> {
                        Text("Ready for the next step?", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { onOpenNext(state.nextStep.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .semantics { contentDescription = "Next step: ${state.nextStep.label}" },
                        ) { Text("Next step") }
                    }
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back to path", color = Palette.Slate)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    state.otherRunning?.let { other ->
        AlertDialog(
            onDismissRequest = onDismissOther,
            title = { Text("Another timer is running") },
            text = {
                val where = if (other.stepLabel.isNotBlank()) "“${other.stepLabel}” in ${other.routineName}" else "another step"
                Text("Only one timer runs at a time. Pause the timer for $where and start this one?")
            },
            confirmButton = {
                TextButton(onClick = onConfirmPauseOther) { Text("Pause it and start") }
            },
            dismissButton = { TextButton(onClick = onDismissOther) { Text("Not now") } },
        )
    }
}

@Composable
private fun TimerCard(
    timer: TimerUi,
    stepCompleted: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        color = Palette.Paper,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Palette.PaperLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Optional timer", style = MaterialTheme.typography.titleSmall, color = Palette.SlateSoft)
            val clock = formatClock(timer.remainingMillis)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                val track = Palette.Stone
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        accent, -90f, 360f * timer.fractionRemaining, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    clock,
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Slate,
                    modifier = Modifier.semantics { contentDescription = "Time left $clock" },
                )
            }
            val statusText = when {
                stepCompleted -> "This step is done. Undo done to use the timer again."
                timer.status == TimerStatus.FINISHED -> "Timer finished. Take the time you need."
                timer.status == TimerStatus.PAUSED_AFTER_RESTART -> "The timer was paused because the device restarted."
                timer.status == TimerStatus.PAUSED -> "Timer paused."
                timer.status == TimerStatus.RUNNING -> "Timer running."
                else -> "Start the timer if it helps."
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                when (timer.status) {
                    TimerStatus.RUNNING -> TimerButton("Pause", R.drawable.ic_ui_pause, enabled = true, onClick = onPause)
                    TimerStatus.PAUSED, TimerStatus.PAUSED_AFTER_RESTART ->
                        TimerButton("Resume", R.drawable.ic_ui_play, enabled = !stepCompleted, onClick = onStart)
                    else -> TimerButton("Start", R.drawable.ic_ui_play, enabled = !stepCompleted, onClick = onStart)
                }
                val canReset = timer.status != TimerStatus.IDLE
                OutlinedButton(
                    onClick = onReset,
                    enabled = canReset,
                    border = BorderStroke(1.5.dp, Palette.Slate.copy(alpha = if (canReset) 1f else 0.3f)),
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    UiIcon(R.drawable.ic_ui_reset, null, Modifier.size(20.dp), tint = Palette.Slate.copy(alpha = if (canReset) 1f else 0.4f))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset", color = Palette.Slate.copy(alpha = if (canReset) 1f else 0.4f))
                }
            }
        }
    }
}

@Composable
private fun TimerButton(label: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
        UiIcon(icon, null, Modifier.size(20.dp), tint = if (enabled) androidx.compose.ui.graphics.Color.White else Palette.SlateSoft)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
