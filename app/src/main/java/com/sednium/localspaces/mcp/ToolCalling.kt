package com.sednium.localspaces.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * This file is the actual replacement for the original project's fake
 * tool system. There, `enableTools` just appended this to the prompt:
 *
 *   "TOOLS AVAILABLE: ... output exactly this XML format:
 *    <tool_call><name>tool_name</name><args>{...}</args></tool_call>"
 *
 * ...and a regex in App.tsx looked for that tag only to print
 * "Tool Executed" with "(Download support integration pending)" — the
 * tool never actually ran, and MCP servers were never queried for what
 * tools they even offered.
 *
 * Real LLM providers (OpenAI, Anthropic, Google, and every OpenAI-
 * compatible endpoint — xAI/Groq/OpenRouter/NVIDIA/local/custom, six of
 * the app's ten providers) already have native, structured function-
 * calling support: you send a `tools` array with each tool's JSON
 * Schema, and the API returns a structured tool-call object instead of
 * hoping the model emits well-formed XML. This file bridges MCP's
 * `tools/list` output into that structured format, and runs the loop
 * that actually executes the calls against the right MCP server.
 */

// ---- Provider-agnostic tool/turn vocabulary ----

data class LlmTool(
    val qualifiedName: String,      // "<mcpServerId>::<toolName>", see McpServerManager
    val description: String?,
    val parameters: JsonObject      // JSON Schema, taken directly from Tool.inputSchema
)

data class LlmToolCall(
    val callId: String,             // provider-assigned id, echoed back in the tool-result turn
    val qualifiedName: String,
    val arguments: JsonObject
)

sealed class LlmChatTurn {
    data class User(val text: String) : LlmChatTurn()
    data class Assistant(val text: String) : LlmChatTurn()
    data class ToolResult(val callId: String, val qualifiedName: String, val content: String, val isError: Boolean) : LlmChatTurn()
}

sealed class LlmTurnResult {
    data class FinalText(val content: String) : LlmTurnResult()
    data class ToolCalls(val calls: List<LlmToolCall>, val assistantPreface: String? = null) : LlmTurnResult()
}

/** Implement this once per provider family (OpenAI-style, Anthropic, Gemini). */
interface ToolCallingChatClient {
    suspend fun send(history: List<LlmChatTurn>, tools: List<LlmTool>): LlmTurnResult
}

fun QualifiedTool.toLlmTool(): LlmTool = LlmTool(
    qualifiedName = qualifiedName,
    description = tool.description,
    parameters = tool.inputSchema
)

// ---- The agent loop ----

class ToolCallOrchestrator(
    private val mcpServers: McpServerManager,
    private val llm: ToolCallingChatClient,
    private val maxIterations: Int = 8
) {
    /**
     * Runs until the model returns plain text instead of tool calls, or
     * `maxIterations` is hit (a hard ceiling against infinite tool-call
     * loops — a misbehaving server or a confused model can otherwise
     * spin forever).
     */
    suspend fun run(userMessage: String, priorHistory: List<LlmChatTurn> = emptyList()): String {
        val history = priorHistory.toMutableList()
        history += LlmChatTurn.User(userMessage)

        val tools = mcpServers.availableTools.map { it.toLlmTool() }

        repeat(maxIterations) {
            when (val turn = llm.send(history, tools)) {
                is LlmTurnResult.FinalText -> return turn.content

                is LlmTurnResult.ToolCalls -> {
                    turn.assistantPreface?.let { history += LlmChatTurn.Assistant(it) }
                    for (call in turn.calls) {
                        val resultTurn = try {
                            val result = mcpServers.callTool(call.qualifiedName, call.arguments)
                            LlmChatTurn.ToolResult(
                                callId = call.callId,
                                qualifiedName = call.qualifiedName,
                                content = result.content.joinToString("\n") { it.toPlainText() },
                                isError = result.isError
                            )
                        } catch (e: Exception) {
                            LlmChatTurn.ToolResult(
                                callId = call.callId,
                                qualifiedName = call.qualifiedName,
                                content = "Tool execution failed: ${e.message}",
                                isError = true
                            )
                        }
                        history += resultTurn
                    }
                }
            }
        }
        return "I wasn't able to finish after $maxIterations tool-call rounds — the task may need a narrower request."
    }
}

private fun ContentBlock.toPlainText(): String = when (this) {
    is ContentBlock.Text -> text
    is ContentBlock.Image -> "[image: $mimeType, ${data.length} base64 chars]"
    is ContentBlock.Audio -> "[audio: $mimeType]"
    is ContentBlock.ResourceLink -> "[resource: ${name ?: uri} ($uri)]"
    is ContentBlock.EmbeddedResource -> "[embedded resource]"
}

// ---- Concrete schema mapping for the six OpenAI-compatible providers ----
// (OpenAI, xAI, Groq, OpenRouter, NVIDIA NIM, Local/Ollama, Custom all speak
// this exact `tools` / `tool_calls` shape on /v1/chat/completions.)

object OpenAiToolSchema {

    /** Builds the `tools` array to send alongside the chat-completions request body. */
    fun buildToolsParam(tools: List<LlmTool>): JsonArray = buildJsonArray {
        tools.forEach { t ->
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", t.qualifiedName.replace("::", "__")) // OpenAI tool names disallow "::"
                    t.description?.let { put("description", it) }
                    put("parameters", t.parameters)
                }
            })
        }
    }

    /** Parses the `tool_calls` array out of a chat-completions response message. */
    fun parseToolCalls(messageJson: JsonObject): List<LlmToolCall>? {
        val rawCalls = messageJson["tool_calls"] as? JsonArray ?: return null
        return rawCalls.map { callElement ->
            val call = callElement as JsonObject
            val function = call["function"] as JsonObject
            val rawName = (function["name"] as JsonPrimitive).content
            val qualifiedName = rawName.replace("__", "::")
            val argsText = (function["arguments"] as JsonPrimitive).content
            LlmToolCall(
                callId = (call["id"] as JsonPrimitive).content,
                qualifiedName = qualifiedName,
                arguments = kotlinx.serialization.json.Json.parseToJsonElement(argsText) as JsonObject
            )
        }
    }
}
