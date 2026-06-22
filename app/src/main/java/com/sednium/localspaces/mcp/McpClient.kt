package com.sednium.localspaces.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicLong

/**
 * The actual MCP client lifecycle, per spec:
 *
 *   1. connect()    — open the transport
 *   2. initialize() — exchange protocolVersion + capabilities + clientInfo,
 *                      then MUST send the `notifications/initialized`
 *                      notification before issuing any other request
 *   3. listTools()   — tools/list
 *   4. callTool()    — tools/call
 *   5. close()
 *
 * Compare this to the original project's "implementation": a string
 * naming the server was spliced into the system prompt and the model
 * was simply asked to pretend it had used it. Nothing here is
 * simulated — every method below performs a real JSON-RPC round trip.
 */
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

    /** Step 1+2: handshake. MUST be called before any other method. */
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

        // Required by spec: client MUST send this notification right after initialize succeeds,
        // before any further requests, to signal it's ready to operate.
        transport.sendNotification(JsonRpcNotification(method = "notifications/initialized"))

        return result
    }

    /** tools/list — returns (and caches) the tool catalog this server exposes. */
    suspend fun listTools(): List<Tool> {
        val response = transport.request(newRequest("tools/list", null))
        response.error?.let { throw McpProtocolException(it) }
        val result = json.decodeFromJsonElement(ListToolsResult.serializer(), response.result!!)
        cachedTools = result.tools
        return result.tools
        // NOTE: a production client should also page through `nextCursor` and should
        // subscribe to `notifications/tools/list_changed` (advertised via
        // ServerCapabilities.tools.listChanged) to know when to re-fetch.
    }

    /** tools/call — actually invokes a tool and returns its content blocks. */
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
