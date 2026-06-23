package com.sednium.localspaces.mcp

sealed class ToolCallEvent {
    data class ModelTurn(val iteration: Int) : ToolCallEvent()
    data class Started(val call: LlmToolCall) : ToolCallEvent()
    data class Retrying(val call: LlmToolCall, val attempt: Int, val reason: String) : ToolCallEvent()
    data class AwaitingConfirmation(val call: LlmToolCall) : ToolCallEvent()
    data class Declined(val call: LlmToolCall) : ToolCallEvent()
    data class Succeeded(val call: LlmToolCall, val resultPreview: String) : ToolCallEvent()
    data class Failed(val call: LlmToolCall, val reason: String) : ToolCallEvent()
}
