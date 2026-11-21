package com.ezstats.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.StateData
import com.ezstats.android.ui.common.CAR_FANS
import com.ezstats.android.ui.common.CAR_MODES
import com.ezstats.android.ui.common.Fact
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.LocalWritable
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.carModeName
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.deviceWhere
import com.ezstats.android.ui.dashboard.DeviceMenuButton
import com.ezstats.android.ui.dashboard.deviceStatus
import com.ezstats.android.ui.dashboard.deviceTone
import com.ezstats.android.ui.dashboard.remoteLeftText
import com.ezstats.android.ui.theme.CoolColor
import com.ezstats.android.ui.theme.HeatColor

/**
 * A vehicle's climate control (MTVehicles) filling the screen: the same dial, power button and target as a thermostat,
 * with its mode (auto, A/C, heater) and fan under it.
 */
@Composable
internal fun CarScreen(vm: EzViewModel, d: Device, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val tint = when {
        !d.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        d.mode == "heating" -> HeatColor.copy(alpha = 0.30f)
        d.mode == "cooling" -> CoolColor.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    DetailScaffold(
        tint = tint,
        title = deviceName(d),
        subtitle = deviceWhere(d),
        snackbar = snackbar,
        onClose = onClose,
        actions = { DeviceMenuButton(vm, d, state.homes) },
        // On a wide screen the dial stays on the left and the mode, fan and facts take the right.
        aside = {
            CarSettings(vm, d)
            Spacer(Modifier.height(24.dp))
        },
    ) {
        StatusPill(deviceStatus(d), deviceTone(d))
        remoteLine(d)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        ThermostatControls(vm, d)
    }
}

/** "Remote start · 8 min left" while it runs without a driver. */
private fun remoteLine(d: Device): String? {
    val car = d.car ?: return null
    if (!car.remote) return null
    return car.remoteSecondsLeft?.let { "Remote start · ${remoteLeftText(it)}" } ?: "Remote start"
}

/** Mode and fan, the temperatures, and what switching it on will do. */
@Composable
private fun CarSettings(vm: EzViewModel, d: Device) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val car = d.car ?: return

    DetailCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Fact("Inside", if (d.roomF != null) "${units.one(d.roomF)}°${units.symbol}" else "–")
            car.ambientF?.let { Fact("Outside", "${units.whole(it)}") }
            if (!writable) {
                Fact("Mode", carModeName(car.climateMode))
                Fact("Fan", cap(d.fanMode ?: "auto"))
            }
        }
        if (writable) {
            Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ConnectedChoice(CAR_MODES, car.climateMode, label = ::carModeName) { name ->
                vm.control(d.key, "mode", name) { it.copy(car = it.car?.copy(climateMode = name)) }
            }
            Text("Fan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ConnectedChoice(CAR_FANS, d.fanMode ?: "auto") { name ->
                vm.control(d.key, "fan", name) { it.copy(fanMode = name) }
            }
        }
        if (car.occupied) {
            Text("Someone is in it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val minutes = car.remoteStartMinutes
        if (writable && !d.enabled && !car.occupied && car.loaded && minutes != null && minutes > 0) {
            Text(
                "Switching it on while nobody is in it runs it for $minutes minutes (a remote start), so it is comfortable when you get in. It uses fuel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!car.loaded) {
            Text(
                "Parked where no chunk is loaded. Changes still apply when someone gets in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
