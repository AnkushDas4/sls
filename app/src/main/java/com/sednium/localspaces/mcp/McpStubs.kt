package com.sednium.localspaces.mcp

import kotlinx.serialization.json.JsonObject

data class QualifiedTool(
    val qualifiedName: String,
    val tool: Tool
)

data class Tool(
    val description: String?,
    val inputSchema: JsonObject
)

interface McpServerManager {
    val availableTools: List<QualifiedTool>
    fun callTool(qualifiedName: String, arguments: JsonObject): ToolResult
}

data class ToolResult(
    val content: List<ContentBlock>,
    val isError: Boolean = false
)

sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Image(val mimeType: String, val data: String) : ContentBlock()
    data class Audio(val mimeType: String) : ContentBlock()
    data class ResourceLink(val name: String?, val uri: String) : ContentBlock()
    data object EmbeddedResource : ContentBlock()
}
