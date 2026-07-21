package io.github.varunkumar.spaceclean.ui

import io.github.varunkumar.spaceclean.ui.theme.OutlineColor
import io.github.varunkumar.spaceclean.ui.theme.SurfaceVariant
import android.app.Activity
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.ScanState
import io.github.varunkumar.spaceclean.ScanType
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground
import io.github.varunkumar.spaceclean.ui.theme.glassSurface
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.NeonBlueAlpha13
import io.github.varunkumar.spaceclean.ui.theme.NeonViolet
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FileSortBy(val label: String) {
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest"),
    NAME("Name A–Z"),
    DATE("Newest"),
    OLDEST("Oldest"),
}

private class TypeFilter(val label: String, val match: (String) -> Boolean)

private val TYPE_FILTERS = listOf(
    TypeFilter("All")      { true },
    TypeFilter("PDF")      { it.endsWith(".pdf", true) },
    TypeFilter("APK")      { it.endsWith(".apk", true) },
    TypeFilter("Archives") { n -> listOf(".zip", ".rar", ".7z").any { n.endsWith(it, true) } },
    TypeFilter("Docs")     { n -> listOf(".doc", ".docx", ".txt", ".xls", ".xlsx", ".ppt", ".pptx", ".csv").any { n.endsWith(it, true) } },
    TypeFilter("Images")   { it.isImageName() },
    TypeFilter("Videos")   { it.isVideoName() },
    TypeFilter("Audio")    { it.isAudioName() },
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    scanType:      ScanType,
    navController: NavHostController,
    viewModel:     ScanViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    val scanState: ScanState = when (scanType) {
        ScanType.PHOTOS        -> uiState.photosState
        ScanType.VIDEOS        -> uiState.videosState
        ScanType.APPS          -> ScanState.Idle
        ScanType.DOCUMENTS     -> uiState.documentsState
        ScanType.AUDIO         -> uiState.audioState
        ScanType.DOWNLOADS     -> uiState.downloadsState
        ScanType.CHAT_MEDIA    -> uiState.chatMediaState
        ScanType.EMPTY_FOLDERS -> uiState.emptyFoldersState
        ScanType.USELESS       -> uiState.uselessState
        ScanType.LARGEST       -> uiState.largestState
        ScanType.OLD_FILES     -> uiState.oldFilesState
        ScanType.DUPLICATES    -> ScanState.Idle   // handled by DuplicatesScreen, not here
    }
    val isScanning = scanState is ScanState.Loading
    val liveFiles  = (scanState as? ScanState.Success)?.files.orEmpty()

    // Keep the previous results on screen during a re-scan so the list doesn't flash
    // empty (and briefly show "All clean") while reloading.
    var lastFiles by remember(scanType) { mutableStateOf(liveFiles) }
    LaunchedEffect(liveFiles) { if (liveFiles.isNotEmpty()) lastFiles = liveFiles }
    val files = if (liveFiles.isNotEmpty() || !isScanning) liveFiles else lastFiles

    fun rescan() {
        when (scanType) {
            ScanType.PHOTOS        -> viewModel.scanPhotos(navigate = false)
            ScanType.VIDEOS        -> viewModel.scanVideos(navigate = false)
            ScanType.APPS          -> Unit
            ScanType.DOCUMENTS     -> viewModel.scanDocuments(navigate = false)
            ScanType.AUDIO         -> viewModel.scanAudio(navigate = false)
            ScanType.DOWNLOADS     -> viewModel.scanDownloads(navigate = false)
            ScanType.CHAT_MEDIA    -> viewModel.scanChatMedia(navigate = false)
            ScanType.EMPTY_FOLDERS -> viewModel.scanEmptyFolders(navigate = false)
            ScanType.USELESS       -> viewModel.scanUseless(navigate = false)
            ScanType.LARGEST       -> viewModel.scanLargest(navigate = false)
            ScanType.OLD_FILES     -> viewModel.scanOldFiles(navigate = false)
            ScanType.DUPLICATES    -> Unit
        }
    }

    // If we arrive with no data (e.g. process death restored this screen), scan once.
    LaunchedEffect(scanType) {
        if (scanState is ScanState.Idle && scanType != ScanType.APPS) rescan()
    }

    val recommended   = uiState.recommendedForDeletion
    val isCompareType = scanType == ScanType.PHOTOS

    val selected: MutableMap<Uri, Boolean> = remember(files, recommended) {
        mutableStateMapOf<Uri, Boolean>().also { map ->
            recommended.forEach { uri -> if (files.any { it.uri == uri }) map[uri] = true }
        }
    }

    var sortBy by remember { mutableStateOf(FileSortBy.SIZE_DESC) }
    var query  by remember { mutableStateOf("") }
    // Type filter is only meaningful for mixed-type sections.
    val showTypeFilter = scanType == ScanType.DOCUMENTS || scanType == ScanType.DOWNLOADS ||
            scanType == ScanType.LARGEST || scanType == ScanType.OLD_FILES
    var typeFilter by remember(scanType) { mutableStateOf(0) }
    val sortedFiles = remember(files, sortBy) {
        when (sortBy) {
            FileSortBy.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            FileSortBy.SIZE_ASC  -> files.sortedBy { it.sizeBytes }
            FileSortBy.NAME      -> files.sortedBy { it.name.lowercase() }
            FileSortBy.DATE      -> files.sortedByDescending { it.dateModifiedMs }
            FileSortBy.OLDEST    -> files.sortedBy { if (it.dateModifiedMs > 0) it.dateModifiedMs else Long.MAX_VALUE }
        }
    }
    val visibleFiles = remember(sortedFiles, query, typeFilter, showTypeFilter) {
        var f = sortedFiles
        if (query.isNotBlank()) f = f.filter { it.name.contains(query.trim(), ignoreCase = true) }
        if (showTypeFilter && typeFilter > 0) f = f.filter { TYPE_FILTERS[typeFilter].match(it.name) }
        f
    }

    val selectedFiles = files.filter { selected[it.uri] == true }
    // "All" reflects/acts on the currently visible (filtered) rows so search + select-all
    // doesn't silently mark files hidden by the query.
    val allSelected   = visibleFiles.isNotEmpty() && visibleFiles.all { selected[it.uri] == true }
    val selectedBytes = selectedFiles.sumOf { it.sizeBytes }

    var showConfirm by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.finalizeDelete()
        else viewModel.onDeleteCancelled()
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            scanType.displayName,
                            style      = MaterialTheme.typography.titleLarge,
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        if (files.isNotEmpty()) {
                            Text(
                                "${files.size} ${if (files.size == 1) "file" else "files"} · ${files.sumOf { it.sizeBytes }.toReadableSize()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = ElectricCyan)
                    }
                },
                actions = {
                    // Re-scan this category in place
                    IconButton(onClick = { rescan() }, enabled = !isScanning) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = ElectricCyan,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, "Re-scan", tint = ElectricCyan)
                        }
                    }
                    if (files.isNotEmpty()) {
                        Row(
                            modifier          = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            NeonCheckbox(
                                checked         = allSelected,
                                onCheckedChange = { chk ->
                                    if (chk) visibleFiles.forEach { selected[it.uri] = true }
                                    else visibleFiles.forEach { selected.remove(it.uri) }
                                },
                            )
                            Text(
                                "All",
                                style  = MaterialTheme.typography.labelMedium,
                                color  = if (allSelected) ElectricCyan else TextSecondary,
                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                                listOf(Color.Transparent, ElectricCyan.copy(0.3f), Color.Transparent)
                            )
                        )
                )
                Button(
                    onClick = { showConfirm = true },
                    enabled  = selectedFiles.isNotEmpty() && !uiState.isDeletingFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        // navigationBarsPadding must precede height so the inset reserves
                        // space BELOW the button instead of shrinking it under the nav bar.
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = ErrorRed,
                        contentColor           = Color.White,
                        disabledContainerColor = ErrorRed.copy(alpha = 0.2f),
                        disabledContentColor   = Color.White.copy(alpha = 0.35f),
                    ),
                ) {
                    if (uiState.isDeletingFiles) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        val label = if (selectedFiles.isEmpty()) "Select files to delete"
                        else "Delete ${selectedFiles.size} ${if (selectedFiles.size == 1) "file" else "files"}  ·  ${selectedBytes.toReadableSize()}"
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        if (files.isEmpty()) {
            AllClearBanner(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isCompareType && recommended.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeonBlueAlpha13)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CompareArrows,
                            null,
                            tint     = ElectricCyan,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "${recommended.size} worse ${if (recommended.size == 1) "copy" else "copies"} auto-selected · tap ⇄ to compare side-by-side",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                        )
                    }
                }

                // Search field (shown once a list has a few items)
                if (files.size > 5) {
                    SearchField(
                        query    = query,
                        onChange = { query = it },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    )
                }

                // Type-filter chips (Documents / Downloads / Largest / Old files)
                if (showTypeFilter) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        TYPE_FILTERS.forEachIndexed { idx, tf ->
                            item(key = tf.label) {
                                FileSortChip(
                                    label    = tf.label,
                                    selected = typeFilter == idx,
                                    onClick  = { typeFilter = idx },
                                )
                            }
                        }
                    }
                }

                // Sort chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    FileSortBy.entries.forEach { option ->
                        item(key = option.name) {
                            FileSortChip(
                                label    = option.label,
                                selected = sortBy == option,
                                onClick  = { sortBy = option },
                            )
                        }
                    }
                }

                if (visibleFiles.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (query.isNotBlank()) "No files match \"${query.trim()}\""
                            else "No ${TYPE_FILTERS.getOrNull(typeFilter)?.label ?: ""} files here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }

                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleFiles, key = { it.uri.toString() }) { file ->
                        val bestFile = if (isCompareType) uiState.bestPairMap[file.uri] else null
                        val isChecked = selected[file.uri] == true
                        FileListRow(
                            modifier        = Modifier.animateItem(),
                            file            = file,
                            checked         = isChecked,
                            isRecommended   = file.uri in recommended,
                            bestFile        = bestFile,
                            onCheckedChange = { chk ->
                                if (chk) selected[file.uri] = true else selected.remove(file.uri)
                            },
                            onViewFile      = {
                                viewModel.openFileViewer(file)
                                navController.navigate(Screen.MediaViewer.route)
                            },
                            onCompare       = {
                                viewModel.openCompare(bestFile!!, file)
                                navController.navigate(Screen.Compare.route)
                            },
                        )
                    }
                }
            }
        }

        DeleteConfirmDialog(
            visible   = showConfirm,
            count     = selectedFiles.size,
            bytes     = selectedBytes,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                viewModel.requestDelete(selectedFiles) { intentSender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query:    String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberCard)
            .border(1.dp, ElectricCyan.copy(0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search by name…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
            BasicTextField(
                value         = query,
                onValueChange = onChange,
                singleLine    = true,
                textStyle     = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush   = SolidColor(ElectricCyan),
                modifier      = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Rounded.Close, "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeleteConfirmDialog(
    visible:   Boolean,
    count:     Int,
    bytes:     Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CyberCard)
                .border(1.dp, ErrorRed.copy(0.35f), RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(listOf(ErrorRed.copy(0.22f), Color.Transparent)),
                            RoundedCornerShape(32.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = ErrorRed, modifier = Modifier.size(34.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Delete $count ${if (count == 1) "file" else "files"}?",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${bytes.toReadableSize()} · Everything moves to the recoverable Trash " +
                        "for ~30 days — photos & videos to your device Trash, other files to " +
                        "SpaceClean's. Restore anytime from Recently Deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceVariant)
                            .border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ErrorRed)
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pill-shaped file row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListRow(
    file:            ScannedFile,
    checked:         Boolean,
    isRecommended:   Boolean,
    bestFile:        ScannedFile?,
    onCheckedChange: (Boolean) -> Unit,
    onViewFile:      () -> Unit,
    onCompare:       () -> Unit,
    modifier:        Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue   = if (checked) ElectricCyan.copy(0.45f) else OutlineColor,
        animationSpec = tween(200),
        label         = "rowBorder",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(corner = 16.dp, accent = if (checked) ElectricCyan else Color.Transparent)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick     = { onCheckedChange(!checked) },
                onLongClick = onViewFile,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeonCheckbox(checked = checked, onCheckedChange = onCheckedChange)

            FileThumb(file = file)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = file.name,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (checked) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text     = file.path.ifBlank { "Unknown location" },
                    style    = MaterialTheme.typography.labelSmall,
                    color    = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isRecommended) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                            .border(
                                0.5.dp,
                                ErrorRed.copy(0.3f),
                                RoundedCornerShape(5.dp),
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "Recommended for Deletion",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                        )
                    }
                }
            }

            if (file.sizeBytes > 0L) {
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (checked) ElectricCyan.copy(0.12f) else SurfaceVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            file.sizeBytes.toReadableSize(),
                            style      = MaterialTheme.typography.labelMedium,
                            color      = if (checked) ElectricCyan else TextSecondary,
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    if (file.durationMs > 0L) {
                        Text(
                            file.durationMs.formatDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
            }

            if (bestFile != null) {
                IconButton(onClick = onCompare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.CompareArrows,
                        "Compare side-by-side",
                        tint     = ElectricCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                IconButton(onClick = onViewFile, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Info,
                        "View details",
                        tint     = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Neon animated checkbox — replaces Material3 Checkbox
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NeonCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val borderColor by animateColorAsState(
        targetValue   = if (checked) ElectricCyan else OutlineColor,
        animationSpec = tween(180),
        label         = "cbBorder",
    )
    val fillColor by animateColorAsState(
        targetValue   = if (checked) ElectricCyan.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(180),
        label         = "cbFill",
    )

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(fillColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = checked) {
            Icon(
                imageVector        = Icons.Rounded.Check,
                contentDescription = null,
                tint               = ElectricCyan,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AllClearBanner(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.radialGradient(listOf(SuccessGreen.copy(0.2f), Color.Transparent)),
                    shape = RoundedCornerShape(36.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("All clean", style = MaterialTheme.typography.titleLarge, color = SuccessGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Nothing found in this category", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sort chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) ElectricCyan.copy(0.5f) else OutlineColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ElectricCyan.copy(0.15f) else CyberCard)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style      = MaterialTheme.typography.labelMedium,
            color      = if (selected) ElectricCyan else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension
// ─────────────────────────────────────────────────────────────────────────────

fun Long.formatDuration(): String {
    val totalSec = this / 1_000L
    val h  = totalSec / 3600
    val m  = (totalSec % 3600) / 60
    val s  = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else              "%d:%02d".format(m, s)
}
