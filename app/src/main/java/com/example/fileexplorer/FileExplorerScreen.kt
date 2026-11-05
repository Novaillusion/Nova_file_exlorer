package com.example.fileexplorer

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

private sealed interface DialogState {
    data object NewFolder : DialogState
    data class Rename(val item: FileItem) : DialogState
    data class ConfirmDelete(val count: Int) : DialogState
}

@Composable
fun FileExplorerScreen(vm: FileExplorerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }

    // Reload on every resume so changes made by other apps show up.
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(msg)
        vm.messageShown()
    }

    val inSelection = state.selected.isNotEmpty()

    BackHandler(enabled = inSelection || searching || !state.atRoot) {
        when {
            inSelection -> vm.clearSelection()
            searching -> { searching = false; vm.setQuery("") }
            else -> vm.navigateUp()
        }
    }

    val visibleItems = remember(state.items, state.query) {
        val q = state.query.trim()
        if (q.isEmpty()) state.items else state.items.filter { it.name.contains(q, ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            when {
                inSelection -> SelectionTopBar(
                    count = state.selected.size,
                    onClose = vm::clearSelection,
                    onSelectAll = { vm.selectAll(visibleItems) },
                    onCopy = { vm.copySelection(cut = false) },
                    onCut = { vm.copySelection(cut = true) },
                    onDelete = { dialog = DialogState.ConfirmDelete(state.selected.size) },
                    onShare = { FileIntents.share(context, vm.selectedFiles()) },
                    onRename = if (state.selected.size == 1) {
                        { vm.selectedItems().firstOrNull()?.let { dialog = DialogState.Rename(it) } }
                    } else null,
                )

                searching -> SearchTopBar(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                    onClose = { searching = false; vm.setQuery("") },
                )

                else -> BrowseTopBar(
                    title = if (state.atRoot) "Internal storage" else state.currentDir.name,
                    canGoUp = !state.atRoot,
                    sort = state.sort,
                    showHidden = state.showHidden,
                    onUp = vm::navigateUp,
                    onSearch = { searching = true },
                    onSort = vm::setSort,
                    onToggleHidden = vm::toggleHidden,
                    onRefresh = vm::refresh,
                )
            }
        },
        floatingActionButton = {
            val clip = state.clipboard
            when {
                clip != null -> Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = vm::clearClipboard) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                    ExtendedFloatingActionButton(
                        onClick = vm::paste,
                        icon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                        text = { Text("${if (clip.cut) "Move" else "Paste"} ${clip.files.size} here") },
                    )
                }

                !inSelection -> FloatingActionButton(onClick = { dialog = DialogState.NewFolder }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Breadcrumbs(root = state.rootDir, current = state.currentDir, onNavigate = vm::navigateTo)
            HorizontalDivider()
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading && state.items.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    visibleItems.isEmpty() -> Text(
                        if (state.query.isBlank()) "This folder is empty" else "No matches for \"${state.query}\"",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        items(visibleItems, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = item.path in state.selected,
                                onClick = {
                                    when {
                                        inSelection -> vm.toggleSelection(item)
                                        item.isDirectory -> vm.navigateTo(item.file)
                                        else -> FileIntents.open(context, item.file)
                                    }
                                },
                                onLongClick = { vm.toggleSelection(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        DialogState.NewFolder -> NameDialog(
            title = "New folder",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { vm.createFolder(it); dialog = null },
            onDismiss = { dialog = null },
        )

        is DialogState.Rename -> NameDialog(
            title = "Rename",
            initialName = d.item.name,
            confirmLabel = "Rename",
            selectBaseName = !d.item.isDirectory,
            onConfirm = { vm.rename(d.item, it); dialog = null },
            onDismiss = { dialog = null },
        )

        is DialogState.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${d.count} item(s)?") },
            text = { Text("Folders are deleted with everything inside them. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteSelection(); dialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        null -> Unit
    }
}

// ---------------------------------------------------------------------------
// Top bars
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(
    title: String,
    canGoUp: Boolean,
    sort: SortOrder,
    showHidden: Boolean,
    onUp: () -> Unit,
    onSearch: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onToggleHidden: () -> Unit,
    onRefresh: () -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (canGoUp) {
                IconButton(onClick = onUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }

            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onSort(option); sortOpen = false },
                            trailingIcon = if (option == sort) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                        leadingIcon = {
                            Icon(
                                if (showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        },
                        onClick = { onToggleHidden(); moreOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = { onRefresh(); moreOpen = false },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: (() -> Unit)?,
) {
    var moreOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all") }
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = onCut) { Icon(Icons.Filled.ContentCut, contentDescription = "Cut") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            Box {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { onShare(); moreOpen = false },
                    )
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { onRename(); moreOpen = false },
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search this folder") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Breadcrumbs & rows
// ---------------------------------------------------------------------------

@Composable
private fun Breadcrumbs(root: File, current: File, onNavigate: (File) -> Unit) {
    val crumbs = remember(root, current) {
        val list = ArrayList<File>()
        var f: File? = current
        while (f != null && f.absolutePath != root.absolutePath) {
            list.add(f)
            f = f.parentFile
        }
        list.add(root)
        list.reverse()
        list
    }
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs) { listState.animateScrollToItem(crumbs.lastIndex) }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(crumbs) { index, dir ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val isLast = index == crumbs.lastIndex
                TextButton(
                    onClick = { onNavigate(dir) },
                    enabled = !isLast,
                    colors = ButtonDefaults.textButtonColors(
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(if (index == 0) "Storage" else dir.name, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val date = dateFormat.format(Date(item.lastModified))
    val subtitle = if (item.isDirectory) {
        val n = item.childCount
        if (n == null) date else "$n item${if (n == 1) "" else "s"} · $date"
    } else {
        "${Formatter.formatShortFileSize(context, item.size)} · $date"
    }

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        leadingContent = {
            Icon(
                iconFor(item),
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
        } else null,
    )
}

@Suppress("DEPRECATION")
private fun iconFor(item: FileItem): ImageVector =
    if (item.isDirectory) Icons.Filled.Folder
    else when (item.file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg" -> Icons.Filled.Image
        "mp4", "mkv", "webm", "avi", "mov", "3gp" -> Icons.Filled.Movie
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus" -> Icons.Filled.AudioFile
        "pdf" -> Icons.Filled.PictureAsPdf
        "zip", "rar", "7z", "tar", "gz" -> Icons.Filled.FolderZip
        "apk" -> Icons.Filled.Android
        "txt", "md", "log", "csv", "json", "xml", "doc", "docx", "odt" -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    selectBaseName: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // When renaming a file, pre-select just "photo" in "photo.jpg".
    val dot = initialName.lastIndexOf('.')
    val selEnd = if (selectBaseName && dot > 0) dot else initialName.length
    var value by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, selEnd))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val name = value.text.trim()
    val error = when {
        '/' in name -> "Name can't contain /"
        name == "." || name == ".." -> "Invalid name"
        else -> null
    }
    val valid = name.isNotEmpty() && error == null && name != initialName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
