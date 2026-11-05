package com.example.fileexplorer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File

class MainActivity : ComponentActivity() {

    /** Folder another app asked us to open ("Open folder", "Show in folder"...). */
    private var startDir by mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startDir = FolderIntents.folderFrom(intent)
        setContent {
            FileExplorerTheme {
                AppRoot(startDir = startDir)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        FolderIntents.folderFrom(intent)?.let { startDir = it }
    }
}

@Composable
internal fun AppRoot(
    pick: PickRequest? = null,
    onPicked: (List<File>) -> Unit = {},
    onCancelPick: () -> Unit = {},
    startDir: File? = null,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StoragePermission.isGranted(context)) }

    // Re-check whenever we come back (e.g. from the Settings screen).
    LifecycleResumeEffect(Unit) {
        granted = StoragePermission.isGranted(context)
        onPauseOrDispose { }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = StoragePermission.isGranted(context) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { granted = StoragePermission.isGranted(context) }

    if (granted) {
        ExplorerScreen(pick = pick, onPicked = onPicked, onCancelPick = onCancelPick, startDir = startDir)
    } else {
        PermissionScreen(
            onGrant = {
                if (StoragePermission.usesAllFilesAccess) {
                    StoragePermission.launchAllFilesSettings(context, settingsLauncher)
                } else {
                    runtimeLauncher.launch(StoragePermission.runtimePermissions)
                }
            },
            onOpenAppSettings = { StoragePermission.openAppSettings(context) },
        )
    }
}
