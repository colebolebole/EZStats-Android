package com.ezstats.android.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.StateData
import com.ezstats.android.ui.common.Fact
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.LocalWritable
import com.ezstats.android.ui.common.Meter
import com.ezstats.android.ui.common.RollingText
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.Stepper
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.deviceWhere
import com.ezstats.android.ui.dashboard.DeviceMenuButton
import com.ezstats.android.ui.dashboard.deviceStatus
import com.ezstats.android.ui.dashboard.deviceTone
import com.ezstats.android.ui.theme.CoolColor
import com.ezstats.android.ui.theme.HeatColor
import com.ezstats.android.ui.theme.HeroNumber
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.SunColor
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

/** One fridge, freezer or refrigerator filling the screen: big temperatures, targets, power, defrost. */
@Composable
fun FridgeScreen(vm: EzViewModel, d: Device, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val cold = d.cold
    val tint = when {
        !d.powered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        cold?.defrosting == true -> HeatColor.copy(alpha = 0.26f)
        cold?.doorOpen == true -> SunColor.copy(alpha = 0.26f)
        else -> CoolColor.copy(alpha = 0.26f)
    }

    DetailScaffold(
        tint = tint,
        title = deviceName(d),
        subtitle = deviceWhere(d),
        snackbar = snackbar,
        onClose = onClose,
        actions = { DeviceMenuButton(vm, d, state.homes) },
    ) {
        StatusPill(deviceStatus(d), deviceTone(d))

        AnimatedVisibility(
            visible = cold?.doorOpen == true,
            enter = fadeIn() + expandVertically(Motion.size),
            exit = fadeOut() + shrinkVertically(Motion.size),
        ) {
            AssistChip(
                onClick = {},
                label = { Text("The door is open") },
                leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }

        compartments(d).forEach { c ->
            DetailCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.Top) {
                        RollingText(c.insideF?.let { units.show(it).roundToInt().toString() } ?: "–", style = HeroNumber)
                        Text("°${units.symbol}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp))
                    }
                    Text("inside now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (c.setpointF != null) {
                    if (writable) {
                        Stepper(
                            name = "target",
                            serverValue = c.setpointF,
                            format = { units.wholeF(it) },
                            step = { v, dir -> units.stepF(v, dir) },
                            onSend = { v -> vm.control(d.key, c.action, v) { c.apply(it, v) } },
                            big = true,
                        )
                    } else {
                        Fact("Target", units.wholeF(c.setpointF))
                    }
                }
            }
        }

        if (writable) PowerButton(d.powered, deviceName(d)) { on -> vm.control(d.key, "powered", on) { it.copy(powered = on) } }

        val facts = buildList {
            if (d.type == "mini_fridge" && cold?.chillerF != null) add("Chiller" to units.whole(cold.chillerF) + units.symbol)
            d.humidity?.let { add("Humidity" to "${it.roundToInt()}%") }
        }
        if (cold?.frost != null || facts.isNotEmpty()) {
            DetailCard {
                cold?.frost?.let { Meter("Frost", it, 0.7) }
                if (facts.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { facts.forEach { (k, v) -> Fact(k, v) } }
                }
            }
        }

        if (writable && cold != null && (d.type == "deep_freezer" || d.type == "refrigerator")) {
            DetailCard("Defrost") {
                Button(
                    enabled = !cold.defrosting,
                    onClick = { vm.control(d.key, "defrost", true) { it.copy(cold = cold.copy(defrosting = true)) } },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.AcUnit, null)
                    Text(if (cold.defrosting) "  Defrosting…" else "  Defrost now")
                }
                if (d.type == "refrigerator") {
                    ListItem(
                        headlineContent = { Text("Defrost automatically") },
                        trailingContent = {
                            Switch(
                                checked = cold.autoDefrost,
                                onCheckedChange = { on -> vm.control(d.key, "auto-defrost", on) { it.copy(cold = cold.copy(autoDefrost = on)) } },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
