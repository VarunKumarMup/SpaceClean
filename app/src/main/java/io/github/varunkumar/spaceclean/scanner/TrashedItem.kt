package io.github.varunkumar.spaceclean.scanner

import android.net.Uri

/** A file currently in Android's system Trash (recoverable until [expiresMs]). */
data class TrashedItem(
    val uri:       Uri,
    val name:      String,
    val sizeBytes: Long,
    val expiresMs: Long,   // epoch millis when Android will auto-purge it (0 = unknown)
)
