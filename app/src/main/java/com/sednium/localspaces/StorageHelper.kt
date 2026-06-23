package com.sednium.localspaces

import android.content.Context
import android.content.SharedPreferences
import com.sednium.localspaces.model.AppSettings
import com.sednium.localspaces.model.ChatSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sednium_storage", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadSettings(): AppSettings {
        val str = prefs.getString("settings_json", null)
        return if (str != null) {
            try {
                json.decodeFromString<AppSettings>(str)
            } catch (e: Exception) {
                AppSettings()
            }
        } else {
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        val str = json.encodeToString(settings)
        prefs.edit().putString("settings_json", str).apply()
    }

    fun loadChats(): List<ChatSession> {
        val str = prefs.getString("chats_json", null)
        return if (str != null) {
            try {
                json.decodeFromString<List<ChatSession>>(str)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun saveChats(chats: List<ChatSession>) = withContext(Dispatchers.IO) {
        val str = json.encodeToString(chats)
        prefs.edit().putString("chats_json", str).apply()
    }
}
