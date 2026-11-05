package com.example.fileexplorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import java.io.File

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
fun WinIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = Color.Unspecified,
) {
    val c = win
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                !enabled -> c.textDisabled
                tint != Color.Unspecified -> tint
                else -> c.text
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CmdButton(
    icon: ImageVector,
    label: String?,
    description: String,
    enabled: Boolean = true,
    dropdown: Boolean = false,
    onClick: () -> Unit,
) {
    val c = win
    val color = if (enabled) c.text else c.textDisabled
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = if (label == null) description else null, tint = color, modifier = Modifier.size(20.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        if (dropdown) {
            Icon(
                Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.textSecondary,
                modifier = Modifier.padding(start = 2.dp).size(16.dp),
            )
        }
    }
}

@Composable
private fun CmdDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(win.divider)
    )
}

@Composable
fun WinMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable () -> Unit,
) {
    val c = win
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        containerColor = if (c.isDark) Color(0xFF2C2C2C) else Color(0xFFF9F9F9),
        shape = RoundedCornerShape(8.dp),
    ) { content() }
}

/** Menu row. [checked] != null shows a tick column like Explorer's radio/check items. */
@Composable
fun WinMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    bold: Boolean = false,
) {
    val c = win
    DropdownMenuItem(
        text = {
            Text(
                text, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) c.text else c.textDisabled,
            )
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (checked != null) {
                    if (checked) Icon(Icons.Filled.Check, null, tint = c.text, modifier = Modifier.size(18.dp))
                } else if (leading != null) {
                    leading()
                } else if (icon != null) {
                    Icon(icon, null, tint = if (enabled) c.text else c.textDisabled, modifier = Modifier.size(18.dp))
                }
            }
        },
        modifier = Modifier.height(40.dp),
    )
}

@Composable
internal fun MenuDivider() = HorizontalDivider(Modifier.padding(vertical = 4.dp), color = win.divider)

// ---------------------------------------------------------------------------
// Address bar: ← → ↑ ⟳ [ This tablet > Internal storage > DCIM > ]  [ Search ]
// ---------------------------------------------------------------------------

private data class Crumb(val label: String, val dir: File?)

private fun crumbsFor(state: UiState): List<Crumb> {
    val list = mutableListOf(Crumb("This tablet", null))
    val dir = state.currentDir ?: return list
    val vol = state.volume
    val stop = vol?.root?.absolutePath ?: "/"
    val segments = ArrayList<File>()
    var f: File? = dir
    while (f != null && f.absolutePath != stop) {
        segments.add(f)
        f = f.parentFile
    }
    if (vol != null) list.add(Crumb(vol.name, vol.root))
    segments.asReversed().forEach { list.add(Crumb(it.name, it)) }
    return list
}

