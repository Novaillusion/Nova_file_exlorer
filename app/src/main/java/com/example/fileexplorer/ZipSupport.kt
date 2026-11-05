package com.example.fileexplorer

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A place inside a zip archive. [inner] is the folder or file path inside the archive,
 * "" for the archive's root. In the UI these are shown as ordinary paths:
 * /storage/emulated/0/Download/photos.zip/2024/beach.jpg
 */
data class ZipLocation(val archive: File, val inner: String) {
    val prefix: String get() = if (inner.isEmpty()) "" else "$inner/"
}

/** Zip archives as "Compressed (zipped) Folders", like Windows Explorer. Blocking: call from Dispatchers.IO. */
object Zips {

    fun isZip(file: File): Boolean = file.extension.equals("zip", ignoreCase = true)

    /** If [path] is a zip file or points inside one, where in the archive it is. */
    fun locate(path: File): ZipLocation? {
        val parts = ArrayDeque<String>()
        var f: File? = path
        while (f != null) {
            if (isZip(f) && f.isFile) return ZipLocation(f, parts.joinToString("/"))
            parts.addFirst(f.name)
            f = f.parentFile
        }
        return null
    }

    /** Opens the archive as UTF-8, falling back to the old DOS code page used by many Windows zips. */
    private fun <T> open(archive: File, block: (ZipFile) -> T): T =
        try {
            ZipFile(archive, Charsets.UTF_8).use(block)
        } catch (e: IllegalArgumentException) { // malformed UTF-8 entry name
            ZipFile(archive, Charset.forName("IBM437")).use(block)
        } catch (e: ZipException) {
            throw IOException("\"${archive.name}\" is damaged or isn't a zip file")
        }

    private fun clean(name: String) = name.replace('\\', '/').trimStart('/')

    private fun skip(name: String, showHidden: Boolean) =
        name.startsWith("__MACOSX/") || (!showHidden && name.split('/').any { it.startsWith(".") })

    /** The folder's immediate children, folders implied by deeper entries included. */
    fun list(loc: ZipLocation, showHidden: Boolean): List<FileItem> = open(loc.archive) { zip ->
        val prefix = loc.prefix
        val children = LinkedHashMap<String, FileItem>()
        for (entry in zip.entries()) {
            val name = clean(entry.name)
            if (!name.startsWith(prefix) || name == prefix || skip(name, showHidden)) continue
            val rest = name.removePrefix(prefix)
            val child = rest.substringBefore('/')
            val isDir = rest.contains('/') || entry.isDirectory
            val existing = children[child]
            if (existing != null && (existing.isDirectory || !isDir)) continue
            children[child] = FileItem(
                file = File(loc.archive, prefix + child),
                name = child,
                isDirectory = isDir,
                size = if (isDir) 0L else entry.size.coerceAtLeast(0L),
                lastModified = entry.time.coerceAtLeast(0L),
                childCount = null,
                inArchive = true,
            )
        }
        children.values.toList()
    }

    /** Everything below [loc] whose name contains [query]. */
    fun search(loc: ZipLocation, query: String, showHidden: Boolean, limit: Int): List<FileItem> =
        open(loc.archive) { zip ->
            val prefix = loc.prefix
            val found = ArrayList<FileItem>()
            for (entry in zip.entries()) {
                val name = clean(entry.name).trimEnd('/')
                if (!name.startsWith(prefix) || skip(name, showHidden)) continue
                val leaf = name.substringAfterLast('/')
                if (leaf.contains(query, ignoreCase = true)) {
                    found.add(
                        FileItem(
                            file = File(loc.archive, name),
                            name = leaf,
                            isDirectory = entry.isDirectory,
                            size = entry.size.coerceAtLeast(0L),
                            lastModified = entry.time.coerceAtLeast(0L),
                            childCount = null,
                            inArchive = true,
                        )
                    )
                    if (found.size >= limit) break
                }
            }
            found
        }

    /**
     * Extracts [loc] (a file, a folder, or the whole archive when inner is "") to [target].
     * For a file, [target] is the output file; otherwise it's the folder to create.
     */
    fun extract(loc: ZipLocation, target: File) = open(loc.archive) { zip ->
        val inner = loc.inner
        val single = if (inner.isEmpty()) null else zip.getEntry(inner)?.takeIf { !it.isDirectory }
        if (single != null) {
            target.parentFile?.mkdirs()
            writeEntry(zip, single, target)
            return@open
        }
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val prefix = loc.prefix
        for (entry in zip.entries()) {
            val name = clean(entry.name)
            if (!name.startsWith(prefix) || name == prefix || name.startsWith("__MACOSX/")) continue
            val out = File(target, name.removePrefix(prefix))
            // "Zip slip": never write outside the destination folder.
            if (!out.canonicalPath.startsWith(root)) throw IOException("Unsafe path in archive: ${entry.name}")
            if (entry.isDirectory) out.mkdirs() else writeEntry(zip, entry, out)
        }
    }

    private fun writeEntry(zip: ZipFile, entry: ZipEntry, out: File) {
        out.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
        if (entry.time > 0) out.setLastModified(entry.time)
    }

    /** Windows "Extract All": into a new folder named after the archive, next to it. */
    fun extractAll(archive: File): File {
        val dest = FileOperations.uniqueTarget(archive.parentFile ?: throw IOException("No parent folder"), archive.nameWithoutExtension)
        extract(ZipLocation(archive, ""), dest)
        return dest
    }

    /** Windows "Compress to ZIP file": zips [sources] into [dest]. */
    fun compress(sources: List<File>, dest: File) {
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            for (src in sources) {
                if (src.isDirectory) {
                    val base = src.parentFile
                    for (f in src.walkTopDown()) {
                        if (f == dest) continue
                        val rel = f.relativeTo(base ?: src).invariantSeparatorsPath
                        if (f.isDirectory) {
                            zos.putNextEntry(ZipEntry("$rel/").apply { time = f.lastModified() })
                            zos.closeEntry()
                        } else {
                            addFile(zos, f, rel)
                        }
                    }
                } else if (src != dest) {
                    addFile(zos, src, src.name)
                }
            }
        }
    }

    private fun addFile(zos: ZipOutputStream, f: File, name: String) {
        zos.putNextEntry(ZipEntry(name).apply { time = f.lastModified() })
        f.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    /** A real copy of an item inside an archive, for opening/sharing with other apps. */
    fun extractToCache(item: FileItem, cacheDir: File): File {
        val loc = locate(item.file) ?: throw IOException("Not inside an archive")
        val dir = File(cacheDir, "zip_view/${(loc.archive.absolutePath + loc.inner).hashCode().toUInt()}")
        val out = File(dir, item.name)
        if (!out.exists()) extract(loc, out)
        return out
    }
}
