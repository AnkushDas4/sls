package com.sednium.localspaces.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/** Real validation dialog: name + URL + optional bearer token, replacing the original's bare two-field form. */
@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, authToken: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server", color = SedniumColors.Orange, fontWeight = FontWeight.Bold) },
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
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), url.trim(), token.trim().ifBlank { null }) },
                enabled = name.isNotBlank() && url.startsWith("http")
            ) { Text("Connect", color = SedniumColors.Orange, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
