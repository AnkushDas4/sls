package com.sednium.localspaces.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.Serializable

/** A request id is a string or a number in JSON-RPC — represented as JsonElement to allow both. */
typealias JsonRpcId = JsonElement

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonRpcId,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonRpcId,
    val result: JsonObject? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/** Standard JSON-RPC / MCP error codes worth branching on explicitly. */
object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

fun jsonId(n: Long): JsonRpcId = JsonPrimitive(n)
