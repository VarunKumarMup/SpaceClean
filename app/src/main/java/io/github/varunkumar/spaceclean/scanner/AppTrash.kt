package io.github.varunkumar.spaceclean.scanner

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A recoverable trash for files Android's own MediaStore Trash can't hold — documents,
 * APKs, archives, chat-app media, and any other non-media file the user deletes.
 *
 * How it works, fully on-device:
 *  - The file is **moved** (fast same-volume rename, with copy-then-delete fallback) into
 *    a private app folder: `Android/data/<pkg>/files/.spaceclean_trash/`.
 *  - Its original absolute path + deletion time are recorded in a JSON index in the app's
 *    internal storage, so it can be put back exactly where it came from.
 *  - Entries older than [RETENTION_MS] (~30 days) are purged automatically, matching the
 *    Android media Trash retention so the whole app tells one consistent story.
 *
 * Nothing here needs the network; deleting the app also discards its trash.
 */
class AppTrash(private val context: Context) {

    data class Entry(
        val id:           String,
        val originalPath: String,   // absolute path to restore back to
        val name:         String,
        val sizeBytes:    Long,
        val trashedAtMs:  Long,
        val storedName:   String,   // file name inside the trash dir
    ) {
        val expiresMs: Long get() = trashedAtMs + RETENTION_MS
    }

    private val trashDir: File
        get() = File(context.getExternalFilesDir(null), TRASH_DIR).apply { if (!exists()) mkdirs() }

    private val indexFile: File
        get() = File(context.filesDir, INDEX_FILE)

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Moves [file] into the trash. Returns true on success. The original is only removed
     * once the copy is safely in place, so a failure never loses data.
     */
    @Synchronized
    fun trash(file: ScannedFile): Boolean {
        val src = resolveAbsolutePath(file) ?: return false
        if (!src.isFile) return false

        val storedName = "${System.currentTimeMillis()}_${sanitize(file.name)}"
        val dest       = File(trashDir, storedName)

        val moved = runCatching {
            if (src.renameTo(dest)) true
            else {
                src.copyTo(dest, overwrite = true)
                if (dest.length() == src.length()) { src.delete(); true } else { dest.delete(); false }
            }
        }.getOrDefault(false)
        if (!moved) return false

        val entries = readIndex()
        entries += Entry(
            id           = storedName,
            originalPath = src.absolutePath,
            name         = file.name,
            sizeBytes    = if (dest.length() > 0) dest.length() else file.sizeBytes,
            trashedAtMs  = System.currentTimeMillis(),
            storedName   = storedName,
        )
        writeIndex(entries)
        return true
    }

    /** Live entries (auto-purges anything past retention first). */
    @Synchronized
    fun list(): List<Entry> {
        purgeExpiredInternal()
        return readIndex().sortedByDescending { it.trashedAtMs }
    }

    /** Restores the given ids back to their original locations. Returns how many succeeded. */
    @Synchronized
    fun restore(ids: Set<String>): Int {
        if (ids.isEmpty()) return 0
        val entries = readIndex()
        var restored = 0
        val remaining = entries.filter { entry ->
            if (entry.id !in ids) return@filter true
            val stored = File(trashDir, entry.storedName)
            val dest   = uniqueDest(File(entry.originalPath))
            val ok = runCatching {
                dest.parentFile?.mkdirs()
                if (stored.renameTo(dest)) true
                else { stored.copyTo(dest, overwrite = true); stored.delete(); true }
            }.getOrDefault(false)
            if (ok) {
                restored++
                // Let MediaStore re-index the restored file so it reappears in scans/gallery.
                runCatching {
                    android.media.MediaScannerConnection.scanFile(
                        context, arrayOf(dest.absolutePath), null, null,
                    )
                }
                false          // drop from index
            } else true        // keep — restore failed
        }
        writeIndex(remaining)
        return restored
    }

    /** Permanently deletes the given ids. Returns how many were removed. */
    @Synchronized
    fun purge(ids: Set<String>): Int {
        if (ids.isEmpty()) return 0
        val entries = readIndex()
        var purged = 0
        val remaining = entries.filter { entry ->
            if (entry.id !in ids) return@filter true
            runCatching { File(trashDir, entry.storedName).delete() }
            purged++
            false
        }
        writeIndex(remaining)
        return purged
    }

    @Synchronized
    fun purgeExpired() = purgeExpiredInternal()

    /** A file:// uri pointing at the stored copy — used only to render a thumbnail. */
    fun storedUri(entry: Entry): Uri = Uri.fromFile(File(trashDir, entry.storedName))

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun purgeExpiredInternal() {
        val now       = System.currentTimeMillis()
        val entries   = readIndex()
        val expired    = entries.filter { it.expiresMs <= now }
        if (expired.isEmpty()) return
        expired.forEach { runCatching { File(trashDir, it.storedName).delete() } }
        writeIndex(entries - expired.toSet())
    }

    /** Rebuilds the absolute filesystem path of a scanned file (valid under All-Files-Access). */
    private fun resolveAbsolutePath(file: ScannedFile): File? {
        if (file.uri.scheme == "file") return file.uri.path?.let { File(it) }
        val rel = file.path.trim('/')
        val root = Environment.getExternalStorageDirectory()
        return if (rel.isEmpty()) File(root, file.name) else File(root, "$rel/${file.name}")
    }

    private fun uniqueDest(target: File): File {
        if (!target.exists()) return target
        val stem = target.nameWithoutExtension
        val ext  = target.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (true) {
            val candidate = File(target.parentFile, "$stem ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun readIndex(): MutableList<Entry> {
        if (!indexFile.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id           = o.getString("id"),
                    originalPath = o.getString("originalPath"),
                    name         = o.getString("name"),
                    sizeBytes    = o.getLong("sizeBytes"),
                    trashedAtMs  = o.getLong("trashedAtMs"),
                    storedName   = o.getString("storedName"),
                )
            }
        }.getOrDefault(mutableListOf())
    }

    private fun writeIndex(entries: List<Entry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("originalPath", e.originalPath)
                    put("name", e.name)
                    put("sizeBytes", e.sizeBytes)
                    put("trashedAtMs", e.trashedAtMs)
                    put("storedName", e.storedName)
                })
            }
            indexFile.writeText(arr.toString())
        }
    }

    companion object {
        private const val TRASH_DIR   = ".spaceclean_trash"
        private const val INDEX_FILE  = "apptrash_index.json"
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000   // ~30 days
    }
}
