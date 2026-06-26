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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.AttachmentType
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.Role
import com.sednium.localspaces.ui.theme.OrangeAlpha
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
    onImageClick: (String) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val isModel = msg.role == Role.MODEL
    var userThoughtExpanded by remember { mutableStateOf(false) }
    val thoughtExpanded = userThoughtExpanded

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (isModel) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(SedniumRadii.lg))
                        .background(SedniumColors.Orange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SedniumColors.Milk, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = if (!isModel) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(OrangeAlpha.a05)
                        .border(1.dp, OrangeAlpha.a30, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                }
            ) {
                if (isModel) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            msg.modelName?.uppercase() ?: providerName.uppercase(),
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

                    // --- Citations Carousel (Research) ---
                    if (msg.citations.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            msg.citations.forEach { cit ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isDark) SedniumColors.DarkSurfaceAlt else SedniumColors.Gray100)
                                        .border(1.dp, if (isDark) SedniumColors.Gray700 else SedniumColors.Gray200, RoundedCornerShape(16.dp))
                                        .clickable { /* open cit.url */ }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("[${cit.id}]", style = MaterialTheme.typography.labelSmall, color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(cit.domain, style = MaterialTheme.typography.labelSmall, color = if (isDark) SedniumColors.Gray300 else SedniumColors.Gray700)
                                }
                            }
                        }
                    }
                }

                // --- Thought / reasoning trace (collapsible <details>) ---
                if (msg.thought != null) {
                    val showThinking = msg.isThinking
                    val thinkingStateText = when {
                        msg.isThinking -> "Thinking…"
                        isGenerating -> "Generating final output…"
                        else -> "Thought Process"
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(SedniumRadii.sm))
                            .clickable { userThoughtExpanded = !thoughtExpanded }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (thoughtExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = SedniumColors.Orange.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = thinkingStateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SedniumColors.Orange.copy(alpha = 0.5f)
                        )
                        if (msg.isThinking || isGenerating) {
                            ThinkingDots(dotColor = SedniumColors.Orange.copy(alpha = 0.5f))
                        }
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
                            com.sednium.localspaces.markdown.MarkdownView(content = msg.thought, isDark = isDark)
                        }
                    }
                }

                // --- Tool Execution / Terminal Bash ---
                if (msg.toolCalls.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(SedniumRadii.sm))
                            .background(SedniumColors.Gray800) // dark themed mono
                            .padding(12.dp)
                    ) {
                        msg.toolCalls.forEach { tool ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$ ", color = SedniumColors.Green500, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                Text(tool.command, color = SedniumColors.Gray300, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                if (tool.isExecuting) {
                                    Text("[\\]", color = SedniumColors.Milk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                } else if (tool.success) {
                                    Box(modifier = Modifier.background(SedniumColors.Green500, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text("SUCCESS", color = SedniumColors.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(modifier = Modifier.background(SedniumColors.Red600, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text("FAILED", color = SedniumColors.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Generating placeholder when there's no content/thought yet ---
                if (msg.content.isBlank() && msg.thought == null && isGenerating && msg.toolCalls.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        ThinkingDots(dotColor = SedniumColors.Orange.copy(alpha = 0.5f))
                        Text(if (msg.isThinking) "Thinking…" else "Generating…", style = MaterialTheme.typography.labelSmall, color = SedniumColors.Orange.copy(alpha = 0.5f))
                    }
                }

                // --- Main content ---
                if (msg.content.isNotBlank()) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        BufferedFadingMarkdown(
                            content = msg.content,
                            isDark = isDark,
                            isStreaming = isModel && isGenerating,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                }

                // --- Attachments row ---
                if (msg.attachments.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        msg.attachments.forEach { att -> AttachmentPreview(att, isDark, onImageClick) }
                    }
                }

                // --- Action Buttons ---
                if (isModel && !isGenerating) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (msg.content.isNotBlank()) {
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.content))
                                    android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = SedniumColors.Gray500,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (onRetry != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Retry",
                                    tint = SedniumColors.Gray500,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(att: Attachment, isDark: Boolean, onImageClick: (String) -> Unit) {
    if (att.type == AttachmentType.IMAGE) {
        val uri = if (att.data.startsWith("content://") || att.data.startsWith("http")) att.data else "data:${att.mimeType};base64,${att.data}"
        coil.compose.AsyncImage(
            model = uri,
            contentDescription = att.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(SedniumRadii.sm))
                .border(1.dp, if (isDark) SedniumColors.Gray800 else SedniumColors.Gray200, RoundedCornerShape(SedniumRadii.sm))
                .clickable { onImageClick(uri) }
        )
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
