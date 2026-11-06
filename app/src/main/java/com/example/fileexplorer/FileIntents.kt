package com.example.fileexplorer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Opening and sharing files with other apps through our FileProvider. */
object FileIntents {

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    const val APK_MIME = "application/vnd.android.package-archive"

    fun isApk(file: File) = file.extension.equals("apk", ignoreCase = true)

    fun mimeType(file: File): String =
        if (isApk(file)) APK_MIME
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    fun open(context: Context, file: File) = view(context, file, chooser = false)

    /** Always ask which app to use, like Explorer's "Open with". */
    fun openWith(context: Context, file: File) = view(context, file, chooser = true)

    private fun view(context: Context, file: File, chooser: Boolean) {
        // Android 8+: the system installer refuses APKs from apps that aren't allowed to install apps.
        if (isApk(file) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            toast(context, "Allow \"Install unknown apps\", then tap the APK again")
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                )
            } catch (e: ActivityNotFoundException) {
                StoragePermission.openAppSettings(context)
            }
            return
        }
        val uri = try {
            uriFor(context, file)
        } catch (e: IllegalArgumentException) {
            toast(context, "Can't open files in this location")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(if (chooser) Intent.createChooser(intent, "Open with") else intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, "No app can open this file")
        }
    }

    fun share(context: Context, files: List<File>) {
        val shareable = files.filter { it.isFile }
        if (shareable.isEmpty()) {
            toast(context, "Folders can't be shared — select files")
            return
        }
        val uris = try {
            shareable.map { uriFor(context, it) }
        } catch (e: IllegalArgumentException) {
            toast(context, "Can't share files in this location")
            return
        }
        val types = shareable.map(::mimeType).distinct()
        val type = types.singleOrNull() ?: "*/*"

        val intent = (
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        ).apply {
            this.type = type
            clipData = ClipData.newRawUri(null, uris[0]).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
