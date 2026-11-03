package com.chaptersplitter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope

/** Chapter Splitter: Material 3 screen. The splitting itself lives in SplitEngine.kt / SplitterState.kt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitterScreen(state: SplitterState, scope: CoroutineScope, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }

    val pickBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            state.bookUri = uri
            state.bookName = displayName(ctx, uri)
            state.finished = false
        }
    }
    val pickDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            state.outUri = uri
            state.outName = DocumentFile.fromTreeUri(ctx, uri)?.name ?: "Selected folder"
            state.finished = false
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Chapter Splitter") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back to menu") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.More, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("View log") },
                                leadingIcon = { Icon(AppIcons.Log, contentDescription = null) },
                                onClick = { menuOpen = false; logOpen = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy debug log") },
                                leadingIcon = { Icon(AppIcons.Copy, contentDescription = null) },
                                onClick = { menuOpen = false; copyDebugLog(ctx) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share debug log") },
                                leadingIcon = { Icon(AppIcons.Share, contentDescription = null) },
                                onClick = { menuOpen = false; shareDebugLog(ctx) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = {
            Surface(color = cs.background, tonalElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (!state.running && (state.bookUri == null || state.outUri == null)) {
                        Text(
                            "Choose an audiobook and an output folder to start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = { state.start(ctx, scope) },
                        enabled = state.canStart,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(AppIcons.Scissors, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            if (state.running) "Splitting…" else "Split chapters",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.running) {
                item(key = "progress") { ProgressCard(state) { logOpen = true } }
            } else if (state.finished) {
                item(key = "result") { ResultCard(state) { logOpen = true } }
            }
            item(key = "intro") {
                Text(
                    "Splits at the chapter marks inside the file (.m4b .m4a .mp3 .ogg .opus). " +
                        "Nothing is re-encoded, so it is fast and keeps full quality.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            item(key = "book") {
                StepCard(
                    step = 1, title = "Audiobook", icon = AppIcons.Audio,
                    value = state.bookName.ifEmpty { null }, placeholder = "No file selected",
                    action = if (state.bookName.isEmpty()) "Choose" else "Change",
                    enabled = !state.running,
                    onPick = { pickBook.launch(arrayOf("*/*")) }
                )
            }
            item(key = "folder") {
                StepCard(
                    step = 2, title = "Output folder", icon = AppIcons.Folder,
                    value = state.outName.ifEmpty { null }, placeholder = "No folder selected",
                    action = if (state.outName.isEmpty()) "Choose" else "Change",
                    enabled = !state.running,
                    onPick = { pickDir.launch(null) }
                )
            }
            if (state.log.isNotEmpty() && !state.running && !state.finished) {
                item(key = "logbtn") {
                    TextButton(onClick = { logOpen = true }) {
                        Icon(AppIcons.Log, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("View log")
                    }
                }
            }
        }
    }

    if (logOpen) LogSheet(state, onDismiss = { logOpen = false }, onCopy = { copyDebugLog(ctx) })
}

// ------------------------------------------------------------------ Pieces

@Composable
private fun StepCard(
    step: Int, title: String, icon: ImageVector,
    value: String?, placeholder: String, action: String,
    enabled: Boolean, onPick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                    Text(step.toString(), style = MaterialTheme.typography.labelLarge, color = cs.onPrimary)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Text(
                    value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value != null) cs.onSurface else cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = onPick, enabled = enabled) { Text(action) }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: SplitterState, onLog: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lines = state.status.split('\n').filter { it.isNotBlank() }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Splitting",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = cs.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = cs.primary,
                trackColor = cs.surface.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(14.dp))
            if (lines.isEmpty()) {
                Text("Getting ready…", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
            } else {
                Text(
                    lines.first(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = cs.onPrimaryContainer
                )
                lines.drop(1).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onLog, modifier = Modifier.padding(top = 4.dp)) { Text("View log") }
        }
    }
}

@Composable
private fun ResultCard(state: SplitterState, onLog: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ok = state.okCount
    val fail = state.failCount
    val good = ok > 0 && fail == 0
    val container = if (good) cs.primaryContainer else cs.errorContainer
    val onContainer = if (good) cs.onPrimaryContainer else cs.onErrorContainer
    val title = when {
        good -> "All done"
        ok > 0 -> "Finished with errors"
        else -> "Nothing was split"
    }
    val body = when {
        ok > 0 && fail == 0 -> "$ok chapter files were saved."
        ok > 0 -> "$ok chapter files saved, $fail failed."
        else -> "No chapters were found, or an error happened. Open the log for details."
    }
    val took = state.status.takeIf { it.startsWith("Finished") }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (good) AppIcons.Check else AppIcons.Alert, contentDescription = null, tint = onContainer)
                Text(title, style = MaterialTheme.typography.titleLarge, color = onContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = onContainer)
            if (took != null) Text(took, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.8f))
            if (state.outName.isNotEmpty() && ok > 0) {
                Text(
                    "In folder: ${state.outName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onLog, modifier = Modifier.padding(top = 4.dp)) { Text("View log") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(state: SplitterState, onDismiss: () -> Unit, onCopy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val list = rememberLazyListState()
    // follow the newest line while a split is running
    LaunchedEffect(state.log.size) {
        if (state.log.isNotEmpty()) list.animateScrollToItem(state.log.size - 1)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onCopy) { Text("Copy debug log") }
            }
            if (state.log.isEmpty()) {
                Text(
                    "The log appears here once a split starts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.log.toList()) { line ->
                        val bad = line.contains("FAILED") || line.startsWith("Error")
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                bad -> cs.error
                                line.trimStart().startsWith("↳") -> cs.onSurfaceVariant
                                else -> cs.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Debug log helpers

private fun copyDebugLog(ctx: Context) {
    val report = fullDebugReport().takeLast(200_000)
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("AuTools debug", report))
    Toast.makeText(ctx, "Debug log copied", Toast.LENGTH_SHORT).show()
}

private fun shareDebugLog(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "AuTools debug log")
        putExtra(Intent.EXTRA_TEXT, fullDebugReport().takeLast(200_000))
    }
    ctx.startActivity(Intent.createChooser(send, "Share debug log"))
}
