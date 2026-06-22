package com.sednium.localspaces

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.ChatSession
import com.sednium.localspaces.model.Role
import com.sednium.localspaces.navigation.SedniumApp
import com.sednium.localspaces.ui.theme.SedniumTheme

/**
 * Single-activity host, mirroring the SPA shell index.tsx mounted into.
 * Replace the in-memory `remember { mutableStateOf(...) }` blocks with a
 * SedniumViewModel backed by DataStore (settings) + Room (chat sessions)
 * for true parity with the original's `localStorage` persistence.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SedniumTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var settings by remember { mutableStateOf(AppSettings()) }
                    var chats by remember {
                        mutableStateOf(
                            listOf(
                                ChatSession(
                                    id = System.currentTimeMillis().toString(),
                                    title = "New Chat",
                                    messages = emptyList(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        )
                    }
                    var currentChatId by remember { mutableStateOf(chats.first().id) }
                    var isLoading by remember { mutableStateOf(false) }

                    SedniumApp(
                        chats = chats,
                        currentChatId = currentChatId,
                        settings = settings,
                        onUpdateSettings = { settings = it },
                        onSelectChat = { id -> currentChatId = id },
                        onNewChat = {
                            val fresh = ChatSession(
                                id = System.currentTimeMillis().toString(),
                                title = "New Chat",
                                updatedAt = System.currentTimeMillis()
                            )
                            chats = listOf(fresh) + chats
                            currentChatId = fresh.id
                        },
                        onDeleteChat = { id ->
                            chats = chats.filterNot { it.id == id }
                            if (chats.isEmpty()) {
                                val fresh = ChatSession(System.currentTimeMillis().toString(), "New Chat", updatedAt = System.currentTimeMillis())
                                chats = listOf(fresh)
                            }
                            if (currentChatId == id) currentChatId = chats.first().id
                        },
                        onDeleteMultipleChats = { ids ->
                            chats = chats.filterNot { ids.contains(it.id) }
                            if (chats.isEmpty()) {
                                val fresh = ChatSession(System.currentTimeMillis().toString(), "New Chat", updatedAt = System.currentTimeMillis())
                                chats = listOf(fresh)
                            }
                            if (ids.contains(currentChatId)) currentChatId = chats.first().id
                        },
                        onRenameChat = { id, title ->
                            chats = chats.map { if (it.id == id) it.copy(title = title) else it }
                        },
                        onTogglePin = { id ->
                            chats = chats.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it }
                        },
                        onClearCurrentChat = {
                            chats = chats.map { if (it.id == currentChatId) it.copy(messages = emptyList()) else it }
                        },
                        onSend = { text, attachments ->
                            val userMsg = ChatMessage(
                                id = System.currentTimeMillis().toString(),
                                role = Role.USER,
                                content = text,
                                attachments = attachments
                            )
                            chats = chats.map {
                                if (it.id == currentChatId) it.copy(messages = it.messages + userMsg, updatedAt = System.currentTimeMillis())
                                else it
                            }
                            // Dispatch to your provider-specific streaming client here
                            // (Kotlin port of services/geminiService.ts), then append the
                            // streamed ChatMessage(role = Role.MODEL, ...) as chunks arrive.
                        },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}
