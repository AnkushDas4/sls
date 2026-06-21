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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * Direct port of the <div className="flex-none h-14 ..."> header in App.tsx.
 * Background: bg-sedYellow/90 + backdrop-blur-sm -> emulated with a flat
 * 90%-alpha fill (Android doesn't get free backdrop-filter on a Box).
 */
@Composable
fun SedniumTopBar(
    title: String,
    subtitle: String,
    showClear: Boolean,
    onMenuClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // h-14
            .background(SedniumColors.SedYellow.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Chats", tint = SedniumColors.SedRed)
            }
            Column {
                Text(
                    text = title.ifBlank { "Sednium AI" },
                    style = MaterialTheme.typography.titleMedium,
                    color = SedniumColors.SedRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SedRedAlpha.a70,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showClear) {
            IconButton(onClick = onClearClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear chat", tint = SedniumColors.SedRed)
            }
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = SedniumColors.SedRed)
        }
    }
}
