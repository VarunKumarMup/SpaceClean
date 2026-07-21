package io.github.varunkumar.spaceclean.scanner

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the AI photo pass found, grouped into reviewable buckets. */
data class AiScanResult(
    val similarGroups: List<List<ScannedFile>> = emptyList(), // neural near-duplicate clusters
    val blurry:        List<ScannedFile>       = emptyList(),
    val screenshots:   List<ScannedFile>       = emptyList(),
    val tags:          Map<Uri, String>        = emptyMap(),   // per-photo "AI sees: …" label
    val analyzedCount: Int                     = 0,
    val available:     Boolean                 = true,          // false if the model couldn't load
)

/**
 * The AI photo pass. For each recent photo it runs the on-device [AiPhotoClassifier] to get a
 * content label + feature embedding, plus a fast focus measure, then sorts them into:
 *  - **Similar groups** — clustered by embedding cosine similarity (catches "same scene,
 *    different shot" that a perceptual hash misses).
 *  - **Blurry / low-quality** — low variance-of-Laplacian (out-of-focus, smeared).
 *  - **Screenshots & memes** — the Screenshots folder plus screen/comic/text-like labels.
 *
 * Fully offline. Bounded to [MAX_PHOTOS] most-recent images so a big gallery stays responsive.
 */
class AiPhotoScanner(private val context: Context) {

    suspend fun scan(progress: (Int, Int) -> Unit = { _, _ -> }): AiScanResult =
        withContext(Dispatchers.IO) {
            val classifier = AiPhotoClassifier(context)
            if (!classifier.isAvailable) {
                classifier.close()
                return@withContext AiScanResult(available = false)
            }

            try {
                val photos = queryRecentPhotos()
                val embeddings = HashMap<Uri, FloatArray>()
                val hashes      = HashMap<Uri, Long>()
                val blurry      = mutableListOf<ScannedFile>()
                val screenshots = mutableListOf<ScannedFile>()
                val tags        = HashMap<Uri, String>()
                var analyzed = 0

                for ((index, photo) in photos.withIndex()) {
                    progress(index, photos.size)
                    val bmp = loadBitmap(photo.uri) ?: continue
                    val result = classifier.classify(bmp)
                    val focus  = focusScore(bmp)
                    hashes[photo.uri] = dHash(bmp)
                    bmp.recycle()
                    analyzed++

                    if (result != null) {
                        embeddings[photo.uri] = result.embedding
                        result.labels.firstOrNull()?.let { (label, _) -> tags[photo.uri] = label }
                        if (isScreenshot(photo, result.labels)) screenshots += photo
                    }
                    if (focus in 0.0..BLUR_THRESHOLD) blurry += photo
                }

                val groups = clusterBySimilarity(photos.filter { hashes.containsKey(it.uri) }, embeddings, hashes)
                progress(photos.size, photos.size)

                AiScanResult(
                    similarGroups = groups,
                    blurry        = blurry.sortedByDescending { it.sizeBytes },
                    screenshots   = screenshots.sortedByDescending { it.sizeBytes },
                    tags          = tags,
                    analyzedCount = analyzed,
                    available     = true,
                )
            } finally {
                classifier.close()
            }
        }

    // ── Similarity clustering (union-find over cosine similarity) ───────────────

