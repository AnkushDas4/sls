package com.sednium.localspaces.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Shared Gemini Models ---

@Serializable
data class GenerateContentRequest(
    val systemInstruction: Content? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null
)

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

// --- Retrofit Setup ---

interface GenericApiService {
    @retrofit2.http.GET
    suspend fun getModels(
        @retrofit2.http.Url url: String,
        @retrofit2.http.HeaderMap headers: Map<String, String> = emptyMap()
    ): ResponseBody

    @retrofit2.http.Streaming
    @retrofit2.http.POST
    suspend fun postChatCompletions(
        @retrofit2.http.Url url: String,
        @retrofit2.http.HeaderMap headers: Map<String, String>,
        @retrofit2.http.Body body: okhttp3.RequestBody
    ): ResponseBody
}

interface UniversalApiService {
    @retrofit2.http.GET("v1beta/models")
    suspend fun listModels(@Query("key") apiKey: String): ResponseBody

    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    @Streaming
    suspend fun generateContentStream(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): ResponseBody
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: UniversalApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(UniversalApiService::class.java)
    }

    val genericService: GenericApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/") // Dummy base URL, we use @Url in method
            .client(okHttpClient)
            .build()
            .create(GenericApiService::class.java)
    }
}

// --- Multimodal content builders ---
// Each provider wants attachments shaped differently. These take the same
// (text, attachments) pair and produce whatever that provider's wire format
// needs. Used for both history messages and the current turn, so an image
// stays attached to its turn across a multi-turn vision conversation
// instead of silently dropping after the first reply.

private fun buildGeminiParts(text: String, attachments: List<com.sednium.localspaces.model.Attachment>): List<Part> {
    val parts = mutableListOf<Part>()
    val textAttachments = attachments.filter { it.type == com.sednium.localspaces.model.AttachmentType.TEXT }
    val imageAttachments = attachments.filter { it.type == com.sednium.localspaces.model.AttachmentType.IMAGE }

    val combinedText = buildString {
        textAttachments.forEach { att -> append("[Attached file: ${att.name}]\n${att.data}\n\n") }
        append(text)
    }
    if (combinedText.isNotEmpty()) parts.add(Part(text = combinedText))
    imageAttachments.forEach { att -> parts.add(Part(inlineData = InlineData(mimeType = att.mimeType, data = att.data))) }
    if (parts.isEmpty()) parts.add(Part(text = ""))
    return parts
}

private fun buildAnthropicContentBlocks(
    text: String,
    attachments: List<com.sednium.localspaces.model.Attachment>
): kotlinx.serialization.json.JsonElement {
    val textAttachments = attachments.filter { it.type == com.sednium.localspaces.model.AttachmentType.TEXT }
    val imageAttachments = attachments.filter { it.type == com.sednium.localspaces.model.AttachmentType.IMAGE }
    if (imageAttachments.isEmpty() && textAttachments.isEmpty()) {
        // Plain string content is valid (and simplest) when there's nothing to attach.
        return kotlinx.serialization.json.JsonPrimitive(text)
    }
    return kotlinx.serialization.json.buildJsonArray {
        imageAttachments.forEach { att ->
            add(kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("image"))
                put("source", kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("base64"))
                    put("media_type", kotlinx.serialization.json.JsonPrimitive(att.mimeType))
                    put("data", kotlinx.serialization.json.JsonPrimitive(att.data))
                })
            })
        }
        val combinedText = buildString {
            textAttachments.forEach { att -> append("[Attached file: ${att.name}]\n${att.data}\n\n") }
            append(text)
        }
        add(kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
            put("text", kotlinx.serialization.json.JsonPrimitive(combinedText))
        })
    }
}

