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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.StateData
import com.ezstats.android.Station

/** One weather station filling the screen: now, today, what it measures, and the whole forecast. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationScreen(vm: EzViewModel, key: String, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    // The full list if it has loaded; otherwise the station that came with the dashboard.
    val station: Station? = vm.weather?.stations?.firstOrNull { it.key == key } ?: state.stations[key]
    LaunchedEffect(station == null) { if (station == null) vm.openDetail = null }
    if (station == null) return

    val units = LocalUnits.current
    val today = station.forecast.firstOrNull { it.daysAhead == 0 } ?: station.forecast.firstOrNull()
    val mine = state.devices.filter { it.weather?.station == key }.map { deviceName(it) }
    val tint = when {
        !station.working -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        today?.sky == "clear" -> Color(0xFFF5B800).copy(alpha = 0.22f)
        today?.sky == "rain" -> CoolColor.copy(alpha = 0.24f)
        today?.sky == "snow" -> Color(0xFF7DD3FC).copy(alpha = 0.26f)
        today?.sky == "thunder" -> Color(0xFF7C3AED).copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }

    DetailScaffold(
        tint = tint,
        title = station.label,
        subtitle = "${station.world ?: "Unknown world"} · ${station.x}, ${station.y}, ${station.z}",
        snackbar = snackbar,
        onClose = onClose,
    ) {
        StatusPill(if (station.working) "Forecasting" else idleWord(station), if (station.working) Tone.GOOD else Tone.NEUTRAL)
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!station.working) {
                Text(
                    idleWhy(station),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // The hero: the temperature outside and the sky today.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                today?.let {
                    Icon(skyIcon(it.sky), SKY[it.sky] ?: it.sky, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${units.one(station.outdoorF)}°${units.symbol}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                )
                Text("Outside now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                today?.let {
                    Text(
                        "${SKY[it.sky] ?: cap(it.sky)} · High ${units.whole(it.highF)} · Low ${units.whole(it.lowF)}" +
                            if (it.rain > 0) " · ${(it.rain * 100).toInt()}% rain" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // What it measures.
            val bits = stationBits(station, units)
            if (bits.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bits.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            if (mine.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Nearest for ${mine.joinToString(", ")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (station.forecast.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Forecast", style = MaterialTheme.typography.titleMedium)
                        ForecastStrip(station.forecast)
                    }
                }
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Day by day", style = MaterialTheme.typography.titleMedium)
                        ForecastList(station)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
