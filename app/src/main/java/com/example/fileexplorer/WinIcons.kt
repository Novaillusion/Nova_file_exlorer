package com.example.fileexplorer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import kotlin.math.ceil

// ---------------------------------------------------------------------------
// Hand-drawn glyphs in the spirit of the Fluent folder / drive icons
// ---------------------------------------------------------------------------

@Composable
fun FolderGlyph(size: Dp, modifier: Modifier = Modifier) {
    val c = win
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val back = Path().apply {
            moveTo(w * 0.06f, h * 0.20f)
            lineTo(w * 0.38f, h * 0.20f)
            lineTo(w * 0.47f, h * 0.29f)
            lineTo(w * 0.94f, h * 0.29f)
            lineTo(w * 0.94f, h * 0.80f)
            lineTo(w * 0.06f, h * 0.80f)
            close()
        }
        drawPath(back, c.folderBack)
        drawRoundRect(
            color = c.folderFront,
            topLeft = Offset(w * 0.06f, h * 0.35f),
            size = GSize(w * 0.88f, h * 0.47f),
            cornerRadius = CornerRadius(w * 0.05f),
        )
    }
}

@Composable
fun DriveGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val left = w * 0.05f
        val top = h * 0.34f
        val bw = w * 0.90f
        val bh = h * 0.36f
        val r = CornerRadius(w * 0.06f)
        // body
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF2F2F2), Color(0xFFBDBDBD)), startY = top, endY = top + bh),
            topLeft = Offset(left, top), size = GSize(bw, bh), cornerRadius = r,
        )
        // darker front strip
        drawRoundRect(
            color = Color(0xFF6B6B6B),
            topLeft = Offset(left, top + bh * 0.58f), size = GSize(bw, bh * 0.42f), cornerRadius = r,
        )
        // activity light
        drawCircle(Color(0xFF3BD43B), radius = w * 0.04f, center = Offset(left + bw * 0.12f, top + bh * 0.79f))
    }
}

// ---------------------------------------------------------------------------
// File type icons, names and sizes
// ---------------------------------------------------------------------------

private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico")
private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "wmv")
private val audioExt = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "wma", "mid")
private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
private val codeExt = setOf("kt", "java", "py", "js", "ts", "html", "css", "c", "cpp", "h", "sh", "json", "xml", "yml", "yaml")
private val textExt = setOf("txt", "md", "log", "doc", "docx", "odt", "rtf")
private val sheetExt = setOf("xls", "xlsx", "csv", "ods")
private val slideExt = setOf("ppt", "pptx", "odp")

fun isImage(file: File) = file.extension.lowercase() in imageExt
fun isVideo(file: File) = file.extension.lowercase() in videoExt

fun fileIcon(file: File): Pair<ImageVector, Color> {
    @Suppress("DEPRECATION")
    val generic = Icons.Filled.InsertDriveFile
    return when (file.extension.lowercase()) {
        in imageExt -> Icons.Filled.Image to Color(0xFF3AA8E8)
        in videoExt -> Icons.Filled.Movie to Color(0xFF9C6ADE)
        in audioExt -> Icons.Filled.AudioFile to Color(0xFFF28C28)
        "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFE5484D)
        in archiveExt -> Icons.Filled.FolderZip to Color(0xFFD4A72C)
        "apk" -> Icons.Filled.Android to Color(0xFF3DDC84)
        in codeExt -> Icons.Filled.Code to Color(0xFF8AB4F8)
        in sheetExt -> Icons.Filled.TableChart to Color(0xFF21A366)
        in slideExt -> Icons.Filled.Slideshow to Color(0xFFD35230)
        in textExt -> Icons.Filled.Description to Color(0xFF5B9BD5)
        else -> generic to Color(0xFF9E9E9E)
    }
}

fun quickIcon(kind: QuickKind): Pair<ImageVector, Color>? = when (kind) {
    QuickKind.DOWNLOADS -> Icons.Filled.Download to Color(0xFF2FB67C)
    QuickKind.DOCUMENTS -> Icons.Filled.Description to Color(0xFF8FA6C4)
    QuickKind.PICTURES -> Icons.Filled.Collections to Color(0xFF3AA8E8)
    QuickKind.MUSIC -> Icons.Filled.LibraryMusic to Color(0xFFF2784B)
    QuickKind.VIDEOS -> Icons.Filled.VideoLibrary to Color(0xFF9C6ADE)
    QuickKind.CAMERA -> Icons.Filled.PhotoCamera to Color(0xFF8E8E8E)
    QuickKind.FOLDER -> null // drawn with FolderGlyph
}

