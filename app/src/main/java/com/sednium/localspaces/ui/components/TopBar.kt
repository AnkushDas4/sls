package com.sednium.localspaces.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.material.icons.filled.Share

import com.sednium.localspaces.navigation.LocalServerStatus
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color

/**
 * Direct port of the <div className="flex-none h-14 ..."> header in App.tsx.
 * Background: bg-sedYellow/90 + backdrop-blur-sm -> emulated with a flat
 * 90%-alpha fill (Android doesn't get free backdrop-filter on a Box).
 */
@Composable
fun SedniumTopBar(
    title: String,
    subtitle: String,
    localServerStatus: LocalServerStatus? = null,
    showClear: Boolean,
    showExport: Boolean = true,
    isFocusMode: Boolean = false,
    onMenuClick: () -> Unit,
    onExportClick: () -> Unit = {},
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFocusModeToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SedniumColors.Milk.copy(alpha = 0.92f))
            .statusBarsPadding()
            .height(56.dp) // h-14
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isFocusMode) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, contentDescription = "Chats", tint = SedniumColors.Orange)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "Sednium AI" },
                    style = MaterialTheme.typography.titleMedium,
                    color = SedniumColors.Orange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangeAlpha.a70,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (localServerStatus != null && localServerStatus != LocalServerStatus.UNKNOWN) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val statusColor = when (localServerStatus) {
                            LocalServerStatus.IDLE -> Color(0xFF4CAF50)
                            LocalServerStatus.PROCESSING -> Color(0xFFFFC107)
                            LocalServerStatus.OFFLINE -> Color(0xFFF44336)
                            else -> Color.Transparent
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }
            }
        }

        if (showExport && !isFocusMode) {
            IconButton(onClick = onExportClick) {
                Icon(Icons.Filled.Share, contentDescription = "Export chat", tint = SedniumColors.Orange)
            }
        }
        
        val showClearConfirmDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        if (showClearConfirmDialog.value) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showClearConfirmDialog.value = false },
                title = { Text("Clear Chat", color = SedniumColors.Orange) },
                text = { Text("Are you sure you want to clear all messages in this chat?", color = MaterialTheme.colorScheme.onSurface) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        onClearClick()
                        showClearConfirmDialog.value = false
                    }) {
                        Text("Clear", color = SedniumColors.Red600)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showClearConfirmDialog.value = false }) {
                        Text("Cancel", color = SedniumColors.Orange)
                    }
                },
                containerColor = SedniumColors.Milk
            )
        }

        if (showClear && !isFocusMode) {
            IconButton(onClick = { showClearConfirmDialog.value = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear chat", tint = SedniumColors.Orange)
            }
        }
        IconButton(onClick = onFocusModeToggle) {
            Icon(if (isFocusMode) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility, contentDescription = "Focus Mode", tint = SedniumColors.Orange)
        }
        if (!isFocusMode) {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = SedniumColors.Orange)
            }
        }
    }
}
