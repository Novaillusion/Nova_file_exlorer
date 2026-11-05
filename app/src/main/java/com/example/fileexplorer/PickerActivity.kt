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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val request = PickRequest.from(intent)
        setContent {
            FileExplorerTheme {
                AppRoot(
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
        val uris = try {
            files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
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
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
