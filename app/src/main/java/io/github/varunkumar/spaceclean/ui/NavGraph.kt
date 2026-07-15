package io.github.varunkumar.spaceclean.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.varunkumar.spaceclean.ScanType
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack

// ─────────────────────────────────────────────────────────────────────────────
// Route definitions
// ─────────────────────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object AppStorage  : Screen("app_storage")
    object MediaViewer : Screen("media_viewer")
    object Compare     : Screen("compare")
    object Settings    : Screen("settings")
    object Search      : Screen("search")
    object FolderScan  : Screen("folder_scan")
    object SocialMedia : Screen("social_media")
    object Duplicates  : Screen("duplicates")
    object Trash       : Screen("trash")
    object QuickClean  : Screen("quick_clean")

    // One parameterised route covers the file-list managers
    // (PHOTOS/VIDEOS/DOCUMENTS/AUDIO/DOWNLOADS/CHAT_MEDIA/EMPTY_FOLDERS).
    // APPS navigates to AppStorage instead.
    object FileList : Screen("file_list/{scanType}") {
        fun createRoute(type: ScanType) = "file_list/${type.name}"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation host
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpaceCleanNavGraph(navController: NavHostController, viewModel: ScanViewModel) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route,
    ) {

        // ── Home dashboard ─────────────────────────────────────────────────
        composable(Screen.Home.route) {
            MainScreen(navController = navController, viewModel = viewModel)
        }

        // ── File list — Photos / Videos / Documents / Audio ────────────────
        // Argument: the ScanType.name string (e.g. "PHOTOS", "VIDEOS", …)
        composable(
            route     = Screen.FileList.route,
            arguments = listOf(navArgument("scanType") { type = NavType.StringType }),
        ) { backStack ->
            val name     = backStack.arguments?.getString("scanType")
            val scanType = name?.let { runCatching { ScanType.valueOf(it) }.getOrNull() }

            // If the argument is missing or unrecognised, bail out safely.
            LaunchedEffect(scanType) {
                if (scanType == null) navController.popBackStack()
            }

            if (scanType != null) {
                FileListScreen(
                    scanType      = scanType,
                    navController = navController,
                    viewModel     = viewModel,
                )
            } else {
                // Dark placeholder shown for the single frame before popBackStack fires.
                Box(Modifier.fillMaxSize().background(VelvetBlack))
            }
        }

        // ── Apps manager (dedicated screen with in-app uninstaller) ────────
        composable(Screen.AppStorage.route) {
            AppStorageScreen(navController = navController, viewModel = viewModel)
        }

        // ── Settings / About ───────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController, viewModel = viewModel)
        }

        // ── Recently Deleted (system Trash) ─────────────────────────────────
        composable(Screen.Trash.route) {
            RecentlyDeletedScreen(navController = navController, viewModel = viewModel)
        }

        // ── Quick Clean review (recommended items before deletion) ──────────
        composable(Screen.QuickClean.route) {
            QuickCleanScreen(navController = navController, viewModel = viewModel)
        }

        // ── Global search ───────────────────────────────────────────────────
        composable(Screen.Search.route) {
            SearchScreen(navController = navController, viewModel = viewModel)
        }

        // ── Social media cleaner (per-app chat media) ───────────────────────
        composable(Screen.SocialMedia.route) {
            SocialMediaScreen(navController = navController, viewModel = viewModel)
        }

        // ── Duplicate files (cross-location exact duplicates) ───────────────
        composable(Screen.Duplicates.route) {
            DuplicatesScreen(navController = navController, viewModel = viewModel)
        }

        // ── Targeted folder scan (SAF) ──────────────────────────────────────
        composable(Screen.FolderScan.route) {
            val uiState by viewModel.uiState.collectAsState()
            val uri = uiState.customFolderUri
            LaunchedEffect(uri) { if (uri == null) navController.popBackStack() }
            if (uri != null) {
                FolderScanScreen(treeUri = uri, navController = navController, viewModel = viewModel)
            } else {
                Box(Modifier.fillMaxSize().background(VelvetBlack))
            }
        }

        // ── Media viewer ───────────────────────────────────────────────────
        // viewingFile can be null after process death or config change;
        // pop immediately rather than showing a white screen.
        composable(Screen.MediaViewer.route) {
            val uiState by viewModel.uiState.collectAsState()
            val file    = uiState.viewingFile

            LaunchedEffect(file) {
                if (file == null) navController.popBackStack()
            }

            if (file != null) {
                MediaDetailsScreen(
                    file   = file,
                    // Only clear state; the LaunchedEffect above performs the single
                    // popBackStack. Popping here too would double-pop past the file list.
                    onBack = { viewModel.closeFileViewer() },
                )
            } else {
                Box(Modifier.fillMaxSize().background(VelvetBlack))
            }
        }

        // ── Side-by-side compare ───────────────────────────────────────────
        // comparePair can be null after process death or config change;
        // pop immediately rather than showing a white screen.
        composable(Screen.Compare.route) {
            val uiState by viewModel.uiState.collectAsState()
            val pair    = uiState.comparePair

            LaunchedEffect(pair) {
                if (pair == null) navController.popBackStack()
            }

            if (pair != null) {
                CompareScreen(
                    keepFile   = pair.first,
                    deleteFile = pair.second,
                    viewModel  = viewModel,
                    // Only clear state; the LaunchedEffect above performs the single pop.
                    onBack     = { viewModel.closeCompare() },
                )
            } else {
                Box(Modifier.fillMaxSize().background(VelvetBlack))
            }
        }
    }
}
