@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import com.sednium.localspaces.mcp.McpServerManager
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
    isLoading: Boolean,
    mcpServerManager: McpServerManager
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
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
                onAttachClick = { /* launch system file/photo picker, append result to `attachments` */ },
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
                // Plug in Coil here:
                // coil.compose.AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (isSettingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { isSettingsOpen = false },
            sheetState = sheetState
        ) {
            SettingsScreen(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onClose = { isSettingsOpen = false },
                mcpServerManager = mcpServerManager
            )
        }
    }
}
