package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

data class ModelPreset(
    val name: String,
    val description: String,
    val sizeMB: Int,
    val url: String,
    val filename: String
)

val PRESET_MODELS = listOf(
    ModelPreset(
        "TinyLlama 1.1B",
        "Extremely fast and lightweight. Good for basic tasks.",
        680,
        "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf?download=true",
        "tinyllama-1.1b.gguf"
    ),
    ModelPreset(
        "Phi-2 2.7B",
        "High quality reasoning for its size (requires more RAM).",
        1600,
        "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf?download=true",
        "phi-2-q4.gguf"
    ),
    ModelPreset(
        "Llama-3 8B Instruct",
        "Highly capable. Requires a powerful device with 8GB+ RAM.",
        4700,
        "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf?download=true",
        "llama-3-8b.gguf"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelResourcesScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var customUrl by remember { mutableStateOf("") }
    var customFilename by remember { mutableStateOf("custom-model.gguf") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Hub & Downloader") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "Info", 
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Download models directly to your device. Once downloaded, select them in Settings. Mobile devices have limited RAM, so pick models under 2-3GB if possible.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                StorageCalculatorCard(context)
            }

            item {
                Text("Recommended Models", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            items(PRESET_MODELS) { preset ->
                ModelDownloadCard(context, preset)
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Custom Download", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Paste a direct link to a .gguf file from HuggingFace to download it.", style = MaterialTheme.typography.bodySmall)
                
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = { Text("Direct GGUF URL") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                OutlinedTextField(
                    value = customFilename,
                    onValueChange = { customFilename = it },
                    label = { Text("Save as filename") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                
                Button(
                    onClick = {
                        if (customUrl.isNotBlank()) {
                            downloadFile(context, customUrl, customFilename)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Download Custom Model")
                }
            }
        }
    }
}

@Composable
fun StorageCalculatorCard(context: Context) {
    var downloadedMB by remember { mutableLongStateOf(0L) }
    var availableMB by remember { mutableLongStateOf(0L) }
    var totalMB by remember { mutableLongStateOf(1L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            var bytes = 0L
            dir?.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gguf")) {
                    bytes += file.length()
                }
            }
            downloadedMB = bytes / (1024 * 1024)
            
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val stat = StatFs(targetDir.absolutePath)
            availableMB = stat.availableBytes / (1024 * 1024)
            totalMB = stat.totalBytes / (1024 * 1024)
            
            delay(2000)
        }
    }

    val usedRatio = if (totalMB > 0) ((totalMB - availableMB).toFloat() / totalMB.toFloat()).coerceIn(0f, 1f) else 0f

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Storage Usage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Models: ${downloadedMB} MB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Free: ${availableMB} MB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            
            LinearProgressIndicator(
                progress = { usedRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun ModelDownloadCard(context: Context, preset: ModelPreset) {
    var progress by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    var isDownloading by remember { mutableStateOf(false) }
    var isFileExists by remember { mutableStateOf(false) }
    var downloadId by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }

    LaunchedEffect(preset.filename) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), preset.filename)
        isFileExists = file.exists()
    }

    LaunchedEffect(isDownloading, downloadId) {
        if (isDownloading && downloadId != -1L) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (isActive && isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusColumn >= 0) cursor.getInt(statusColumn) else -1
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        isDownloading = false
                        isFileExists = true
                        progress = 1f
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        isDownloading = false
                        progress = -1f
                    } else {
                        val downloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        if (downloadedColumn >= 0 && totalColumn >= 0) {
                            val bytesDownloaded = cursor.getLong(downloadedColumn)
                            val bytesTotal = cursor.getLong(totalColumn)
                            if (bytesTotal > 0) {
                                progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            }
                        }
                    }
                    cursor.close()
                } else {
                    isDownloading = false
                }
                delay(1000)
            }
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(preset.description, style = MaterialTheme.typography.bodyMedium)
            Text("Size: ~${preset.sizeMB} MB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)

            if (isFileExists) {
                Text(
                    "Downloaded (${preset.filename})", 
                    color = MaterialTheme.colorScheme.primary, 
                    style = MaterialTheme.typography.labelLarge
                )
            } else if (isDownloading) {
                LinearProgressIndicator(
                    progress = { if (progress >= 0f) progress else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("Downloading... " + (if (progress >= 0) "${(progress * 100).toInt()}%" else "Starting"), style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    onClick = {
                        val id = downloadFile(context, preset.url, preset.filename)
                        if (id != -1L) {
                            downloadId = id
                            isDownloading = true
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }
            }
        }
    }
}

private fun downloadFile(context: Context, url: String, filename: String): Long {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("Downloading AI Model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    } catch (e: Exception) {
        e.printStackTrace()
        return -1L
    }
}
