package com.example.autoclicker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.autoclicker.model.ClickConfig
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "clicker_config")

/** 用 DataStore 持久化配置（Gson 序列化为 JSON 字符串） */
class ConfigRepository(private val context: Context) {

    private val gson = Gson()
    private val configKey = stringPreferencesKey("config_json")
    private val profilesKey = stringPreferencesKey("profiles_json")

    val configFlow: Flow<ClickConfig> = context.configDataStore.data.map { prefs ->
        val json = prefs[configKey]
        if (json.isNullOrBlank()) ClickConfig()
        else runCatching { gson.fromJson(json, ClickConfig::class.java) }.getOrDefault(ClickConfig())
    }

    suspend fun saveConfig(config: ClickConfig) {
        context.configDataStore.edit { prefs ->
            prefs[configKey] = gson.toJson(config)
        }
    }

    /** 方案列表：name -> JSON */
    val profilesFlow: Flow<Map<String, ClickConfig>> = context.configDataStore.data.map { prefs ->
        val json = prefs[profilesKey]
        if (json.isNullOrBlank()) emptyMap()
        else runCatching {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, ClickConfig>>() {}.type
            gson.fromJson<Map<String, ClickConfig>>(json, type)
        }.getOrDefault(emptyMap())
    }

    suspend fun saveProfile(config: ClickConfig) {
        context.configDataStore.edit { prefs ->
            val json = prefs[profilesKey]
            val map = if (json.isNullOrBlank()) mutableMapOf()
            else runCatching {
                val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, ClickConfig>>() {}.type
                gson.fromJson<MutableMap<String, ClickConfig>>(json, type)
            }.getOrDefault(mutableMapOf())
            map[config.name] = config
            prefs[profilesKey] = gson.toJson(map)
        }
    }

    suspend fun deleteProfile(name: String) {
        context.configDataStore.edit { prefs ->
            val json = prefs[profilesKey]
            if (!json.isNullOrBlank()) {
                val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, ClickConfig>>() {}.type
                val map = runCatching {
                    gson.fromJson<MutableMap<String, ClickConfig>>(json, type)
                }.getOrDefault(mutableMapOf())
                map.remove(name)
                prefs[profilesKey] = gson.toJson(map)
            }
        }
    }
}
