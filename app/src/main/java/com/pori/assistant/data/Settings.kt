package com.pori.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "pori_settings")

class Settings(private val context: Context) {
    private val kApiKey = stringPreferencesKey("api_key")
    private val kModel = stringPreferencesKey("model")
    private val kLang = stringPreferencesKey("language")

    val apiKey: Flow<String> = context.store.data.map { it[kApiKey] ?: "" }
    val language: Flow<String> = context.store.data.map { it[kLang] ?: Languages.AUTO }

    suspend fun apiKeyNow() = apiKey.first()
    suspend fun languageNow() = language.first()
    suspend fun modelNow() = context.store.data.first()[kModel] ?: DEFAULT_MODEL

    suspend fun setApiKey(v: String) = context.store.edit { it[kApiKey] = v.trim() }
    suspend fun setLanguage(v: String) = context.store.edit { it[kLang] = v }
    suspend fun setModel(v: String) = context.store.edit { it[kModel] = v }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
