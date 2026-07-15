package io.github.varunkumar.spaceclean.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/**
 * Finds visually-similar photos by comparing actual image content with a perceptual
 * difference-hash (dHash), not filenames.
 *
 * Pipeline:
 *  1. Bucket every photo by exact pixel dimensions (cheap MediaStore metadata) — near-
 *     identical shots almost always share the same sensor dimensions, and this bounds the
 *     number of comparisons.
 *  2. Compute a 64-bit dHash for each photo from a tiny thumbnail (system-cached, fast).
 *  3. Cluster photos whose hashes are within [SIMILARITY_THRESHOLD] Hamming distance
 *     (union-find). Only clusters of ≥2 are returned.
 *
 * This catches bursts, near-duplicates, and lightly-edited copies regardless of filename,
 * while different-looking photos (large Hamming distance) are never grouped.
 */
class SimilarPhotoScanner(private val contentResolver: ContentResolver) {

    fun findSimilarGroups(): List<List<ScannedFile>> {
        val entries = queryAllPhotos()
        if (entries.size < 2) return emptyList()

        val groups = mutableListOf<List<ScannedFile>>()

        val byDimension = entries
            .filter { it.widthPx > 0 && it.heightPx > 0 }
            .groupBy { it.widthPx to it.heightPx }

        for ((_, bucket) in byDimension) {
            if (bucket.size < 2) continue
            groups.addAll(clusterByContent(bucket))
        }

        // Biggest groups first (most reclaimable).
        return groups.sortedByDescending { it.size }
    }

    // ── Perceptual clustering ───────────────────────────────────────────────────

    private fun clusterByContent(bucket: List<ScannedFile>): List<List<ScannedFile>> {
        val hashed = bucket.mapNotNull { f -> dHashOf(f.uri)?.let { f to it } }
        if (hashed.size < 2) return emptyList()

        val n = hashed.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (hamming(hashed[i].second, hashed[j].second) <= SIMILARITY_THRESHOLD) union(i, j)
            }
        }

        val clusters = LinkedHashMap<Int, MutableList<ScannedFile>>()
        for (i in 0 until n) clusters.getOrPut(find(i)) { mutableListOf() }.add(hashed[i].first)
        return clusters.values.filter { it.size >= 2 }
    }

    private fun dHashOf(uri: Uri): Long? = runCatching {
        val tiny = loadTinyBitmap(uri) ?: return null
        val small = if (tiny.width == 9 && tiny.height == 8) tiny
                    else Bitmap.createScaledBitmap(tiny, 9, 8, true).also { if (it !== tiny) tiny.recycle() }

        val px = IntArray(9 * 8)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        small.recycle()

        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left  = luma(px[y * 9 + x])
                val right = luma(px[y * 9 + x + 1])
                hash = (hash shl 1) or (if (left > right) 1L else 0L)
            }
        }
        hash
    }.getOrNull()

    /** Loads a small bitmap fast — the OS thumbnail cache on Q+, else a downsampled decode. */
    private fun loadTinyBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { return contentResolver.loadThumbnail(uri, Size(32, 32), null) }
        }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / (sample * 2) >= 32) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    private fun luma(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 30 + g * 59 + b * 11) / 100
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    // ── Photo enumeration ───────────────────────────────────────────────────────

    private fun queryAllPhotos(): List<ScannedFile> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )

        val results = mutableListOf<ScannedFile>()
        contentResolver.query(
            collection, projection,
            "${MediaStore.Images.Media.SIZE} > ?", arrayOf(MIN_SIZE_BYTES.toString()),
            "${MediaStore.Images.Media.DATE_TAKEN} ASC",
        )?.use { cursor ->
            val idIdx     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathIdx   = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val sizeIdx   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateIdx   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val widthIdx  = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                results += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                    name           = name,
                    path           = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    sizeBytes      = cursor.getLong(sizeIdx),
                    dateModifiedMs = cursor.getLong(dateIdx),
                    widthPx        = cursor.getInt(widthIdx),
                    heightPx       = cursor.getInt(heightIdx),
                )
            }
        }
        return results
    }

    companion object {
        private const val MIN_SIZE_BYTES       = 40L * 1024   // skip icons/tiny images
        // Max dHash Hamming distance (of 64) to count as "similar". Higher = catches more.
        // Bursts/near-dupes are typically ≤ 10; clearly different scenes are ≥ 18.
        private const val SIMILARITY_THRESHOLD = 13
    }
}
