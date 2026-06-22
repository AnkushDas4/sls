package com.sednium.localspaces.mcp

import com.sednium.localspaces.model.MCPConfig
import com.sednium.localspaces.model.McpConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * Owns one McpClient per configured server, connects/disconnects them as
 * the user edits Settings, and presents a single aggregated tool catalog
 * to the rest of the app (the chat ViewModel only ever talks to this
 * class, never to an individual McpClient directly).
 *
 * Tool names are qualified as "<serverId>::<toolName>" in the aggregate
 * catalog to avoid collisions between servers that happen to expose a
 * tool with the same bare name (e.g. two unrelated servers both
 * exposing "search").
 */
data class McpServerStatusInfo(
    val config: MCPConfig,
    val status: McpConnectionStatus,
    val tools: List<Tool> = emptyList(),
    val error: String? = null
)

class McpServerManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val clients = mutableMapOf<String, McpClient>()
    private val _statuses = MutableStateFlow<Map<String, McpServerStatusInfo>>(emptyMap())
    val statuses: StateFlow<Map<String, McpServerStatusInfo>> = _statuses

    /** The flattened, qualified tool list across every currently-connected server. */
    val availableTools: List<QualifiedTool>
        get() = _statuses.value.values.flatMap { info ->
            info.tools.map { QualifiedTool(serverId = info.config.id, serverName = info.config.name, tool = it) }
        }

    fun connect(config: MCPConfig) {
        _statuses.update { it + (config.id to McpServerStatusInfo(config, McpConnectionStatus.CONNECTING)) }
        scope.launch {
            try {
                val client = McpClient(config)
                client.initialize()
                val tools = client.listTools()
                clients[config.id] = client
                _statuses.update {
                    it + (config.id to McpServerStatusInfo(config, McpConnectionStatus.CONNECTED, tools))
                }
            } catch (e: Exception) {
                _statuses.update {
                    it + (config.id to McpServerStatusInfo(config, McpConnectionStatus.ERROR, error = e.message))
                }
            }
        }
    }

    fun disconnect(serverId: String) {
        clients.remove(serverId)?.close()
        _statuses.update { it - serverId }
    }

    fun reconnectAll(configs: List<MCPConfig>) {
        clients.values.forEach { it.close() }
        clients.clear()
        _statuses.update { emptyMap() }
        configs.forEach { connect(it) }
    }

    /** Dispatches a tool/call to whichever server owns the qualified tool name. */
    suspend fun callTool(qualifiedName: String, arguments: JsonObject): CallToolResult {
        val (serverId, toolName) = qualifiedName.split("::", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else throw IllegalArgumentException("Not a qualified tool name: $qualifiedName")
        }
        val client = clients[serverId]
            ?: throw IllegalStateException("MCP server '$serverId' is not connected")
        return client.callTool(toolName, arguments)
    }

    fun shutdown() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}

data class QualifiedTool(val serverId: String, val serverName: String, val tool: Tool) {
    val qualifiedName: String get() = "$serverId::${tool.name}"
}
