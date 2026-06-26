package com.sednium.localspaces.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.ChatSession
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * Per-session generation config. Unlike the global Settings screen, every
 * value here is nullable on the underlying ChatSession — null means "follow
 * whatever the global AppSettings default currently is." Hitting the reset
 * (↺) icon on a row clears that one override back to null/inherited; nothing
 * here ever touches AppSettings itself.
 */
@Composable
fun SessionConfigDialog(
    session: ChatSession,
    globalSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (ChatSession) -> Unit
) {
    var temperature by remember { mutableStateOf(session.temperatureOverride ?: globalSettings.temperature) }
    var topP by remember { mutableStateOf(session.topPOverride ?: globalSettings.topP) }
    var topK by remember { mutableStateOf((session.topKOverride ?: globalSettings.topK).toFloat()) }
    var maxTokens by remember { mutableStateOf((session.maxTokensOverride ?: globalSettings.maxTokens).toFloat()) }
    var systemInstruction by remember { mutableStateOf(session.systemInstructionOverride ?: "") }
    var useCustomSystemInstruction by remember { mutableStateOf(session.systemInstructionOverride != null) }

    // Which fields are currently overridden vs. just previewing the global
    // default — only overridden ones get written back as non-null.
    var temperatureOverridden by remember { mutableStateOf(session.temperatureOverride != null) }
    var topPOverridden by remember { mutableStateOf(session.topPOverride != null) }
    var topKOverridden by remember { mutableStateOf(session.topKOverride != null) }
    var maxTokensOverridden by remember { mutableStateOf(session.maxTokensOverride != null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp),
            color = SedniumColors.Milk
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Session settings",
                    color = SedniumColors.Orange,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "These only apply to \"${session.title}\" — your global defaults in Settings are untouched.",
                    color = OrangeAlpha.a60,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    SettingsSliderRow(
                        label = "Temperature" + if (!temperatureOverridden) " (global)" else "",
                        value = temperature,
                        onValueChange = { temperature = it; temperatureOverridden = true },
                        valueRange = 0f..2f,
                        steps = 19,
                        onReset = if (temperatureOverridden) {
                            { temperature = globalSettings.temperature; temperatureOverridden = false }
                        } else null
                    )
                    SettingsSliderRow(
                        label = "Top P" + if (!topPOverridden) " (global)" else "",
                        value = topP,
                        onValueChange = { topP = it; topPOverridden = true },
                        valueRange = 0f..1f,
                        steps = 19,
                        onReset = if (topPOverridden) {
                            { topP = globalSettings.topP; topPOverridden = false }
                        } else null
                    )
                    SettingsSliderRow(
                        label = "Top K" + if (!topKOverridden) " (global)" else "",
                        value = topK,
                        onValueChange = { topK = it; topKOverridden = true },
                        valueRange = 1f..100f,
                        steps = 98,
                        displayFormat = { it.toInt().toString() },
                        onReset = if (topKOverridden) {
                            { topK = globalSettings.topK.toFloat(); topKOverridden = false }
                        } else null
                    )
                    SettingsSliderRow(
                        label = "Max Output Tokens" + if (!maxTokensOverridden) " (global)" else "",
                        value = maxTokens,
                        onValueChange = { maxTokens = it; maxTokensOverridden = true },
                        valueRange = 256f..8192f,
                        steps = 30,
                        displayFormat = { it.toInt().toString() },
                        onReset = if (maxTokensOverridden) {
                            { maxTokens = globalSettings.maxTokens.toFloat(); maxTokensOverridden = false }
                        } else null
                    )

                    SettingsSwitchRow(
                        label = "Custom system prompt",
                        description = "Override the global system instruction for this chat only",
                        checked = useCustomSystemInstruction,
                        onCheckedChange = { useCustomSystemInstruction = it }
                    )
                    if (useCustomSystemInstruction) {
                        SettingsTextField(
                            label = "System instruction",
                            value = systemInstruction,
                            onValueChange = { systemInstruction = it },
                            placeholder = "Leave blank to use the global default text",
                            singleLine = false
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = OrangeAlpha.a70)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        onSave(
                            session.copy(
                                temperatureOverride = if (temperatureOverridden) temperature else null,
                                topPOverride = if (topPOverridden) topP else null,
                                topKOverride = if (topKOverridden) topK.toInt() else null,
                                maxTokensOverride = if (maxTokensOverridden) maxTokens.toInt() else null,
                                systemInstructionOverride = if (useCustomSystemInstruction) systemInstruction else null
                            )
                        )
                    }) {
                        Text("Save", color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
