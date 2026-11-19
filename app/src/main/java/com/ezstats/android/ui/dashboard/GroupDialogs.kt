package com.ezstats.android.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Group
import com.ezstats.android.data.GroupIndex
import com.ezstats.android.data.GroupKind
import com.ezstats.android.data.StateData
import com.ezstats.android.data.allGroups
import com.ezstats.android.data.positionsIn
import com.ezstats.android.data.siblingsOf
import com.ezstats.android.data.validParents
import com.ezstats.android.ui.common.cap
import kotlinx.coroutines.launch

/**
 * Makes an organization, folder or home. A folder or home asks where it goes ("Inside"): the top, or one of the
 * organizations and folders it can sit in.
 */
@Composable
fun NewGroupDialog(vm: EzViewModel, state: StateData, kind: GroupKind, onDismiss: () -> Unit) {
    val index = remember(state) { GroupIndex(state.allGroups()) }
    val parents = remember(index, kind) { if (kind == GroupKind.ORG) emptyList() else validParents(index, null) }
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New ${kind.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) { name = it; error = null } },
                    label = { Text("${cap(kind.label)} name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error.orEmpty()) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parents.size > 1) {
                    ParentChoice("Inside", parents, index, parent) { parent = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = vm.createGroup(kind, name, parent)
                        busy = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Moves a folder or home, with everything in it, to the top or into an organization or folder. */
@Composable
fun MoveGroupDialog(vm: EzViewModel, state: StateData, group: Group, onDismiss: () -> Unit) {
    val index = remember(state) { GroupIndex(state.allGroups()) }
    val parents = remember(index, group) { validParents(index, group) }
    val current = index.parentOf(group)
    var parent by remember { mutableStateOf(current) }
    var before by remember { mutableStateOf("") } // "" is the end of the place it goes to
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val positions = remember(index, group, parent) { positionsIn(index, group, parent) }
    val alreadyLast = parent == current && siblingsOf(index, group).lastOrNull()?.id == group.id

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Move ${group.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parents.size <= 1) {
                    Text(
                        "There is nowhere else it can go. Make an organization or a folder first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Everything in it moves with it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ParentChoice("Inside", parents, index, parent) { parent = it; before = ""; error = null }
                }
                if (positions.isNotEmpty()) {
                    PositionChoice(positions, before) { before = it; error = null }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (parent != current || before.isNotEmpty() || !alreadyLast),
                onClick = {
                    busy = true
                    scope.launch {
                        val result = if (before.isEmpty() && parent != current) vm.moveGroup(group, parent) else vm.placeGroup(group, parent, before)
                        busy = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text("Move") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Where among the groups already there: "At the end", or in front of any one of them. */
@Composable
private fun PositionChoice(others: List<Group>, selected: String, onSelect: (String) -> Unit) {
    Text("Position", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        (listOf<Group?>(null) + others).forEach { other ->
            val id = other?.id.orEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == id, role = Role.RadioButton, onClick = { onSelect(id) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == id, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    other?.let { "Before ${it.name}" } ?: "At the end",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A short scrolling list of radio rows: the top level, then each organization and folder with its path. */
@Composable
private fun ParentChoice(title: String, parents: List<Group?>, index: GroupIndex, selected: String, onSelect: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
        parents.forEach { p ->
            val id = p?.id.orEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == id, role = Role.RadioButton, onClick = { onSelect(id) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == id, onClick = null)
                Spacer(Modifier.width(12.dp))
                Icon(
                    when (p?.kind) {
                        null -> Icons.Rounded.VerticalAlignTop
                        GroupKind.ORG -> Icons.Rounded.Business
                        GroupKind.FOLDER -> Icons.Rounded.Folder
                        GroupKind.HOME -> Icons.Rounded.Home
                    },
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    p?.let { index.path(it) } ?: "Top level",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
