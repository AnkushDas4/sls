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
}

interface GeminiApiService {
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

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    val genericService: GenericApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/") // Dummy base URL, we use @Url in method
            .client(okHttpClient)
            .build()
            .create(GenericApiService::class.java)
    }
}

suspend fun generateContentStream(
    apiKey: String,
    modelName: String,
    prompt: String,
    history: List<com.sednium.localspaces.model.ChatMessage>,
    onChunkReceived: (String, String?) -> Unit // (deltaText, deltaThought)
) = withContext(Dispatchers.IO) {
    val contents = history.map { msg ->
        Content(
            role = if (msg.role == com.sednium.localspaces.model.Role.USER) "user" else "model",
            parts = listOf(Part(text = msg.content))
        )
    } + Content("user", listOf(Part(text = prompt)))

    val request = GenerateContentRequest(
        contents = contents
    )

    try {
        val response = RetrofitClient.service.generateContentStream(modelName, apiKey, request)
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
                        if (text != null) {
                            onChunkReceived(text, null)
                        }
                    } catch (e: Exception) {
                        // Handle parsing errors
                    }
                }
            }
        }
    } catch (e: Exception) {
        onChunkReceived("Error: ${e.message}\n", null)
    }
}

suspend fun testApiKey(apiKey: String, provider: com.sednium.localspaces.model.ModelProvider): Boolean = withContext(Dispatchers.IO) {
    if (provider == com.sednium.localspaces.model.ModelProvider.GOOGLE) {
        try {
            val response = RetrofitClient.service.listModels(apiKey)
            response.close()
            true
        } catch (e: Exception) {
            false
        }
    } else {
        // Not implemented for other providers yet, but assume true if not empty
        apiKey.isNotBlank()
    }
}

suspend fun fetchDynamicModels(apiKey: String, provider: com.sednium.localspaces.model.ModelProvider, localBaseUrl: String): List<com.sednium.localspaces.model.ModelOption> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() && provider != com.sednium.localspaces.model.ModelProvider.LOCAL && provider != com.sednium.localspaces.model.ModelProvider.CUSTOM) return@withContext emptyList()
    
    val baseUrl = com.sednium.localspaces.model.PROVIDER_CONFIG[provider]?.defaultUrl ?: ""
    val normalizedBaseUrl = if (provider == com.sednium.localspaces.model.ModelProvider.LOCAL || provider == com.sednium.localspaces.model.ModelProvider.CUSTOM) localBaseUrl else baseUrl

    try {
        if (provider == com.sednium.localspaces.model.ModelProvider.GOOGLE) {
            val response = RetrofitClient.service.listModels(apiKey).string()
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
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
        } else {
            mapOf("Authorization" to "Bearer $apiKey")
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
