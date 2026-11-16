package com.ezstats.android.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.Home
import com.ezstats.android.data.StateData
import com.ezstats.android.ui.common.ConfirmDialog
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.TextInputDialog
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.entrance
import com.ezstats.android.ui.common.greeting
import com.ezstats.android.ui.common.isShortScreen
import com.ezstats.android.ui.common.plural
import com.ezstats.android.ui.common.skyIcon
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.Tone
import com.ezstats.android.ui.theme.numeric
import kotlinx.coroutines.launch
import java.util.Calendar

private const val NO_HOME = "none"

/** One line of the dashboard grid. Keeping them in a flat list is what lets a tile be found and scrolled to. */
private sealed interface DashRow {
    val key: String

    data class Note(val message: String, val note: String?, val error: Boolean, override val key: String) : DashRow
    data class Summary(val heating: Int, val cooling: Int, val alerts: Int, val devices: Int) : DashRow {
        override val key = "summary"
    }
    data object Worlds : DashRow { override val key = "worlds" }
    data object Empty : DashRow { override val key = "empty" }
    data class Header(val home: Home?, val count: Int, val expanded: Boolean, val summary: String?) : DashRow {
        override val key get() = "home:${home?.id ?: NO_HOME}"
    }
    data class Dev(val d: Device) : DashRow {
        override val key get() = "dev:${d.key}"
    }
}

/** How a device sorts within its home: the things you control first. */
private val ORDER = mapOf("thermostat" to 0, "unit" to 1, "equipment" to 2, "instrument" to 3, "vent" to 4)

/** The devices, one group per home (or a single group when there are no homes). */
private fun sections(state: StateData): List<Pair<Home?, List<Device>>> =
    if (state.homes.isEmpty()) {
        listOf(null to state.devices)
    } else {
        val ids = state.homes.mapTo(HashSet()) { it.id }
        val byHome = state.devices.groupBy { it.home }
        val loose = state.devices.filter { it.home !in ids }
        state.homes.map { h -> h to byHome[h.id].orEmpty() } + (if (loose.isEmpty()) emptyList() else listOf(null to loose))
    }

/** "2 thermostats · 1 heating": what a folded-shut home still has to say for itself. */
private fun homeSummary(list: List<Device>): String? {
    val thermostats = list.filter { it.isThermostat }
    if (thermostats.isEmpty()) return if (list.isEmpty()) null else plural(list.size, "device")
    val parts = mutableListOf(plural(thermostats.size, "thermostat"))
    thermostats.count { it.isHeating }.takeIf { it > 0 }?.let { parts += "$it heating" }
    thermostats.count { it.isCooling }.takeIf { it > 0 }?.let { parts += "$it cooling" }
    return parts.joinToString(" · ")
}

private fun buildRows(state: StateData, banner: String?, collapsed: Set<String>, groups: List<Pair<Home?, List<Device>>>) = buildList {
    banner?.let { add(DashRow.Note(it, "Trying again…", true, "banner")) }
    if (!state.writable) {
        add(
            DashRow.Note(
                (state.readOnlyReason ?: "Changing devices is not available.") +
                    " You can still rename devices and sort them into homes here.",
                null, false, "readonly",
            ),
        )
    }
    if (state.devices.isNotEmpty()) {
        add(
            DashRow.Summary(
                heating = state.devices.count { it.isHeating },
                cooling = state.devices.count { it.isCooling },
                alerts = state.devices.count { it.attention.isNotEmpty() || it.cold?.doorOpen == true },
                devices = state.devices.size,
            ),
        )
    }
    if (state.worlds.isNotEmpty()) add(DashRow.Worlds)
    if (state.devices.isEmpty()) add(DashRow.Empty)
    groups.forEach { (home, list) ->
        val expanded = state.homes.isEmpty() || (home?.id ?: NO_HOME) !in collapsed
        if (state.homes.isNotEmpty()) add(DashRow.Header(home, list.size, expanded, homeSummary(list)))
        // Wide tiles first, then the compact ones, so the compact tiles pack together without gaps.
        if (expanded) list.sortedWith(compareBy({ !wide(it) }, { ORDER[it.kind] ?: 9 })).forEach { add(DashRow.Dev(it)) }
    }
}

/** Thermostats get a wide tile with room for their dial; everything else a compact one. */
private fun wide(d: Device) = d.isThermostat || d.type == "refrigerator"

