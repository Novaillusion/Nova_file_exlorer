package com.example.fileexplorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File

private sealed interface DialogState {
    data class NewItem(val folder: Boolean, val suggested: String) : DialogState
    data class Rename(val item: FileItem) : DialogState
    data class ConfirmDelete(val items: List<FileItem>) : DialogState
    data class Properties(val item: FileItem) : DialogState
}

@Composable
fun ExplorerScreen(
    vm: ExplorerViewModel = viewModel(),
    pick: PickRequest? = null,
    onPicked: (List<File>) -> Unit = {},
    onCancelPick: () -> Unit = {},
    startDir: File? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = win
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    // Another app asked us to show a specific folder.
    var handledStart by rememberSaveable(startDir?.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(startDir) {
        if (startDir != null && !handledStart) {
            vm.navigateTo(startDir)
            handledStart = true
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.messageShown()
    }

    BackHandler(
        enabled = drawer.isOpen || state.selected.isNotEmpty() || searchOpen ||
            state.query.isNotEmpty() || state.canGoBack || !state.isHome
    ) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            state.selected.isNotEmpty() -> vm.selectNone()
            searchOpen || state.query.isNotEmpty() -> { searchOpen = false; vm.setQuery("") }
            state.canGoBack -> vm.goBack()
            else -> vm.navigateUp()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.window)) {
        val permanentPane = maxWidth >= 840.dp
        val compact = maxWidth < 600.dp
        val closeDrawer: () -> Unit = { scope.launch { drawer.close() } }

        val pane: @Composable (Modifier) -> Unit = { m ->
            NavigationPane(
                state = state,
                onHome = { vm.goHome(); closeDrawer() },
                onOpen = { vm.navigateTo(it); closeDrawer() },
                onUnpin = vm::unpin,
                modifier = m,
            )
        }
        val main: @Composable (Modifier, (() -> Unit)?) -> Unit = { m, onMenu ->
            MainArea(
                state = state,
                vm = vm,
                compact = compact,
                searchOpen = searchOpen,
                setSearchOpen = { searchOpen = it },
                onMenu = onMenu,
                onDialog = { dialog = it },
                pick = pick,
                onPicked = onPicked,
                onCancelPick = onCancelPick,
                modifier = m,
            )
        }

        if (permanentPane) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(260.dp).fillMaxHeight().background(c.pane).systemBarsPadding()) {
                    pane(Modifier)
                }
                VerticalDivider(color = c.divider)
                main(Modifier.weight(1f), null)
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = c.pane,
                        drawerShape = RectangleShape,
                        modifier = Modifier.width(290.dp),
                    ) { pane(Modifier) }
                },
            ) {
                main(Modifier.fillMaxSize(), { scope.launch { drawer.open() } })
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp),
        )
    }

    when (val d = dialog) {
        is DialogState.NewItem -> NameDialog(
            title = if (d.folder) "New folder" else "New text document",
            initialName = d.suggested,
            confirmLabel = "Create",
            selectBaseName = !d.folder,
            requireChange = false,
            onConfirm = {
                if (d.folder) vm.createFolder(it) else vm.createTextFile(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is DialogState.Rename -> NameDialog(
            title = "Rename",
            initialName = d.item.name,
            confirmLabel = "Rename",
            selectBaseName = !d.item.isDirectory,
            requireChange = true,
            onConfirm = { vm.rename(d.item, it); dialog = null },
            onDismiss = { dialog = null },
        )

        is DialogState.ConfirmDelete -> DeleteDialog(
            items = d.items,
            onConfirm = { vm.deleteSelection(); dialog = null },
            onDismiss = { dialog = null },
        )

        is DialogState.Properties -> PropertiesDialog(
            item = d.item,
            location = d.item.file.parent.orEmpty(),
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

@Composable
private fun MainArea(
    state: UiState,
    vm: ExplorerViewModel,
    compact: Boolean,
    searchOpen: Boolean,
    setSearchOpen: (Boolean) -> Unit,
    onMenu: (() -> Unit)?,
    onDialog: (DialogState) -> Unit,
    pick: PickRequest?,
    onPicked: (List<File>) -> Unit,
    onCancelPick: () -> Unit,
    modifier: Modifier,
) {
    val c = win
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val openItem: (FileItem) -> Unit = { item ->
        when {
            item.isDirectory -> vm.navigateTo(item.file)
            pick == null -> FileIntents.open(context, item.file)
            !pick.matches(item.file) -> vm.showMessage("${typeName(item)} isn't a type this app can use")
            pick.allowMultiple -> vm.toggleSelection(item)
            else -> onPicked(listOf(item.file))
        }
    }
    // While picking for another app, only matching files can be selected.
    val toggleItem: (FileItem) -> Unit = { item ->
        if (pick == null || (!item.isDirectory && pick.matches(item.file))) vm.toggleSelection(item)
    }
    val dimmed: (FileItem) -> Boolean = { item ->
        pick != null && !item.isDirectory && !pick.matches(item.file)
    }

    Column(
        modifier
            .background(c.window)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    ) {
        // Title-bar area: address bar + command bar on the "chrome" colour.
        Column(Modifier.fillMaxWidth().background(c.chrome).statusBarsPadding()) {
            AddressBar(
                state = state,
                compact = compact,
                searchOpen = searchOpen,
                onMenu = onMenu,
                onBack = vm::goBack,
                onForward = vm::goForward,
                onUp = vm::navigateUp,
                onRefresh = vm::refresh,
                onNavigate = vm::navigateTo,
                onPath = vm::goToPath,
                onOpenSearch = { setSearchOpen(true) },
                onCloseSearch = { setSearchOpen(false); vm.setQuery("") },
                onQuery = vm::setQuery,
            )
            HorizontalDivider(color = c.divider)
            CommandBar(
                state = state,
                compact = compact,
                onNewFolder = { onDialog(DialogState.NewItem(folder = true, suggested = vm.suggestedName(true))) },
                onNewTextFile = { onDialog(DialogState.NewItem(folder = false, suggested = vm.suggestedName(false))) },
                onCut = { vm.copySelection(cut = true) },
                onCopy = { vm.copySelection(cut = false) },
                onPaste = vm::paste,
                onRename = { vm.selectedItems().singleOrNull()?.let { onDialog(DialogState.Rename(it)) } },
                onShare = { FileIntents.share(context, vm.selectedFiles()) },
                onDelete = { vm.selectedItems().takeIf { it.isNotEmpty() }?.let { onDialog(DialogState.ConfirmDelete(it)) } },
                onSort = { field, asc ->
                    vm.setSort(field ?: state.sortField, asc ?: state.ascending)
                },
                onViewMode = vm::setViewMode,
                onToggleHidden = vm::toggleHidden,
                onSelectAll = vm::selectAll,
                onSelectNone = vm::selectNone,
                onInvert = vm::invertSelection,
                onPin = vm::pinSelection,
                onCopyPath = {
                    val paths = vm.selectedFiles().map { it.absolutePath }
                        .ifEmpty { listOfNotNull(state.currentDir?.absolutePath) }
                    if (paths.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(paths.joinToString("\n") { "\"$it\"" }))
                        vm.showMessage("Path copied")
                    }
                },
                onProperties = {
                    val target = vm.selectedItems().singleOrNull() ?: state.currentDir?.let(FileItem::from)
                    target?.let { onDialog(DialogState.Properties(it)) }
                },
                onRefresh = vm::refresh,
            )
        }
        HorizontalDivider(color = c.divider)
        if (state.loading || state.searching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = if (state.searching) Color(0xFF16C60C) else c.accent, // Explorer's green search progress
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isHome && state.searchResults == null ->
                    HomePage(state, compact, onOpen = vm::navigateTo)

                state.shown.isEmpty() && !state.loading -> EmptyMessage(
                    when {
                        state.searchResults == null -> "This folder is empty."
                        state.searching -> "Searching…"
                        else -> "No items match your search."
                    }
                )

                state.viewMode == ViewMode.DETAILS -> DetailsView(
                    state = state,
                    wide = !compact,
                    onOpen = openItem,
                    onToggle = toggleItem,
                    onSort = vm::sortByColumn,
                    dimmed = dimmed,
                    foldersAlwaysOpen = pick != null,
                )

                else -> LargeIconsView(
                    state = state, onOpen = openItem, onToggle = toggleItem,
                    dimmed = dimmed, foldersAlwaysOpen = pick != null,
                )
            }
        }

        HorizontalDivider(color = c.divider)
        if (pick != null) {
            OpenDialogBar(
                pick = pick,
                selected = vm.selectedItems(),
                onOpen = { onPicked(vm.selectedFiles()) },
                onCancel = onCancelPick,
            )
            HorizontalDivider(color = c.divider)
        }
        StatusBar(state, onViewMode = vm::setViewMode, modifier = Modifier.navigationBarsPadding())
    }
}
