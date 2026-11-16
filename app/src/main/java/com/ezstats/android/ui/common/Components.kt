package com.ezstats.android.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.Tone
import com.ezstats.android.ui.theme.numeric
import com.ezstats.android.ui.theme.toneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A small rounded status label. Its colour and text glide when the status changes. */
@Composable
fun StatusPill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val colors = toneColors(tone)
    val container by animateColorAsState(colors.container, Motion.color, label = "pill")
    val content by animateColorAsState(colors.content, Motion.color, label = "pill text")
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = modifier) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) },
            label = "pill text",
        ) { t ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tone != Tone.NEUTRAL) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
                    Spacer(Modifier.width(6.dp))
                }
                Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** An icon in a soft rounded square: what kind of thing a card is about. */
@Composable
fun IconBadge(icon: ImageVector, container: Color, content: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(MaterialTheme.shapes.small).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.55f), tint = content) }
}

/** "Label value": a small fact in a card. */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.numeric)
    }
}

/** A bar for a fraction between 0 and 1: a tank, a filter, frost. It fills smoothly as the value changes. */
@Composable
fun Meter(name: String, fraction: Double, warnAbove: Double? = null, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0.0, 1.0).toFloat()
    val shown by animateFloatAsState(f, Motion.drift, label = "meter")
    val warn = warnAbove != null && fraction >= warnAbove
    val color by animateColorAsState(
        if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        Motion.color,
        label = "meter colour",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.labelLarge.numeric, color = if (warn) color else Color.Unspecified)
        }
        LinearProgressIndicator(
            progress = { shown },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/**
 * A local copy of a server value that the user is changing: it follows the server whenever the server's value
 * changes, and [send]s the user's value once their hand has rested for a moment.
 */
@Composable
fun rememberDebounced(serverValue: Int, send: (Int) -> Unit): Pair<Int, (Int) -> Unit> {
    var local by remember(serverValue) { mutableIntStateOf(serverValue) }
    var dirty by remember(serverValue) { mutableStateOf(false) }
    LaunchedEffect(local, dirty) {
        if (dirty) {
            delay(450)
            send(local)
        }
    }
    return local to { v: Int -> local = v; dirty = true }
}

/** A value with − and +; sends the new value once the hand has rested. */
@Composable
fun Stepper(
    name: String,
    serverValue: Int,
    format: (Int) -> String,
    step: (Int, Int) -> Int,
    onSend: (Int) -> Unit,
    big: Boolean = false,
) {
    val (local, set) = rememberDebounced(serverValue, onSend)
    val haptic = LocalHapticFeedback.current
    val button = if (big) 56.dp else 40.dp
    val press = { dir: Int ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        set(step(local, dir))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (big) Arrangement.SpaceBetween else Arrangement.Start,
    ) {
        if (!big) Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { press(-1) }, modifier = Modifier.springyPull(limit = 12.dp).size(button)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Lower the $name", modifier = Modifier.size(button * 0.5f))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = if (big) Modifier else Modifier.padding(horizontal = 14.dp),
        ) {
            RollingText(
                format(local),
                style = (if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge).numeric,
            )
            if (big) Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { press(1) }, modifier = Modifier.springyPull(limit = 12.dp).size(button)) {
            Icon(Icons.Rounded.Add, contentDescription = "Raise the $name", modifier = Modifier.size(button * 0.5f))
        }
    }
}

/**
 * A tappable card that sinks a little under the finger and springs back with a small bounce. A long press
 * (with a haptic tick) is for secondary actions such as renaming.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PressCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    pull: Pull? = Pull.HORIZONTAL,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.965f else 1f, if (pressed) Motion.quick else Motion.bouncy, label = "press")
    val haptic = LocalHapticFeedback.current
    val clickable = onClick != null || onLongClick != null
    Surface(
        // The caller's modifier comes first, so whatever measures the card (its screen position) sees it unpressed.
        modifier = modifier
            .then(if (pull != null && (onClick != null || onLongClick != null)) Modifier.springyPull(pull) else Modifier)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(shape)
            .then(
                if (clickable) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = androidx.compose.material3.ripple(),
                        onClick = { onClick?.invoke() },
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
                    )
                } else Modifier,
            ),
        shape = shape,
        color = color,
        contentColor = contentColor,
    ) { Column(content = content) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, current: String?, onPick: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, name ->
            SegmentedButton(
                selected = current == name,
                onClick = {
                    if (current != name) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(name)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(cap(name)) }
        }
    }
}

/** A heading over a group of cards, with an optional count and trailing control. */
@Composable
fun SectionHeader(title: String, count: Int? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (count != null) {
            Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/**
 * A dialog with one text field. [onConfirm] returns an error to show under the field, or null when it worked
 * (the dialog then closes).
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirmLabel: String,
    hint: String? = null,
    maxLength: Int = 32,
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> String?,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) { text = it; error = null } },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: hint.orEmpty()) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = onConfirm(text)
                        busy = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
