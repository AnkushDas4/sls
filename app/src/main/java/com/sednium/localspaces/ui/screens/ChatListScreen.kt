package com.sednium.localspaces.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.model.ChatSession
import com.sednium.localspaces.ui.components.ChatListRow
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * PAGE 2 / 4 — Chat List Screen.
 * Slides in from the left (mirrors ChatListDrawer.tsx, which used
 * `translate-x-0` / `-translate-x-full` over 300ms). Host this inside a
 * ModalNavigationDrawer / ModalDrawerSheet from Material3.
 */
@Composable
fun ChatListScreen(
    chats: List<ChatSession>,
    currentChatId: String,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onClose: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteMultiple: (List<String>) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val sortedChats = remember(chats) {
        chats.sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    Column(modifier = Modifier.fillMaxSize().background(SedniumColors.SedYellow)) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Chat, contentDescription = null, tint = SedniumColors.SedRed)
                Text("Chats", style = MaterialTheme.typography.titleLarge, color = SedniumColors.SedRed)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    isSelectionMode = !isSelectionMode
                    selectedIds = emptySet()
                }) {
                    Text(if (isSelectionMode) "Cancel" else "Select", color = SedniumColors.SedRed)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = SedniumColors.SedRed)
                }
            }
        }

        // --- New Chat button ---
        Button(
            onClick = onNewChat,
            enabled = !isSelectionMode,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SedniumColors.SedRed,
                contentColor = SedniumColors.SedYellow,
                disabledContainerColor = SedRedAlpha.a50,
                disabledContentColor = SedniumColors.SedYellow.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" New Chat", style = MaterialTheme.typography.labelLarge)
        }

        // --- List ---
        LazyColumn(
            modifier = Modifier.weight(1f).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sortedChats, key = { it.id }) { chat ->
                ChatListRow(
                    chat = chat,
                    isCurrent = chat.id == currentChatId,
                    isSelectionMode = isSelectionMode,
                    isChecked = selectedIds.contains(chat.id),
                    onClick = {
                        if (isSelectionMode) {
                            selectedIds = if (selectedIds.contains(chat.id)) selectedIds - chat.id else selectedIds + chat.id
                        } else {
                            onSelectChat(chat.id)
                        }
                    },
                    onTogglePin = { onTogglePin(chat.id) },
                    onRename = { /* surface a text-edit affordance per chat.id, then onRenameChat(id, newTitle) */ },
                    onDelete = { onDeleteChat(chat.id) }
                )
            }
        }

        // --- Delete selected bar ---
        AnimatedVisibility(visible = isSelectionMode && selectedIds.isNotEmpty(), enter = slideInVertically { it }) {
            Button(
                onClick = {
                    onDeleteMultiple(selectedIds.toList())
                    selectedIds = emptySet()
                    isSelectionMode = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = SedniumColors.Red600, contentColor = SedniumColors.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(" Delete Selected (${selectedIds.size})", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
