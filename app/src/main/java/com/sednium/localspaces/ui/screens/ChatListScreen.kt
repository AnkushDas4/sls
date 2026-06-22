package com.sednium.localspaces.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
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
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

import androidx.compose.foundation.layout.systemBarsPadding

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
    var chatToRename by remember { mutableStateOf<ChatSession?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val sortedChats = remember(chats) {
        chats.sortedWith(compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    val filteredChats = remember(sortedChats, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedChats
        } else {
            val query = searchQuery.lowercase()
            sortedChats.filter { chat ->
                chat.title.lowercase().contains(query) || 
                chat.messages.any { it.content.lowercase().contains(query) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Chat, contentDescription = null, tint = SedniumColors.Orange)
                Text("Chats", style = MaterialTheme.typography.titleLarge, color = SedniumColors.Orange)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    isSelectionMode = !isSelectionMode
                    selectedIds = emptySet()
                }) {
                    Text(if (isSelectionMode) "Cancel" else "Select", color = SedniumColors.Orange)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = SedniumColors.Orange)
                }
            }
        }

        // --- New Chat button ---
        Button(
            onClick = onNewChat,
            enabled = !isSelectionMode,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SedniumColors.Orange,
                contentColor = SedniumColors.Milk,
                disabledContainerColor = OrangeAlpha.a50,
                disabledContentColor = SedniumColors.Milk.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" New Chat", style = MaterialTheme.typography.labelLarge)
        }

        // --- Search Bar ---
        androidx.compose.material3.OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search chats...", style = MaterialTheme.typography.bodyMedium, color = SedniumColors.Gray500) },
            leadingIcon = { 
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = SedniumColors.Gray500) 
            },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = SedniumColors.Gray500)
                    }
                }
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SedniumColors.Orange,
                unfocusedBorderColor = SedniumColors.Gray700,
                focusedTextColor = SedniumColors.Gray100, // Fixed this to be visible in dark mode, or use Gray800 in light mode
                unfocusedTextColor = SedniumColors.Gray200,
                focusedContainerColor = SedniumColors.DarkSurfaceAlt,
                unfocusedContainerColor = SedniumColors.DarkSurfaceAlt
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // --- List ---
        LazyColumn(
            modifier = Modifier.weight(1f).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredChats, key = { it.id }) { chat ->
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
                    onRename = { 
                        chatToRename = chat
                        newTitle = chat.title
                    },
                    onDelete = { onDeleteChat(chat.id) }
                )
            }
        }

        if (chatToRename != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { chatToRename = null },
                title = { Text("Rename Chat", color = SedniumColors.Orange) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SedniumColors.Orange,
                            focusedTextColor = SedniumColors.White,
                            unfocusedTextColor = SedniumColors.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        chatToRename?.let { chat ->
                            onRenameChat(chat.id, newTitle)
                        }
                        chatToRename = null
                    }) {
                        Text("Save", color = SedniumColors.Orange)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToRename = null }) {
                        Text("Cancel", color = SedniumColors.Orange)
                    }
                },
                containerColor = SedniumColors.Milk
            )
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
