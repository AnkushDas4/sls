package com.sednium.localspaces.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.AttachmentType
import com.sednium.localspaces.model.SavedModelPreset
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii
import com.sednium.localspaces.ui.theme.SpinningIcon
import com.sednium.localspaces.ui.theme.popUpSpec

/**
 * Port of the pill-shaped composer at the bottom of App.tsx: attachment
 * chips row, attach + preset-bookmark buttons, auto-growing text field,
 * and a send button that morphs color depending on whether it's armed.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MessageComposer(
    input: String,
    onInputChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Int) -> Unit,
    isLoading: Boolean,
    isPresetMenuOpen: Boolean,
    onTogglePresetMenu: () -> Unit,
    presets: List<SavedModelPreset>,
    activePresetId: String?,
    onSelectPreset: (SavedModelPreset) -> Unit,
    onAttachClick: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = (input.isNotBlank() || attachments.isNotEmpty()) && !isLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SedniumRadii.pill))
            .background(SedniumColors.SedYellow)
            .border(1.dp, SedRedAlpha.a30, RoundedCornerShape(SedniumRadii.pill))
            .padding(6.dp)
    ) {
        // --- Attachment chips ---
        AnimatedVisibility(visible = attachments.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                attachments.forEachIndexed { idx, att ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(SedniumRadii.sm))
                            .background(SedRedAlpha.a10)
                            .border(1.dp, SedRedAlpha.a20, RoundedCornerShape(SedniumRadii.sm))
                            .padding(start = 8.dp, end = 28.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(
                            if (att.type == AttachmentType.IMAGE) "IMG" else att.name.substringAfterLast('.', "TXT").uppercase().take(4),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SedniumColors.SedRed
                        )
                        Text(att.name, style = MaterialTheme.typography.bodySmall, color = SedniumColors.SedRed, maxLines = 1)
                        IconButton(onClick = { onRemoveAttachment(idx) }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = SedRedAlpha.a60, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onAttachClick, enabled = !isLoading) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach", tint = SedniumColors.SedRed.copy(alpha = 0.7f))
            }

            Box {
                IconButton(onClick = onTogglePresetMenu, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Presets",
                        tint = SedniumColors.SedRed.copy(alpha = if (isPresetMenuOpen) 1f else 0.7f)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPresetMenuOpen,
                    enter = scaleIn(animationSpec = popUpSpec(), transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    PresetMenu(presets = presets, activePresetId = activePresetId, onSelect = onSelectPreset)
                }
            }

            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 150.dp),
                placeholder = { Text("Message…", color = SedniumColors.SedRed.copy(alpha = 0.5f)) },
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = SedniumColors.SedRed,
                    unfocusedTextColor = SedniumColors.SedRed,
                    cursorColor = SedniumColors.SedRed
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (canSend) SedniumColors.SedRed else SedRedAlpha.a10)
                    .let { if (canSend) it else it } // shadow omitted for native simplicity
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSend, enabled = canSend) {
                    if (isLoading) {
                        SpinningIcon(icon = Icons.Filled.Refresh, tint = SedniumColors.SedRed)
                    } else {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) SedniumColors.SedYellow else SedRedAlpha.a40
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetMenu(
    presets: List<SavedModelPreset>,
    activePresetId: String?,
    onSelect: (SavedModelPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(SedniumRadii.lg))
            .background(SedniumColors.SedYellow)
            .border(1.dp, SedRedAlpha.a20, RoundedCornerShape(SedniumRadii.lg))
            .heightIn(max = 240.dp)
            .padding(8.dp)
    ) {
        if (presets.isEmpty()) {
            Text(
                "No saved configurations. Save presets from Settings > Behavior.",
                style = MaterialTheme.typography.labelSmall,
                color = SedRedAlpha.a70
            )
        } else {
            Text(
                "SAVED CONFIGURATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = SedRedAlpha.a70,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            presets.forEach { preset ->
                val isActive = preset.id == activePresetId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SedniumRadii.sm))
                        .background(if (isActive) SedRedAlpha.a10 else Color.Transparent)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(preset.name, color = SedniumColors.SedRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(preset.model, color = SedRedAlpha.a60, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        preset.chatMode.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = SedniumColors.SedYellow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SedniumColors.SedRed)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
