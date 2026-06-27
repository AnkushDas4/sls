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
import com.sednium.localspaces.model.ModelProvider
import com.sednium.localspaces.model.Role
import com.sednium.localspaces.model.ToolCallState
import com.sednium.localspaces.mcp.*
import com.sednium.localspaces.navigation.SedniumApp
import com.sednium.localspaces.ui.theme.SedniumTheme
import kotlinx.coroutines.launch
import com.sednium.localspaces.api.generateContentStream

/**
 * Runs one full agentic tool-calling round for a user message: builds the
 * right ToolCallingChatClient for the active provider, runs it through
 * ToolCallOrchestrator (send turn -> execute any tool calls via MCP -> feed
 * results back -> repeat, up to ToolCallPolicy.maxIterations), and reports
 * live per-call status via onToolCallsUpdated so the existing
 * ToolActivityView UI lights up exactly the way it already expected to.
 *
 * Only the CURRENT user message's tool-call round-trip gets fully structured
 * history (tool_use/tool_result preserved exactly as each provider needs).
 * Earlier turns from previous messages in this same chat are passed in as
 * plain User/Assistant text — a deliberate simplification, since
 * ChatMessage itself doesn't persist structured tool-call data across app
 * restarts. This doesn't affect correctness of the round Trip happening
 * right now, only whether a model can see the exact tool calls it made many
 * messages ago (it can still see the text content of what happened).
 */
