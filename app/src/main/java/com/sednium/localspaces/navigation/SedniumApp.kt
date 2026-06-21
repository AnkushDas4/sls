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
import androidx.compose.foundation.layout.fillMaxWidth
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.ChatSession
import com.sednium.localspaces.ui.components.ImageViewerOverlay
import com.sednium.localspaces.ui.screens.ChatListScreen
import com.sednium.localspaces.ui.screens.ChatScreen
import com.sednium.localspaces.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

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

    val currentChat = chats.find { it.id == currentChatId } ?: chats.firstOrNull()
    val isConfigValid = settings.model.isNotBlank()

    val context = androidx.compose.ui.platform.LocalContext.current
    val contentResolver = context.contentResolver
    
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
                drawerContainerColor = com.sednium.localspaces.ui.theme.SedniumColors.SedYellow
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

    if (isSettingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { isSettingsOpen = false },
            sheetState = sheetState,
            containerColor = com.sednium.localspaces.ui.theme.SedniumColors.SedYellow
        ) {
            SettingsScreen(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onClose = { isSettingsOpen = false }
            )
        }
    }
}
