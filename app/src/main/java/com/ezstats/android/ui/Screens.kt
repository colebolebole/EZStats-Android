package com.ezstats.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ezstats.android.EzViewModel
import com.ezstats.android.Home
import com.ezstats.android.Server
import com.ezstats.android.ServerStore
import com.ezstats.android.Status
import com.ezstats.android.Tab
import kotlinx.coroutines.launch

/** Picks the screen for where the app is: no server, connecting, offline, sign-in, or the dashboard. */
@Composable
fun EzApp(vm: EzViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    EzTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                vm.active == null || vm.showServers -> ServersScreen(vm)
                vm.status == Status.CHECKING -> CenteredProgress()
                vm.status == Status.OFFLINE -> OfflineScreen(vm)
                vm.status == Status.LOGIN -> LoginScreen(vm)
                else -> MainScreen(vm, snackbar)
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

// --- servers -------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(vm: EzViewModel) {
    var editing by remember { mutableStateOf<Server?>(null) }
    var adding by rememberSaveable { mutableStateOf(vm.servers.isEmpty()) }
    var deleting by remember { mutableStateOf<Server?>(null) }
    val canClose = vm.active != null
    BackHandler(enabled = canClose) { vm.showServers = false }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Servers") },
                navigationIcon = {
                    if (canClose) IconButton(onClick = { vm.showServers = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add server") },
            )
        },
    ) { padding ->
        if (vm.servers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Connect to an EZStats server", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add the address players open in their browser, for example http://192.168.1.10:8765. " +
                        "It is the web.public-url from the plugin's config.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.servers, key = { it.id }) { server ->
                    ServerRow(
                        server,
                        selected = server.id == vm.active?.id,
                        onSelect = { vm.selectServer(server.id) },
                        onEdit = { editing = server },
                        onDelete = { deleting = server },
                    )
                }
            }
        }
    }

    if (adding) {
        ServerDialog(null, onDismiss = { adding = false }) { name, url -> vm.addServer(name, url) }
    }
    editing?.let { s ->
        ServerDialog(s, onDismiss = { editing = null }) { name, url -> vm.editServer(s.id, name, url) }
    }
    deleting?.let { s ->
        ConfirmDialog("Delete server?", "Remove \"${s.name}\" from this app? You'll be signed out of it.", "Delete",
            onDismiss = { deleting = null }, onConfirm = { vm.deleteServer(s.id) })
    }
}

@Composable
private fun ServerRow(server: Server, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(if (selected) Icons.Rounded.Check else Icons.Rounded.Dns, null) } }
            },
            headlineContent = { Text(server.name, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(server.url, maxLines = 1) },
            trailingContent = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${server.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            },
        )
    }
}

@Composable
private fun ServerDialog(existing: Server?, onDismiss: () -> Unit, onSave: (name: String, url: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(existing?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Server URL") },
                    placeholder = { Text("192.168.1.10:8765") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "http:// is assumed if you leave it off. Use https:// when you can.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = ServerStore.normalizeUrl(url)
                if (clean == null) {
                    error = "Enter a valid http:// or https:// address"
                } else {
                    onSave(name.trim().ifEmpty { ServerStore.hostOf(clean) }, clean)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- connecting and signing in ---------------------------------------------------------------------------

@Composable
private fun OfflineScreen(vm: EzViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CloudOff, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Can't reach the server", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(vm.active?.url.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            vm.statusError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.check() }) { Text("Try again") }
        TextButton(onClick = { vm.showServers = true }) { Text("Change server") }
    }
}

@Composable
private fun LoginScreen(vm: EzViewModel) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var useLink by rememberSaveable { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    val submit = {
        if (!vm.loginBusy) {
            if (useLink) vm.signInWithLink(link) else vm.signIn(user, pass)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Thermostat, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Sign in", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                vm.active?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                vm.active?.url.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            if (useLink) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Link or token") },
                    supportingText = { Text("Run /ezstats link in game, then paste what it gives you.") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Minecraft name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password")
                        }
                    },
                    supportingText = { Text("Set in game with /ezstats register") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            vm.loginError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { submit() },
                enabled = !vm.loginBusy && (if (useLink) link.isNotBlank() else user.isNotBlank() && pass.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (vm.loginBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (useLink) "Open link" else "Sign in")
            }
            TextButton(onClick = { useLink = !useLink }) { Text(if (useLink) "Use my password instead" else "Use a one-time link instead") }
            TextButton(onClick = { vm.showServers = true }) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Change server")
            }
        }
    }
}

// --- the dashboard ---------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: EzViewModel, snackbar: SnackbarHostState) {
    val state = vm.data ?: return CenteredProgress()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var menu by remember { mutableStateOf(false) }

    // Keep the screen current, but only while the app is in view.
    LaunchedEffect(vm.active?.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollLoop() }
    }
    val showWeather = state.weatherTab
    if (!showWeather && vm.tab == Tab.WEATHER) vm.tab = Tab.DEVICES

    CompositionLocalProvider(
        LocalUnits provides UnitFmt(state.units == "C"),
        LocalWritable provides state.writable,
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(vm.active?.name.orEmpty(), maxLines = 1, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Signed in as ${state.player}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refreshNow() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
                    },
                    scrollBehavior = scroll,
                )
            },
            bottomBar = {
                if (showWeather) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = vm.tab == Tab.DEVICES,
                            onClick = { vm.tab = Tab.DEVICES },
                            icon = { Icon(Icons.Rounded.Thermostat, null) },
                            label = { Text("Devices") },
                        )
                        NavigationBarItem(
                            selected = vm.tab == Tab.WEATHER,
                            onClick = { vm.tab = Tab.WEATHER },
                            icon = { Icon(Icons.Rounded.WbSunny, null) },
                            label = { Text("Weather") },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when (vm.tab) {
                Tab.DEVICES -> DevicesTab(vm, padding)
                Tab.WEATHER -> WeatherTab(vm, padding)
            }
        }
    }
}

