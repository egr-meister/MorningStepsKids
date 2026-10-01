package com.morningsteps.kids.ui.path

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningsteps.kids.R
import com.morningsteps.kids.domain.routines.RoutineDetail
import com.morningsteps.kids.domain.routines.Step
import com.morningsteps.kids.domain.routines.TimerStatus
import com.morningsteps.kids.ui.common.ConfirmDialog
import com.morningsteps.kids.ui.common.EmptyMessage
import com.morningsteps.kids.ui.common.Illustration
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.common.formatDuration
import com.morningsteps.kids.ui.parent.ParentsHoldButton
import com.morningsteps.kids.ui.theme.Palette
import com.morningsteps.kids.ui.theme.RoutineColors
import com.morningsteps.kids.ui.theme.colors
import com.morningsteps.kids.ui.theme.iconRes
import com.morningsteps.kids.ui.theme.stepIconRes

private const val LEFT = 0.30f
private const val RIGHT = 0.70f
private const val CENTER = 0.5f
private val NODE_SIZE = 92.dp

/** Horizontal position (fraction of width) of every node: Start, the steps (alternating), Done. */
private fun nodeX(index: Int, stepCount: Int): Float = when (index) {
    0 -> CENTER
    stepCount + 1 -> CENTER
    else -> if ((index - 1) % 2 == 0) LEFT else RIGHT
}

@Composable
fun PathScreen(
    state: PathUiState,
    onOpenPicker: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenParents: () -> Unit,
    onOpenStep: (routineId: String, stepId: String) -> Unit,
    onToggleStep: (stepId: String) -> Unit,
    onOpenFinish: (routineId: String) -> Unit,
    onStartFresh: () -> Unit,
) {
    var confirmFresh by rememberSaveable { mutableStateOf(false) }
    val detail = state.detail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Ivory)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        PathTopBar(
            detail = detail,
            onOpenPicker = onOpenPicker,
            onOpenHistory = onOpenHistory,
            onOpenParents = onOpenParents,
            onStartFresh = { confirmFresh = true },
        )
        when {
            state.loading -> Spacer(Modifier.weight(1f))
            detail == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyMessage("No routines yet. Ask a parent to create one.")
            }
            else -> {
                StepPath(
                    detail = detail,
                    state = state,
                    onOpenStep = { onOpenStep(detail.routine.id, it) },
                    onToggleStep = onToggleStep,
                    onOpenFinish = { onOpenFinish(detail.routine.id) },
                    onStartFresh = { confirmFresh = true },
                    modifier = Modifier.weight(1f),
                )
                PathBottomBar(
                    detail = detail,
                    onNext = { step -> onOpenStep(detail.routine.id, step.id) },
                    onFinish = { onOpenFinish(detail.routine.id) },
                )
            }
        }
        if (state.loading || detail == null) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
        }
    }

    if (confirmFresh && detail != null) {
        ConfirmDialog(
            title = "Start fresh?",
            message = "This clears the checkmarks and timers of “${detail.routine.name}”. Finished routines in History stay.",
            confirmLabel = "Start fresh",
            onConfirm = {
                confirmFresh = false
                onStartFresh()
            },
            onDismiss = { confirmFresh = false },
        )
    }
}

@Composable
private fun PathTopBar(
    detail: RoutineDetail?,
    onOpenPicker: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenParents: () -> Unit,
    onStartFresh: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteSign(detail = detail, onClick = onOpenPicker, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(0.01f))
        IconButton(onClick = onOpenHistory) {
            UiIcon(R.drawable.ic_ui_history, "History", tint = Palette.Slate)
        }
        ParentsHoldButton(onUnlocked = onOpenParents)
        if (detail != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    UiIcon(R.drawable.ic_ui_more_vert, "More options", tint = Palette.Slate)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Start fresh") },
                        leadingIcon = { UiIcon(R.drawable.ic_ui_reset, null) },
                        enabled = detail.inProgress,
                        onClick = {
                            menuOpen = false
                            onStartFresh()
                        },
                    )
                }
            }
        }
    }
}

