package com.ezstats.android.ui.onboarding

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Server
import com.ezstats.android.data.ServerStore
import com.ezstats.android.ui.common.ConfirmDialog
import com.ezstats.android.ui.common.IconBadge
import com.ezstats.android.ui.common.PressCard
import com.ezstats.android.ui.common.entrance
import com.ezstats.android.ui.common.rememberSidePadding
import com.ezstats.android.ui.theme.Motion
import kotlin.coroutines.cancellation.CancellationException

/** The servers this app knows, to switch between, add, edit and delete. */
@Composable
fun ServersScreen(vm: EzViewModel) {
    var editing by remember { mutableStateOf<Server?>(null) }
    var adding by rememberSaveable { mutableStateOf(vm.servers.isEmpty()) }
    var deleting by remember { mutableStateOf<Server?>(null) }
    val canClose = vm.active != null
    val gesture = remember { Animatable(0f) }
    // Dragging back shrinks the screen and nudges it away; letting go past the middle slides it out.
    PredictiveBackHandler(enabled = canClose) { progress ->
        try {
            progress.collect { gesture.snapTo(it.progress) }
            vm.showServers = false
        } catch (e: CancellationException) {
            gesture.animateTo(0f, Motion.settle)
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .graphicsLayer {
                val g = gesture.value
                scaleX = 1f - 0.08f * g
                scaleY = 1f - 0.08f * g
                translationX = g * 40.dp.toPx()
                shape = RoundedCornerShape((32 * g).dp)
                clip = g > 0f
            }
            .nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Servers") },
                subtitle = { Text("EZStats servers you can connect to") },
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
                Modifier.fillMaxSize().padding(padding).padding(horizontal = rememberSidePadding(560.dp, 32.dp), vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HeroBadge(
                    Icons.Rounded.Dns,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialShapes.Sunny.toShape(),
                )
                Spacer(Modifier.height(24.dp))
                Text("Connect to an EZStats server", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add the address players open in their browser, for example http://192.168.1.10:8765. " +
                        "It is the web.public-url from the plugin's config.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val side = rememberSidePadding(680.dp)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = side, end = side,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(vm.servers, key = { _, s -> s.id }) { i, server ->
                    Box(Modifier.animateItem().entrance("server:${server.id}", i)) {
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
    }

    if (adding) {
        ServerDialog(null, onDismiss = { adding = false }) { name, url -> vm.addServer(name, url) }
    }
    editing?.let { s ->
        ServerDialog(s, onDismiss = { editing = null }) { name, url -> vm.editServer(s.id, name, url) }
    }
    deleting?.let { s ->
        ConfirmDialog(
            "Delete server?", "Remove \"${s.name}\" from this app? You'll be signed out of it.", "Delete",
            onDismiss = { deleting = null }, onConfirm = { vm.deleteServer(s.id) },
        )
    }
}

@Composable
private fun ServerRow(server: Server, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    PressCard(
        onClick = onSelect,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (selected) Icons.Rounded.Check else Icons.Rounded.Dns,
                if (selected) scheme.primary else scheme.surfaceContainerHighest,
                if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                size = 48.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(server.url, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${server.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ServerDialog(existing: Server?, onDismiss: () -> Unit, onSave: (name: String, url: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(existing?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val save = {
        val clean = ServerStore.normalizeUrl(url)
        if (clean == null) {
            error = "Enter a valid http:// or https:// address"
        } else {
            onSave(name.trim().ifEmpty { ServerStore.hostOf(clean) }, clean)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Dns, null) },
        title = { Text(if (existing == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.10:8765") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "http:// is assumed if you leave it off. Use https:// when you can.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name (optional)") },
                    singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = { TextButton(onClick = save) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
