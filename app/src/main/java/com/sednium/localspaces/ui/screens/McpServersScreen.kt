package com.sednium.localspaces.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.mcp.McpServerManager
import com.sednium.localspaces.model.MCPConfig
import com.sednium.localspaces.model.McpConnectionStatus
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

data class McpPreset(
    val name: String,
    val description: String,
    val url: String,
    val requiresAuth: Boolean
)

val MCP_PRESETS = listOf(
    McpPreset(
        name = "Brave Search",
        description = "Web search & summarization via Brave's secure API.",
        url = "https://brave.mcp.example.com",
        requiresAuth = true
    ),
    McpPreset(
        name = "GitHub",
        description = "Read, search, and comment on repositories and issues.",
        url = "https://github.mcp.example.com",
        requiresAuth = true
    ),
    McpPreset(
        name = "Local File System",
        description = "Read and write access to your configured local directories.",
        url = "http://localhost:3000",
        requiresAuth = false
    )
)

@Composable
fun McpServersScreen(
    mcpServerManager: McpServerManager,
    configs: List<MCPConfig>,
    onAddClick: () -> Unit,
    onPresetClick: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onReconnectAll: () -> Unit,
    onToggleTool: (serverId: String, toolName: String, enabled: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val statuses by mcpServerManager.statuses.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SedniumColors.SedYellow)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Sensors, contentDescription = null, tint = SedniumColors.Orange)
                Text("MCP Servers", style = MaterialTheme.typography.titleLarge, color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
            }
            Row {
                IconButton(onClick = onReconnectAll) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reconnect All", tint = SedniumColors.Orange)
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Server", tint = SedniumColors.Orange)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = SedniumColors.Orange)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (configs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No MCP servers connected.", color = OrangeAlpha.a60, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onAddClick) {
                    Text("Add your first server", color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Text("Your Servers", style = MaterialTheme.typography.labelMedium, color = OrangeAlpha.a60, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(configs) { config ->
                    val statusInfo = statuses[config.id]
                    McpServerCard(
                        config = config,
                        status = statusInfo?.status ?: McpConnectionStatus.ERROR,
                        error = statusInfo?.error,
                        tools = statusInfo?.tools ?: emptyList(),
                        onRemove = { onRemove(config.id) },
                        onToggleTool = { toolName, enabled -> onToggleTool(config.id, toolName, enabled) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Marketplace Presets", style = MaterialTheme.typography.labelMedium, color = OrangeAlpha.a60, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(if (configs.isEmpty()) 2f else 1f)
        ) {
            items(MCP_PRESETS) { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(OrangeAlpha.a05)
                        .clickable { onPresetClick(preset.name, preset.url) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, fontWeight = FontWeight.Bold, color = SedniumColors.Orange)
                        Text(preset.description, style = MaterialTheme.typography.labelSmall, color = OrangeAlpha.a60)
                        if (preset.requiresAuth) {
                            Text("Requires Auth", style = MaterialTheme.typography.labelSmall, color = SedniumColors.Orange, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Icon(Icons.Filled.Add, contentDescription = "Add Preset", tint = SedniumColors.Orange)
                }
            }
        }
    }
}

@Composable
fun McpServerCard(
    config: MCPConfig,
    status: McpConnectionStatus,
    error: String?,
    tools: List<com.sednium.localspaces.mcp.Tool>,
    onRemove: () -> Unit,
    onToggleTool: (toolName: String, enabled: Boolean) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val toolCount = tools.size
    val activeCount = tools.count { it.name !in config.disabledTools }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OrangeAlpha.a05)
            .border(1.dp, OrangeAlpha.a20, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(config.name, fontWeight = FontWeight.Bold, color = SedniumColors.Orange, style = MaterialTheme.typography.titleMedium)
                    when (status) {
                        McpConnectionStatus.CONNECTING -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = SedniumColors.Orange)
                            Text("Connecting", color = OrangeAlpha.a60, style = MaterialTheme.typography.labelSmall)
                        }
                        McpConnectionStatus.CONNECTED -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SedniumColors.Orange.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Connected", color = SedniumColors.Orange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        McpConnectionStatus.ERROR -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(com.sednium.localspaces.ui.theme.SedniumColors.Orange.copy(alpha = 0.1f)) // A bit redundant since everything is orange scaled
                                    .border(1.dp, SedniumColors.Orange, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Error", color = SedniumColors.Orange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(config.url, color = OrangeAlpha.a60, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (status == McpConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$activeCount of $toolCount tool${if (toolCount == 1) "" else "s"} active",
                        color = OrangeAlpha.a60,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else if (status == McpConnectionStatus.ERROR && error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error, color = SedniumColors.Orange, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == McpConnectionStatus.CONNECTED && toolCount > 0) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "Hide tools" else "Show tools",
                            tint = OrangeAlpha.a60
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove Server", tint = OrangeAlpha.a60)
                }
            }
        }

        if (expanded && status == McpConnectionStatus.CONNECTED) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(OrangeAlpha.a05)
                    .padding(horizontal = 8.dp)
            ) {
                tools.forEach { tool ->
                    val enabled = tool.name !in config.disabledTools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                tool.name,
                                color = if (enabled) SedniumColors.Orange else OrangeAlpha.a40,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            tool.description?.let {
                                Text(
                                    it,
                                    color = if (enabled) OrangeAlpha.a60 else OrangeAlpha.a40,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = enabled,
                            onCheckedChange = { onToggleTool(tool.name, it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = SedniumColors.Orange,
                                checkedTrackColor = SedniumColors.Orange.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }
    }
}
