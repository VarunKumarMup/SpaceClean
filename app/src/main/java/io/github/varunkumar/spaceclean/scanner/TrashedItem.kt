package io.github.varunkumar.spaceclean.scanner

import android.net.Uri

/**
 * A recoverable, deleted file shown on the Recently Deleted screen.
 *
 * Two sources are merged into one list:
 *  - [appTrashId] == null → an item in Android's own system Trash (photos/videos/audio).
 *    [uri] is its MediaStore content:// uri.
 *  - [appTrashId] != null → an item in SpaceClean's app-managed trash (documents, APKs,
 *    chat media, etc.). [uri] is a file:// uri to the stored copy, used only for previews.
 */
data class TrashedItem(
    val uri:        Uri,
    val name:       String,
    val sizeBytes:  Long,
    val expiresMs:  Long,          // epoch millis when it will auto-purge (0 = unknown)
    val appTrashId: String? = null,
)
