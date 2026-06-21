package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sednium_settings")

class SettingsRepository(private val context: Context) {
    private val moshi = Moshi.Builder().build()
    private val messageListType = Types.newParameterizedType(List::class.java, Message::class.java)
    private val messageListAdapter = moshi.adapter<List<Message>>(messageListType)

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val PROVIDER = stringPreferencesKey("provider")
        val NATIVE_MODEL_URI = stringPreferencesKey("native_model_uri")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val CHAT_HISTORY = stringPreferencesKey("sednium_history")
        val SYSTEM_INSTRUCTION = stringPreferencesKey("system_instruction")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        val XAI_API_KEY = stringPreferencesKey("xai_api_key")
        val NVIDIA_API_KEY = stringPreferencesKey("nvidia_api_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        val LOCAL_BASE_URL = stringPreferencesKey("local_base_url")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val ENABLE_HAPTICS = booleanPreferencesKey("enable_haptics")
        val ENABLE_DYNAMIC_COLOR = booleanPreferencesKey("enable_dynamic_color")
        val SEDNIUM_SETTINGS_V1 = booleanPreferencesKey("sednium_settings_v1")
        
        // Analytics
        val TOTAL_TOKENS = longPreferencesKey("total_tokens")
        val TOTAL_MESSAGES = longPreferencesKey("total_messages")
        val TOTAL_TIME_MS = longPreferencesKey("total_time_ms")
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            !preferences.contains(PreferencesKeys.SEDNIUM_SETTINGS_V1)
        }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeStr = preferences[PreferencesKeys.THEME] ?: Theme.SYSTEM.name
            val providerStr = preferences[PreferencesKeys.PROVIDER] ?: ProviderType.NATIVE_ANDROID.name
            
            AppSettings(
                theme = Theme.valueOf(themeStr),
                provider = ProviderType.valueOf(providerStr),
                nativeModelUriString = preferences[PreferencesKeys.NATIVE_MODEL_URI],
                temperature = preferences[PreferencesKeys.TEMPERATURE] ?: 0.7,
                systemInstruction = preferences[PreferencesKeys.SYSTEM_INSTRUCTION] ?: "You are a helpful, concise AI assistant.",
                autoScrollToBottom = preferences[PreferencesKeys.AUTO_SCROLL] ?: true,
                openaiApiKey = preferences[PreferencesKeys.OPENAI_API_KEY] ?: "",
                anthropicApiKey = preferences[PreferencesKeys.ANTHROPIC_API_KEY] ?: "",
                xaiApiKey = preferences[PreferencesKeys.XAI_API_KEY] ?: "",
                nvidiaApiKey = preferences[PreferencesKeys.NVIDIA_API_KEY] ?: "",
                geminiApiKey = preferences[PreferencesKeys.GEMINI_API_KEY] ?: "",
                openrouterApiKey = preferences[PreferencesKeys.OPENROUTER_API_KEY] ?: "",
                groqApiKey = preferences[PreferencesKeys.GROQ_API_KEY] ?: "",
                customApiKey = preferences[PreferencesKeys.CUSTOM_API_KEY] ?: "",
                localBaseUrl = preferences[PreferencesKeys.LOCAL_BASE_URL] ?: "http://10.0.2.2:11434/v1",
                modelName = preferences[PreferencesKeys.MODEL_NAME] ?: "",
                enableHaptics = preferences[PreferencesKeys.ENABLE_HAPTICS] ?: true,
                enableDynamicColor = preferences[PreferencesKeys.ENABLE_DYNAMIC_COLOR] ?: true
            )
        }

    val chatHistoryFlow: Flow<List<Message>> = context.dataStore.data
        .map { preferences ->
            val historyStr = preferences[PreferencesKeys.CHAT_HISTORY]
            if (historyStr.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    messageListAdapter.fromJson(historyStr) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    val analyticsFlow: Flow<AppAnalytics> = context.dataStore.data
        .map { preferences ->
            AppAnalytics(
                totalTokens = preferences[PreferencesKeys.TOTAL_TOKENS] ?: 0L,
                totalMessages = preferences[PreferencesKeys.TOTAL_MESSAGES] ?: 0L,
                totalTimeMs = preferences[PreferencesKeys.TOTAL_TIME_MS] ?: 0L
            )
        }

    suspend fun recordAnalytics(tokens: Long, timeMs: Long) {
        context.dataStore.edit { preferences ->
            val currentTokens = preferences[PreferencesKeys.TOTAL_TOKENS] ?: 0L
            val currentMessages = preferences[PreferencesKeys.TOTAL_MESSAGES] ?: 0L
            val currentTimeMs = preferences[PreferencesKeys.TOTAL_TIME_MS] ?: 0L
            
            preferences[PreferencesKeys.TOTAL_TOKENS] = currentTokens + tokens
            preferences[PreferencesKeys.TOTAL_MESSAGES] = currentMessages + 1
            preferences[PreferencesKeys.TOTAL_TIME_MS] = currentTimeMs + timeMs
        }
    }

    suspend fun clearAnalytics() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOTAL_TOKENS] = 0L
            preferences[PreferencesKeys.TOTAL_MESSAGES] = 0L
            preferences[PreferencesKeys.TOTAL_TIME_MS] = 0L
        }
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = settings.theme.name
            preferences[PreferencesKeys.PROVIDER] = settings.provider.name
            preferences[PreferencesKeys.TEMPERATURE] = settings.temperature
            preferences[PreferencesKeys.SYSTEM_INSTRUCTION] = settings.systemInstruction
            preferences[PreferencesKeys.AUTO_SCROLL] = settings.autoScrollToBottom
            preferences[PreferencesKeys.OPENAI_API_KEY] = settings.openaiApiKey
            preferences[PreferencesKeys.ANTHROPIC_API_KEY] = settings.anthropicApiKey
            preferences[PreferencesKeys.XAI_API_KEY] = settings.xaiApiKey
            preferences[PreferencesKeys.NVIDIA_API_KEY] = settings.nvidiaApiKey
            preferences[PreferencesKeys.GEMINI_API_KEY] = settings.geminiApiKey
            preferences[PreferencesKeys.OPENROUTER_API_KEY] = settings.openrouterApiKey
            preferences[PreferencesKeys.GROQ_API_KEY] = settings.groqApiKey
            preferences[PreferencesKeys.CUSTOM_API_KEY] = settings.customApiKey
            preferences[PreferencesKeys.LOCAL_BASE_URL] = settings.localBaseUrl
            preferences[PreferencesKeys.MODEL_NAME] = settings.modelName
            preferences[PreferencesKeys.ENABLE_HAPTICS] = settings.enableHaptics
            preferences[PreferencesKeys.ENABLE_DYNAMIC_COLOR] = settings.enableDynamicColor
            preferences[PreferencesKeys.SEDNIUM_SETTINGS_V1] = true
            settings.nativeModelUriString?.let {
                preferences[PreferencesKeys.NATIVE_MODEL_URI] = it
            }
        }
    }

    suspend fun saveChatHistory(history: List<Message>) {
        context.dataStore.edit { preferences ->
            val historyStr = messageListAdapter.toJson(history)
            preferences[PreferencesKeys.CHAT_HISTORY] = historyStr
        }
    }
}
