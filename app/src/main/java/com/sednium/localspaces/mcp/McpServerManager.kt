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
