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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.DashEntry
import com.ezstats.android.data.Device
import com.ezstats.android.data.Group
import com.ezstats.android.data.GroupIndex
import com.ezstats.android.data.GroupKind
import com.ezstats.android.data.NO_HOME
import com.ezstats.android.data.StateData
import com.ezstats.android.data.allGroups
import com.ezstats.android.data.flattenGroups
import com.ezstats.android.ui.common.ConfirmDialog
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.TextInputDialog
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.entrance
import com.ezstats.android.ui.common.greeting
import com.ezstats.android.ui.common.deviceGridColumns
import com.ezstats.android.ui.common.isShortScreen
import com.ezstats.android.ui.common.rememberSidePadding
import com.ezstats.android.ui.common.plural
import com.ezstats.android.ui.common.skyIcon
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.Tone
import com.ezstats.android.ui.theme.numeric
import kotlinx.coroutines.launch
import java.util.Calendar

/** One line of the dashboard grid. Keeping them in a flat list is what lets a tile be found and scrolled to. */
private sealed interface DashRow {
    val key: String

    data class Note(val message: String, val note: String?, val error: Boolean, override val key: String) : DashRow
    data class Summary(val heating: Int, val cooling: Int, val alerts: Int, val devices: Int) : DashRow {
        override val key = "summary"
    }
    data object Worlds : DashRow { override val key = "worlds" }
    data object Empty : DashRow { override val key = "empty" }
    /** An organization, folder or home (null: the devices in no home), indented by how deep it is. */
    data class Header(
        val group: Group?,
        val depth: Int,
        val count: Int,
        val expanded: Boolean,
        val summary: String?,
        val canMoveEarlier: Boolean,
        val canMoveLater: Boolean,
    ) : DashRow {
        override val key get() = "group:${group?.id ?: NO_HOME}"
    }
    data class Dev(val d: Device) : DashRow {
        override val key get() = "dev:${d.key}"
    }
}

/** "2 thermostats · 1 heating": what a folded-shut group still has to say for itself. */
private fun homeSummary(list: List<Device>): String? {
    val thermostats = list.filter { it.isThermostat }
    if (thermostats.isEmpty()) return if (list.isEmpty()) null else plural(list.size, "device")
    val parts = mutableListOf(plural(thermostats.size, "thermostat"))
    thermostats.count { it.isHeating }.takeIf { it > 0 }?.let { parts += "$it heating" }
    thermostats.count { it.isCooling }.takeIf { it > 0 }?.let { parts += "$it cooling" }
    return parts.joinToString(" · ")
}

private fun buildRows(state: StateData, banner: String?, collapsed: Set<String>) = buildList {
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
    // Organizations, folders and homes with their devices, in the order the player arranged them (the server keeps it).
    flattenGroups(state, collapsed).forEach { entry ->
        when (entry) {
            is DashEntry.Header -> add(
                DashRow.Header(
                    entry.group, entry.depth, entry.devices.size, entry.expanded, homeSummary(entry.devices),
                    entry.canMoveEarlier, entry.canMoveLater,
                ),
            )
            is DashEntry.Dev -> add(DashRow.Dev(entry.device))
        }
    }
}

/** Thermostats get a wide tile with room for their dial; everything else a compact one. */
private fun wide(d: Device) = d.isThermostat || d.type == "refrigerator"

