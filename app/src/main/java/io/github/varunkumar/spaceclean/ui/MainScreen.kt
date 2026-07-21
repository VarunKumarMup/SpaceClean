package io.github.varunkumar.spaceclean.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.AiState
import io.github.varunkumar.spaceclean.AppStorageState
import io.github.varunkumar.spaceclean.DuplicatesState
import io.github.varunkumar.spaceclean.HomeUiState
import io.github.varunkumar.spaceclean.MainActivity
import io.github.varunkumar.spaceclean.QuickCleanCategory
import io.github.varunkumar.spaceclean.ScanState
import io.github.varunkumar.spaceclean.ScanType
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.DuplicateScanner
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.AuroraBase
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.NeonViolet
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground
import io.github.varunkumar.spaceclean.ui.theme.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// System health (RAM + battery) — polled live, read-only info
// ─────────────────────────────────────────────────────────────────────────────

private data class SystemHealth(
    val ramFreeMb:  Long    = 0L,
    val batteryPct: Int     = -1,
    val isCharging: Boolean = false,
)

private fun readSystemHealth(context: Context): SystemHealth {
    val ram = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem / 1_048_576L
    }.getOrElse { 0L }

    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val pct = runCatching {
        battery?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) l * 100 / s else -1
        } ?: -1
    }.getOrElse { -1 }
    val charging = runCatching {
        battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let { st ->
            st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
    }.getOrElse { false }

    return SystemHealth(ramFreeMb = ram, batteryPct = pct, isCharging = charging)
}

// ─────────────────────────────────────────────────────────────────────────────
// Category metadata
// ─────────────────────────────────────────────────────────────────────────────

private data class TileDef(
    val type:     ScanType,
    val icon:     ImageVector,
    val label:    String,
    val subtitle: String,
    val color:    Color,
)

private val TILES = listOf(
    TileDef(ScanType.PHOTOS,        Icons.Rounded.Photo,        "Photos",       "Duplicates & Bursts",   Color(0xFF00F0FF)),
    TileDef(ScanType.VIDEOS,        Icons.Rounded.Videocam,     "Videos",       "Large & Redundant",     Color(0xFF8B5CF6)),
    TileDef(ScanType.APPS,          Icons.Rounded.Apps,         "Apps",         "Installed Storage",     Color(0xFF9D00FF)),
    TileDef(ScanType.DOCUMENTS,     Icons.Rounded.Description,  "Documents",    "PDFs, ZIPs & Logs",     Color(0xFF3B82F6)),
    TileDef(ScanType.AUDIO,         Icons.Rounded.MusicNote,    "Audio",        "Music & Voice Notes",   Color(0xFF14B8A6)),
    TileDef(ScanType.DOWNLOADS,     Icons.Rounded.Download,     "Downloads",    "APKs, ZIPs & Junk",     Color(0xFFFF8A3D)),
    TileDef(ScanType.CHAT_MEDIA,    Icons.Rounded.Chat,         "Chat Media",   "WhatsApp, Telegram…",   Color(0xFF22C55E)),
    TileDef(ScanType.EMPTY_FOLDERS, Icons.Rounded.FolderDelete, "Leftover",     "Orphan Folders",        Color(0xFFEC4899)),
    TileDef(ScanType.USELESS,       Icons.Rounded.AutoDelete,   "Useless",      "GIFs, Temp & Logs",     Color(0xFF84CC16)),
    TileDef(ScanType.LARGEST,       Icons.Rounded.Storage,      "Largest",      "Biggest Space Hogs",    Color(0xFFF59E0B)),
    TileDef(ScanType.OLD_FILES,     Icons.Rounded.History,      "Old & Unused", "Oldest Files First",    Color(0xFF6366F1)),
    TileDef(ScanType.DUPLICATES,    Icons.Rounded.ContentCopy,  "Duplicates",   "Same File, 2+ Copies",  Color(0xFFE11D48)),
)