/** Windows-style "Type" column text. */
fun typeName(item: FileItem): String {
    if (item.isDirectory) return "File folder"
    val ext = item.file.extension.lowercase()
    return when (ext) {
        "" -> "File"
        "txt" -> "Text Document"
        "pdf" -> "PDF Document"
        "apk" -> "Android Package"
        "md" -> "Markdown File"
        "doc", "docx" -> "Word Document"
        "xls", "xlsx" -> "Excel Worksheet"
        "ppt", "pptx" -> "PowerPoint Presentation"
        "zip" -> "Compressed (zipped) Folder"
        "mp3" -> "MP3 File"
        "mp4" -> "MP4 Video"
        else -> "${ext.uppercase()} File"
    }
}

private val grouping = NumberFormat.getIntegerInstance()

/** Windows shows file sizes in whole KB: "1,234 KB". */
fun winSize(bytes: Long): String = "${grouping.format(ceil(bytes / 1024.0).toLong())} KB"

/** "195 GB" style for drives and totals. */
fun humanSize(bytes: Long): String {
    val units = listOf("bytes", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return when {
        i == 0 -> "$bytes bytes"
        v >= 100 -> "%.0f %s".format(v, units[i])
        v >= 10 -> "%.1f %s".format(v, units[i])
        else -> "%.2f %s".format(v, units[i])
    }
}

fun exactBytes(bytes: Long): String = "${grouping.format(bytes)} bytes"

// ---------------------------------------------------------------------------
// Icon for a list/grid item, with real thumbnails for images & videos
// ---------------------------------------------------------------------------

internal object Thumbs {
    // Big enough for a screenful of 3x thumbnails.
    val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(file: File, px: Int): Bitmap? {
        val key = "${file.absolutePath}:${file.lastModified()}:$px"
        cache.get(key)?.let { return it }
        val bmp = runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isVideo(file) ->
                    ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    ThumbnailUtils.createImageThumbnail(file, Size(px, px), null)
                isImage(file) -> decodeSampled(file, px)
                else -> null
            }
        }.getOrNull()
        bmp?.let { cache.put(key, it) }
        return bmp
    }

    @Suppress("DEPRECATION")
    fun apkIcon(context: Context, file: File, px: Int): Bitmap? {
        val key = "apk:${file.absolutePath}:${file.lastModified()}:$px"
        cache.get(key)?.let { return it }
        val bmp = runCatching {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)?.applicationInfo ?: return@runCatching null
            // Point the ApplicationInfo at the APK so its resources (and icon) can be loaded.
            info.sourceDir = file.absolutePath
            info.publicSourceDir = file.absolutePath
            info.loadIcon(pm).toBitmap(px, px)
        }.getOrNull()
        bmp?.let { cache.put(key, it) }
        return bmp
    }

    private fun decodeSampled(file: File, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Composable
fun ItemIcon(item: FileItem, size: Dp, thumbnails: Boolean = false) {
    when {
        item.isDirectory -> FolderGlyph(size)
        !item.inArchive && item.file.extension.equals("apk", ignoreCase = true) -> ApkIcon(item, size)
        thumbnails && (isImage(item.file) || isVideo(item.file)) -> ThumbnailOrIcon(item, size)
        else -> TypeIcon(item, size)
    }
}

@Composable
private fun ThumbnailOrIcon(item: FileItem, size: Dp) {
    val px = (size.value * 2.5f).toInt()
    val thumb by produceState<ImageBitmap?>(null, item.path, item.lastModified, px) {
        value = withContext(Dispatchers.IO) { Thumbs.load(item.file, px)?.asImageBitmap() }
    }
    val t = thumb
    if (t != null) {
        Image(
            bitmap = t,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp)),
        )
    } else {
        TypeIcon(item, size)
    }
}

/** The app's own launcher icon, read from inside the APK. */
@Composable
private fun ApkIcon(item: FileItem, size: Dp) {
    val context = LocalContext.current
    val px = (size.value * 2.5f).toInt().coerceAtLeast(48)
    val icon by produceState<ImageBitmap?>(null, item.path, item.lastModified, px) {
        value = withContext(Dispatchers.IO) { Thumbs.apkIcon(context, item.file, px)?.asImageBitmap() }
    }
    val i = icon
    if (i != null) {
        Image(bitmap = i, contentDescription = null, modifier = Modifier.size(size))
    } else {
        TypeIcon(item, size)
    }
}

@Composable
private fun TypeIcon(item: FileItem, size: Dp) {
    val (vector, tint) = fileIcon(item.file)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.9f))
    }
}
