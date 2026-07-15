package io.github.varunkumar.spaceclean.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanRepository(private val context: Context) {

    private val scanner       = FileScanner(context.contentResolver)
    private val simScanner    = SimilarPhotoScanner(context.contentResolver)
    private val appScanner    = AppStorageScanner(context)
    private val advCleaners   = AdvancedCleaners(context)
    private val dupScanner    = DuplicateScanner(context)

    // ── Photos — exact duplicates + similar/burst photos combined ─────────────

    /**
     * Returns (flatList, groups) where:
     *  - flatList is every file that belongs to any duplicate or burst group
     *  - groups is the list of groups (each ≥ 2 files) driving auto-selection
     */
    suspend fun getPhotoData(): Pair<List<ScannedFile>, List<List<ScannedFile>>> =
        withContext(Dispatchers.IO) {
            // Exact duplicates grouped by identical byte-size
            val dupFiles  = scanner.findDuplicatePhotos(minSizeBytes = 50L * 1_024)
            val dupGroups = dupFiles
                .groupBy { it.sizeBytes }
                .values
                .filter { it.size > 1 }
                .toList()

            // Similar / burst photos grouped by dimension + sequential filename
            val simGroups = simScanner.findSimilarGroups()

            // Merge, keeping unique URIs
            val allGroups = dupGroups + simGroups
            val seen      = HashSet<Uri>()
            val allFiles  = mutableListOf<ScannedFile>()
            for (group in allGroups) {
                for (file in group) {
                    if (seen.add(file.uri)) allFiles.add(file)
                }
            }

            Pair(allFiles, allGroups)
        }

    // ── Videos ────────────────────────────────────────────────────────────────

    suspend fun getVideos(minMb: Long = 50L): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findLargeVideos(minMb * 1_024 * 1_024) }

    // ── Apps ──────────────────────────────────────────────────────────────────

    suspend fun getAppStorage(): List<AppInfo> =
        withContext(Dispatchers.IO) { appScanner.findAppsByStorage() }

    // ── Documents ─────────────────────────────────────────────────────────────

    suspend fun getDocuments(): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findDocuments() }

    // ── Audio ─────────────────────────────────────────────────────────────────

    suspend fun getAudioFiles(): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findAudioFiles() }

    // ── Downloads & junk (APK / ZIP / LOG / TMP in the Downloads folder) ────────

    suspend fun getDownloadJunk(): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findDownloadJunk() }

    // ── Useless files (gifs, temp, logs, abandoned partial downloads) ──────────

    suspend fun getUselessFiles(): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findUselessFiles() }

    // ── Largest files across all storage ───────────────────────────────────────

    suspend fun getLargestFiles(): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findLargestFiles() }

    // ── Oldest / least-recently-used files ──────────────────────────────────────

    suspend fun getOldestFiles(): List<ScannedFile> =
        withContext(Dispatchers.IO) { scanner.findOldestFiles() }

    // ── Exact duplicate files across storage + chat apps ────────────────────────

    suspend fun getDuplicateGroups(): List<List<ScannedFile>> =
        withContext(Dispatchers.IO) { dupScanner.findDuplicateGroups() }

    // ── Chat media residue (WhatsApp / Telegram / Messenger / Viber / Signal) ──

    suspend fun getChatMedia(): List<ScannedFile> =
        withContext(Dispatchers.IO) { advCleaners.findChatMedia() }

    // ── Leftover / orphan folders (from uninstalled apps + empty top-level dirs) ─
    // Surface only entries the app can actually remove, so the Clean button is never
    // dead: a folder is deletable when its parent directory is writable. On API 30+
    // the OS blocks Android/data & Android/obb, so those are naturally excluded.

    suspend fun getEmptyFolders(): List<ScannedFile> = withContext(Dispatchers.IO) {
        advCleaners.findCorpseFolders().filter { sf ->
            runCatching {
                val path = sf.uri.path ?: return@runCatching false
                java.io.File(path).parentFile?.canWrite() == true
            }.getOrDefault(false)
        }
    }

    // ── System Trash (recently deleted, recoverable) ────────────────────────────
    // Android 11+ keeps deleted media in a Trash for ~30 days. With All-Files-Access
    // we can enumerate every trashed item and offer restore / permanent delete.

    suspend fun getTrashedItems(): List<TrashedItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_EXPIRES,
        )
        val queryArgs = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Files.FileColumns.DATE_EXPIRES} DESC",
            )
        }

        val out = mutableListOf<TrashedItem>()
        runCatching {
            context.contentResolver.query(collection, projection, queryArgs, null)?.use { c ->
                val idIdx   = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val expIdx  = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx) ?: continue
                    out += TrashedItem(
                        uri       = ContentUris.withAppendedId(collection, c.getLong(idIdx)),
                        name      = name,
                        sizeBytes = c.getLong(sizeIdx),
                        // DATE_EXPIRES is stored in seconds.
                        expiresMs = if (expIdx >= 0 && !c.isNull(expIdx)) c.getLong(expIdx) * 1_000L else 0L,
                    )
                }
            }
        }
        out
    }

    // ── Storage statistics ────────────────────────────────────────────────────

    suspend fun getStorageStats(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        try {
            val path  = Environment.getExternalStorageDirectory()
            val stat  = StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free  = stat.availableBlocksLong * stat.blockSizeLong
            Pair(total, total - free)
        } catch (_: Exception) { Pair(0L, 0L) }
    }
}
