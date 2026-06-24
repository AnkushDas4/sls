package com.sednium.localspaces.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.Attachment
import com.sednium.localspaces.model.ChatMessage
import com.sednium.localspaces.model.ChatMode
import com.sednium.localspaces.model.PROVIDER_CONFIG
import com.sednium.localspaces.model.Role
import com.sednium.localspaces.model.SavedModelPreset
import com.sednium.localspaces.model.ToolCallState
import com.sednium.localspaces.ui.components.ChatBubble
import com.sednium.localspaces.ui.components.MessageComposer
import com.sednium.localspaces.ui.components.SedniumTopBar
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors
import com.sednium.localspaces.ui.theme.SedniumRadii

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.launch

import com.sednium.localspaces.navigation.LocalServerStatus

@Composable
fun ToolActivityView(toolCalls: List<ToolCallState>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = SedniumRadii.md, bottomEnd = SedniumRadii.md))
            .background(SedniumColors.Gray900) // very dark header
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SedniumColors.Orange, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("AGENT ACTIVITY", style = MaterialTheme.typography.labelSmall, color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
        }
        toolCalls.forEach { tool ->
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

/**
 * PAGE 1 / 4 — Chat Screen.
 * Direct port of App.tsx's top-level layout: header, scrollable message
 * list (or empty state), composer, and footer disclaimer caption.
 */
@Composable
fun ChatScreen(
    chatTitle: String,
    settings: AppSettings,
    localServerStatus: LocalServerStatus = LocalServerStatus.UNKNOWN,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isConfigValid: Boolean,
    input: String,
    attachments: List<Attachment>,
    isPresetMenuOpen: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onAttachClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onTogglePresetMenu: () -> Unit,
    onSelectPreset: (SavedModelPreset) -> Unit,
    onMenuClick: () -> Unit,
    onExportClick: () -> Unit,
    onClearClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val providerName = PROVIDER_CONFIG[settings.provider]?.displayName ?: "Unknown"
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var wasLoading by remember { mutableStateOf(isLoading) }
    
    // Haptic feedback when generation completes
    LaunchedEffect(isLoading) {
        if (wasLoading && !isLoading && messages.isNotEmpty() && messages.last().role == Role.MODEL) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        wasLoading = isLoading
    }
    
    // Auto-scroll logic
    val lastMessage = messages.lastOrNull()
    var isUserScrolling by remember { mutableStateOf(false) }
    
    // Detect manual scrolling to stop auto-scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling = true
        }
    }
    
    // Reset user scrolling state when we are at the bottom
    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward) {
            isUserScrolling = false
        }
    }
    
    LaunchedEffect(messages.size) {
        // Auto scroll for new messages
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(
        lastMessage?.content?.length, 
        lastMessage?.thought?.length, 
        lastMessage?.toolCalls?.size
    ) {
        if (messages.isNotEmpty() && !isUserScrolling) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            
            // Only auto-scroll if we are already near the bottom
            if (totalItems - lastVisibleIndex <= 2) {
                // Use scrollToItem instead of animateScrollToItem during generation to prevent animation interruption jitters
                listState.scrollToItem(messages.size - 1)
            }
        }
    }
    
    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            listState.canScrollForward && (totalItems - lastVisibleIndex > 2)
        }
    }

    val activeToolCalls = remember(messages, isLoading) {
        val lastMsg = messages.lastOrNull()
        if (isLoading && lastMsg != null && lastMsg.role == Role.MODEL && lastMsg.toolCalls.isNotEmpty()) {
            lastMsg.toolCalls
        } else {
            emptyList()
        }
    }

    var isFocusMode by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SedniumColors.Milk,
        topBar = {
            SedniumTopBar(
                title = chatTitle,
                subtitle = "$providerName · ${settings.chatMode.name}",
                localServerStatus = if (settings.provider == com.sednium.localspaces.model.ModelProvider.LOCAL) localServerStatus else null,
                showClear = messages.isNotEmpty(),
                showExport = messages.isNotEmpty(),
                isFocusMode = isFocusMode,
                onMenuClick = onMenuClick,
                onExportClick = onExportClick,
                onClearClick = onClearClick,
                onSettingsClick = onSettingsClick,
                onFocusModeToggle = { isFocusMode = !isFocusMode }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SedniumColors.Milk.copy(alpha = 0.92f))
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
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isLoading) "Generating response…"
                        else "Sednium AI may occasionally produce inaccurate, misleading, or\nbeautifully imaginative outputs. Please cross-reference critical data\nindependently.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangeAlpha.a40,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 12.sp
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
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isLast = msg.id == messages.last().id
                        ChatBubble(
                            msg = msg,
                            providerName = providerName,
                            isDark = false,
                            isGenerating = isLoading && isLast,
                            onImageClick = onImageClick,
                            onRetry = if (isLast && msg.role == Role.MODEL && !isLoading) onRetry else null
                        )
                    }
                }

                AnimatedVisibility(
                    visible = activeToolCalls.isNotEmpty() && !isFocusMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ToolActivityView(toolCalls = activeToolCalls)
                }
                
                AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        },
                        containerColor = SedniumColors.Orange,
                        contentColor = SedniumColors.Milk,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                    }
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
                .background(SedniumColors.Orange),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SedniumColors.Milk, modifier = Modifier.size(32.dp))
        }
        Text(
            "Ready to Chat",
            style = MaterialTheme.typography.titleLarge,
            color = SedniumColors.Orange,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )

        if (!isConfigValid) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(SedniumRadii.md))
                    .background(OrangeAlpha.a10)
                    .border(1.dp, OrangeAlpha.a20, RoundedCornerShape(SedniumRadii.md))
                    .clickable(onClick = onTapConfigure)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = SedniumColors.Orange)
                Text("Configuration Needed", color = SedniumColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Tap to setup $providerName", color = OrangeAlpha.a70, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text(
                "Using ${modelLabel.ifBlank { "Unknown Model" }}.\nStart typing to generate a response.",
                style = MaterialTheme.typography.bodyMedium,
                color = SedniumColors.Orange.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
