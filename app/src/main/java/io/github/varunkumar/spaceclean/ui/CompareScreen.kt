package io.github.varunkumar.spaceclean.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    keepFile:   ScannedFile,
    deleteFile: ScannedFile,
    viewModel:  ScanViewModel,
    onBack:     () -> Unit,
) {
    BackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsState()

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.finalizeDelete()
            onBack()
        } else {
            viewModel.onDeleteCancelled()
        }
    }

    Scaffold(
        containerColor = VelvetBlack,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Side-by-Side Compare",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Pinch to zoom  ·  Keep the best copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan.copy(0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = ElectricCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VelvetBlack),
            )
        },
        bottomBar = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, ErrorRed.copy(0.4f), Color.Transparent)
                            )
                        )
                )
                Button(
                    onClick = {
                        viewModel.requestDelete(listOf(deleteFile)) { intentSender ->
                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                    },
                    enabled  = !uiState.isDeletingFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(54.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = ErrorRed,
                        contentColor           = Color.White,
                        disabledContainerColor = ErrorRed.copy(0.25f),
                        disabledContentColor   = Color.White.copy(0.4f),
                    ),
                ) {
                    if (uiState.isDeletingFiles) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                            Text(
                                "Delete Worse Copy · ${deleteFile.sizeBytes.toReadableSize()}",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ComparePanel(
                file     = keepFile,
                label    = "KEEP",
                badge    = SuccessGreen,
                modifier = Modifier.weight(1f),
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                ElectricCyan.copy(0.3f),
                                ElectricCyan.copy(0.3f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            ComparePanel(
                file     = deleteFile,
                label    = "DELETE",
                badge    = ErrorRed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single compare panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComparePanel(
    file:     ScannedFile,
    label:    String,
    badge:    Color,
    modifier: Modifier = Modifier,
) {
    val uriStr  = file.uri.toString()
    val isImage = uriStr.contains("/images/media/") ||
            file.name.let { n -> n.endsWith(".jpg", true) || n.endsWith(".jpeg", true) ||
                    n.endsWith(".png", true) || n.endsWith(".webp", true) }
    val isVideo = uriStr.contains("/video/media/") ||
            file.name.let { n -> n.endsWith(".mp4", true) || n.endsWith(".mov", true) ||
                    n.endsWith(".mkv", true) }

    Column(
        modifier            = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(badge.copy(alpha = 0.10f))
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector        = if (label == "KEEP") Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint               = badge,
                    modifier           = Modifier.size(11.dp),
                )
                Text(
                    label,
                    style         = MaterialTheme.typography.labelSmall,
                    color         = badge,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
        }

        // Media preview
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF080810)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isImage -> ZoomableImagePanel(uri = file.uri)
                isVideo -> SmallVideoPanel(uri = file.uri)
                else    -> GenericFileIcon(name = file.name)
            }
        }

        // Details pill
        DetailsPill(file = file, badge = badge)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Zoomable image — pinch-to-zoom
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZoomableImagePanel(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val sample = calcSample(bounds.outWidth, bounds.outHeight, 720)
                val opts   = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }

    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale  = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        } else {
            CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline video player
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SmallVideoPanel(uri: Uri) {
    val context = LocalContext.current
    val player  = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player  = player
                useController = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
}

@Composable
private fun GenericFileIcon(name: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(ElectricCyan.copy(0.1f), RoundedCornerShape(16.dp))
            .border(1.dp, ElectricCyan.copy(0.3f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = name.substringAfterLast('.').uppercase().take(4),
            style = MaterialTheme.typography.labelLarge,
            color = ElectricCyan,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Details pill at bottom of each panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailsPill(file: ScannedFile, badge: Color) {
    val dateStr = remember(file.dateModifiedMs) {
        if (file.dateModifiedMs > 0L)
            SimpleDateFormat("d MMM yy", Locale.getDefault()).format(Date(file.dateModifiedMs))
        else "—"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberCard)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text       = file.name,
            style      = MaterialTheme.typography.labelSmall,
            color      = TextPrimary,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            PillChip(text = file.sizeBytes.toReadableSize(), tint = badge)
            PillChip(text = dateStr, tint = TextMuted)
        }
    }
}

@Composable
private fun PillChip(text: String, tint: Color = TextMuted) {
    Box(
        modifier = Modifier
            .background(tint.copy(0.10f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────────────────────────

private fun calcSample(w: Int, h: Int, targetPx: Int = 720): Int {
    if (w <= 0 || h <= 0) return 1
    val max = maxOf(w, h)
    var s = 1
    while (max / (s * 2) >= targetPx) s *= 2
    return s
}
