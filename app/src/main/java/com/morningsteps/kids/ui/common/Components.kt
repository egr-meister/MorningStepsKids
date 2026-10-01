package com.morningsteps.kids.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morningsteps.kids.R
import com.morningsteps.kids.ui.theme.Palette
import java.util.Locale

/** Small tinted UI icon from the bundled `ic_ui_*` set. */
@Composable
fun UiIcon(@DrawableRes res: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(painter = painterResource(res), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

/** Full-color illustration icon (step and theme icons). */
@Composable
fun Illustration(@DrawableRes res: Int, contentDescription: String?, size: Dp, modifier: Modifier = Modifier) {
    Image(painter = painterResource(res), contentDescription = contentDescription, modifier = modifier.size(size))
}

/**
 * Standard screen frame with a back button. Content is constrained on wide screens so phones,
 * tablets and resizable windows all stay readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    maxContentWidth: Dp = 640.dp,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { UiIcon(R.drawable.ic_ui_arrow_back, "Back") }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            content(
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.SlateSoft,
        modifier = modifier
            .padding(top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Illustration(R.drawable.ic_launcher_foreground, null, 120.dp)
        Spacer(Modifier.heightIn(min = 12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Slate)
    }
}

/** Row with leading icon and trailing content used across the parent area. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Formats milliseconds as m:ss (rounded up so "0:00" only shows when time is really over). */
fun formatClock(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Spoken/written duration, e.g. "2 minutes 30 seconds". */
fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    val parts = buildList {
        if (m > 0) add(if (m == 1) "1 minute" else "$m minutes")
        if (s > 0) add(if (s == 1) "1 second" else "$s seconds")
    }
    return if (parts.isEmpty()) "0 seconds" else parts.joinToString(" ")
}

@Composable
fun rememberSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }
