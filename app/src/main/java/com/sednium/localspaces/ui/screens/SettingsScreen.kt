package com.sednium.localspaces.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.ChatMode
import com.sednium.localspaces.model.ModelProvider
import com.sednium.localspaces.model.SavedModelPreset
import com.sednium.localspaces.model.PROVIDER_CONFIG
import com.sednium.localspaces.navigation.LocalServerStatus
import com.sednium.localspaces.ui.components.SettingsSectionLabel
import com.sednium.localspaces.ui.components.SettingsSliderRow
import com.sednium.localspaces.ui.components.SettingsSwitchRow
import com.sednium.localspaces.ui.components.SettingsTextField
import com.sednium.localspaces.ui.theme.OrangeAlpha
import com.sednium.localspaces.ui.theme.SedniumColors

enum class SettingsTab {
    API_MODELS, FEATURES_GENERAL
}

fun authenticateWithBiometrics(
    activity: FragmentActivity,
    onSuccess: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Authentication required")
        .setSubtitle("Authenticate to view or edit your API Key")
        .setDeviceCredentialAllowed(true)
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    localServerStatus: LocalServerStatus = LocalServerStatus.UNKNOWN,
    onUpdateSettings: (AppSettings) -> Unit,
    onClose: () -> Unit
) {
    var currentTab by remember { mutableStateOf(SettingsTab.API_MODELS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // --- Header Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                SettingsTabButton(
                    title = "API & MODELS",
                    isSelected = currentTab == SettingsTab.API_MODELS,
                    modifier = Modifier.weight(1f),
                    onClick = { currentTab = SettingsTab.API_MODELS }
                )
                SettingsTabButton(
                    title = "FEATURES & GENERAL",
                    isSelected = currentTab == SettingsTab.FEATURES_GENERAL,
                    modifier = Modifier.weight(1f),
                    onClick = { currentTab = SettingsTab.FEATURES_GENERAL }
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = SedniumColors.Orange)
            }
        }
        
        HorizontalDivider(color = OrangeAlpha.a20, thickness = 1.dp)

        // --- Content ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (currentTab == SettingsTab.API_MODELS) {
                ApiModelsContent(settings, localServerStatus, onUpdateSettings)
            } else {
                FeaturesGeneralContent(settings, onUpdateSettings)
            }
        }
    }
}

