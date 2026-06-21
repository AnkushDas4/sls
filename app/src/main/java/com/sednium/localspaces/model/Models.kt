package com.sednium.localspaces.model

/**
 * Core domain models for Sednium Local Spaces.
 * Direct Kotlin port of the original `types.ts`.
 */

enum class Role { USER, MODEL }

enum class ChatMode { QUICK, THINKING, CODING }

enum class ModelProvider {
    GOOGLE, OPENAI, ANTHROPIC, XAI, GROQ, OPENROUTER, NVIDIA, LOCAL, CUSTOM, BROWSER
}

enum class AppTheme { LIGHT, DARK }

enum class AttachmentType { IMAGE, TEXT }

data class Attachment(
    val type: AttachmentType,
    val mimeType: String,
    val data: String,      // base64 (no prefix) for images, raw text for text files
    val name: String
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val isError: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val thought: String? = null   // optional reasoning / <think> trace
)

data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long,
    val isPinned: Boolean = false
)

data class MCPConfig(
    val id: String,
    val name: String,
    val url: String
)

data class SavedModelPreset(
    val id: String,
    val name: String,
    val provider: ModelProvider,
    val model: String,
    val chatMode: ChatMode,
    val systemInstruction: String
)

data class AppSettings(
    val theme: AppTheme = AppTheme.LIGHT,
    val provider: ModelProvider = ModelProvider.GOOGLE,

    val chatMode: ChatMode = ChatMode.QUICK,
    val enableTools: Boolean = true,
    val mcpServers: List<MCPConfig> = emptyList(),
    val skills: List<Triple<String, String, String>> = emptyList(), // id, name, content
    val savedPresets: List<SavedModelPreset> = emptyList(),
    val activePresetId: String? = null,

    val model: String = "gemini-1.5-pro",
    val systemInstruction: String = "",
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

data class ProviderInfo(val displayName: String, val defaultUrl: String, val apiLink: String = "", val popularModels: List<String> = emptyList())

/** Mirrors PROVIDER_CONFIG from constants.ts */
val PROVIDER_CONFIG: Map<ModelProvider, ProviderInfo> = mapOf(
    ModelProvider.GOOGLE to ProviderInfo(
        "Google Gemini", 
        "https://generativelanguage.googleapis.com", 
        "https://aistudio.google.com/app/apikey", 
        listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-2.0-flash-exp")
    ),
    ModelProvider.OPENAI to ProviderInfo(
        "OpenAI", 
        "https://api.openai.com/v1", 
        "https://platform.openai.com/api-keys",
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
    ),
    ModelProvider.ANTHROPIC to ProviderInfo(
        "Anthropic Claude", 
        "https://api.anthropic.com/v1", 
        "https://console.anthropic.com/settings/keys",
        listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus-latest")
    ),
    ModelProvider.XAI to ProviderInfo(
        "xAI Grok", 
        "https://api.x.ai/v1", 
        "https://console.x.ai/",
        listOf("grok-beta", "grok-vision-beta")
    ),
    ModelProvider.GROQ to ProviderInfo(
        "Groq", 
        "https://api.groq.com/openai/v1", 
        "https://console.groq.com/keys",
        listOf("llama-3.1-70b-versatile", "llama-3.1-8b-instant", "llama3-70b-8192")
    ),
    ModelProvider.OPENROUTER to ProviderInfo(
        "OpenRouter", 
        "https://openrouter.ai/api/v1", 
        "https://openrouter.ai/keys",
        listOf("auto", "anthropic/claude-3.5-sonnet", "openai/gpt-4o", "meta-llama/llama-3.1-405b-instruct")
    ),
    ModelProvider.NVIDIA to ProviderInfo(
        "NVIDIA NIM", 
        "https://integrate.api.nvidia.com/v1", 
        "https://build.nvidia.com/explore/discover",
        listOf("meta/llama3-70b-instruct", "meta/llama-3.1-405b-instruct")
    ),
    ModelProvider.LOCAL to ProviderInfo("Local Server", "http://localhost:11434/v1"),
    ModelProvider.CUSTOM to ProviderInfo("Custom Endpoint", ""),
    ModelProvider.BROWSER to ProviderInfo("Browser GGUF", "", "")
)
