package com.sednium.localspaces.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.AttachmentType
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.Role
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii
import com.sednium.localspaces.ui.theme.ThinkingDots

/**
 * Port of components/MessageItem.tsx — the per-message row. Model
 * messages: left-aligned with a sedRed/sedYellow bot avatar.
 * User messages: right-aligned pill, sedRed/5 fill + sedRed/30 border.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatBubble(
    msg: ChatMessage,
    providerName: String,
    isDark: Boolean,
    isGenerating: Boolean,
    onImageClick: (String) -> Unit
) {
    val isModel = msg.role == Role.MODEL
    var thoughtExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isModel) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(SedniumRadii.lg))
                        .background(SedniumColors.SedRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SedniumColors.SedYellow, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = if (!isModel) {
                    Modifier
                        .clip(RoundedCornerShape(SedniumRadii.pill))
                        .background(SedRedAlpha.a05)
                        .border(1.dp, SedRedAlpha.a30, RoundedCornerShape(SedniumRadii.pill))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                }
            ) {
                if (isModel) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            providerName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = SedniumColors.Gray500
                        )
                        if (msg.isError) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SedniumColors.Red100)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = SedniumColors.Red500, modifier = Modifier.size(10.dp))
                                Text("ERROR", color = SedniumColors.Red500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // --- Thought / reasoning trace (collapsible <details>) ---
                if (msg.thought != null) {
                    val showThinking = isGenerating && msg.content.isBlank()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(SedniumRadii.sm))
                            .padding(4.dp)
                    ) {
                        if (showThinking) {
                            ThinkingDots(dotColor = SedniumColors.Gray400)
                        }
                        Text(
                            if (showThinking) "Thinking…" else "Internal Thought Process",
                            style = MaterialTheme.typography.labelSmall,
                            color = SedniumColors.Gray400,
                            modifier = Modifier.padding(start = if (showThinking) 0.dp else 0.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = thoughtExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp, top = 4.dp)
                                .border(0.dp, SedniumColors.Gray200) // left rule emulated via padding card
                                .padding(start = 8.dp)
                        ) {
                            MarkdownText(content = msg.thought, isDark = isDark)
                        }
                    }
                }

                // --- Generating placeholder when there's no content/thought yet ---
                if (msg.content.isBlank() && msg.thought == null && isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThinkingDots(dotColor = SedniumColors.SedRed.copy(alpha = 0.5f))
                        Text("Generating…", style = MaterialTheme.typography.labelSmall, color = SedniumColors.SedRed.copy(alpha = 0.5f))
                    }
                }

                // --- Main content ---
                if (msg.content.isNotBlank()) {
                    MarkdownText(
                        content = msg.content,
                        isDark = isDark,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }

                // --- Attachments row ---
                if (msg.attachments.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        msg.attachments.forEach { att -> AttachmentPreview(att, isDark, onImageClick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(att: Attachment, isDark: Boolean, onImageClick: (String) -> Unit) {
    if (att.type == AttachmentType.IMAGE) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(SedniumRadii.sm))
                .border(1.dp, if (isDark) SedniumColors.Gray800 else SedniumColors.Gray200, RoundedCornerShape(SedniumRadii.sm))
        ) {
            // AsyncImage / Coil painter goes here, decoding att.data (base64) + att.mimeType.
            // Tapping calls onImageClick(dataUri) to open the full-screen ImageViewerOverlay.
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(SedniumRadii.sm))
                .background(if (isDark) SedniumColors.DarkSurfaceAlt else SedniumColors.White)
                .border(1.dp, if (isDark) SedniumColors.Gray700 else SedniumColors.Gray200, RoundedCornerShape(SedniumRadii.sm))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                att.name.substringAfterLast('.', "FILE").uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SedniumColors.Blue600
            )
            Text(att.name, style = MaterialTheme.typography.bodySmall, color = if (isDark) SedniumColors.Gray300 else SedniumColors.Gray700)
        }
    }
}
