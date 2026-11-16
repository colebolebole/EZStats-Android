package com.ezstats.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezstats.android.DetailKind
import com.ezstats.android.DetailRef
import com.ezstats.android.Device
import com.ezstats.android.EzViewModel
import com.ezstats.android.Home
import com.ezstats.android.StateData
import kotlin.math.roundToInt

// Fridges, freezers, refrigerators and mini fridges: every unit with a temperature to hold.

/** One temperature this device holds: a fridge has one, a refrigerator has a fridge and a freezer. */
private class Compartment(
    val name: String,
    val insideF: Double?,
    val setpointF: Int?,
    val action: String,
    val apply: (Device, Int) -> Device,
)

private fun compartments(d: Device): List<Compartment> {
    val cold = d.cold
    return if (d.type == "refrigerator") {
        listOf(
            Compartment("Fridge", cold?.fridgeInsideF, cold?.fridgeSetpointF, "fridge-setpoint") { dev, v ->
                dev.copy(cold = dev.cold?.copy(fridgeSetpointF = v))
            },
            Compartment("Freezer", d.insideF, d.setpoint, "setpoint") { dev, v -> dev.copy(setpoint = v) },
        )
    } else {
        listOf(Compartment("Inside", d.insideF, d.setpoint, "setpoint") { dev, v -> dev.copy(setpoint = v) })
    }
}

@Composable
private fun FridgePill(d: Device) {
    val defrosting = d.cold?.defrosting == true
    val text = when {
        !d.powered -> "Off"
        defrosting -> "Defrosting"
        else -> UNIT_STATE[d.state] ?: cap(d.state)
    }
    val tone = when {
        !d.powered -> Tone.NEUTRAL
        defrosting -> Tone.WARN
        d.state == "running" -> Tone.GOOD
        else -> Tone.NEUTRAL
    }
    StatusPill(text, tone)
}

// --- the dashboard tile ---------------------------------------------------------------------------------

/** A fridge on the dashboard: what it reads inside. Tapping it opens the full-screen view with the controls. */
@Composable
fun FridgeTile(vm: EzViewModel, d: Device, homes: List<Home>) {
    val units = LocalUnits.current
    DeviceFrame(
        vm, d, homes,
        pill = { FridgePill(d) },
        onClick = { vm.openDetail = DetailRef(DetailKind.DEVICE, d.key) },
        modifier = Modifier.trackTile(vm, d.key),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            compartments(d).forEach { c ->
                Column(Modifier.weight(1f)) {
                    Text(
                        c.insideF?.let { units.whole(it) + units.symbol } ?: "–",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${c.name} inside" + (c.setpointF?.let { " · target ${units.wholeF(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (d.cold?.doorOpen == true) {
            AssistChip(
                onClick = {},
                label = { Text("Door open") },
                leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

// --- the full-screen view -------------------------------------------------------------------------------------

/** One fridge, freezer or refrigerator filling the screen: big temperatures, targets, power, defrost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitScreen(vm: EzViewModel, d: Device, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val cold = d.cold
    val tint = when {
        !d.powered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        cold?.defrosting == true -> HeatColor.copy(alpha = 0.24f)
        cold?.doorOpen == true -> Color(0xFFF5B800).copy(alpha = 0.22f)
        else -> CoolColor.copy(alpha = 0.24f)
    }

    DetailScaffold(
        tint = tint,
        title = deviceName(d),
        subtitle = deviceWhere(d),
        snackbar = snackbar,
        onClose = onClose,
        actions = { DeviceMenuButton(vm, d, state.homes) },
    ) {
        FridgePill(d)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            compartments(d).forEach { c ->
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            c.name.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            c.insideF?.let { units.whole(it) + units.symbol } ?: "–",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Light,
                        )
                        Text("inside now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (c.setpointF != null) {
                            Spacer(Modifier.height(10.dp))
                            if (writable) {
                                BigStepper(
                                    name = "Target",
                                    serverValue = c.setpointF,
                                    format = { units.wholeF(it) },
                                    step = { v, dir -> units.stepF(v, dir) },
                                    onSend = { v -> vm.control(d.key, c.action, v) { c.apply(it, v) } },
                                )
                            } else {
                                Fact("Target", units.wholeF(c.setpointF))
                            }
                        }
                    }
                }
            }

            if (writable) {
                FilledIconButton(
                    onClick = { vm.control(d.key, "powered", !d.powered) { it.copy(powered = !d.powered) } },
                    modifier = Modifier.size(76.dp),
                    colors = if (d.powered) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        if (d.powered) "Switch ${deviceName(d)} off" else "Switch ${deviceName(d)} on",
                        Modifier.size(34.dp),
                    )
                }
            }

            cold?.frost?.let { Meter("Frost", it, 0.7) }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (d.type == "mini_fridge" && cold?.chillerF != null) Fact("Chiller", units.whole(cold.chillerF) + units.symbol)
                d.humidity?.let { Fact("Humidity", "${it.roundToInt()}%") }
            }

            if (cold?.doorOpen == true) {
                AssistChip(
                    onClick = {},
                    label = { Text("Door open") },
                    leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }

            if (writable && cold != null && (d.type == "deep_freezer" || d.type == "refrigerator")) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
            Spacer(Modifier.height(24.dp))
        }
    }
}
