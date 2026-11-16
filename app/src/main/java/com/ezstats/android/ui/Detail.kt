package com.ezstats.android.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.ezstats.android.DetailKind
import com.ezstats.android.DetailRef
import com.ezstats.android.EzViewModel
import com.ezstats.android.StateData
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

/** Records where this tile is on screen under [tileKey] while it exists. */
@Composable
fun Modifier.trackTile(vm: EzViewModel, tileKey: String): Modifier {
    DisposableEffect(tileKey) { onDispose { vm.tileBounds.remove(tileKey) } }
    return onGloballyPositioned { vm.tileBounds[tileKey] = unscaled(it.boundsInRoot(), vm) }
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
 * A thermostat, fridge or weather station full screen on top of the dashboard. It grows out of the tile that was
 * tapped and shrinks back into it, so the dashboard underneath stays exactly where it was. The back gesture drags
 * it back toward its tile.
 */
@Composable
fun DetailOverlay(
    vm: EzViewModel,
    ref: DetailRef,
    state: StateData,
    snackbar: SnackbarHostState,
    expand: Animatable<Float, AnimationVector1D>, // 0 = the tile, 1 = the whole screen; shared with the dashboard
) {
    val scope = rememberCoroutineScope()
    val gesture = remember(ref) { Animatable(0f) } // how far the back gesture has been dragged
    var closing by remember(ref) { mutableStateOf(false) }
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val page = MaterialTheme.colorScheme.background

    LaunchedEffect(ref) { expand.animateTo(1f, Motion.open) }

    suspend fun close() {
        if (closing) return
        closing = true
        // A device may have been moved to another home: bring its tile into view (unseen, under this screen)
        // so the shrink lands on it.
        if (ref.kind == DetailKind.DEVICE) vm.revealTile?.invoke(ref.key)
        expand.animateTo(0f, Motion.close)
        vm.openDetail = null
    }

    PredictiveBackHandler(enabled = !closing) { progress ->
        try {
            progress.collect { event ->
                gesture.snapTo(event.progress)
                expand.snapTo(1f - 0.3f * event.progress)
            }
            scope.launch { gesture.animateTo(0f, Motion.close) }
            close()
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
                shape = RevealShape(vm.tileBounds[ref.tileKey], expand.value, 28.dp.toPx())
                clip = true
            }
            .drawBehind { drawRect(lerpColor(card, page, expand.value.coerceIn(0f, 1f))) }
            .pointerInput(Unit) {}, // the dashboard underneath must not receive touches
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // The content arrives a beat after the window starts growing, rising into place as it fades in.
                val e = expand.value.coerceIn(0f, 1f)
                val t = ((e - 0.25f) / 0.6f).coerceIn(0f, 1f)
                alpha = t * t * (3f - 2f * t)
                translationY = (1f - t) * 28.dp.toPx()
            },
        ) {
            val onClose: () -> Unit = { scope.launch { close() } }
            when (ref.kind) {
                DetailKind.DEVICE -> DeviceDetail(vm, ref.key, state, snackbar, onClose)
                DetailKind.STATION -> StationScreen(vm, ref.key, state, snackbar, onClose)
            }
        }
    }
}

/** Picks the full-screen view for a device. */
@Composable
private fun DeviceDetail(vm: EzViewModel, key: String, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val d = state.devices.firstOrNull { it.key == key }
    val supported = d != null && (d.isThermostat || (d.kind == "unit" && d.type in FRIDGES))
    LaunchedEffect(supported) { if (!supported) vm.openDetail = null } // the device went away, or can't be opened
    if (d == null) return
    when {
        d.isThermostat -> ThermostatScreen(vm, key, state, snackbar, onClose)
        supported -> UnitScreen(vm, d, state, snackbar, onClose)
    }
}

// --- the frame the full-screen views share ----------------------------------------------------------------------

/** A tinted page with a back arrow, a title and a centred, scrolling column. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val shown by animateColorAsState(tint, label = "detail tint")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(shown, MaterialTheme.colorScheme.background)))) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                            Text(
                                subtitle,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}
