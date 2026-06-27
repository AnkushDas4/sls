package com.sednium.localspaces.mcp

import com.sednium.localspaces.api.Content
import com.sednium.localspaces.api.FunctionCall
import com.sednium.localspaces.api.FunctionResponse
import com.sednium.localspaces.api.GenerateContentRequest
import com.sednium.localspaces.api.GenerationConfig
import com.sednium.localspaces.api.Part
import com.sednium.localspaces.api.RetrofitClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody

/**
 * This file is the part that was actually missing for tool calling to work
 * end to end. McpServerManager/McpClient (real MCP wire protocol) and
 * ToolCallOrchestrator (the agentic loop: send turn, execute tool calls,
 * feed results back, repeat) already existed and were already correct —
 * they just had no [ToolCallingChatClient] implementation to actually talk
 * to a model with tool declarations attached. These three classes are that
 * missing piece, one per request format.
 *
 * Each `send()` call here is a single, non-streaming HTTP request — the
 * orchestrator needs one complete response to inspect for tool calls before
 * deciding what happens next, so normal token-by-token streaming doesn't
 * apply to these turns. (The plain chat path without tools still streams
 * normally — see MainActivity.kt's branch on `mcpServers.availableTools`.)
 */

private fun nextSyntheticCallId(): String = "call_" + java.util.UUID.randomUUID().toString().take(8)

private val lenientJson = Json { ignoreUnknownKeys = true }

// =====================================================================
// Gemini
// =====================================================================

class GeminiToolChatClient(
    private val apiKey: String,
    private val modelName: String,
    private val systemInstruction: String,
    private val temperature: Float,
    private val topP: Float,
    private val topK: Int,
    private val maxTokens: Int
) : ToolCallingChatClient {

    override suspend fun send(history: List<LlmChatTurn>, tools: List<LlmTool>): LlmTurnResult {
        val contents = history.map { it.toGeminiContent() }
        val sysContent = if (systemInstruction.isNotBlank()) {
            Content("user", listOf(Part(text = systemInstruction)))
        } else null

        val toolsJson: List<JsonObject>? = if (tools.isEmpty()) null else listOf(
            buildJsonObject {
                put("functionDeclarations", buildJsonArray {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", JsonPrimitive(qualifiedNameForWire(t.qualifiedName)))
                            t.description?.let { put("description", JsonPrimitive(it)) }
                            put("parameters", t.parameters)
                        })
                    }
                })
            }
        )

        val request = GenerateContentRequest(
            systemInstruction = sysContent,
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = temperature,
                topP = topP,
                topK = topK,
                maxOutputTokens = maxTokens
            ),
            tools = toolsJson
        )

        val response = RetrofitClient.service.generateContent(modelName, apiKey.trim(), request)
        val parts = response.candidates.firstOrNull()?.content?.parts ?: emptyList()

        val textBuilder = StringBuilder()
        val calls = mutableListOf<LlmToolCall>()
        parts.forEach { part ->
            part.text?.let { textBuilder.append(it) }
            part.functionCall?.let { fc ->
                calls += LlmToolCall(
                    callId = nextSyntheticCallId(),
                    qualifiedName = qualifiedNameFromWire(fc.name),
                    arguments = fc.args
                )
            }
        }

        return if (calls.isNotEmpty()) {
            LlmTurnResult.ToolCalls(calls, textBuilder.toString().ifBlank { null })
        } else {
            LlmTurnResult.FinalText(textBuilder.toString())
        }
    }

    private fun LlmChatTurn.toGeminiContent(): Content = when (this) {
        is LlmChatTurn.User -> Content("user", listOf(Part(text = text)))
        is LlmChatTurn.Assistant -> Content("model", listOf(Part(text = text)))
        is LlmChatTurn.AssistantToolCalls -> {
            val parts = mutableListOf<Part>()
            text?.let { parts.add(Part(text = it)) }
            calls.forEach { call ->
                parts.add(
                    Part(
                        functionCall = FunctionCall(
                            name = qualifiedNameForWire(call.qualifiedName),
                            args = call.arguments
                        )
                    )
                )
            }
            Content("model", parts)
        }
        is LlmChatTurn.ToolResult -> Content(
            "function",
            listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = qualifiedNameForWire(qualifiedName),
                        response = buildJsonObject {
                            put("content", JsonPrimitive(content))
                            if (isError) put("error", JsonPrimitive(true))
                        }
                    )
                )
            )
        )
    }
}

// =====================================================================
// Anthropic
// =====================================================================

