package io.github.varunkumar.spaceclean.ui

import io.github.varunkumar.spaceclean.ui.theme.OutlineColor
import io.github.varunkumar.spaceclean.ui.theme.SurfaceVariant
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.ScanState
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground

private data class SocialGroup(val app: String, val color: Color, val files: List<ScannedFile>) {
    val totalBytes get() = files.sumOf { it.sizeBytes }
}

private enum class SocialSort(val label: String) {
    SIZE("Largest"),
    NAME("Name A–Z"),
    OLDEST("Oldest"),
}

private fun classifyApp(file: ScannedFile): Pair<String, Color> {
    val s = (file.uri.toString() + " " + file.path).lowercase()
    return when {
        "w4b" in s || "whatsapp business" in s        -> "WhatsApp Business" to Color(0xFF1FA855)
        "whatsapp" in s || "com.whatsapp" in s          -> "WhatsApp" to Color(0xFF25D366)
        "telegram" in s || "thunderdog" in s            -> "Telegram" to Color(0xFF2AABEE)
        "orca" in s || "facebook" in s || "messenger" in s -> "Messenger" to Color(0xFF0A7CFF)
        "viber" in s                                    -> "Viber" to Color(0xFF7360F2)
        "signal" in s || "securesms" in s               -> "Signal" to Color(0xFF3A76F0)
        else                                            -> "Other" to TextSecondary
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val state    = uiState.chatMediaState

    val files = (state as? ScanState.Success)?.files.orEmpty()
    val groups = remember(files) {
        files.groupBy { classifyApp(it) }
            .map { (k, v) -> SocialGroup(k.first, k.second, v) }
            .sortedByDescending { it.totalBytes }
    }

    val selected = remember(files) { mutableStateMapOf<android.net.Uri, Boolean>() }
    var expanded by remember { mutableStateOf<String?>(null) }
    var sortBy   by remember { mutableStateOf(SocialSort.SIZE) }

    fun sortFiles(list: List<ScannedFile>) = when (sortBy) {
        SocialSort.SIZE   -> list.sortedByDescending { it.sizeBytes }
        SocialSort.NAME   -> list.sortedBy { it.name.lowercase() }
        SocialSort.OLDEST -> list.sortedBy { if (it.dateModifiedMs > 0) it.dateModifiedMs else Long.MAX_VALUE }
    }

    fun preview(file: ScannedFile) {
        viewModel.openFileViewer(file)
        navController.navigate(Screen.MediaViewer.route)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.finalizeDelete()
        else viewModel.onDeleteCancelled()
    }

    fun deleteApp(group: SocialGroup) {
        val toDelete = group.files.filter { selected[it.uri] == true }
            .ifEmpty { group.files }                       // nothing ticked → clean all
        viewModel.requestDelete(toDelete) { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Social Media Cleaner", style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (state is ScanState.Success) {
                            Text("${files.size} files · ${files.sumOf { it.sizeBytes }.toReadableSize()}",
                                style = MaterialTheme.typography.labelMedium, color = TextSecondary)
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
        when {
            state is ScanState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    Text("Scanning chat media…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No chat media found.", color = TextMuted)
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding        = PaddingValues(vertical = 2.dp),
                    ) {
                        SocialSort.entries.forEach { opt ->
                            item(key = opt.name) { SocialSortChip(opt.label, sortBy == opt) { sortBy = opt } }
                        }
                    }
                }
                items(groups, key = { it.app }) { group ->
                    AppGroupCard(
                        group       = group.copy(files = sortFiles(group.files)),
                        expanded    = expanded == group.app,
                        selected    = selected,
                        onToggle    = { expanded = if (expanded == group.app) null else group.app },
                        onSelectAll = { all -> group.files.forEach { if (all) selected[it.uri] = true else selected.remove(it.uri) } },
                        onClean     = { deleteApp(group) },
                        onPreview   = { preview(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ElectricCyan.copy(0.15f) else CyberCard)
            .border(1.dp, if (selected) ElectricCyan.copy(0.5f) else OutlineColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) ElectricCyan else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGroupCard(
    group:       SocialGroup,
    expanded:    Boolean,
    selected:    MutableMap<android.net.Uri, Boolean>,
    onToggle:    () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onClean:     () -> Unit,
    onPreview:   (ScannedFile) -> Unit,
) {
    val pickedInGroup = group.files.count { selected[it.uri] == true }
    val allPicked = pickedInGroup == group.files.size && group.files.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberCard)
            .border(1.dp, group.color.copy(0.25f), RoundedCornerShape(16.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(group.color.copy(0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Chat, null, tint = group.color, modifier = Modifier.size(22.dp)) }

            Column(Modifier.weight(1f)) {
                Text(group.app, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("${group.files.size} files · ${group.totalBytes.toReadableSize()}",
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Text(group.totalBytes.toReadableSize(), style = MaterialTheme.typography.titleSmall,
                color = group.color, fontWeight = FontWeight.Black)
            Icon(Icons.Rounded.ExpandMore, null, tint = TextMuted, modifier = Modifier.size(22.dp))
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                // Select-all + clean row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeonCheckbox(checked = allPicked, onCheckedChange = onSelectAll)
                    Text(if (allPicked) "All selected" else "Select all",
                        style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ErrorRed.copy(0.14f))
                            .border(1.dp, ErrorRed.copy(0.3f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onClean)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Text(
                                if (pickedInGroup > 0) "Delete $pickedInGroup" else "Clean all",
                                style = MaterialTheme.typography.labelMedium, color = ErrorRed, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // File list (capped height so the card stays manageable)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(group.files, key = { it.uri.toString() }) { file ->
                        val checked = selected[file.uri] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceVariant)
                                .combinedClickable(
                                    onClick     = { if (checked) selected.remove(file.uri) else selected[file.uri] = true },
                                    onLongClick = { onPreview(file) },
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NeonCheckbox(checked = checked, onCheckedChange = {
                                if (it) selected[file.uri] = true else selected.remove(file.uri)
                            })
                            FileThumb(file = file, size = 40.dp, corner = 8.dp)
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.labelMedium,
                                    color = if (checked) TextPrimary else TextSecondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Hold to preview", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            Text(file.sizeBytes.toReadableSize(), style = MaterialTheme.typography.labelSmall,
                                color = if (checked) ElectricCyan else TextMuted)
                        }
                    }
                }
            }
        }
    }
}
