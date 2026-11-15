package com.ezstats.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ezstats.android.Device
import com.ezstats.android.EzViewModel
import com.ezstats.android.Home
import com.ezstats.android.StateData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val HeatColor = Color(0xFFF2711C)
private val CoolColor = Color(0xFF3B82F6)

/** A device's card: the dial for a thermostat, a tile for everything else. */
@Composable
fun DeviceCard(vm: EzViewModel, d: Device, state: StateData) {
    if (d.isThermostat) ThermostatCard(vm, d, state) else DeviceTile(vm, d, state.homes)
}

// --- the frame every device shares ----------------------------------------------------------------------

@Composable
private fun DeviceFrame(
    vm: EzViewModel,
    d: Device,
    homes: List<Home>,
    pill: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(
                        typeName(d).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(deviceName(d), style = MaterialTheme.typography.titleLarge)
                    Text(
                        deviceWhere(d),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.padding(top = 6.dp)) { pill() }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${deviceName(d)}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; rename = true },
                        )
                        if (homes.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Home",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            val options = listOf(Home("", "No home")) + homes
                            options.forEach { h ->
                                DropdownMenuItem(
                                    text = { Text(h.name) },
                                    leadingIcon = { if (h.id == d.home) Icon(Icons.Rounded.Check, null) else Box(Modifier.size(24.dp)) },
                                    onClick = {
                                        menu = false
                                        if (h.id != d.home) scope.launch { vm.moveToHome(d.key, h.id)?.let { vm.messages.tryEmit(it) } }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }

    if (rename) {
        TextInputDialog(
            title = "Rename ${deviceName(d)}",
            initial = d.nickname.orEmpty(),
            label = "Nickname",
            confirmLabel = "Save",
            hint = "Leave it empty to use its own name.",
            onDismiss = { rename = false },
            onConfirm = { vm.renameDevice(d.key, it) },
        )
    }
}

// --- thermostat ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ThermostatCard(vm: EzViewModel, d: Device, state: StateData) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val (minF, maxF) = if (d.pool) 75.0 to 104.0 else 60.0 to 85.0

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
    val tone = when {
        !d.enabled -> Tone.NEUTRAL
        d.mode == "heating" -> Tone.HEAT
        d.mode == "cooling" -> Tone.COOL
        else -> Tone.NEUTRAL
    }

    DeviceFrame(
        vm, d, state.homes,
        pill = { StatusPill(if (d.enabled) cap(d.mode) else "Off", tone) },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Dial(d, local, units, writable && d.enabled, minF, maxF, change)
        }

        if (writable) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val stepF = if (units.celsius) 0.5 * 9 / 5 else 1.0
                FilledTonalIconButton(
                    onClick = { change(local - stepF) },
                    enabled = d.enabled,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Rounded.Remove, "Lower the target of ${deviceName(d)}") }
                FilledIconButton(
                    onClick = { vm.control(d.key, "enabled", !d.enabled) { it.copy(enabled = !d.enabled) } },
                    modifier = Modifier.size(64.dp),
                    colors = if (d.enabled) IconButtonDefaults.filledIconButtonColors()
                    else IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        if (d.enabled) "Switch ${deviceName(d)} off" else "Switch ${deviceName(d)} on",
                        Modifier.size(28.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = { change(local + stepF) },
                    enabled = d.enabled,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Rounded.Add, "Raise the target of ${deviceName(d)}") }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.humidity?.let { Fact("Humidity", "${it.roundToInt()}%") }
            Fact("Equipment", "${d.runningUnits}/${d.totalUnits} running")
            if (!writable && d.profile != "normal") Fact("Profile", cap(d.profile))
        }

        if (d.attention.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Text("No weather station in range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val today = station.forecast.firstOrNull { it.daysAhead == 0 } ?: station.forecast.firstOrNull()
    Column {
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

@Composable
private fun Dial(
    d: Device,
    target: Double,
    units: UnitFmt,
    interactive: Boolean,
    minF: Double,
    maxF: Double,
    onChange: (Double) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val arcColor = when {
        !d.enabled -> scheme.outline
        d.mode == "heating" -> HeatColor
        d.mode == "cooling" -> CoolColor
        else -> scheme.primary
    }
    val track = scheme.surfaceVariant
    val surface = scheme.surfaceContainerLow
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
            .widthIn(max = 280.dp)
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
            val stroke = 18.dp.toPx()
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
                drawCircle(surface, 6.dp.toPx(), point(it))
                drawCircle(marker, 3.dp.toPx(), point(it))
            }
            if (d.enabled) {
                drawCircle(surface, 15.dp.toPx(), point(frac))
                drawCircle(arcColor, 11.dp.toPx(), point(frac))
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
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                if (d.enabled) units.target(target) else "–",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
            )
            Text(
                "Now ${units.one(d.roomF)}°${units.symbol}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- everything that isn't a thermostat ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, current: String?, onPick: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, name ->
            SegmentedButton(
                selected = current == name,
                onClick = { if (current != name) onPick(name) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(cap(name)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceTile(vm: EzViewModel, d: Device, homes: List<Home>) {
    val units = LocalUnits.current
    val writable = LocalWritable.current

    val pill: @Composable () -> Unit = {
        when (d.kind) {
            "equipment" -> {
                val text = when {
                    !d.enabled -> "Off"
                    d.running -> when (d.mode) { "heating" -> "Heating"; "cooling" -> "Cooling"; else -> "Running" }
                    else -> "Idle"
                }
                val tone = if (d.enabled && d.running) when (d.mode) { "heating" -> Tone.HEAT; "cooling" -> Tone.COOL; else -> Tone.GOOD } else Tone.NEUTRAL
                StatusPill(text, tone)
            }
            "unit" -> {
                val text = if (d.powered) UNIT_STATE[d.state] ?: cap(d.state) else "Off"
                val tone = when {
                    d.powered && d.state == "running" -> Tone.GOOD
                    d.state == "no_filter" || d.state == "filter_spent" -> Tone.WARN
                    else -> Tone.NEUTRAL
                }
                StatusPill(text, tone)
            }
        }
    }

    DeviceFrame(vm, d, homes, pill) {
        when (d.kind) {
            "equipment" -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fact("Mode", cap(d.mode))
                    d.rpm?.let { Fact("Motor", "${it.roundToInt()} rpm") }
                    if (d.tier > 0) Fact("Stage", "★".repeat(d.tier) + "☆".repeat((3 - d.tier).coerceAtLeast(0)))
                }
                if (writable) {
                    FilledTonalButton(
                        onClick = { vm.control(d.key, "equipment-enabled", !d.enabled) { it.copy(enabled = !d.enabled) } },
                    ) { Text(if (d.enabled) "Switch off" else "Switch on") }
                }
            }

            "unit" -> UnitBody(vm, d, units, writable)

            "instrument" -> InstrumentBody(d, units)

            "vent" -> Text(
                if (d.type == "water") "Carries water for a pool or radiant system." else "Blows conditioned air into its room.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Text(
                "This kind of device isn't shown yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitBody(vm: EzViewModel, d: Device, units: UnitFmt, writable: Boolean) {
    val facts = mutableListOf<Pair<String, String>>()

    when {
        d.type == "dehumidifier" || d.type == "humidifier" -> {
            Readout(d.humidity?.let { "${it.roundToInt()}%" } ?: "–", "Humidity here")
            val target = d.setpoint
            if (target != null) {
                if (writable) {
                    Stepper(
                        "Target", target,
                        format = { "$it%" },
                        step = { v, dir -> (v + dir * 5).coerceIn(0, 100) },
                        onSend = { v -> vm.control(d.key, "setpoint", v) { it.copy(setpoint = v) } },
                    )
                } else {
                    facts += "Target" to "$target%"
                }
            }
            if (d.type == "dehumidifier") d.tank?.let { Meter("Tank", it, 0.8) }
        }

        d.type == "air_purifier" -> {
            d.air?.let { Readout("AQI ${it.aqi}", BAND[it.band] ?: it.band) }
            d.filter?.let { Meter("Filter", it) }
            if (writable) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf("low", "medium", "high", "auto"), d.fanMode) { name ->
                        vm.control(d.key, "fan", name) { it.copy(fanMode = name) }
                    }
                }
            } else if (d.fanMode != null) {
                facts += "Fan" to (cap(d.fanMode) + if (d.fanMode == "auto" && d.fanSpeed != null) " (${d.fanSpeed})" else "")
            }
        }

        d.type in FRIDGES -> FridgeBody(vm, d, units, writable, facts)
    }

    if (facts.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.forEach { (k, v) -> Fact(k, v) }
        }
    }

    if (writable) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.control(d.key, "powered", !d.powered) { it.copy(powered = !d.powered) } }) {
                Text(if (d.powered) "Switch off" else "Switch on")
            }
            val cold = d.cold
            if (cold != null && (d.type == "deep_freezer" || d.type == "refrigerator")) {
                FilledTonalButton(
                    enabled = !cold.defrosting,
                    onClick = { vm.control(d.key, "defrost", true) { it.copy(cold = cold.copy(defrosting = true)) } },
                ) { Text(if (cold.defrosting) "Defrosting" else "Defrost now") }
                if (d.type == "refrigerator") {
                    FilterChip(
                        selected = cold.autoDefrost,
                        onClick = {
                            vm.control(d.key, "auto-defrost", !cold.autoDefrost) { it.copy(cold = cold.copy(autoDefrost = !cold.autoDefrost)) }
                        },
                        label = { Text("Auto-defrost ${if (cold.autoDefrost) "on" else "off"}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun FridgeBody(vm: EzViewModel, d: Device, units: UnitFmt, writable: Boolean, facts: MutableList<Pair<String, String>>) {
    val cold = d.cold

    @Composable
    fun compartment(name: String, insideF: Double?, setpointF: Int?, action: String, update: (Device, Int) -> Device) {
        if (insideF != null) Readout(units.whole(insideF) + units.symbol, "$name inside")
        if (setpointF == null) return
        if (writable) {
            Stepper(
                "$name target", setpointF,
                format = { units.wholeF(it) },
                step = { v, dir -> units.stepF(v, dir) },
                onSend = { v -> vm.control(d.key, action, v) { update(it, v) } },
            )
        } else {
            facts += "$name target" to units.wholeF(setpointF)
        }
    }

    if (d.type == "refrigerator") {
        compartment("Fridge", cold?.fridgeInsideF, cold?.fridgeSetpointF, "fridge-setpoint") { dev, v ->
            dev.copy(cold = dev.cold?.copy(fridgeSetpointF = v))
        }
        compartment("Freezer", d.insideF, d.setpoint, "setpoint") { dev, v -> dev.copy(setpoint = v) }
    } else {
        compartment("Inside", d.insideF, d.setpoint, "setpoint") { dev, v -> dev.copy(setpoint = v) }
        if (d.type == "mini_fridge" && cold?.chillerF != null) facts += "Chiller" to (units.whole(cold.chillerF) + units.symbol)
    }
    if (cold?.doorOpen == true) {
        AssistChip(
            onClick = {},
            label = { Text("Door open") },
            leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
    cold?.frost?.let { Meter("Frost", it, 0.7) }
    if (cold?.defrosting == true) facts += "Status" to "Defrosting"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstrumentBody(d: Device, units: UnitFmt) {
    val r = d.reading
    val parts = mutableListOf<String>()
    r?.tempF?.let { parts += "${units.one(it)}°${units.symbol}" }
    r?.humidity?.let { parts += "${it.roundToInt()}%" }
    r?.air?.let { parts += "AQI ${it.aqi}" }
    r?.windMs?.let { parts += units.speed(it) + (r.windFrom?.let { f -> " $f" } ?: "") }
    if (parts.isNotEmpty() && r != null) {
        val caption = when {
            r.air != null -> BAND[r.air.band] ?: r.air.band
            r.weather != null -> r.weather
            r.windLevel != null -> WIND[r.windLevel] ?: r.windLevel
            else -> "Reads here"
        }
        Readout(parts.joinToString("  ·  "), caption)
    }
    if (d.links > 0) {
        val what = if (d.type == "sensor_hub") "sensor" else "device"
        Fact("Shows", "${d.links} $what${if (d.links == 1) "" else "s"}")
    }
    if (parts.isEmpty() && d.links == 0) {
        Text("No reading of its own", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