class AnthropicToolChatClient(
    private val apiKey: String,
    private val modelName: String,
    private val baseUrl: String,
    private val systemInstruction: String,
    private val temperature: Float,
    private val topP: Float,
    private val topK: Int,
    private val maxTokens: Int
) : ToolCallingChatClient {

    override suspend fun send(history: List<LlmChatTurn>, tools: List<LlmTool>): LlmTurnResult {
        val toolsArray = if (tools.isEmpty()) null else buildJsonArray {
            tools.forEach { t ->
                add(buildJsonObject {
                    put("name", JsonPrimitive(qualifiedNameForWire(t.qualifiedName)))
                    t.description?.let { put("description", JsonPrimitive(it)) }
                    put("input_schema", t.parameters)
                })
            }
        }

        val requestJson = buildJsonObject {
            put("model", JsonPrimitive(modelName))
            put("max_tokens", JsonPrimitive(maxTokens))
            put("temperature", JsonPrimitive(temperature))
            put("top_p", JsonPrimitive(topP))
            put("top_k", JsonPrimitive(topK))
            put("messages", buildAnthropicMessages(history))
            if (systemInstruction.isNotBlank()) put("system", JsonPrimitive(systemInstruction))
            toolsArray?.let { put("tools", it) }
        }

        val requestBody = RequestBody.create("application/json".toMediaType(), requestJson.toString())
        val endpointUrl = if (baseUrl.endsWith("/")) "${baseUrl}messages" else "$baseUrl/messages"
        val headers = mapOf(
            "x-api-key" to apiKey.trim(),
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json"
        )

        val responseText = RetrofitClient.genericService.postChatCompletions(endpointUrl, headers, requestBody).string()
        val json = lenientJson.parseToJsonElement(responseText).jsonObject
        (json["error"] as? JsonObject)?.let { err ->
            throw Exception(err["message"]?.jsonPrimitive?.content ?: "Anthropic API error")
        }

        val contentArray = json["content"]?.jsonArray ?: JsonArray(emptyList())
        val textBuilder = StringBuilder()
        val calls = mutableListOf<LlmToolCall>()
        contentArray.forEach { block ->
            val obj = block.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> textBuilder.append(obj["text"]?.jsonPrimitive?.content ?: "")
                "tool_use" -> {
                    val id = obj["id"]?.jsonPrimitive?.content ?: nextSyntheticCallId()
                    val rawName = obj["name"]?.jsonPrimitive?.content ?: ""
                    val input = obj["input"] as? JsonObject ?: JsonObject(emptyMap())
                    calls += LlmToolCall(id, qualifiedNameFromWire(rawName), input)
                }
            }
        }

        return if (calls.isNotEmpty()) {
            LlmTurnResult.ToolCalls(calls, textBuilder.toString().ifBlank { null })
        } else {
            LlmTurnResult.FinalText(textBuilder.toString())
        }
    }
}

/**
 * Anthropic requires every tool_result for a given assistant turn's
 * tool_use calls to live together in ONE following user message. Our
 * history is a flat list with one ToolResult turn per call, so consecutive
 * ToolResult turns get merged back into a single user message here.
 */
private fun buildAnthropicMessages(history: List<LlmChatTurn>): JsonArray = buildJsonArray {
    var i = 0
    while (i < history.size) {
        when (val turn = history[i]) {
            is LlmChatTurn.User -> {
                add(buildJsonObject { put("role", JsonPrimitive("user")); put("content", JsonPrimitive(turn.text)) })
                i++
            }
            is LlmChatTurn.Assistant -> {
                add(buildJsonObject { put("role", JsonPrimitive("assistant")); put("content", JsonPrimitive(turn.text)) })
                i++
            }
            is LlmChatTurn.AssistantToolCalls -> {
                add(buildJsonObject {
                    put("role", JsonPrimitive("assistant"))
                    put("content", buildJsonArray {
                        turn.text?.let { add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(it)) }) }
                        turn.calls.forEach { call ->
                            add(buildJsonObject {
                                put("type", JsonPrimitive("tool_use"))
                                put("id", JsonPrimitive(call.callId))
                                put("name", JsonPrimitive(qualifiedNameForWire(call.qualifiedName)))
                                put("input", call.arguments)
                            })
                        }
                    })
                })
                i++
                val resultBlocks = mutableListOf<LlmChatTurn.ToolResult>()
                while (i < history.size && history[i] is LlmChatTurn.ToolResult) {
                    resultBlocks += history[i] as LlmChatTurn.ToolResult
                    i++
                }
                if (resultBlocks.isNotEmpty()) {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", buildJsonArray {
                            resultBlocks.forEach { tr ->
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("tool_result"))
                                    put("tool_use_id", JsonPrimitive(tr.callId))
                                    put("content", JsonPrimitive(tr.content))
                                    if (tr.isError) put("is_error", JsonPrimitive(true))
                                })
                            }
                        })
                    })
                }
            }
            is LlmChatTurn.ToolResult -> {
                // Defensive fallback — shouldn't normally hit this since the
                // AssistantToolCalls branch above already consumes any
                // ToolResult turns immediately following it.
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("tool_result"))
                            put("tool_use_id", JsonPrimitive(turn.callId))
                            put("content", JsonPrimitive(turn.content))
                            if (turn.isError) put("is_error", JsonPrimitive(true))
                        })
                    })
                })
                i++
            }
        }
    }
}

