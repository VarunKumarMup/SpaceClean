package io.github.varunkumar.spaceclean.scanner

import android.net.Uri

/**
 * Represents a single file found during a scan.
 *
 * All fields except [uri], [name], [sizeBytes], and [dateModifiedMs] carry optional
 * metadata that certain scan types populate:
 *  - [durationMs]  — audio/video play-time in milliseconds (0 = not applicable)
 *  - [widthPx]     — image/video frame width (0 = not applicable)
 *  - [heightPx]    — image/video frame height (0 = not applicable)
 */
data class ScannedFile(
    val uri:           Uri,
    val name:          String,
    val path:          String,
    val sizeBytes:     Long,
    val dateModifiedMs: Long,
    val durationMs:    Long = 0L,
    val widthPx:       Int  = 0,
    val heightPx:      Int  = 0,
)