@Composable
fun AddressBar(
    state: UiState,
    compact: Boolean,
    searchOpen: Boolean,
    onMenu: (() -> Unit)?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (File?) -> Unit,
    onPath: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQuery: (String) -> Unit,
) {
    val bs = if (compact) 36.dp else 40.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onMenu != null) WinIconButton(Icons.Filled.Menu, "Navigation pane", onMenu, size = bs)
        WinIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, enabled = state.canGoBack, size = bs)
        WinIconButton(Icons.AutoMirrored.Filled.ArrowForward, "Forward", onForward, enabled = state.canGoForward, size = bs)
        WinIconButton(Icons.Filled.ArrowUpward, "Up", onUp, enabled = !state.isHome, size = bs)
        if (!compact) WinIconButton(Icons.Filled.Refresh, "Refresh", onRefresh, size = bs)
        Spacer(Modifier.width(4.dp))

        if (compact && searchOpen) {
            SearchBox(
                query = state.query,
                onQuery = onQuery,
                placeholder = "Search ${state.locationName}",
                autoFocus = true,
                modifier = Modifier.weight(1f),
            )
            WinIconButton(Icons.Filled.Close, "Close search", onCloseSearch, size = bs)
        } else {
            AddressBox(
                state = state,
                onNavigate = onNavigate,
                onPath = onPath,
                onRefresh = if (compact) onRefresh else null,
                modifier = Modifier.weight(1f),
            )
            if (compact) {
                WinIconButton(Icons.Filled.Search, "Search", onOpenSearch, size = bs)
            } else {
                Spacer(Modifier.width(8.dp))
                SearchBox(
                    query = state.query,
                    onQuery = onQuery,
                    placeholder = "Search ${state.locationName}",
                    modifier = Modifier.width(260.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun AddressBox(
    state: UiState,
    onNavigate: (File?) -> Unit,
    onPath: (String) -> Unit,
    onRefresh: (() -> Unit)?,
    modifier: Modifier,
) {
    val c = win
    val shape = RoundedCornerShape(4.dp)
    var editing by remember(state.currentDir) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(c.field)
            .border(1.dp, if (editing) c.accent else c.fieldBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            // Explorer lets you click the empty part of the address bar and type a path.
            val initial = state.currentDir?.absolutePath ?: "This tablet"
            var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
            val focus = remember { FocusRequester() }
            var hadFocus by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { editing = false; onPath(text.text) }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .focusRequester(focus)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
                    },
            )
            WinIconButton(Icons.Filled.Close, "Cancel", { editing = false }, size = 32.dp, iconSize = 16.dp)
        } else {
            Spacer(Modifier.width(8.dp))
            when {
                state.isHome -> Icon(Icons.Filled.TabletAndroid, null, tint = c.accent, modifier = Modifier.size(16.dp))
                state.volume?.root?.absolutePath == state.currentDir?.absolutePath -> DriveGlyph(18.dp)
                else -> FolderGlyph(18.dp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { editing = true },
                contentAlignment = Alignment.CenterStart,
            ) {
                val crumbs = remember(state.currentDir, state.volumes) { crumbsFor(state) }
                val listState = rememberLazyListState()
                LaunchedEffect(crumbs) { listState.scrollToItem(crumbs.lastIndex) }
                LazyRow(
                    state = listState,
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(start = 2.dp, end = 16.dp),
                ) {
                    itemsIndexed(crumbs) { _, crumb ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                crumb.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.text,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .clickable { onNavigate(crumb.dir) }
                                    .padding(horizontal = 5.dp, vertical = 6.dp),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                tint = c.textSecondary, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            if (onRefresh != null) {
                WinIconButton(Icons.Outlined.Refresh, "Refresh", onRefresh, size = 32.dp, iconSize = 16.dp)
            }
        }
    }
}

@Composable
fun SearchBox(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val c = win
    val shape = RoundedCornerShape(4.dp)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = modifier.height(34.dp).clip(shape).background(c.field).border(1.dp, c.fieldBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    placeholder, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            WinIconButton(Icons.Filled.Close, "Clear search", { onQuery("") }, size = 30.dp, iconSize = 14.dp)
        } else {
            Icon(
                Icons.Filled.Search, null, tint = c.textSecondary,
                modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Command bar: New ▾ | ✂ ⧉ 📋 ✎ ⇪ 🗑 | Sort ▾ | View ▾ | ⋯
// ---------------------------------------------------------------------------

@Composable
fun CommandBar(
    state: UiState,
    compact: Boolean,
    onNewFolder: () -> Unit,
    onNewTextFile: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSort: (SortField?, Boolean?) -> Unit,
    onViewMode: (ViewMode) -> Unit,
    onToggleHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onInvert: () -> Unit,
    onPin: () -> Unit,
    onCopyPath: () -> Unit,
    onProperties: () -> Unit,
    onRefresh: () -> Unit,
    onAssistiveTouch: () -> Unit,
    onExtractAll: () -> Unit,
) {
    val c = win
    val inFolder = !state.isHome && state.searchResults == null && !state.inArchive
    val editable = !state.inArchive
    val sel = state.selected.size
    val selectedHasFolder = state.shown.any { it.isDirectory && it.path in state.selected }

    var newOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var viewOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CmdButton(Icons.Outlined.AddCircleOutline, "New", "New", enabled = inFolder, dropdown = true) { newOpen = true }
            WinMenu(newOpen, { newOpen = false }) {
                WinMenuItem("Folder", { newOpen = false; onNewFolder() }, leading = { FolderGlyph(20.dp) })
                WinMenuItem("Text document", { newOpen = false; onNewTextFile() }, icon = Icons.Outlined.Description)
            }
        }
        if (state.inArchive) {
            // Explorer shows "Extract all" in the command bar inside a compressed folder.
            CmdButton(Icons.Outlined.Unarchive, "Extract all", "Extract all", onClick = onExtractAll)
        }
        CmdDivider()
        WinIconButton(Icons.Outlined.ContentCut, "Cut", onCut, enabled = sel > 0 && editable)
        WinIconButton(Icons.Outlined.ContentCopy, "Copy", onCopy, enabled = sel > 0)
        WinIconButton(Icons.Outlined.ContentPaste, "Paste", onPaste, enabled = inFolder && state.clipboard != null)
        WinIconButton(Icons.Outlined.DriveFileRenameOutline, "Rename", onRename, enabled = sel == 1 && editable)
        WinIconButton(Icons.Outlined.Share, "Share", onShare, enabled = sel > 0)
        WinIconButton(Icons.Outlined.Delete, "Delete", onDelete, enabled = sel > 0 && editable)
        CmdDivider()

        Box {
            CmdButton(Icons.Outlined.SwapVert, if (compact) null else "Sort", "Sort", dropdown = true) { sortOpen = true }
            WinMenu(sortOpen, { sortOpen = false }) {
                SortField.entries.forEach { f ->
                    WinMenuItem(f.label, { sortOpen = false; onSort(f, null) }, checked = state.sortField == f)
                }
                MenuDivider()
                WinMenuItem("Ascending", { sortOpen = false; onSort(null, true) }, checked = state.ascending)
                WinMenuItem("Descending", { sortOpen = false; onSort(null, false) }, checked = !state.ascending)
            }
        }
        Box {
            val viewIcon = if (state.viewMode == ViewMode.DETAILS) Icons.Outlined.Reorder else Icons.Outlined.GridView
            CmdButton(viewIcon, if (compact) null else "View", "View", dropdown = true) { viewOpen = true }
            WinMenu(viewOpen, { viewOpen = false }) {
                WinMenuItem(
                    "Large icons", { viewOpen = false; onViewMode(ViewMode.LARGE_ICONS) },
                    checked = state.viewMode == ViewMode.LARGE_ICONS,
                )
                WinMenuItem(
                    "Details", { viewOpen = false; onViewMode(ViewMode.DETAILS) },
                    checked = state.viewMode == ViewMode.DETAILS,
                )
                MenuDivider()
                WinMenuItem("Hidden items", { viewOpen = false; onToggleHidden() }, checked = state.showHidden)
            }
        }
        CmdDivider()
        Box {
            WinIconButton(Icons.Outlined.MoreHoriz, "See more", { moreOpen = true })
            WinMenu(moreOpen, { moreOpen = false }) {
                val any = state.shown.isNotEmpty() && !state.isHome || state.searchResults?.isNotEmpty() == true
                WinMenuItem("Select all", { moreOpen = false; onSelectAll() }, icon = Icons.Outlined.SelectAll, enabled = any)
                WinMenuItem("Select none", { moreOpen = false; onSelectNone() }, icon = Icons.Outlined.Deselect, enabled = sel > 0)
                WinMenuItem("Invert selection", { moreOpen = false; onInvert() }, icon = Icons.Outlined.FlipToBack, enabled = any)
                MenuDivider()
                WinMenuItem("Pin to Quick access", { moreOpen = false; onPin() }, icon = Icons.Outlined.PushPin, enabled = selectedHasFolder && editable)
                WinMenuItem("Copy as path", { moreOpen = false; onCopyPath() }, icon = Icons.Outlined.Link, enabled = sel > 0 || !state.isHome)
                WinMenuItem("Properties", { moreOpen = false; onProperties() }, icon = Icons.Outlined.Info, enabled = editable && (sel == 1 || (sel == 0 && !state.isHome)))
                MenuDivider()
                WinMenuItem("Refresh", { moreOpen = false; onRefresh() }, icon = Icons.Outlined.Refresh)
                MenuDivider()
                WinMenuItem("Assistive Touch…", { moreOpen = false; onAssistiveTouch() }, icon = Icons.Outlined.TouchApp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status bar: "23 items   2 items selected  1.20 MB"            [≡] [▦]
// ---------------------------------------------------------------------------

@Composable
fun StatusBar(
    state: UiState,
    onViewMode: (ViewMode) -> Unit,
    onIconScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = win
    val count = if (state.isHome && state.searchResults == null) state.quickAccess.size + state.volumes.size else state.shown.size
    val selectedItems = state.shown.filter { it.path in state.selected }
    val text = buildString {
        append("$count item${if (count == 1) "" else "s"}")
        if (state.searching) append("    Searching…")
        if (selectedItems.isNotEmpty()) {
            append("    ${selectedItems.size} item${if (selectedItems.size == 1) "" else "s"} selected")
            if (selectedItems.none { it.isDirectory }) append("  ${humanSize(selectedItems.sumOf { it.size })}")
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        // Icon size slider: 1x .. 3x in quarter steps.
        Icon(Icons.Outlined.PhotoSizeSelectSmall, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
        Slider(
            value = state.iconScale,
            onValueChange = onIconScale,
            valueRange = 1f..3f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.fieldBorder,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .width(120.dp)
                .padding(horizontal = 4.dp)
                .semantics { contentDescription = "Icon size" },
        )
        Icon(Icons.Outlined.PhotoSizeSelectLarge, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        WinIconButton(
            Icons.Outlined.Reorder, "Details", { onViewMode(ViewMode.DETAILS) }, size = 28.dp, iconSize = 16.dp,
            tint = if (state.viewMode == ViewMode.DETAILS) c.accent else c.textSecondary,
        )
        WinIconButton(
            Icons.Outlined.GridView, "Large icons", { onViewMode(ViewMode.LARGE_ICONS) }, size = 28.dp, iconSize = 16.dp,
            tint = if (state.viewMode == ViewMode.LARGE_ICONS) c.accent else c.textSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Navigation pane
// ---------------------------------------------------------------------------

@Composable
fun QuickGlyph(item: QuickItem, size: Dp) {
    val icon = quickIcon(item.kind)
    if (icon == null) {
        FolderGlyph(size)
    } else {
        Icon(icon.first, contentDescription = null, tint = icon.second, modifier = Modifier.size(size))
    }
}

@Composable
fun NavigationPane(
    state: UiState,
    onHome: () -> Unit,
    onOpen: (File) -> Unit,
    onUnpin: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = win
    var phoneExpanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        PaneItem(
            label = "Home",
            selected = state.isHome,
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, null, tint = Color(0xFFF2B84B), modifier = Modifier.size(18.dp)) },
        )
        PaneDivider()
        state.quickAccess.forEach { q ->
            PaneItem(
                label = q.name,
                selected = state.currentDir?.absolutePath == q.dir.absolutePath,
                onClick = { onOpen(q.dir) },
                icon = { QuickGlyph(q, 18.dp) },
                trailing = {
                    if (q.pinnedByUser) {
                        WinIconButton(
                            Icons.Filled.PushPin, "Unpin ${q.name}", { onUnpin(q.dir) },
                            size = 28.dp, iconSize = 14.dp, tint = c.textSecondary,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.PushPin, null, tint = c.textSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp).size(14.dp),
                        )
                    }
                },
            )
        }
        PaneDivider()
        PaneItem(
            label = "This tablet",
            selected = false,
            onClick = { phoneExpanded = !phoneExpanded },
            chevron = if (phoneExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            icon = { Icon(Icons.Filled.TabletAndroid, null, tint = c.accent, modifier = Modifier.size(18.dp)) },
        )
        if (phoneExpanded) {
            state.volumes.forEach { v ->
                PaneItem(
                    label = v.name,
                    selected = state.currentDir?.absolutePath == v.root.absolutePath,
                    onClick = { onOpen(v.root) },
                    indent = 22.dp,
                    icon = { DriveGlyph(20.dp) },
                )
            }
        }
    }
}

@Composable
private fun PaneDivider() =
    HorizontalDivider(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), color = win.divider)

@Composable
private fun PaneItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    indent: Dp = 0.dp,
    chevron: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = win
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) c.paneSelected else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Windows 11 marks the selected pane item with a small accent pill.
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(if (selected) c.accent else Color.Transparent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(6.dp + indent))
        if (chevron != null) {
            Icon(chevron, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(
            label, color = c.text, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
