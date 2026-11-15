package com.ezstats.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.ForecastDay
import com.ezstats.android.Station
import com.ezstats.android.Wind
import kotlin.math.roundToInt

fun skyIcon(sky: String): ImageVector = when (sky) {
    "rain" -> Icons.Rounded.WaterDrop
    "snow" -> Icons.Rounded.AcUnit
    "thunder" -> Icons.Rounded.FlashOn
    "clear" -> Icons.Rounded.WbSunny
    else -> Icons.Rounded.Cloud
}

/** "12 mph from the W, Breezy"; short: "W 12 mph". */
@Composable
fun windText(w: Wind, short: Boolean): String {
    val units = LocalUnits.current
    if (short) return (w.from?.let { "$it " } ?: "") + units.speed(w.speedMs)
    val parts = mutableListOf(units.speed(w.speedMs) + (w.from?.let { " from the $it" } ?: ""))
    if (w.level.isNotBlank()) parts += WIND[w.level] ?: w.level
    return parts.joinToString(", ")
}

private fun dayLabel(n: Int) = when (n) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "In $n days" }
private fun dayShort(n: Int) = when (n) { 0 -> "Today"; 1 -> "Tmrw"; else -> "+$n" }

/** The seven days of a station, one per row: day, sky, high and low, rain, and a line of notes. */
@Composable
fun ForecastList(station: Station) {
    val units = LocalUnits.current
    if (station.forecast.isEmpty()) {
        Text(
            "This station isn't forecasting right now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        station.forecast.forEach { d ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(d.daysAhead), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                    Icon(skyIcon(d.sky), SKY[d.sky] ?: d.sky, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "  ${SKY[d.sky] ?: cap(d.sky)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${units.whole(d.highF)} / ${units.whole(d.lowF)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (d.rain > 0) "${(d.rain * 100).roundToInt()}%" else "–",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
                val extra = mutableListOf<String>()
                if (d.note.isNotBlank()) extra += d.note
                if (d.thunder >= 0.05) extra += "${(d.thunder * 100).roundToInt()}% thunder"
                d.wind?.let { extra += windText(it, true) }
                d.aqi?.let { extra += "AQI $it" }
                if (extra.isNotEmpty()) {
                    Text(
                        extra.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The seven days side by side. */
@Composable
private fun ForecastStrip(days: List<ForecastDay>) {
    val units = LocalUnits.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            Column(
                Modifier.width(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(dayShort(d.daysAhead), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(skyIcon(d.sky), SKY[d.sky] ?: d.sky, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text(units.whole(d.highF), style = MaterialTheme.typography.titleSmall)
                Text(units.whole(d.lowF), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (d.rain > 0) "${(d.rain * 100).roundToInt()}%" else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun stationBits(s: Station, units: UnitFmt): List<String> {
    val bits = mutableListOf<String>()
    s.wind?.let { w ->
        val level = WIND[w.level] ?: w.level
        bits += "Wind ${units.speed(w.speedMs)}${w.from?.let { " from the $it" } ?: ""}" + if (level.isNotBlank()) ", $level" else ""
    }
    s.air?.let { bits += "Air ${it.aqi}, ${BAND[it.band] ?: it.band}" }
    if (s.lightning > 0) bits += "${s.lightning} lightning strike${if (s.lightning == 1) "" else "s"}"
    return bits
}

@Composable
private fun StationCard(station: Station, nearestFor: List<String>) {
    val units = LocalUnits.current
    var open by rememberSaveable(station.key) { mutableStateOf(false) }
    val today = station.forecast.firstOrNull { it.daysAhead == 0 } ?: station.forecast.firstOrNull()

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(station.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${station.world ?: "Unknown world"} · ${station.x}, ${station.y}, ${station.z}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill("Forecasting", Tone.GOOD)
            }
            if (nearestFor.isNotEmpty()) {
                Text(
                    "Nearest for ${nearestFor.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${units.one(station.outdoorF)}°${units.symbol}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                    Text("Outside now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (today != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(skyIcon(today.sky), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("  ${SKY[today.sky] ?: cap(today.sky)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "High ${units.whole(today.highF)} · Low ${units.whole(today.lowF)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val bits = stationBits(station, units)
            if (bits.isNotEmpty()) {
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (station.forecast.isNotEmpty()) ForecastStrip(station.forecast)
            TextButton(onClick = { open = !open }, modifier = Modifier.align(Alignment.Start)) {
                Text(if (open) "Hide day-by-day" else "Day-by-day details")
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(open) { ForecastList(station) }
        }
    }
}

private fun idleWhy(s: Station) = when {
    s.working -> ""
    !s.enabled -> "Switched off by an admin."
    !s.intact -> "Its tower is damaged: it forecasts again once it is repaired."
    else -> "It hasn't measured the weather yet."
}

private fun idleWord(s: Station) = when {
    !s.enabled -> "Off"
    !s.intact -> "Damaged"
    else -> "Waiting"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherTab(vm: EzViewModel, padding: PaddingValues) {
    val wx = vm.weather
    val state = vm.data
    if (wx == null) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Loading the weather…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var search by rememberSaveable { mutableStateOf("") }
    var world by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("all") }
    var worldMenu by remember { mutableStateOf(false) }

    val all = wx.stations
    val worlds = all.mapNotNull { it.world }.distinct()
    val q = search.trim().lowercase()
    val shown = all.filter {
        (world.isEmpty() || it.world == world) &&
            (status == "all" || (status == "working") == it.working) &&
            (q.isEmpty() || it.label.lowercase().contains(q) || (it.world ?: "").lowercase().contains(q))
    }
    fun nearestFor(key: String) = state?.devices
        ?.filter { it.weather?.station == key }
        ?.map { deviceName(it) }
        .orEmpty()

    val mine = shown.filter { it.working && nearestFor(it.key).isNotEmpty() }
    val others = shown.filter { it.working && nearestFor(it.key).isEmpty() }
    val idle = shown.filter { !it.working }
    val working = all.count { it.working }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(340.dp),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (all.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No weather stations on this server yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            return@LazyVerticalGrid
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${all.size} station${if (all.size == 1) "" else "s"} · $working forecasting" +
                        if (all.size - working > 0) " · ${all.size - working} not forecasting" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search stations") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "working" to "Forecasting", "idle" to "Not forecasting").forEach { (id, label) ->
                        FilterChip(selected = status == id, onClick = { status = id }, label = { Text(label) })
                    }
                    if (worlds.size > 1) {
                        Box {
                            FilterChip(
                                selected = world.isNotEmpty(),
                                onClick = { worldMenu = true },
                                label = { Text(world.ifEmpty { "All worlds" }) },
                                trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
                            )
                            DropdownMenu(expanded = worldMenu, onDismissRequest = { worldMenu = false }) {
                                DropdownMenuItem(text = { Text("All worlds") }, onClick = { world = ""; worldMenu = false })
                                worlds.forEach { w ->
                                    DropdownMenuItem(text = { Text(w) }, onClick = { world = w; worldMenu = false })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No stations match.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            }
        }

        fun section(title: String, list: List<Station>) {
            if (list.isEmpty()) return
            item(span = { GridItemSpan(maxLineSpan) }, key = "h:$title") { SectionHeader(title, list.size) }
            items(list, key = { "s:$title:${it.key}" }) { StationCard(it, nearestFor(it.key)) }
        }

        section("Near your thermostats", mine)
        others.groupBy { it.world ?: "Unknown world" }.forEach { (w, list) ->
            section(if (mine.isNotEmpty()) "$w · other stations" else w, list)
        }

        if (idle.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h:idle") { SectionHeader("Not forecasting", idle.size) }
            items(idle, key = { "i:${it.key}" }) { s ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${s.world ?: "Unknown world"} · ${s.x}, ${s.y}, ${s.z}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(idleWhy(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(idleWord(s))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
