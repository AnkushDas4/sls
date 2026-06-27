package com.sednium.localspaces.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.sednium.localspaces.ui.components.BufferedFadingMarkdown
import com.sednium.localspaces.ui.components.SettingsTextField
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/** The three single-turn tools shipped in v1 — same set as Gallery's Prompt Lab. */
enum class PromptLabTool(val label: String, val placeholder: String, val systemPrompt: String) {
    SUMMARIZE(
        label = "Summarize",
        placeholder = "Paste the text you want summarized…",
        systemPrompt = "You are a precise summarization assistant. Read the user's text and " +
            "produce a clear, concise summary that captures the key points. Use short bullet " +
            "points where that helps scanability. Output only the summary — no preamble, no " +
            "commentary about the task itself."
    ),
    REWRITE(
        label = "Rewrite",
        placeholder = "Paste the text you want rewritten…",
        systemPrompt = "You are an expert editor. Rewrite the user's text to improve clarity, " +
            "grammar, and flow while preserving its original meaning, tone, and intent. Output " +
            "only the rewritten text — no preamble, no explanation of what changed."
    ),
    CODE_GEN(
        label = "Code Gen",
        placeholder = "Describe the function, snippet, or component you need…",
        systemPrompt = "You are an expert software engineer. Based on the user's request, " +
            "generate clean, correct, well-commented code in a single markdown code block. " +
            "If something is genuinely ambiguous, make the most reasonable assumption and note " +
            "it in one short line after the code block — otherwise output only the code."
    )
}

private fun iconFor(tool: PromptLabTool) = when (tool) {
    PromptLabTool.SUMMARIZE -> Icons.Filled.Summarize
    PromptLabTool.REWRITE -> Icons.Filled.AutoFixHigh
    PromptLabTool.CODE_GEN -> Icons.Filled.Code
}

/** Optional tone/style modifier, only shown when Rewrite is the active tool. */
enum class RewriteTone(val label: String, val instruction: String?) {
    DEFAULT("Default", null),
    PROFESSIONAL("Professional", "Use a polished, professional tone suitable for business communication."),
    CASUAL("Casual", "Use a relaxed, friendly, conversational tone."),
    CONCISE("Concise", "Be as concise as possible — cut every unnecessary word while preserving the full meaning."),
    PERSUASIVE("Persuasive", "Use a confident, persuasive tone that makes the strongest possible case.")
}

/**
 * Prompt Lab — a separate, single-turn workspace distinct from the chat
 * thread. Pick a tool, paste/describe your input, run it once, get a
 * result. Nothing here is saved as a ChatSession unless the user explicitly
 * taps "Continue in Chat", which hands the exchange off to the normal chat
 * flow via onSendToChat.
 */
@Composable
fun PromptLabScreen(
    isRunning: Boolean,
    output: String,
    isDark: Boolean,
    onBack: () -> Unit,
    onRun: (tool: PromptLabTool, input: String, toneInstruction: String?) -> Unit,
    onSendToChat: (tool: PromptLabTool, input: String, output: String) -> Unit
) {
    var selectedTool by remember { mutableStateOf(PromptLabTool.SUMMARIZE) }
    var selectedTone by remember { mutableStateOf(RewriteTone.DEFAULT) }
    var input by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Scaffold(
        containerColor = SedniumColors.Milk,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SedniumColors.Milk.copy(alpha = 0.92f))
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to chat", tint = SedniumColors.Orange)
                }
                Text(
                    "Prompt Lab",
                    style = MaterialTheme.typography.titleMedium,
                    color = SedniumColors.Orange,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            // --- Tool picker ---
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PromptLabTool.values().toList()) { tool ->
                    val selected = tool == selectedTool
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) SedniumColors.Orange else OrangeAlpha.a05)
                            .clickable { selectedTool = tool }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            iconFor(tool),
                            contentDescription = null,
                            tint = if (selected) SedniumColors.Milk else SedniumColors.Orange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            tool.label,
                            color = if (selected) SedniumColors.Milk else SedniumColors.Orange,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (selectedTool == PromptLabTool.REWRITE) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(RewriteTone.values().toList()) { tone ->
                        val selected = tone == selectedTone
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) SedniumColors.Orange.copy(alpha = 0.85f) else OrangeAlpha.a05)
                                .clickable { selectedTone = tone }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                tone.label,
                                color = if (selected) SedniumColors.Milk else SedniumColors.Orange.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SettingsTextField(
                    label = "",
                    value = input,
                    onValueChange = { input = it },
                    placeholder = selectedTool.placeholder,
                    singleLine = false,
                    modifier = Modifier.heightIn(min = 120.dp)
                )

                Button(
                    onClick = {
                        if (input.isNotBlank() && !isRunning) {
                            val tone = if (selectedTool == PromptLabTool.REWRITE) selectedTone.instruction else null
                            onRun(selectedTool, input, tone)
                        }
                    },
                    enabled = input.isNotBlank() && !isRunning,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SedniumColors.Orange,
                        contentColor = SedniumColors.Milk,
                        disabledContainerColor = OrangeAlpha.a50,
                        disabledContentColor = SedniumColors.Milk.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SedniumColors.Milk)
                        Text("  Running…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("Run ${selectedTool.label}", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (output.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(OrangeAlpha.a05)
                            .padding(14.dp)
                    ) {
                        SelectionContainer {
                            BufferedFadingMarkdown(
                                content = output,
                                isDark = isDark,
                                isStreaming = isRunning
                            )
                        }
                    }

                    if (!isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(output)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = SedniumColors.Orange, modifier = Modifier.size(16.dp))
                                Text("  Copy", color = SedniumColors.Orange)
                            }
                            OutlinedButton(
                                onClick = { onSendToChat(selectedTool, input, output) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Chat, contentDescription = null, tint = SedniumColors.Orange, modifier = Modifier.size(16.dp))
                                Text("  Continue in Chat", color = SedniumColors.Orange)
                            }
                        }
                    }
                } else {
                    Text(
                        "Single-turn — nothing here is saved to your chat history unless you tap \"Continue in Chat.\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangeAlpha.a60,
                        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                    )
                }
            }
        }
    }
}
