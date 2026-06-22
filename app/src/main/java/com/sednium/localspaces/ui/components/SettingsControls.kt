package com.sednium.localspaces.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.mcp.McpServerStatusInfo
import com.sednium.localspaces.model.McpConnectionStatus
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii
import com.sednium.localspaces.ui.theme.SpinningIcon

/**
 * One row in the MCP Servers section — a real status dot driven by
 * McpServerManager.statuses, not just a name/URL string sitting inert
 * in a list like the original SettingsDrawer.tsx.
 */
@Composable
fun McpServerRow(info: McpServerStatusInfo, onRetry: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SedniumRadii.sm))
            .background(SedRedAlpha.a05)
            .border(1.dp, SedRedAlpha.a20, RoundedCornerShape(SedniumRadii.sm))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(info.status)

        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(info.config.name, fontWeight = FontWeight.Bold, color = SedniumColors.SedRed, style = MaterialTheme.typography.bodySmall)
            Text(
                when (info.status) {
                    McpConnectionStatus.CONNECTED -> "${info.tools.size} tool${if (info.tools.size == 1) "" else "s"} · ${info.config.url}"
                    McpConnectionStatus.CONNECTING -> "Connecting…"
                    McpConnectionStatus.ERROR -> info.error ?: "Connection failed"
                    McpConnectionStatus.DISCONNECTED -> "Disconnected"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (info.status == McpConnectionStatus.ERROR) SedniumColors.Red600 else SedRedAlpha.a60,
                maxLines = 1
            )
        }

        if (info.status == McpConnectionStatus.CONNECTING) {
            SpinningIcon(icon = Icons.Filled.Refresh, tint = SedRedAlpha.a60, size = 16.dp)
        } else if (info.status == McpConnectionStatus.ERROR) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = SedniumColors.SedRed)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = SedRedAlpha.a60)
        }
    }
}

@Composable
private fun StatusDot(status: McpConnectionStatus) {
    val color = when (status) {
        McpConnectionStatus.CONNECTED -> SedniumColors.Green500
        McpConnectionStatus.CONNECTING -> SedniumColors.Orange500
        McpConnectionStatus.ERROR -> SedniumColors.Red500
        McpConnectionStatus.DISCONNECTED -> SedniumColors.Gray400
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
    )
}

/** Real validation dialog: name + URL + optional bearer token, replacing the original's bare two-field form. */
@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, authToken: String?) -> Unit
) {
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var url by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("https://") }
    var token by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server", color = SedniumColors.SedRed, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Streamable HTTP endpoint URL") }, singleLine = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Bearer token (optional)") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), url.trim(), token.trim().ifBlank { null }) },
                enabled = name.isNotBlank() && url.startsWith("http")
            ) { Text("Connect", color = SedniumColors.SedRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SedRedAlpha.a70) } }
    )
}
