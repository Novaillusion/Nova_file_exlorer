package com.example.fileexplorer

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opened when another app asks for a file ("Choose a file", attach, upload...).
 * Shows the normal explorer plus a Windows-style Open/Cancel bar, then hands the
 * chosen files back as content:// URIs with temporary read permission.
 */
class PickerActivity : ComponentActivity() {

    companion object {
        /**
         * Open a file for reading and writing (like Intent.ACTION_OPEN_DOCUMENT, but
         * sent straight to the explorer, e.g. by Krita). Extras: type / EXTRA_MIME_TYPES,
         * EXTRA_ALLOW_MULTIPLE, [PickRequest.EXTRA_EXTENSIONS].
         */
        const val ACTION_OPEN_DOCUMENT = "com.example.fileexplorer.action.OPEN_DOCUMENT"

        /**
         * Save As (like Intent.ACTION_CREATE_DOCUMENT): the user picks a folder and a
         * name (EXTRA_TITLE is the suggestion); the file is created and handed back.
         */
        const val ACTION_CREATE_DOCUMENT = "com.example.fileexplorer.action.CREATE_DOCUMENT"

        private const val PREFS = "picker"
        private const val KEY_LAST_DIR = "lastDocumentDir"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val request = PickRequest.from(intent)
        // open/save for an app like Krita: start in the folder used last time
        val startDir = if (request.documentResult) {
            getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_DIR, null)
                ?.let(::File)?.takeIf { it.isDirectory }
        } else null
        setContent {
            FileExplorerTheme {
                AppRoot(
                    startDir = startDir,
                    pick = request,
                    onPicked = ::deliver,
                    onCancelPick = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private fun deliver(files: List<File>) {
        if (files.isEmpty()) return
        val documentResult = PickRequest.from(intent).documentResult
        if (documentResult) {
            files.first().parentFile?.let {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_DIR, it.absolutePath).apply()
            }
        }
        val uris = try {
            if (documentResult) {
                // read/write access the app can keep, like the system file picker gives
                files.map { NovaDocumentsProvider.uriFor(this, it) }
            } else {
                files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Can't share files from this location", Toast.LENGTH_SHORT).show()
            return
        }
        val result = Intent().apply {
            data = uris.first()
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (documentResult) {
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
