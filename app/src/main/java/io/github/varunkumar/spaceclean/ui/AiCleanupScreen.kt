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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.varunkumar.spaceclean.AiState
import io.github.varunkumar.spaceclean.ScanViewModel
import io.github.varunkumar.spaceclean.scanner.ScannedFile
import io.github.varunkumar.spaceclean.toReadableSize
import io.github.varunkumar.spaceclean.ui.theme.CyberCard
import io.github.varunkumar.spaceclean.ui.theme.ElectricCyan
import io.github.varunkumar.spaceclean.ui.theme.ErrorRed
import io.github.varunkumar.spaceclean.ui.theme.NeonViolet
import io.github.varunkumar.spaceclean.ui.theme.SuccessGreen
import io.github.varunkumar.spaceclean.ui.theme.TextMuted
import io.github.varunkumar.spaceclean.ui.theme.TextPrimary
import io.github.varunkumar.spaceclean.ui.theme.TextSecondary
import io.github.varunkumar.spaceclean.ui.theme.WarningAmber
import io.github.varunkumar.spaceclean.ui.theme.auroraBackground

private data class AiRow(val file: ScannedFile, val reason: String, val tag: String?, val preselect: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCleanupScreen(navController: NavHostController, viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val ai = uiState.aiState

    LaunchedEffect(Unit) { if (ai is AiState.Idle) viewModel.scanAi() }

    val rows: List<AiRow> = remember(ai) {
        val result = (ai as? AiState.Success)?.result ?: return@remember emptyList()
        val out = LinkedHashMap<Uri, AiRow>()
        // Worse copies of AI-similar groups (keep the best one).
        result.similarGroups.forEach { group ->
            val keeper = aiKeeper(group)
            group.filter { it.uri != keeper.uri }.forEach {
                out[it.uri] = AiRow(it, "Similar shot", result.tags[it.uri], preselect = true)
            }
        }
        // Blurry & screenshots are only SUGGESTED (never pre-checked) — the user decides,
        // so a sharp portrait with a soft background is never auto-selected for deletion.
        result.blurry.forEach {
            out.getOrPut(it.uri) { AiRow(it, "Blurry / low quality", result.tags[it.uri], preselect = false) }
        }
        result.screenshots.forEach {
            out.getOrPut(it.uri) { AiRow(it, "Screenshot / meme", result.tags[it.uri], preselect = false) }
        }
        out.values.sortedByDescending { it.file.sizeBytes }
    }

    val selected = remember(rows) {
        mutableStateMapOf<Uri, Boolean>().apply { rows.forEach { if (it.preselect) put(it.file.uri, true) } }
    }
    val chosen = rows.filter { selected[it.file.uri] == true }
    val chosenBytes = chosen.sumOf { it.file.sizeBytes }
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
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = NeonViolet, modifier = Modifier.size(20.dp))
                            Text("AI Photo Cleanup", style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text("On-device AI · nothing leaves your phone",
                            style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = ElectricCyan)
                    }
                },
                actions = {
                    val allSelected = rows.isNotEmpty() && rows.all { selected[it.file.uri] == true }
                    if (rows.isNotEmpty()) {
                        Row(modifier = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NeonCheckbox(checked = allSelected, onCheckedChange = { chk ->
                                if (chk) rows.forEach { selected[it.file.uri] = true }
                                else rows.forEach { selected.remove(it.file.uri) }
                            })
                            Text(if (allSelected) "None" else "All",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (allSelected) ElectricCyan else TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (uiState.isDeletingFiles) ErrorRed.copy(0.4f) else ErrorRed)
                        .clickable(enabled = !uiState.isDeletingFiles) { showConfirm = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ai is AiState.Loading -> AiLoading(ai.done, ai.total)
                ai is AiState.Error -> Centered("AI unavailable", ai.message, ErrorRed)
                ai is AiState.Success && !ai.result.available ->
                    Centered("AI model unavailable", "This device couldn't load the on-device model. Other cleanup tools still work.", WarningAmber)
                rows.isEmpty() && ai is AiState.Success ->
                    Centered("No photo junk found", "The AI analyzed ${ (ai).result.analyzedCount } photos and found nothing worth deleting.", SuccessGreen)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        val analyzed = (ai as? AiState.Success)?.result?.analyzedCount ?: 0
                        Text("The AI looked at $analyzed photos. Only near-identical duplicates are " +
                            "pre-checked — blurry shots and screenshots are just suggestions, so tick " +
                            "the ones you actually want gone. Everything goes to the recoverable Trash.",
                            style = MaterialTheme.typography.labelMedium, color = TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                    items(rows, key = { it.file.uri.toString() }) { row ->
                        val checked = selected[row.file.uri] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CyberCard)
                                .border(1.dp, if (checked) ErrorRed.copy(0.4f) else Color(0xFF2A2A3A), RoundedCornerShape(14.dp))
                                .clickable { if (checked) selected.remove(row.file.uri) else selected[row.file.uri] = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            NeonCheckbox(checked = checked, onCheckedChange = {
                                if (it) selected[row.file.uri] = true else selected.remove(row.file.uri)
                            })
                            FileThumb(file = row.file, size = 46.dp)
                            Column(Modifier.weight(1f)) {
                                Text(row.file.name, style = MaterialTheme.typography.bodyMedium,
                                    color = if (checked) TextPrimary else TextSecondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                        .background(NeonViolet.copy(0.14f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                        Text(row.reason, style = MaterialTheme.typography.labelSmall, color = NeonViolet)
                                    }
                                    if (row.tag != null) {
                                        Text("AI sees: ${row.tag}", style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (row.file.sizeBytes > 0L) {
                                Text(row.file.sizeBytes.toReadableSize(), style = MaterialTheme.typography.labelMedium,
                                    color = if (checked) ErrorRed else TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    DeleteConfirmDialog(
        visible = showConfirm, count = chosen.size, bytes = chosenBytes,
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
private fun AiLoading(done: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = NeonViolet, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text("Analyzing your photos with on-device AI…",
            style = MaterialTheme.typography.titleMedium, color = TextPrimary,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (total > 0) "$done / $total" else "Starting…",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(16.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = NeonViolet, trackColor = CyberCard,
            )
        } else {
            CircularProgressIndicator(color = NeonViolet, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Runs entirely on your device — no internet, ever.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun Centered(title: String, body: String, tint: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = tint, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