/** The routine selector, drawn as a small wooden route sign. */
@Composable
private fun RouteSign(detail: RoutineDetail?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = detail?.routine?.theme?.colors() ?: RoutineColors(Palette.Stone, Palette.Slate)
    val name = detail?.routine?.name ?: "Routines"
    val shape = SignShape
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(colors.soft)
            .border(BorderStroke(2.dp, Palette.Slate), shape)
            .clickable(onClickLabel = "choose a routine", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Routine: $name. Choose a routine." }
            .padding(start = 10.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (detail != null) Illustration(detail.routine.theme.iconRes(), null, 30.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Slate,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        UiIcon(R.drawable.ic_ui_expand_more, null, Modifier.size(20.dp), tint = Palette.Slate)
    }
}

@Composable
private fun StepPath(
    detail: RoutineDetail,
    state: PathUiState,
    onOpenStep: (String) -> Unit,
    onToggleStep: (String) -> Unit,
    onOpenFinish: () -> Unit,
    onStartFresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = detail.steps
    val count = steps.size
    val colors = detail.routine.theme.colors()
    val next = detail.nextSuggestedStep
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "message") {
            val message = when {
                detail.allComplete -> "All steps are done. Tap Done when you are ready."
                detail.completedCount == 0 -> "One step at a time."
                else -> "Ready for the next step?"
            }
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.SlateSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (state.startedOnEarlierDay) {
            item(key = "earlier-day") {
                EarlierDayNote(onStartFresh = onStartFresh)
            }
        }
        item(key = "start") {
            PathRow(
                x = nodeX(0, count), prevX = null, nextX = nodeX(1, count),
                upperFilled = false, lowerFilled = steps.firstOrNull()?.let { detail.isCompleted(it.id) } == true,
                colors = colors, minHeight = 104.dp,
            ) { width ->
                EndNode(label = "Start", filled = true, colors = colors, width = width, enabled = true, x = nodeX(0, count), onActivate = null)
            }
        }
        itemsIndexed(steps, key = { _, s -> s.id }) { i, step ->
            val index = i + 1
            val done = detail.isCompleted(step.id)
            val nextDone = if (i + 1 < count) detail.isCompleted(steps[i + 1].id) else detail.allComplete
            PathRow(
                x = nodeX(index, count), prevX = nodeX(index - 1, count), nextX = nodeX(index + 1, count),
                upperFilled = done, lowerFilled = nextDone, colors = colors, minHeight = 156.dp,
            ) { width ->
                StepNodeRow(
                    step = step,
                    position = index,
                    total = count,
                    done = done,
                    isNext = next?.id == step.id,
                    timerStatus = state.timerStatuses[step.id],
                    colors = colors,
                    x = nodeX(index, count),
                    width = width,
                    onOpen = { onOpenStep(step.id) },
                    onToggle = { onToggleStep(step.id) },
                )
            }
        }
        item(key = "done") {
            PathRow(
                x = nodeX(count + 1, count), prevX = nodeX(count, count), nextX = null,
                upperFilled = detail.allComplete, lowerFilled = false, colors = colors, minHeight = 120.dp,
            ) { width ->
                EndNode(
                    label = "Done",
                    filled = detail.allComplete,
                    colors = colors,
                    width = width,
                    enabled = detail.allComplete,
                    x = nodeX(count + 1, count),
                    onActivate = onOpenFinish,
                )
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EarlierDayNote(onStartFresh: () -> Unit) {
    Surface(
        color = Palette.Paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Palette.PaperLine),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "This routine was started on an earlier day. Keep going, or start fresh.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStartFresh) { Text("Start fresh") }
        }
    }
}

/**
 * One row of the route. Draws the half-connectors above and below the node so the full curve is
 * continuous across rows, whatever their heights. Completed sections are solid and filled; the rest
 * is a dashed trail. Content receives the available width.
 */
@Composable
private fun PathRow(
    x: Float,
    prevX: Float?,
    nextX: Float?,
    upperFilled: Boolean,
    lowerFilled: Boolean,
    colors: RoutineColors,
    minHeight: Dp,
    content: @Composable (maxWidth: Dp) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .drawBehind {
                val w = size.width
                val h = size.height
                val cx = x * w
                val cy = h / 2f
                if (prevX != null) {
                    val px = prevX * w
                    val path = Path().apply {
                        moveTo((px + cx) / 2f, 0f)
                        cubicTo((px + 3 * cx) / 4f, h / 8f, cx, h / 4f, cx, cy)
                    }
                    drawTrail(path, upperFilled, colors)
                }
                if (nextX != null) {
                    val nx = nextX * w
                    val path = Path().apply {
                        moveTo(cx, cy)
                        cubicTo(cx, h * 3f / 4f, (3 * cx + nx) / 4f, h * 7f / 8f, (cx + nx) / 2f, h)
                    }
                    drawTrail(path, lowerFilled, colors)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        content(maxWidth)
    }
}

private fun DrawScope.drawTrail(path: Path, filled: Boolean, colors: RoutineColors) {
    val base = 18.dp.toPx()
    drawPath(path, color = Palette.Stone, style = Stroke(width = base, cap = StrokeCap.Round))
    if (filled) {
        drawPath(path, color = colors.soft, style = Stroke(width = base - 4.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color = colors.deep, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    } else {
        drawPath(
            path,
            color = Palette.SlateSoft.copy(alpha = 0.7f),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 10.dp.toPx())),
            ),
        )
    }
}

@Composable
private fun EndNode(
    label: String,
    filled: Boolean,
    colors: RoutineColors,
    width: Dp,
    enabled: Boolean,
    x: Float,
    onActivate: (() -> Unit)?,
) {
    val nodeWidth = 132.dp
    val shape = RoundedCornerShape(50)
    val description = when {
        onActivate == null -> "Start of the path"
        enabled -> "Done. All steps are complete. Open to finish the routine."
        else -> "Done. Available when all steps are complete."
    }
    Box(
        modifier = Modifier
            .offset(x = width * x - nodeWidth / 2)
            .width(nodeWidth)
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (filled) colors.soft else Palette.Paper)
            .border(BorderStroke(if (enabled && onActivate != null) 3.dp else 2.dp, Palette.Slate.copy(alpha = if (enabled) 1f else 0.5f)), shape)
            .then(
                if (onActivate != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = "finish the routine", onClick = onActivate)
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = description
                if (onActivate != null) {
                    role = Role.Button
                    if (enabled) {
                        onClick(label = "finish the routine") {
                            onActivate()
                            true
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label == "Done") {
                UiIcon(R.drawable.ic_ui_check, null, Modifier.size(22.dp), tint = Palette.Slate.copy(alpha = if (enabled) 1f else 0.5f))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Slate.copy(alpha = if (enabled) 1f else 0.6f),
            )
        }
    }
}

@Composable
private fun StepNodeRow(
    step: Step,
    position: Int,
    total: Int,
    done: Boolean,
    isNext: Boolean,
    timerStatus: TimerStatus?,
    colors: RoutineColors,
    x: Float,
    width: Dp,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val centerX = width * x
    val nodeLeft = centerX - NODE_SIZE / 2
    val labelOnRight = x < CENTER
    val gap = 14.dp
    val labelStart = if (labelOnRight) centerX + NODE_SIZE / 2 + gap else 12.dp
    val labelWidth = if (labelOnRight) width - labelStart - 12.dp else nodeLeft - gap - 12.dp

    val hasTimer = step.timerDurationSeconds != null
    val statusText = when {
        done -> "Done"
        isNext -> "Up next"
        else -> "Not done yet"
    }
    val timerText = step.timerDurationSeconds?.let { secs ->
        when (timerStatus) {
            TimerStatus.RUNNING -> "timer running"
            TimerStatus.PAUSED, TimerStatus.PAUSED_AFTER_RESTART -> "timer paused"
            TimerStatus.FINISHED -> "timer finished"
            else -> "${formatDuration(secs)} timer"
        }
    }
    val nodeDescription = buildString {
        append("Step $position of $total: ${step.label}. $statusText.")
        if (timerText != null) append(" Has a $timerText.")
    }

    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // The stepping stone.
        Box(
            modifier = Modifier
                .offset(x = nodeLeft)
                .align(Alignment.CenterStart)
                .size(NODE_SIZE),
        ) {
            if (isNext && !done) {
                // Soft outline for the suggested step (also announced as "Up next").
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(NODE_SIZE + 2.dp)
                        .border(BorderStroke(4.dp, Palette.Highlight), CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(NODE_SIZE - 10.dp)
                    .clip(CircleShape)
                    .background(if (done) colors.soft else Palette.Paper)
                    .border(BorderStroke(2.5.dp, Palette.Slate), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "open step", onClick = onOpen)
                    .clearAndSetSemantics {
                        contentDescription = nodeDescription
                        stateDescription = statusText
                        role = Role.Button
                        onClick(label = "open step") {
                            onOpen()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Illustration(stepIconRes(step.iconKey), null, 52.dp)
            }
            // Position badge.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Palette.Paper)
                    .border(BorderStroke(1.5.dp, Palette.Slate), CircleShape)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text("$position", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Slate)
            }
            if (done) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Palette.Slate)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    UiIcon(R.drawable.ic_ui_check, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
            if (hasTimer) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (timerStatus == TimerStatus.RUNNING) Palette.Highlight else Palette.Paper)
                        .border(BorderStroke(1.5.dp, Palette.Slate), CircleShape)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    UiIcon(R.drawable.ic_ui_timer, null, Modifier.size(16.dp), tint = Palette.Slate)
                }
            }
        }

        // Label, status and the Done / Undo action.
        Column(
            modifier = Modifier
                .offset(x = labelStart)
                .width(labelWidth.coerceAtLeast(96.dp))
                .align(Alignment.CenterStart),
            horizontalAlignment = if (labelOnRight) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                step.label,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Slate,
                textAlign = if (labelOnRight) TextAlign.Start else TextAlign.End,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                "Step $position of $total · $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.SlateSoft,
                fontWeight = if (isNext && !done) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clearAndSetSemantics { },
            )
            if (done) {
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Undo done for ${step.label}" },
                ) { Text("Undo") }
            } else {
                OutlinedButton(
                    onClick = onToggle,
                    border = BorderStroke(1.5.dp, Palette.Slate),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Mark ${step.label} done" },
                ) {
                    UiIcon(R.drawable.ic_ui_check, null, Modifier.size(18.dp), tint = Palette.Slate)
                    Spacer(Modifier.width(6.dp))
                    Text("Done", color = Palette.Slate)
                }
            }
        }
    }
}

@Composable
private fun PathBottomBar(detail: RoutineDetail, onNext: (Step) -> Unit, onFinish: () -> Unit) {
    val colors = detail.routine.theme.colors()
    val total = detail.steps.size
    val doneCount = detail.completedCount
    Surface(color = Palette.Paper, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$doneCount of $total steps done",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        heading()
                    },
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { detail.progress },
                    color = colors.deep,
                    trackColor = Palette.Stone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .clearAndSetSemantics { },
                )
            }
            Spacer(Modifier.width(16.dp))
            val next = detail.nextSuggestedStep
            if (detail.allComplete) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                ) { Text("Finish routine") }
            } else if (next != null) {
                Button(
                    onClick = { onNext(next) },
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .semantics { contentDescription = "Next step: ${next.label}" },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Slate),
                ) { Text("Next step") }
            }
        }
    }
}

/** Route-sign silhouette: rounded on the left, pointed on the right. */
private val SignShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val r = size.height * 0.22f
    val tip = size.height * 0.45f
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
