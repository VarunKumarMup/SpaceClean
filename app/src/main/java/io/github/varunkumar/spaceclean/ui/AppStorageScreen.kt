package io.github.varunkumar.spaceclean.ui

import io.github.varunkumar.spaceclean.ui.theme.OutlineColor
import io.github.varunkumar.spaceclean.ui.theme.SurfaceVariant
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.AppStorageState
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.AppInfo
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.NeonViolet
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AppSortBy(val label: String) {
    SIZE("By Size"),
    NAME("By Name"),
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStorageScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState  by viewModel.uiState.collectAsState()
    val appState  = uiState.appStorageState
    val context   = LocalContext.current

    var sortBy      by remember { mutableStateOf(AppSortBy.SIZE) }
    var sheetApp    by remember { mutableStateOf<AppInfo?>(null) }
    val sheetState  = rememberModalBottomSheetState()

    // StartActivityForResult fires its callback when we return from the system
    // uninstall / app-info screen (regardless of result), so we re-scan to reflect
    // any uninstall.
    val systemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.scanAppStorage(navigate = false) }

    fun openAppInfo(pkg: String) {
        runCatching {
            systemLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            )
        }
    }

    fun uninstall(pkg: String) {
        val pkgUri = Uri.fromParts("package", pkg, null)
        // ACTION_UNINSTALL_PACKAGE shows the system confirm dialog *over the app* and
        // returns here afterwards. Fall back to ACTION_DELETE if an OEM lacks it.
        val launched = runCatching {
            @Suppress("DEPRECATION")
            systemLauncher.launch(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE, pkgUri)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            )
            true
        }.getOrDefault(false)

        if (!launched) runCatching {
            systemLauncher.launch(Intent(Intent.ACTION_DELETE, pkgUri))
        }
    }

    LaunchedEffect(Unit) {
        if (appState is AppStorageState.Idle) viewModel.scanAppStorage(navigate = false)
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Storage", style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (appState is AppStorageState.Success) {
                            val total = appState.apps.sumOf { it.apkSizeBytes }
                            Text(
                                "${appState.apps.size} apps · ${total.toReadableSize()} APK total",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = ElectricCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        when (appState) {
            is AppStorageState.Idle,
            is AppStorageState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                        Text("Analyzing installed apps…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            is AppStorageState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Error: ${appState.message}", color = ErrorRed)
                }
            }

            is AppStorageState.Success -> {
                val rawApps = appState.apps.filter { it.packageName != context.packageName }
                val sortedApps = when (sortBy) {
                    AppSortBy.SIZE -> rawApps.sortedByDescending { it.apkSizeBytes }
                    AppSortBy.NAME -> rawApps.sortedBy { it.appName.lowercase() }
                }
                val maxSize  = sortedApps.firstOrNull()?.apkSizeBytes ?: 1L
                val totalApk = rawApps.sumOf { it.apkSizeBytes }

                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding        = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            AppSortBy.entries.forEach { option ->
                                item(key = option.name) {
                                    SortChip(option.label, sortBy == option) { sortBy = option }
                                }
                            }
                        }
                    }

                    item {
                        SummaryCard(count = rawApps.size, totalApk = totalApk)
                        Spacer(Modifier.height(4.dp))
                    }

                    itemsIndexed(sortedApps, key = { _, app -> app.packageName }) { index, app ->
                        AppRow(
                            app          = app,
                            rank         = index + 1,
                            maxSizeBytes = maxSize,
                            onClick      = { sheetApp = app },
                            onInfo       = { openAppInfo(app.packageName) },
                            onUninstall  = { uninstall(app.packageName) },
                        )
                    }

                    item {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Tap an app for options. APK sizes are compressed; actual installed size may be larger.",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }

    // ── Action sheet ──────────────────────────────────────────────────────────
    sheetApp?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { sheetApp = null },
            sheetState       = sheetState,
            containerColor   = CyberCard,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier              = Modifier.padding(bottom = 8.dp),
                ) {
                    AppIcon(app.packageName, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${app.apkSizeBytes.toReadableSize()} · ${app.packageName}",
                            style = MaterialTheme.typography.labelSmall, color = TextMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                SheetAction(Icons.Rounded.Settings, ElectricCyan, "Open App Info",
                    "Force stop, clear cache, manage storage") {
                    sheetApp = null; openAppInfo(app.packageName)
                }
                SheetAction(Icons.Rounded.Delete, ErrorRed, "Uninstall",
                    "Remove this app from the device") {
                    sheetApp = null; uninstall(app.packageName)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(count: Int, totalApk: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberCard)
            .border(1.dp, ElectricCyan.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text("$count User Apps", style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Total compressed APK size", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Text(totalApk.toReadableSize(), style = MaterialTheme.typography.titleMedium,
                color = ElectricCyan, fontWeight = FontWeight.Black)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    app:          AppInfo,
    rank:         Int,
    maxSizeBytes: Long,
    onClick:      () -> Unit,
    onInfo:       () -> Unit,
    onUninstall:  () -> Unit,
) {
    val sizeFraction = if (maxSizeBytes > 0L)
        (app.apkSizeBytes.toFloat() / maxSizeBytes).coerceIn(0f, 1f) else 0f
    val barBrush = when (rank) {
        1    -> Brush.linearGradient(listOf(WarningAmber, ErrorRed))
        2, 3 -> Brush.linearGradient(listOf(ElectricCyan, NeonViolet))
        else -> Brush.linearGradient(listOf(ElectricCyan.copy(0.5f), NeonViolet.copy(0.4f)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberCard)
            .border(
                1.dp,
                when (rank) { 1 -> WarningAmber.copy(0.3f); 2, 3 -> ElectricCyan.copy(0.15f); else -> OutlineColor },
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(
                    when (rank) { 1 -> WarningAmber.copy(0.18f); 2 -> TextSecondary.copy(0.12f); 3 -> ElectricCyan.copy(0.10f); else -> SurfaceVariant },
                    RoundedCornerShape(8.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text("$rank", style = MaterialTheme.typography.labelSmall,
                    color = when (rank) { 1 -> WarningAmber; 2 -> TextSecondary; 3 -> ElectricCyan; else -> TextMuted },
                    fontWeight = FontWeight.Bold)
            }

            AppIcon(app.packageName, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(app.apkSizeBytes.toReadableSize(), style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan, fontWeight = FontWeight.Bold)
            }

            // Quick actions
            ActionIcon(Icons.Rounded.Info, ElectricCyan, "App info", onInfo)
            ActionIcon(Icons.Rounded.Delete, ErrorRed, "Uninstall ${app.appName}", onUninstall)
        }

        if (sizeFraction > 0f) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(SurfaceVariant)) {
                Box(Modifier.fillMaxWidth(sizeFraction).fillMaxHeight().background(barBrush))
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, tint: Color, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(tint.copy(0.1f), RoundedCornerShape(10.dp))
            .border(1.dp, tint.copy(0.22f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, desc, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SheetAction(
    icon:     ImageVector,
    tint:     Color,
    title:    String,
    subtitle: String,
    onClick:  () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant)
            .border(1.dp, tint.copy(0.18f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(tint.copy(0.14f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App icon loader
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap  by produceState<Bitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                if (drawable is BitmapDrawable) drawable.bitmap
                else {
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(Canvas(bmp))
                    bmp
                }
            }.getOrNull()
        }
    }

    Box(modifier = modifier.background(SurfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.Android, null, tint = TextMuted, modifier = Modifier.size(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sort chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ElectricCyan.copy(0.15f) else CyberCard)
            .border(1.dp, if (selected) ElectricCyan.copy(0.5f) else OutlineColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) ElectricCyan else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
