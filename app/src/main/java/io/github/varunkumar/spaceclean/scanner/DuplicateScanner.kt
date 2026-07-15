package io.github.varunkumar.spaceclean.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.security.MessageDigest

/**
 * Finds EXACT duplicate files (identical content) across the whole device — including
 * the same file existing in both phone storage and a chat app's media folder
 * (WhatsApp / Telegram / Messenger / Viber / Signal).
 *
 * Strategy (offline, no network):
 *  1. Collect candidate files from MediaStore (every type) + a walk of chat-media roots.
 *  2. De-duplicate the candidate list by absolute path so one physical file is never
 *     listed twice (a WhatsApp file indexed by MediaStore must not look like a copy
 *     of itself).
 *  3. Bucket by exact byte-size (cheap), then within each size-bucket compare a content
 *     hash (MD5 over the full stream). Files sharing a hash are genuine duplicates.
 *
 * Note: apps that recompress media on send (e.g. WhatsApp-sent photos) produce a
 * different byte stream, so those are intentionally NOT matched — only true copies are.
 */
class DuplicateScanner(private val context: Context) {

    private val resolver = context.contentResolver

    fun findDuplicateGroups(minBytes: Long = 24L * 1024): List<List<ScannedFile>> {
        // key = absolute path → one entry per physical file (prefer MediaStore content uri)
        val candidates = LinkedHashMap<String, ScannedFile>()
        collectMediaStore(minBytes, candidates)
        collectChatMedia(minBytes, candidates)

        val groups = mutableListOf<List<ScannedFile>>()

        // Only files that share an exact size are worth hashing.
        candidates.values
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }
            .forEach { (_, sameSize) ->
                val byHash = HashMap<String, MutableList<ScannedFile>>()
                for (f in sameSize) {
                    val h = hashOf(f.uri) ?: continue
                    byHash.getOrPut(h) { mutableListOf() }.add(f)
                }
                byHash.values.forEach { if (it.size > 1) groups += it }
            }

        // Biggest reclaimable groups first: size * (copies - 1)
        return groups.sortedByDescending { it.first().sizeBytes * (it.size - 1) }
    }

    // ── Collectors ──────────────────────────────────────────────────────────────

    private fun collectMediaStore(minBytes: Long, out: MutableMap<String, ScannedFile>) {
        val collection = MediaStore.Files.getContentUri("external")
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
        )
        runCatching {
            resolver.query(
                collection, projection,
                "${MediaStore.Files.FileColumns.SIZE} >= ?",
                arrayOf(minBytes.toString()), null,
            )?.use { c ->
                val idIdx   = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                @Suppress("DEPRECATION")
                val dataIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext()) {
                    val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                    if (path.isNullOrBlank()) continue           // need a real path to de-dup
                    val name = c.getString(nameIdx) ?: File(path).name
                    val size = c.getLong(sizeIdx)
                    if (size < minBytes) continue
                    out[path] = ScannedFile(
                        uri            = ContentUris.withAppendedId(collection, c.getLong(idIdx)),
                        name           = name,
                        path           = path,
                        sizeBytes      = size,
                        dateModifiedMs = c.getLong(dateIdx) * 1_000L,
                    )
                }
            }
        }
    }

    private fun collectChatMedia(minBytes: Long, out: MutableMap<String, ScannedFile>) {
        val root = Environment.getExternalStorageDirectory()
        val roots = listOf(
            "WhatsApp/Media", "Android/media/com.whatsapp/WhatsApp/Media",
            "WhatsApp Business/Media", "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
            "Telegram", "Android/media/org.telegram.messenger/Telegram",
            "Android/media/com.facebook.orca",
            "viber/media", "Android/media/com.viber.voip",
            "Signal", "Android/media/org.thoughtcrime.securesms",
        ).map { File(root, it) }

        for (dir in roots) {
            if (!dir.exists() || !dir.canRead()) continue
            walk(dir, minBytes, out, depth = 0, maxDepth = 6)
        }
    }

    private fun walk(dir: File, minBytes: Long, out: MutableMap<String, ScannedFile>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            when {
                child.isDirectory && !child.name.startsWith(".") ->
                    walk(child, minBytes, out, depth + 1, maxDepth)
                child.isFile && child.length() >= minBytes -> {
                    val path = child.absolutePath
                    if (out.containsKey(path)) continue          // already indexed by MediaStore
                    out[path] = ScannedFile(
                        uri            = Uri.fromFile(child),
                        name           = child.name,
                        path           = path,
                        sizeBytes      = child.length(),
                        dateModifiedMs = child.lastModified(),
                    )
                }
            }
        }
    }

    // ── Content hash ──────────────────────────────────────────────────────────────

    private fun hashOf(uri: Uri): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        resolver.openInputStream(uri)?.use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        } ?: return null
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    companion object {
        /** True if a path lives inside a chat app's media folder. */
        fun isChatPath(path: String): Boolean {
            val p = path.lowercase()
            return listOf("whatsapp", "telegram", "com.facebook.orca", "viber",
                "signal", "securesms", "thunderdog").any { it in p }
        }

        /**
         * The copy to KEEP in a duplicate group: prefer a non-chat (phone storage) copy,
         * and among candidates the oldest (most likely the original). The rest are the
         * redundant copies safe to delete.
         */
        fun pickKeeper(group: List<ScannedFile>): ScannedFile {
            val pool = group.filterNot { isChatPath(it.path) }.ifEmpty { group }
            return pool.minByOrNull { if (it.dateModifiedMs > 0) it.dateModifiedMs else Long.MAX_VALUE }
                ?: group.first()
        }
    }
}
