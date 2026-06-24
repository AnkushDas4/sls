package com.sednium.localspaces.model

import kotlinx.serialization.Serializable

/**
 * Core domain models for Sednium Local Spaces.
 * Direct Kotlin port of the original `types.ts`.
 */

@Serializable enum class Role { USER, MODEL }

@Serializable enum class ChatMode { QUICK, THINKING, CODING }

@Serializable enum class ModelProvider {
    GOOGLE, OPENAI, ANTHROPIC, XAI, GROQ, OPENROUTER, NVIDIA, LOCAL, CUSTOM, BROWSER
}

@Serializable enum class AppTheme { LIGHT, DARK }

@Serializable enum class AttachmentType { IMAGE, TEXT }

@Serializable data class Attachment(
    val type: AttachmentType,
    val mimeType: String,
    val data: String,      // base64 (no prefix) for images, raw text for text files
    val name: String
)

@Serializable data class ToolCallState(
    val command: String,
    val isExecuting: Boolean,
    val success: Boolean
)

@Serializable data class Citation(
    val id: Int,
    val title: String,
    val domain: String,
    val url: String
)

@Serializable data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val modelName: String? = null,
    val isError: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val thought: String? = null,
    val isThinking: Boolean = false,
    val toolCalls: List<ToolCallState> = emptyList(),
    val citations: List<Citation> = emptyList()
)

@Serializable data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long,
    val isPinned: Boolean = false
)

@Serializable data class MCPConfig(
    val id: String,
    val name: String,
    val url: String,
    val authToken: String? = null
)

enum class McpConnectionStatus {
    CONNECTING, CONNECTED, ERROR
}

@Serializable data class SavedModelPreset(
    val id: String,
    val name: String,
    val provider: ModelProvider,
    val model: String,
    val chatMode: ChatMode,
    val systemInstruction: String
)

@Serializable data class AppSettings(
    val theme: AppTheme = AppTheme.LIGHT,
    val provider: ModelProvider = ModelProvider.GOOGLE,

    val chatMode: ChatMode = ChatMode.QUICK,
    val enableTools: Boolean = true,
    val mcpServers: List<MCPConfig> = emptyList(),
    val skills: List<Skill> = emptyList(), // Changed to Skill to be serializable safely
    val savedPresets: List<SavedModelPreset> = emptyList(),
    val activePresetId: String? = null,

    val model: String = "gemini-1.5-pro",
    val systemInstruction: String = "You are an elite, world-class software architect and principal engineer called Oorty made by Sednium(link to website Sednium.com). Your primary directive is to write exceptionally clean, robust, secure, and highly optimized code. CRITICAL INSTRUCTIONS: Always think step-by-step about the architecture before writing the code. When asked to implement a feature or fix a bug: 1. Analyze the existing constraints, dependencies, and performance implications. 2. Select the optimal algorithms and data structures. 3. Write production-ready code that includes necessary error handling, edge-case checks, and typing (if applicable). 4. Do not hallucinaste dependencies or methods. Use the most up-to-date, idiomatic patterns for the requested language or framework. 5. Provide the full context for edits. Do not use placeholders like \"// ... rest of code\" unless the file is massive. Provide the fully integrated function or component. 6. Explain briefly *why* you chose the specific technical approach over alternatives. 7. If tool capabilities are enabled, actively utilize them (e.g., zip creation) to scaffold entire projects or workflows for the user instead of making them copy-paste dozens of files manually.",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 4096,

    val enableHistory: Boolean = true,
    val historyLimit: Int = 50,

    val googleApiKey: String = "",
    val openaiApiKey: String = "",
    val anthropicApiKey: String = "",
    val xaiApiKey: String = "",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val nvidiaApiKey: String = "",

    val localBaseUrl: String = "http://localhost:11434/v1",
    val customApiKey: String = ""
)

@Serializable data class Skill(val id: String, val name: String, val content: String)


enum class ModelIconType {
    TEXT, CODE, AGENT, IMAGE, VIDEO, AUTO, LIGHTNING
}

data class ModelOption(val id: String, val label: String, val icon: ModelIconType = ModelIconType.TEXT)

data class ProviderInfo(val displayName: String, val defaultUrl: String, val apiLink: String = "", val popularModels: List<ModelOption> = emptyList())