@Composable
fun DevicesTab(vm: EzViewModel, state: StateData, padding: PaddingValues, gridState: LazyGridState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var newGroup by remember { mutableStateOf<GroupKind?>(null) }
    var renameGroup by remember { mutableStateOf<Group?>(null) }
    var moveGroupFor by remember { mutableStateOf<Group?>(null) }
    var deleteGroup by remember { mutableStateOf<Group?>(null) }
    var sheetFor by remember { mutableStateOf<Device?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Worked out again only when what they depend on changes, not on every frame of an animation.
    val rows = remember(state, vm.banner, vm.collapsed) { buildRows(state, vm.banner, vm.collapsed) }
    val groupIds = remember(state.orgs, state.folders, state.homes, state.devices) {
        val homeIds = state.homes.mapTo(HashSet()) { it.id }
        state.allGroups().map { it.id } + (if (state.devices.any { it.home !in homeIds }) listOf(NO_HOME) else emptyList())
    }
    val allCollapsed = groupIds.isNotEmpty() && groupIds.all { it in vm.collapsed }

    // The full-screen view asks for its tile to be brought into view before it shrinks back into it:
    // the device may have been moved to another home, which may be folded shut or far down the list.
    val latestRows by rememberUpdatedState(rows)
    val latestState by rememberUpdatedState(state)
    DisposableEffect(vm, gridState) {
        vm.revealTile = reveal@{ key ->
            val s = latestState
            val dev = s.devices.firstOrNull { it.key == key } ?: return@reveal
            val groups = s.allGroups()
            if (groups.isNotEmpty()) {
                // Unfold the home it is in and every folder and organization above that.
                val index = GroupIndex(groups)
                val chain = if (dev.home in index.byId) index.selfAndAncestors(dev.home) else listOf(NO_HOME)
                val shut = chain.filter { it in vm.collapsed }
                if (shut.isNotEmpty()) {
                    shut.forEach { vm.toggleHome(it) }
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
                            text = { Text("New organization") },
                            leadingIcon = { Icon(Icons.Rounded.Business, null) },
                            onClick = { menu = false; newGroup = GroupKind.ORG },
                        )
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                            onClick = { menu = false; newGroup = GroupKind.FOLDER },
                        )
                        DropdownMenuItem(
                            text = { Text("New home") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null) },
                            onClick = { menu = false; newGroup = GroupKind.HOME },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (vm.arranging) "Done arranging" else "Arrange") },
                            leadingIcon = { Icon(if (vm.arranging) Icons.Rounded.Check else Icons.Rounded.SwapHoriz, null) },
                            onClick = { menu = false; vm.arranging = !vm.arranging },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menu = false; vm.refreshNow() },
                        )
                        if (groupIds.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (allCollapsed) "Expand everything" else "Collapse everything") },
                                leadingIcon = { Icon(if (allCollapsed) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess, null) },
                                onClick = {
                                    menu = false
                                    vm.setAllCollapsed(groupIds, !allCollapsed)
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
            // On a tablet the grid has an even number of columns (a thermostat takes two, a compact tile one), so rows
            // of wide tiles fill the width; on a very wide screen the content stops growing and centres.
            val screenDp = LocalConfiguration.current.screenWidthDp
            val side = rememberSidePadding(1400.dp, if (screenDp >= 600) 24.dp else 16.dp)
            val tabletColumns = deviceGridColumns(screenDp - (side.value * 2).toInt())
            LazyVerticalGrid(
                state = gridState,
                columns = tabletColumns?.let { GridCells.Fixed(it) } ?: GridCells.Adaptive(164.dp),
                contentPadding = PaddingValues(
                    start = side, end = side,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (tabletColumns != null) 16.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (tabletColumns != null) 16.dp else 12.dp),
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
                                val group = row.group
                                GroupHeader(
                                    group = group,
                                    depth = row.depth,
                                    count = row.count,
                                    expanded = row.expanded,
                                    summary = row.summary,
                                    arranging = vm.arranging,
                                    canMoveEarlier = row.canMoveEarlier,
                                    canMoveLater = row.canMoveLater,
                                    onToggle = { vm.toggleHome(group?.id ?: NO_HOME) },
                                    onRename = group?.let { { renameGroup = it } },
                                    onMove = group?.takeIf { it.kind != GroupKind.ORG }?.let { { moveGroupFor = it } },
                                    onDelete = group?.let { { deleteGroup = it } },
                                    onMoveEarlier = { group?.let { g -> scope.launch { vm.nudgeGroup(g, -1)?.let { vm.messages.tryEmit(it) } } } },
                                    onMoveLater = { group?.let { g -> scope.launch { vm.nudgeGroup(g, 1)?.let { vm.messages.tryEmit(it) } } } },
                                )
                            }
                            is DashRow.Dev -> Box {
                                DeviceTile(
                                    vm = vm,
                                    d = row.d,
                                    station = row.d.weather?.let { state.stations[it.station] },
                                    wide = wide(row.d),
                                    onLongPress = { sheetFor = row.d },
                                )
                                if (vm.arranging) {
                                    // Its place among the devices of its home, and the arrows to change it.
                                    val sibs = state.devices.filter { it.home == row.d.home }
                                    val at = sibs.indexOfFirst { it.key == row.d.key }
                                    ArrangeArrows(
                                        canMoveEarlier = at > 0,
                                        canMoveLater = at in 0 until sibs.lastIndex,
                                        label = deviceName(row.d),
                                        onMoveEarlier = { scope.launch { vm.nudgeDevice(row.d.key, -1)?.let { vm.messages.tryEmit(it) } } },
                                        onMoveLater = { scope.launch { vm.nudgeDevice(row.d.key, 1)?.let { vm.messages.tryEmit(it) } } },
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                                    )
                                }
                            }
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
    newGroup?.let { kind -> NewGroupDialog(vm, state, kind, onDismiss = { newGroup = null }) }
    renameGroup?.let { g ->
        TextInputDialog(
            "Rename ${g.kind.label}", g.name, "${cap(g.kind.label)} name", "Save",
            onDismiss = { renameGroup = null },
            onConfirm = { vm.renameGroup(g, it) },
        )
    }
    moveGroupFor?.let { g -> MoveGroupDialog(vm, state, g, onDismiss = { moveGroupFor = null }) }
    deleteGroup?.let { g ->
        ConfirmDialog(
            "Delete \"${g.name}\"?",
            when (g.kind) {
                GroupKind.HOME -> "Its devices aren't deleted: they go back to \"No home\"."
                else -> "Nothing inside is deleted: its folders and homes move up a level."
            },
            "Delete",
            onDismiss = { deleteGroup = null },
            onConfirm = { scope.launch { vm.deleteGroup(g)?.let { vm.messages.tryEmit(it) } } },
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

/**
 * The heading of an organization, folder or home (or of the devices in no home), indented by how deep it sits. Tapping
 * it folds what is inside away or brings it back. While arranging, ▲ ▼ move it one place instead of the menu.
 */
@Composable
private fun GroupHeader(
    group: Group?,
    depth: Int,
    count: Int,
    expanded: Boolean,
    summary: String?,
    arranging: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onMove: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
) {
    val title = group?.name ?: "No home"
    val kind = group?.kind
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, Motion.bouncy, label = "chevron")
    var menu by remember { mutableStateOf(false) }
    val icon = when (kind) {
        GroupKind.ORG -> Icons.Rounded.Business
        GroupKind.FOLDER -> Icons.Rounded.Folder
        GroupKind.HOME -> Icons.Rounded.Home
        null -> Icons.Rounded.HomeWork
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (depth == 0) 8.dp else 2.dp, start = (depth * 16).dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = if (expanded) "Collapse $title" else "Expand $title", onClick = onToggle)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (kind == GroupKind.ORG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = if (kind == GroupKind.HOME || kind == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = if (kind == GroupKind.ORG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
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
        if (arranging && group != null) {
            IconButton(onClick = onMoveEarlier, enabled = canMoveEarlier) { Icon(Icons.Rounded.ArrowUpward, "Move $title up") }
            IconButton(onClick = onMoveLater, enabled = canMoveLater) { Icon(Icons.Rounded.ArrowDownward, "Move $title down") }
        } else if (group != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options for $title") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                    onRename?.let {
                        DropdownMenuItem(text = { Text("Rename ${group.kind.label}") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; it() })
                    }
                    onMove?.let {
                        DropdownMenuItem(text = { Text("Move ${group.kind.label}…") }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) }, onClick = { menu = false; it() })
                    }
                    onDelete?.let {
                        DropdownMenuItem(text = { Text("Delete ${group.kind.label}") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; it() })
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

/** ◀ ▶ over a device's tile while arranging: one place earlier or later among the devices of its home. */
@Composable
private fun ArrangeArrows(
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    label: String,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoveEarlier, enabled = canMoveEarlier) { Icon(Icons.Rounded.ChevronLeft, "Move $label earlier") }
            IconButton(onClick = onMoveLater, enabled = canMoveLater) { Icon(Icons.Rounded.ChevronRight, "Move $label later") }
        }
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
