package com.ezstats.android.ui.weather

import com.ezstats.android.ui.theme.LocalIsDark
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezstats.android.data.Station
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.PressCard
import com.ezstats.android.ui.common.SectionHeader
import com.ezstats.android.ui.common.skyIcon
import com.ezstats.android.ui.common.skyName
import com.ezstats.android.ui.theme.numeric

private sealed interface PickerRow {
    val key: String

    data class Header(val title: String, val count: Int) : PickerRow { override val key = "h:$title" }
    data class Item(val station: Station) : PickerRow { override val key = "s:${station.key}" }
}

/** Every station, to jump to: search, filter, and a card per station painted in its own sky. */
@Composable
fun StationPicker(
    stations: List<Station>,
    nearest: Map<String, List<String>>,
    favorites: Set<String>,
    current: String?,
    bottom: Dp,
    loading: Boolean,
    onToggleFavorite: (String) -> Unit,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("all") }
    var world by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    // The Favorites filter goes away when the last favorite does; don't leave it selected.
    androidx.compose.runtime.LaunchedEffect(favorites.isEmpty()) { if (favorites.isEmpty() && status == "favorites") status = "all" }

    val worlds = remember(stations) { stations.mapNotNull { it.world }.distinct().sorted() }
    val q = search.trim().lowercase()
    val shown = stations.filter {
        (world.isEmpty() || it.world == world) &&
            when (status) {
                "working" -> it.working
                "idle" -> !it.working
                "favorites" -> it.key in favorites
                else -> true
            } &&
            (q.isEmpty() || it.label.lowercase().contains(q) || (it.world ?: "").lowercase().contains(q))
    }
    val rows = buildList {
        val (starred, rest) = shown.partition { it.key in favorites }
        if (starred.isNotEmpty()) {
            add(PickerRow.Header("Favorites", starred.size))
            starred.forEach { add(PickerRow.Item(it)) }
        }
        val (working, idle) = rest.partition { it.working }
        val (mine, others) = working.partition { nearest[it.key].orEmpty().isNotEmpty() }
        if (mine.isNotEmpty()) {
            add(PickerRow.Header("Near your thermostats", mine.size))
            mine.forEach { add(PickerRow.Item(it)) }
        }
        others.groupBy { it.world ?: "Unknown world" }.forEach { (w, list) ->
            add(PickerRow.Header(w, list.size))
            list.forEach { add(PickerRow.Item(it)) }
        }
        if (idle.isNotEmpty()) {
            add(PickerRow.Header("Not forecasting", idle.size))
            idle.forEach { add(PickerRow.Item(it)) }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Locations", style = MaterialTheme.typography.headlineLarge)
                    val working = stations.count { it.working }
                    val starred = stations.count { it.key in favorites }
                    Text(
                        "${stations.size} stations · $working forecasting" + if (starred > 0) " · $starred favorite${if (starred == 1) "" else "s"}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            }
            if (favorites.isEmpty() && !loading) {
                Text(
                    "Tap a star to keep a station on the Weather page. Tap a card to look at any station.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (loading) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search stations or worlds") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (search.isNotEmpty()) ({ IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("all" to "All", "favorites" to "Favorites", "working" to "Forecasting", "idle" to "Not forecasting")
                    .filter { it.first != "favorites" || favorites.isNotEmpty() }
                    .forEach { (id, label) ->
                    FilterChip(selected = status == id, onClick = { status = id }, label = { Text(label) })
                }
                if (worlds.size > 1) {
                    worlds.forEach { w ->
                        FilterChip(selected = world == w, onClick = { world = if (world == w) "" else w }, label = { Text(w) })
                    }
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "No stations match.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                        )
                    }
                }
                items(rows, key = { it.key }) { row ->
                    Box(Modifier.animateItem()) {
                        when (row) {
                            is PickerRow.Header -> SectionHeader(row.title, row.count)
                            is PickerRow.Item -> StationCard(
                                row.station,
                                nearest[row.station.key].orEmpty(),
                                selected = row.station.key == current,
                                favorite = row.station.key in favorites,
                                onToggleFavorite = { onToggleFavorite(row.station.key) },
                                onClick = { onPick(row.station.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    s: Station,
    nearestFor: List<String>,
    selected: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val units = LocalUnits.current
    val dark = LocalIsDark.current
    val today = s.today
    val sky = skyOf(s)
    PressCard(onClick = onClick, color = Color.Transparent, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(sky.brush(dark))) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                if (favorite) "Remove ${s.label} from favorites" else "Add ${s.label} to favorites",
                                tint = if (favorite) Favorite else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    if (nearestFor.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Thermostat, null, Modifier.size(14.dp), tint = OnSkyMuted)
                            Spacer(Modifier.width(4.dp))
                            Text(nearestFor.joinToString(", "), style = MaterialTheme.typography.labelLarge, color = OnSkyMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(s.world ?: "Unknown world", style = MaterialTheme.typography.labelLarge, color = OnSkyMuted)
                    }
                    Text(
                        if (s.working) today?.let { skyName(it.sky, s.phase) } ?: "" else idleWhy(s),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (today != null && s.working) {
                            Icon(skyIcon(today.sky, s.phase), null, Modifier.size(22.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (s.working) units.whole(s.outdoorF) else idleWord(s),
                            style = if (s.working) MaterialTheme.typography.displaySmall.numeric else MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                    if (today != null && s.working) {
                        Text("H ${units.whole(today.highF)}  L ${units.whole(today.lowF)}", style = MaterialTheme.typography.labelLarge.numeric, color = OnSkyMuted)
                    }
                }
            }
            if (selected) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(3.dp, Color.White.copy(alpha = 0.85f)),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.matchParentSize(),
                ) {}
            }
        }
    }
}
