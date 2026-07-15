package io.github.varunkumar.spaceclean.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeleteRequest {
    /**
     * API 30+: launch this [intentSender]; call back with RESULT_OK to confirm.
     * [alreadyDeleted] lists any non-media / file:// items that were removed directly
     * BEFORE the dialog — the caller must prune those immediately, because they are
     * gone from disk even if the user cancels the dialog.
     */
    data class RequiresConfirmation(
        val intentSender:   IntentSender,
        val alreadyDeleted: List<Uri> = emptyList(),
    ) : DeleteRequest()

    /**
     * Files were removed directly with no dialog. [deletedUris] lists exactly what was
     * removed; [failedCount] is how many items could not be removed (surfaced to the user
     * instead of failing silently).
     */
    data class DirectSuccess(val deletedUris: List<Uri>, val failedCount: Int = 0) : DeleteRequest()

    data class Error(val message: String) : DeleteRequest()
}

/**
 * Deletion strategies by item kind:
 *
 * **Media (images / videos / audio)** — sent to the **system Trash** (recoverable ~30
 * days) via [MediaStore.createTrashRequest] on API 30+. `createTrashRequest` ONLY accepts
 * URIs from the Images/Video/Audio collections — a `MediaStore.Files` URI (as produced by
 * the Documents / Downloads / Largest / Old-files scans) makes it throw. Media URIs are
 * therefore remapped to their proper typed collection by MediaStore row id first.
 *
 * **Non-media `content://`** (PDFs, APKs, archives, logs…) — MediaStore has no Trash for
 * these; they are deleted directly through [ContentResolver.delete] (permitted broadly by
 * All-Files-Access), with a filesystem fallback for rows the resolver refuses.
 *
 * **`file://` URIs** (orphan folders, chat media) — deleted via [File.deleteRecursively].
 */
class FileDeleter(private val contentResolver: ContentResolver) {

    suspend fun initiateDelete(files: List<ScannedFile>): DeleteRequest = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext DeleteRequest.DirectSuccess(emptyList())

        val deleted = mutableListOf<Uri>()
        var failed  = 0

        // 1. file:// URIs (folders + legacy paths) — recursive direct delete.
        for (f in files.filter { it.uri.scheme == "file" }) {
            runCatching {
                val file = File(requireNotNull(f.uri.path))
                if (!file.exists() || file.deleteRecursively()) deleted += f.uri else failed++
            }.onFailure { failed++ }
        }

        val contentFiles = files.filter { it.uri.scheme == "content" }
        val (media, nonMedia) = contentFiles.partition {
            it.name.isImageName() || it.name.isVideoName() || it.name.isAudioName()
        }

        // 2. Non-media content:// — no Trash support; delete directly.
        for (f in nonMedia) {
            if (deleteContentRow(f)) deleted += f.uri else failed++
        }

        // 3. Media content:// — recoverable Trash on API 30+, direct delete below that.
        if (media.isEmpty()) {
            return@withContext DeleteRequest.DirectSuccess(deleted, failed)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val trashUris = media.map { typedMediaUri(it) }
                val pi = MediaStore.createTrashRequest(contentResolver, trashUris, true)
                DeleteRequest.RequiresConfirmation(pi.intentSender, alreadyDeleted = deleted)
            }.getOrElse {
                // Trash request refused (odd row, huge batch…) — fall back to direct
                // per-item deletion so the button is never dead.
                for (f in media) {
                    if (deleteContentRow(f)) deleted += f.uri else failed++
                }
                DeleteRequest.DirectSuccess(deleted, failed)
            }
        } else {
            for (f in media) {
                if (deleteContentRow(f)) deleted += f.uri else failed++
            }
            DeleteRequest.DirectSuccess(deleted, failed)
        }
    }

    /** Restores trashed items back to normal (untrash). content:// only, API 30+. */
    suspend fun initiateRestore(uris: List<Uri>): DeleteRequest = withContext(Dispatchers.IO) {
        val contentUris = uris.filter { it.scheme == "content" }
        if (contentUris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext DeleteRequest.DirectSuccess(emptyList())
        }
        runCatching {
            val pi = MediaStore.createTrashRequest(contentResolver, contentUris, false)
            DeleteRequest.RequiresConfirmation(pi.intentSender)
        }.getOrElse { e -> DeleteRequest.Error(e.message ?: "Failed to build restore request") }
    }

    /** Permanently deletes items (used from the in-app Trash). content:// only, API 30+. */
    suspend fun initiatePermanentDelete(uris: List<Uri>): DeleteRequest = withContext(Dispatchers.IO) {
        val contentUris = uris.filter { it.scheme == "content" }
        if (contentUris.isEmpty()) return@withContext DeleteRequest.DirectSuccess(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val pi = MediaStore.createDeleteRequest(contentResolver, contentUris)
                DeleteRequest.RequiresConfirmation(pi.intentSender)
            }.getOrElse { e -> DeleteRequest.Error(e.message ?: "Failed to build delete request") }
        } else {
            val deleted = mutableListOf<Uri>()
            for (uri in contentUris) runCatching {
                if (contentResolver.delete(uri, null, null) > 0) deleted += uri
            }
            DeleteRequest.DirectSuccess(deleted)
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Rebuilds a URI in the typed media collection (Images/Video/Audio) that
     * `createTrashRequest` requires. MediaStore row ids are shared across the Files
     * collection and the typed collections, so remapping by id is lossless.
     */
    private fun typedMediaUri(f: ScannedFile): Uri {
        val id = runCatching { ContentUris.parseId(f.uri) }.getOrNull() ?: return f.uri
        val collection = when {
            f.name.isImageName() -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            f.name.isVideoName() -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            f.name.isAudioName() -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else                 -> return f.uri
        }
        return ContentUris.withAppendedId(collection, id)
    }

    /**
     * Deletes one content:// row, falling back to the raw filesystem path (rebuilt from
     * the scan's relative path — valid under All-Files-Access) when the resolver refuses.
     */
    private fun deleteContentRow(f: ScannedFile): Boolean {
        val viaResolver = runCatching { contentResolver.delete(f.uri, null, null) > 0 }
            .getOrDefault(false)
        if (viaResolver) return true

        return runCatching {
            val abs = File(Environment.getExternalStorageDirectory(), "${f.path}/${f.name}")
            if (abs.isFile && abs.delete()) {
                // Remove the stale MediaStore row so re-scans don't resurrect a ghost entry.
                runCatching { contentResolver.delete(f.uri, null, null) }
                true
            } else false
        }.getOrDefault(false)
    }
}

// Kept here (not in the UI layer) so the deleter has no Compose dependencies.
private fun String.isImageName() =
    listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif")
        .any { endsWith(it, ignoreCase = true) }

private fun String.isVideoName() =
    listOf(".mp4", ".mkv", ".mov", ".webm", ".3gp", ".avi", ".m4v")
        .any { endsWith(it, ignoreCase = true) }

private fun String.isAudioName() =
    listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac", ".amr")
        .any { endsWith(it, ignoreCase = true) }
