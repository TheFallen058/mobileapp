package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rebble.libpebblecommon.database.entity.LAUNCHER_FOLDER_MAX_COUNT
import io.rebble.libpebblecommon.database.entity.LauncherFolderEntity
import io.rebble.libpebblecommon.database.entity.toLauncherFolderName

/** Launcher folder of an app, or null when it sits in the launcher root. */
val CommonApp.folderId: Int?
    get() = (commonAppType as? CommonAppTypeLocal)?.folderId

/** Position of the app in the order synchronised to the watch. */
val CommonApp.order: Int
    get() = (commonAppType as? CommonAppTypeLocal)?.order ?: 0

/**
 * One row of the launcher root list: either an app or a folder.
 *
 * A folder takes the slot of its first member in the app order, mirroring how the watch places
 * it, so what the user arranges here is what they see on the wrist.
 */
sealed interface LauncherRow {
    val key: String

    data class App(val app: CommonApp) : LauncherRow {
        override val key get() = app.uuid.toString()
    }

    data class Folder(val folder: LauncherFolderEntity, val appCount: Int) : LauncherRow {
        override val key get() = "folder-${folder.id}"
    }
}

fun buildLauncherRows(
    apps: List<CommonApp>,
    folders: List<LauncherFolderEntity>,
): List<LauncherRow> {
    val foldersById = folders.associateBy { it.id }
    val appCounts = apps.groupingBy { it.folderId }.eachCount()
    val emitted = mutableSetOf<Int>()

    val rows = apps.mapNotNull { app ->
        val folderId = app.folderId
        // An app pointing at a folder that no longer exists belongs in the root.
        val folder = folderId?.let { foldersById[it] }
            ?: return@mapNotNull LauncherRow.App(app)
        if (!emitted.add(folder.id)) {
            return@mapNotNull null
        }
        LauncherRow.Folder(folder, appCounts[folder.id] ?: 0)
    }

    // Unlike the watch, the phone shows folders that have no apps in them: that is where the
    // user puts apps into them, and where they can delete one they no longer want.
    val empty = folders.filterNot { it.id in emitted }.map { LauncherRow.Folder(it, 0) }
    return rows + empty
}

@Composable
fun LauncherFolderListItem(
    row: LauncherRow.Folder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(42.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(row.folder.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (row.appCount == 1) "1 app" else "${row.appCount} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun LauncherFolderNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.toLauncherFolderName()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
                if (trimmed != name.trim()) {
                    // The watch stores names in a fixed-width field, so say so rather than
                    // silently dropping characters when the configuration is sent.
                    Text(
                        text = "Will be shortened to \"$trimmed\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteLauncherFolderDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$folderName\"?") },
        text = { Text("The apps in it go back to the main list. Nothing is uninstalled.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MoveToLauncherFolderDialog(
    appTitle: String,
    folders: List<LauncherFolderEntity>,
    selectedFolderId: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    var selected by remember { mutableStateOf(selectedFolderId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"$appTitle\"") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Main list") },
                    leadingContent = {
                        RadioButton(selected = selected == null, onClick = { selected = null })
                    },
                    modifier = Modifier.clickable { selected = null },
                )
                folders.forEach { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = {
                            RadioButton(
                                selected = selected == folder.id,
                                onClick = { selected = folder.id },
                            )
                        },
                        modifier = Modifier.clickable { selected = folder.id },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown when the watch cannot hold another folder. */
const val LAUNCHER_FOLDER_LIMIT_MESSAGE =
    "You can have up to $LAUNCHER_FOLDER_MAX_COUNT folders"
