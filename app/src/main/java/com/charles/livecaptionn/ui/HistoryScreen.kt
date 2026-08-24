package com.charles.livecaptionn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charles.livecaptionn.data.TranscriptEntry
import com.charles.livecaptionn.data.TranscriptExport
import com.charles.livecaptionn.data.TranscriptHistoryStore
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyStore: TranscriptHistoryStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val t = LocalUiStrings.current
    var entries by remember { mutableStateOf<List<TranscriptEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TranscriptEntry?>(null) }

    LaunchedEffect(Unit) {
        entries = historyStore.getAll()
    }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else entries.filter { entry ->
            entry.originalText.contains(searchQuery, ignoreCase = true) ||
                entry.translatedText.contains(searchQuery, ignoreCase = true)
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t["Delete entry"]) },
            text = {
                Text(t.format("Delete this transcript entry?\n\n\"%s\"", deleteTarget?.originalText?.take(60)))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget ?: return@TextButton
                    scope.launch {
                        historyStore.deleteById(target.id)
                        entries = historyStore.getAll()
                    }
                    deleteTarget = null
                }) {
                    Text(t["Delete"])
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(t["Cancel"])
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(t["Transcript History"]) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t["Back"])
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, TranscriptExport(entries).asText())
                            }
                            context.startActivity(Intent.createChooser(share, t["Share transcripts"]))
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = t["Share transcripts"])
                        }
                        TextButton(onClick = {
                            scope.launch {
                                historyStore.clear()
                                entries = emptyList()
                            }
                        }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t["Clear"])
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (entries.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(t["Search transcripts…"]) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = t["Clear search"], modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (filteredEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) t["No matching transcripts"]
                        else t["No transcripts yet"],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (searchQuery.isNotBlank()) t["Try a different search term."]
                        else t["Transcripts will appear here as you use captioning."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TranscriptCard(
                            entry = entry,
                            onDelete = { deleteTarget = entry }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    entry: TranscriptEntry,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val t = LocalUiStrings.current
    val timeStr = remember(entry.timestamp) {
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        fmt.format(Date(entry.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entry.sourceLanguage.uppercase()} \u2192 ${entry.targetLanguage.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val data = ClipData.newPlainText(
                                "transcript",
                                "${entry.originalText}\n${entry.translatedText}"
                            )
                            clip.setPrimaryClip(data)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = t["Copy"],
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = t["Delete"],
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = entry.originalText,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.translatedText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
