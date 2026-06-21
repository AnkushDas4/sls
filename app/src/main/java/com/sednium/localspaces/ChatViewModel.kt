package com.sednium.localspaces

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.channels.BufferOverflow

enum class ModelState {
    OFFLINE, LOADING, READY, GENERATING
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val isFirstLaunch: StateFlow<Boolean> = repository.isFirstLaunch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _chatHistory = MutableStateFlow<List<Message>>(emptyList())
    val chatHistory: StateFlow<List<Message>> = _chatHistory.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    
    val analytics: StateFlow<AppAnalytics> = repository.analyticsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppAnalytics())

    private val _tps = MutableStateFlow(0.0)
    val tps: StateFlow<Double> = _tps.asStateFlow()
    
    private val _modelState = MutableStateFlow(ModelState.OFFLINE)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _ramUsageMegabytes = MutableStateFlow(0L)
    val ramUsageMegabytes: StateFlow<Long> = _ramUsageMegabytes.asStateFlow()
    
    private val _currentStreamingContent = MutableStateFlow("")
    val currentStreamingContent: StateFlow<String> = _currentStreamingContent.asStateFlow()

    private var generationJob: Job? = null

    private val _settingsToSave = MutableSharedFlow<AppSettings>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var lastInitializedUri: String? = null

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { newSettings ->
                _settings.value = newSettings
                // Could initialize engine here if changed 
            }
        }
        viewModelScope.launch {
            repository.chatHistoryFlow.collect {
                _chatHistory.value = it
            }
        }
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            _settingsToSave.debounce(500L).collect { debouncedSettings ->
                repository.updateSettings(debouncedSettings)
            }
        }
        startRamMonitor()
    }

    private fun startRamMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            while (true) {
                activityManager.getMemoryInfo(memoryInfo)
                val usedMem = memoryInfo.totalMem - memoryInfo.availMem
                _ramUsageMegabytes.value = usedMem / (1024 * 1024)
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        _settingsToSave.tryEmit(newSettings)
        
        if (newSettings.provider == ProviderType.NATIVE_ANDROID && newSettings.nativeModelUriString != null) {
            val currentUri = newSettings.nativeModelUriString
            if (currentUri != lastInitializedUri) {
                lastInitializedUri = currentUri
                initializeEngine(newSettings.nativeModelUri!!)
            }
        }
    }

    private fun initializeEngine(modelUri: Uri) {
        _isGenerating.value = true
        _modelState.value = ModelState.LOADING
        // In reality, Uri to Path resolution depends on Android ContentResolver.
        // Since the backend handles paths, we get the absolute path here:
        val modelPath = modelUri.path ?: modelUri.toString()
        
        // Mocking work on background:
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // fake init time
            LlamaCpp.initContext(
                modelPath = modelPath,
                nCtx = 2048,
                nThreads = 4,
                useMmap = true
            )
            _isGenerating.value = false
            _modelState.value = ModelState.READY
        }
    }

    private fun buildSystemInstruction(settings: AppSettings, mode: com.sednium.localspaces.ui.ChatMode): String {
        val quickPrompt = """You are an AI designed for maximal speed, precision, and brevity. Your primary
directive is to provide the user with the most direct, accurate, and concise
answer possible.
Do not use conversational filler, preamble, or apologies. Avoid phrases like
"Here is the answer," "I think that...", or "Sure!". Get straight to the point.
If the objective is to answer a factual question, provide just the fact. If
asked to summarize, pull only the absolutely critical points into minimal bullet
points.
You do not need to show your work or explain your chain of thought unless
specifically asked. Strive for high-density information transfer. Your tone
should be completely neutral, objective, and machine-like efficiency."""

        val thinkingPrompt = """You are an advanced, highly analytical AI. Your primary directive is to engage
in deep, rigorous chain-of-thought reasoning before outputting any final answer.
CRITICAL INSTRUCTION: For every single response, you MUST enclose your internal
reasoning process within <think> and </think> tags.
Inside the <think> block:
1. Deconstruct the user's prompt into foundational premises.
2. Identify any ambiguities, edge cases, or hidden assumptions.
3. Formulate multiple potential hypotheses or solutions.
4. Critically evaluate each hypothesis against the constraints and facts.
5. Anticipate counter-arguments and refine your logic.
6. Arrive at a solid, logical conclusion.

Only after you have completed this exhaustive internal monologue inside the
<think> tags should you write your final, confident response to the user. Do
not leak the <think> structure in your final output, keep them strictly
separated.
If the problem involves math, logic, or code, you must manually trace variables
or formula steps. Do not skip steps. Accuracy and profound depth of thought are
your only goals."""

        val codingPrompt = """You are an elite, world-class software architect and principal engineer. Your
primary directive is to write exceptionally clean, robust, secure, and highly
optimized code.
CRITICAL INSTRUCTIONS:
Always think step-by-step about the architecture before writing the code.
When asked to implement a feature or fix a bug:
1. Analyze the existing constraints, dependencies, and performance implications.
2. Select the optimal algorithms and data structures.
3. Write production-ready code that includes necessary error handling,
   edge-case checks, and typing (if applicable).
4. Do not hallucinate dependencies or methods. Use the most up-to-date,
   idiomatic patterns for the requested language or framework.
5. Provide the full context for edits. Do not use placeholders like
   "// ... rest of code" unless the file is massive. Provide the fully
   integrated function or component.
6. Explain briefly *why* you chose the specific technical approach over
   alternatives.
7. If tool capabilities are enabled, actively utilize them (e.g., zip creation)
   to scaffold entire projects or workflows for the user instead of making them
   copy-paste dozens of files manually."""

        val modePrompt = when(mode) {
            com.sednium.localspaces.ui.ChatMode.QUICK -> quickPrompt
            com.sednium.localspaces.ui.ChatMode.THINKING -> thinkingPrompt
            com.sednium.localspaces.ui.ChatMode.CODING -> codingPrompt
        }

        val sb = StringBuilder()
        if (settings.systemInstruction.isNotBlank()) {
            sb.append(settings.systemInstruction)
            sb.append("\n\n")
        }
        sb.append(modePrompt)
        
        // Appending tools
        sb.append("\n\nTOOLS AVAILABLE: You have access to the following tools. To use a tool, output exactly this XML format:\n<tool_call><name>tool_name</name><args>{\"key\":\"value\"}</args></tool_call>\n\n- name: create_zip\n  description: Generates a downloadable zip file of a project.\n  args: {\"filename\": string}")
        return sb.toString()
    }

    fun sendMessage(content: String, mode: com.sednium.localspaces.ui.ChatMode = com.sednium.localspaces.ui.ChatMode.QUICK, attachedUris: List<android.net.Uri> = emptyList()) {
        if (content.isBlank()) return
        
        _isGenerating.value = true
        _modelState.value = ModelState.GENERATING
        _currentStreamingContent.value = "Preparing request..."

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var processedContent = content
                val uriStrings = attachedUris.map { it.toString() }

                if (attachedUris.isNotEmpty()) {
                    val cr = getApplication<Application>().contentResolver
                    val fileContents = java.lang.StringBuilder()
                    attachedUris.forEach { uri ->
                        try {
                            val mimeType = cr.getType(uri)
                            var filename = uri.path ?: "unknown"
                            if (uri.scheme == "content") {
                                cr.query(uri, null, null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (idx != -1) filename = cursor.getString(idx)
                                    }
                                }
                            } else {
                                val cut = filename.lastIndexOf('/')
                                if (cut != -1) filename = filename.substring(cut + 1)
                            }
                            
                            fileContents.append("\n\n--- BEGIN FILE: $filename ---\n")
                            if (filename.endsWith(".zip", ignoreCase = true) || mimeType == "application/zip") {
                                cr.openInputStream(uri)?.use { inputStream ->
                                    java.util.zip.ZipInputStream(inputStream).use { zis ->
                                        var entry = zis.nextEntry
                                        while (entry != null) {
                                            if (!entry.isDirectory && !entry.name.contains("__MACOSX") && !entry.name.endsWith(".png") && !entry.name.endsWith(".jpg") && !entry.name.endsWith(".jpeg") && !entry.name.endsWith(".mp4") && !entry.name.endsWith(".apk")) {
                                                val entryMetadata = "\n--- ZIP ENTRY: ${entry.name} ---\n"
                                                val bytes = zis.readBytes()
                                                val text = String(bytes, Charsets.UTF_8)
                                                // Verify text is somewhat readable (not binary)
                                                if (!text.contains("\u0000")) {
                                                    fileContents.append(entryMetadata)
                                                    fileContents.append(text)
                                                }
                                            }
                                            zis.closeEntry()
                                            entry = zis.nextEntry
                                        }
                                    }
                                }
                            } else {
                                cr.openInputStream(uri)?.use { inputStream ->
                                    val text = inputStream.bufferedReader().use { it.readText() }
                                    fileContents.append(text)
                                }
                            }
                            fileContents.append("\n--- END FILE: $filename ---\n")
                        } catch (e: Exception) {
                            fileContents.append("\nError reading file $uri: ${e.message}\n")
                        }
                    }
                    processedContent += "\n\nATTACHED FILES CONTENT:\n" + fileContents.toString()
                }

                val finalContent = processedContent

                val newHistory = _chatHistory.value.toMutableList()
                newHistory.add(Message(Role.USER, finalContent, attachedUris = uriStrings))
                
                if (_settings.value.enableHistory && newHistory.size > _settings.value.historyLimit) {
                    newHistory.subList(0, newHistory.size - _settings.value.historyLimit).clear()
                }
                
                _chatHistory.value = newHistory
                _currentStreamingContent.value = ""

                val startTime = System.currentTimeMillis()
                var tokenCount = 0
                var lastUpdate = 0L
                var fullContent = ""

                val onToken: (String) -> Unit = { token ->
                    fullContent += token
                    tokenCount++
                    val now = System.currentTimeMillis()
                    
                    val elapsedMs = now - startTime
                    if (elapsedMs > 0) {
                        _tps.value = (tokenCount * 1000.0) / elapsedMs
                    }

                    if (now - lastUpdate > 16L) {
                        _currentStreamingContent.value = fullContent
                        lastUpdate = now
                    }
                }

                val onError: (String) -> Unit = { errorMsg ->
                    viewModelScope.launch {
                        _currentStreamingContent.value = ""
                        val finalHistory = _chatHistory.value.toMutableList()
                        finalHistory.add(Message(Role.ASSISTANT, "**Error**: $errorMsg\n\nPlease check your settings.", isError = true))
                        _chatHistory.value = finalHistory
                        repository.saveChatHistory(finalHistory)
                    }
                }

                val onDone: () -> Unit = {
                    val elapsedTimeMs = System.currentTimeMillis() - startTime
                    viewModelScope.launch {
                        val parsed = parseThinkTags(fullContent)
                        _currentStreamingContent.value = parsed.displayContent
                        commitAssistantMessage(parsed.displayContent, elapsedTimeMs, parsed.thought)
                        repository.recordAnalytics(tokenCount.toLong(), elapsedTimeMs)
                    }
                }

                val currentSystemInstruct = buildSystemInstruction(_settings.value, mode)
                
                if (_settings.value.provider == ProviderType.NATIVE_ANDROID) {
                    val prompt = formatPrompt(newHistory.dropLast(1), currentSystemInstruct) + 
                                 "User: ${finalContent}\nAssistant:"
                    
                    if (_settings.value.nativeModelUriString.isNullOrBlank()) {
                        onError("Please select a .gguf model file.")
                        return@launch
                    }

                    LlamaCpp.completion(
                        prompt = prompt,
                        nPredict = 2048,
                        temperature = _settings.value.temperature,
                        topK = 40,
                        topP = 0.9
                    ).collect { token ->
                        onToken(token)
                    }
                    onDone()
                } else {
                    ChatService.streamChatResponse(
                        history = newHistory.dropLast(1),
                        newMessage = finalContent,
                        settings = _settings.value.copy(systemInstruction = currentSystemInstruct),
                        onToken = onToken,
                        onError = onError,
                        onDone = onDone
                    )
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || e.message?.contains("cancel", true) == true) {
                    // Ignored
                } else if (e is java.net.UnknownHostException) {
                    val finalHistory = _chatHistory.value.toMutableList()
                    finalHistory.add(Message(Role.ASSISTANT, "**Network Error**: Unable to reach the API provider. Please check your internet connection or ensure your DNS/firewall isn't blocking the connection.", isError = true))
                    _chatHistory.value = finalHistory
                    repository.saveChatHistory(finalHistory)
                } else {
                    val finalHistory = _chatHistory.value.toMutableList()
                    finalHistory.add(Message(Role.ASSISTANT, "**Error**: ${e.message}\n\nPlease check your settings.", isError = true))
                    _chatHistory.value = finalHistory
                    repository.saveChatHistory(finalHistory)
                }
            } finally {
                _isGenerating.value = false
                _modelState.value = ModelState.READY
                _tps.value = 0.0
                _currentStreamingContent.value = ""
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        _modelState.value = ModelState.READY
        if (_currentStreamingContent.value.isNotBlank()) {
            val finalContent = _currentStreamingContent.value
            viewModelScope.launch {
                val parsed = parseThinkTags(finalContent)
            	commitAssistantMessage(parsed.displayContent, null, parsed.thought)
            }
        }
    }

    data class ParsedResponse(val displayContent: String, val thought: String?)

    private fun parseThinkTags(raw: String): ParsedResponse {
        val ts = raw.indexOf("<think>")
        val te = raw.indexOf("</think>")
        return if (ts != -1 && te != -1) {
            val thought = raw.substring(ts + 7, te).trim()
            val display = raw.substring(0, ts) + raw.substring(te + 8)
            ParsedResponse(displayContent = display.trim(), thought = thought)
        } else {
            ParsedResponse(displayContent = raw.trim(), thought = null)
        }
    }

    private suspend fun commitAssistantMessage(content: String, processingTimeMs: Long? = null, thought: String? = null) {
        _currentStreamingContent.value = ""
        val finalHistory = _chatHistory.value.toMutableList()
        finalHistory.add(Message(Role.ASSISTANT, content, processingTimeMs = processingTimeMs, thought = thought))
        if (_settings.value.enableHistory && finalHistory.size > _settings.value.historyLimit) {
            finalHistory.subList(0, finalHistory.size - _settings.value.historyLimit).clear()
        }
        _chatHistory.value = finalHistory
        repository.saveChatHistory(finalHistory)
    }

    fun regenerateLastMessage() {
        val currentHistory = _chatHistory.value.toMutableList()
        if (currentHistory.isEmpty()) return
        
        var lastUserMsg: Message? = null
        for (i in currentHistory.indices.reversed()) {
            if (currentHistory[i].role == Role.USER) {
                lastUserMsg = currentHistory[i]
                val dropCount = currentHistory.size - i
                repeat(dropCount) {
                    currentHistory.removeAt(currentHistory.size - 1)
                }
                break
            }
        }
        
        if (lastUserMsg != null) {
            _chatHistory.value = currentHistory
            sendMessage(lastUserMsg.content)
        }
    }

    fun clearAnalytics() {
        viewModelScope.launch {
            repository.clearAnalytics()
        }
    }
    fun clearChatHistory() {
        viewModelScope.launch {
            _chatHistory.value = emptyList()
            repository.saveChatHistory(emptyList())
        }
    }

    private fun formatPrompt(history: List<Message>, systemInstruction: String): String {
        val sb = java.lang.StringBuilder()
        sb.append("System: ").append(systemInstruction).append("\n")
        
        for (i in 0 until history.size - 1) {
            val msg = history[i]
            sb.append(if (msg.role == Role.USER) "User: " else "Assistant: ")
              .append(msg.content).append("\n")
        }
        
        if (history.isNotEmpty()) {
            sb.append("User: ").append(history.last().content).append("\nAssistant:")
        }
        return sb.toString()
    }

    fun exportHistory(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val text = _chatHistory.value.joinToString("\n\n") { "${if (it.role == Role.USER) "User" else "Assistant"}: ${it.content}" }
                    outputStream.write(text.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
