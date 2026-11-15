package com.ezstats.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tone { NEUTRAL, HEAT, COOL, GOOD, WARN }

/** Container and content colours for a status. Heat and cool stay orange and blue whatever the theme. */
@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.HEAT -> if (dark) Color(0xFF5C2A10) to Color(0xFFFFDBC9) else Color(0xFFFFDBC9) to Color(0xFF7A2E00)
        Tone.COOL -> if (dark) Color(0xFF0F3B6B) to Color(0xFFD3E4FF) else Color(0xFFD3E4FF) to Color(0xFF00315C)
        Tone.GOOD -> if (dark) Color(0xFF1C4B2A) to Color(0xFFC8F0D0) else Color(0xFFC8F0D0) to Color(0xFF00391A)
        Tone.WARN -> if (dark) Color(0xFF574500) to Color(0xFFFFE8A3) else Color(0xFFFFE8A3) to Color(0xFF4A3800)
        Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun StatusPill(text: String, tone: Tone = Tone.NEUTRAL) {
    val (container, content) = toneColors(tone)
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** "Label value": a small fact in a card. */
@Composable
fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** A bar for a fraction between 0 and 1: a tank, a filter, frost. */
@Composable
fun Meter(name: String, fraction: Double, warnAbove: Double? = null) {
    val f = fraction.coerceIn(0.0, 1.0).toFloat()
    val warn = warnAbove != null && fraction >= warnAbove
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { f },
            modifier = Modifier.fillMaxWidth(),
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** A big number with a small caption: what a card is mostly about. */
@Composable
fun Readout(value: String, caption: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A value with − and + that sends the new value once the hand has rested, like the thermostat dial.
 * [serverValue] is what the server has; the shown value follows it again whenever it changes.
 */
@Composable
fun Stepper(
    name: String,
    serverValue: Int,
    format: (Int) -> String,
    step: (Int, Int) -> Int,
    onSend: (Int) -> Unit,
) {
    var local by remember(serverValue) { mutableIntStateOf(serverValue) }
    var dirty by remember(serverValue) { mutableStateOf(false) }
    LaunchedEffect(local, dirty) {
        if (dirty) {
            delay(450)
            onSend(local)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { local = step(local, -1); dirty = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Lower $name")
        }
        Text(
            format(local),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        FilledTonalIconButton(onClick = { local = step(local, 1); dirty = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "Raise $name")
        }
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