/** Mirrors PROVIDER_CONFIG from constants.ts */
val PROVIDER_CONFIG: Map<ModelProvider, ProviderInfo> = mapOf(
    ModelProvider.GOOGLE to ProviderInfo(
        "Google Gemini", 
        "https://generativelanguage.googleapis.com", 
        "https://aistudio.google.com/app/apikey", 
        listOf(
            ModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", ModelIconType.LIGHTNING),
            ModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", ModelIconType.AGENT),
            ModelOption("gemini-3.1-flash-live", "Gemini 3.1 Flash Live", ModelIconType.LIGHTNING),
            ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", ModelIconType.LIGHTNING),
            ModelOption("gemma-4-31b", "Gemma 4 31B", ModelIconType.CODE)
        )
    ),
    ModelProvider.OPENAI to ProviderInfo(
        "OpenAI", 
        "https://api.openai.com/v1", 
        "https://platform.openai.com/api-keys",
        listOf(
            ModelOption("gpt-5.4-thinking", "GPT-5.4 Thinking", ModelIconType.AGENT),
            ModelOption("gpt-5.4-pro", "GPT-5.4 Pro", ModelIconType.CODE),
            ModelOption("gpt-5.4-standard", "GPT-5.4 (Standard)", ModelIconType.AUTO),
            ModelOption("gpt-5.4-mini", "GPT-5.4 Mini / Nano", ModelIconType.LIGHTNING),
            ModelOption("gpt-oss-120b", "gpt-oss-120b", ModelIconType.CODE)
        )
    ),
    ModelProvider.ANTHROPIC to ProviderInfo(
        "Anthropic Claude", 
        "https://api.anthropic.com/v1", 
        "https://console.anthropic.com/settings/keys",
        listOf(
            ModelOption("claude-fable-5", "Claude Fable 5", ModelIconType.AGENT),
            ModelOption("claude-opus-4.8", "Claude Opus 4.8", ModelIconType.TEXT),
            ModelOption("claude-sonnet-4.6", "Claude Sonnet 4.6", ModelIconType.CODE),
            ModelOption("claude-haiku-4.5", "Claude Haiku 4.5", ModelIconType.LIGHTNING)
        )
    ),
    ModelProvider.XAI to ProviderInfo(
        "xAI Grok", 
        "https://api.x.ai/v1", 
        "https://console.x.ai/",
        listOf(
            ModelOption("grok-4.3", "Grok 4.3", ModelIconType.AGENT),
            ModelOption("grok-4.20-reasoning", "Grok 4.20 Reasoning", ModelIconType.AGENT),
            ModelOption("grok-build-0.1", "grok-build-0.1", ModelIconType.CODE),
            ModelOption("grok-4.20-non-reasoning", "Grok 4.20 Non-Reasoning", ModelIconType.LIGHTNING),
            ModelOption("grok-1.5-vision", "Grok 1.5 Vision", ModelIconType.IMAGE)
        )
    ),
    ModelProvider.GROQ to ProviderInfo(
        "Groq", 
        "https://api.groq.com/openai/v1", 
        "https://console.groq.com/keys",
        listOf(
            ModelOption("llama-4-scout-17b", "Llama 4 Scout 17B", ModelIconType.LIGHTNING),
            ModelOption("llama-3.3-70b", "Llama 3.3 70B", ModelIconType.LIGHTNING),
            ModelOption("qwen-3-32b", "Qwen 3 32B", ModelIconType.CODE),
            ModelOption("kimi-k2-instruct", "Kimi K2 Instruct", ModelIconType.AGENT),
            ModelOption("whisper-large-v3", "Whisper Large V3", ModelIconType.VIDEO)
        )
    ),
    ModelProvider.OPENROUTER to ProviderInfo(
        "OpenRouter", 
        "https://openrouter.ai/api/v1", 
        "https://openrouter.ai/keys",
        listOf(
            ModelOption("owl-alpha", "Owl Alpha", ModelIconType.AGENT),
            ModelOption("nex-n2-pro", "Nex-N2-Pro", ModelIconType.AGENT),
            ModelOption("laguna-m.1", "Laguna M.1 (Poolside)", ModelIconType.CODE),
            ModelOption("laguna-xs.2", "Laguna XS.2 (Poolside)", ModelIconType.CODE),
            ModelOption("deepseek-v4-flash", "DeepSeek V4 Flash", ModelIconType.LIGHTNING)
        )
    ),
    ModelProvider.NVIDIA to ProviderInfo(
        "NVIDIA NIM", 
        "https://integrate.api.nvidia.com/v1", 
        "https://build.nvidia.com/explore/discover",
        listOf(
            ModelOption("nemotron-3-super-120b", "Nemotron 3 Super 120B", ModelIconType.AGENT),
            ModelOption("nemotron-3-ultra-550b", "Nemotron 3 Ultra 550B", ModelIconType.AGENT),
            ModelOption("nemotron-3-nano-omni-30b", "Nemotron 3 Nano Omni 30B", ModelIconType.IMAGE),
            ModelOption("nemotron-nano-12b-v2-vl", "Nemotron Nano 12B v2 VL", ModelIconType.VIDEO),
            ModelOption("cosmos3-nano", "Cosmos3-Nano", ModelIconType.VIDEO)
        )
    ),
    ModelProvider.LOCAL to ProviderInfo("Local Server", "http://localhost:11434/v1"),
    ModelProvider.CUSTOM to ProviderInfo("Custom Endpoint", ""),
    ModelProvider.BROWSER to ProviderInfo("Browser GGUF", "", "")
)
