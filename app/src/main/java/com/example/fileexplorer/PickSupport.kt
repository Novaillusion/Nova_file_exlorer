package com.example.fileexplorer

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Another app asked us for a file (ACTION_GET_CONTENT / ACTION_PICK).
 * [mimeTypes] holds patterns like "image/x" or "application/pdf"; empty means anything.
 */
data class PickRequest(
    val allowMultiple: Boolean,
    val mimeTypes: List<String>,
) {
    fun matches(file: File): Boolean {
        if (mimeTypes.isEmpty()) return true
        val mime = FileIntents.mimeType(file)
        return mimeTypes.any { pattern ->
            when {
                pattern == "*/*" -> true
                pattern.endsWith("/*") -> mime.startsWith(pattern.dropLast(1))
                else -> mime.equals(pattern, ignoreCase = true)
            }
        }
    }

    /** Text for the "file type" box of the Open dialog, Windows style. */
    val filterLabel: String
        get() = if (mimeTypes.isEmpty()) "All files (*.*)" else mimeTypes.joinToString(", ")

    companion object {
        fun from(intent: Intent): PickRequest {
            val extra = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty()
            val types = (extra + listOfNotNull(intent.type))
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            return PickRequest(
                allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
                mimeTypes = if (types.any { it == "*/*" || it == "*" }) emptyList() else types,
            )
        }
    }
}

object FolderIntents {
    /** Folder another app asked us to show (ACTION_VIEW on a directory), if we can map it to a path. */
    fun folderFrom(intent: Intent?): File? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val file = when (uri.scheme) {
            "file" -> uri.path?.let(::File)
            "content" -> fromDocumentUri(uri)
            else -> null
        }
        return file?.takeIf { it.isDirectory }
    }

    /** content://com.android.externalstorage.documents/{tree|document}/primary:DCIM/Camera -> /storage/emulated/0/DCIM/Camera */
    private fun fromDocumentUri(uri: Uri): File? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching {
            if (uri.pathSegments.firstOrNull() == "tree" && uri.pathSegments.size < 3) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
        }.getOrNull() ?: return null
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }
        return if (path.isEmpty()) root else File(root, path)
    }
}
