package io.github.varunkumar.spaceclean.ui

import io.github.varunkumar.spaceclean.ui.theme.OutlineColor
import io.github.varunkumar.spaceclean.ui.theme.SurfaceVariant
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import io.github.varunkumar.spaceclean.DuplicatesState
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.DuplicateScanner
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
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.duplicatesState

    val groups = (state as? DuplicatesState.Success)?.groups.orEmpty()

    // Pre-select every redundant copy (all but the keeper) when results change.
    val selected = remember(groups) {
        mutableStateMapOf<Uri, Boolean>().apply {
            groups.forEach { g ->
                val keeper = DuplicateScanner.pickKeeper(g)
                g.forEach { if (it.uri != keeper.uri) put(it.uri, true) }
            }
        }
    }

    val allFiles      = groups.flatten()
    val selectedFiles = allFiles.filter { selected[it.uri] == true }
    val selectedBytes = selectedFiles.sumOf { it.sizeBytes }
    val reclaimable   = groups.sumOf { g -> g.drop(1).sumOf { it.sizeBytes } }   // best case
    var showConfirm by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.finalizeDelete()
        else viewModel.onDeleteCancelled()
    }

    fun preview(file: ScannedFile) {
        viewModel.openFileViewer(file)
        navController.navigate(Screen.MediaViewer.route)
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Duplicate Files", style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (state is DuplicatesState.Success) {
                            Text("${groups.size} groups · up to ${reclaimable.toReadableSize()} reclaimable",
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
            if (selectedFiles.isNotEmpty()) {
                Button(
                    onClick = { showConfirm = true },
                    enabled = !uiState.isDeletingFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                ) {
                    if (uiState.isDeletingFiles) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Delete ${selectedFiles.size} copies · ${selectedBytes.toReadableSize()}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state is DuplicatesState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    Text("Comparing file contents…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("This can take a moment on large libraries", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            state is DuplicatesState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = ErrorRed)
            }
            groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(56.dp))
                    Text("No duplicates found", style = MaterialTheme.typography.titleMedium,
                        color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Text("No identical copies across your storage and chat apps.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        modifier = Modifier.padding(horizontal = 40.dp))
                }
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "One copy is kept (✓) and the rest are pre-selected. Hold a row to preview.",
                        style = MaterialTheme.typography.labelMedium, color = TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(groups, key = { it.first().uri.toString() }) { group ->
                    DuplicateGroupCard(
                        group     = group,
                        keeper    = DuplicateScanner.pickKeeper(group),
                        selected  = selected,
                        onPreview = { preview(it) },
                    )
                }
            }
        }
    }

    DeleteConfirmDialog(
        visible   = showConfirm,
        count     = selectedFiles.size,
        bytes     = selectedBytes,
        onDismiss = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            viewModel.requestDelete(selectedFiles) { sender ->
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateGroupCard(
    group:     List<ScannedFile>,
    keeper:    ScannedFile,
    selected:  MutableMap<Uri, Boolean>,
    onPreview: (ScannedFile) -> Unit,
) {
    val copies     = group.size
    val perCopy    = group.first().sizeBytes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberCard)
            .border(1.dp, OutlineColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Group header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$copies copies", style = MaterialTheme.typography.titleSmall,
                color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("·  ${perCopy.toReadableSize()} each", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("save ${(perCopy * (copies - 1)).toReadableSize()}",
                style = MaterialTheme.typography.labelMedium, color = ElectricCyan, fontWeight = FontWeight.Bold)
        }

        group.forEach { file ->
            val isKeeper = file.uri == keeper.uri
            val checked  = selected[file.uri] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isKeeper) SuccessGreen.copy(0.07f) else SurfaceVariant)
                    .combinedClickable(
                        onClick     = { if (!isKeeper) { if (checked) selected.remove(file.uri) else selected[file.uri] = true } },
                        onLongClick = { onPreview(file) },
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isKeeper) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, "Keep", tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    }
                } else {
                    NeonCheckbox(checked = checked, onCheckedChange = {
                        if (it) selected[file.uri] = true else selected.remove(file.uri)
                    })
                }
                FileThumb(file = file, size = 40.dp, corner = 8.dp)
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val chat = DuplicateScanner.isChatPath(file.path)
                        Icon(
                            if (chat) Icons.Rounded.Chat else Icons.Rounded.Smartphone,
                            null, tint = if (chat) WarningAmber else TextSecondary, modifier = Modifier.size(11.dp),
                        )
                        Text(
                            (if (chat) "Chat · " else "Phone · ") + locationOf(file.path),
                            style = MaterialTheme.typography.labelSmall, color = TextMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isKeeper) {
                    Text("KEEP", style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun locationOf(path: String): String =
    path.substringBeforeLast('/', "").substringAfterLast('/', "").ifBlank { "storage" }
