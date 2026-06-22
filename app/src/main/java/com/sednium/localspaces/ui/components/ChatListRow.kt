package com.sednium.localspaces.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.model.ChatSession
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.outlined.*

/** Port of one row inside ChatListDrawer.tsx — selectable, pinnable, renamable, deletable. */
@Composable
fun ChatListRow(
    chat: ChatSession,
    isCurrent: Boolean,
    isSelectionMode: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember(chat.updatedAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(chat.updatedAt))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SedniumRadii.md))
            .background(if (isCurrent && !isSelectionMode) OrangeAlpha.a10 else Color.Transparent)
            .border(
                1.dp,
                if (isCurrent && !isSelectionMode) OrangeAlpha.a20 else Color.Transparent,
                RoundedCornerShape(SedniumRadii.md)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        AnimatedVisibility(visible = isSelectionMode, enter = fadeIn()) {
            Icon(
                if (isChecked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (isChecked) SedniumColors.Orange else OrangeAlpha.a50
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (chat.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = OrangeAlpha.a60, modifier = Modifier.padding(end = 2.dp))
                }
                Text(
                    chat.title.ifBlank { "New Chat" },
                    fontWeight = FontWeight.Bold,
                    color = SedniumColors.Orange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(dateFmt, style = MaterialTheme.typography.labelSmall, color = OrangeAlpha.a50)
        }

        if (!isSelectionMode) {
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (chat.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, 
                    contentDescription = if (chat.isPinned) "Unpin" else "Pin", 
                    tint = OrangeAlpha.a60
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = OrangeAlpha.a60)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = OrangeAlpha.a60)
            }
        }
    }
}
