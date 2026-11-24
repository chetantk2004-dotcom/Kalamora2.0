package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kalamoraPrefsDataStore by preferencesDataStore(name = "kalamora_user_prefs")

class UserPreferencesStore(private val context: Context) {
    private val darkSepiaThemeKey = booleanPreferencesKey("dark_sepia_theme")

    val isDarkSepiaTheme: Flow<Boolean> = context.applicationContext.kalamoraPrefsDataStore.data
        .map { prefs -> prefs[darkSepiaThemeKey] ?: false }

    suspend fun setDarkSepiaTheme(enabled: Boolean) {
        context.applicationContext.kalamoraPrefsDataStore.edit { prefs ->
            prefs[darkSepiaThemeKey] = enabled
        }
    }
}
