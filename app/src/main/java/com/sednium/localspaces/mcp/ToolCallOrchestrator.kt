package com.sednium.localspaces.mcp

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class LlmTool(
    val qualifiedName: String,
    val description: String?,
    val parameters: JsonObject
)

data class LlmToolCall(
    val callId: String,
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

interface ToolCallingChatClient {
    suspend fun send(history: List<LlmChatTurn>, tools: List<LlmTool>): LlmTurnResult
}

fun QualifiedTool.toLlmTool(): LlmTool = LlmTool(
    qualifiedName = qualifiedName,
    description = tool.description,
    parameters = tool.inputSchema
)

class ToolCallOrchestrator(
    private val mcpServers: McpServerManager,
    private val llm: ToolCallingChatClient,
    private val policy: ToolCallPolicy = ToolCallPolicy(),
    private val onEvent: (ToolCallEvent) -> Unit = {},
    private val confirmDestructive: suspend (LlmToolCall, Tool) -> Boolean = { _, _ -> true }
) {
    suspend fun run(userMessage: String, priorHistory: List<LlmChatTurn> = emptyList()): String {
        val history = priorHistory.toMutableList()
        history += LlmChatTurn.User(userMessage)
        val semaphore = Semaphore(policy.maxConcurrentCalls)

        repeat(policy.maxIterations) { iteration ->
            onEvent(ToolCallEvent.ModelTurn(iteration + 1))

            val qualifiedTools = mcpServers.availableTools
            val toolsByName: Map<String, Tool> = qualifiedTools.associate { it.qualifiedName to it.tool }
            val llmTools = qualifiedTools.map { it.toLlmTool() }

            when (val turn = llm.send(history, llmTools)) {
                is LlmTurnResult.FinalText -> return turn.content

                is LlmTurnResult.ToolCalls -> {
                    turn.assistantPreface?.let { history += LlmChatTurn.Assistant(it) }

                    val groups: Map<Pair<String, JsonObject>, List<LlmToolCall>> =
                        turn.calls.groupBy { it.qualifiedName to it.arguments }

                    val resultBySignature: Map<Pair<String, JsonObject>, LlmChatTurn.ToolResult> = coroutineScope {
                        groups.map { (signature, callsSharingSignature) ->
                            async {
                                val representative = callsSharingSignature.first()
                                val result = semaphore.withPermit {
                                    executeOneCall(representative, toolsByName[representative.qualifiedName])
                                }
                                signature to result
                            }
                        }.awaitAll().toMap()
                    }

                    turn.calls.forEach { call ->
                        val canonical = resultBySignature.getValue(call.qualifiedName to call.arguments)
                        history += canonical.copy(callId = call.callId)
                    }
                }
            }
        }
        return "I wasn't able to finish after ${policy.maxIterations} tool-call rounds."
    }

    private suspend fun executeOneCall(call: LlmToolCall, tool: Tool?): LlmChatTurn.ToolResult {
        onEvent(ToolCallEvent.Started(call))

        if (tool == null) {
            val msg = "Tool '${call.qualifiedName}' isn't currently available."
            onEvent(ToolCallEvent.Failed(call, msg))
            return LlmChatTurn.ToolResult(call.callId, call.qualifiedName, msg, isError = true)
        }

        validateArguments(tool, call.arguments)?.let { problem ->
            onEvent(ToolCallEvent.Failed(call, problem))
            return LlmChatTurn.ToolResult(call.callId, call.qualifiedName, problem, isError = true)
        }

        if (policy.confirmDestructiveCalls && tool.annotations?.destructiveHint == true) {
            onEvent(ToolCallEvent.AwaitingConfirmation(call))
            if (!confirmDestructive(call, tool)) {
                onEvent(ToolCallEvent.Declined(call))
                return LlmChatTurn.ToolResult(call.callId, call.qualifiedName, "The user declined to run this tool.", isError = true)
            }
        }

        var attempt = 0
        while (true) {
            try {
                val result = withTimeoutOrNull(policy.perCallTimeoutMs) {
                    mcpServers.callTool(call.qualifiedName, call.arguments)
                } ?: throw McpTransportException("Tool call timed out after ${policy.perCallTimeoutMs}ms")

                val text = result.content.joinToString("\n") { it.toPlainText() }
                val bounded = if (text.length > policy.maxResultChars) {
                    text.take(policy.maxResultChars) + "\n…[truncated ${text.length - policy.maxResultChars} more characters]"
                } else text

                onEvent(ToolCallEvent.Succeeded(call, bounded.take(120)))
                return LlmChatTurn.ToolResult(call.callId, call.qualifiedName, bounded, isError = result.isError)
            } catch (e: McpTransportException) {
                attempt++
                if (attempt > policy.maxRetries) {
                    val msg = "Tool call failed after $attempt attempt(s): ${e.message}"
                    onEvent(ToolCallEvent.Failed(call, msg))
                    return LlmChatTurn.ToolResult(call.callId, call.qualifiedName, msg, isError = true)
                }
                onEvent(ToolCallEvent.Retrying(call, attempt, e.message ?: "network error"))
                delay(policy.retryBaseDelayMs * (1L shl (attempt - 1)))
            } catch (e: Exception) {
                val msg = "Tool execution failed: ${e.message}"
                onEvent(ToolCallEvent.Failed(call, msg))
                return LlmChatTurn.ToolResult(call.callId, call.qualifiedName, msg, isError = true)
            }
        }
    }

    private fun validateArguments(tool: Tool, arguments: JsonObject): String? {
        val required = (tool.inputSchema["required"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?: emptyList()
        val missing = required.filterNot { arguments.containsKey(it) }
        return if (missing.isNotEmpty()) {
            "Missing required argument(s) for '${tool.name}': ${missing.joinToString(", ")}"
        } else null
    }
}

private fun ContentBlock.toPlainText(): String = when (this) {
    is ContentBlock.Text -> text
    is ContentBlock.Image -> "[image: $mimeType, ${data.length} base64 chars]"
    is ContentBlock.Audio -> "[audio: $mimeType]"
    is ContentBlock.ResourceLink -> "[resource: ${name ?: uri} ($uri)]"
    is ContentBlock.EmbeddedResource -> "[embedded resource]"
}

object OpenAiToolSchema {
    fun buildToolsParam(tools: List<LlmTool>): JsonArray = buildJsonArray {
        tools.forEach { t ->
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", t.qualifiedName.replace("::", "__"))
                    t.description?.let { put("description", it) }
                    put("parameters", t.parameters)
                }
            })
        }
    }

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
