package com.example.fileexplorer

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Clipboard(val files: List<File>, val cut: Boolean)

data class UiState(
    val rootDir: File,
    val currentDir: File,
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val selected: Set<String> = emptySet(), // absolute paths
    val clipboard: Clipboard? = null,
    val sort: SortOrder = SortOrder.NAME_ASC,
    val showHidden: Boolean = false,
    val query: String = "",
    val message: String? = null,
) {
    val atRoot: Boolean get() = currentDir.absolutePath == rootDir.absolutePath
}

class FileExplorerViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    private val root: File = Environment.getExternalStorageDirectory()

    private val _state = MutableStateFlow(
        UiState(
            rootDir = root,
            currentDir = savedState.get<String>(KEY_DIR)?.let(::File)?.takeIf { it.isDirectory } ?: root,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        val s = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = withContext(Dispatchers.IO) {
                FileOperations.list(s.currentDir, s.showHidden, s.sort)
            }
            _state.update { st ->
                // Drop selections for files that no longer exist.
                val paths = items.mapTo(HashSet()) { it.path }
                st.copy(items = items, loading = false, selected = st.selected.filterTo(HashSet()) { it in paths })
            }
        }
    }

    fun navigateTo(dir: File) {
        if (!dir.isDirectory || !dir.canRead()) {
            _state.update { it.copy(message = "Can't open \"${dir.name}\"") }
            return
        }
        savedState[KEY_DIR] = dir.absolutePath
        _state.update { it.copy(currentDir = dir, items = emptyList(), selected = emptySet(), query = "") }
        refresh()
    }

    fun navigateUp() {
        val s = _state.value
        if (s.atRoot) return
        s.currentDir.parentFile?.let(::navigateTo)
    }

    // ---- Selection ----

    fun toggleSelection(item: FileItem) = _state.update {
        val sel = if (item.path in it.selected) it.selected - item.path else it.selected + item.path
        it.copy(selected = sel)
    }

    fun selectAll(visible: List<FileItem>) =
        _state.update { it.copy(selected = visible.mapTo(HashSet()) { f -> f.path }) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectedItems(): List<FileItem> = _state.value.let { s -> s.items.filter { it.path in s.selected } }

    fun selectedFiles(): List<File> = selectedItems().map { it.file }

    // ---- Clipboard ----

    fun copySelection(cut: Boolean) {
        val files = selectedFiles()
        if (files.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = Clipboard(files, cut),
                selected = emptySet(),
                message = "${files.size} item(s) ready — open a folder and tap ${if (cut) "Move" else "Paste"}",
            )
        }
    }

    fun clearClipboard() = _state.update { it.copy(clipboard = null) }

    fun paste() {
        val s = _state.value
        val clip = s.clipboard ?: return
        launchOp(
            op = { FileOperations.paste(clip.files, s.currentDir, move = clip.cut) },
            onSuccess = { n ->
                _state.update { it.copy(clipboard = null) }
                "${if (clip.cut) "Moved" else "Copied"} $n item(s)"
            },
        )
    }

    // ---- Mutations ----

    fun createFolder(name: String) {
        val dir = _state.value.currentDir
        launchOp({ FileOperations.createFolder(dir, name.trim()) }) { "Created \"${it.name}\"" }
    }

    fun rename(item: FileItem, newName: String) {
        launchOp({ FileOperations.rename(item.file, newName.trim()) }) {
            clearSelection()
            "Renamed to \"${it.name}\""
        }
    }

    fun deleteSelection() {
        val files = selectedFiles()
        launchOp({ FileOperations.delete(files) }) {
            clearSelection()
            "Deleted $it item(s)"
        }
    }

    // ---- View options ----

    fun setSort(sort: SortOrder) {
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun toggleHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

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
    }
}