private data class Suggestion(
    val tile:     TileDef,
    val subtitle: String,
    val bytes:    Long,
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MainScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState      by viewModel.uiState.collectAsState()
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val requiredPermissions = remember { MainActivity.requiredPermissions() }

    // Poll RAM + battery every 3s while the dashboard is on screen.
    val sysHealth by produceState(initialValue = SystemHealth(), context) {
        while (true) {
            value = readSystemHealth(context)
            delay(3_000L)
        }
    }

    // Reclaimable total — excludes LARGEST / OLD_FILES (cross-sections of the others)
    // and Apps (no file list), so nothing is double-counted.
    val reclaimableStates = listOf(
        uiState.photosState, uiState.videosState, uiState.documentsState,
        uiState.audioState, uiState.downloadsState, uiState.chatMediaState,
        uiState.emptyFoldersState, uiState.uselessState,
    )
    // Deduped by URI — the useless/downloads/documents scans can overlap.
    val reclaimableBytes = remember(reclaimableStates) {
        val seen = HashSet<Uri>()
        reclaimableStates
            .filterIsInstance<ScanState.Success>()
            .sumOf { s -> s.files.sumOf { f -> if (seen.add(f.uri)) f.sizeBytes else 0L } }
    }
    val isAnyScanning = (reclaimableStates + uiState.largestState + uiState.oldFilesState)
        .any { it is ScanState.Loading } ||
        uiState.appStorageState is AppStorageState.Loading ||
        uiState.duplicatesState is DuplicatesState.Loading

    // Scan progress: how many of the 11 categories have finished (success or error).
    val doneCount = remember(uiState) {
        val fileDone = listOf(
            uiState.photosState, uiState.videosState, uiState.documentsState,
            uiState.audioState, uiState.downloadsState, uiState.chatMediaState,
            uiState.emptyFoldersState, uiState.uselessState,
            uiState.largestState, uiState.oldFilesState,
        ).count { it is ScanState.Success || it is ScanState.Error }
        val appsDone = if (uiState.appStorageState is AppStorageState.Success ||
            uiState.appStorageState is AppStorageState.Error) 1 else 0
        val dupDone  = if (uiState.duplicatesState is DuplicatesState.Success ||
            uiState.duplicatesState is DuplicatesState.Error) 1 else 0
        fileDone + appsDone + dupDone
    }
    val hasScanned = doneCount > 0

    // Cleanup suggestions — every category with results, biggest first.
    val suggestions = remember(uiState) {
        TILES.mapNotNull { tile ->
            when (tile.type) {
                ScanType.APPS -> {
                    val apps = (uiState.appStorageState as? AppStorageState.Success)?.apps
                    if (apps.isNullOrEmpty()) null
                    else Suggestion(tile, "${apps.size} apps installed", apps.sumOf { it.apkSizeBytes })
                }
                ScanType.DUPLICATES -> {
                    val groups = (uiState.duplicatesState as? DuplicatesState.Success)?.groups
                    val redundant = groups?.flatMap { it.drop(1) }
                    if (redundant.isNullOrEmpty()) null
                    else Suggestion(tile, "${groups.size} duplicate groups", redundant.sumOf { it.sizeBytes })
                }
                else -> {
                    val files = (tileState(tile.type, uiState) as? ScanState.Success)?.files
                    if (files.isNullOrEmpty()) null
                    else Suggestion(tile, "${files.size} files found", files.sumOf { it.sizeBytes })
                }
            }
        }.sortedByDescending { it.bytes }
    }

    // Safe "recommended for cleanup" set — used only for the hero button's size/enabled
    // state; the full reviewable list lives on the Quick Clean screen.
    val recommendedBytes = remember(uiState) { recommendedCleanupFiles(uiState).sumOf { it.sizeBytes } }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.onPermissionGranted()
        else scope.launch { snackbarHost.showSnackbar("Storage permission is required to scan files.") }
    }

    // Targeted folder scan — Storage Access Framework folder picker
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setCustomFolder(uri)
            navController.navigate(Screen.FolderScan.route)
        }
    }

    LaunchedEffect(uiState.pendingNavigation) {
        uiState.pendingNavigation?.let { type ->
            viewModel.clearPendingNavigation()
            if (type == ScanType.APPS) navController.navigate(Screen.AppStorage.route)
            else navController.navigate(Screen.FileList.createRoute(type))
        }
    }

    fun requestPerm(): Boolean {
        if (uiState.permissionGranted) return true
        if (requiredPermissions.isEmpty()) {
            // API 30+: MANAGE_EXTERNAL_STORAGE can't be requested via a runtime dialog —
            // launching an empty permission array would vacuously report "granted".
            // Send the user to the All-Files-Access toggle; MainActivity.onResume()
            // verifies the grant and unlocks the app on return.
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            permLauncher.launch(requiredPermissions)
        }
        return false
    }

    fun tileClick(type: ScanType, state: ScanState, scan: () -> Unit) {
        if (!requestPerm()) return
        when {
            state is ScanState.Loading -> Unit
            state is ScanState.Success -> navController.navigate(Screen.FileList.createRoute(type))
            else                       -> scan()
        }
    }

    fun appStorageTap() {
        when {
            uiState.appStorageState is AppStorageState.Loading -> Unit
            uiState.appStorageState is AppStorageState.Success -> navController.navigate(Screen.AppStorage.route)
            else -> viewModel.scanAppStorage()
        }
    }

    fun socialMediaTap() {
        if (!requestPerm()) return
        if (uiState.chatMediaState !is ScanState.Success && uiState.chatMediaState !is ScanState.Loading) {
            viewModel.scanChatMedia(navigate = false)
        }
        navController.navigate(Screen.SocialMedia.route)
    }

    fun duplicatesTap() {
        if (!requestPerm()) return
        if (uiState.duplicatesState !is DuplicatesState.Success && uiState.duplicatesState !is DuplicatesState.Loading) {
            viewModel.scanDuplicates()
        }
        navController.navigate(Screen.Duplicates.route)
    }

    // One shared router for suggestion rows and tool chips.
    fun openCategory(type: ScanType) {
        val state = tileState(type, uiState)
        when (type) {
            ScanType.PHOTOS        -> tileClick(type, state) { viewModel.scanPhotos() }
            ScanType.VIDEOS        -> tileClick(type, state) { viewModel.scanVideos() }
            ScanType.APPS          -> appStorageTap()
            ScanType.DOCUMENTS     -> tileClick(type, state) { viewModel.scanDocuments() }
            ScanType.AUDIO         -> tileClick(type, state) { viewModel.scanAudio() }
            ScanType.DOWNLOADS     -> tileClick(type, state) { viewModel.scanDownloads() }
            ScanType.CHAT_MEDIA    -> socialMediaTap()
            ScanType.EMPTY_FOLDERS -> tileClick(type, state) { viewModel.scanEmptyFolders() }
            ScanType.USELESS       -> tileClick(type, state) { viewModel.scanUseless() }
            ScanType.LARGEST       -> tileClick(type, state) { viewModel.scanLargest() }
            ScanType.OLD_FILES     -> tileClick(type, state) { viewModel.scanOldFiles() }
            ScanType.DUPLICATES    -> duplicatesTap()
        }
    }

    fun scanAll() {
        if (!requestPerm()) return
        if (isAnyScanning) return
        // navigate = false → populate results in place, stay on the dashboard
        viewModel.scanPhotos(navigate = false)
        viewModel.scanVideos(navigate = false)
        viewModel.scanDocuments(navigate = false)
        viewModel.scanAudio(navigate = false)
        viewModel.scanDownloads(navigate = false)
        viewModel.scanChatMedia(navigate = false)
        viewModel.scanEmptyFolders(navigate = false)
        viewModel.scanUseless(navigate = false)
        viewModel.scanLargest(navigate = false)
        viewModel.scanOldFiles(navigate = false)
        viewModel.scanDuplicates()
        viewModel.scanAppStorage(navigate = false)
    }

    Scaffold(
        containerColor = AuroraBase,
        contentColor   = TextPrimary,
        snackbarHost   = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(data, containerColor = CyberCard, contentColor = TextPrimary, actionColor = ElectricCyan)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().auroraBackground()) {
            Box(Modifier.fillMaxSize().padding(pad)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                ) {
                    DashboardHeader(
                        savedBytes = uiState.totalSpaceSavedBytes,
                        onTrash    = { navController.navigate(Screen.Trash.route) },
                        onSearch   = { navController.navigate(Screen.Search.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                    )

                    // ── Hero: one big scan gauge ────────────────────────────────
                    HeroScanGauge(
                        totalBytes       = uiState.totalStorageBytes,
                        usedBytes        = uiState.usedStorageBytes,
                        reclaimableBytes = reclaimableBytes,
                        isScanning       = isAnyScanning,
                        doneCount        = doneCount,
                        totalCount       = TILES.size,
                        hasScanned       = hasScanned,
                        quickCleanBytes  = recommendedBytes,
                        isDeleting       = uiState.isDeletingFiles,
                        onScan           = ::scanAll,
                        onQuickClean     = { navController.navigate(Screen.QuickClean.route) },
                        modifier         = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(16.dp))

                    SystemHealthRow(
                        ramFreeMb  = sysHealth.ramFreeMb,
                        batteryPct = sysHealth.batteryPct,
                        isCharging = sysHealth.isCharging,
                        modifier   = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(14.dp))

                    AiCleanupCard(
                        state    = uiState.aiState,
                        onClick  = { if (requestPerm()) navController.navigate(Screen.AiCleanup.route) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(22.dp))

                    // ── Cleanup suggestions (only after results exist) ──────────
                    if (suggestions.isNotEmpty()) {
                        SectionLabel("CLEANUP SUGGESTIONS")
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier            = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { s ->
                                SuggestionRow(
                                    suggestion = s,
                                    onClick    = { openCategory(s.tile.type) },
                                )
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }

                    // ── All tools — quiet chips ─────────────────────────────────
                    SectionLabel("ALL TOOLS")
                    Spacer(Modifier.height(8.dp))
                    val toolRows = remember { TILES.chunked(2) }
                    Column(
                        modifier            = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        toolRows.forEach { rowTiles ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowTiles.forEach { tile ->
                                    ToolChip(
                                        icon     = tile.icon,
                                        label    = tile.label,
                                        tint     = tile.color,
                                        onClick  = { openCategory(tile.type) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowTiles.size < 2) Spacer(Modifier.weight(1f))
                            }
                        }
                        // Targeted folder scan lives with the tools.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolChip(
                                icon     = Icons.Rounded.FolderOpen,
                                label    = "Scan a folder…",
                                tint     = ElectricCyan,
                                onClick  = { folderLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Safe "recommended for cleanup" set — only genuinely-redundant/junk items:
 * similar-photo worse copies, duplicate redundant copies, leftover folders, and
 * download junk (apk/zip/log/tmp). NEVER the raw photo/video/document lists.
 * Each item is tagged with the reason it's recommended (for the review screen).
 */
internal data class CleanupItem(val file: ScannedFile, val reason: String)

private val DOC_EXTENSIONS = listOf(
    ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv",
)

internal fun String.isDocumentName() = DOC_EXTENSIONS.any { endsWith(it, ignoreCase = true) }

internal fun recommendedCleanupItems(uiState: HomeUiState): List<CleanupItem> {
    val cats = uiState.quickCleanCategories
    val out  = LinkedHashMap<Uri, CleanupItem>()

    if (QuickCleanCategory.SIMILAR.key in cats) {
        (uiState.photosState as? ScanState.Success)?.files
            ?.filter { it.uri in uiState.recommendedForDeletion }
            ?.forEach { out[it.uri] = CleanupItem(it, "Similar photo") }
    }
    if (QuickCleanCategory.DUPLICATES.key in cats) {
        (uiState.duplicatesState as? DuplicatesState.Success)?.groups?.forEach { g ->
            val keeper = DuplicateScanner.pickKeeper(g)
            g.filter { it.uri != keeper.uri }.forEach { out[it.uri] = CleanupItem(it, "Duplicate copy") }
        }
    }
    if (QuickCleanCategory.FOLDERS.key in cats) {
        (uiState.emptyFoldersState as? ScanState.Success)?.files
            ?.forEach { out[it.uri] = CleanupItem(it, "Leftover folder") }
    }
    // Downloads split: junk (APK/ZIP/logs) vs documents — docs are auto-selected only
    // when the user opted in from Settings; otherwise they're never touched.
    val downloadFiles = (uiState.downloadsState as? ScanState.Success)?.files.orEmpty()
    if (QuickCleanCategory.DOWNLOADS.key in cats) {
        downloadFiles.filter { !it.name.isDocumentName() }
            .forEach { out[it.uri] = CleanupItem(it, "Download junk") }
    }
    if (QuickCleanCategory.DOCS.key in cats) {
        downloadFiles.filter { it.name.isDocumentName() }
            .forEach { out[it.uri] = CleanupItem(it, "Old document") }
        (uiState.documentsState as? ScanState.Success)?.files
            ?.forEach { f -> out.getOrPut(f.uri) { CleanupItem(f, "Old document") } }
    }
    if (QuickCleanCategory.USELESS.key in cats) {
        (uiState.uselessState as? ScanState.Success)?.files
            ?.forEach { f -> out.getOrPut(f.uri) { CleanupItem(f, "Useless file") } }
    }
    if (QuickCleanCategory.AI.key in cats) {
        (uiState.aiState as? AiState.Success)?.result?.let { ai ->
            ai.blurry.forEach { f -> out.getOrPut(f.uri) { CleanupItem(f, "Blurry (AI)") } }
            ai.similarGroups.forEach { group ->
                val keeper = aiKeeper(group)
                group.filter { it.uri != keeper.uri }
                    .forEach { f -> out.getOrPut(f.uri) { CleanupItem(f, "Similar (AI)") } }
            }
        }
    }
    return out.values.sortedByDescending { it.file.sizeBytes }
}

/**
 * Big files worth a look — SUGGESTED on the Quick Clean screen but never
 * pre-selected. Excludes anything already in the recommended set.
 */
internal fun bigFileSuggestions(uiState: HomeUiState, alreadyListed: Set<Uri>): List<CleanupItem> {
    val minBytes = 150L * 1_024 * 1_024
    return (uiState.largestState as? ScanState.Success)?.files.orEmpty()
        .asSequence()
        .filter { it.sizeBytes >= minBytes && it.uri !in alreadyListed }
        .take(15)
        .map { CleanupItem(it, "Large file") }
        .toList()
}

internal fun recommendedCleanupFiles(uiState: HomeUiState): List<ScannedFile> =
    recommendedCleanupItems(uiState).map { it.file }

/** Best photo to KEEP in an AI-similar group: highest resolution, then largest, then newest. */
internal fun aiKeeper(group: List<ScannedFile>): ScannedFile =
    group.maxByOrNull { it.widthPx.toLong() * it.heightPx.toLong() * 1_000_000L +
        it.sizeBytes + it.dateModifiedMs / 1_000_000L } ?: group.first()

// Maps ScanType → ScanState, bridging AppStorageState / DuplicatesState.
private fun tileState(type: ScanType, uiState: HomeUiState): ScanState =
    when (type) {
        ScanType.PHOTOS        -> uiState.photosState
        ScanType.VIDEOS        -> uiState.videosState
        ScanType.DOCUMENTS     -> uiState.documentsState
        ScanType.AUDIO         -> uiState.audioState
        ScanType.DOWNLOADS     -> uiState.downloadsState
        ScanType.CHAT_MEDIA    -> uiState.chatMediaState
        ScanType.EMPTY_FOLDERS -> uiState.emptyFoldersState
        ScanType.USELESS       -> uiState.uselessState
        ScanType.LARGEST       -> uiState.largestState
        ScanType.OLD_FILES     -> uiState.oldFilesState
        ScanType.DUPLICATES    -> when (val d = uiState.duplicatesState) {
            is DuplicatesState.Idle    -> ScanState.Idle
            is DuplicatesState.Loading -> ScanState.Loading
            // Redundant (deletable) copies — one keeper per group stays.
            is DuplicatesState.Success -> ScanState.Success(d.groups.flatMap { g -> g.drop(1) })
            is DuplicatesState.Error   -> ScanState.Error(d.message)
        }
        ScanType.APPS          -> when (val s = uiState.appStorageState) {
            is AppStorageState.Idle    -> ScanState.Idle
            is AppStorageState.Loading -> ScanState.Loading
            is AppStorageState.Success -> ScanState.Success(emptyList())
            is AppStorageState.Error   -> ScanState.Error(s.message)
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(savedBytes: Long, onTrash: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "AI Space Cleaner",
                style      = MaterialTheme.typography.titleLarge,
                color      = TextPrimary,
                fontWeight = FontWeight.Black,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                "OFFLINE  •  PRIVATE  •  ON-DEVICE AI",
                style         = MaterialTheme.typography.labelSmall,
                color         = ElectricCyan.copy(alpha = 0.6f),
                letterSpacing = 1.2.sp,
                fontWeight    = FontWeight.SemiBold,
                maxLines      = 1,
                overflow      = TextOverflow.Ellipsis,
            )
        }

        if (savedBytes > 0L) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(NeonViolet.copy(0.15f), ElectricCyan.copy(0.10f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    savedBytes.toReadableSize(),
                    style      = MaterialTheme.typography.titleSmall,
                    color      = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                )
                Text("cleaned", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CyberCard)
                .border(1.dp, ElectricCyan.copy(0.18f), CircleShape)
                .clickable(onClick = onTrash),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Restore, "Recently deleted", tint = ElectricCyan, modifier = Modifier.size(20.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CyberCard)
                .border(1.dp, ElectricCyan.copy(0.18f), CircleShape)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, "Search", tint = ElectricCyan, modifier = Modifier.size(20.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CyberCard)
                .border(1.dp, ElectricCyan.copy(0.18f), CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Settings, "Settings", tint = ElectricCyan, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero scan gauge — storage state, scan progress, and results in one dial
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroScanGauge(
    totalBytes:       Long,
    usedBytes:        Long,
    reclaimableBytes: Long,
    isScanning:       Boolean,
    doneCount:        Int,
    totalCount:       Int,
    hasScanned:       Boolean,
    quickCleanBytes:  Long,
    isDeleting:       Boolean,
    onScan:           () -> Unit,
    onQuickClean:     () -> Unit,
    modifier:         Modifier = Modifier,
) {
    val fraction = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val pct      = (fraction * 100f).toInt()

    // Ring fill: storage fraction when idle, full when results are in.
    val ringTarget = if (hasScanned && !isScanning) 1f else fraction
    val ringValue by animateFloatAsState(
        targetValue   = ringTarget,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label         = "heroRing",
    )

    // Scanning: a comet arc orbits the dial.
    val spin = rememberInfiniteTransition(label = "heroSpin")
    val spinAngle by spin.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label         = "spinAngle",
    )

    val showAllClean = hasScanned && !isScanning && reclaimableBytes == 0L
    val ringColors   =
        if (hasScanned && !isScanning) {
            if (showAllClean) listOf(SuccessGreen, ElectricCyan) else listOf(ElectricCyan, NeonViolet)
        } else listOf(ElectricCyan, NeonViolet)

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(236.dp)
                .clip(CircleShape)
                .clickable(enabled = !isScanning, onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(236.dp)) {
                val strokeW = 16.dp.toPx()
                val radius  = (size.minDimension / 2f) - (strokeW / 2f) - 6.dp.toPx()
                val center  = Offset(size.width / 2f, size.height / 2f)
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                // Track
                drawArc(
                    color      = Color.White.copy(alpha = 0.08f),
                    startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft    = topLeft, size = arcSize,
                    style      = Stroke(strokeW, cap = StrokeCap.Round),
                )

                if (isScanning) {
                    // Orbiting comet
                    rotate(spinAngle, center) {
                        drawArc(
                            brush      = Brush.sweepGradient(
                                0.00f to Color.Transparent,
                                0.72f to ElectricCyan.copy(alpha = 0.0f),
                                0.86f to ElectricCyan.copy(alpha = 0.55f),
                                1.00f to ElectricCyan,
                                center = center,
                            ),
                            startAngle = 0f, sweepAngle = 320f, useCenter = false,
                            topLeft    = topLeft, size = arcSize,
                            style      = Stroke(strokeW, cap = StrokeCap.Round),
                        )
                    }
                } else if (ringValue > 0.01f) {
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = ringColors,
                            start  = Offset(topLeft.x, topLeft.y + arcSize.height),
                            end    = Offset(topLeft.x + arcSize.width, topLeft.y),
                        ),
                        startAngle = 135f, sweepAngle = 270f * ringValue, useCenter = false,
                        topLeft    = topLeft, size = arcSize,
                        style      = Stroke(strokeW, cap = StrokeCap.Round),
                    )
                }
            }

            // Center readout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    isScanning -> {
                        Text(
                            "$doneCount/$totalCount",
                            style      = MaterialTheme.typography.headlineLarge,
                            color      = ElectricCyan,
                            fontWeight = FontWeight.Black,
                            fontSize   = 44.sp,
                        )
                        Text(
                            "SCANNING…",
                            style         = MaterialTheme.typography.labelSmall,
                            color         = TextSecondary,
                            letterSpacing = 2.sp,
                            fontWeight    = FontWeight.Bold,
                        )
                    }
                    showAllClean -> {
                        Text(
                            "✓",
                            style      = MaterialTheme.typography.headlineLarge,
                            color      = SuccessGreen,
                            fontWeight = FontWeight.Black,
                            fontSize   = 48.sp,
                        )
                        Text(
                            "ALL CLEAN",
                            style         = MaterialTheme.typography.labelSmall,
                            color         = SuccessGreen,
                            letterSpacing = 2.sp,
                            fontWeight    = FontWeight.Bold,
                        )
                    }
                    hasScanned -> {
                        Text(
                            reclaimableBytes.toReadableSize(),
                            style      = MaterialTheme.typography.headlineLarge,
                            color      = ElectricCyan,
                            fontWeight = FontWeight.Black,
                            fontSize   = 40.sp,
                        )
                        Text(
                            "CAN BE REVIEWED",
                            style         = MaterialTheme.typography.labelSmall,
                            color         = TextSecondary,
                            letterSpacing = 2.sp,
                            fontWeight    = FontWeight.Bold,
                        )
                        if (totalBytes > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${usedBytes.toReadableSize()} / ${totalBytes.toReadableSize()} used",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                    else -> {
                        Text(
                            "$pct%",
                            style      = MaterialTheme.typography.headlineLarge,
                            color      = ElectricCyan,
                            fontWeight = FontWeight.Black,
                            fontSize   = 48.sp,
                        )
                        Text(
                            "STORAGE USED",
                            style         = MaterialTheme.typography.labelSmall,
                            color         = TextSecondary,
                            letterSpacing = 2.sp,
                            fontWeight    = FontWeight.Bold,
                        )
                        if (totalBytes > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${usedBytes.toReadableSize()} / ${totalBytes.toReadableSize()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Scan button
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        if (isScanning) listOf(CyberCard, CyberCard)
                        else listOf(ElectricCyan, NeonViolet)
                    )
                )
                .clickable(enabled = !isScanning, onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    isScanning -> "SCANNING…"
                    hasScanned -> "RESCAN"
                    else       -> "SMART SCAN"
                },
                style         = MaterialTheme.typography.titleMedium,
                color         = if (isScanning) TextSecondary else AuroraBase,
                fontWeight    = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
        }

        // One-tap cleanup of the safe recommended set (goes to recoverable Trash).
        if (quickCleanBytes > 0L && !isScanning) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ErrorRed.copy(0.14f))
                    .border(1.dp, ErrorRed.copy(0.45f), RoundedCornerShape(24.dp))
                    .clickable(enabled = !isDeleting, onClick = onQuickClean),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "Clean ${quickCleanBytes.toReadableSize()}",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = ErrorRed,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Photo Cleanup entry card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiCleanupCard(state: AiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val subtitle = when (state) {
        is AiState.Loading -> if (state.total > 0) "Analyzing ${state.done}/${state.total} photos…" else "Starting on-device AI…"
        is AiState.Success -> {
            val r = state.result
            val junk = r.similarGroups.sumOf { (it.size - 1).coerceAtLeast(0) } + r.blurry.size + r.screenshots.size
            if (!r.available) "Model unavailable on this device"
            else if (junk == 0) "Analyzed ${r.analyzedCount} photos · all good"
            else "$junk photos to review · blurry, similar & screenshots"
        }
        is AiState.Error -> "Tap to try again"
        AiState.Idle -> "Find blurry, similar & screenshot photos — on-device"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(NeonViolet.copy(0.22f), ElectricCyan.copy(0.10f))))
            .border(1.dp, NeonViolet.copy(0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp)
                .background(NeonViolet.copy(0.2f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = NeonViolet, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI Photo Cleanup", style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary, fontWeight = FontWeight.Black)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(NeonViolet.copy(0.25f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("NEW", style = MaterialTheme.typography.labelSmall, color = NeonViolet,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (state is AiState.Loading) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(22.dp), color = NeonViolet, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = NeonViolet, modifier = Modifier.size(22.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(5.dp).background(ElectricCyan, CircleShape))
        Text(
            text,
            style         = MaterialTheme.typography.labelSmall,
            color         = ElectricCyan.copy(alpha = 0.7f),
            letterSpacing = 2.5.sp,
            fontWeight    = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cleanup suggestion row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    val tile = suggestion.tile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(corner = 16.dp, accent = tile.color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tile.color.copy(0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, null, tint = tile.color, modifier = Modifier.size(20.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                tile.label,
                style      = MaterialTheme.typography.titleSmall,
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                suggestion.subtitle,
                style    = MaterialTheme.typography.labelSmall,
                color    = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            suggestion.bytes.toReadableSize(),
            style      = MaterialTheme.typography.titleSmall,
            color      = tile.color,
            fontWeight = FontWeight.Black,
        )
        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quiet tool chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolChip(
    icon:     ImageVector,
    label:    String,
    tint:     Color,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .glassSurface(corner = 14.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            label,
            style      = MaterialTheme.typography.labelLarge,
            color      = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// System health row (RAM + battery) — read-only info chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SystemHealthRow(
    ramFreeMb:  Long,
    batteryPct: Int,
    isCharging: Boolean,
    modifier:   Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Free RAM — read-only system info (no "boost"/performance claims).
        HealthChip(
            dot   = ElectricCyan,
            label = "RAM Free",
            value = when {
                ramFreeMb <= 0    -> "—"
                ramFreeMb >= 1024 -> "%.1f GB".format(ramFreeMb / 1024f)
                else              -> "$ramFreeMb MB"
            },
            modifier = Modifier.weight(1f),
        )
        HealthChip(
            dot   = when {
                batteryPct < 0  -> TextMuted
                batteryPct < 20 -> Color(0xFFFF4560)
                batteryPct < 40 -> WarningAmber
                isCharging      -> SuccessGreen
                else            -> ElectricCyan
            },
            label = if (isCharging) "Charging" else "Battery",
            value = if (batteryPct >= 0) "$batteryPct%" else "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HealthChip(dot: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier
            .glassSurface(corner = 14.dp, accent = dot)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(9.dp).background(dot, CircleShape))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