@Composable
fun SettingsTabButton(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) SedniumColors.Orange else OrangeAlpha.a40,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .fillMaxWidth(if (title.length > 5) 0.8f else 1f)
                    .background(SedniumColors.Orange)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiModelsContent(
    settings: AppSettings,
    localServerStatus: LocalServerStatus,
    onUpdateSettings: (AppSettings) -> Unit
) {
    var apiKeyUnlocked by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Provider Dropdown
    SettingsSectionLabel("CHOOSE PROVIDER")
    var expandedProviderDropdown by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandedProviderDropdown,
        onExpandedChange = { expandedProviderDropdown = it }
    ) {
        SettingsTextField(
            label = "",
            value = PROVIDER_CONFIG[settings.provider]?.displayName ?: settings.provider.name,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProviderDropdown) }
        )
        ExposedDropdownMenu(
            expanded = expandedProviderDropdown,
            onDismissRequest = { expandedProviderDropdown = false },
            modifier = Modifier.background(SedniumColors.Milk)
        ) {
            ModelProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(PROVIDER_CONFIG[provider]?.displayName ?: provider.name, color = SedniumColors.Orange) },
                    onClick = {
                        val popularModels = PROVIDER_CONFIG[provider]?.popularModels
                        val defaultModel = popularModels?.firstOrNull()?.id ?: ""
                        onUpdateSettings(settings.copy(provider = provider, model = defaultModel))
                        expandedProviderDropdown = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // API Key Field
    val providerName = PROVIDER_CONFIG[settings.provider]?.displayName ?: ""
    SettingsSectionLabel("${providerName.uppercase()} API KEY")
    SettingsTextField(
        label = "",
        value = if (!apiKeyUnlocked && apiKeyFor(settings).isNotEmpty()) "••••••••••••••••••••" else apiKeyFor(settings),
        onValueChange = { if (apiKeyUnlocked) onUpdateSettings(updateApiKeyFor(settings, it)) },
        placeholder = "sk-…",
        isSecret = !apiKeyUnlocked && apiKeyFor(settings).isNotEmpty(),
        readOnly = !apiKeyUnlocked,
        trailingIcon = {
            Row(modifier = Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (apiKeyUnlocked) "LOCK" else "EDIT/VIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SedniumColors.Orange,
                    modifier = Modifier.clickable {
                        if (apiKeyUnlocked) {
                            apiKeyUnlocked = false
                        } else {
                            if (context is FragmentActivity) {
                                authenticateWithBiometrics(context) {
                                    apiKeyUnlocked = true
                                }
                            }
                        }
                    }.padding(4.dp)
                )
                if (apiKeyUnlocked) {
                    Text(
                        text = "CLEAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SedniumColors.Orange,
                        modifier = Modifier.clickable { onUpdateSettings(updateApiKeyFor(settings, "")) }.padding(4.dp)
                    )
                }
            }
        }
    )
    val apiLink = PROVIDER_CONFIG[settings.provider]?.apiLink
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!apiLink.isNullOrBlank()) {
            TextButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(apiLink))
                    context.startActivity(intent)
                }
            ) {
                Text("Get API Key", style = MaterialTheme.typography.labelSmall, color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        var testConnectionStatus by remember { mutableStateOf<String?>(null) }
        var isTestingConnection by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (testConnectionStatus != null) {
                Text(
                    text = testConnectionStatus!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (testConnectionStatus == "Success") Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            TextButton(
                enabled = !isTestingConnection && apiKeyFor(settings).isNotBlank(),
                onClick = {
                    val apiKeyToTest = apiKeyFor(settings)
                    val providerToTest = settings.provider
                    isTestingConnection = true
                    testConnectionStatus = "Testing..."
                    scope.launch {
                        try {
                            val success = com.sednium.localspaces.api.testApiKey(apiKeyToTest, providerToTest)
                            testConnectionStatus = if (success) "Success" else "Failed"
                        } catch (e: Exception) {
                            testConnectionStatus = "Failed"
                        } finally {
                            isTestingConnection = false
                        }
                    }
                }
            ) {
                Text(if (isTestingConnection) "Testing..." else "Test Connection", style = MaterialTheme.typography.labelSmall, color = if (apiKeyFor(settings).isNotBlank()) SedniumColors.Orange else Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (settings.provider == ModelProvider.LOCAL || settings.provider == ModelProvider.CUSTOM) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsSectionLabel("Base URL")
            if (settings.provider == ModelProvider.LOCAL && localServerStatus != LocalServerStatus.UNKNOWN) {
                Spacer(modifier = Modifier.width(8.dp))
                val statusColor = when (localServerStatus) {
                    LocalServerStatus.IDLE -> Color(0xFF4CAF50)
                    LocalServerStatus.PROCESSING -> Color(0xFFFFC107)
                    LocalServerStatus.OFFLINE -> Color(0xFFF44336)
                    else -> Color.Transparent
                }
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            }
        }
        SettingsTextField(
            label = "",
            value = settings.localBaseUrl,
            onValueChange = { onUpdateSettings(settings.copy(localBaseUrl = it)) },
            placeholder = "http://localhost:11434/v1"
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Model Dropdown
    SettingsSectionLabel("Select Model")
    var expandedModelDropdown by remember { mutableStateOf(false) }
    val modelsForProvider = PROVIDER_CONFIG[settings.provider]?.popularModels ?: emptyList()

    if (modelsForProvider.isEmpty()) {
        SettingsTextField(
            label = "",
            value = settings.model,
            onValueChange = { onUpdateSettings(settings.copy(model = it)) },
            placeholder = "e.g. gemini-1.5-pro"
        )
    } else {
        ExposedDropdownMenuBox(
            expanded = expandedModelDropdown,
            onExpandedChange = { expandedModelDropdown = it }
        ) {
            val displayValue = modelsForProvider.find { it.id == settings.model }?.label ?: settings.model
            SettingsTextField(
                label = "",
                value = displayValue,
                onValueChange = {},
                placeholder = "Select or type a model",
                readOnly = true,
                modifier = Modifier.menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown) }
            )
            ExposedDropdownMenu(
                expanded = expandedModelDropdown,
                onDismissRequest = { expandedModelDropdown = false },
                modifier = Modifier.background(SedniumColors.Milk)
            ) {
                modelsForProvider.forEach { modelOption ->
                    DropdownMenuItem(
                        text = { Text(modelOption.label, color = SedniumColors.Orange) },
                        leadingIcon = {
                            val iconVector = when(modelOption.icon) {
                                com.sednium.localspaces.model.ModelIconType.TEXT -> androidx.compose.material.icons.Icons.Default.Notes
                                com.sednium.localspaces.model.ModelIconType.CODE -> androidx.compose.material.icons.Icons.Default.Code
                                com.sednium.localspaces.model.ModelIconType.AGENT -> androidx.compose.material.icons.Icons.Default.Psychology
                                com.sednium.localspaces.model.ModelIconType.IMAGE -> androidx.compose.material.icons.Icons.Default.Image
                                com.sednium.localspaces.model.ModelIconType.VIDEO -> androidx.compose.material.icons.Icons.Default.PlayArrow
                                com.sednium.localspaces.model.ModelIconType.AUTO -> androidx.compose.material.icons.Icons.Default.Refresh
                                com.sednium.localspaces.model.ModelIconType.LIGHTNING -> androidx.compose.material.icons.Icons.Default.FlashOn
                            }
                            androidx.compose.material3.Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = SedniumColors.Orange
                            )
                        },
                        onClick = {
                            onUpdateSettings(settings.copy(model = modelOption.id))
                            expandedModelDropdown = false
                        }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // System Prompts
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SYSTEM PROMPTS", style = MaterialTheme.typography.labelSmall, color = OrangeAlpha.a70, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.Description, contentDescription = null, tint = OrangeAlpha.a70, modifier = Modifier.size(16.dp))
        }
        Box(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(Color.Transparent)
                .border(1.dp, SedniumColors.Orange, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .clickable {
                     val newMode = when(settings.chatMode) {
                        ChatMode.QUICK -> ChatMode.THINKING
                        ChatMode.THINKING -> ChatMode.CODING
                        ChatMode.CODING -> ChatMode.QUICK
                    }
                    onUpdateSettings(settings.copy(chatMode = newMode))
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("${settings.chatMode.name} MODE", style = MaterialTheme.typography.labelSmall, color = SedniumColors.Orange, fontWeight = FontWeight.Bold)
        }
    }
    
    SettingsTextField(
        label = "",
        value = settings.systemInstruction,
        onValueChange = { onUpdateSettings(settings.copy(systemInstruction = it)) },
        placeholder = "You are an elite, world-class software architect...",
        singleLine = false,
        modifier = Modifier.height(200.dp)
    )
}

@Composable
fun FeaturesGeneralContent(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit
) {
    SettingsSectionLabel("BEHAVIOR MODE")
    Text(
        text = settings.chatMode.name.lowercase(),
        style = MaterialTheme.typography.bodySmall,
        color = OrangeAlpha.a70,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    val sliderPosition = when(settings.chatMode) {
        ChatMode.QUICK -> 0f
        ChatMode.THINKING -> 1f
        ChatMode.CODING -> 2f
    }
    Slider(
        value = sliderPosition,
        onValueChange = { value ->
            val newMode = when {
                value < 0.5f -> ChatMode.QUICK
                value < 1.5f -> ChatMode.THINKING
                else -> ChatMode.CODING
            }
            onUpdateSettings(settings.copy(chatMode = newMode))
        },
        valueRange = 0f..2f,
        steps = 1,
        colors = SliderDefaults.colors(
            thumbColor = SedniumColors.Orange,
            activeTrackColor = OrangeAlpha.a40,
            inactiveTrackColor = OrangeAlpha.a20
        )
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("QUICK", style = MaterialTheme.typography.labelSmall, color = OrangeAlpha.a70)
        Text("THINKING", style = MaterialTheme.typography.labelSmall, color = OrangeAlpha.a70)
        Text("CODING", style = MaterialTheme.typography.labelSmall, color = OrangeAlpha.a70)
    }
    
    HorizontalDivider(color = OrangeAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

    SettingsSectionLabel("GENERATION PARAMETERS")
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
        label = "Max Tokens (Output)",
        value = settings.maxTokens.toFloat(),
        onValueChange = { onUpdateSettings(settings.copy(maxTokens = it.toInt())) },
        valueRange = 256f..32000f,
        displayFormat = { it.toInt().toString() }
    )

    HorizontalDivider(color = OrangeAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

    // Tool Calling
    SettingsSwitchRow(
        label = "TOOL CALLING CAPABILITIES",
        description = "Enables AI to make zips, read workflows, use MCPs. Turning off saves API cost.",
        checked = settings.enableTools,
        onCheckedChange = { onUpdateSettings(settings.copy(enableTools = it)) }
    )
    
    HorizontalDivider(color = OrangeAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))
    
    // History
    SettingsSwitchRow(
        label = "SAVE CHAT HISTORY",
        description = "Persist chats to local storage",
        checked = settings.enableHistory,
        onCheckedChange = { onUpdateSettings(settings.copy(enableHistory = it)) }
    )

    HorizontalDivider(color = OrangeAlpha.a20, modifier = Modifier.padding(vertical = 12.dp))

    // Presets
    SettingsSectionLabel("SAVED MODEL CONFIGURATIONS")
    Text("Save your current Provider, Model, Mode, and Prompts as a preset to quickly switch in the main chat.", style = MaterialTheme.typography.bodySmall, color = OrangeAlpha.a70)
    
    Spacer(modifier = Modifier.height(12.dp))
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            SettingsTextField(
                label = "",
                value = "",
                onValueChange = { },
                placeholder = "e.g. Code Llama Fast"
            )
        }
        Button(
            onClick = {
                val newPreset = SavedModelPreset(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${settings.provider.name} - ${settings.model}",
                    provider = settings.provider,
                    model = settings.model,
                    chatMode = settings.chatMode,
                    systemInstruction = settings.systemInstruction
                )
                onUpdateSettings(settings.copy(
                    savedPresets = settings.savedPresets + newPreset,
                    activePresetId = newPreset.id
                ))
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = SedniumColors.Orange,
                contentColor = SedniumColors.Milk
            )
        ) {
            Text("SAVE", fontWeight = FontWeight.Bold)
        }
    }
}

fun apiKeyFor(s: AppSettings): String = when (s.provider) {
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

