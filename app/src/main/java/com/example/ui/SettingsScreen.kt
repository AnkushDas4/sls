package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.example.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModelResources: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val analytics by viewModel.analytics.collectAsState()
    val haptic = LocalHapticFeedback.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                viewModel.updateSettings(settings.copy(nativeModelUriString = it.toString()))
            }
        }
    )

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.exportHistory(it) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Model Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            var expandedProvider by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedProvider,
                onExpandedChange = { expandedProvider = !expandedProvider }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = settings.provider.name,
                    onValueChange = { },
                    label = { Text("Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedProvider,
                    onDismissRequest = { expandedProvider = false }
                ) {
                    com.example.ProviderType.values().forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.name) },
                            onClick = {
                                val newModelName = if (settings.provider == selectionOption) settings.modelName else ""
                                viewModel.updateSettings(settings.copy(provider = selectionOption, modelName = newModelName))
                                expandedProvider = false
                            }
                        )
                    }
                }
            }

            if (settings.provider != com.example.ProviderType.NATIVE_ANDROID) {
                val modelPresets = when (settings.provider) {
                    com.example.ProviderType.OPENAI -> listOf(
                        "gpt-4o" to "GPT-4o",
                        "gpt-4o-mini" to "GPT-4o Mini",
                        "o1-mini" to "o1-mini",
                        "o3-mini" to "o3-mini"
                    )
                    com.example.ProviderType.ANTHROPIC -> listOf(
                        "claude-3-5-sonnet-20241022" to "Claude 3.5 Sonnet",
                        "claude-3-5-haiku-20241022" to "Claude 3.5 Haiku",
                        "claude-3-opus-20240229" to "Claude 3 Opus"
                    )
                    com.example.ProviderType.XAI -> listOf(
                        "grok-2-1212" to "Grok 2",
                        "grok-beta" to "Grok Beta"
                    )
                    com.example.ProviderType.GEMINI -> listOf(
                        "gemini-3.5-flash" to "Gemini 3.5 Flash",
                        "gemini-3.1-pro-preview" to "Gemini 3.1 Pro Preview"
                    )
                    com.example.ProviderType.NVIDIA -> listOf(
                        "meta/llama-3.1-405b-instruct" to "Llama 3.1 405B",
                        "meta/llama-3.1-70b-instruct" to "Llama 3.1 70B",
                        "mistralai/mistral-large" to "Mistral Large",
                        "mistralai/mixtral-8x22b-instruct-v0.1" to "Mixtral 8x22B",
                        "google/gemma-2-27b-it" to "Gemma 2 27B",
                        "microsoft/phi-3-medium-128k-instruct" to "Phi-3 Medium"
                    )
                    com.example.ProviderType.OPENROUTER -> listOf(
                        "openrouter/auto" to "Auto Settings",
                        "google/gemini-2.5-pro" to "Gemini 2.5 Pro",
                        "anthropic/claude-3.5-sonnet" to "Claude 3.5 Sonnet",
                        "meta-llama/llama-3.3-70b-instruct" to "Llama 3.3 70B",
                        "deepseek/deepseek-chat" to "DeepSeek Chat"
                    )
                    com.example.ProviderType.GROQ -> listOf(
                        "llama-3.3-70b-versatile" to "Llama 3.3 70B Versatile",
                        "llama-3.1-8b-instant" to "Llama 3.1 8B Instant",
                        "mixtral-8x7b-32768" to "Mixtral 8x7B"
                    )
                    com.example.ProviderType.LOCAL -> listOf(
                        "llama3" to "Llama 3",
                        "phi3" to "Phi 3",
                        "mistral" to "Mistral"
                    )
                    else -> emptyList()
                }

                if (modelPresets.isNotEmpty()) {
                    var expandedModel by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedModel,
                        onExpandedChange = { expandedModel = !expandedModel }
                    ) {
                        OutlinedTextField(
                            value = settings.modelName,
                            onValueChange = { viewModel.updateSettings(settings.copy(modelName = it)) },
                            label = { Text("Model Name / ID") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModel,
                            onDismissRequest = { expandedModel = false }
                        ) {
                            modelPresets.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text("$label ($value)") },
                                    onClick = {
                                        viewModel.updateSettings(settings.copy(modelName = value))
                                        expandedModel = false
                                    }
                               )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = settings.modelName,
                        onValueChange = { viewModel.updateSettings(settings.copy(modelName = it)) },
                        label = { Text("Model Name / ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (settings.provider) {
                com.example.ProviderType.OPENAI -> {
                    ApiKeyEditor(
                        label = "OpenAI API Key",
                        apiKey = settings.openaiApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(openaiApiKey = it)) },
                        onGetUrl = "https://platform.openai.com/api-keys",
                        onGetLinkText = "Get OpenAI API Key ↗"
                    )
                }
                com.example.ProviderType.ANTHROPIC -> {
                    ApiKeyEditor(
                        label = "Anthropic API Key",
                        apiKey = settings.anthropicApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(anthropicApiKey = it)) },
                        onGetUrl = "https://console.anthropic.com/settings/keys",
                        onGetLinkText = "Get Anthropic API Key ↗"
                    )
                }
                com.example.ProviderType.XAI -> {
                    ApiKeyEditor(
                        label = "xAI API Key",
                        apiKey = settings.xaiApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(xaiApiKey = it)) },
                        onGetUrl = "https://console.x.ai/",
                        onGetLinkText = "Get xAI API Key ↗"
                    )
                }
                com.example.ProviderType.NVIDIA -> {
                    ApiKeyEditor(
                        label = "NVIDIA API Key",
                        apiKey = settings.nvidiaApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(nvidiaApiKey = it)) },
                        onGetUrl = "https://build.nvidia.com",
                        onGetLinkText = "Get NVIDIA API Key ↗"
                    )
                }
                com.example.ProviderType.GEMINI -> {
                    ApiKeyEditor(
                        label = "Gemini API Key",
                        apiKey = settings.geminiApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(geminiApiKey = it)) },
                        onGetUrl = "https://aistudio.google.com/app/apikey",
                        onGetLinkText = "Get Gemini API Key ↗"
                    )
                }
                com.example.ProviderType.OPENROUTER -> {
                    ApiKeyEditor(
                        label = "OpenRouter API Key",
                        apiKey = settings.openrouterApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(openrouterApiKey = it)) },
                        onGetUrl = "https://openrouter.ai/keys",
                        onGetLinkText = "Get OpenRouter API Key ↗"
                    )
                }
                com.example.ProviderType.GROQ -> {
                    ApiKeyEditor(
                        label = "Groq API Key",
                        apiKey = settings.groqApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(groqApiKey = it)) },
                        onGetUrl = "https://console.groq.com/keys",
                        onGetLinkText = "Get Groq API Key ↗"
                    )
                }
                com.example.ProviderType.CUSTOM -> {
                    OutlinedTextField(
                        value = settings.localBaseUrl,
                        onValueChange = { viewModel.updateSettings(settings.copy(localBaseUrl = it)) },
                        label = { Text("API Path URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ApiKeyEditor(
                        label = "Custom API Key",
                        apiKey = settings.customApiKey,
                        onSave = { viewModel.updateSettings(settings.copy(customApiKey = it)) }
                    )
                }
                com.example.ProviderType.LOCAL -> {
                    OutlinedTextField(
                        value = settings.localBaseUrl,
                        onValueChange = { viewModel.updateSettings(settings.copy(localBaseUrl = it)) },
                        label = { Text("API Path URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                com.example.ProviderType.NATIVE_ANDROID -> {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Local Model File (.gguf)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = settings.nativeModelUriString ?: "No model selected", 
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.nativeModelUriString == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                filePickerLauncher.launch(arrayOf("*/*")) 
                            }) {
                                Text("Select Model File")
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider()

            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App Theme", style = MaterialTheme.typography.labelLarge)
                
                var expandedTheme by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedTheme,
                    onExpandedChange = { expandedTheme = !expandedTheme }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = when (settings.theme) {
                            com.example.Theme.LIGHT -> "Light"
                            com.example.Theme.DARK -> "Dark"
                            com.example.Theme.SYSTEM -> "System Default"
                        },
                        onValueChange = { },
                        label = { Text("Theme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTheme,
                        onDismissRequest = { expandedTheme = false }
                    ) {
                        com.example.Theme.values().forEach { selectTheme ->
                            val label = when (selectTheme) {
                                com.example.Theme.LIGHT -> "Light"
                                com.example.Theme.DARK -> "Dark"
                                com.example.Theme.SYSTEM -> "System Default"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.updateSettings(settings.copy(theme = selectTheme))
                                    expandedTheme = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dynamic Color", style = MaterialTheme.typography.labelLarge)
                        Text("Use system dynamic color palette (Android 12+)", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = settings.enableDynamicColor,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(enableDynamicColor = it)) }
                    )
                }
            }

            HorizontalDivider()

            Text("Parameters", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-scroll chat to bottom", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = settings.autoScrollToBottom,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(autoScrollToBottom = it)) }
                    )
                }
            }

            Column {
                Text("System Instruction", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = settings.systemInstruction,
                    onValueChange = { viewModel.updateSettings(settings.copy(systemInstruction = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temperature: ${"%.2f".format(settings.temperature)}", style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = { viewModel.updateSettings(settings.copy(temperature = 0.7)) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Reset")
                    }
                }
                Slider(
                    value = settings.temperature.toFloat(),
                    onValueChange = { 
                        viewModel.updateSettings(settings.copy(temperature = it.toDouble())) 
                    },
                    valueRange = 0f..2f
                )
            }

            HorizontalDivider()

            Text("Model Resources", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onNavigateToModelResources) {
                Text("View Recommended Models")
            }

            HorizontalDivider()

            Text("Usage Analytics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Messages Processed", style = MaterialTheme.typography.bodyMedium)
                    Text("${analytics.totalMessages}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Tokens Generated", style = MaterialTheme.typography.bodyMedium)
                    Text("${analytics.totalTokens}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Processing Time", style = MaterialTheme.typography.bodyMedium)
                    val timeSeconds = analytics.totalTimeMs / 1000.0
                    Text(String.format("%.1f s", timeSeconds), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Button(
                onClick = { viewModel.clearAnalytics() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Analytics")
            }

            Button(
                onClick = { exportFileLauncher.launch("chat_history.txt") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Chat History")
            }

            Button(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Clear Chat History")
            }
        }
    }
}

@Composable
fun ApiKeyEditor(
    label: String,
    apiKey: String,
    onSave: (String) -> Unit,
    onGetUrl: String? = null,
    onGetLinkText: String? = null
) {
    var isEditing by remember(label, apiKey) { mutableStateOf(false) }
    var tempKey by remember(label, apiKey) { mutableStateOf(apiKey) }
    val context = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = if (isEditing) tempKey else apiKey,
                onValueChange = { tempKey = it; isEditing = true },
                label = { Text(label) },
                visualTransformation = if (!isEditing && apiKey.isNotEmpty()) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.weight(1f)
            )
            if (isEditing) {
                Button(
                    onClick = {
                        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Authentication required")
                            .setSubtitle("Authenticate to save API Key")
                            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            .build()
                        
                        val biometricPrompt = androidx.biometric.BiometricPrompt(
                            context,
                            androidx.core.content.ContextCompat.getMainExecutor(context),
                            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                    onSave(tempKey)
                                    isEditing = false
                                }
                            }
                        )
                        biometricPrompt.authenticate(promptInfo)
                    }
                ) {
                    Text("Save")
                }
            }
        }
        if (onGetUrl != null && onGetLinkText != null) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            TextButton(
                onClick = { uriHandler.openUri(onGetUrl) },
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(onGetLinkText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
