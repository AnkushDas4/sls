package com.sednium.localspaces.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sednium.localspaces.AppSettings
import com.sednium.localspaces.ChatViewModel
import com.sednium.localspaces.Message
import com.sednium.localspaces.Role
import com.sednium.localspaces.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Haptics Helper Function
fun performHaptic(context: Context, settings: AppSettings, effectType: Int) {
    if (!settings.enableHaptics) return
    val vibrator = context.getSystemService(android.os.Vibrator::class.java) ?: return
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val effect = when (effectType) {
            1 -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK) // LIGHT
            2 -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK) // MEDIUM/HEAVY
            else -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
        }
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        if (effectType == 2) vibrator.vibrate(50) else vibrator.vibrate(20)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val currentStreamingContent by viewModel.currentStreamingContent.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val listState = rememberLazyListState()

    var textInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val isDark = when (settings.theme) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> isSystemInDarkTheme()
    }

    // Auto-scroll to bottom on chat state updates
    LaunchedEffect(chatHistory.size, isGenerating) {
        if (settings.autoScrollToBottom) {
            val totalItems = chatHistory.size + if (isGenerating && currentStreamingContent.isNotBlank()) 1 else 0
            if (totalItems > 0) {
                try {
                    listState.animateScrollToItem(totalItems - 1)
                } catch (e: Exception) {
                    listState.scrollToItem(totalItems - 1)
                }
            }
        }
    }

    // Response complete haptic triggers
    var wasGenerating by remember { mutableStateOf(false) }
    LaunchedEffect(isGenerating) {
        if (wasGenerating && !isGenerating) {
            performHaptic(context, settings, 1) // LIGHT
        }
        wasGenerating = isGenerating
    }

    val mainBg = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mainBg)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // 1. CHAT HEADER
        ChatHeader(
            settings = settings,
            isDark = isDark,
            hasMessages = chatHistory.isNotEmpty(),
            modelState = modelState,
            onNavigateToSettings = onNavigateToSettings
        )

        // 2. MESSAGE LIST
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatHistory.isEmpty() && (currentStreamingContent.isBlank() || !isGenerating)) {
                // Empty state
                EmptyStateView(
                    settings = settings,
                    isDark = isDark,
                    onNavigateToSettings = onNavigateToSettings
                )
            } else {
                // List of Messages with max 720dp centering
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 720.dp)
                            .testTag("message_list"),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    ) {
                        itemsIndexed(chatHistory, key = { _, msg -> msg.id }) { index, msg ->
                            Box(modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = spring(stiffness = 400f, dampingRatio = 1f))) {
                                AnimatedMessageItem {
                                    MessageItemRow(
                                        message = msg,
                                        isLatest = index == chatHistory.lastIndex,
                                        isLoading = isGenerating,
                                        settings = settings,
                                        isDark = isDark,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }

                        if (isGenerating && currentStreamingContent.isNotBlank()) {
                            item(key = "streaming_model") {
                                Box(modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = spring(stiffness = 400f, dampingRatio = 1f))) {
                                    AnimatedMessageItem {
                                        MessageItemRow(
                                            message = Message(Role.MODEL, currentStreamingContent, id = "streaming_model"),
                                            isLatest = true,
                                            isLoading = isGenerating,
                                            settings = settings,
                                            isDark = isDark,
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. CHAT INPUT
        ChatInputBar(
            value = textInput,
            onValueChange = { textInput = it },
            settings = settings,
            onSettingsChange = { viewModel.updateSettings(it) },
            isDark = isDark,
            isLoading = isGenerating,
            onSend = { mode, uris ->
                if (textInput.isNotBlank() && settings.isConfigValid) {
                    performHaptic(context, settings, 2) // MEDIUM
                    viewModel.sendMessage(textInput, mode, uris)
                    textInput = ""
                }
            },
            onStop = {
                performHaptic(context, settings, 1) // LIGHT
                viewModel.stopGeneration()
            }
        )

        // AI Disclaimer
        Text(
            text = "AI-generated information can be misleading or incorrect. Please verify important details.",
            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
fun ChatHeader(
    settings: AppSettings,
    isDark: Boolean,
    hasMessages: Boolean,
    modelState: com.sednium.localspaces.ModelState,
    onNavigateToSettings: () -> Unit
) {
    val barBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val borderColor = if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = barBg
    ) {
        Column(
            modifier = Modifier.statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = if (isDark) Color.White else Color(0xFF18181B)
                        )
                    }

                    Column {
                        Text(
                            text = "Sednium AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = getProviderColor(settings.provider)
                        )
                        Text(
                            text = settings.provider.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f)
                        )
                    }
                }
                
                // Status Indicator
                val statusText: String
                val statusColor: Color
                when (modelState) {
                    com.sednium.localspaces.ModelState.OFFLINE -> {
                        statusText = "Offline"
                        statusColor = Color.Gray
                    }
                    com.sednium.localspaces.ModelState.LOADING -> {
                        statusText = "Loading..."
                        statusColor = Color(0xFFEAB308) // Yellow
                    }
                    com.sednium.localspaces.ModelState.READY -> {
                        statusText = "Ready"
                        statusColor = getProviderColor(settings.provider)
                    }
                    com.sednium.localspaces.ModelState.GENERATING -> {
                        statusText = "Running..."
                        statusColor = Color(0xFF3B82F6) // Blue
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderColor)
            )
        }
    }
}


@Composable
fun EmptyStateView(
    settings: AppSettings,
    isDark: Boolean,
    onNavigateToSettings: () -> Unit
) {
    val animScale = remember { Animatable(0.95f) }
    val animAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { animScale.animateTo(1f, animationSpec = tween(200)) }
        launch { animAlpha.animateTo(1f, animationSpec = tween(200)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .alpha(animAlpha.value)
            .graphicsLayer {
                scaleX = animScale.value
                scaleY = animScale.value
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            // Rounded Box Bot icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (isDark) 0.dp else 1.dp,
                        color = Color(0xFFF4F4F5),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(if (isDark) Color(0xFF27272A) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "Assistant Profile",
                    tint = if (isDark) Color.White else Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Welcome to Sednium LS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF18181B),
                fontSize = 18.sp
            )

            if (!settings.isConfigValid) {
                Surface(
                    color = Color(0x1AEF4444),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSettings() }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Configuration Needed",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Please touch here to configure your model setup and keys.",
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            } else {
                val modelDisplayName = settings.modelName.ifBlank {
                    if (settings.provider == com.sednium.localspaces.ProviderType.NATIVE_ANDROID) "On-Device model" else "Setup model"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Using ",
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = modelDisplayName,
                        color = getProviderColor(settings.provider),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = ". Start typing to chat.",
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Disclaimer: AI-generated content can be inaccurate or misleading. Always verify important information before relying on it.",
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedMessageItem(
    content: @Composable () -> Unit
) {
    val animOffset = remember { Animatable(10f) }
    val animAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            animOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = 400f, dampingRatio = 1f)
            )
        }
        launch {
            animAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(150)
            )
        }
    }
    Box(
        modifier = Modifier
            .alpha(animAlpha.value)
            .graphicsLayer {
                translationY = animOffset.value
            }
    ) {
        content()
    }
}

@Composable
fun MessageItemRow(
    message: Message,
    isLatest: Boolean,
    isLoading: Boolean,
    settings: AppSettings,
    isDark: Boolean,
    viewModel: ChatViewModel
) {
    val isUser = message.role == Role.USER

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val hasOverlay = isHovered || isFocused

    val overlayBg = if (hasOverlay) {
        if (isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(overlayBg)
            .hoverable(interactionSource = interactionSource)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar Box
        val avatarBg = if (isUser) {
            if (isDark) Color(0xFF2A2B32) else Color(0xFFE4E4E7)
        } else {
            if (isDark) Color(0xFF0284C7) else Color(0xFF0284C7)
        }
        val avatarIconTint = if (isUser) {
            if (isDark) Color.White else Color(0xFF52525B)
        } else {
            Color.White
        }

        val isAILoading = !isUser && isLatest && isLoading
        val avatarPulseScale = if (isAILoading) {
            val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse_trans")
            infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "avatar_scale"
            )
        } else {
            remember { mutableStateOf(1.0f) }
        }
        val avatarPulseAlpha = if (isAILoading) {
            val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse_alpha")
            infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "avatar_alpha"
            )
        } else {
            remember { mutableStateOf(0.0f) }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp)
        ) {
            if (isAILoading) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = avatarPulseScale.value
                            scaleY = avatarPulseScale.value
                            alpha = avatarPulseAlpha.value
                        }
                        .background(getProviderColor(settings.provider), shape = RoundedCornerShape(12.dp))
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isAILoading) getProviderColor(settings.provider) else avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUser) Icons.Default.Person else Icons.Default.Android,
                    contentDescription = "Avatar",
                    tint = avatarIconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Content Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isUser) "YOU" else settings.provider.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                    )

                    if (!isUser && message.processingTimeMs != null) {
                        val seconds = message.processingTimeMs.toDouble() / 1000.0
                        Text(
                            text = String.format("%.2fs", seconds),
                            fontSize = 10.sp,
                            color = getProviderColor(settings.provider)
                        )
                    }

                    if (message.isError) {
                        Surface(
                            color = Color(0x1AEF4444),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error Label",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "ERROR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                    
                    val timeString = remember(message.timestampMs) {
                        java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.timestampMs))
                    }
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
                    )
                }
            }

            // Text Content area with Markdown or animated bouncing dots
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.attachedUris.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(message.attachedUris) { uriString ->
                            Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(android.net.Uri.parse(uriString)),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                
                if (message.content.isEmpty() && isLatest && isLoading && message.thought.isNullOrEmpty()) {
                    BouncingDots(getProviderColor(settings.provider))
                } else {
                    message.thought?.let { thought ->
                        var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
                        androidx.compose.material3.Surface(
                            onClick = { expanded = !expanded },
                            color = getProviderColor(settings.provider).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology, 
                                        contentDescription = null,
                                        tint = getProviderColor(settings.provider),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Thinking trace", 
                                        style = MaterialTheme.typography.labelSmall,
                                        color = getProviderColor(settings.provider)
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = getProviderColor(settings.provider),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                androidx.compose.animation.AnimatedVisibility(expanded) {
                                    Text(
                                        text = thought,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    MarkdownText(
                        text = message.content,
                        color = if (isDark) Color.White else Color(0xFF18181B),
                        style = TextStyle(
                            color = if (isDark) Color.White else Color(0xFF18181B),
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        ),
                        isStreaming = !isUser && isLatest && isLoading
                    )
                }
                
                // Actions display when not user, and not currently loading entire assistant payload
                if (!isUser && !isLoading) {
                    ActionsRow(
                        message = message,
                        isLatest = isLatest,
                        isDark = isDark,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun ActionsRow(
    message: Message,
    isLatest: Boolean,
    isDark: Boolean,
    viewModel: ChatViewModel
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var isCopied by remember { mutableStateOf(false) }
        val context = LocalContext.current

        LaunchedEffect(isCopied) {
            if (isCopied) {
                delay(2000)
                isCopied = false
            }
        }

        IconButton(
            onClick = {
                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Copied Text", message.content))
                isCopied = true
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = "Copy message text",
                tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                modifier = Modifier.size(14.dp)
            )
        }

        if (isLatest && !message.isError && message.content.isNotEmpty()) {
            IconButton(
                onClick = {
                    viewModel.regenerateLastMessage()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Regenerate last message",
                    tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun BouncingDots(color: Color) {
    val duration = 600
    val maxOffset = (-8).dp

    val transition = rememberInfiniteTransition(label = "bouncing_dots")

    @Composable
    fun animateDot(delay: Int): Float {
        val anim = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_bounce"
        )
        return kotlin.math.sin(anim.value * Math.PI).toFloat()
    }

    val bounce1 = animateDot(0)
    val bounce2 = animateDot(150)
    val bounce3 = animateDot(300)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        listOf(bounce1, bounce2, bounce3).forEach { bounce ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        translationY = bounce * maxOffset.toPx()
                    }
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    style: TextStyle,
    isStreaming: Boolean = false
) {
    val cursorContent = text + if(isStreaming) " █" else ""
    com.mikepenz.markdown.m3.Markdown(
        content = cursorContent,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AgenticOptionsPanel(
    isDark: Boolean,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    var readWorkspace by remember { mutableStateOf(false) }
    var autoExecute by remember { mutableStateOf(false) }
    var writeAccess by remember { mutableStateOf(true) }
    var webSearch by remember { mutableStateOf(true) }
    var expandedProvider by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF1A1A1D) else Color(0xFFFAFAFA))
            .border(1.dp, if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Agent Configuration", 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black
            )
            
            Box {
                Row(
                    modifier = Modifier
                        .background(if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7), RoundedCornerShape(8.dp))
                        .clickable { expandedProvider = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp), tint = if(isDark) Color.White else Color.Black)
                    Text(text = settings.provider.name, fontSize = 12.sp, color = if(isDark) Color.White else Color.Black)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = if(isDark) Color.White else Color.Black)
                }
                
                androidx.compose.material3.DropdownMenu(
                    expanded = expandedProvider,
                    onDismissRequest = { expandedProvider = false },
                    modifier = Modifier.background(if(isDark) Color(0xFF27272A) else Color.White)
                ) {
                    listOf(
                        com.sednium.localspaces.ProviderType.NVIDIA, 
                        com.sednium.localspaces.ProviderType.OPENROUTER, 
                        com.sednium.localspaces.ProviderType.GROQ
                    ).forEach { prod ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(prod.name, color = if(isDark) Color.White else Color.Black) },
                            onClick = { 
                                onSettingsChange(settings.copy(provider = prod))
                                expandedProvider = false
                            }
                        )
                    }
                }
            }
        }
        
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val providerColor = getProviderColor(settings.provider)
            item { AgentToggle("File Analysis", readWorkspace, { readWorkspace = it }, isDark, Icons.Default.Search, providerColor) }
            item { AgentToggle("Code Execution", autoExecute, { autoExecute = it }, isDark, Icons.Default.PlayArrow, providerColor) }
            item { AgentToggle("Web Search", webSearch, { webSearch = it }, isDark, Icons.Default.Language, providerColor) }
            item { AgentToggle("Write Access", writeAccess, { writeAccess = it }, isDark, Icons.Default.Edit, providerColor) }
        }
        
        OutlinedTextField(
            value = settings.systemInstruction,
            onValueChange = { onSettingsChange(settings.copy(systemInstruction = it)) },
            placeholder = { Text("Premium System Prompt (e.g., 'You are an expert architect...'). Be highly specific.") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 120.dp),
            textStyle = TextStyle(color = if(isDark) Color.White else Color.Black, fontSize = 13.sp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if(isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
                focusedBorderColor = getProviderColor(settings.provider)
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun AgentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, isDark: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val tint = if (checked) color else if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (checked) (if (isDark) Color.White else Color.Black) else (if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)),
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun FileDropZone(
    isDark: Boolean,
    onClick: () -> Unit
) {
    val dashColor = if (isDark) Color(0xFF52525B) else Color(0xFFA1A1AA)
    val bgColor = if (isDark) Color(0xFF1A1A1D) else Color(0xFFFAFAFA)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .drawBehind {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
                drawRoundRect(color = dashColor, style = stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Upload, contentDescription = null, tint = dashColor)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Drag and drop files here", color = dashColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Supports .zip, .jpg, .mp4, etc.", color = dashColor.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun FilePreviewItem(uri: android.net.Uri, isDark: Boolean, onRemove: () -> Unit) {
    val context = LocalContext.current
    var mimeType by androidx.compose.runtime.remember { mutableStateOf<String?>("") }
    var fileName by androidx.compose.runtime.remember { mutableStateOf<String>("") }
    
    androidx.compose.runtime.LaunchedEffect(uri) {
        val type = context.contentResolver.getType(uri)
        mimeType = type
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)),
        contentAlignment = Alignment.Center
    ) {
        if (mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(uri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    imageVector = if (mimeType?.contains("zip") == true || fileName.endsWith(".zip")) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isDark) Color.White else Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = fileName.take(10) + if(fileName.length > 10) "..." else "",
                    fontSize = 8.sp,
                    maxLines = 1,
                    color = if (isDark) Color.White else Color.Black,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        androidx.compose.material3.IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

fun getProviderColor(provider: com.sednium.localspaces.ProviderType): Color {
    return when(provider) {
        com.sednium.localspaces.ProviderType.OPENAI -> Color(0xFF10A37F)
        com.sednium.localspaces.ProviderType.ANTHROPIC -> Color(0xFFD97757)
        com.sednium.localspaces.ProviderType.GEMINI -> Color(0xFF1A73E8)
        com.sednium.localspaces.ProviderType.NVIDIA -> Color(0xFF76B900)
        com.sednium.localspaces.ProviderType.OPENROUTER -> Color(0xFF3B82F6)
        com.sednium.localspaces.ProviderType.GROQ -> Color(0xFFF55036)
        com.sednium.localspaces.ProviderType.XAI -> Color(0xFF1DA1F2)
        com.sednium.localspaces.ProviderType.LOCAL -> Color(0xFFF59E0B)
        com.sednium.localspaces.ProviderType.CUSTOM -> Color(0xFF6366F1)
        com.sednium.localspaces.ProviderType.NATIVE_ANDROID -> Color(0xFF3DDC84)
    }
}

enum class ChatMode(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    QUICK("Quick", Icons.Default.FlashOn),
    THINKING("Thinking", Icons.Default.Psychology),
    CODING("Coding", Icons.Default.Code)
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    isDark: Boolean,
    isLoading: Boolean,
    onSend: (ChatMode, List<android.net.Uri>) -> Unit,
    onStop: () -> Unit
) {
    var selectedMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ChatMode.QUICK) }
    var attachedUris by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<android.net.Uri>>(emptyList()) }
    
    val filePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            attachedUris = attachedUris + uris
        }
    )
    
    val barBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val borderColor = if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)

    Surface(
        color = barBg,
        tonalElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Formatting toolbar
                Row(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    androidx.compose.material3.IconButton(
                        onClick = { 
                            filePicker.launch("*/*")
                        },
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                    }
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(ChatMode.entries) { mode ->
                            val isSelected = selectedMode == mode
                            val bg = if (isSelected) {
                                if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7)
                            } else Color.Transparent
                            val contentColor = if (isSelected) {
                                if (isDark) Color.White else Color.Black
                            } else {
                                if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
                            }
                            Row(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .combinedClickable(
                                        onClick = { selectedMode = mode },
                                        onLongClick = {
                                            android.widget.Toast.makeText(context, mode.label, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(mode.icon, contentDescription = mode.label, tint = contentColor, modifier = Modifier.size(16.dp))
                                Text(
                                    text = mode.label,
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                
                if (selectedMode == ChatMode.CODING) {
                    AgenticOptionsPanel(isDark = isDark, settings = settings, onSettingsChange = onSettingsChange)
                }

                // Centered inputbox of max 720dp width
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF121212) else Color(0xFFF4F4F5))
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    if (attachedUris.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(attachedUris) { uri ->
                                FilePreviewItem(uri = uri, isDark = isDark, onRemove = { attachedUris = attachedUris.filter { it != uri } })
                            }
                        }
                    } else if (selectedMode == ChatMode.CODING) {
                        FileDropZone(isDark = isDark, onClick = { filePicker.launch("*/*") })
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    // Left Input field area
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = settings.isConfigValid && !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            .testTag("chat_input"),
                        textStyle = TextStyle(
                            color = if (isDark) Color.White else Color(0xFF18181B),
                            fontSize = 16.sp
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Default
                        ),
                        minLines = 1,
                        maxLines = 5,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (value.isBlank()) {
                                    Text(
                                        text = if (settings.isConfigValid) "Message..." else "Setup required...",
                                        color = if (isDark) Color(0xFF52525B) else Color(0xFFA1A1AA),
                                        style = TextStyle(fontSize = 16.sp)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1AEF4444))
                                    .clickable { onStop() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop Generation",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        val isSendActive = value.isNotBlank() && settings.isConfigValid && !isLoading
                        val sendBg = if (isSendActive) {
                            getProviderColor(settings.provider)
                        } else {
                            if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7) // Subtle accent grey when inactive
                        }
                        val sendIconColor = if (isSendActive) {
                            Color.White
                        } else {
                            if (isDark) Color(0xFF71717A) else Color(0xFFA1A1AA)
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(sendBg)
                                .clickable(enabled = isSendActive) { 
                                    onSend(selectedMode, attachedUris)
                                    attachedUris = emptyList()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = sendIconColor,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send prompt button",
                                    tint = sendIconColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                } // Close the Row inside the Column

                // Footer description row
                Row(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modelDisplayName = settings.modelName.ifBlank {
                            if (settings.provider == com.sednium.localspaces.ProviderType.NATIVE_ANDROID) "On-Device Model" else "Settings Model"
                        }
                        val historyStatus = if (settings.enableHistory) "History On" else "History Off"
                        Text(
                            text = "$modelDisplayName • $historyStatus",
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                        
                        if (settings.provider == com.sednium.localspaces.ProviderType.LOCAL || settings.provider == com.sednium.localspaces.ProviderType.CUSTOM) {
                            LlamaServerStatusIndicator(
                                baseUrl = settings.localBaseUrl,
                                isDark = isDark
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LlamaServerStatusIndicator(baseUrl: String, isDark: Boolean) {
    var status by remember { mutableStateOf<Boolean?>(null) }
    
    LaunchedEffect(baseUrl) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            while (true) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(baseUrl.removeSuffix("/") + "/models")
                        .build()
                    val response = client.newCall(request).execute()
                    status = response.isSuccessful
                    response.close()
                } catch (e: Exception) {
                    status = false
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val color = when(status) {
            true -> Color(0xFF10B981) // Emerald Green
            false -> Color(0xFFEF4444) // Red
            null -> Color.Gray
        }
        Box(
            modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color)
        )
        Text(
            text = when(status) {
                true -> "Llama.cpp Connected"
                false -> "Llama.cpp Disconnected"
                null -> "Checking Server..."
            },
            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f),
            fontSize = 10.sp
        )
    }
}
