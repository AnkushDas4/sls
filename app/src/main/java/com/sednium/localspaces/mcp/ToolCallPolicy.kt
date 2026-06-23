package com.sednium.localspaces.mcp

data class ToolCallPolicy(
    val maxIterations: Int = 8,
    val maxConcurrentCalls: Int = 4,
    val perCallTimeoutMs: Long = 30_000,
    val maxRetries: Int = 2,
    val retryBaseDelayMs: Long = 500,
    val maxResultChars: Int = 8_000,
    val confirmDestructiveCalls: Boolean = true
)
