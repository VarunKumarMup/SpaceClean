package io.github.varunkumar.spaceclean.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.QuickCleanCategory
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.AppThemeId
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ThemeState
import io.github.varunkumar.spaceclean.ui.theme.applyAndSaveTheme
import io.github.varunkumar.spaceclean.ui.theme.NeonViolet
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
    val hasAllFiles = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else uiState.permissionGranted
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary, fontWeight = FontWeight.Bold)
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
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Privacy hero ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(SuccessGreen.copy(0.14f), ElectricCyan.copy(0.06f)))
                    )
                    .border(1.dp, SuccessGreen.copy(0.25f), RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .background(SuccessGreen.copy(0.16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.WifiOff, null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                        }
                        Text("100% Offline", style = MaterialTheme.typography.titleMedium,
                            color = SuccessGreen, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SpaceClean requests no internet permission. Your files, app list, and scan "
                            + "results never leave this device — there is no network code to send them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // ── Recently deleted (Trash) ──────────────────────────────────────
            ActionCard(
                icon       = Icons.Rounded.Restore,
                tint       = SuccessGreen,
                title      = "Recently deleted",
                subtitle   = "Restore files or empty the Trash",
                actionIcon = Icons.Rounded.ChevronRight,
                onClick    = { navController.navigate(Screen.Trash.route) },
            )

            // ── Quick Clean auto-select categories ────────────────────────────
            QuickCleanPrefsSection(
                enabled  = uiState.quickCleanCategories,
                onToggle = { key, on -> viewModel.setQuickCleanCategory(key, on) },
            )

            // ── Theme picker ──────────────────────────────────────────────────
            ThemePickerSection()

            // ── Lifetime cleaned ──────────────────────────────────────────────
            InfoCard(
                icon  = Icons.Rounded.CleaningServices,
                tint  = ElectricCyan,
                title = "Space cleaned (lifetime)",
                value = uiState.totalSpaceSavedBytes.toReadableSize(),
            )

            // ── Storage ───────────────────────────────────────────────────────
            ActionCard(
                icon       = Icons.Rounded.Folder,
                tint       = NeonViolet,
                title      = "Device storage",
                subtitle   = if (uiState.totalStorageBytes > 0L)
                    "${uiState.usedStorageBytes.toReadableSize()} used of ${uiState.totalStorageBytes.toReadableSize()}"
                else "Tap refresh to calculate",
                actionIcon = Icons.Rounded.Refresh,
                onClick    = { viewModel.refreshStorageStats() },
            )

            // ── Storage devices (internal + SD / USB) ─────────────────────────
            StorageVolumesSection()

            // ── Permission status ─────────────────────────────────────────────
            ActionCard(
                icon       = if (hasAllFiles) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                tint       = if (hasAllFiles) SuccessGreen else ElectricCyan,
                title      = "All-files access",
                subtitle   = if (hasAllFiles) "Granted — full scanning enabled" else "Not granted — tap to open settings",
                actionIcon = if (hasAllFiles) null else Icons.Rounded.ArrowBackIosNew,
                onClick    = {
                    if (!hasAllFiles) runCatching {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.fromParts("package", context.packageName, null))
                        else
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null))
                        context.startActivity(intent)
                    }
                },
            )

            // ── FAQ ───────────────────────────────────────────────────────────
            FaqSection()

            // ── Contact developer ─────────────────────────────────────────────
            ContactSection()

            Spacer(Modifier.height(8.dp))
            Text(
                "SpaceClean  ·  v$versionName",
                style    = MaterialTheme.typography.labelMedium,
                color    = TextMuted,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "OFFLINE  •  PRIVATE  •  NO ADS",
                style         = MaterialTheme.typography.labelSmall,
                color         = ElectricCyan.copy(0.5f),
                letterSpacing = 2.sp,
                fontWeight    = FontWeight.Bold,
                modifier      = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Clean category preferences
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickCleanPrefsSection(enabled: Set<String>, onToggle: (String, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "QUICK CLEAN AUTO-SELECTS",
            style      = MaterialTheme.typography.labelSmall,
            color      = ElectricCyan.copy(0.7f),
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CyberCard)
                .border(1.dp, ElectricCyan.copy(0.12f), RoundedCornerShape(16.dp))
                .padding(vertical = 4.dp),
        ) {
            QuickCleanCategory.entries.forEachIndexed { idx, cat ->
                val on = cat.key in enabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(cat.key, !on) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cat.title, style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(cat.subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked         = on,
                        onCheckedChange = { onToggle(cat.key, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = ElectricCyan,
                            checkedTrackColor   = ElectricCyan.copy(0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = VelvetBlack,
                        ),
                    )
                }
                if (idx != QuickCleanCategory.entries.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(ElectricCyan.copy(0.06f))
                    )
                }
            }
        }
        Text(
            "Only what's enabled here gets pre-selected on the Quick Clean review screen. " +
                "Everything still asks for confirmation before deleting.",
            style    = MaterialTheme.typography.labelSmall,
            color    = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FAQ
// ─────────────────────────────────────────────────────────────────────────────

private val FAQS = listOf(
    "Is my data really private?" to
        "Yes. SpaceClean has no internet permission at all — you can verify it in the app's " +
        "permission list. Nothing is ever uploaded, and there are no ads, trackers, or accounts.",
    "Where do deleted files go?" to
        "Everything goes to a recoverable Trash for about 30 days — open Settings → Recently Deleted " +
        "to restore anything or delete it for good. Photos and videos use Android's own system Trash; " +
        "documents, APKs and other files use SpaceClean's built-in trash. Only empty leftover folders " +
        "are removed directly.",
    "How does duplicate detection work?" to
        "It compares the actual content of files (a hash), so it only flags true duplicates — even " +
        "across phone storage and WhatsApp. Similar photos are matched by how they look, not by name.",
    "Why does it need All-files access?" to
        "A file manager/cleaner has to read across your whole storage to find junk, duplicates and " +
        "large files. The permission is used only on your device to show and clean files.",
    "Will it delete something important?" to
        "Deletions always ask for confirmation, and Quick Clean only pre-selects the categories you " +
        "enable above — duplicate copies, similar-photo extras, leftover folders and junk. Documents " +
        "are never auto-selected unless you turn that on, and really big files are only suggested " +
        "for review, never picked for you.",
)

@Composable
private fun FaqSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "FAQ",
            style      = MaterialTheme.typography.labelSmall,
            color      = ElectricCyan.copy(0.7f),
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        FAQS.forEach { (q, a) -> FaqItem(question = q, answer = a) }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberCard)
            .border(1.dp, ElectricCyan.copy(0.1f), RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question,
                style      = MaterialTheme.typography.bodyMedium,
                color      = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, tint = TextMuted, modifier = Modifier.size(22.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                answer,
                style    = MaterialTheme.typography.bodyMedium,
                color    = TextSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contact developer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactSection() {
    val context = LocalContext.current
    val email = "varunkumarmuppuri@gmail.com"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "CONTACT",
            style      = MaterialTheme.typography.labelSmall,
            color      = ElectricCyan.copy(0.7f),
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        ActionCard(
            icon       = Icons.Rounded.MailOutline,
            tint       = ElectricCyan,
            title      = "Email the developer",
            subtitle   = email,
            actionIcon = Icons.Rounded.ChevronRight,
            onClick    = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$email")
                            putExtra(Intent.EXTRA_SUBJECT, "SpaceClean feedback")
                        }
                    )
                }
            },
        )
    }
}