@Composable
fun DevicesTab(vm: EzViewModel, state: StateData, padding: PaddingValues, gridState: LazyGridState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var newHome by remember { mutableStateOf(false) }
    var renameHome by remember { mutableStateOf<Home?>(null) }
    var deleteHome by remember { mutableStateOf<Home?>(null) }
    var sheetFor by remember { mutableStateOf<Device?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Worked out again only when what they depend on changes, not on every frame of an animation.
    val groups = remember(state.devices, state.homes) { sections(state) }
    val rows = remember(state, vm.banner, vm.collapsed, groups) { buildRows(state, vm.banner, vm.collapsed, groups) }
    val allCollapsed = state.homes.isNotEmpty() && groups.all { (it.first?.id ?: NO_HOME) in vm.collapsed }

    // The full-screen view asks for its tile to be brought into view before it shrinks back into it:
    // the device may have been moved to another home, which may be folded shut or far down the list.
    val latestRows by rememberUpdatedState(rows)
    val latestState by rememberUpdatedState(state)
    DisposableEffect(vm, gridState) {
        vm.revealTile = reveal@{ key ->
            val s = latestState
            val dev = s.devices.firstOrNull { it.key == key } ?: return@reveal
            if (s.homes.isNotEmpty()) {
                val homeId = s.homes.firstOrNull { it.id == dev.home }?.id ?: NO_HOME
                if (homeId in vm.collapsed) {
                    vm.toggleHome(homeId)
                    withFrameNanos { }
                    withFrameNanos { }
                }
            }
            val itemKey = "dev:$key"
            fun info() = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
            fun fits(i: LazyGridItemInfo): Boolean {
                val l = gridState.layoutInfo
                return i.offset.y >= l.beforeContentPadding && i.offset.y + i.size.height <= l.viewportEndOffset - l.afterContentPadding
            }
            val seen = info()
            if (seen != null && fits(seen)) return@reveal
            val index = latestRows.indexOfFirst { it.key == itemKey }
            if (index < 0) return@reveal
            vm.tileBounds.remove(key) // it moves: wait for it to report where it ended up
            gridState.scrollToItem(index)
            gridState.scrollBy(-gridState.layoutInfo.viewportSize.height / 6f) // leave some room above it
            repeat(24) {
                if (info() != null && vm.tileBounds.containsKey(key)) return@reveal
                withFrameNanos { }
            }
        }
        onDispose { vm.revealTile = null }
    }

    val short = isShortScreen()
    val scroll = if (short) TopAppBarDefaults.enterAlwaysScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val refresh = rememberPullToRefreshState()

    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        // The greeting on its own line and who and where under it, so it fits the narrowest phones; on a short
        // screen (a phone on its side) the big header gives way to a one-line bar.
        val title = greeting(hour)
        val subtitle = listOfNotNull(state.player.ifBlank { null }, vm.active?.name).joinToString(" · ")
        val barColors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        val barActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Rounded.Settings, "Settings") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                        DropdownMenuItem(
                            text = { Text("New home") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null) },
                            onClick = { menu = false; newHome = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menu = false; vm.refreshNow() },
                        )
                        if (state.homes.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (allCollapsed) "Expand all homes" else "Collapse all homes") },
                                leadingIcon = { Icon(if (allCollapsed) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess, null) },
                                onClick = {
                                    menu = false
                                    vm.setAllCollapsed(groups.map { it.first?.id ?: NO_HOME }, !allCollapsed)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Servers") },
                            leadingIcon = { Icon(Icons.Rounded.Dns, null) },
                            onClick = { menu = false; vm.showServers = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                            onClick = {
                                menu = false
                                vm.active?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                            onClick = { menu = false; vm.signOut() },
                        )
                    }
                }
        }
        if (short) {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = barActions,
                scrollBehavior = scroll,
                colors = barColors,
            )
        } else LargeFlexibleTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = barActions,
            scrollBehavior = scroll,
            colors = barColors,
        )

        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.refreshNow() },
            state = refresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refresh,
                    isRefreshing = vm.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(164.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    rows,
                    key = { _, row -> row.key },
                    contentType = { _, row -> if (row is DashRow.Dev) (if (wide(row.d)) "wide" else "tile") else row::class },
                    span = { _, row ->
                        when {
                            row is DashRow.Dev && !wide(row.d) -> GridItemSpan(1)
                            // Two columns wide, or the whole row where two would leave a one-column hole.
                            row is DashRow.Dev -> GridItemSpan(if (maxLineSpan <= 3) maxLineSpan else 2)
                            else -> GridItemSpan(maxLineSpan)
                        }
                    },
                ) { index, row ->
                    Box(
                        Modifier
                            .animateItem(
                                fadeInSpec = Motion.fadeIn(),
                                placementSpec = Motion.place,
                                fadeOutSpec = Motion.fadeOut(),
                            )
                            .entrance(row.key, index),
                    ) {
                        when (row) {
                            is DashRow.Note -> Banner(row.message, row.note, row.error)
                            is DashRow.Summary -> SummaryStrip(row)
                            DashRow.Worlds -> WorldsStrip(state)
                            DashRow.Empty -> EmptyDevices()
                            is DashRow.Header -> {
                                val home = row.home
                                HomeHeader(
                                    title = home?.name ?: "No home",
                                    count = row.count,
                                    expanded = row.expanded,
                                    summary = row.summary,
                                    onToggle = { vm.toggleHome(home?.id ?: NO_HOME) },
                                    onRename = home?.let { { renameHome = it } },
                                    onDelete = home?.let { { deleteHome = it } },
                                )
                            }
                            is DashRow.Dev -> DeviceTile(
                                vm = vm,
                                d = row.d,
                                station = row.d.weather?.let { state.stations[it.station] },
                                wide = wide(row.d),
                                onLongPress = { sheetFor = row.d },
                            )
                        }
                    }
                }
            }
        }
    }

    sheetFor?.let { d ->
        // Follow the live device, so a rename shows up in the sheet's title as soon as it lands.
        val live = state.devices.firstOrNull { it.key == d.key } ?: d
        DeviceSheet(vm, live, state.homes, onDismiss = { sheetFor = null })
    }
    if (newHome) {
        TextInputDialog("New home", "", "Home name", "Create", onDismiss = { newHome = false }, onConfirm = { vm.createHome(it) })
    }
    renameHome?.let { h ->
        TextInputDialog("Rename home", h.name, "Home name", "Save", onDismiss = { renameHome = null }, onConfirm = { vm.renameHome(h.id, it) })
    }
    deleteHome?.let { h ->
        ConfirmDialog(
            "Delete \"${h.name}\"?",
            "Its devices aren't deleted: they go back to \"No home\".",
            "Delete",
            onDismiss = { deleteHome = null },
            onConfirm = { scope.launch { vm.deleteHome(h.id)?.let { vm.messages.tryEmit(it) } } },
        )
    }
}

