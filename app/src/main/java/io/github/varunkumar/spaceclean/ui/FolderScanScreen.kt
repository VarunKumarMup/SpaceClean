package io.github.varunkumar.spaceclean.ui

import io.github.varunkumar.spaceclean.ui.theme.OutlineColor
import io.github.varunkumar.spaceclean.ui.theme.SurfaceVariant
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FolderEntry(val uri: Uri, val name: String, val sizeBytes: Long, val path: String)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderScanScreen(treeUri: Uri, navController: NavHostController, viewModel: ScanViewModel) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val folderName = remember(treeUri) { displayNameOf(treeUri) }

    // null = still scanning
    var entries by remember { mutableStateOf<List<FolderEntry>?>(null) }
    LaunchedEffectScan(treeUri, context) { entries = it }

    val selected = remember { mutableStateMapOf<Uri, Boolean>() }
    var showConfirm by remember { mutableStateOf(false) }
    var isDeleting  by remember { mutableStateOf(false) }

    val list          = entries.orEmpty()
    val selectedItems = list.filter { selected[it.uri] == true }
    val selectedBytes = selectedItems.sumOf { it.sizeBytes }

    Scaffold(
        modifier       = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor   = TextPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(folderName, style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entries != null) {
                            Text(
                                "${list.size} files · ${list.sumOf { it.sizeBytes }.toReadableSize()}",
                                style = MaterialTheme.typography.labelMedium, color = TextSecondary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.setCustomFolder(null)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = ElectricCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (selectedItems.isNotEmpty()) {
                Button(
                    onClick = { showConfirm = true },
                    enabled = !isDeleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Delete ${selectedItems.size} · ${selectedBytes.toReadableSize()}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        when {
            entries == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    Text("Scanning folder…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No files found in this folder.", color = TextMuted)
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.uri.toString() }) { entry ->
                    val checked = selected[entry.uri] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CyberCard)
                            .border(1.dp, if (checked) ElectricCyan.copy(0.45f) else OutlineColor, RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = { if (checked) selected.remove(entry.uri) else selected[entry.uri] = true },
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NeonCheckbox(checked = checked, onCheckedChange = {
                            if (it) selected[entry.uri] = true else selected.remove(entry.uri)
                        })
                        FileThumb(
                            file = ScannedFile(entry.uri, entry.name, entry.path, entry.sizeBytes, 0L),
                            size = 44.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium,
                                color = if (checked) TextPrimary else TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                            Text(entry.path, style = MaterialTheme.typography.labelSmall,
                                color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (entry.sizeBytes > 0L) {
                            Text(entry.sizeBytes.toReadableSize(), style = MaterialTheme.typography.labelMedium,
                                color = if (checked) ElectricCyan else TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        FolderDeleteDialog(
            count     = selectedItems.size,
            bytes     = selectedBytes,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                isDeleting  = true
                scope.launch {
                    val (deletedUris, freed) = withContext(Dispatchers.IO) {
                        var freedBytes = 0L
                        val gone = mutableListOf<Uri>()
                        selectedItems.forEach { e ->
                            val ok = runCatching {
                                DocumentsContract.deleteDocument(context.contentResolver, e.uri)
                            }.getOrDefault(false)
                            if (ok) { gone += e.uri; freedBytes += e.sizeBytes }
                        }
                        gone to freedBytes
                    }
                    entries = entries?.filterNot { it.uri in deletedUris.toHashSet() }
                    deletedUris.forEach { selected.remove(it) }
                    if (freed > 0L) viewModel.recordSpaceFreed(freed)
                    isDeleting = false
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderDeleteDialog(count: Int, bytes: Long, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CyberCard)
                .border(1.dp, ErrorRed.copy(0.35f), RoundedCornerShape(24.dp)).padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Delete $count ${if (count == 1) "file" else "files"}?",
                    style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Frees up ${bytes.toReadableSize()} · cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(SurfaceVariant).border(1.dp, OutlineColor, RoundedCornerShape(14.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(ErrorRed).clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SAF traversal (DocumentsContract — no extra dependency)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LaunchedEffectScan(treeUri: Uri, context: Context, onResult: (List<FolderEntry>) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(treeUri) {
        val result = withContext(Dispatchers.IO) { walkTree(context, treeUri) }
        onResult(result)
    }
}

private fun walkTree(context: Context, treeUri: Uri): List<FolderEntry> {
    val resolver = context.contentResolver
    val rootId   = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
    val out      = mutableListOf<FolderEntry>()

    fun recurse(docId: String, relPath: String, depth: Int) {
        if (depth > 8 || out.size > 1500) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id   = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    val size = if (!c.isNull(3)) c.getLong(3) else 0L
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        recurse(id, if (relPath.isEmpty()) name else "$relPath/$name", depth + 1)
                    } else {
                        out += FolderEntry(
                            uri       = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            name      = name,
                            sizeBytes = size,
                            path      = relPath.ifEmpty { "/" },
                        )
                    }
                }
            }
        }
    }

    recurse(rootId, "", 0)
    return out.sortedByDescending { it.sizeBytes }
}

private fun displayNameOf(treeUri: Uri): String {
    // tree uri docId looks like "primary:Download/Sub" — show the last path segment
    val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return "Folder"
    val after = docId.substringAfter(':', docId)
    return after.substringAfterLast('/').ifBlank { "Internal storage" }
}
