package io.github.varunkumar.spaceclean.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeleteRequest {
    /**
     * API 30+: launch this [intentSender]; call back with RESULT_OK to confirm.
     * [alreadyDeleted] lists non-media / file items that were already moved to the
     * app trash BEFORE the dialog — the caller prunes those immediately, because they
     * are already gone from their original location even if the user cancels the dialog.
     */
    data class RequiresConfirmation(
        val intentSender:   IntentSender,
        val alreadyDeleted: List<Uri> = emptyList(),
    ) : DeleteRequest()

    /**
     * Files were removed directly with no dialog (everything is recoverable — media in the
     * system Trash, other files in the app trash). [deletedUris] lists exactly what was
     * removed; [failedCount] is how many items could not be removed (surfaced to the user
     * instead of failing silently).
     */
    data class DirectSuccess(val deletedUris: List<Uri>, val failedCount: Int = 0) : DeleteRequest()

    data class Error(val message: String) : DeleteRequest()
}

/**
 * Deletion strategies, all of them recoverable:
 *
 * **Media (images / videos / audio)** — sent to Android's **system Trash** (recoverable
 * ~30 days) via [MediaStore.createTrashRequest] on API 30+. That API only accepts URIs
 * from the Images/Video/Audio collections, so a `MediaStore.Files` URI is remapped to its
 * typed collection by row id first.
 *
 * **Every other file** (PDFs, APKs, archives, chat media, `file://` items…) — MediaStore
 * has no Trash for these, so they are moved into SpaceClean's own [AppTrash] (also ~30-day
 * recoverable) instead of being permanently deleted.
 *
 * **Directories** (empty leftover / orphan folders) — nothing to recover, removed directly.
 */
class FileDeleter(context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val appTrash = AppTrash(context)

    suspend fun initiateDelete(files: List<ScannedFile>): DeleteRequest = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext DeleteRequest.DirectSuccess(emptyList())

        val deleted = mutableListOf<Uri>()
        var failed  = 0

        val (media, others) = files.partition {
            it.uri.scheme == "content" &&
                (it.name.isImageName() || it.name.isVideoName() || it.name.isAudioName())
        }

        // 1. Non-media (any scheme): directories delete directly, files go to the app trash.
        for (f in others) {
            val handled = when {
                isDirectory(f)          -> deleteDirectory(f)
                appTrash.trash(f)       -> { removeMediaStoreRow(f); true }
                else                    -> false
            }
            if (handled) deleted += f.uri else failed++
        }

        // 2. Media content:// — recoverable system Trash on API 30+, else app trash.
        if (media.isEmpty()) {
            return@withContext DeleteRequest.DirectSuccess(deleted, failed)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val trashUris = media.map { typedMediaUri(it) }
                val pi = MediaStore.createTrashRequest(contentResolver, trashUris, true)
                DeleteRequest.RequiresConfirmation(pi.intentSender, alreadyDeleted = deleted)
            }.getOrElse {
                // Trash request refused — fall back to the app trash so nothing is dead.
                for (f in media) {
                    if (appTrash.trash(f)) { removeMediaStoreRow(f); deleted += f.uri } else failed++
                }
                DeleteRequest.DirectSuccess(deleted, failed)
            }
        } else {
            for (f in media) {
                if (appTrash.trash(f)) { removeMediaStoreRow(f); deleted += f.uri } else failed++
            }
            DeleteRequest.DirectSuccess(deleted, failed)
        }
    }

    /** Restores system-Trash items (untrash). content:// only, API 30+. */
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

    /** Permanently deletes system-Trash items. content:// only, API 30+. */
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

    private fun isDirectory(f: ScannedFile): Boolean {
        if (f.uri.scheme != "file") return false
        return runCatching { File(requireNotNull(f.uri.path)).isDirectory }.getOrDefault(false)
    }

    private fun deleteDirectory(f: ScannedFile): Boolean = runCatching {
        val dir = File(requireNotNull(f.uri.path))
        !dir.exists() || dir.deleteRecursively()
    }.getOrDefault(false)

    /**
     * Rebuilds a URI in the typed media collection (Images/Video/Audio) that
     * `createTrashRequest` requires. MediaStore row ids are shared across the Files
     * collection and the typed collections, so remapping by id is lossless.
     */
    private fun typedMediaUri(f: ScannedFile): Uri = typedMediaUri(f.uri, f.name)

    companion object {
        /**
         * Rebuilds a generic `MediaStore.Files` uri as a typed Images/Video/Audio uri, which
         * `createTrashRequest` / `createDeleteRequest` require. Shared by the delete path and
         * the Recently-Deleted restore/permanent-delete path (trashed items come back as Files
         * uris). Row ids are shared across collections, so the remap is lossless.
         */
        fun typedMediaUri(uri: Uri, name: String): Uri {
            if (uri.scheme != "content") return uri
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return uri
            val collection = when {
                name.isImageName() -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                name.isVideoName() -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                name.isAudioName() -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else               -> return uri
            }
            return ContentUris.withAppendedId(collection, id)
        }
    }

    /** Drops the stale MediaStore row after a file was moved to the app trash. */
    private fun removeMediaStoreRow(f: ScannedFile) {
        if (f.uri.scheme == "content") runCatching { contentResolver.delete(f.uri, null, null) }
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
