package com.sednium.localspaces.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val MCP_PROTOCOL_VERSION = "2025-11-25"

@Serializable
data class Implementation(
    val name: String,
    val version: String
)

@Serializable
data class ClientCapabilities(
    val roots: JsonObject? = null,
    val sampling: JsonObject? = null,
    val elicitation: JsonObject? = null
)

@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability? = null,
    val resources: JsonObject? = null,
    val prompts: JsonObject? = null,
    val logging: JsonObject? = null
)

@Serializable
data class ToolsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class InitializeParams(
    val protocolVersion: String = MCP_PROTOCOL_VERSION,
    val capabilities: ClientCapabilities,
    val clientInfo: Implementation
)

@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: Implementation,
    val instructions: String? = null
)

// ---- tools/list ----

@Serializable
data class Tool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject,
    val annotations: ToolAnnotations? = null
)

@Serializable
data class ToolAnnotations(
    val title: String? = null,
    val readOnlyHint: Boolean? = null,
    val destructiveHint: Boolean? = null,
    val idempotentHint: Boolean? = null,
    val openWorldHint: Boolean? = null
)

@Serializable
data class ListToolsResult(
    val tools: List<Tool>,
    val nextCursor: String? = null
)

// ---- tools/call ----

@Serializable
data class CallToolParams(
    val name: String,
    val arguments: JsonObject
)

@Serializable
data class CallToolResult(
    val content: List<ContentBlock>,
    val isError: Boolean = false,
    val structuredContent: JsonObject? = null
)

@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(val data: String, val mimeType: String) : ContentBlock()

    @Serializable
    @SerialName("audio")
    data class Audio(val data: String, val mimeType: String) : ContentBlock()

    @Serializable
    @SerialName("resource_link")
    data class ResourceLink(val uri: String, val name: String? = null, val mimeType: String? = null) : ContentBlock()

    @Serializable
    @SerialName("resource")
    data class EmbeddedResource(val resource: JsonObject) : ContentBlock()
}
