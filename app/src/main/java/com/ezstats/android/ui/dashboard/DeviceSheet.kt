package com.ezstats.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.Home
import com.ezstats.android.data.homeChoices
import com.ezstats.android.ui.common.IconBadge
import com.ezstats.android.ui.common.TextInputDialog
import com.ezstats.android.ui.common.deviceIcon
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.deviceWhere
import com.ezstats.android.ui.common.shimmer
import com.ezstats.android.ui.theme.toneColors
import kotlinx.coroutines.launch

/**
 * What you can do to a device besides controlling it: rename it, and move it to another home. Opened by a long
 * press on its tile, or the ⋮ of its full-screen view.
 */
@Composable
fun DeviceSheet(vm: EzViewModel, d: Device, homes: List<Home>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var rename by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<String?>(null) }
    // Every home, named by its path ("Acme Co › Kyleton › House 1") so two homes with one name can be told apart.
    val state = vm.data
    val options = remember(state?.orgs, state?.folders, state?.homes, homes) {
        state?.let { s -> homeChoices(s).map { (g, path) -> Home(g.id, path, g.parent) } } ?: homes
    }

    fun close() = scope.launch { sheet.hide() }.invokeOnCompletion { if (!sheet.isVisible) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val tone = toneColors(deviceTone(d))
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(deviceIcon(d), tone.container, tone.accent, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(deviceName(d), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(deviceWhere(d), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Rename") },
                supportingContent = { Text(d.nickname?.let { "Called \"$it\"" } ?: "Give it a name of your own") },
                leadingContent = { Icon(Icons.Rounded.Edit, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large)
                    .selectable(selected = false, role = Role.Button, onClick = { rename = true }),
            )
            if (options.isNotEmpty()) {
                Text(
                    "Home",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                )
                (listOf(Home("", "No home")) + options).forEach { h ->
                    val selected = h.id == d.home || (h.id.isEmpty() && options.none { it.id == d.home })
                    ListItem(
                        headlineContent = { Text(h.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(if (h.id.isEmpty()) Icons.Rounded.HomeWork else Icons.Rounded.Home, null) },
                        trailingContent = {
                            if (moving == h.id) LoadingIndicator(Modifier.size(32.dp)) else RadioButton(selected = selected, onClick = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.large).selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!selected && moving == null) {
                                    moving = h.id
                                    scope.launch {
                                        val error = vm.moveToHome(d.key, h.id)
                                        moving = null
                                        if (error != null) vm.messages.tryEmit(error) else close()
                                    }
                                }
                            },
                        ),
                    )
                }
            }
        }
    }

    if (rename) {
        TextInputDialog(
            title = "Rename ${deviceName(d)}",
            initial = d.nickname.orEmpty(),
            label = "Nickname",
            confirmLabel = "Save",
            hint = "Leave it empty to use its own name.",
            onDismiss = { rename = false },
            onConfirm = { vm.renameDevice(d.key, it) },
        )
    }
}

/** The ⋮ of a full-screen device view: opens [DeviceSheet]. */
@Composable
fun DeviceMenuButton(vm: EzViewModel, d: Device, homes: List<Home>, tint: Color = Color.Unspecified) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Rounded.MoreVert, "More options for ${deviceName(d)}", tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
    }
    if (open) DeviceSheet(vm, d, homes, onDismiss = { open = false })
}

/** The shape of the dashboard, shimmering, while it loads for the first time. */
@Composable
fun DashboardSkeleton() {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).shimmer(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Box(Modifier.widthIn(max = 280.dp).fillMaxWidth().height(36.dp).clip(MaterialTheme.shapes.small).background(block))
        Box(Modifier.width(140.dp).height(18.dp).clip(MaterialTheme.shapes.small).background(block))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Box(Modifier.width(92.dp).height(30.dp).clip(CircleShape).background(block)) }
        }
        Box(Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.large).background(block))
        Box(Modifier.fillMaxWidth().height(196.dp).clip(MaterialTheme.shapes.large).background(block))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(160.dp).clip(MaterialTheme.shapes.large).background(block))
            Box(Modifier.weight(1f).height(160.dp).clip(MaterialTheme.shapes.large).background(block))
        }
    }
}
