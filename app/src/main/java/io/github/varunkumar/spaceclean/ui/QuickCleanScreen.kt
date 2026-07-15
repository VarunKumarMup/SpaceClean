package io.github.varunkumar.spaceclean.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCleanScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val items = remember(uiState.photosState, uiState.duplicatesState, uiState.emptyFoldersState,
        uiState.downloadsState, uiState.documentsState, uiState.uselessState,
        uiState.recommendedForDeletion, uiState.quickCleanCategories) {
        recommendedCleanupItems(uiState)
    }
    // Really big files — suggested for review, but NEVER pre-selected.
    val suggestions = remember(items, uiState.largestState) {
        bigFileSuggestions(uiState, items.map { it.file.uri }.toSet())
    }

    // Recommended items pre-selected; suggestions start unchecked.
    val selected = remember(items) {
        mutableStateMapOf<Uri, Boolean>().apply { items.forEach { put(it.file.uri, true) } }
    }
    val chosen        = (items + suggestions).filter { selected[it.file.uri] == true }
    val chosenBytes   = chosen.sumOf { it.file.sizeBytes }
    var showConfirm by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.finalizeDelete()
            navController.popBackStack()
        } else viewModel.onDeleteCancelled()
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quick Clean", style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Review before deleting · media goes to Trash",
                            style = MaterialTheme.typography.labelMedium, color = TextSecondary)
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
        bottomBar = {
            if (chosen.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (uiState.isDeletingFiles) ErrorRed.copy(0.4f) else ErrorRed)
                        .clickable(enabled = !uiState.isDeletingFiles) { showConfirm = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    if (uiState.isDeletingFiles) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Clean ${chosen.size} · ${chosenBytes.toReadableSize()}",
                            color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        if (items.isEmpty() && suggestions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(56.dp))
                    Text("Nothing to quick-clean", style = MaterialTheme.typography.titleMedium,
                        color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Text("Run a scan first, or your junk is already gone.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (items.isNotEmpty()) {
                    item(key = "hdr-safe") {
                        Text(
                            "${items.size} safe items auto-selected. Uncheck anything you want to keep — " +
                                "you can change the categories in Settings.",
                            style = MaterialTheme.typography.labelMedium, color = TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    items(items, key = { it.file.uri.toString() }) { item ->
                        QuickCleanRow(
                            item     = item,
                            checked  = selected[item.file.uri] == true,
                            onToggle = { chk ->
                                if (chk) selected[item.file.uri] = true else selected.remove(item.file.uri)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (suggestions.isNotEmpty()) {
                    item(key = "hdr-big") {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                            Text(
                                "REVIEW — REALLY BIG FILES",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                            )
                            Text(
                                "Not selected automatically — tick the ones you no longer need.",
                                style = MaterialTheme.typography.labelSmall, color = TextMuted,
                            )
                        }
                    }
                    items(suggestions, key = { it.file.uri.toString() }) { item ->
                        QuickCleanRow(
                            item     = item,
                            checked  = selected[item.file.uri] == true,
                            onToggle = { chk ->
                                if (chk) selected[item.file.uri] = true else selected.remove(item.file.uri)
                            },
                            accent   = WarningAmber,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "footer") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    DeleteConfirmDialog(
        visible   = showConfirm,
        count     = chosen.size,
        bytes     = chosenBytes,
        onDismiss = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            viewModel.requestDelete(chosen.map { it.file }) { sender ->
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        },
    )
}

@Composable
private fun QuickCleanRow(
    item:     CleanupItem,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent:   Color = ElectricCyan,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberCard)
            .border(1.dp, if (checked) ErrorRed.copy(0.4f) else Color(0xFF2A2A3A), RoundedCornerShape(14.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NeonCheckbox(checked = checked, onCheckedChange = onToggle)
        FileThumb(file = item.file, size = 46.dp)
        Column(Modifier.weight(1f)) {
            Text(item.file.name, style = MaterialTheme.typography.bodyMedium,
                color = if (checked) TextPrimary else TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent.copy(0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(item.reason, style = MaterialTheme.typography.labelSmall, color = accent)
                }
                Text(item.file.path.ifBlank { "" }, style = MaterialTheme.typography.labelSmall,
                    color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.file.sizeBytes > 0L) {
            Text(item.file.sizeBytes.toReadableSize(), style = MaterialTheme.typography.labelMedium,
                color = if (checked) ErrorRed else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}
