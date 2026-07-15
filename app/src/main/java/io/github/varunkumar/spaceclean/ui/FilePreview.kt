package io.github.varunkumar.spaceclean.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.SurfaceVariant
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// File-type detection
// ─────────────────────────────────────────────────────────────────────────────

fun String.isImageName() =
    listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif")
        .any { endsWith(it, ignoreCase = true) }

fun String.isVideoName() =
    listOf(".mp4", ".mkv", ".mov", ".webm", ".3gp", ".avi", ".m4v")
        .any { endsWith(it, ignoreCase = true) }

fun String.isAudioName() =
    listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac", ".amr")
        .any { endsWith(it, ignoreCase = true) }

fun String.isPdfName() = endsWith(".pdf", ignoreCase = true)

// ─────────────────────────────────────────────────────────────────────────────
// In-memory thumbnail cache — scrolling a list must never re-decode a thumb.
// 1/8 of the app's max heap, evicting least-recently-used bitmaps.
// ─────────────────────────────────────────────────────────────────────────────

private val thumbCache = object : android.util.LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(64L * 1_024 * 1_024).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private inline fun cachedThumb(key: String, produce: () -> Bitmap?): Bitmap? {
    thumbCache.get(key)?.let { return it }
    return produce()?.also { thumbCache.put(key, it) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared file preview — real thumbnail for media, typed icon for everything else
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FileThumb(
    file:     ScannedFile,
    modifier: Modifier = Modifier,
    size:     Dp = 52.dp,
    corner:   Dp = 10.dp,
) {
    val isImage  = file.name.isImageName()
    val isVideo  = file.name.isVideoName()
    val isPdf    = file.name.isPdfName()
    val isFolder = file.uri.scheme == "file" && file.sizeBytes == 0L

    when {
        isImage || isVideo -> MediaThumb(uri = file.uri, isVideo = isVideo, size = size, corner = corner, modifier = modifier)
        isPdf              -> PdfThumb(uri = file.uri, size = size, corner = corner, modifier = modifier)
        else               -> TypeIconBox(name = file.name, isFolder = isFolder, size = size, corner = corner, modifier = modifier)
    }
}

/** Renders the first page of a PDF as a thumbnail via the platform PdfRenderer. */
@Composable
private fun PdfThumb(uri: Uri, size: Dp, corner: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(thumbCache.get("pdf:$uri"), uri) {
        if (value == null) value = withContext(Dispatchers.IO) {
            cachedThumb("pdf:$uri") { renderPdfPage(context, uri, targetW = 220) }
        }
    }
    Box(
        modifier         = modifier.size(size).clip(RoundedCornerShape(corner)).background(SurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.PictureAsPdf, null, tint = ErrorRed, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** Renders the first page of a PDF to a white-backed bitmap. Shared by list thumbs and the viewer. */
internal fun renderPdfPage(context: Context, uri: Uri, targetW: Int = 220): Bitmap? = runCatching {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val ratio   = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.3f
                val targetH = (targetW * ratio).toInt().coerceIn(1, targetW * 2)
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(android.graphics.Color.WHITE)   // pages render on transparent
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
}.getOrNull()

@Composable
private fun MediaThumb(uri: Uri, isVideo: Boolean, size: Dp, corner: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(thumbCache.get("m:$uri"), uri) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            cachedThumb("m:$uri") {
              runCatching {
                when (uri.scheme) {
                    "content" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
                    } else {
                        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@runCatching null
                        @Suppress("DEPRECATION")
                        if (isVideo) MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                        else MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
                    }
                    "file" -> {
                        val f = File(uri.path ?: return@runCatching null)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (isVideo) ThumbnailUtils.createVideoThumbnail(f, Size(160, 160), null)
                            else ThumbnailUtils.createImageThumbnail(f, Size(160, 160), null)
                        } else if (!isVideo) decodeFileThumb(f) else null
                    }
                    else -> null
                }
              }.getOrNull()
            }
        }
    }

    Box(
        modifier         = modifier.size(size).clip(RoundedCornerShape(corner)).background(SurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(if (isVideo) Icons.Rounded.Movie else Icons.Rounded.Image, null,
                tint = TextMuted, modifier = Modifier.size(size * 0.42f))
        }
    }
}

@Composable
private fun TypeIconBox(name: String, isFolder: Boolean, size: Dp, corner: Dp, modifier: Modifier) {
    val (icon, tint) = fileIconFor(name, isFolder)
    Box(
        modifier         = modifier.size(size).background(tint.copy(alpha = 0.12f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

private fun fileIconFor(name: String, isFolder: Boolean): Pair<ImageVector, Color> = when {
    isFolder                                   -> Icons.Rounded.Folder          to WarningAmber
    name.endsWith(".apk", true)                -> Icons.Rounded.Android         to SuccessGreen
    name.endsWith(".pdf", true)                -> Icons.Rounded.PictureAsPdf    to ErrorRed
    name.endsWith(".zip", true) || name.endsWith(".rar", true) ||
        name.endsWith(".7z", true)             -> Icons.Rounded.Archive         to WarningAmber
    name.endsWith(".doc", true) || name.endsWith(".docx", true) ||
        name.endsWith(".txt", true)            -> Icons.Rounded.Description     to ElectricCyan
    name.endsWith(".xls", true) || name.endsWith(".xlsx", true) ||
        name.endsWith(".csv", true)            -> Icons.Rounded.TableChart      to SuccessGreen
    name.isAudioName()                         -> Icons.Rounded.AudioFile       to Color(0xFF14B8A6)
    name.endsWith(".log", true) || name.endsWith(".tmp", true)
                                               -> Icons.Rounded.BugReport       to TextMuted
    else                                       -> Icons.Rounded.InsertDriveFile to TextSecondary
}

private fun decodeFileThumb(f: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    val max = maxOf(bounds.outWidth, bounds.outHeight)
    while (max / (sample * 2) >= 160) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(f.path, opts)
}
