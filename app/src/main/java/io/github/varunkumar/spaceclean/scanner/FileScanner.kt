package io.github.varunkumar.spaceclean.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * All MediaStore queries run synchronously — always call from a background dispatcher.
 * Zero network I/O; zero internet permission required.
 */
class FileScanner(private val contentResolver: ContentResolver) {

    // ── Large videos (≥ minBytes) with duration ───────────────────────────────

    fun findLargeVideos(minBytes: Long = 50L * 1_024 * 1_024): List<ScannedFile> {
        val collection = videoCollection()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
        )
        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projection,
            "${MediaStore.Video.Media.SIZE} >= ?",
            arrayOf(minBytes.toString()),
            "${MediaStore.Video.Media.SIZE} DESC",
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathIdx = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val durIdx  = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = cursor.getLong(sizeIdx),
                    dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                    durationMs     = if (durIdx >= 0) cursor.getLong(durIdx) else 0L,
                )
            }
        }
        return results
    }

    // ── Duplicate photos (same exact byte size, > 1 occurrence) ──────────────

    fun findDuplicatePhotos(minSizeBytes: Long = 100L * 1_024): List<ScannedFile> {
        val all = queryImages(
            selection     = "${MediaStore.Images.Media.SIZE} > ?",
            selectionArgs = arrayOf(minSizeBytes.toString()),
            sortOrder     = "${MediaStore.Images.Media.SIZE} DESC",
        )
        return all
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }
            .values
            .flatten()
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    fun findDocuments(): List<ScannedFile> {
        val collection = filesCollection()
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            // RELATIVE_PATH added in API 29; DATA works on all levels
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                add(MediaStore.Files.FileColumns.DATA)
        }.toTypedArray()

        val col = MediaStore.Files.FileColumns.DISPLAY_NAME
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0 AND (" +
                "LOWER($col) LIKE '%.pdf'  OR LOWER($col) LIKE '%.apk'  OR " +
                "LOWER($col) LIKE '%.zip'  OR LOWER($col) LIKE '%.rar'  OR " +
                "LOWER($col) LIKE '%.7z'   OR LOWER($col) LIKE '%.docx' OR " +
                "LOWER($col) LIKE '%.doc'  OR LOWER($col) LIKE '%.xlsx' OR " +
                "LOWER($col) LIKE '%.xls'  OR LOWER($col) LIKE '%.pptx' OR " +
                "LOWER($col) LIKE '%.ppt'  OR LOWER($col) LIKE '%.txt'  OR " +
                "LOWER($col) LIKE '%.csv'  OR LOWER($col) LIKE '%.log')"

        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Files.FileColumns.SIZE} DESC",
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val size = cursor.getLong(sizeIdx)
                if (size <= 0L) continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = size,
                    dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                )
            }
        }
        return results
    }

    // ── Audio files (music, voice notes, podcasts) ────────────────────────────

    fun findAudioFiles(minSizeBytes: Long = 100L * 1_024): List<ScannedFile> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
        )
        // Use RELATIVE_PATH only if available (API 29+)
        val projectionFull = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            projection + MediaStore.Audio.Media.RELATIVE_PATH
        else
            projection

        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projectionFull,
            "${MediaStore.Audio.Media.SIZE} >= ?",
            arrayOf(minSizeBytes.toString()),
            "${MediaStore.Audio.Media.SIZE} DESC",
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val durIdx  = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val pathIdx = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = cursor.getLong(sizeIdx),
                    dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                    durationMs     = if (durIdx >= 0) cursor.getLong(durIdx) else 0L,
                )
            }
        }
        return results
    }

    // ── Largest files (everything ≥ minBytes, any type, biggest first) ─────────

    fun findLargestFiles(minBytes: Long = 5L * 1_024 * 1_024, limit: Int = 200): List<ScannedFile> {
        val collection = filesCollection()
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                add(MediaStore.Files.FileColumns.DATA)
        }.toTypedArray()

        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projection,
            "${MediaStore.Files.FileColumns.SIZE} >= ?",
            arrayOf(minBytes.toString()),
            "${MediaStore.Files.FileColumns.SIZE} DESC",
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext() && results.size < limit) {
                val name = cursor.getString(nameIdx) ?: continue
                val size = cursor.getLong(sizeIdx)
                if (size < minBytes) continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = size,
                    dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                )
            }
        }
        return results
    }

    // ── Oldest / least-recently-modified files (any type ≥ minBytes) ───────────

    fun findOldestFiles(minBytes: Long = 1L * 1_024 * 1_024, limit: Int = 200): List<ScannedFile> {
        val collection = filesCollection()
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                add(MediaStore.Files.FileColumns.DATA)
        }.toTypedArray()

        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projection,
            "${MediaStore.Files.FileColumns.SIZE} >= ? AND ${MediaStore.Files.FileColumns.DATE_MODIFIED} > 0",
            arrayOf(minBytes.toString()),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC",   // oldest first
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext() && results.size < limit) {
                val name = cursor.getString(nameIdx) ?: continue
                val size = cursor.getLong(sizeIdx)
                if (size < minBytes) continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = size,
                    dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                )
            }
        }
        return results
    }

    // ── Useless files — things users almost never miss ────────────────────────
    // GIFs saved by chat apps, temp/log/backup files, and abandoned partial
    // downloads. Everything here is safe to surface for one-tap cleanup.

    fun findUselessFiles(limit: Int = 400): List<ScannedFile> {
        val collection = filesCollection()
        val col        = MediaStore.Files.FileColumns.DISPLAY_NAME
        val exts = listOf(
            ".gif", ".tmp", ".log", ".bak", ".old",
            ".part", ".partial", ".crdownload", ".download",
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0 AND (" +
                exts.joinToString(" OR ") { "LOWER($col) LIKE '%$it'" } + ")"

        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                add(MediaStore.Files.FileColumns.DATA)
        }.toTypedArray()

        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Files.FileColumns.SIZE} DESC",
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
            else
                cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext() && results.size < limit) {
                val name = cursor.getString(nameIdx) ?: continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = cursor.getLong(sizeIdx),
                    dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                )
            }
        }
        return results
    }

    // ── Download junk ─────────────────────────────────────────────────────────

    fun findDownloadJunk(): List<ScannedFile> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) findDownloadJunkQ() else findDownloadJunkLegacy()

    private fun findDownloadJunkQ(): List<ScannedFile> {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val col        = MediaStore.Downloads.DISPLAY_NAME
        val selection  = "LOWER($col) LIKE '%.pdf' OR LOWER($col) LIKE '%.apk' OR " +
                         "LOWER($col) LIKE '%.log' OR LOWER($col) LIKE '%.zip' OR LOWER($col) LIKE '%.tmp'"
        return queryDownloads(collection, selection)
    }

    private fun findDownloadJunkLegacy(): List<ScannedFile> {
        val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() || !dir.canRead()) return emptyList()
        val exts = setOf("pdf", "apk", "log", "zip", "tmp")
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in exts }
            .map { f ->
                ScannedFile(
                    uri            = Uri.fromFile(f),
                    name           = f.name,
                    path           = f.parent ?: "",
                    sizeBytes      = f.length(),
                    dateModifiedMs = f.lastModified(),
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    private fun queryDownloads(collection: Uri, selection: String): List<ScannedFile> {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.RELATIVE_PATH,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
        )
        val results = mutableListOf<ScannedFile>()
        contentResolver.query(collection, projection, selection, null, "${MediaStore.Downloads.SIZE} DESC")
            ?.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    results += ScannedFile(
                        uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                        name           = name,
                        path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                        sizeBytes      = cursor.getLong(sizeIdx),
                        dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                    )
                }
            }
        return results
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun queryImages(
        selection:     String?,
        selectionArgs: Array<String>?,
        sortOrder:     String?,
    ): List<ScannedFile> {
        val collection = imageCollection()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        val results = mutableListOf<ScannedFile>()
        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    results += ScannedFile(
                        uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                        name           = name,
                        path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                        sizeBytes      = cursor.getLong(sizeIdx),
                        dateModifiedMs = cursor.getLong(dateIdx) * 1_000L,
                    )
                }
            }
        return results
    }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun imageCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun filesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Files.getContentUri("external")
}
