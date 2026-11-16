package com.ezstats.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ezstats.android.Device
import com.ezstats.android.EzViewModel
import com.ezstats.android.StateData
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val HeatColor = Color(0xFFF2711C)
private val CoolColor = Color(0xFF3B82F6)

private fun targetRange(d: Device) = if (d.pool) 75.0 to 104.0 else 60.0 to 85.0

private fun thermostatTone(d: Device) = when {
    !d.enabled -> Tone.NEUTRAL
    d.mode == "heating" -> Tone.HEAT
    d.mode == "cooling" -> Tone.COOL
    else -> Tone.NEUTRAL
}

private fun thermostatStatus(d: Device) = if (d.enabled) cap(d.mode) else "Off"

// --- the dashboard tile ---------------------------------------------------------------------------------

/** A thermostat on the dashboard: a small read-only dial. Tapping it opens the full-screen view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThermostatTile(vm: EzViewModel, d: Device, state: StateData) {
    val (minF, maxF) = targetRange(d)
    DeviceFrame(
        vm, d, state.homes,
        pill = { StatusPill(thermostatStatus(d), thermostatTone(d)) },
        onClick = { vm.openThermostat = d.key },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Dial(d, d.targetF ?: ((minF + maxF) / 2), LocalUnits.current, false, minF, maxF, {}, compact = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.humidity?.let { Fact("Humidity", "${it.roundToInt()}%") }
            Fact("Equipment", "${d.runningUnits}/${d.totalUnits} running")
        }
        if (d.attention.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    "  " + (ATTENTION[d.attention.first()] ?: cap(d.attention.first())) +
                        if (d.attention.size > 1) " +${d.attention.size - 1} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- the full-screen view ---------------------------------------------------------------------------------

/** One thermostat filling the screen, tinted by what it is doing. Back returns to the dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermostatScreen(vm: EzViewModel, key: String, state: StateData, snackbar: SnackbarHostState) {
    val d = state.devices.firstOrNull { it.key == key && it.isThermostat }
    LaunchedEffect(d == null) { if (d == null) vm.openThermostat = null }
    BackHandler { vm.openThermostat = null }
    if (d == null) return

    val tint by animateColorAsState(
        when {
            !d.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            d.mode == "heating" -> HeatColor.copy(alpha = 0.28f)
            d.mode == "cooling" -> CoolColor.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        },
        label = "thermostat tint",
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, MaterialTheme.colorScheme.background)))) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(deviceName(d), maxLines = 1, style = MaterialTheme.typography.titleLarge)
                            Text(
                                deviceWhere(d),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.openThermostat = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = { DeviceMenuButton(vm, d, state.homes) },
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
            ) {
                StatusPill(thermostatStatus(d), thermostatTone(d))
                Spacer(Modifier.height(12.dp))
                ThermostatControls(vm, d, state, Modifier.widthIn(max = 520.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The dial, buttons, readings, profile and weather of one thermostat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThermostatControls(vm: EzViewModel, d: Device, state: StateData, modifier: Modifier = Modifier) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val (minF, maxF) = targetRange(d)

    // The target being dialled in. It follows the server again whenever the server's value changes.
    var local by remember(d.key, d.targetF) { mutableDoubleStateOf(d.targetF ?: ((minF + maxF) / 2)) }
    var dirty by remember(d.key, d.targetF) { mutableStateOf(false) }
    LaunchedEffect(local, dirty) {
        if (dirty) {
            delay(450)
            val sent = local
            vm.control(d.key, "target", sent) { it.copy(targetF = sent) }
        }
    }

    fun snap(f: Double): Double {
        val step = if (units.celsius) 0.5 else 1.0
        val shown = (units.show(f) / step).roundToInt() * step
        val back = if (units.celsius) shown * 9 / 5 + 32 else shown
        return ((back * 10).roundToInt() / 10.0).coerceIn(minF, maxF)
    }

    val change: (Double) -> Unit = { f ->
        val snapped = snap(f)
        if (snapped != local) {
            local = snapped
            dirty = true
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Dial(d, local, units, writable && d.enabled, minF, maxF, change, maxSize = 340.dp)

        if (writable) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val stepF = if (units.celsius) 0.5 * 9 / 5 else 1.0
                FilledTonalIconButton(
                    onClick = { change(local - stepF) },
                    enabled = d.enabled,
                    modifier = Modifier.size(60.dp),
                ) { Icon(Icons.Rounded.Remove, "Lower the target of ${deviceName(d)}", Modifier.size(28.dp)) }
                FilledIconButton(
                    onClick = { vm.control(d.key, "enabled", !d.enabled) { it.copy(enabled = !d.enabled) } },
                    modifier = Modifier.size(76.dp),
                    colors = if (d.enabled) IconButtonDefaults.filledIconButtonColors()
                    else IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        if (d.enabled) "Switch ${deviceName(d)} off" else "Switch ${deviceName(d)} on",
                        Modifier.size(34.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = { change(local + stepF) },
                    enabled = d.enabled,
                    modifier = Modifier.size(60.dp),
                ) { Icon(Icons.Rounded.Add, "Raise the target of ${deviceName(d)}", Modifier.size(28.dp)) }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            d.humidity?.let { Fact("Humidity", "${it.roundToInt()}%") }
            Fact("Equipment", "${d.runningUnits}/${d.totalUnits} running")
            if (!writable && d.profile != "normal") Fact("Profile", cap(d.profile))
        }

        if (d.attention.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                d.attention.forEach { item ->
                    AssistChip(
                        onClick = {},
                        label = { Text(ATTENTION[item] ?: cap(item)) },
                        leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
        }

        if (writable) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Segmented(listOf("eco", "normal", "turbo"), d.profile) { name ->
                    vm.control(d.key, "profile", name) { it.copy(profile = name) }
                }
            }
        }

        ThermostatWeather(d, state)
    }
}

/** The outdoor conditions of the nearest weather station, with a forecast that opens under it. */
@Composable
private fun ThermostatWeather(d: Device, state: StateData) {
    val units = LocalUnits.current
    val station = d.weather?.let { state.stations[it.station] }
    var open by remember(d.key) { mutableStateOf(false) }
    if (station == null) {
        Text(
            "No weather station in range",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val today = station.forecast.firstOrNull { it.daysAhead == 0 } ?: station.forecast.firstOrNull()
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (today != null) {
                Icon(skyIcon(today.sky), SKY[today.sky] ?: today.sky, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Box(Modifier.size(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("${units.whole(station.outdoorF)} outside", style = MaterialTheme.typography.titleSmall)
                if (today != null) {
                    Text(
                        "H ${units.whole(today.highF)}  L ${units.whole(today.lowF)}" +
                            if (today.rain > 0) "  ·  ${(today.rain * 100).roundToInt()}% rain" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { open = !open }) {
                Text(if (open) "Hide forecast" else "Forecast")
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 4.dp)) {
                Text(
                    "${station.label} · ${d.weather?.distance ?: "?"} blocks away",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ForecastList(station)
            }
        }
    }
}

// --- the dial -----------------------------------------------------------------------------------------------

/**
 * The 270° gauge: the target is the arc and the thumb, the room's temperature is the small dot on it.
 * Drag or tap to set the target when [interactive]. [compact] is the small read-only version for tiles.
 */
@Composable
private fun Dial(
    d: Device,
    target: Double,
    units: UnitFmt,
    interactive: Boolean,
    minF: Double,
    maxF: Double,
    onChange: (Double) -> Unit,
    compact: Boolean = false,
    maxSize: Dp = if (compact) 190.dp else 280.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val arcColor = when {
        !d.enabled -> scheme.outline
        d.mode == "heating" -> HeatColor
        d.mode == "cooling" -> CoolColor
        else -> scheme.primary
    }
    val track = scheme.surfaceVariant
    val surface = if (compact) scheme.surfaceContainerLow else scheme.background
    val marker = scheme.onSurface
    val frac = ((target - minF) / (maxF - minF)).coerceIn(0.0, 1.0).toFloat()
    val roomFrac = d.roomF?.let { ((it - minF) / (maxF - minF)).coerceIn(0.0, 1.0).toFloat() }
    val latest by rememberUpdatedState(onChange)

    fun pick(p: Offset, size: IntSize) {
        val dx = p.x - size.width / 2f
        val dy = p.y - size.height / 2f
        var deg = Math.toDegrees(atan2(dy, dx).toDouble())
        if (deg < 0) deg += 360.0
        var off = (deg - 135 + 360) % 360
        if (off > 270) off = if (off > 315) 0.0 else 270.0 // the gap at the bottom snaps to the nearer end
        latest(minF + off / 270 * (maxF - minF))
    }

    Box(
        Modifier
            .widthIn(max = maxSize)
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(interactive, minF, maxF) {
                if (interactive) detectTapGestures { pick(it, size) }
            }
            .pointerInput(interactive, minF, maxF) {
                if (interactive) {
                    detectDragGestures(onDragStart = { pick(it, size) }) { change, _ ->
                        change.consume()
                        pick(change.position, size)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (if (compact) 12.dp else 20.dp).toPx()
            val diameter = size.minDimension - stroke * 2
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val center = Offset(size.width / 2, size.height / 2)
            fun point(f: Float): Offset {
                val a = Math.toRadians((135 + 270 * f).toDouble())
                return Offset(center.x + (diameter / 2) * cos(a).toFloat(), center.y + (diameter / 2) * sin(a).toFloat())
            }
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(arcColor, 135f, 270f * frac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            roomFrac?.let {
                drawCircle(surface, stroke * 0.38f, point(it))
                drawCircle(marker, stroke * 0.17f, point(it))
            }
            if (d.enabled && !compact) {
                drawCircle(surface, stroke * 0.85f, point(frac))
                drawCircle(arcColor, stroke * 0.62f, point(frac))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    !d.enabled -> "Off"
                    d.mode == "cooling" -> "Cooling to"
                    d.mode == "heating" -> "Heating to"
                    else -> "Set to"
                },
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                if (d.enabled) units.target(target) else "–",
                style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
            )
            Text(
                "Now ${units.one(d.roomF)}°${units.symbol}",
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