private data class VolumeInfo(
    val name:       String,
    val removable:  Boolean,
    val totalBytes: Long,
    val freeBytes:  Long,
)

@Composable
private fun StorageVolumesSection() {
    val context = LocalContext.current
    val volumes = remember {
        runCatching {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.map { vol ->
                var total = 0L
                var free  = 0L
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
                val path = dir?.path ?: if (vol.isPrimary) Environment.getExternalStorageDirectory().path else null
                if (path != null) runCatching {
                    val sf = android.os.StatFs(path)
                    total = sf.blockCountLong * sf.blockSizeLong
                    free  = sf.availableBlocksLong * sf.blockSizeLong
                }
                VolumeInfo(
                    name       = vol.getDescription(context) ?: if (vol.isPrimary) "Internal storage" else "Storage",
                    removable  = vol.isRemovable,
                    totalBytes = total,
                    freeBytes  = free,
                )
            }
        }.getOrElse { emptyList() }
    }

    if (volumes.size <= 1) return  // only primary — nothing extra worth showing

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "STORAGE DEVICES",
            style         = MaterialTheme.typography.labelSmall,
            color         = ElectricCyan.copy(0.7f),
            fontWeight    = FontWeight.Bold,
            modifier      = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        volumes.forEach { v ->
            val used = (v.totalBytes - v.freeBytes).coerceAtLeast(0L)
            ActionCard(
                icon       = if (v.removable) Icons.Rounded.SdCard else Icons.Rounded.Folder,
                tint       = if (v.removable) WarningAmber else NeonViolet,
                title      = v.name + if (v.removable) "  (removable)" else "",
                subtitle   = if (v.totalBytes > 0L)
                    "${used.toReadableSize()} used of ${v.totalBytes.toReadableSize()}"
                else "Tap a folder scan to clean this device",
                actionIcon = null,
                onClick    = {},
            )
        }
    }
}

@Composable
private fun ThemePickerSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "THEME",
            style      = MaterialTheme.typography.labelSmall,
            color      = ElectricCyan.copy(0.7f),
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CyberCard)
                .border(1.dp, ElectricCyan.copy(0.12f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppThemeId.entries.forEach { theme ->
                val selected = ThemeState.current == theme
                Column(
                    modifier            = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { applyAndSaveTheme(context, theme) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Swatch: theme background with its two accents
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.palette.background)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) ElectricCyan else theme.palette.outline,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(theme.palette.accent))
                            Box(Modifier.size(12.dp).clip(CircleShape).background(theme.palette.accent2))
                        }
                    }
                    Text(
                        theme.displayName,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (selected) ElectricCyan else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, tint: Color, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberCard)
            .border(1.dp, tint.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(tint.copy(0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionCard(
    icon:       ImageVector,
    tint:       Color,
    title:      String,
    subtitle:   String,
    actionIcon: ImageVector?,
    onClick:    () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberCard)
            .border(1.dp, tint.copy(0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(tint.copy(0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        if (actionIcon != null) {
            Icon(actionIcon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
