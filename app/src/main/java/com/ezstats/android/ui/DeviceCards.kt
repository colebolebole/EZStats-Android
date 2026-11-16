package com.ezstats.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezstats.android.Device
import com.ezstats.android.EzViewModel
import com.ezstats.android.Home
import com.ezstats.android.StateData
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A device's card on the dashboard: a thermostat opens full screen when tapped. */
@Composable
fun DeviceCard(vm: EzViewModel, d: Device, state: StateData) {
    if (d.isThermostat) ThermostatTile(vm, d, state) else DeviceTile(vm, d, state.homes)
}

// --- the frame every device shares ----------------------------------------------------------------------

/** The ⋮ menu of a device: rename it and move it between homes. */
@Composable
fun DeviceMenuButton(vm: EzViewModel, d: Device, homes: List<Home>) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

/** A card with the device's kind, name and place at the top, a status pill and menu, then [content]. */
@Composable
fun DeviceFrame(
    vm: EzViewModel,
    d: Device,
    homes: List<Home>,
    pill: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                DeviceMenuButton(vm, d, homes)
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, content = inner)
    else Card(modifier = modifier, shape = shape, colors = colors, content = inner)
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