private suspend fun runAgenticTurn(
    mcpServerManager: McpServerManager,
    provider: ModelProvider,
    apiKey: String,
    modelName: String,
    baseUrl: String,
    systemInstruction: String,
    temperature: Float,
    topP: Float,
    topK: Int,
    maxTokens: Int,
    userMessage: String,
    priorHistory: List<ChatMessage>,
    onToolCallsUpdated: (List<ToolCallState>) -> Unit
): String {
    val llmClient: ToolCallingChatClient = when (provider) {
        ModelProvider.GOOGLE -> GeminiToolChatClient(
            apiKey = apiKey, modelName = modelName, systemInstruction = systemInstruction,
            temperature = temperature, topP = topP, topK = topK, maxTokens = maxTokens
        )
        ModelProvider.ANTHROPIC -> AnthropicToolChatClient(
            apiKey = apiKey, modelName = modelName, baseUrl = baseUrl, systemInstruction = systemInstruction,
            temperature = temperature, topP = topP, topK = topK, maxTokens = maxTokens
        )
        else -> OpenAiCompatToolChatClient(
            apiKey = apiKey, modelName = modelName, baseUrl = baseUrl, systemInstruction = systemInstruction,
            temperature = temperature, topP = topP, maxTokens = maxTokens,
            isOpenRouter = provider == ModelProvider.OPENROUTER
        )
    }

    val toolCallStates = linkedMapOf<String, ToolCallState>()

    val orchestrator = ToolCallOrchestrator(
        mcpServers = mcpServerManager,
        llm = llmClient,
        // No confirmation dialog exists yet for destructive tools (see
        // README for this batch) — turning this off explicitly rather than
        // leaving a flag on that looks like it gates something but doesn't,
        // since the orchestrator's default confirmDestructive lambda
        // auto-approves regardless.
        policy = ToolCallPolicy(confirmDestructiveCalls = false),
        onEvent = { event ->
            val update: Pair<String, ToolCallState>? = when (event) {
                is ToolCallEvent.Started -> event.call.callId to ToolCallState(event.call.qualifiedName.substringAfter("::"), isExecuting = true, success = false)
                is ToolCallEvent.Retrying -> event.call.callId to ToolCallState("${event.call.qualifiedName.substringAfter("::")} (retrying)", isExecuting = true, success = false)
                is ToolCallEvent.Succeeded -> event.call.callId to ToolCallState(event.call.qualifiedName.substringAfter("::"), isExecuting = false, success = true)
                is ToolCallEvent.Failed -> event.call.callId to ToolCallState(event.call.qualifiedName.substringAfter("::"), isExecuting = false, success = false)
                is ToolCallEvent.Declined -> event.call.callId to ToolCallState("${event.call.qualifiedName.substringAfter("::")} (declined)", isExecuting = false, success = false)
                else -> null
            }
            if (update != null) {
                toolCallStates[update.first] = update.second
                onToolCallsUpdated(toolCallStates.values.toList())
            }
        }
    )

    val priorTurns: List<LlmChatTurn> = priorHistory.map { msg ->
        if (msg.role == Role.USER) LlmChatTurn.User(msg.content) else LlmChatTurn.Assistant(msg.content)
    }

    return orchestrator.run(userMessage, priorTurns)
}

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
                    var showPromptLab by remember { mutableStateOf(false) }
                    var promptLabOutput by remember { mutableStateOf("") }
                    var promptLabRunning by remember { mutableStateOf(false) }

                    LaunchedEffect(settings) {
                        storage.saveSettings(settings)
                    }

                    LaunchedEffect(chats) {
                        storage.saveChats(chats)
                    }

                    val mcpServerManager = remember { com.sednium.localspaces.mcp.McpServerManager() }

                    LaunchedEffect(Unit) {
                        // Settings (including mcpServers) are already loaded from
                        // SharedPreferences via StorageHelper above; the manager
                        // itself is purely in-memory, so without this the servers
                        // the user previously added would show as "Error" / blank
                        // until they manually hit Reconnect All.
                        if (initialSettings.mcpServers.isNotEmpty()) {
                            mcpServerManager.connectSavedServers(initialSettings.mcpServers)
                        }
                    }

                    if (showPromptLab) {
                        com.sednium.localspaces.ui.screens.PromptLabScreen(
                            isRunning = promptLabRunning,
                            output = promptLabOutput,
                            isDark = settings.theme == com.sednium.localspaces.model.AppTheme.DARK,
                            onBack = { showPromptLab = false; promptLabOutput = "" },
                            onRun = { tool, toolInput, toneInstruction ->
                                promptLabOutput = ""
                                promptLabRunning = true
                                scope.launch {
                                    try {
                                        val apiKey = com.sednium.localspaces.ui.screens.apiKeyFor(settings)
                                        if (apiKey.isBlank()) {
                                            promptLabOutput = "Error: API Key is missing. Please add it in settings."
                                            return@launch
                                        }
                                        val effectiveSystemPrompt = if (toneInstruction != null) {
                                            "${tool.systemPrompt}\n\n$toneInstruction"
                                        } else {
                                            tool.systemPrompt
                                        }
                                        generateContentStream(
                                            apiKey = apiKey,
                                            modelName = settings.model,
                                            prompt = toolInput,
                                            history = emptyList(),
                                            provider = settings.provider,
                                            baseUrl = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.defaultUrl ?: "",
                                            systemInstruction = effectiveSystemPrompt,
                                            temperature = settings.temperature,
                                            topP = settings.topP,
                                            topK = settings.topK,
                                            maxTokens = settings.maxTokens,
                                            onChunkReceived = { deltaText, _ ->
                                                promptLabOutput += deltaText
                                            }
                                        )
                                    } catch (e: Exception) {
                                        promptLabOutput += "\nError: ${e.message}"
                                    } finally {
                                        promptLabRunning = false
                                    }
                                }
                            },
                            onSendToChat = { tool, toolInput, toolOutput ->
                                val now = System.currentTimeMillis()
                                val fresh = ChatSession(
                                    id = now.toString(),
                                    title = "${tool.label}: ${toolInput.take(30)}",
                                    messages = listOf(
                                        ChatMessage(id = now.toString() + "_u", role = Role.USER, content = toolInput),
                                        ChatMessage(
                                            id = (now + 1).toString() + "_m",
                                            role = Role.MODEL,
                                            content = toolOutput,
                                            modelName = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.displayName ?: settings.model
                                        )
                                    ),
                                    updatedAt = now
                                )
                                chats = listOf(fresh) + chats
                                currentChatId = fresh.id
                                showPromptLab = false
                                promptLabOutput = ""
                            }
                        )
                    } else {
                    SedniumApp(
                        chats = chats,
                        currentChatId = currentChatId,
                        settings = settings,
                        mcpServerManager = mcpServerManager,
                        onUpdateSettings = { settings = it },
                        onUpdateSessionConfig = { updatedSession ->
                            chats = chats.map { if (it.id == updatedSession.id) updatedSession else it }
                        },
                        onOpenPromptLab = { showPromptLab = true },
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
                        onRetry = {
                            val chat = chats.find { it.id == currentChatId } ?: return@SedniumApp
                            if (chat.messages.isEmpty()) return@SedniumApp
                            
                            // Remove the last model message(s) if any
                            var newMessages = chat.messages
                            while (newMessages.isNotEmpty() && newMessages.last().role == Role.MODEL) {
                                newMessages = newMessages.dropLast(1)
                            }
                            
                            val lastUserMsg = newMessages.lastOrNull { it.role == Role.USER }
                            if (lastUserMsg == null) return@SedniumApp
                            
                            // Remove the last user msg from the history to be sent as the new prompt
                            val historyWithoutLastUser = newMessages.dropLast(1)
                            
                            val modelMsgId = (System.currentTimeMillis() + 1).toString()
                            val initialModelMsg = ChatMessage(
                                id = modelMsgId,
                                role = Role.MODEL,
                                content = "",
                                modelName = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.displayName ?: settings.model,
                                isThinking = false
                            )
                            
                            chats = chats.map {
                                if (it.id == currentChatId) it.copy(
                                    messages = newMessages + initialModelMsg,
                                    updatedAt = System.currentTimeMillis()
                                )
                                else it
                            }

                            isLoading = true
                            scope.launch {
                                val startTime = System.currentTimeMillis()
                                var firstTokenTime: Long? = null
                                try {
                                    val apiKey = com.sednium.localspaces.ui.screens.apiKeyFor(settings)
                                    if (apiKey.isBlank()) throw Exception("API Key is missing.")
                                    val effectiveSystemInstruction = chat.systemInstructionOverride ?: settings.systemInstruction
                                    val effectiveTemp = chat.temperatureOverride ?: settings.temperature
                                    val effectiveTopP = chat.topPOverride ?: settings.topP
                                    val effectiveTopK = chat.topKOverride ?: settings.topK
                                    val effectiveMaxTokens = chat.maxTokensOverride ?: settings.maxTokens
                                    val resolvedBaseUrl = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.defaultUrl ?: ""

                                    if (settings.enableTools && mcpServerManager.availableTools.isNotEmpty()) {
                                        val finalText = runAgenticTurn(
                                            mcpServerManager = mcpServerManager,
                                            provider = settings.provider,
                                            apiKey = apiKey,
                                            modelName = settings.model,
                                            baseUrl = resolvedBaseUrl,
                                            systemInstruction = effectiveSystemInstruction,
                                            temperature = effectiveTemp,
                                            topP = effectiveTopP,
                                            topK = effectiveTopK,
                                            maxTokens = effectiveMaxTokens,
                                            userMessage = lastUserMsg.content,
                                            priorHistory = historyWithoutLastUser,
                                            onToolCallsUpdated = { states ->
                                                chats = chats.map { chat ->
                                                    if (chat.id == currentChatId) {
                                                        chat.copy(messages = chat.messages.map { msg ->
                                                            if (msg.id == modelMsgId) msg.copy(toolCalls = states) else msg
                                                        })
                                                    } else chat
                                                }
                                            }
                                        )
                                        firstTokenTime = System.currentTimeMillis()
                                        chats = chats.map { chat ->
                                            if (chat.id == currentChatId) {
                                                chat.copy(
                                                    messages = chat.messages.map { msg ->
                                                        if (msg.id == modelMsgId) msg.copy(content = finalText) else msg
                                                    },
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            } else chat
                                        }
                                    } else {
                                        generateContentStream(
                                            apiKey = apiKey,
                                            modelName = settings.model,
                                            prompt = lastUserMsg.content,
                                            history = historyWithoutLastUser,
                                            provider = settings.provider,
                                            baseUrl = resolvedBaseUrl,
                                            systemInstruction = effectiveSystemInstruction,
                                            temperature = effectiveTemp,
                                            topP = effectiveTopP,
                                            topK = effectiveTopK,
                                            maxTokens = effectiveMaxTokens,
                                            attachments = lastUserMsg.attachments,
                                            onChunkReceived = { deltaText, _ ->
                                            if (firstTokenTime == null && deltaText.isNotEmpty()) {
                                                firstTokenTime = System.currentTimeMillis()
                                            }
                                            chats = chats.map { chat ->
                                                if (chat.id == currentChatId) {
                                                    val updatedMessages = chat.messages.map { msg ->
                                                        if (msg.id == modelMsgId) msg.copy(content = msg.content + deltaText) else msg
                                                    }
                                                    chat.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
                                                } else chat
                                            }
                                        }
                                    )
                                    }
                                    val endTime = System.currentTimeMillis()
                                    val ttft = firstTokenTime ?: endTime
                                    val latency = ttft - startTime
                                    val decodeMs = (endTime - ttft).coerceAtLeast(1)
                                    chats = chats.map { chat ->
                                        if (chat.id == currentChatId) {
                                            val updatedMessages = chat.messages.map { msg ->
                                                if (msg.id == modelMsgId) {
                                                    val approxTokens = msg.content.length / 4.0
                                                    msg.copy(
                                                        latencyMs = latency,
                                                        tokensPerSecond = (approxTokens / (decodeMs / 1000.0)).toFloat()
                                                    )
                                                } else msg
                                            }
                                            chat.copy(messages = updatedMessages)
                                        } else chat
                                    }
                                } catch (e: Exception) {
                                    chats = chats.map { chat ->
                                        if (chat.id == currentChatId) {
                                            val updatedMessages = chat.messages.map { msg ->
                                                if (msg.id == modelMsgId) msg.copy(content = "Error: ${e.message}", isError = true) else msg
                                            }
                                            chat.copy(messages = updatedMessages)
                                        } else chat
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
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
                            
                            val currentChatSession = chats.find { it.id == currentChatId }
                            val activeChatHistory = currentChatSession?.messages ?: emptyList()
                            
                            chats = chats.map {
                                if (it.id == currentChatId) it.copy(
                                    messages = it.messages + userMsg + initialModelMsg,
                                    updatedAt = System.currentTimeMillis()
                                )
                                else it
                            }

                            isLoading = true
                            scope.launch {
                                val startTime = System.currentTimeMillis()
                                var firstTokenTime: Long? = null
                                try {
                                    val apiKey = com.sednium.localspaces.ui.screens.apiKeyFor(settings)
                                    if (apiKey.isBlank()) {
                                        throw Exception("API Key is missing. Please add it in settings.")
                                    }
                                    val effectiveSystemInstruction = currentChatSession?.systemInstructionOverride ?: settings.systemInstruction
                                    val effectiveTemp = currentChatSession?.temperatureOverride ?: settings.temperature
                                    val effectiveTopP = currentChatSession?.topPOverride ?: settings.topP
                                    val effectiveTopK = currentChatSession?.topKOverride ?: settings.topK
                                    val effectiveMaxTokens = currentChatSession?.maxTokensOverride ?: settings.maxTokens
                                    val resolvedBaseUrl = com.sednium.localspaces.model.PROVIDER_CONFIG[settings.provider]?.defaultUrl ?: ""

                                    if (settings.enableTools && mcpServerManager.availableTools.isNotEmpty()) {
                                        // --- Agentic tool-calling path ---
                                        // Non-streaming per turn (the orchestrator needs a
                                        // complete response to check for tool calls), but tool
                                        // execution progress still streams live into the
                                        // existing ToolActivityView via toolCalls updates.
                                        val finalText = runAgenticTurn(
                                            mcpServerManager = mcpServerManager,
                                            provider = settings.provider,
                                            apiKey = apiKey,
                                            modelName = settings.model,
                                            baseUrl = resolvedBaseUrl,
                                            systemInstruction = effectiveSystemInstruction,
                                            temperature = effectiveTemp,
                                            topP = effectiveTopP,
                                            topK = effectiveTopK,
                                            maxTokens = effectiveMaxTokens,
                                            userMessage = text,
                                            priorHistory = activeChatHistory,
                                            onToolCallsUpdated = { states ->
                                                chats = chats.map { chat ->
                                                    if (chat.id == currentChatId) {
                                                        chat.copy(messages = chat.messages.map { msg ->
                                                            if (msg.id == modelMsgId) msg.copy(toolCalls = states) else msg
                                                        })
                                                    } else chat
                                                }
                                            }
                                        )
                                        firstTokenTime = System.currentTimeMillis()
                                        chats = chats.map { chat ->
                                            if (chat.id == currentChatId) {
                                                chat.copy(
                                                    messages = chat.messages.map { msg ->
                                                        if (msg.id == modelMsgId) msg.copy(content = finalText) else msg
                                                    },
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            } else chat
                                        }
                                    } else {
                                        generateContentStream(
                                            apiKey = apiKey,
                                            modelName = settings.model,
                                            prompt = text,
                                            history = activeChatHistory,
                                            provider = settings.provider,
                                            baseUrl = resolvedBaseUrl,
                                            systemInstruction = effectiveSystemInstruction,
                                            temperature = effectiveTemp,
                                            topP = effectiveTopP,
                                            topK = effectiveTopK,
                                            maxTokens = effectiveMaxTokens,
                                            attachments = attachments,
                                            onChunkReceived = { deltaText, _ ->
                                                if (firstTokenTime == null && deltaText.isNotEmpty()) {
                                                    firstTokenTime = System.currentTimeMillis()
                                                }
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
                                    }
                                    // --- Performance Insights ---
                                    // tokensPerSecond is approximate (chars/4 heuristic) since
                                    // these streaming APIs don't report exact per-chunk token
                                    // counts — see the comment on ChatMessage.tokensPerSecond.
                                    val endTime = System.currentTimeMillis()
                                    val ttft = firstTokenTime ?: endTime
                                    val latency = ttft - startTime
                                    val decodeMs = (endTime - ttft).coerceAtLeast(1)
                                    chats = chats.map { chat ->
                                        if (chat.id == currentChatId) {
                                            val newMessages = chat.messages.map { msg ->
                                                if (msg.id == modelMsgId) {
                                                    val approxTokens = msg.content.length / 4.0
                                                    msg.copy(
                                                        latencyMs = latency,
                                                        tokensPerSecond = (approxTokens / (decodeMs / 1000.0)).toFloat()
                                                    )
                                                } else msg
                                            }
                                            chat.copy(messages = newMessages)
                                        } else chat
                                    }
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
}