private fun buildOpenAiContent(
    text: String,
    attachments: List<com.sednium.localspaces.model.Attachment>
): kotlinx.serialization.json.JsonElement {
    val textAttachments = attachments.filter { it.type == com.sednium.localspaces.model.AttachmentType.TEXT }
    val imageAttachments = attachments.filter { it.type == com.sednium.localspaces.model.AttachmentType.IMAGE }
    if (imageAttachments.isEmpty() && textAttachments.isEmpty()) {
        // Keep plain string content when there's nothing attached — safest
        // for OpenAI-compatible providers that are strict about the schema.
        return kotlinx.serialization.json.JsonPrimitive(text)
    }
    return kotlinx.serialization.json.buildJsonArray {
        val combinedText = buildString {
            textAttachments.forEach { att -> append("[Attached file: ${att.name}]\n${att.data}\n\n") }
            append(text)
        }
        add(kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
            put("text", kotlinx.serialization.json.JsonPrimitive(combinedText))
        })
        imageAttachments.forEach { att ->
            add(kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("image_url"))
                put("image_url", kotlinx.serialization.json.buildJsonObject {
                    put("url", kotlinx.serialization.json.JsonPrimitive("data:${att.mimeType};base64,${att.data}"))
                })
            })
        }
    }
}

suspend fun generateContentStream(
    apiKey: String,
    modelName: String,
    prompt: String,
    history: List<com.sednium.localspaces.model.ChatMessage>,
    provider: com.sednium.localspaces.model.ModelProvider = com.sednium.localspaces.model.ModelProvider.GOOGLE,
    baseUrl: String = "",
    systemInstruction: String = "",
    // These previously existed on AppSettings but were never actually wired
    // into any of the three request branches below — the sliders in
    // Settings did nothing. Defaults mirror AppSettings' own defaults so
    // existing call sites that don't pass them keep prior (if accidental)
    // behavior.
    temperature: Float = 0.7f,
    topP: Float = 0.9f,
    topK: Int = 40,
    maxTokens: Int = 4096,
    // Attachments for the CURRENT turn (the `prompt` text). History messages
    // carry their own attachments on ChatMessage.attachments already — see
    // buildGeminiParts/buildAnthropicContentBlocks/buildOpenAiContent below,
    // which read msg.attachments for every history message too, so a vision
    // conversation stays multi-turn instead of losing the image after the
    // first reply.
    attachments: List<com.sednium.localspaces.model.Attachment> = emptyList(),
    onChunkReceived: (String, String?) -> Unit // (deltaText, deltaThought)
) = withContext(Dispatchers.IO) {
    val cleanApiKey = apiKey.trim()
    if (provider == com.sednium.localspaces.model.ModelProvider.GOOGLE) {
        val contents = history.map { msg ->
            Content(
                role = if (msg.role == com.sednium.localspaces.model.Role.USER) "user" else "model",
                parts = buildGeminiParts(msg.content, msg.attachments)
            )
        } + Content("user", buildGeminiParts(prompt, attachments))

        val sysContent = if (systemInstruction.isNotBlank()) Content("user", listOf(Part(text = systemInstruction))) else null
        val request = GenerateContentRequest(
            systemInstruction = sysContent,
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = temperature,
                topP = topP,
                topK = topK,
                maxOutputTokens = maxTokens
            )
        )
        try {
            val response = RetrofitClient.service.generateContentStream(modelName, cleanApiKey, request)
            response.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("data: ")) {
                        val data = line!!.removePrefix("data: ")
                        try {
                            val chunk = Json.parseToJsonElement(data).jsonObject
                            val text = chunk["candidates"]?.jsonArray
                                ?.getOrNull(0)?.jsonObject
                                ?.get("content")?.jsonObject
                                ?.get("parts")?.jsonArray
                                ?.getOrNull(0)?.jsonObject
                                ?.get("text")?.jsonPrimitive?.content
                            if (text != null) onChunkReceived(text, null)
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    if (errorBody != null) {
                        try {
                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(errorBody).jsonObject
                            json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "HTTP ${e.code()}"
                        } catch (ex: Exception) { "HTTP ${e.code()}" }
                    } else "HTTP ${e.code()}"
                } catch (ex: Exception) { "HTTP ${e.code()}" }
            } else if (e is java.net.SocketTimeoutException) {
                "Connection timed out. The server might be experiencing high demand."
            } else if (e is java.net.UnknownHostException) {
                "Network error: Unable to resolve host."
            } else {
                e.message ?: "Unknown error"
            }
            onChunkReceived("Error: $errorMsg\n", null)
        }
    } else if (provider == com.sednium.localspaces.model.ModelProvider.ANTHROPIC) {
        val messagesArray = kotlinx.serialization.json.buildJsonArray {
            history.forEach { msg ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("role", kotlinx.serialization.json.JsonPrimitive(if (msg.role == com.sednium.localspaces.model.Role.USER) "user" else "assistant"))
                    put("content", buildAnthropicContentBlocks(msg.content, msg.attachments))
                })
            }
            add(kotlinx.serialization.json.buildJsonObject {
                put("role", kotlinx.serialization.json.JsonPrimitive("user"))
                put("content", buildAnthropicContentBlocks(prompt, attachments))
            })
        }
        val requestJson = kotlinx.serialization.json.buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive(modelName))
            put("stream", kotlinx.serialization.json.JsonPrimitive(true))
            put("max_tokens", kotlinx.serialization.json.JsonPrimitive(maxTokens))
            put("temperature", kotlinx.serialization.json.JsonPrimitive(temperature))
            put("top_p", kotlinx.serialization.json.JsonPrimitive(topP))
            put("top_k", kotlinx.serialization.json.JsonPrimitive(topK))
            put("messages", messagesArray)
            if (systemInstruction.isNotBlank()) {
                put("system", kotlinx.serialization.json.JsonPrimitive(systemInstruction))
            }
        }
        val requestBody = okhttp3.RequestBody.create("application/json".toMediaType(), requestJson.toString())
        val endpointUrl = if (baseUrl.endsWith("/")) "${baseUrl}messages" else "$baseUrl/messages"
        val headers = mapOf("x-api-key" to cleanApiKey, "anthropic-version" to "2023-06-01", "Content-Type" to "application/json")

        try {
            val response = RetrofitClient.genericService.postChatCompletions(endpointUrl, headers, requestBody)
            response.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("data: ")) {
                        val data = line!!.removePrefix("data: ")
                        try {
                            val chunk = Json.parseToJsonElement(data).jsonObject
                            if (chunk["type"]?.jsonPrimitive?.content == "content_block_delta") {
                                val text = chunk["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                                if (text.isNotEmpty()) onChunkReceived(text, null)
                            } else if (chunk["type"]?.jsonPrimitive?.content == "error") {
                                val msg = chunk["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Unknown Error"
                                onChunkReceived("Error: $msg\n", null)
                            }
                        } catch (e: Exception) {}
                    } else if (line!!.startsWith("{")) {
                        if(line!!.contains("error")) {
                             onChunkReceived("Error: ${line}\n", null)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    if (errorBody != null) {
                        try {
                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(errorBody).jsonObject
                            json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "HTTP ${e.code()}"
                        } catch (ex: Exception) { "HTTP ${e.code()}" }
                    } else "HTTP ${e.code()}"
                } catch (ex: Exception) { "HTTP ${e.code()}" }
            } else if (e is java.net.SocketTimeoutException) {
                "Connection timed out. The server might be experiencing high demand."
            } else if (e is java.net.UnknownHostException) {
                "Network error: Unable to resolve host."
            } else {
                e.message ?: "Unknown error"
            }
            onChunkReceived("Error: $errorMsg\n", null)
        }
    } else {
        // OpenAI format fallback for other providers
        val messagesArray = kotlinx.serialization.json.buildJsonArray {
            if (systemInstruction.isNotBlank()) {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("role", kotlinx.serialization.json.JsonPrimitive("system"))
                    put("content", kotlinx.serialization.json.JsonPrimitive(systemInstruction))
                })
            }
            history.forEach { msg ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("role", kotlinx.serialization.json.JsonPrimitive(if (msg.role == com.sednium.localspaces.model.Role.USER) "user" else "assistant"))
                    put("content", buildOpenAiContent(msg.content, msg.attachments))
                })
            }
            add(kotlinx.serialization.json.buildJsonObject {
                put("role", kotlinx.serialization.json.JsonPrimitive("user"))
                put("content", buildOpenAiContent(prompt, attachments))
            })
        }
        val requestJson = kotlinx.serialization.json.buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive(modelName))
            put("stream", kotlinx.serialization.json.JsonPrimitive(true))
            put("temperature", kotlinx.serialization.json.JsonPrimitive(temperature))
            put("top_p", kotlinx.serialization.json.JsonPrimitive(topP))
            put("max_tokens", kotlinx.serialization.json.JsonPrimitive(maxTokens))
            put("messages", messagesArray)
        }
        val requestBody = okhttp3.RequestBody.create("application/json".toMediaType(), requestJson.toString())
        val endpointUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
        val headers = mutableMapOf("Authorization" to "Bearer $cleanApiKey", "Content-Type" to "application/json")
        if (provider == com.sednium.localspaces.model.ModelProvider.OPENROUTER) {
            headers["HTTP-Referer"] = "https://github.com/sednium/localspaces"
            headers["X-Title"] = "LocalSpaces AI"
        }

        try {
            val response = RetrofitClient.genericService.postChatCompletions(endpointUrl, headers, requestBody)
            response.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("data: ") && line != "data: [DONE]") {
                        val data = line!!.removePrefix("data: ")
                        try {
                            val chunk = Json.parseToJsonElement(data).jsonObject
                            val text = chunk["choices"]?.jsonArray
                                ?.getOrNull(0)?.jsonObject
                                ?.get("delta")?.jsonObject
                                ?.get("content")?.jsonPrimitive?.content ?: ""
                            if (text.isNotEmpty()) onChunkReceived(text, null)
                        } catch (e: Exception) {}
                    } else if (line!!.startsWith("{")) {
                        // might be an error or unstreamed reply
                        if(line!!.contains("error")) {
                             onChunkReceived("Error: ${line}\n", null)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = if (e is retrofit2.HttpException) {
                try {
                    val errorBody = e.response()?.errorBody()?.string()
                    if (errorBody != null) {
                        try {
                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(errorBody).jsonObject
                            json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "HTTP ${e.code()}"
                        } catch (ex: Exception) { "HTTP ${e.code()}" }
                    } else "HTTP ${e.code()}"
                } catch (ex: Exception) { "HTTP ${e.code()}" }
            } else if (e is java.net.SocketTimeoutException) {
                "Connection timed out. The server might be experiencing high demand."
            } else if (e is java.net.UnknownHostException) {
                "Network error: Unable to resolve host."
            } else {
                e.message ?: "Unknown error"
            }
            onChunkReceived("Error: $errorMsg\n", null)
        }
    }
}

