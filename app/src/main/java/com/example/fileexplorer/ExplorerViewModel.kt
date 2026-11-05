package com.example.fileexplorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Clipboard(val files: List<File>, val cut: Boolean)

enum class ViewMode { DETAILS, LARGE_ICONS }

data class UiState(
    /** null = the "This phone" page (Quick access + Devices and drives). */
    val currentDir: File? = null,
    val volumes: List<VolumeInfo> = emptyList(),
    val quickAccess: List<QuickItem> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val selected: Set<String> = emptySet(),
    val clipboard: Clipboard? = null,
    val sortField: SortField = SortField.NAME,
    val ascending: Boolean = true,
    val viewMode: ViewMode = ViewMode.DETAILS,
    val showHidden: Boolean = false,
    val query: String = "",
    /** Non-null while a search is active; fills in progressively. */
    val searchResults: List<FileItem>? = null,
    val searching: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val message: String? = null,
) {
    val isHome: Boolean get() = currentDir == null

    /** What the content area shows right now. */
    val shown: List<FileItem> get() = searchResults ?: items

    /** The volume the current folder lives on (deepest matching root). */
    val volume: VolumeInfo?
        get() = currentDir?.let { dir ->
            val p = dir.absolutePath
            volumes.filter { p == it.root.absolutePath || p.startsWith(it.root.absolutePath + "/") }
                .maxByOrNull { it.root.absolutePath.length }
        }

    val locationName: String
        get() = when {
            currentDir == null -> "This phone"
            volume?.root?.absolutePath == currentDir.absolutePath -> volume!!.name
            else -> currentDir.name
        }
}

class ExplorerViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val quickStore = QuickAccessStore(app)

    private val _state = MutableStateFlow(
        UiState(
            currentDir = saved.get<String>(KEY_DIR)
                ?.takeIf { it.isNotEmpty() }
                ?.let(::File)
                ?.takeIf { it.isDirectory },
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val back = ArrayDeque<File?>()
    private val forward = ArrayDeque<File?>()
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    // ---------------------------------------------------------------- loading

    fun refresh() {
        val s = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val vols = withContext(Dispatchers.IO) { Volumes.load(getApplication()) }
            val quick = withContext(Dispatchers.IO) { quickStore.items() }
            val items = withContext(Dispatchers.IO) {
                s.currentDir?.let { FileOperations.list(it, s.showHidden, s.sortField, s.ascending) } ?: emptyList()
            }
            _state.update { st ->
                st.copy(volumes = vols, quickAccess = quick, items = items, loading = false)
                    .let { it.copy(selected = it.selected.filterTo(HashSet()) { p -> it.shown.any { f -> f.path == p } }) }
            }
        }
        if (s.query.isNotBlank()) runSearch(s.query, debounce = false)
    }

    // ------------------------------------------------------------- navigation

    private fun go(dir: File?, record: Boolean) {
        val current = _state.value.currentDir
        if (dir != null && (!dir.isDirectory || !dir.canRead())) {
            _state.update { it.copy(message = "Can't open \"${dir.name}\"") }
            syncHistory()
            return
        }
        if (record && current?.absolutePath != dir?.absolutePath) {
            back.addLast(current)
            forward.clear()
        }
        saved[KEY_DIR] = dir?.absolutePath ?: ""
        searchJob?.cancel()
        _state.update {
            it.copy(
                currentDir = dir, items = emptyList(), selected = emptySet(),
                query = "", searchResults = null, searching = false,
            )
        }
        syncHistory()
        refresh()
    }

    private fun syncHistory() =
        _state.update { it.copy(canGoBack = back.isNotEmpty(), canGoForward = forward.isNotEmpty()) }

    fun navigateTo(dir: File?) = go(dir, record = true)

    fun goHome() = go(null, record = true)

    fun goBack() {
        if (back.isEmpty()) return
        forward.addLast(_state.value.currentDir)
        go(back.removeLast(), record = false)
    }

    fun goForward() {
        if (forward.isEmpty()) return
        back.addLast(_state.value.currentDir)
        go(forward.removeLast(), record = false)
    }

    fun navigateUp() {
        val s = _state.value
        val dir = s.currentDir ?: return
        val atVolumeRoot = s.volume?.root?.absolutePath == dir.absolutePath
        go(if (atVolumeRoot) null else dir.parentFile, record = true)
    }

    /** Typed into the address bar. */
    fun goToPath(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.equals("This phone", ignoreCase = true)) {
            goHome()
            return
        }
        val f = File(t)
        when {
            f.isDirectory -> navigateTo(f)
            else -> _state.update { it.copy(message = "Can't find '$t'. Check the spelling and try again.") }
        }
    }

    // ----------------------------------------------------------------- search

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        runSearch(q, debounce = true)
    }

    private fun runSearch(q: String, debounce: Boolean) {
        searchJob?.cancel()
        val query = q.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        val s = _state.value
        val roots = s.currentDir?.let { listOf(it) } ?: s.volumes.map { it.root }
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            _state.update { it.copy(searching = true, searchResults = emptyList(), selected = emptySet()) }
            withContext(Dispatchers.IO) {
                val found = ArrayList<FileItem>()
                var lastEmit = 0
                outer@ for (root in roots) {
                    val walk = root.walkTopDown().onEnter { it == root || s.showHidden || !it.isHidden }
                    for (f in walk) {
                        ensureActive()
                        if (f == root || (!s.showHidden && f.isHidden)) continue
                        if (f.name.contains(query, ignoreCase = true)) {
                            found.add(FileItem.from(f))
                            if (found.size >= MAX_RESULTS) break@outer
                            if (found.size - lastEmit >= 25) {
                                lastEmit = found.size
                                val snapshot = found.toList()
                                _state.update { it.copy(searchResults = snapshot) }
                            }
                        }
                    }
                }
                val sorted = found.sortedWith(FileOperations.comparator(s.sortField, s.ascending))
                _state.update { it.copy(searchResults = sorted, searching = false) }
            }
        }
    }

    // -------------------------------------------------------------- selection

    fun toggleSelection(item: FileItem) = _state.update {
        it.copy(selected = if (item.path in it.selected) it.selected - item.path else it.selected + item.path)
    }

    fun selectAll() = _state.update { it.copy(selected = it.shown.mapTo(HashSet()) { f -> f.path }) }

    fun selectNone() = _state.update { it.copy(selected = emptySet()) }

    fun invertSelection() = _state.update { st ->
        st.copy(selected = st.shown.map { it.path }.filterTo(HashSet()) { it !in st.selected })
    }

    fun selectedItems(): List<FileItem> = _state.value.let { s -> s.shown.filter { it.path in s.selected } }

    fun selectedFiles(): List<File> = selectedItems().map { it.file }

    // ------------------------------------------------------- clipboard & edits

    fun copySelection(cut: Boolean) = copy(selectedFiles(), cut)

    fun copy(files: List<File>, cut: Boolean) {
        if (files.isEmpty()) return
        _state.update { it.copy(clipboard = Clipboard(files, cut), selected = emptySet()) }
    }

    fun paste() {
        val s = _state.value
        val clip = s.clipboard ?: return
        val dest = s.currentDir ?: return
        launchOp({ FileOperations.paste(clip.files, dest, move = clip.cut) }) { n ->
            if (clip.cut) _state.update { it.copy(clipboard = null) }
            "${if (clip.cut) "Moved" else "Copied"} $n item(s)"
        }
    }

    fun suggestedName(folder: Boolean): String {
        val dir = _state.value.currentDir ?: return ""
        return if (folder) FileOperations.nextFreeName(dir, "New folder")
        else FileOperations.nextFreeName(dir, "New Text Document", ".txt")
    }

    fun createFolder(name: String) {
        val dir = _state.value.currentDir ?: return
        launchOp({ FileOperations.createFolder(dir, name.trim()) }) { null }
    }

    fun createTextFile(name: String) {
        val dir = _state.value.currentDir ?: return
        launchOp({ FileOperations.createTextFile(dir, name.trim()) }) { null }
    }

    fun rename(item: FileItem, newName: String) {
        launchOp({ FileOperations.rename(item.file, newName.trim()) }) {
            selectNone()
            null
        }
    }

    fun delete(items: List<FileItem>) {
        val files = items.map { it.file }
        launchOp({ FileOperations.delete(files) }) {
            selectNone()
            "Deleted $it item(s)"
        }
    }

    fun pinSelection() = pin(selectedItems())

    fun pin(items: List<FileItem>) {
        val dirs = items.filter { it.isDirectory }
        dirs.forEach { quickStore.pin(it.file) }
        _state.update { it.copy(selected = emptySet(), message = "Pinned ${dirs.size} folder(s) to Quick access") }
        refresh()
    }

    fun isPinned(dir: File): Boolean =
        _state.value.quickAccess.any { it.pinnedByUser && it.dir.absolutePath == dir.absolutePath }

    fun unpin(dir: File) {
        quickStore.unpin(dir)
        refresh()
    }

    // ---------------------------------------------------------- view options

    fun setSort(field: SortField = _state.value.sortField, ascending: Boolean = _state.value.ascending) {
        _state.update { it.copy(sortField = field, ascending = ascending) }
        refresh()
    }

    /** Tapping a column header: same column flips direction, new column starts ascending. */
    fun sortByColumn(field: SortField) {
        val s = _state.value
        setSort(field, if (s.sortField == field) !s.ascending else true)
    }

    fun setViewMode(mode: ViewMode) = _state.update { it.copy(viewMode = mode) }

    fun toggleHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun showMessage(msg: String) = _state.update { it.copy(message = msg) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun <T> launchOp(op: () -> Result<T>, onSuccess: (T) -> String?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { op() }
            result
                .onSuccess { value -> onSuccess(value)?.let { msg -> _state.update { it.copy(message = msg) } } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Operation failed") } }
            refresh()
        }
    }

    private companion object {
        const val KEY_DIR = "current_dir"
        const val MAX_RESULTS = 1000
    }
}
