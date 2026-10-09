package com.example.gabayai.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.gabayai.domain.GuideLanguage
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.preferencesStore by preferencesDataStore(name = "gabay-preferences")

/** Preferences only. No photograph, extracted text or answers enter DataStore. */
class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.preferencesStore
    private val safeData = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
    val language: Flow<GuideLanguage> = safeData.map { GuideLanguage.fromCode(it[LANGUAGE] ?: "fil") }
    val semanticEnabled: Flow<Boolean> = safeData.map { it[SEMANTIC] ?: false }
    suspend fun setLanguage(value: GuideLanguage) {
        if (value !in listOf(GuideLanguage.FILIPINO, GuideLanguage.ENGLISH)) return
        store.edit { it[LANGUAGE] = value.code }
    }
    suspend fun setSemanticEnabled(value: Boolean) { store.edit { it[SEMANTIC] = value } }
    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val SEMANTIC = booleanPreferencesKey("semantic_enabled")
    }
}
