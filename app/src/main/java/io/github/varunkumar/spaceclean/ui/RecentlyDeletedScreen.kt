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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.TrashState
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.VelvetBlack
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.trashState

    // Always re-query on entry — items just deleted elsewhere in the app (or restored
    // from another screen) must show up immediately. scanTrash() refreshes silently
    // when data already exists, so there's no loading flash.
    LaunchedEffect(Unit) { viewModel.scanTrash() }

    val items = (state as? TrashState.Success)?.items.orEmpty()
    val selected = remember(items) { mutableStateMapOf<Uri, Boolean>() }
    val picked = items.filter { selected[it.uri] == true }

    // One launcher for both restore + permanent-delete confirmation dialogs.
    val opLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selected.clear()
            viewModel.onTrashChanged()
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
                        Text("Recently Deleted", style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (state is TrashState.Success) {
                            Text("${items.size} items · ${items.sumOf { it.sizeBytes }.toReadableSize()}",
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
        bottomBar = {
            if (picked.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(
                        icon = Icons.Rounded.Restore, label = "Restore ${picked.size}", tint = SuccessGreen,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.restoreFromTrash(picked) { sender ->
                                opLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }
                        },
                    )
                    ActionButton(
                        icon = Icons.Rounded.DeleteForever, label = "Delete", tint = ErrorRed,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.permanentlyDeleteTrash(picked) { sender ->
                                opLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        when {
            state is TrashState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
            }
            state is TrashState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = ErrorRed)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Restore, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                    Text("Trash is empty", style = MaterialTheme.typography.titleMedium, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Deleted photos & videos appear here,\nrecoverable until Android auto-removes them.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        "Everything you delete lands here and stays recoverable until the shown date (about 30 days), then it's removed automatically. Empty leftover folders are the only thing deleted directly.",
                        style = MaterialTheme.typography.labelSmall, color = TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                items(items, key = { it.uri.toString() }) { item ->
                    val checked = selected[item.uri] == true
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CyberCard)
                            .border(1.dp, if (checked) ElectricCyan.copy(0.45f) else Color(0xFF2A2A3A), RoundedCornerShape(14.dp))
                            .clickable { if (checked) selected.remove(item.uri) else selected[item.uri] = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NeonCheckbox(checked = checked, onCheckedChange = {
                            if (it) selected[item.uri] = true else selected.remove(item.uri)
                        })
                        FileThumb(
                            file = ScannedFile(item.uri, item.name, "", item.sizeBytes, 0L),
                            size = 46.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium,
                                color = if (checked) TextPrimary else TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                            Text(expiryLabel(item.expiresMs), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text(item.sizeBytes.toReadableSize(), style = MaterialTheme.typography.labelMedium,
                            color = if (checked) ElectricCyan else TextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    label:    String,
    tint:     Color,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(0.14f))
            .border(1.dp, tint.copy(0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

private fun expiryLabel(expiresMs: Long): String {
    if (expiresMs <= 0L) return "Recoverable"
    val days = ((expiresMs - System.currentTimeMillis()) / 86_400_000L)
    return when {
        days <= 0 -> "Expires soon"
        days == 1L -> "1 day left"
        else -> "$days days left"
    }
}