/** What the whole place is doing, at a glance: how many rooms are heating, cooling, or need a look. */
@Composable
private fun SummaryStrip(s: DashRow.Summary) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (s.heating > 0) StatusPill("${s.heating} heating", Tone.HEAT)
        if (s.cooling > 0) StatusPill("${s.cooling} cooling", Tone.COOL)
        if (s.alerts > 0) StatusPill(if (s.alerts == 1) "1 needs a look" else "${s.alerts} need a look", Tone.WARN)
        if (s.heating == 0 && s.cooling == 0 && s.alerts == 0) StatusPill("All quiet", Tone.GOOD)
        StatusPill(plural(s.devices, "device"))
    }
}

/** Each world's season and weather, side by side. */
@Composable
private fun WorldsStrip(state: StateData) {
    val units = LocalUnits.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.worlds.forEach { w ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                // A fixed width: inside a horizontal scroll there is no width to share out.
                modifier = Modifier.width(if (state.worlds.size == 1) 320.dp else 236.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            w.season?.let {
                                Text(
                                    cap(it) + if (w.seasonPaused) " · paused" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (w.airF != null) {
                            w.weather?.let { Icon(skyIcon(it.lowercase()), null, Modifier.size(20.dp)) }
                            Spacer(Modifier.width(6.dp))
                            Text(units.whole(w.airF), style = MaterialTheme.typography.titleLarge.numeric)
                        }
                    }
                    if (w.season != null && w.seasonDays > 0) {
                        val f by animateFloatAsState((w.seasonDay.toFloat() / w.seasonDays).coerceIn(0f, 1f), Motion.drift, label = "season")
                        LinearProgressIndicator(
                            progress = { f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                            drawStopIndicator = {},
                        )
                        Text(
                            "Day ${w.seasonDay} of ${w.seasonDays}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDevices() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).clip(MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
            Icon(Icons.Rounded.Thermostat, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text("No devices yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "In game, stand at a thermostat and run /ezstats own to claim it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A home's heading. Tapping it folds the home's devices away or brings them back. */
@Composable
private fun HomeHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    summary: String?,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, Motion.bouncy, label = "chevron")
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = if (expanded) "Collapse $title" else "Expand $title", onClick = onToggle)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelMedium.numeric,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (!expanded && summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options for $title") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                    onRename?.let {
                        DropdownMenuItem(text = { Text("Rename home") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; it() })
                    }
                    onDelete?.let {
                        DropdownMenuItem(text = { Text("Delete home") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; it() })
                    }
                }
            }
        }
        Icon(
            Icons.Rounded.ExpandMore,
            null,
            Modifier.padding(end = 8.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Banner(message: String, note: String?, error: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Rounded.CloudOff else Icons.Rounded.Info, null)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