suspend fun testApiKey(apiKey: String, provider: com.sednium.localspaces.model.ModelProvider, localBaseUrl: String): Boolean = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() && provider != com.sednium.localspaces.model.ModelProvider.LOCAL && provider != com.sednium.localspaces.model.ModelProvider.CUSTOM) return@withContext false
    try {
        val models = fetchDynamicModels(apiKey, provider, localBaseUrl)
        models.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

suspend fun fetchDynamicModels(apiKey: String, provider: com.sednium.localspaces.model.ModelProvider, localBaseUrl: String): List<com.sednium.localspaces.model.ModelOption> = withContext(Dispatchers.IO) {
    val cleanApiKey = apiKey.trim()
    if (cleanApiKey.isBlank() && provider != com.sednium.localspaces.model.ModelProvider.LOCAL && provider != com.sednium.localspaces.model.ModelProvider.CUSTOM) return@withContext emptyList()
    
    val baseUrl = com.sednium.localspaces.model.PROVIDER_CONFIG[provider]?.defaultUrl ?: ""
    val normalizedBaseUrl = if (provider == com.sednium.localspaces.model.ModelProvider.LOCAL || provider == com.sednium.localspaces.model.ModelProvider.CUSTOM) localBaseUrl else baseUrl

    try {
        if (provider == com.sednium.localspaces.model.ModelProvider.GOOGLE) {
            val response = RetrofitClient.service.listModels(cleanApiKey).string()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val models = root["models"]?.jsonArray
            return@withContext models?.mapNotNull {
                val name = it.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/") ?: return@mapNotNull null
                val displayName = it.jsonObject["displayName"]?.jsonPrimitive?.content ?: name
                val lowerName = name.lowercase()
                val icon = when {
                    lowerName.contains("vision") -> com.sednium.localspaces.model.ModelIconType.IMAGE
                    lowerName.contains("flash") -> com.sednium.localspaces.model.ModelIconType.LIGHTNING
                    lowerName.contains("pro") -> com.sednium.localspaces.model.ModelIconType.AGENT
                    lowerName.contains("code") -> com.sednium.localspaces.model.ModelIconType.CODE
                    else -> com.sednium.localspaces.model.ModelIconType.AUTO
                }
                com.sednium.localspaces.model.ModelOption(name, displayName, icon)
            } ?: emptyList()
        }

        // Generic OpenAI-compatible list models (Anthropic also using similar list models)
        val url = if (normalizedBaseUrl.endsWith("/")) "${normalizedBaseUrl}models" else "$normalizedBaseUrl/models"
        val headers = if (provider == com.sednium.localspaces.model.ModelProvider.ANTHROPIC) {
            mapOf("x-api-key" to cleanApiKey, "anthropic-version" to "2023-06-01")
        } else if (provider == com.sednium.localspaces.model.ModelProvider.OPENROUTER) {
            mapOf("Authorization" to "Bearer $cleanApiKey", "HTTP-Referer" to "https://github.com/sednium/localspaces", "X-Title" to "LocalSpaces AI")
        } else {
            mapOf("Authorization" to "Bearer $cleanApiKey")
        }
        val response = RetrofitClient.genericService.getModels(url, headers).string()
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(response).jsonObject
        // Anthropic returns array in "data", but wait, Anthropic models endpoint returns `{ type: "list", data: [ { type: "model", id: "claude-..." } ] }`
        val data = root["data"]?.jsonArray
        return@withContext data?.mapNotNull {
            val id = it.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val displayName = it.jsonObject["display_name"]?.jsonPrimitive?.content ?: id
            val lowerName = id.lowercase()
            val icon = when {
                lowerName.contains("vision") || lowerName.contains("vl") -> com.sednium.localspaces.model.ModelIconType.IMAGE
                lowerName.contains("think") || lowerName.contains("reason") || lowerName.contains("pro") || lowerName.contains("sonnet") || lowerName.contains("opus") -> com.sednium.localspaces.model.ModelIconType.AGENT
                lowerName.contains("code") -> com.sednium.localspaces.model.ModelIconType.CODE
                lowerName.contains("flash") || lowerName.contains("mini") || lowerName.contains("nano") || lowerName.contains("scout") || lowerName.contains("haiku") -> com.sednium.localspaces.model.ModelIconType.LIGHTNING
                else -> com.sednium.localspaces.model.ModelIconType.AUTO
            }
            com.sednium.localspaces.model.ModelOption(id, displayName, icon) // Many providers don't give a "displayName"
        } ?: emptyList()
    } catch (e: Exception) {
        return@withContext emptyList()
    }
}