@Composable
private fun DevicesTab(vm: EzViewModel, padding: PaddingValues) {
    val state = vm.data ?: return
    val scope = rememberCoroutineScope()
    var newHome by remember { mutableStateOf(false) }
    var renameHome by remember { mutableStateOf<Home?>(null) }
    var deleteHome by remember { mutableStateOf<Home?>(null) }

    val sections: List<Pair<Home?, List<com.ezstats.android.Device>>> =
        if (state.homes.isEmpty()) {
            listOf(null to state.devices)
        } else {
            val loose = state.devices.filter { d -> state.homes.none { it.id == d.home } }
            state.homes.map { h -> h to state.devices.filter { it.home == h.id } } +
                (if (loose.isEmpty()) emptyList() else listOf(null to loose))
        }
    val order = mapOf("thermostat" to 0, "unit" to 1, "equipment" to 2, "instrument" to 3, "vent" to 4)

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
        vm.banner?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "banner") {
                Banner(message, "Trying again…", error = true)
            }
        }
        if (!state.writable) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "readonly") {
                Banner(
                    (state.readOnlyReason ?: "Changing devices is not available.") +
                        " You can still rename devices and sort them into homes here.",
                    null,
                    error = false,
                )
            }
        }

        if (state.worlds.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "worlds") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.worlds.forEach { w ->
                            val units = LocalUnits.current
                            Column {
                                Text(w.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                val parts = mutableListOf<String>()
                                w.season?.let {
                                    parts += "${cap(it)}, day ${w.seasonDay} of ${w.seasonDays}" + if (w.seasonPaused) " (paused)" else ""
                                }
                                w.airF?.let { parts += "${units.one(it)}°${units.symbol} outside" + (w.weather?.let { s -> ", ${s.lowercase()}" } ?: "") }
                                if (parts.isNotEmpty()) {
                                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "new-home") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { newHome = true }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("New home")
                }
            }
        }

        if (state.devices.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Thermostat, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("No devices yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "In game, stand at a thermostat and run /ezstats own to claim it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        sections.forEach { (home, list) ->
            if (state.homes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "home:${home?.id ?: "none"}") {
                    var menu by remember { mutableStateOf(false) }
                    SectionHeader(home?.name ?: "No home", list.size) {
                        if (home != null) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options for ${home.name}") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Rename home") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                        onClick = { menu = false; renameHome = home },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete home") },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                        onClick = { menu = false; deleteHome = home },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(list.sortedBy { order[it.kind] ?: 9 }, key = { it.key }) { d -> DeviceCard(vm, d, state) }
        }
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

@Composable
private fun Banner(message: String, note: String?, error: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Rounded.CloudOff else Icons.Rounded.Home, null)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
