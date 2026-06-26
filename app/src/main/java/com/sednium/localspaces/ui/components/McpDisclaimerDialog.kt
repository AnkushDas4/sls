package com.sednium.localspaces.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * Shown once, the first time a user tries to add an MCP server. MCP servers can
 * execute arbitrary tool calls on the model's behalf (read files, hit network
 * endpoints, etc.), so we make sure the user has explicitly acknowledged that
 * before any server config is ever persisted or connected to.
 */
@Composable
fun McpDisclaimerDialog(
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            color = SedniumColors.SedYellow
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = SedniumColors.Orange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Before you connect a server",
                        color = SedniumColors.Orange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.padding(top = 8.dp))

                Text(
                    text = "MCP servers let the model call tools on a remote endpoint you " +
                        "choose — reading files, hitting APIs, or anything else the server " +
                        "exposes. Only connect servers you trust, and only paste tokens for " +
                        "servers you control. Sednium does not vet third-party MCP servers " +
                        "or marketplace presets.",
                    color = OrangeAlpha.a70,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Text(
                    text = "You can disable individual tools per server, or remove a server " +
                        "entirely, at any time from the MCP Servers screen.",
                    color = OrangeAlpha.a70,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Not now", color = OrangeAlpha.a70)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onAcknowledge) {
                        Text("I understand", color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
