package com.morningsteps.kids.ui.parent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.morningsteps.kids.R
import com.morningsteps.kids.ui.common.UiIcon
import com.morningsteps.kids.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val HOLD_MILLIS = 3_000

/**
 * "Parents" control: press and hold for three seconds to enter the parent area.
 * A short tap (or a screen-reader activation) opens an accessible alternative: a simple
 * grown-up question. This prevents accidental entry; it is not authentication.
 */
@Composable
fun ParentsHoldButton(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var showAlternative by rememberSaveable { mutableStateOf(false) }
    val unlock by rememberUpdatedState(onUnlocked)
    val fill = Palette.Highlight.copy(alpha = 0.45f)

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Stone)
            .drawBehind {
                drawRect(fill, size = Size(size.width * progress.value, size.height))
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Parents. Press and hold for three seconds, or double tap for a grown-up question."
                onClick(label = "open the grown-up question") {
                    showAlternative = true
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var completed = false
                        val job: Job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(HOLD_MILLIS, easing = LinearEasing))
                            completed = true
                            unlock()
                        }
                        tryAwaitRelease()
                        if (!completed) {
                            val wasShortTap = progress.value < 0.08f
                            job.cancel()
                            if (wasShortTap) showAlternative = true
                        }
                        scope.launch { progress.snapTo(0f) }
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(R.drawable.ic_ui_parents, null, Modifier.size(20.dp), tint = Palette.Slate)
        Spacer(Modifier.width(6.dp))
        Text("Parents", style = MaterialTheme.typography.labelMedium, color = Palette.Slate)
    }

    if (showAlternative) {
        GrownUpQuestionDialog(
            onSolved = {
                showAlternative = false
                unlock()
            },
            onDismiss = { showAlternative = false },
        )
    }
}

@Composable
private fun GrownUpQuestionDialog(onSolved: () -> Unit, onDismiss: () -> Unit) {
    val a = rememberSaveable { Random.nextInt(11, 20) }
    val b = rememberSaveable { Random.nextInt(6, 10) }
    var answer by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    fun check() {
        if (answer.trim().toIntOrNull() == a + b) {
            onSolved()
        } else {
            wrong = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("For grown-ups") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Press and hold the Parents button for three seconds, or answer this question.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("What is $a + $b?", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = answer,
                    onValueChange = {
                        answer = it.filter(Char::isDigit).take(3)
                        wrong = false
                    },
                    label = { Text("Answer") },
                    singleLine = true,
                    isError = wrong,
                    supportingText = { if (wrong) Text("That is not quite right. Try again.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { check() }),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::check) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
