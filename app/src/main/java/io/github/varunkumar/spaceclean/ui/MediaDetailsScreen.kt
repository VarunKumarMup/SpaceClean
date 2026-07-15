package io.github.varunkumar.spaceclean.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CardSurface
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.NeonBlue
import io.github.varunkumar.spaceclean.ui.theme.NeonBlueAlpha13
import io.github.varunkumar.spaceclean.ui.theme.PitchBlack
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Screen entry point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsScreen(file: ScannedFile, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val uriStr  = file.uri.toString()
    val n       = file.name
    val isImage = uriStr.contains("/images/media/") ||
            listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif")
                .any { n.endsWith(it, ignoreCase = true) }
    val isVideo = uriStr.contains("/video/media/") ||
            listOf(".mp4", ".mkv", ".mov", ".webm", ".3gp", ".avi", ".m4v")
                .any { n.endsWith(it, ignoreCase = true) }
    val isPdf   = n.endsWith(".pdf", ignoreCase = true)

    Scaffold(
        containerColor = PitchBlack,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style    = MaterialTheme.typography.titleMedium,
                        color    = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = NeonBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchBlack),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Media area (fixed height, non-scrollable) ─────────────────────
            when {
                isImage -> FullImagePreview(file.uri)
                isVideo -> VideoPlayerPreview(file.uri)
                isPdf   -> PdfPagePreview(file.uri, file.name)
                else    -> FileIconBanner(file.name, isFolder = file.sizeBytes == 0L)
            }

            // ── Scrollable details below ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(24.dp))
                FileDetailsCard(file)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full-resolution image viewer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullImagePreview(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                // Pass 1: get image dimensions
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                // Pass 2: decode at a sensible sample size (keeps sharpness, avoids OOM)
                val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetPx = 1440)
                val opts   = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 520.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Fit,
            )
        } else {
            CircularProgressIndicator(color = NeonBlue, modifier = Modifier.size(44.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PDF first-page viewer
// ─────────────────────────────────────────────────────────────────────────────

private sealed class PdfState {
    object Loading : PdfState()
    data class Ready(val bitmap: Bitmap) : PdfState()
    object Failed : PdfState()
}

@Composable
private fun PdfPagePreview(uri: Uri, name: String) {
    val context = LocalContext.current
    val state by produceState<PdfState>(initialValue = PdfState.Loading, key1 = uri) {
        val bmp = withContext(Dispatchers.IO) { renderPdfPage(context, uri, targetW = 1080) }
        value = if (bmp != null) PdfState.Ready(bmp) else PdfState.Failed
    }

    when (val s = state) {
        is PdfState.Ready -> Box(
            modifier         = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap             = s.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Fit,
            )
        }
        PdfState.Loading -> Box(
            modifier         = Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF080810)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = NeonBlue, modifier = Modifier.size(44.dp))
        }
        PdfState.Failed -> FileIconBanner(name, isFolder = false)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExoPlayer video viewer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoPlayerPreview(uri: Uri) {
    val context = LocalContext.current

    val exoPlayer = androidx.compose.runtime.remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player        = exoPlayer
                    useController = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Icon banner for non-media files (downloads, folders)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileIconBanner(name: String, isFolder: Boolean) {
    val (icon, tint) = when {
        isFolder                                 -> Icons.Rounded.Folder          to WarningAmber
        name.endsWith(".pdf", ignoreCase = true) -> Icons.Rounded.PictureAsPdf   to ErrorRed
        name.endsWith(".apk", ignoreCase = true) -> Icons.Rounded.Android        to SuccessGreen
        name.endsWith(".zip", ignoreCase = true) -> Icons.Rounded.Archive        to WarningAmber
        name.endsWith(".log", ignoreCase = true) -> Icons.Rounded.BugReport      to TextMuted
        name.endsWith(".tmp", ignoreCase = true) -> Icons.Rounded.BugReport      to TextMuted
        else                                     -> Icons.Rounded.InsertDriveFile to TextSecondary
    }

    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color(0xFF080810)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier         = Modifier
                .size(110.dp)
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(58.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Details card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileDetailsCard(file: ScannedFile) {
    val dateStr = androidx.compose.runtime.remember(file.dateModifiedMs) {
        if (file.dateModifiedMs > 0L)
            SimpleDateFormat("MMM d, yyyy  ·  h:mm a", Locale.getDefault()).format(Date(file.dateModifiedMs))
        else
            "—"
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardSurface),
        border    = BorderStroke(1.dp, NeonBlueAlpha13),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(6.dp).background(NeonBlue, androidx.compose.foundation.shape.CircleShape))
                Text(
                    "FILE DETAILS",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = TextSecondary,
                    letterSpacing = 2.sp,
                )
            }

            HorizontalDivider(color = TextMuted.copy(alpha = 0.12f))

            DetailRow(
                icon  = Icons.Rounded.InsertDriveFile,
                label = "Name",
                value = file.name,
            )
            DetailRow(
                icon  = Icons.Rounded.DataUsage,
                label = "Size",
                value = if (file.sizeBytes > 0L) file.sizeBytes.toReadableSize() else "Empty folder",
            )
            DetailRow(
                icon  = Icons.Rounded.Schedule,
                label = "Modified",
                value = dateStr,
            )
            DetailRow(
                icon  = Icons.Rounded.FolderOpen,
                label = "Location",
                value = file.path.ifBlank { "Unknown" },
            )
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .background(NeonBlueAlpha13, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(20.dp))
        }

        Column(
            modifier            = Modifier.weight(1f).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun calculateSampleSize(srcWidth: Int, srcHeight: Int, targetPx: Int = 1440): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    val maxDim = maxOf(srcWidth, srcHeight)
    var sample = 1
    while (maxDim / (sample * 2) >= targetPx) sample *= 2
    return sample
}
