package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

/**
 * Contents of one launcher folder. Reordering here changes the app order the watch already
 * synchronises, which is what determines the order inside the folder.
 */
@Composable
fun LauncherFolderScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    folderId: Int,
) {
    val sharedViewModel: SharedLockerViewModel = koinInject()
    sharedViewModel.Init()
    val libPebble = rememberLibPebble()
    val scope = rememberCoroutineScope()

    val folders by remember { libPebble.getLauncherFolders() }.collectAsState(emptyList())
    val folder = folders.firstOrNull { it.id == folderId }

    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(folder?.name) {
        topBarParams.searchAvailable(null)
        topBarParams.title(folder?.name ?: "")
        topBarParams.actions {
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Rename folder")
            }
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete folder")
            }
        }
    }

    // The folder was deleted from under us.
    LaunchedEffect(folders, folder) {
        if (folders.isNotEmpty() && folder == null) {
            navBarNav.goBack()
        }
    }

    val allApps = loadLockerEntries(
        type = AppType.Watchapp,
        searchQuery = "",
        watchType = sharedViewModel.watchType.value,
        showIncompatible = sharedViewModel.showIncompatible.value,
        showScaled = sharedViewModel.showScaled.value,
        limit = 700,
    )
    if (allApps == null || folder == null) {
        return
    }

    val apps = remember(allApps, folderId) { allApps.filter { it.folderId == folderId } }
    var mutableApps by remember(apps) { mutableStateOf(apps) }
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    var prevNeighbourUuid by remember { mutableStateOf<Uuid?>(null) }
    var dragMoved by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        mutableApps = mutableApps.toMutableList().apply { add(to.index, removeAt(from.index)) }
        prevNeighbourUuid = if (to.index > 0) mutableApps[to.index - 1].uuid else null
        dragMoved = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    fun onDragStopped(uuid: Uuid) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
        if (!dragMoved) return
        dragMoved = false
        val neighbour = prevNeighbourUuid
        prevNeighbourUuid = null
        // Same absolute-position rule as the root list: the visible index is not the order index
        // because this list only shows one folder's apps.
        val newOrder = if (neighbour == null) {
            apps.firstOrNull()?.order ?: 0
        } else {
            val neighbourOrder = apps.firstOrNull { it.uuid == neighbour }?.order ?: 0
            val draggedOrder = apps.firstOrNull { it.uuid == uuid }?.order ?: 0
            if (draggedOrder > neighbourOrder) neighbourOrder + 1 else neighbourOrder
        }
        scope.launch { libPebble.setAppOrder(uuid, newOrder) }
    }

    Column {
        if (mutableApps.isEmpty()) {
            Text(
                text = "This folder is empty. Move apps into it from your collection.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            items(mutableApps, key = { it.uuid }) { entry ->
                ReorderableItem(reorderableState, key = entry.uuid) { isDragging ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = { onDragStopped(entry.uuid) },
                            )
                            .shake(isDragging)
                            .fillMaxWidth(),
                    ) {
                        NativeWatchfaceListItem(
                            entry = entry,
                            onClick = {
                                navBarNav.navigateTo(
                                    PebbleNavBarRoutes.LockerAppRoute(
                                        uuid = entry.uuid.toString(),
                                        storedId = entry.storeId,
                                        storeSource = entry.appstoreSource?.id,
                                    )
                                )
                            },
                            topBarParams = topBarParams,
                            highlightInLocker = false,
                        )
                        IconButton(onClick = {
                            scope.launch { libPebble.setAppFolder(entry.uuid, null) }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Move ${entry.title} out of the folder",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        LauncherFolderNameDialog(
            title = "Rename folder",
            initialName = folder.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                showRename = false
                scope.launch { libPebble.renameLauncherFolder(folderId, name) }
            },
        )
    }

    if (showDelete) {
        DeleteLauncherFolderDialog(
            folderName = folder.name,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                scope.launch {
                    libPebble.deleteLauncherFolder(folderId)
                    navBarNav.goBack()
                }
            },
        )
    }
}
