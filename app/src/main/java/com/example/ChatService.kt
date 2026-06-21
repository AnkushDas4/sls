package com.example

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

object ChatService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun streamChatResponse(
        history: List<Message>,
        newMessage: String,
        settings: AppSettings,
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) {
        val providerName = settings.provider.name
        try {
            when (settings.provider) {
                ProviderType.OPENAI -> {
                    val apiKey = settings.openaiApiKey
                    if (apiKey.isBlank()) throw Exception("Please enter your OpenAI API Key.")
                    streamOpenAICompatible(
                        baseUrl = "https://api.openai.com/v1",
                        apiKey = apiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "gpt-4o",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.XAI -> {
                    val apiKey = settings.xaiApiKey
                    if (apiKey.isBlank()) throw Exception("Please enter your xAI API Key.")
                    streamOpenAICompatible(
                        baseUrl = "https://api.x.ai/v1",
                        apiKey = apiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "grok-beta",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.NVIDIA -> {
                    val apiKey = settings.nvidiaApiKey
                    if (apiKey.isBlank()) throw Exception("Please enter your NVIDIA API Key.")
                    streamOpenAICompatible(
                        baseUrl = "https://integrate.api.nvidia.com/v1",
                        apiKey = apiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "meta/llama-3.1-8b-instruct",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken,
                        maxTokens = 1024
                    )
                    onDone()
                }
                ProviderType.OPENROUTER -> {
                    val apiKey = settings.openrouterApiKey
                    if (apiKey.isBlank()) throw Exception("Please enter your OpenRouter API Key.")
                    streamOpenAICompatible(
                        baseUrl = "https://openrouter.ai/api/v1",
                        apiKey = apiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "openrouter/auto",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.GROQ -> {
                    val apiKey = settings.groqApiKey
                    if (apiKey.isBlank()) throw Exception("Please enter your Groq API Key.")
                    streamOpenAICompatible(
                        baseUrl = "https://api.groq.com/openai/v1",
                        apiKey = apiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "llama-3.3-70b-versatile",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.LOCAL -> {
                    val baseUrl = settings.localBaseUrl.ifBlank { "http://10.0.2.2:11434/v1" }
                    streamOpenAICompatible(
                        baseUrl = baseUrl,
                        apiKey = "sk-local",
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "llama3",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.CUSTOM -> {
                    val baseUrl = settings.localBaseUrl.ifBlank { "http://10.0.2.2:11434/v1" }
                    streamOpenAICompatible(
                        baseUrl = baseUrl,
                        apiKey = settings.customApiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "custom-model",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.ANTHROPIC -> {
                    val apiKey = settings.anthropicApiKey
                    if (apiKey.isBlank()) throw Exception("Please enter your Anthropic API Key.")
                    streamAnthropic(
                        apiKey = apiKey,
                        modelName = if (settings.modelName.isNotBlank()) settings.modelName else "claude-3-5-sonnet-20241022",
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.GEMINI -> {
                    val apiKey = settings.geminiApiKey.trim()
                    if (apiKey.isBlank()) throw Exception("Please enter your Gemini API Key.")
                    streamGemini(
                        apiKey = apiKey,
                        modelName = (if (settings.modelName.isNotBlank()) settings.modelName else "gemini-3.5-flash").trim(),
                        settings = settings,
                        history = history,
                        newMessage = newMessage,
                        onToken = onToken
                    )
                    onDone()
                }
                ProviderType.NATIVE_ANDROID -> {
                    throw Exception("NATIVE_ANDROID should be handled directly by the engine.")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e.message?.contains("cancel", true) == true) {
                // Ignore
            } else if (e is java.net.UnknownHostException) {
                onError("Network Error: Unable to reach the API provider. Please check your internet connection or ensure your DNS/firewall isn't blocking the connection.")
            } else {
                onError("Error: ${e.message}")
            }
        }
    }

    private suspend fun streamGemini(
        apiKey: String,
        modelName: String,
        settings: AppSettings,
        history: List<Message>,
        newMessage: String,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val contentsArray = JSONArray()

        // Converse history
        for (msg in history) {
            val obj = JSONObject()
            obj.put("role", if (msg.role == Role.USER) "user" else "model")
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", msg.content)
            partsArray.put(partObj)
            obj.put("parts", partsArray)
            contentsArray.put(obj)
        }

        // Current prompt
        val lastObj = JSONObject()
        lastObj.put("role", "user")
        val lastPartsArray = JSONArray()
        val lastPartObj = JSONObject()
        lastPartObj.put("text", newMessage)
        lastPartsArray.put(lastPartObj)
        lastObj.put("parts", lastPartsArray)
        contentsArray.put(lastObj)

        val jsonBody = JSONObject()
        jsonBody.put("contents", contentsArray)

        // System Instruction
        if (settings.systemInstruction.isNotBlank()) {
            val sysInstructionObj = JSONObject()
            val sysPartsArray = JSONArray()
            val sysPartObj = JSONObject()
            sysPartObj.put("text", settings.systemInstruction)
            sysPartsArray.put(sysPartObj)
            sysInstructionObj.put("parts", sysPartsArray)
            jsonBody.put("systemInstruction", sysInstructionObj)
        }

        // Generation Config
        val genConfigObj = JSONObject()
        genConfigObj.put("temperature", settings.temperature)
        jsonBody.put("generationConfig", genConfigObj)

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?key=$apiKey&alt=sse")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val call = client.newCall(request)
        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }

        val response = call.execute()
        if (!response.isSuccessful) {
            val code = response.code
            val errBody = response.body?.string()
            throw Exception("Gemini API Error ($code): $errBody")
        }

        response.body?.source()?.inputStream()?.bufferedReader()?.use { reader ->
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    try {
                        val obj = JSONObject(data)
                        val candidates = obj.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val contentObj = firstCandidate.optJSONObject("content")
                            val parts = contentObj?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text")
                                if (!text.isNullOrEmpty()) {
                                    onToken(text)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore empty or malformed line chunks
                    }
                }
            }
        }
    }

    private suspend fun streamOpenAICompatible(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        settings: AppSettings,
        history: List<Message>,
        newMessage: String,
        onToken: (String) -> Unit,
        maxTokens: Int? = null
    ) = withContext(Dispatchers.IO) {
        
        val messagesArray = JSONArray()
        if (settings.systemInstruction.isNotBlank()) {
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", settings.systemInstruction)
            messagesArray.put(sysObj)
        }

        for (msg in history) {
            val obj = JSONObject()
            obj.put("role", if (msg.role == Role.USER) "user" else "assistant")
            obj.put("content", msg.content)
            messagesArray.put(obj)
        }

        val lastObj = JSONObject()
        lastObj.put("role", "user")
        lastObj.put("content", newMessage)
        messagesArray.put(lastObj)

        val jsonBody = JSONObject()
        jsonBody.put("model", modelName)
        jsonBody.put("messages", messagesArray)
        jsonBody.put("temperature", settings.temperature)
        jsonBody.put("stream", true)
        if (maxTokens != null) {
            jsonBody.put("max_tokens", maxTokens)
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val targetUrl = if (baseUrl.endsWith("/chat/completions") || baseUrl.endsWith("/messages")) {
            baseUrl
        } else {
            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            "$normalizedBase/chat/completions"
        }

        val request = Request.Builder()
            .url(targetUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        
        val call = client.newCall(request)
        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
        
        val response = call.execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.body?.string()}")
        }

        response.body?.source()?.inputStream()?.bufferedReader()?.use { reader ->
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = JSONObject(data)
                        val choices = obj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content")
                            if (!content.isNullOrEmpty()) {
                                onToken(content)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore empty or unparseables
                    }
                }
            }
        }
    }

    private suspend fun streamAnthropic(
        apiKey: String,
        modelName: String,
        settings: AppSettings,
        history: List<Message>,
        newMessage: String,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        val messagesArray = JSONArray()
        for (msg in history) {
            val obj = JSONObject()
            obj.put("role", if (msg.role == Role.USER) "user" else "assistant")
            obj.put("content", msg.content)
            messagesArray.put(obj)
        }

        val lastObj = JSONObject()
        lastObj.put("role", "user")
        lastObj.put("content", newMessage)
        messagesArray.put(lastObj)

        val jsonBody = JSONObject()
        jsonBody.put("model", modelName)
        jsonBody.put("max_tokens", 4096)
        if (settings.systemInstruction.isNotBlank()) {
            jsonBody.put("system", settings.systemInstruction)
        }
        jsonBody.put("messages", messagesArray)
        jsonBody.put("stream", true)
        jsonBody.put("temperature", settings.temperature)

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .build()
        
        val call = client.newCall(request)
        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
        
        val response = call.execute()
        if (!response.isSuccessful) {
            val code = response.code
            val errBody = response.body?.string()
            if (code == 401) throw Exception("Invalid Anthropic API key.")
            throw Exception("Anthropic Error ($code): $errBody")
        }

        response.body?.source()?.inputStream()?.bufferedReader()?.use { reader ->
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    try {
                        val obj = JSONObject(data)
                        val type = obj.optString("type")
                        if (type == "message_stop") break
                        if (type == "content_block_delta") {
                            val delta = obj.optJSONObject("delta")
                            if (delta?.optString("type") == "text_delta") {
                                val text = delta.optString("text")
                                if (text.isNotEmpty()) {
                                    onToken(text)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore empty or unparseables
                    }
                }
            }
        }
    }
}
