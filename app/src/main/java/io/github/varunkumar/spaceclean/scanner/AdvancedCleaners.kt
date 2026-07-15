package io.github.varunkumar.spaceclean.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Offline-only cleaners for chat media residue and orphaned app folders.
 * No INTERNET permission used or required.
 */
class AdvancedCleaners(private val context: Context) {

    // ── Chat Media Sweeper ────────────────────────────────────────────────────

    /**
     * Walks the standard directories where WhatsApp, Telegram, Messenger, Viber,
     * and Signal cache received media, voice notes, and sent files.
     * Covers both legacy root paths and modern Android/media scoped-storage paths.
     */
    fun findChatMedia(): List<ScannedFile> {
        val root    = Environment.getExternalStorageDirectory()
        val results = mutableListOf<ScannedFile>()

        val chatRoots = buildList {
            // WhatsApp
            add(File(root, "WhatsApp/Media"))
            add(File(root, "Android/media/com.whatsapp/WhatsApp/Media"))
            // WhatsApp Business
            add(File(root, "WhatsApp Business/Media"))
            add(File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"))
            // Telegram
            add(File(root, "Telegram"))
            add(File(root, "Android/media/org.telegram.messenger/Telegram"))
            add(File(root, "Android/media/org.thunderdog.challegram"))
            // Facebook Messenger
            add(File(root, "Android/media/com.facebook.orca"))
            // Viber
            add(File(root, "viber/media"))
            add(File(root, "Android/media/com.viber.voip"))
            // Signal
            add(File(root, "Signal"))
            add(File(root, "Android/media/org.thoughtcrime.securesms"))
        }

        for (chatRoot in chatRoots) {
            if (!chatRoot.exists() || !chatRoot.canRead()) continue
            walkForMediaFiles(chatRoot, results, depth = 0, maxDepth = 6)
        }

        return results
            .distinctBy { it.uri }
            .sortedByDescending { it.sizeBytes }
    }

    private fun walkForMediaFiles(
        dir: File,
        sink: MutableList<ScannedFile>,
        depth: Int,
        maxDepth: Int,
    ) {
        if (depth > maxDepth) return
        val children = try { dir.listFiles() } catch (_: SecurityException) { null } ?: return
        for (child in children) {
            when {
                child.isFile && child.length() > 0L ->
                    sink += ScannedFile(
                        uri            = Uri.fromFile(child),
                        name           = child.name,
                        path           = child.parent ?: "",
                        sizeBytes      = child.length(),
                        dateModifiedMs = child.lastModified(),
                    )
                child.isDirectory && !child.name.startsWith(".") ->
                    walkForMediaFiles(child, sink, depth + 1, maxDepth)
            }
        }
    }

    // ── Corpse / Orphan Folder Finder ─────────────────────────────────────────

    /**
     * Scans Android/data, Android/media, and Android/obb for subdirectories whose
     * package name does NOT match any installed app — these are "corpse" folders
     * left behind by uninstalled applications.
     *
     * Also scans the external storage root for completely empty top-level directories
     * that are not standard system folders.
     *
     * On API 30+ the content of Android/data subdirectories is unreadable without
     * MANAGE_EXTERNAL_STORAGE; we still surface the folder if it exists and the
     * package is not installed, but report size 0 rather than refusing to list it.
     */
    fun findCorpseFolders(): List<ScannedFile> {
        val installed = installedPackageNames()
        val root      = Environment.getExternalStorageDirectory()
        val results   = mutableListOf<ScannedFile>()

        // ── Scan Android/data, Android/media, Android/obb ──────────────────────
        listOf("Android/data", "Android/media", "Android/obb").forEach { rel ->
            val scanRoot = File(root, rel)
            if (!scanRoot.exists()) return@forEach
            val children = try { scanRoot.listFiles() } catch (_: SecurityException) { null } ?: return@forEach
            for (child in children) {
                if (!child.isDirectory) continue
                val pkg = child.name
                if (pkg !in installed && looksLikePackageName(pkg)) {
                    results += ScannedFile(
                        uri            = Uri.fromFile(child),
                        name           = pkg,
                        path           = child.parent ?: "",
                        sizeBytes      = safeDirSizeBytes(child),
                        dateModifiedMs = child.lastModified(),
                    )
                }
            }
        }

        // ── Scan top-level external storage for empty orphan dirs ──────────────
        val topLevel = try { root.listFiles() } catch (_: SecurityException) { null } ?: emptyArray()
        for (dir in topLevel) {
            if (!dir.isDirectory || dir.name.startsWith(".")) continue
            if (dir.name.lowercase() in SYSTEM_DIRS) continue
            val contents = try { dir.listFiles() } catch (_: SecurityException) { null }
            if (contents != null && contents.isEmpty()) {
                results += ScannedFile(
                    uri            = Uri.fromFile(dir),
                    name           = dir.name,
                    path           = dir.parent ?: "",
                    sizeBytes      = 0L,
                    dateModifiedMs = dir.lastModified(),
                )
            }
        }

        return results
            .distinctBy { it.uri }
            .sortedByDescending { it.sizeBytes }
    }

    private fun installedPackageNames(): Set<String> =
        try {
            context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .mapTo(HashSet()) { it.packageName }
        } catch (_: Exception) { emptySet() }

    private fun looksLikePackageName(name: String): Boolean =
        name.contains('.') && name.length > 4 && name.all { it.isLetterOrDigit() || it == '.' || it == '_' }

    private fun safeDirSizeBytes(dir: File): Long {
        var total = 0L
        try {
            dir.walkTopDown().maxDepth(5).filter { it.isFile }.forEach { total += it.length() }
        } catch (_: SecurityException) {}
        return total
    }

    companion object {
        private val SYSTEM_DIRS = setOf(
            "android", "dcim", "pictures", "movies", "music",
            "downloads", "documents", "ringtones", "podcasts",
            "notifications", "alarms", "audiobooks", "bluetooth",
        )
    }
}
