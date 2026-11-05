package com.example.fileexplorer

import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int?,
) {
    val path: String get() = file.absolutePath

    companion object {
        fun from(file: File): FileItem {
            val isDir = file.isDirectory
            return FileItem(
                file = file,
                name = file.name,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                childCount = if (isDir) file.list()?.size else null,
            )
        }
    }
}

enum class SortOrder(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
}

/** Plain, blocking file-system operations. Call these from Dispatchers.IO. */
object FileOperations {

    fun list(dir: File, showHidden: Boolean, sort: SortOrder): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { showHidden || !it.isHidden }
            .map(FileItem::from)
            .sortedWith(comparator(sort))
            .toList()
    }

    private fun comparator(sort: SortOrder): Comparator<FileItem> {
        val foldersFirst = compareByDescending<FileItem> { it.isDirectory }
        val byName = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val order = when (sort) {
            SortOrder.NAME_ASC -> byName
            SortOrder.NAME_DESC -> byName.reversed()
            SortOrder.DATE_DESC -> compareByDescending<FileItem> { it.lastModified }.then(byName)
            SortOrder.DATE_ASC -> compareBy<FileItem> { it.lastModified }.then(byName)
            SortOrder.SIZE_DESC -> compareByDescending<FileItem> { it.size }.then(byName)
            SortOrder.SIZE_ASC -> compareBy<FileItem> { it.size }.then(byName)
        }
        return foldersFirst.then(order)
    }

    fun createFolder(parent: File, name: String): Result<File> = runCatching {
        validateName(name)
        val dir = File(parent, name)
        require(!dir.exists()) { "\"$name\" already exists" }
        check(dir.mkdir()) { "Couldn't create \"$name\"" }
        dir
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        validateName(newName)
        val target = File(file.parentFile, newName)
        if (target.absolutePath == file.absolutePath) return@runCatching file
        require(!target.exists()) { "\"$newName\" already exists" }
        check(file.renameTo(target)) { "Couldn't rename \"${file.name}\"" }
        target
    }

    fun delete(files: List<File>): Result<Int> = runCatching {
        val failed = files.count { !it.deleteRecursively() }
        check(failed == 0) { "Couldn't delete $failed item(s)" }
        files.size
    }

    /** Copies (or moves) [sources] into [destDir], picking "name (1).ext" style names on conflict. */
    fun paste(sources: List<File>, destDir: File, move: Boolean): Result<Int> = runCatching {
        var count = 0
        for (src in sources) {
            if (!src.exists()) continue
            require(!(src.isDirectory && isSameOrInside(destDir, src))) {
                "Can't ${if (move) "move" else "copy"} a folder into itself"
            }
            if (move && src.parentFile?.canonicalPath == destDir.canonicalPath) {
                count++ // already here, nothing to do
                continue
            }
            val target = uniqueTarget(destDir, src.name)
            if (move) {
                // renameTo is instant on the same volume; fall back to copy+delete across volumes.
                if (!src.renameTo(target)) {
                    src.copyRecursively(target, overwrite = false)
                    check(src.deleteRecursively()) { "Copied, but couldn't remove original \"${src.name}\"" }
                }
            } else {
                src.copyRecursively(target, overwrite = false)
            }
            count++
        }
        count
    }

    private fun isSameOrInside(child: File, parent: File): Boolean {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        return c == p || c.startsWith(p + File.separator)
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Name can't be empty" }
        require('/' !in name && '\u0000' !in name) { "Name can't contain /" }
        require(name != "." && name != "..") { "Invalid name" }
    }
}
