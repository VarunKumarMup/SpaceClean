package io.github.varunkumar.spaceclean.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

class AppStorageScanner(private val context: Context) {

    fun findAppsByStorage(): List<AppInfo> {
        val pm       = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return packages
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // user-installed only
            .mapNotNull { info ->
                runCatching {
                    val apkSize = computeApkSize(info)
                    AppInfo(
                        packageName  = info.packageName,
                        appName      = pm.getApplicationLabel(info).toString(),
                        apkSizeBytes = apkSize,
                    )
                }.getOrNull()
            }
            .filter { it.apkSizeBytes > 0L }
            .sortedByDescending { it.apkSizeBytes }
    }

    private fun computeApkSize(info: ApplicationInfo): Long {
        var size = File(info.sourceDir).length()
        info.splitSourceDirs?.forEach { split -> size += File(split).length() }
        return size
    }
}
