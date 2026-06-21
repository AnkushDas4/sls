package com.example

import android.net.Uri
import com.squareup.moshi.JsonClass

enum class Theme {
    LIGHT, DARK, SYSTEM
}

enum class ProviderType {
    OPENAI, ANTHROPIC, XAI, GEMINI, NVIDIA, OPENROUTER, GROQ, NATIVE_ANDROID, LOCAL, CUSTOM
}

data class AppSettings(
    val theme: Theme = Theme.SYSTEM,
    val provider: ProviderType = ProviderType.NATIVE_ANDROID,
    val modelName: String = "",
    val nativeModelUriString: String? = null, 
    val systemInstruction: String = "You are a helpful, concise AI assistant.",
    val temperature: Double = 0.7,
    val enableHistory: Boolean = true,
    val historyLimit: Int = 50,
    val autoScrollToBottom: Boolean = true,
    val openaiApiKey: String = "",
    val anthropicApiKey: String = "",
    val xaiApiKey: String = "",
    val nvidiaApiKey: String = "",
    val geminiApiKey: String = "",
    val openrouterApiKey: String = "",
    val groqApiKey: String = "",
    val customApiKey: String = "",
    val localBaseUrl: String = "http://10.0.2.2:11434/v1",
    val enableHaptics: Boolean = true,
    val enableDynamicColor: Boolean = true
) {
    val nativeModelUri: Uri?
        get() = nativeModelUriString?.let { Uri.parse(it) }

    val isConfigValid: Boolean
        get() = when (provider) {
            ProviderType.OPENAI -> openaiApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.ANTHROPIC -> anthropicApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.XAI -> xaiApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.NVIDIA -> nvidiaApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.GEMINI -> geminiApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.OPENROUTER -> openrouterApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.GROQ -> groqApiKey.isNotBlank() && modelName.isNotBlank()
            ProviderType.LOCAL -> localBaseUrl.isNotBlank() && modelName.isNotBlank()
            ProviderType.CUSTOM -> localBaseUrl.isNotBlank() && modelName.isNotBlank()
            ProviderType.NATIVE_ANDROID -> !nativeModelUriString.isNullOrBlank()
        }
}

enum class Role {
    USER, ASSISTANT, MODEL
}

@JsonClass(generateAdapter = true)
data class AppAnalytics(
    val totalTokens: Long = 0L,
    val totalMessages: Long = 0L,
    val totalTimeMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class Message(
    val role: Role,
    val content: String,
    val isError: Boolean = false,
    val id: String = java.util.UUID.randomUUID().toString(),
    val processingTimeMs: Long? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val attachedUris: List<String> = emptyList(),
    val thought: String? = null
)

typealias ChatMessage = Message

