package com.sednium.localspaces.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.PROVIDER_CONFIG
import com.sednium.localspaces.model.SavedModelPreset
import com.sednium.localspaces.ui.components.ChatBubble
import com.sednium.localspaces.ui.components.MessageComposer
import com.sednium.localspaces.ui.components.SedniumTopBar
import com.sednium.localspaces.ui.theme.SedRedAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding

/**
 * PAGE 1 / 4 — Chat Screen.
 * Direct port of App.tsx's top-level layout: header, scrollable message
 * list (or empty state), composer, and footer disclaimer caption.
 */
@Composable
fun ChatScreen(
    chatTitle: String,
    settings: AppSettings,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isConfigValid: Boolean,
    input: String,
    attachments: List<Attachment>,
    isPresetMenuOpen: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onTogglePresetMenu: () -> Unit,
    onSelectPreset: (SavedModelPreset) -> Unit,
    onMenuClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val providerName = PROVIDER_CONFIG[settings.provider]?.displayName ?: "Unknown"

    Scaffold(
        containerColor = SedniumColors.SedYellow,
        topBar = {
            SedniumTopBar(
                title = chatTitle,
                subtitle = "$providerName · ${settings.chatMode.name}",
                showClear = messages.isNotEmpty(),
                onMenuClick = onMenuClick,
                onClearClick = onClearClick,
                onSettingsClick = onSettingsClick
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SedniumColors.SedYellow.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(12.dp)
            ) {
                MessageComposer(
                    input = input,
                    onInputChange = onInputChange,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    isLoading = isLoading,
                    isPresetMenuOpen = isPresetMenuOpen,
                    onTogglePresetMenu = onTogglePresetMenu,
                    presets = settings.savedPresets,
                    activePresetId = settings.activePresetId,
                    onSelectPreset = onSelectPreset,
                    onAttachClick = onAttachClick,
                    onSend = onSend
                )
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isLoading) "Generating response…"
                        else "Sednium AI may occasionally produce inaccurate, misleading, or beautifully imaginative outputs. Please cross-reference critical data independently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SedniumColors.SedRed.copy(alpha = 0.6f)
                    )
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyState(
                isConfigValid = isConfigValid,
                providerName = providerName,
                modelLabel = settings.model,
                onTapConfigure = onSettingsClick,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Top
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        msg = msg,
                        providerName = providerName,
                        isDark = false,
                        isGenerating = isLoading && msg.id == messages.last().id,
                        onImageClick = onImageClick
                    )
                }
            }
        }
    }
}

/** "Ready to Chat" placeholder shown when the active chat session has no messages yet. */
@Composable
private fun EmptyState(
    isConfigValid: Boolean,
    providerName: String,
    modelLabel: String,
    onTapConfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(SedniumRadii.lg))
                .background(SedniumColors.SedRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SedniumColors.SedYellow, modifier = Modifier.size(32.dp))
        }
        Text(
            "Ready to Chat",
            style = MaterialTheme.typography.titleLarge,
            color = SedniumColors.SedRed,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )

        if (!isConfigValid) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(SedniumRadii.md))
                    .background(SedRedAlpha.a10)
                    .border(1.dp, SedRedAlpha.a20, RoundedCornerShape(SedniumRadii.md))
                    .clickable(onClick = onTapConfigure)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = SedniumColors.SedRed)
                Text("Configuration Needed", color = SedniumColors.SedRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Tap to setup $providerName", color = SedRedAlpha.a70, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text(
                "Using ${modelLabel.ifBlank { "Unknown Model" }}.\nStart typing to generate a response.",
                style = MaterialTheme.typography.bodyMedium,
                color = SedniumColors.SedRed.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