    /**
     * Groups only photos that are **visually near-identical** — the perceptual hash (dHash)
     * Hamming distance is the safety gate, so two *different* subjects (e.g. two people who
     * both happen to wear jeans) can never be grouped, no matter what the content model thinks.
     * The neural embedding is an additional required condition, never a relaxation.
     */
    private fun clusterBySimilarity(
        photos: List<ScannedFile>,
        embeddings: Map<Uri, FloatArray>,
        hashes: Map<Uri, Long>,
    ): List<List<ScannedFile>> {
        val n = photos.size
        if (n < 2) return emptyList()
        val parent = IntArray(n) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; parent[x] = r; return r }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        for (i in 0 until n) {
            val hi = hashes[photos[i].uri] ?: continue
            for (j in i + 1 until n) {
                val hj = hashes[photos[j].uri] ?: continue
                if (hamming(hi, hj) > HASH_MAX_DISTANCE) continue          // must look near-identical
                val ei = embeddings[photos[i].uri]
                val ej = embeddings[photos[j].uri]
                val contentAgrees = ei == null || ej == null ||
                    AiPhotoClassifier.cosine(ei, ej) >= SIMILARITY_THRESHOLD
                if (contentAgrees) union(i, j)
            }
        }
        return (0 until n).groupBy { find(it) }.values
            .filter { it.size >= 2 }
            .map { idxs -> idxs.map { photos[it] } }
    }

    // ── Perceptual hash (dHash) ─────────────────────────────────────────────────

    private fun dHash(bitmap: Bitmap): Long {
        val w = 9; val h = 8
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        var hash = 0L; var bit = 0
        for (y in 0 until h) for (x in 0 until 8) {
            val left  = luma(px[y * w + x])
            val right = luma(px[y * w + x + 1])
            if (left > right) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    private fun luma(p: Int) =
        0.299 * (p shr 16 and 0xFF) + 0.587 * (p shr 8 and 0xFF) + 0.114 * (p and 0xFF)

    private fun hamming(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)

    // ── Heuristics ──────────────────────────────────────────────────────────────

    private fun isScreenshot(photo: ScannedFile, labels: List<Pair<String, Float>>): Boolean {
        val path = (photo.path + " " + photo.name).lowercase()
        if ("screenshot" in path || "screen_" in path) return true
        return labels.any { (label, conf) -> conf >= 0.35f && label.lowercase() in SCREEN_LABELS }
    }

    /** Variance of a 3×3 Laplacian over a small grayscale copy. Low = blurry. */
    private fun focusScore(bitmap: Bitmap): Double {
        val w = 100; val h = 100
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val gray = DoubleArray(w * h)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        for (i in px.indices) {
            val p = px[i]
            gray[i] = 0.299 * (p shr 16 and 0xFF) + 0.587 * (p shr 8 and 0xFF) + 0.114 * (p and 0xFF)
        }
        var sum = 0.0; var sumSq = 0.0; var count = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val idx = y * w + x
            val lap = -4 * gray[idx] + gray[idx - 1] + gray[idx + 1] + gray[idx - w] + gray[idx + w]
            sum += lap; sumSq += lap * lap; count++
        }
        if (count == 0) return Double.MAX_VALUE
        val mean = sum / count
        return sumSq / count - mean * mean   // variance
    }

    // ── Photo source + bitmap loading ───────────────────────────────────────────

    private fun queryRecentPhotos(): List<ScannedFile> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val out = mutableListOf<ScannedFile>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Images.Media.SIZE} >= ?", arrayOf(MIN_SIZE_BYTES.toString()),
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nmIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val ptIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val szIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dtIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val wIdx  = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val hIdx  = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext() && out.size < MAX_PHOTOS) {
                val name = c.getString(nmIdx) ?: continue
                out += ScannedFile(
                    uri            = ContentUris.withAppendedId(collection, c.getLong(idIdx)),
                    name           = name,
                    path           = if (ptIdx >= 0) c.getString(ptIdx) ?: "" else "",
                    sizeBytes      = c.getLong(szIdx),
                    dateModifiedMs = c.getLong(dtIdx) * 1000L,
                    widthPx        = if (wIdx >= 0) c.getInt(wIdx) else 0,
                    heightPx       = if (hIdx >= 0) c.getInt(hIdx) else 0,
                )
            }
        }
        return out
    }

    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(224, 224), null)
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } ?: return@runCatching null
        // loadThumbnail can hand back a HARDWARE bitmap, whose pixels can't be read with
        // getPixels(); copy it to a software config so classification/blur can inspect it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE)
            bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
        else bmp
    }.getOrNull()

    companion object {
        private const val MAX_PHOTOS          = 500
        private const val MIN_SIZE_BYTES      = 40L * 1024
        // Photos must be near-identical (≤10 of 64 hash bits differ) to ever group — the
        // hard safety gate that stops distinct photos of people being flagged as duplicates.
        private const val HASH_MAX_DISTANCE    = 10
        private const val SIMILARITY_THRESHOLD = 0.80f     // secondary content check only
        // Variance below this ≈ SEVERELY out of focus. Kept low on purpose so sharp portraits
        // with a soft background are not misflagged; blurry items are only ever *suggested*.
        private const val BLUR_THRESHOLD       = 18.0
        private val SCREEN_LABELS = setOf(
            "web site", "menu", "monitor", "comic book", "envelope", "packet", "book jacket",
        )
    }
}
