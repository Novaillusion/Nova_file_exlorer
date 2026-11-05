package com.example.fileexplorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------
// Home page: Quick access + Devices and drives
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomePage(state: UiState, compact: Boolean, onOpen: (File) -> Unit) {
    var quickOpen by rememberSaveable { mutableStateOf(true) }
    var drivesOpen by rememberSaveable { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        SectionHeader("Quick access", quickOpen) { quickOpen = !quickOpen }
        if (quickOpen) {
            FlowRow(Modifier.fillMaxWidth()) {
                state.quickAccess.forEach { q ->
                    QuickTile(
                        q,
                        modifier = if (compact) Modifier.fillMaxWidth(0.5f) else Modifier.width(230.dp),
                        onClick = { onOpen(q.dir) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader("Devices and drives", drivesOpen) { drivesOpen = !drivesOpen }
        if (drivesOpen) {
            FlowRow(Modifier.fillMaxWidth()) {
                state.volumes.forEach { v ->
                    DriveTile(
                        v,
                        modifier = if (compact) Modifier.fillMaxWidth() else Modifier.width(300.dp),
                        onClick = { onOpen(v.root) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val c = win
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(title, color = c.text, fontSize = 15.sp)
    }
}

@Composable
private fun QuickTile(item: QuickItem, modifier: Modifier, onClick: () -> Unit) {
    val c = win
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickGlyph(item, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.pinnedByUser) item.dir.parentFile?.name.orEmpty() else "This phone",
                    color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.PushPin, null, tint = c.textSecondary, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun DriveTile(volume: VolumeInfo, modifier: Modifier, onClick: () -> Unit) {
    val c = win
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DriveGlyph(50.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(volume.name, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // The capacity bar: blue, turning red when the drive is over 90% full.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(c.usageTrack)
                    .border(1.dp, Color(0xFFBCBCBC))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(volume.usedFraction)
                        .background(if (volume.usedFraction > 0.9f) c.usageFull else c.usageFill)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${humanSize(volume.freeBytes)} free of ${humanSize(volume.totalBytes)}",
                color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Folder contents: Details view
// ---------------------------------------------------------------------------

/** "Internal storage/DCIM/Camera" style location for search results. */
private fun locationLabel(state: UiState, file: File): String {
    val parent = file.parentFile?.absolutePath ?: return ""
    val vol = state.volumes
        .filter { parent == it.root.absolutePath || parent.startsWith(it.root.absolutePath + "/") }
        .maxByOrNull { it.root.absolutePath.length }
        ?: return parent
    return vol.name + parent.removePrefix(vol.root.absolutePath)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailsView(
    state: UiState,
    wide: Boolean,
    onOpen: (FileItem) -> Unit,
    onToggle: (FileItem) -> Unit,
    onSort: (SortField) -> Unit,
    dimmed: (FileItem) -> Boolean = { false },
    foldersAlwaysOpen: Boolean = false,
) {
    val c = win
    val inSelection = state.selected.isNotEmpty()
    val searching = state.searchResults != null
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Column(Modifier.fillMaxSize()) {
        // Column headers - tap to sort, tap again to reverse, like Explorer.
        Row(
            Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Name", SortField.NAME, state, onSort, Modifier.weight(1f).padding(start = (if (inSelection) 32.dp else 0.dp) + (if (wide) 30.dp else 42.dp)))
            if (wide) {
                HeaderSeparator()
                HeaderCell("Date modified", SortField.DATE, state, onSort, Modifier.width(150.dp))
                HeaderSeparator()
                HeaderCell("Type", SortField.TYPE, state, onSort, Modifier.width(150.dp))
                HeaderSeparator()
                HeaderCell("Size", SortField.SIZE, state, onSort, Modifier.width(90.dp))
            } else {
                HeaderCell("Date", SortField.DATE, state, onSort, Modifier)
                HeaderCell("Size", SortField.SIZE, state, onSort, Modifier)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 16.dp),
        ) {
            items(state.shown, key = { it.path }) { item ->
                val selected = item.path in state.selected
                val date = dateFormat.format(Date(item.lastModified))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (wide) 36.dp else 54.dp)
                        .alpha(if (dimmed(item)) 0.4f else 1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) c.selection else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (inSelection && !(foldersAlwaysOpen && item.isDirectory)) onToggle(item) else onOpen(item)
                            },
                            onLongClick = { onToggle(item) },
                        )
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (inSelection) {
                        SelectionBox(selected)
                        Spacer(Modifier.width(12.dp))
                    }
                    ItemIcon(item, if (wide) 20.dp else 32.dp, thumbnails = !wide)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name, color = c.text, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val sub = when {
                            searching -> locationLabel(state, item.file)
                            wide -> null
                            item.isDirectory -> date
                            else -> "$date    ${winSize(item.size)}"
                        }
                        if (sub != null) {
                            Text(
                                sub, color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (wide) {
                        CellText(date, Modifier.width(150.dp).padding(start = 9.dp))
                        CellText(typeName(item), Modifier.width(150.dp).padding(start = 9.dp))
                        CellText(
                            if (item.isDirectory) "" else winSize(item.size),
                            Modifier.width(90.dp).padding(end = 4.dp), TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(label: String, field: SortField, state: UiState, onSort: (SortField) -> Unit, modifier: Modifier) {
    val c = win
    val active = state.sortField == field
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .clickable { onSort(field) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, color = c.textSecondary, style = MaterialTheme.typography.bodySmall,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
        )
        if (active) {
            Icon(
                if (state.ascending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (state.ascending) "ascending" else "descending",
                tint = c.textSecondary, modifier = Modifier.padding(start = 2.dp).size(14.dp),
            )
        }
    }
}

@Composable
private fun HeaderSeparator() {
    Box(Modifier.width(1.dp).height(18.dp).background(win.divider))
}

@Composable
private fun CellText(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text, color = win.textSecondary, style = MaterialTheme.typography.bodySmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = modifier,
    )
}

@Composable
private fun SelectionBox(selected: Boolean) {
    val c = win
    Icon(
        if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) c.accent else c.textSecondary,
        modifier = Modifier.size(20.dp),
    )
}

// ---------------------------------------------------------------------------
// Folder contents: Large icons view
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LargeIconsView(
    state: UiState,
    onOpen: (FileItem) -> Unit,
    onToggle: (FileItem) -> Unit,
    dimmed: (FileItem) -> Boolean = { false },
    foldersAlwaysOpen: Boolean = false,
) {
    val c = win
    val inSelection = state.selected.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(state.shown, key = { it.path }) { item ->
            val selected = item.path in state.selected
            Box(
                Modifier
                    .alpha(if (dimmed(item)) 0.4f else 1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) c.selection else Color.Transparent)
                    .combinedClickable(
                        onClick = {
                            if (inSelection && !(foldersAlwaysOpen && item.isDirectory)) onToggle(item) else onOpen(item)
                        },
                        onLongClick = { onToggle(item) },
                    )
                    .padding(6.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        ItemIcon(item, if (item.isDirectory) 68.dp else 60.dp, thumbnails = true)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.name, color = c.text, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (inSelection) {
                    Box(Modifier.align(Alignment.TopStart)) { SelectionBox(selected) }
                }
            }
        }
    }
}

@Composable
fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = win.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
