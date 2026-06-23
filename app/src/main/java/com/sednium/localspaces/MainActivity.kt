package com.sednium.localspaces

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import com.sednium.localspaces.api.generateContentStream

/**
 * Single-activity host, mirroring the SPA shell index.tsx mounted into.
 * Replace the in-memory `remember { mutableStateOf(...) }` blocks with a
 * SedniumViewModel backed by DataStore (settings) + Room (chat sessions)
 * for true parity with the original's `localStorage` persistence.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = StorageHelper(this)
        val initialSettings = storage.loadSettings()
        val initialChats = storage.loadChats().ifEmpty {
            listOf(
                ChatSession(
                    id = System.currentTimeMillis().toString(),
                    title = "New Chat",
                    messages = emptyList(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        enableEdgeToEdge()
        setContent {
            SedniumTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var settings by remember { mutableStateOf(initialSettings) }
                    var chats by remember { mutableStateOf(initialChats) }
                    var currentChatId by remember { mutableStateOf(chats.firstOrNull()?.id ?: System.currentTimeMillis().toString()) }
                    var isLoading by remember { mutableStateOf(false) }

                    LaunchedEffect(settings) {
                        storage.saveSettings(settings)
                    }

                    LaunchedEffect(chats) {
                        storage.saveChats(chats)
                    }

                    val mcpServerManager = remember { com.sednium.localspaces.mcp.McpServerManager() }

                    SedniumApp(
                        chats = chats,
                        currentChatId = currentChatId,
                        settings = settings,
                        mcpServerManager = mcpServerManager,
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
                            val modelMsgId = (System.currentTimeMillis() + 1).toString()
                            val initialModelMsg = ChatMessage(
                                id = modelMsgId,
                                role = Role.MODEL,
                                content = "",
                                modelName = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.displayName ?: settings.model,
                                isThinking = false
                            )
                            
                            val activeChatHistory = chats.find { it.id == currentChatId }?.messages ?: emptyList()
                            
                            chats = chats.map {
                                if (it.id == currentChatId) it.copy(
                                    messages = it.messages + userMsg + initialModelMsg,
                                    updatedAt = System.currentTimeMillis()
                                )
                                else it
                            }

                            isLoading = true
                            scope.launch {
                                try {
                                    val apiKey = com.sednium.localspaces.ui.screens.apiKeyFor(settings)
                                    if (apiKey.isBlank()) {
                                        throw Exception("API Key is missing. Please add it in settings.")
                                    }
                                    generateContentStream(
                                        apiKey = apiKey,
                                        modelName = settings.model,
                                        prompt = text,
                                        history = activeChatHistory,
                                        provider = settings.provider,
                                        baseUrl = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.defaultUrl ?: "",
                                        onChunkReceived = { deltaText, _ ->
                                            chats = chats.map { chat ->
                                                if (chat.id == currentChatId) {
                                                    val newMessages = chat.messages.map { msg ->
                                                        if (msg.id == modelMsgId) msg.copy(content = msg.content + deltaText) else msg
                                                    }
                                                    chat.copy(messages = newMessages, updatedAt = System.currentTimeMillis())
                                                } else chat
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    chats = chats.map { chat ->
                                        if (chat.id == currentChatId) {
                                            val newMessages = chat.messages.map { msg ->
                                                if (msg.id == modelMsgId) msg.copy(content = msg.content + "\nError: ${e.message}", isError = true) else msg
                                            }
                                            chat.copy(messages = newMessages)
                                        } else chat
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}