// =====================================================================
// OpenAI-compatible (OpenAI, Groq, OpenRouter, xAI, Nvidia, Custom)
// =====================================================================

class OpenAiCompatToolChatClient(
    private val apiKey: String,
    private val modelName: String,
    private val baseUrl: String,
    private val systemInstruction: String,
    private val temperature: Float,
    private val topP: Float,
    private val maxTokens: Int,
    private val isOpenRouter: Boolean = false
) : ToolCallingChatClient {

    override suspend fun send(history: List<LlmChatTurn>, tools: List<LlmTool>): LlmTurnResult {
        val messagesArray = buildJsonArray {
            if (systemInstruction.isNotBlank()) {
                add(buildJsonObject { put("role", JsonPrimitive("system")); put("content", JsonPrimitive(systemInstruction)) })
            }
            history.forEach { turn ->
                when (turn) {
                    is LlmChatTurn.User -> add(buildJsonObject {
                        put("role", JsonPrimitive("user")); put("content", JsonPrimitive(turn.text))
                    })
                    is LlmChatTurn.Assistant -> add(buildJsonObject {
                        put("role", JsonPrimitive("assistant")); put("content", JsonPrimitive(turn.text))
                    })
                    is LlmChatTurn.AssistantToolCalls -> add(buildJsonObject {
                        put("role", JsonPrimitive("assistant"))
                        put("content", turn.text?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("tool_calls", buildJsonArray {
                            turn.calls.forEach { call ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(call.callId))
                                    put("type", JsonPrimitive("function"))
                                    put("function", buildJsonObject {
                                        put("name", JsonPrimitive(qualifiedNameForWire(call.qualifiedName)))
                                        put("arguments", JsonPrimitive(call.arguments.toString()))
                                    })
                                })
                            }
                        })
                    })
                    is LlmChatTurn.ToolResult -> add(buildJsonObject {
                        put("role", JsonPrimitive("tool"))
                        put("tool_call_id", JsonPrimitive(turn.callId))
                        put("content", JsonPrimitive(turn.content))
                    })
                }
            }
        }

        val requestJson = buildJsonObject {
            put("model", JsonPrimitive(modelName))
            put("temperature", JsonPrimitive(temperature))
            put("top_p", JsonPrimitive(topP))
            put("max_tokens", JsonPrimitive(maxTokens))
            put("messages", messagesArray)
            if (tools.isNotEmpty()) put("tools", OpenAiToolSchema.buildToolsParam(tools))
        }

        val requestBody = RequestBody.create("application/json".toMediaType(), requestJson.toString())
        val endpointUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
        val headers = mutableMapOf("Authorization" to "Bearer ${apiKey.trim()}", "Content-Type" to "application/json")
        if (isOpenRouter) {
            headers["HTTP-Referer"] = "https://github.com/sednium/localspaces"
            headers["X-Title"] = "LocalSpaces AI"
        }

        val responseText = RetrofitClient.genericService.postChatCompletions(endpointUrl, headers, requestBody).string()
        val json = lenientJson.parseToJsonElement(responseText).jsonObject
        (json["error"] as? JsonObject)?.let { err ->
            throw Exception(err["message"]?.jsonPrimitive?.content ?: "API error")
        }

        val message = json["choices"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("message") as? JsonObject
            ?: throw Exception("Malformed response: no message in choices[0]")

        val text = (message["content"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
        val calls = OpenAiToolSchema.parseToolCalls(message)

        return if (!calls.isNullOrEmpty()) {
            LlmTurnResult.ToolCalls(calls, text.ifBlank { null })
        } else {
            LlmTurnResult.FinalText(text)
        }
    }
}

// =====================================================================
// Shared name-mangling — every provider here has restrictions on function
// names (no "::"), and OpenAiToolSchema already established the "__"
// convention for that. Kept as shared helpers so all three clients agree on
// the exact same mapping.
// =====================================================================

private fun qualifiedNameForWire(qualifiedName: String): String = qualifiedName.replace("::", "__")
private fun qualifiedNameFromWire(wireName: String): String = wireName.replace("__", "::")
