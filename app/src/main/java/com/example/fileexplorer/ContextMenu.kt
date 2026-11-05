package com.example.fileexplorer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Everything the right-click menu can do. Each action gets the items it applies to. */
class ContextActions(
    val open: (FileItem) -> Unit,
    val openWith: (FileItem) -> Unit,
    val select: (List<FileItem>) -> Unit,
    val cut: (List<FileItem>) -> Unit,
    val copy: (List<FileItem>) -> Unit,
    val rename: (FileItem) -> Unit,
    val share: (List<FileItem>) -> Unit,
    val delete: (List<FileItem>) -> Unit,
    val pin: (List<FileItem>) -> Unit,
    val unpin: (FileItem) -> Unit,
    val isPinned: (FileItem) -> Boolean,
    val copyPath: (List<FileItem>) -> Unit,
    val properties: (FileItem) -> Unit,
    /** True while another app is asking us for a file: editing actions are hidden. */
    val pickMode: Boolean,
)

/**
 * Windows 11 style context menu: a strip of quick-action icons on top
 * (Cut, Copy, Rename, Share, Delete), then the regular commands.
 */
@Composable
fun ItemContextMenu(
    targets: List<FileItem>,
    allSelected: Boolean,
    offset: DpOffset,
    actions: ContextActions,
    onDismiss: () -> Unit,
) {
    val single = targets.singleOrNull()
    fun run(action: () -> Unit): () -> Unit = { onDismiss(); action() }

    WinMenu(expanded = true, onDismiss = onDismiss, offset = offset) {
        if (!actions.pickMode) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                WinIconButton(Icons.Outlined.ContentCut, "Cut", run { actions.cut(targets) })
                WinIconButton(Icons.Outlined.ContentCopy, "Copy", run { actions.copy(targets) })
                WinIconButton(
                    Icons.Outlined.DriveFileRenameOutline, "Rename",
                    run { single?.let(actions.rename) }, enabled = single != null,
                )
                WinIconButton(
                    Icons.Outlined.Share, "Share",
                    run { actions.share(targets) }, enabled = targets.any { !it.isDirectory },
                )
                WinIconButton(Icons.Outlined.Delete, "Delete", run { actions.delete(targets) })
            }
            MenuDivider()
        }

        if (single != null) {
            WinMenuItem(
                "Open", run { actions.open(single) }, bold = true,
                icon = if (single.isDirectory) Icons.Outlined.FolderOpen else Icons.Outlined.FileOpen,
            )
            if (!single.isDirectory && !actions.pickMode) {
                WinMenuItem("Open with", run { actions.openWith(single) }, icon = Icons.Outlined.Apps)
            }
        }
        if (!allSelected) {
            WinMenuItem("Select", run { actions.select(targets) }, icon = Icons.Outlined.CheckBox)
        }
        if (!actions.pickMode) {
            if (single != null && single.isDirectory) {
                if (actions.isPinned(single)) {
                    WinMenuItem("Unpin from Quick access", run { actions.unpin(single) }, icon = Icons.Outlined.PushPin)
                } else {
                    WinMenuItem("Pin to Quick access", run { actions.pin(targets) }, icon = Icons.Outlined.PushPin)
                }
            } else if (targets.any { it.isDirectory }) {
                WinMenuItem("Pin to Quick access", run { actions.pin(targets) }, icon = Icons.Outlined.PushPin)
            }
            WinMenuItem("Copy as path", run { actions.copyPath(targets) }, icon = Icons.Outlined.Link)
        }
        MenuDivider()
        WinMenuItem(
            "Properties", run { single?.let(actions.properties) },
            icon = Icons.Outlined.Info, enabled = single != null,
        )
    }
}
