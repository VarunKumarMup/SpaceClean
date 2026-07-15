package io.github.varunkumar.spaceclean.ui

import io.github.varunkumar.spaceclean.ui.theme.OutlineColor
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.SolidColor
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val pool: List<ScannedFile> = remember(
        uiState.photosState, uiState.videosState, uiState.documentsState, uiState.audioState,
        uiState.downloadsState, uiState.chatMediaState, uiState.emptyFoldersState,
        uiState.largestState, uiState.oldFilesState,
    ) {
        listOf(
            uiState.photosState, uiState.videosState, uiState.documentsState, uiState.audioState,
            uiState.downloadsState, uiState.chatMediaState, uiState.emptyFoldersState,
            uiState.largestState, uiState.oldFilesState,
        ).filterIsInstance<ScanState.Success>()
            .flatMap { it.files }
            .distinctBy { it.uri }
    }

    val results = remember(pool, query) {
        if (query.isBlank()) emptyList()
        else pool.filter { it.name.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.sizeBytes }
    }

    val selected = remember { mutableStateMapOf<Uri, Boolean>() }
    val selectedFiles = results.filter { selected[it.uri] == true }
    val selectedBytes = selectedFiles.sumOf { it.sizeBytes }
    var showConfirm by remember { mutableStateOf(false) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.finalizeDelete()
        else viewModel.onDeleteCancelled()
    }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = { Text("Search", style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary, fontWeight = FontWeight.Bold) },
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
                        Text("Delete ${selectedFiles.size} · ${selectedBytes.toReadableSize()}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberCard)
                    .border(1.dp, ElectricCyan.copy(0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search files and folders…",
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    BasicTextField(
                        value         = query,
                        onValueChange = { query = it },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush   = SolidColor(ElectricCyan),
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Rounded.Close, "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            when {
                pool.isEmpty() -> CenterHint(
                    "Nothing to search yet.\nRun a scan (or Smart Scan All) first, then search across everything found."
                )
                query.isBlank() -> CenterHint(
                    "${pool.size} scanned items ready.\nType to search by name across all categories."
                )
                results.isEmpty() -> CenterHint("No matches for \"${query.trim()}\".")
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${results.size} ${if (results.size == 1) "result" else "results"}",
                            style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text("Tap to select · hold to preview",
                            style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    LazyColumn(
                        contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(results, key = { it.uri.toString() }) { file ->
                            val checked = selected[file.uri] == true
                            ResultRow(
                                modifier = Modifier.animateItem(),
                                file    = file,
                                checked = checked,
                                onToggle = { if (checked) selected.remove(file.uri) else selected[file.uri] = true },
                                onPreview = {
                                    viewModel.openFileViewer(file)
                                    navController.navigate(Screen.MediaViewer.route)
                                },
                            )
                        }
                    }
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
private fun ResultRow(
    file: ScannedFile,
    checked: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberCard)
            .border(1.dp, if (checked) ElectricCyan.copy(0.45f) else OutlineColor, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onPreview)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NeonCheckbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumb(file = file, size = 46.dp)
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium,
                color = if (checked) TextPrimary else TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
            Text(file.path.ifBlank { "Unknown location" }, style = MaterialTheme.typography.labelSmall,
                color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (file.sizeBytes > 0L) {
            Text(file.sizeBytes.toReadableSize(), style = MaterialTheme.typography.labelMedium,
                color = if (checked) ElectricCyan else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(32.dp))
    }
}
