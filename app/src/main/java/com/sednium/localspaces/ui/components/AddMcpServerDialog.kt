package com.sednium.localspaces.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/** Real validation dialog: name + URL + optional bearer token, replacing the original's bare two-field form. */
@Composable
fun AddMcpServerDialog(
    initialName: String = "",
    initialUrl: String = "https://",
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, authToken: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }

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
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Add MCP Server",
                    color = SedniumColors.Orange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SedniumColors.Orange,
                    unfocusedBorderColor = OrangeAlpha.a30,
                    focusedLabelColor = SedniumColors.Orange,
                    unfocusedLabelColor = OrangeAlpha.a60,
                    cursorColor = SedniumColors.Orange
                )

                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name") }, 
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = url, 
                    onValueChange = { url = it },
                    label = { Text("Streamable HTTP endpoint URL") }, 
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                
                OutlinedTextField(
                    value = token, 
                    onValueChange = { token = it },
                    label = { Text("Bearer token (optional)") }, 
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { 
                        Text("Cancel", color = OrangeAlpha.a70) 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onAdd(name.trim(), url.trim(), token.trim().ifBlank { null }) },
                        enabled = name.isNotBlank() && url.startsWith("http")
                    ) { 
                        Text("Connect", color = if (name.isNotBlank() && url.startsWith("http")) SedniumColors.Orange else OrangeAlpha.a40, fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    }
}
