package com.ezstats.android.ui.detail

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.StateData
import com.ezstats.android.ui.common.Pull
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.springyPull
import com.ezstats.android.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// --- where a tile is, so the full-screen view can grow from it ------------------------------------------------

/**
 * A tile's rectangle as it would be if the dashboard weren't sunk back behind the full-screen view. The dashboard
 * shrinks about the middle of the screen, and the rectangle has to be where the tile will be once it settles.
 */
internal fun unscaled(r: Rect, vm: EzViewModel): Rect {
    val s = vm.dashboardScale()
    val size = vm.rootSize
    if (s == 1f || s <= 0f || size.width == 0f) return r
    val cx = size.width / 2
    val cy = size.height / 2
    fun fix(o: Offset) = Offset(cx + (o.x - cx) / s, cy + (o.y - cy) / s)
    return Rect(fix(r.topLeft), fix(r.bottomRight))
}

/** Records where this device's tile is on screen while it exists. */
fun Modifier.trackTile(vm: EzViewModel, key: String): Modifier = composed {
    DisposableEffect(key) { onDispose { vm.tileBounds.remove(key) } }
    onGloballyPositioned { vm.tileBounds[key] = unscaled(it.boundsInRoot(), vm) }
}

/** The window the full-screen view is seen through: it grows from the tile's rectangle to the whole screen. */
private class RevealShape(private val from: Rect?, private val progress: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val full = Rect(0f, 0f, size.width, size.height)
        val start = from ?: Rect(Offset(size.width / 2 - 60f, size.height / 2 - 60f), Size(120f, 120f))
        val r = lerp(start, full, progress)
        val radius = lerpFloat(corner, 0f, progress).coerceAtLeast(0f) // a spring can overshoot past full screen
        return Outline.Rounded(RoundRect(r, CornerRadius(radius, radius)))
    }
}

// --- the overlay ------------------------------------------------------------------------------------------------

/**
 * A device full screen on top of the dashboard. It grows out of the tile that was tapped and shrinks back into
 * it, so the dashboard underneath stays exactly where it was. The back gesture drags it back toward its tile.
 */
@Composable
fun DetailOverlay(
    vm: EzViewModel,
    key: String,
    state: StateData,
    snackbar: SnackbarHostState,
    expand: Animatable<Float, AnimationVector1D>, // 0 = the tile, 1 = the whole screen; shared with the dashboard
) {
    val scope = rememberCoroutineScope()
    val gesture = remember(key) { Animatable(0f) } // how far the back gesture has been dragged
    var closing by remember(key) { mutableStateOf(false) }
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val page = MaterialTheme.colorScheme.background

    LaunchedEffect(key) { expand.animateTo(1f, Motion.open) }

    suspend fun close() {
        if (closing) return
        closing = true
        // A device may have been moved to another home: bring its tile into view (unseen, under this screen)
        // so the shrink lands on it.
        vm.revealTile?.invoke(key)
        expand.animateTo(0f, Motion.close)
        vm.openDetail = null
    }

    PredictiveBackHandler(enabled = !closing) { progress ->
        try {
            progress.collect { event ->
                gesture.snapTo(event.progress)
                expand.snapTo(1f - 0.3f * event.progress)
            }
            // Closing runs in this screen's own scope: the back handler's coroutine is cancelled as soon as
            // the back is handled, and an animation started in it would be cut short.
            scope.launch { gesture.animateTo(0f, Motion.close) }
            scope.launch { close() }
        } catch (e: CancellationException) {
            scope.launch { gesture.animateTo(0f, Motion.settle) }
            expand.animateTo(1f, Motion.settle)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val g = gesture.value
                scaleX = 1f - 0.06f * g
                scaleY = 1f - 0.06f * g
                shape = RevealShape(vm.tileBounds[key], expand.value, 28.dp.toPx())
                clip = true
                // Near the tile, the whole view dissolves into the real tile underneath it (which is right there,
                // the same size), so closing melts back into the card instead of blinking out, and opening grows
                // out of it.
                val e = expand.value.coerceIn(0f, 1f)
                val t = (e / 0.4f).coerceIn(0f, 1f)
                alpha = t * t * (3f - 2f * t)
            }
            .drawBehind { drawRect(lerpColor(card, page, expand.value.coerceIn(0f, 1f))) }
            .pointerInput(Unit) {}, // the dashboard underneath must not receive touches
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // The content arrives a beat after the window starts growing, rising into place as it fades in.
                val e = expand.value.coerceIn(0f, 1f)
                val t = ((e - 0.2f) / 0.6f).coerceIn(0f, 1f)
                alpha = t * t * (3f - 2f * t)
                translationY = (1f - t) * 32.dp.toPx()
            },
        ) {
            val onClose: () -> Unit = { scope.launch { close() } }
            val d = state.devices.firstOrNull { it.key == key }
            LaunchedEffect(d?.opensDetail) { if (d?.opensDetail != true) vm.openDetail = null } // it went away
            when {
                d == null -> Unit
                d.isThermostat -> ThermostatScreen(vm, d, state, snackbar, onClose)
                d.isFridge -> FridgeScreen(vm, d, state, snackbar, onClose)
                d.isHumidityUnit || d.isPurifier -> UnitScreen(vm, d, state, snackbar, onClose)
            }
        }
    }
}

// --- the frame the full-screen views share ----------------------------------------------------------------------

/** A page washed with [tint] at the top, with a back arrow, a title, and a centred, scrolling column. */
@Composable
fun DetailScaffold(
    tint: Color,
    title: String,
    subtitle: String,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown by animateColorAsState(tint, Motion.color, label = "detail tint")
    val scroll = rememberScrollState()
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to shown, 0.55f to MaterialTheme.colorScheme.background),
        ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                            Text(
                                subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * The big power button. Expressive: it is a circle while off and a rounded square while on, and squashes a
 * little further under the finger.
 */
@Composable
fun PowerButton(on: Boolean, name: String, onToggle: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    FilledIconButton(
        onClick = {
            haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onToggle(!on)
        },
        shapes = IconButtonShapes(
            shape = if (on) RoundedCornerShape(28.dp) else CircleShape,
            pressedShape = RoundedCornerShape(18.dp),
        ),
        modifier = Modifier.springyPull(limit = 22.dp).size(84.dp),
        colors = if (on) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(Icons.Rounded.PowerSettingsNew, if (on) "Switch $name off" else "Switch $name on", Modifier.size(36.dp))
    }
}

/** A row of connected toggle buttons, one per option (Expressive's connected button group). */
@Composable
fun ConnectedChoice(options: List<String>, current: String?, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, name ->
            ToggleButton(
                checked = current == name,
                onCheckedChange = {
                    if (current != name) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onPick(name)
                    }
                },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) { Text(cap(name), maxLines = 1) }
        }
    }
}

/** A titled card on a detail page. */
@Composable
fun DetailCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
