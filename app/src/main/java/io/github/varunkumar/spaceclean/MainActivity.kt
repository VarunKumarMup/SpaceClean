package io.github.varunkumar.spaceclean

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import io.github.varunkumar.spaceclean.ui.SpaceCleanNavGraph
import io.github.varunkumar.spaceclean.ui.theme.SpaceCleanTheme
import io.github.varunkumar.spaceclean.ui.theme.ThemeState
import io.github.varunkumar.spaceclean.ui.theme.loadSavedTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()

    // Prevents onResume() from firing the All-Files-Access Settings intent more
    // than once per activity lifecycle. Without this guard, every return from the
    // Settings page (without granting) would immediately re-open Settings — a loop.
    // The flag resets when the Activity is destroyed (process kill / swipe away)
    // so a fresh cold start always sends the user to Settings once.
    private var settingsIntentFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSavedTheme(this)   // apply the user's chosen theme before first frame
        enableEdgeToEdge()
        setContent {
            SpaceCleanTheme {
                // Match the system bar icon colour to the active theme (dark icons on Light).
                val isLight = ThemeState.palette.isLight
                LaunchedEffect(isLight) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = isLight
                        isAppearanceLightNavigationBars = isLight
                    }
                }
                val navController = rememberNavController()
                SpaceCleanNavGraph(navController = navController, viewModel = viewModel)
            }
        }
    }

    // Called on: first app launch, return from permission dialog, return from Settings.
    // This is the single authoritative place where storage access is verified and the
    // ViewModel is unlocked — no LaunchedEffect or Compose-side check needed.
    override fun onResume() {
        super.onResume()
        when {
            // ── Access confirmed: unlock the app and clear the redirect flag ──
            hasStorageAccess() -> {
                settingsIntentFired = false
                viewModel.onPermissionGranted()
            }

            // ── Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE ────────────────
            // This permission cannot be requested via a runtime dialog. The only
            // way to grant it is the "All files access" toggle in Settings.
            // Open that Settings page once per foreground cycle; if the user
            // returns without granting, leave the app in the locked state rather
            // than looping back to Settings indefinitely.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !settingsIntentFired -> {
                settingsIntentFired = true
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }

            // ── API ≤ 29: READ_EXTERNAL_STORAGE is a normal runtime permission ─
            // The Compose layer (MainScreen's permLauncher) drives the dialog when
            // a tile is tapped. onResume() only needs to verify — not re-request —
            // so no action is taken here on API ≤ 29 if not yet granted.
        }
    }

    // ── Storage access check ─────────────────────────────────────────────────

    private fun hasStorageAccess(): Boolean = when {
        // Android 11+ (API 30+): check the "All files access" flag directly.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()

        // API ≤ 29: check the legacy READ_EXTERNAL_STORAGE runtime permission.
        else ->
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    // ── Companion: permission list for the Compose runtime-permission launcher ─

    companion object {
        /**
         * Used by MainScreen's Compose [permLauncher] when a tile is tapped and
         * [permissionGranted] is false.
         *
         * API 30+ → MANAGE_EXTERNAL_STORAGE is handled exclusively via the Settings
         *           intent in [onResume]. An empty array is returned so the Compose
         *           launcher never fires a dialog that immediately returns DENIED
         *           (MANAGE_EXTERNAL_STORAGE is not a requestable runtime permission),
         *           which was the root cause of the "storage permission required" loop.
         *
         * API ≤ 29 → READ_EXTERNAL_STORAGE is a normal runtime permission that the
         *            system dialog can approve. Return it here so the launcher works.
         */
        fun requiredPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> emptyArray()
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
