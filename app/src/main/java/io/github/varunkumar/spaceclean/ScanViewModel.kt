package io.github.varunkumar.spaceclean

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.varunkumar.spaceclean.scanner.AppInfo
import io.github.varunkumar.spaceclean.scanner.DeleteEffects
import io.github.varunkumar.spaceclean.scanner.DeleteRequest
import io.github.varunkumar.spaceclean.scanner.FileDeleter
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.scanner.ScanRepository
import io.github.varunkumar.spaceclean.scanner.TrashedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Domain types
// ─────────────────────────────────────────────────────────────────────────────

enum class ScanType(val displayName: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    APPS("Apps"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    DOWNLOADS("Downloads & Junk"),
    CHAT_MEDIA("Chat Media"),
    EMPTY_FOLDERS("Leftover Folders"),
    USELESS("Useless Files"),
    LARGEST("Largest Files"),
    OLD_FILES("Old & Unused"),
    DUPLICATES("Duplicate Files"),
}

sealed class ScanState {
    object Idle                                      : ScanState()
    object Loading                                   : ScanState()
    data class Success(val files: List<ScannedFile>) : ScanState()
    data class Error(val message: String)            : ScanState()
}

sealed class AppStorageState {
    object Idle                                  : AppStorageState()
    object Loading                               : AppStorageState()
    data class Success(val apps: List<AppInfo>)  : AppStorageState()
    data class Error(val message: String)        : AppStorageState()
}

sealed class DuplicatesState {
    object Idle                                              : DuplicatesState()
    object Loading                                           : DuplicatesState()
    /** Each inner list is a set of ≥2 files with identical content. */
    data class Success(val groups: List<List<ScannedFile>>) : DuplicatesState()
    data class Error(val message: String)                   : DuplicatesState()
}

sealed class TrashState {
    object Idle                                        : TrashState()
    object Loading                                     : TrashState()
    data class Success(val items: List<TrashedItem>)   : TrashState()
    data class Error(val message: String)              : TrashState()
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class HomeUiState(
    // Storage meter
    val totalStorageBytes: Long = 0L,
    val usedStorageBytes:  Long = 0L,

    // 5-manager scan states
    val photosState:    ScanState       = ScanState.Idle,
    val videosState:    ScanState       = ScanState.Idle,
    val appStorageState: AppStorageState = AppStorageState.Idle,
    val documentsState: ScanState       = ScanState.Idle,
    val audioState:     ScanState       = ScanState.Idle,
    val downloadsState: ScanState       = ScanState.Idle,
    val chatMediaState: ScanState       = ScanState.Idle,
    val emptyFoldersState: ScanState    = ScanState.Idle,
    val uselessState:   ScanState       = ScanState.Idle,
    val largestState:   ScanState       = ScanState.Idle,
    val oldFilesState:  ScanState       = ScanState.Idle,
    val duplicatesState: DuplicatesState = DuplicatesState.Idle,
    val trashState:      TrashState      = TrashState.Idle,

    // Smart auto-selection — populated for PHOTOS
    val recommendedForDeletion: Set<Uri>              = emptySet(),
    val bestPairMap:            Map<Uri, ScannedFile> = emptyMap(),

    // Viewer / compare
    val viewingFile: ScannedFile?                    = null,
    val comparePair: Pair<ScannedFile, ScannedFile>? = null,

    // Targeted folder scan (Storage Access Framework tree uri)
    val customFolderUri: Uri? = null,

    // Common
    val permissionGranted:    Boolean   = false,
    val totalSpaceSavedBytes: Long      = 0L,
    val isDeletingFiles:      Boolean   = false,
    val pendingNavigation:    ScanType? = null,

    // Which categories Quick Clean auto-selects (user-editable in Settings).
    val quickCleanCategories: Set<String> = QuickCleanCategory.DEFAULTS,
)

/**
 * The categories Quick Clean can auto-select from, with user-facing metadata.
 * DOCS is off by default — documents are auto-SUGGESTED never auto-SELECTED
 * unless the user opts in from Settings.
 */
enum class QuickCleanCategory(
    val key:      String,
    val title:    String,
    val subtitle: String,
    val defaultOn: Boolean,
) {
    SIMILAR   ("similar",    "Similar photos",   "Worse copies of near-identical shots",       true),
    DUPLICATES("duplicates", "Duplicate copies", "Exact copies — one original always kept",    true),
    FOLDERS   ("folders",    "Leftover folders", "Empty folders from uninstalled apps",        true),
    DOWNLOADS ("downloads",  "Download junk",    "Old APKs, ZIPs, logs & temp downloads",      true),
    USELESS   ("useless",    "Useless files",    "GIFs, temp, log & abandoned partial files",  true),
    DOCS      ("docs",       "Documents",        "PDFs & Office files — review these yourself", false);

    companion object {
        val DEFAULTS: Set<String> = entries.filter { it.defaultOn }.map { it.key }.toSet()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository(application)
    private val deleter    = FileDeleter(application)
    private val prefs      = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var pendingDeleteFiles: List<ScannedFile> = emptyList()

    private val _uiState = MutableStateFlow(
        HomeUiState(
            totalSpaceSavedBytes = prefs.getLong(KEY_SPACE_SAVED, 0L),
            quickCleanCategories = prefs.getString(KEY_QUICK_CLEAN_CATS, null)
                ?.split(',')?.filter { it.isNotBlank() }?.toSet()
                ?: QuickCleanCategory.DEFAULTS,
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ── Quick Clean category preferences ───────────────────────────────────────

    fun setQuickCleanCategory(key: String, enabled: Boolean) {
        val next = if (enabled) _uiState.value.quickCleanCategories + key
                   else _uiState.value.quickCleanCategories - key
        prefs.edit().putString(KEY_QUICK_CLEAN_CATS, next.joinToString(",")).apply()
        _uiState.update { it.copy(quickCleanCategories = next) }
    }

    init { refreshStorageStats() }

    // ── Permission ─────────────────────────────────────────────────────────────

    fun onPermissionGranted() = _uiState.update { it.copy(permissionGranted = true) }

    // ── Storage stats ──────────────────────────────────────────────────────────

    fun refreshStorageStats() {
        viewModelScope.launch {
            val (total, used) = repository.getStorageStats()
            _uiState.update { it.copy(totalStorageBytes = total, usedStorageBytes = used) }
        }
    }

    // ── Navigation signal ──────────────────────────────────────────────────────

    fun clearPendingNavigation() = _uiState.update { it.copy(pendingNavigation = null) }

    // ── Targeted folder scan ─────────────────────────────────────────────────────

    fun setCustomFolder(uri: Uri?) = _uiState.update { it.copy(customFolderUri = uri) }

    fun recordSpaceFreed(bytes: Long) = recordSpaceSaved(bytes)

    // ── Viewer / compare ───────────────────────────────────────────────────────

    fun openFileViewer(file: ScannedFile)  = _uiState.update { it.copy(viewingFile = file) }
    fun closeFileViewer()                  = _uiState.update { it.copy(viewingFile = null) }

    fun openCompare(keep: ScannedFile, delete: ScannedFile) =
        _uiState.update { it.copy(comparePair = Pair(keep, delete)) }

    fun closeCompare() = _uiState.update { it.copy(comparePair = null) }

    // ── Scan launchers ─────────────────────────────────────────────────────────

    /**
     * [navigate] = true when a single tile is tapped (jump straight into the list);
     * false when triggered by "Smart Scan All", which must populate every tile in
     * place without yanking the user off the dashboard.
     */
    fun scanPhotos(navigate: Boolean = true) {
        if (!_uiState.value.permissionGranted) return
        if (_uiState.value.photosState is ScanState.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(photosState = ScanState.Loading) }
            runCatching { repository.getPhotoData() }
                .onSuccess { (files, groups) ->
                    val (recommended, bestPairs) = analyzeGroups(groups)
                    _uiState.update { state ->
                        state.copy(
                            photosState            = ScanState.Success(files),
                            recommendedForDeletion = state.recommendedForDeletion + recommended,
                            bestPairMap            = state.bestPairMap + bestPairs,
                            pendingNavigation      = if (navigate) ScanType.PHOTOS else state.pendingNavigation,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(photosState = ScanState.Error(e.message ?: "Scan failed")) }
                }
        }
    }

    fun scanVideos(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.VIDEOS else null,
        getState   = { _uiState.value.videosState },
        setLoading = { _uiState.update { it.copy(videosState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(videosState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(videosState = ScanState.Error(m)) } },
        fetch      = { repository.getVideos() },
    )

    fun scanAppStorage(navigate: Boolean = true) {
        if (_uiState.value.appStorageState is AppStorageState.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(appStorageState = AppStorageState.Loading) }
            runCatching { repository.getAppStorage() }
                .onSuccess { apps ->
                    _uiState.update { it.copy(
                        appStorageState   = AppStorageState.Success(apps),
                        pendingNavigation = if (navigate) ScanType.APPS else it.pendingNavigation,
                    ) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(appStorageState = AppStorageState.Error(e.message ?: "Scan failed")) }
                }
        }
    }

    fun scanDocuments(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.DOCUMENTS else null,
        getState   = { _uiState.value.documentsState },
        setLoading = { _uiState.update { it.copy(documentsState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(documentsState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(documentsState = ScanState.Error(m)) } },
        fetch      = { repository.getDocuments() },
    )

    fun scanAudio(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.AUDIO else null,
        getState   = { _uiState.value.audioState },
        setLoading = { _uiState.update { it.copy(audioState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(audioState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(audioState = ScanState.Error(m)) } },
        fetch      = { repository.getAudioFiles() },
    )

    fun scanDownloads(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.DOWNLOADS else null,
        getState   = { _uiState.value.downloadsState },
        setLoading = { _uiState.update { it.copy(downloadsState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(downloadsState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(downloadsState = ScanState.Error(m)) } },
        fetch      = { repository.getDownloadJunk() },
    )

    fun scanChatMedia(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.CHAT_MEDIA else null,
        getState   = { _uiState.value.chatMediaState },
        setLoading = { _uiState.update { it.copy(chatMediaState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(chatMediaState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(chatMediaState = ScanState.Error(m)) } },
        fetch      = { repository.getChatMedia() },
    )

    fun scanEmptyFolders(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.EMPTY_FOLDERS else null,
        getState   = { _uiState.value.emptyFoldersState },
        setLoading = { _uiState.update { it.copy(emptyFoldersState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(emptyFoldersState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(emptyFoldersState = ScanState.Error(m)) } },
        fetch      = { repository.getEmptyFolders() },
    )

    fun scanUseless(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.USELESS else null,
        getState   = { _uiState.value.uselessState },
        setLoading = { _uiState.update { it.copy(uselessState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(uselessState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(uselessState = ScanState.Error(m)) } },
        fetch      = { repository.getUselessFiles() },
    )

    fun scanLargest(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.LARGEST else null,
        getState   = { _uiState.value.largestState },
        setLoading = { _uiState.update { it.copy(largestState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(largestState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(largestState = ScanState.Error(m)) } },
        fetch      = { repository.getLargestFiles() },
    )

    fun scanOldFiles(navigate: Boolean = true) = launchScan(
        navTarget  = if (navigate) ScanType.OLD_FILES else null,
        getState   = { _uiState.value.oldFilesState },
        setLoading = { _uiState.update { it.copy(oldFilesState = ScanState.Loading) } },
        setSuccess = { f -> _uiState.update { it.copy(oldFilesState = ScanState.Success(f)) } },
        setError   = { m -> _uiState.update { it.copy(oldFilesState = ScanState.Error(m)) } },
        fetch      = { repository.getOldestFiles() },
    )

    // Duplicates produce GROUPS, not a flat list — handled separately. Navigation is
    // driven by the tile (dedicated DuplicatesScreen), so pendingNavigation isn't set.
    fun scanDuplicates() {
        if (!_uiState.value.permissionGranted) return
        if (_uiState.value.duplicatesState is DuplicatesState.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(duplicatesState = DuplicatesState.Loading) }
            runCatching { repository.getDuplicateGroups() }
                .onSuccess { groups -> _uiState.update { it.copy(duplicatesState = DuplicatesState.Success(groups)) } }
                .onFailure { e -> _uiState.update { it.copy(duplicatesState = DuplicatesState.Error(e.message ?: "Scan failed")) } }
        }
    }

    // ── System Trash (recently deleted) ─────────────────────────────────────────

    fun scanTrash() {
        viewModelScope.launch {
            // Only show the spinner on first load — silent refresh keeps the current
            // list on screen instead of flashing while re-querying.
            if (_uiState.value.trashState !is TrashState.Success) {
                _uiState.update { it.copy(trashState = TrashState.Loading) }
            }
            runCatching { repository.getTrashedItems() }
                .onSuccess { items -> _uiState.update { it.copy(trashState = TrashState.Success(items)) } }
                .onFailure { e -> _uiState.update { it.copy(trashState = TrashState.Error(e.message ?: "Failed")) } }
        }
    }

    /**
     * Restore trashed items (untrash). App-trash items are restored in place; system-media
     * items need the returned intent sender launched by the screen.
     */
    fun restoreFromTrash(items: List<TrashedItem>, onNeedsConfirmation: (IntentSender) -> Unit) {
        if (items.isEmpty()) return
        val appIds    = items.mapNotNull { it.appTrashId }.toSet()
        val mediaUris = items.filter { it.appTrashId == null }.map { it.uri }
        viewModelScope.launch {
            if (appIds.isNotEmpty()) repository.restoreFromAppTrash(appIds)
            if (mediaUris.isEmpty()) { onTrashChanged(); return@launch }
            when (val r = deleter.initiateRestore(mediaUris)) {
                is DeleteRequest.RequiresConfirmation -> onNeedsConfirmation(r.intentSender)
                else -> onTrashChanged()
            }
        }
    }

    /** Permanently delete trashed items now (skip the ~30-day wait). */
    fun permanentlyDeleteTrash(items: List<TrashedItem>, onNeedsConfirmation: (IntentSender) -> Unit) {
        if (items.isEmpty()) return
        val appIds    = items.mapNotNull { it.appTrashId }.toSet()
        val mediaUris = items.filter { it.appTrashId == null }.map { it.uri }
        viewModelScope.launch {
            if (appIds.isNotEmpty()) repository.purgeAppTrash(appIds)
            if (mediaUris.isEmpty()) { onTrashChanged(); return@launch }
            when (val r = deleter.initiatePermanentDelete(mediaUris)) {
                is DeleteRequest.RequiresConfirmation -> onNeedsConfirmation(r.intentSender)
                else -> onTrashChanged()
            }
        }
    }

    /** Called after a restore/permanent-delete confirmation returns OK. */
    fun onTrashChanged() {
        scanTrash()
        refreshStorageStats()
    }

    // ── In-app uninstaller callback ────────────────────────────────────────────

    fun removeInstalledApp(packageName: String) {
        _uiState.update { state ->
            val current = state.appStorageState as? AppStorageState.Success ?: return@update state
            state.copy(
                appStorageState = AppStorageState.Success(
                    current.apps.filter { it.packageName != packageName }
                )
            )
        }
        refreshStorageStats()
    }

    // ── Auto-selection algorithm ───────────────────────────────────────────────

    /**
     * Ranks files in each group by pixel count (using stored widthPx/heightPx when
     * available, falling back to BitmapFactory.inJustDecodeBounds for legacy files),
     * then by modification date.  Top-ranked = keeper; others = recommended for deletion.
     */
    private suspend fun analyzeGroups(
        groups: Collection<List<ScannedFile>>,
    ): Pair<Set<Uri>, Map<Uri, ScannedFile>> = withContext(Dispatchers.IO) {
        val cr          = getApplication<Application>().contentResolver
        val recommended = mutableSetOf<Uri>()
        val bestPairs   = mutableMapOf<Uri, ScannedFile>()

        groups.filter { it.size > 1 }.forEach { group ->
            val ranked = group.map { file ->
                val pixels = when {
                    file.widthPx > 0 && file.heightPx > 0 ->
                        file.widthPx.toLong() * file.heightPx.toLong()
                    else -> runCatching {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        cr.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                        opts.outWidth.toLong() * opts.outHeight.toLong()
                    }.getOrElse { 0L }
                }
                file to pixels
            }.sortedWith(
                compareByDescending<Pair<ScannedFile, Long>> { it.second }
                    .thenByDescending { it.first.dateModifiedMs }
            )

            val best = ranked.first().first
            ranked.drop(1).forEach { (f, _) ->
                recommended.add(f.uri)
                bestPairs[f.uri] = best
            }
        }

        Pair(recommended, bestPairs)
    }

    // ── Deletion pipeline ──────────────────────────────────────────────────────

    fun requestDelete(
        selectedFiles:       List<ScannedFile>,
        onNeedsConfirmation: (IntentSender) -> Unit,
    ) {
        if (selectedFiles.isEmpty()) return
        pendingDeleteFiles = selectedFiles
        _uiState.update { it.copy(isDeletingFiles = true) }

        viewModelScope.launch {
            when (val result = deleter.initiateDelete(selectedFiles)) {
                is DeleteRequest.RequiresConfirmation -> {
                    // Non-media items were already removed from disk before the system
                    // trash dialog — prune them now, regardless of what the user picks
                    // in the dialog, and keep only the media items pending.
                    if (result.alreadyDeleted.isNotEmpty()) {
                        val gone = result.alreadyDeleted.toHashSet()
                        val goneFiles = pendingDeleteFiles.filter { it.uri in gone }
                        recordSpaceSaved(goneFiles.sumOf { it.sizeBytes })
                        pruneResultsOf(gone)
                        pendingDeleteFiles = pendingDeleteFiles.filter { it.uri !in gone }
                    }
                    _uiState.update { it.copy(isDeletingFiles = false) }
                    onNeedsConfirmation(result.intentSender)
                }
                // Only prune what was actually removed (some leftover folders may be
                // blocked by the OS); they stay visible instead of vanishing on disk.
                is DeleteRequest.DirectSuccess -> {
                    if (result.failedCount > 0) {
                        toast("${result.failedCount} item${if (result.failedCount == 1) "" else "s"} couldn't be deleted")
                    }
                    finalizeDelete(result.deletedUris)
                }
                is DeleteRequest.Error -> {
                    pendingDeleteFiles = emptyList()
                    _uiState.update { it.copy(isDeletingFiles = false) }
                    toast("Delete failed: ${result.message}")
                }
            }
        }
    }

    /**
     * [deletedUris] = null → the system delete dialog confirmed all pending URIs
     * (API 30+ all-or-nothing). Non-null → only these were removed directly.
     */
    fun finalizeDelete(deletedUris: List<Uri>? = null) {
        val removedSet = deletedUris?.toHashSet()
        val files      = if (removedSet == null) pendingDeleteFiles
                         else pendingDeleteFiles.filter { it.uri in removedSet }

        recordSpaceSaved(files.sumOf { it.sizeBytes })
        pruneResultsOf(files.map { it.uri }.toHashSet())

        pendingDeleteFiles = emptyList()
        _uiState.update { it.copy(isDeletingFiles = false) }

        if (files.isNotEmpty()) {
            DeleteEffects.playDeleteSound()
            // Media just landed in the system Trash — refresh so Recently Deleted
            // shows it immediately.
            scanTrash()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }

    fun onDeleteCancelled() {
        pendingDeleteFiles = emptyList()
        _uiState.update { it.copy(isDeletingFiles = false) }
    }

    // ── Space-saved counter ────────────────────────────────────────────────────

    fun recordSpaceSaved(bytes: Long) {
        if (bytes <= 0L) return
        val newTotal = _uiState.value.totalSpaceSavedBytes + bytes
        prefs.edit().putLong(KEY_SPACE_SAVED, newTotal).apply()
        _uiState.update { it.copy(totalSpaceSavedBytes = newTotal) }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun launchScan(
        navTarget:  ScanType?,
        getState:   () -> ScanState,
        setLoading: () -> Unit,
        setSuccess: (List<ScannedFile>) -> Unit,
        setError:   (String) -> Unit,
        fetch:      suspend () -> List<ScannedFile>,
    ) {
        if (!_uiState.value.permissionGranted) return
        if (getState() is ScanState.Loading) return
        viewModelScope.launch {
            setLoading()
            runCatching { fetch() }
                .onSuccess {
                    setSuccess(it)
                    if (navTarget != null) _uiState.update { s -> s.copy(pendingNavigation = navTarget) }
                }
                .onFailure { setError(it.message ?: "Scan failed") }
        }
    }

    private fun pruneResultsOf(deletedUris: Set<Uri>) {
        fun ScanState.pruned() = if (this is ScanState.Success)
            ScanState.Success(files.filter { it.uri !in deletedUris }) else this

        fun DuplicatesState.pruned() = if (this is DuplicatesState.Success)
            DuplicatesState.Success(
                groups.map { g -> g.filter { it.uri !in deletedUris } }.filter { it.size >= 2 }
            ) else this

        _uiState.update { s ->
            s.copy(
                photosState            = s.photosState.pruned(),
                videosState            = s.videosState.pruned(),
                documentsState         = s.documentsState.pruned(),
                audioState             = s.audioState.pruned(),
                downloadsState         = s.downloadsState.pruned(),
                chatMediaState         = s.chatMediaState.pruned(),
                emptyFoldersState      = s.emptyFoldersState.pruned(),
                uselessState           = s.uselessState.pruned(),
                largestState           = s.largestState.pruned(),
                oldFilesState          = s.oldFilesState.pruned(),
                duplicatesState        = s.duplicatesState.pruned(),
                recommendedForDeletion = s.recommendedForDeletion - deletedUris,
                bestPairMap            = s.bestPairMap.filterKeys { it !in deletedUris },
                // If the open viewer/compare target was just deleted, clear it so the
                // NavGraph pops the screen instead of showing a missing file.
                viewingFile            = s.viewingFile?.takeUnless { it.uri in deletedUris },
                comparePair            = s.comparePair?.takeUnless {
                    it.first.uri in deletedUris || it.second.uri in deletedUris
                },
            )
        }

        refreshStorageStats()
    }

    companion object {
        private const val PREFS_NAME           = "spaceclean_prefs"
        private const val KEY_SPACE_SAVED      = "space_saved_bytes"
        private const val KEY_QUICK_CLEAN_CATS = "quick_clean_categories"
    }
}

// ── Extension ─────────────────────────────────────────────────────────────────

fun Long.toReadableSize(): String = when {
    this >= 1_073_741_824L -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576L     -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024L         -> "%.0f KB".format(this / 1_024.0)
    else                   -> "$this B"
}
