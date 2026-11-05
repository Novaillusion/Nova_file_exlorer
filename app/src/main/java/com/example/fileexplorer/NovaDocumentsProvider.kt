package com.example.fileexplorer

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * Exposes the tablet's storage as documents (Storage Access Framework).
 *
 * - Files chosen in the explorer for Krita (and other apps that ask through
 *   [PickerActivity.ACTION_OPEN_DOCUMENT] / [PickerActivity.ACTION_CREATE_DOCUMENT])
 *   are handed back as document URIs of this provider, with read *and* write
 *   access that the app can keep (persistable), so it can save back to them and
 *   reopen them from its recent files later.
 * - Android also lists the explorer in its own file picker (the ☰ menu), for apps
 *   that use ACTION_OPEN_DOCUMENT / ACTION_CREATE_DOCUMENT.
 *
 * Document ids are absolute paths. Reading the storage relies on the explorer's own
 * "All files access".
 */
class NovaDocumentsProvider : DocumentsProvider() {

    companion object {
        fun authority(context: Context) = "${context.packageName}.documents"

        fun uriFor(context: Context, file: File): Uri =
            DocumentsContract.buildDocumentUri(authority(context), file.absolutePath)

        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_AVAILABLE_BYTES,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        val ctx = context ?: return cursor
        for (volume in Volumes.load(ctx)) {
            cursor.newRow().apply {
                add(Root.COLUMN_ROOT_ID, volume.root.absolutePath)
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY)
                add(Root.COLUMN_TITLE, ctx.getString(R.string.app_name))
                add(Root.COLUMN_SUMMARY, volume.name)
                add(Root.COLUMN_DOCUMENT_ID, volume.root.absolutePath)
                add(Root.COLUMN_ICON, R.drawable.ic_launcher)
                add(Root.COLUMN_AVAILABLE_BYTES, volume.freeBytes)
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        addRow(cursor, fileFor(documentId))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        val dir = fileFor(parentDocumentId)
        dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { addRow(cursor, it) }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = fileFor(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val dir = fileFor(parentDocumentId)
        val target = uniqueFile(dir, displayName, mimeType == Document.MIME_TYPE_DIR)
        val created = if (mimeType == Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile()
        if (!created) throw FileNotFoundException("Couldn't create ${target.absolutePath}")
        return target.absolutePath
    }

    override fun deleteDocument(documentId: String) {
        val file = fileFor(documentId)
        if (!file.deleteRecursively()) throw FileNotFoundException("Couldn't delete $documentId")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileFor(documentId)
        val target = File(file.parentFile, displayName)
        if (target.exists() || !file.renameTo(target)) throw FileNotFoundException("Couldn't rename $documentId")
        return target.absolutePath
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith(parentDocumentId.trimEnd('/') + "/")

    override fun getDocumentType(documentId: String): String = mimeOf(fileFor(documentId))

    private fun fileFor(documentId: String): File {
        val file = File(documentId)
        if (!file.isAbsolute) throw FileNotFoundException("Bad document id $documentId")
        return file
    }

    private fun mimeOf(file: File): String = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        else -> FileIntents.mimeType(file).let { if (it == "*/*") "application/octet-stream" else it }
    }

    private fun addRow(cursor: MatrixCursor, file: File) {
        var flags = 0
        if (file.isDirectory) {
            if (file.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        if (file.parentFile?.canWrite() == true) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        val mime = mimeOf(file)
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, file.absolutePath)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_MIME_TYPE, mime)
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, if (file.isDirectory) null else file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }
    }

    /** "name.ext", or "name (1).ext" if that exists. */
    private fun uniqueFile(dir: File, displayName: String, isDir: Boolean): File {
        var candidate = File(dir, displayName)
        if (!candidate.exists()) return candidate
        val base = if (isDir) displayName else displayName.substringBeforeLast('.', displayName)
        val ext = if (isDir || !displayName.contains('.')) "" else "." + displayName.substringAfterLast('.')
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$ext")
            n++
        }
        return candidate
    }
}
