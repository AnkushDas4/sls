package com.sednium.localspaces.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicLong

class McpClient(
    private val config: com.sednium.localspaces.model.MCPConfig,
    private val clientInfo: Implementation = Implementation(name = "sednium-local-spaces-android", version = "1.0.0")
) {
    private val transport = StreamableHttpTransport(endpoint = config.url, authToken = config.authToken)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val nextId = AtomicLong(0)

    var serverInfo: Implementation? = null
        private set
    var serverCapabilities: ServerCapabilities? = null
        private set
    var cachedTools: List<Tool> = emptyList()
        private set

    private fun newRequest(method: String, params: JsonObject?) =
        JsonRpcRequest(id = JsonPrimitive(nextId.incrementAndGet()), method = method, params = params)

    suspend fun initialize(): InitializeResult {
        val params = json.encodeToJsonElement(
            InitializeParams.serializer(),
            InitializeParams(
                capabilities = ClientCapabilities(),
                clientInfo = clientInfo
            )
        ).jsonObject

        val response = transport.request(newRequest("initialize", params))
        response.error?.let { throw McpProtocolException(it) }
        val result = json.decodeFromJsonElement(InitializeResult.serializer(), response.result!!)

        serverInfo = result.serverInfo
        serverCapabilities = result.capabilities

        transport.sendNotification(JsonRpcNotification(method = "notifications/initialized"))

        return result
    }

    suspend fun listTools(): List<Tool> {
        val response = transport.request(newRequest("tools/list", null))
        response.error?.let { throw McpProtocolException(it) }
        val result = json.decodeFromJsonElement(ListToolsResult.serializer(), response.result!!)
        cachedTools = result.tools
        return result.tools
    }

    suspend fun callTool(name: String, arguments: JsonObject): CallToolResult {
        val params = json.encodeToJsonElement(
            CallToolParams.serializer(),
            CallToolParams(name = name, arguments = arguments)
        ).jsonObject

        val response = transport.request(newRequest("tools/call", params))
        response.error?.let { throw McpProtocolException(it) }
        return json.decodeFromJsonElement(CallToolResult.serializer(), response.result!!)
    }

    fun close() = transport.close()
}

class McpProtocolException(val rpcError: JsonRpcError) :
    Exception("MCP error ${rpcError.code}: ${rpcError.message}")
