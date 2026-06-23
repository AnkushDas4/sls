package com.sednium.localspaces.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.ChatSession
import com.sednium.localspaces.model.ModelProvider
import com.sednium.localspaces.ui.components.ImageViewerOverlay
import com.sednium.localspaces.ui.screens.ChatListScreen
import com.sednium.localspaces.ui.screens.ChatScreen
import com.sednium.localspaces.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class LocalServerStatus {
    OFFLINE, IDLE, PROCESSING, UNKNOWN
}

/**
 * The root composable. Functionally equivalent to App.tsx: a single
 * always-mounted Chat page, with the Chat List sliding in from the left
 * (ModalNavigationDrawer == ChatListDrawer.tsx) and Settings presented as
 * a bottom sheet (ModalBottomSheet == SettingsDrawer.tsx), plus the
 * full-screen ImageViewerOverlay stacked on top of everything (z-[100]
 * in the original).
 *
 * State here is intentionally minimal/in-memory; wire a real
 * ViewModel + Room/DataStore-backed repository for persistence parity
 * with the original's localStorage-based `sednium_settings` /
 * `sednium_chats` keys.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SedniumApp(
    chats: List<ChatSession>,
    currentChatId: String,
    settings: AppSettings,
    mcpServerManager: com.sednium.localspaces.mcp.McpServerManager,
    onUpdateSettings: (AppSettings) -> Unit,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteMultipleChats: (List<String>) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onClearCurrentChat: () -> Unit,
    onSend: (String, List<Attachment>) -> Unit,
    isLoading: Boolean
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var isSettingsOpen by remember { mutableStateOf(false) }
    var isPresetMenuOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }
    var selectedImage by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf("") }
    var localServerStatus by remember { mutableStateOf(LocalServerStatus.UNKNOWN) }

    androidx.compose.runtime.LaunchedEffect(settings.provider, settings.localBaseUrl, isLoading) {
        if (settings.provider != ModelProvider.LOCAL) {
            localServerStatus = LocalServerStatus.UNKNOWN
            return@LaunchedEffect
        }
        if (isLoading) {
            localServerStatus = LocalServerStatus.PROCESSING
            return@LaunchedEffect
        }
        
        while (true) {
            localServerStatus = withContext(Dispatchers.IO) {
                try {
                    val baseUrl = settings.localBaseUrl.removeSuffix("/")
                    // Ollama uses /api/tags or /api/version, standard OpenAI uses /models.
                    // We can just try to connect to the baseUrl.
                    val urlStr = if (baseUrl.endsWith("/v1")) baseUrl.replace("/v1", "/") else baseUrl
                    val url = URL(urlStr)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.requestMethod = "GET"
                    val code = connection.responseCode
                    if (code in 200..404) LocalServerStatus.IDLE else LocalServerStatus.OFFLINE
                } catch (e: Exception) {
                    LocalServerStatus.OFFLINE
                }
            }
            delay(5000)
        }
    }

    val currentChat = chats.find { it.id == currentChatId } ?: chats.firstOrNull()
    val isConfigValid = settings.model.isNotBlank()

    val context = androidx.compose.ui.platform.LocalContext.current
    val contentResolver = context.contentResolver
    
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(exportText.toByteArray())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val newAttachments = uris.mapNotNull { uri ->
            val typeStr = contentResolver.getType(uri) ?: "application/octet-stream"
            val type = if (typeStr.startsWith("image/")) com.sednium.localspaces.model.AttachmentType.IMAGE else com.sednium.localspaces.model.AttachmentType.TEXT
            var name = "attachment"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
            
            Attachment(type = type, mimeType = typeStr, data = uri.toString(), name = name)
        }
        attachments = attachments + newAttachments
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = com.sednium.localspaces.ui.theme.SedniumColors.Milk
            ) {
                ChatListScreen(
                    chats = chats,
                    currentChatId = currentChatId,
                    onSelectChat = { id -> onSelectChat(id); scope.launch { drawerState.close() } },
                    onNewChat = { onNewChat(); scope.launch { drawerState.close() } },
                    onClose = { scope.launch { drawerState.close() } },
                    onDeleteChat = onDeleteChat,
                    onDeleteMultiple = onDeleteMultipleChats,
                    onRenameChat = onRenameChat,
                    onTogglePin = onTogglePin
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChatScreen(
                chatTitle = currentChat?.title ?: "Sednium AI",
                settings = settings,
                localServerStatus = localServerStatus,
                messages = currentChat?.messages ?: emptyList(),
                isLoading = isLoading,
                isConfigValid = isConfigValid,
                input = input,
                attachments = attachments,
                isPresetMenuOpen = isPresetMenuOpen,
                onInputChange = { input = it },
                onSend = {
                    if (input.isNotBlank() || attachments.isNotEmpty()) {
                        onSend(input, attachments)
                        input = ""
                        attachments = emptyList()
                    }
                },
                onAttachClick = { pickerLauncher.launch("*/*") },
                onRemoveAttachment = { idx -> attachments = attachments.toMutableList().also { it.removeAt(idx) } },
                onTogglePresetMenu = { isPresetMenuOpen = !isPresetMenuOpen },
                onSelectPreset = { preset ->
                    onUpdateSettings(
                        settings.copy(
                            provider = preset.provider,
                            model = preset.model,
                            chatMode = preset.chatMode,
                            systemInstruction = preset.systemInstruction,
                            activePresetId = preset.id
                        )
                    )
                    isPresetMenuOpen = false
                },
                onMenuClick = { scope.launch { drawerState.open() } },
                onExportClick = {
                    val chat = currentChat
                    if (chat != null) {
                        exportText = chat.messages.joinToString("\n\n") { msg ->
                            val roleName = if (msg.role == com.sednium.localspaces.model.Role.USER) "You" else "Sednium AI"
                            "[$roleName]\n${msg.content}"
                        }
                        val safeTitle = chat.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20).ifBlank { "chat" }
                        val filename = "sednium_export_${safeTitle}.txt"
                        exportLauncher.launch(filename)
                    }
                },
                onClearClick = onClearCurrentChat,
                onSettingsClick = { isSettingsOpen = true },
                onImageClick = { url -> selectedImage = url }
            )

            ImageViewerOverlay(
                imageUrl = selectedImage,
                onDismiss = { selectedImage = null }
            ) { url ->
                coil.compose.AsyncImage(
                    model = url, 
                    contentDescription = null, 
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }

    var showMcpServers by remember { mutableStateOf(false) }
    var showAddMcpServer by remember { mutableStateOf(false) }

    if (isSettingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { isSettingsOpen = false },
            sheetState = sheetState,
            containerColor = com.sednium.localspaces.ui.theme.SedniumColors.Milk,
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            SettingsScreen(
                settings = settings,
                localServerStatus = localServerStatus,
                mcpServerManager = mcpServerManager,
                onOpenMcpServers = { showMcpServers = true },
                onUpdateSettings = onUpdateSettings,
                onClose = { 
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            isSettingsOpen = false
                        }
                    }
                }
            )
        }
    }

    if (showMcpServers) {
        val mcpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMcpServers = false },
            sheetState = mcpSheetState,
            containerColor = com.sednium.localspaces.ui.theme.SedniumColors.SedYellow,
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            com.sednium.localspaces.ui.screens.McpServersScreen(
                mcpServerManager = mcpServerManager,
                configs = settings.mcpServers,
                onAddClick = { showAddMcpServer = true },
                onRemove = { serverId ->
                    onUpdateSettings(settings.copy(mcpServers = settings.mcpServers.filter { it.id != serverId }))
                    mcpServerManager.disconnect(serverId)
                },
                onReconnectAll = { mcpServerManager.reconnectAll(settings.mcpServers) },
                onClose = {
                    scope.launch { mcpSheetState.hide() }.invokeOnCompletion {
                        if (!mcpSheetState.isVisible) {
                            showMcpServers = false
                        }
                    }
                }
            )
        }
    }

    if (showAddMcpServer) {
        com.sednium.localspaces.ui.components.AddMcpServerDialog(
            onDismiss = { showAddMcpServer = false },
            onAdd = { name, url, authToken ->
                val newConfig = com.sednium.localspaces.model.MCPConfig(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    authToken = authToken
                )
                onUpdateSettings(settings.copy(mcpServers = settings.mcpServers + newConfig))
                mcpServerManager.connect(newConfig)
                showAddMcpServer = false
            }
        )
    }
}
