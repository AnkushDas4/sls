package com.sednium.localspaces.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.ChatMode
import com.sednium.localspaces.model.ModelProvider
import com.sednium.localspaces.model.PROVIDER_CONFIG
import com.sednium.localspaces.ui.components.ProviderChip
import com.sednium.localspaces.ui.components.SettingsSectionLabel
import com.sednium.localspaces.ui.components.SettingsSliderRow
import com.sednium.localspaces.ui.components.SettingsSwitchRow
import com.sednium.localspaces.ui.components.SettingsTextField
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

/**
 * PAGE 3 / 4 — Settings Screen.
 * Faithful, scoped port of SettingsDrawer.tsx's 874 lines: provider
 * picker, chat-mode picker, generation sliders, history toggle, and a
 * per-provider API key field. Host inside a Material3 ModalBottomSheet
 * to match the original's "slide-up from the bottom" presentation
 * (`animate-slide-up`, see SedniumMotion.slideUpSpec()).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SedniumColors.SedYellow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = SedniumColors.SedRed)
                Text("Settings", style = MaterialTheme.typography.titleLarge, color = SedniumColors.SedRed)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = SedniumColors.SedRed)
            }
        }

        // --- Provider & Model ---
        SettingsSectionLabel("Provider & Model")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelProvider.entries.forEach { provider ->
                ProviderChip(
                    label = PROVIDER_CONFIG[provider]?.displayName ?: provider.name,
                    selected = settings.provider == provider,
                    onClick = { onUpdateSettings(settings.copy(provider = provider)) }
                )
            }
        }
        SettingsTextField(
            label = "Model",
            value = settings.model,
            onValueChange = { onUpdateSettings(settings.copy(model = it)) },
            placeholder = "e.g. gemini-1.5-pro"
        )

        HorizontalDivider(color = SedRedAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

        // --- Chat Mode ---
        SettingsSectionLabel("Chat Mode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatMode.entries.forEach { mode ->
                ProviderChip(
                    label = mode.name,
                    selected = settings.chatMode == mode,
                    onClick = { onUpdateSettings(settings.copy(chatMode = mode)) }
                )
            }
        }

        HorizontalDivider(color = SedRedAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

        // --- Behavior ---
        SettingsSectionLabel("Behavior")
        SettingsSwitchRow(
            label = "Enable Tools",
            description = "Allow function/tool calling when supported",
            checked = settings.enableTools,
            onCheckedChange = { onUpdateSettings(settings.copy(enableTools = it)) }
        )
        SettingsTextField(
            label = "System Instruction",
            value = settings.systemInstruction,
            onValueChange = { onUpdateSettings(settings.copy(systemInstruction = it)) },
            placeholder = "Optional system prompt override",
            singleLine = false
        )

        HorizontalDivider(color = SedRedAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

        // --- Generation parameters ---
        SettingsSectionLabel("Generation")
        SettingsSliderRow(
            label = "Temperature",
            value = settings.temperature,
            onValueChange = { onUpdateSettings(settings.copy(temperature = it)) },
            valueRange = 0f..2f
        )
        SettingsSliderRow(
            label = "Top P",
            value = settings.topP,
            onValueChange = { onUpdateSettings(settings.copy(topP = it)) },
            valueRange = 0f..1f
        )
        SettingsSliderRow(
            label = "Top K",
            value = settings.topK.toFloat(),
            onValueChange = { onUpdateSettings(settings.copy(topK = it.toInt())) },
            valueRange = 1f..100f,
            displayFormat = { it.toInt().toString() }
        )
        SettingsSliderRow(
            label = "Max Tokens",
            value = settings.maxTokens.toFloat(),
            onValueChange = { onUpdateSettings(settings.copy(maxTokens = it.toInt())) },
            valueRange = 256f..32000f,
            displayFormat = { it.toInt().toString() }
        )

        HorizontalDivider(color = SedRedAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

        // --- History ---
        SettingsSectionLabel("History")
        SettingsSwitchRow(
            label = "Save Chat History",
            description = "Persist chats to local storage",
            checked = settings.enableHistory,
            onCheckedChange = { onUpdateSettings(settings.copy(enableHistory = it)) }
        )

        HorizontalDivider(color = SedRedAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

        // --- API Key (only the active provider's key is shown, matching the TSX's conditional render) ---
        SettingsSectionLabel("${PROVIDER_CONFIG[settings.provider]?.displayName ?: ""} API Key")
        SettingsTextField(
            label = "API Key",
            value = apiKeyFor(settings),
            onValueChange = { onUpdateSettings(updateApiKeyFor(settings, it)) },
            placeholder = "sk-…",
            isSecret = true
        )
        if (settings.provider == ModelProvider.LOCAL || settings.provider == ModelProvider.CUSTOM) {
            SettingsTextField(
                label = "Base URL",
                value = settings.localBaseUrl,
                onValueChange = { onUpdateSettings(settings.copy(localBaseUrl = it)) },
                placeholder = "http://localhost:11434/v1"
            )
        }
    }
}

private fun apiKeyFor(s: AppSettings): String = when (s.provider) {
    ModelProvider.GOOGLE -> s.googleApiKey
    ModelProvider.OPENAI -> s.openaiApiKey
    ModelProvider.ANTHROPIC -> s.anthropicApiKey
    ModelProvider.XAI -> s.xaiApiKey
    ModelProvider.GROQ -> s.groqApiKey
    ModelProvider.OPENROUTER -> s.openRouterApiKey
    ModelProvider.NVIDIA -> s.nvidiaApiKey
    ModelProvider.CUSTOM -> s.customApiKey
    else -> ""
}

private fun updateApiKeyFor(s: AppSettings, value: String): AppSettings = when (s.provider) {
    ModelProvider.GOOGLE -> s.copy(googleApiKey = value)
    ModelProvider.OPENAI -> s.copy(openaiApiKey = value)
    ModelProvider.ANTHROPIC -> s.copy(anthropicApiKey = value)
    ModelProvider.XAI -> s.copy(xaiApiKey = value)
    ModelProvider.GROQ -> s.copy(groqApiKey = value)
    ModelProvider.OPENROUTER -> s.copy(openRouterApiKey = value)
    ModelProvider.NVIDIA -> s.copy(nvidiaApiKey = value)
    ModelProvider.CUSTOM -> s.copy(customApiKey = value)
    else -> s
}
