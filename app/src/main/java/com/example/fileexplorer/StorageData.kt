package com.example.fileexplorer

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

// ---------------------------------------------------------------------------
// "Devices and drives": the storage volumes on the phone
// ---------------------------------------------------------------------------

data class VolumeInfo(
    val name: String,
    val root: File,
    val totalBytes: Long,
    val freeBytes: Long,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
) {
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
}

object Volumes {
    fun load(context: Context): List<VolumeInfo> {
        val sm = context.getSystemService(StorageManager::class.java)
        val primary = Environment.getExternalStorageDirectory()

        // Every mounted volume gives us an app-specific dir like
        // /storage/1234-ABCD/Android/data/<pkg>/files - strip that back to the volume root.
        val roots = LinkedHashSet<File>().apply { add(primary) }
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            val p = dir.absolutePath
            val i = p.indexOf("/Android/data/")
            if (i > 0) roots.add(File(p.substring(0, i)))
        }

        return roots.filter { it.exists() }.map { root ->
            val vol = runCatching { sm?.getStorageVolume(root) }.getOrNull()
            val stat = runCatching { StatFs(root.path) }.getOrNull()
            VolumeInfo(
                name = vol?.getDescription(context) ?: if (root == primary) "Internal storage" else root.name,
                root = root,
                totalBytes = stat?.totalBytes ?: 0L,
                freeBytes = stat?.availableBytes ?: 0L,
                isPrimary = vol?.isPrimary ?: (root == primary),
                isRemovable = vol?.isRemovable ?: false,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Quick access: default library folders + user pins
// ---------------------------------------------------------------------------

enum class QuickKind { DOWNLOADS, DOCUMENTS, PICTURES, MUSIC, VIDEOS, CAMERA, FOLDER }

data class QuickItem(
    val name: String,
    val dir: File,
    val kind: QuickKind,
    val pinnedByUser: Boolean,
)

class QuickAccessStore(context: Context) {
    private val prefs = context.getSharedPreferences("quick_access", Context.MODE_PRIVATE)

    private fun pins(): List<String> =
        prefs.getString(KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun pin(dir: File) {
        val current = pins()
        if (dir.absolutePath in current) return
        prefs.edit().putString(KEY, (current + dir.absolutePath).joinToString("\n")).apply()
    }

    fun unpin(dir: File) {
        prefs.edit().putString(KEY, pins().filter { it != dir.absolutePath }.joinToString("\n")).apply()
    }

    @Suppress("DEPRECATION")
    fun items(): List<QuickItem> {
        fun lib(type: String, name: String, kind: QuickKind) =
            QuickItem(name, Environment.getExternalStoragePublicDirectory(type), kind, pinnedByUser = false)

        val defaults = listOf(
            lib(Environment.DIRECTORY_DOWNLOADS, "Downloads", QuickKind.DOWNLOADS),
            lib(Environment.DIRECTORY_DOCUMENTS, "Documents", QuickKind.DOCUMENTS),
            lib(Environment.DIRECTORY_PICTURES, "Pictures", QuickKind.PICTURES),
            lib(Environment.DIRECTORY_MUSIC, "Music", QuickKind.MUSIC),
            lib(Environment.DIRECTORY_MOVIES, "Videos", QuickKind.VIDEOS),
            lib(Environment.DIRECTORY_DCIM, "Camera", QuickKind.CAMERA),
        ).filter { it.dir.isDirectory }

        val defaultPaths = defaults.map { it.dir.absolutePath }.toSet()
        val user = pins()
            .filter { it !in defaultPaths }
            .map(::File)
            .filter { it.isDirectory }
            .map { QuickItem(it.name, it, QuickKind.FOLDER, pinnedByUser = true) }

        return defaults + user
    }

    private companion object {
        const val KEY = "pins"
    }
}
