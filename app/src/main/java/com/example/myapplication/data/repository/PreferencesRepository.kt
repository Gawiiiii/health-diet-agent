package com.example.myapplication.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class PreferencesRepository(private val context: Context) {
    private val allergiesKey = stringPreferencesKey("allergies")
    private val dislikesKey = stringPreferencesKey("dislikes")
    private val goalsKey = stringPreferencesKey("health_goals")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            allergies = parseList(prefs[allergiesKey]),
            dislikes = parseList(prefs[dislikesKey]),
            healthGoals = parseList(prefs[goalsKey])
        )
    }

    suspend fun updatePreferences(preferences: UserPreferences) {
        context.dataStore.edit { prefs ->
            prefs[allergiesKey] = serializeList(preferences.allergies)
            prefs[dislikesKey] = serializeList(preferences.dislikes)
            prefs[goalsKey] = serializeList(preferences.healthGoals)
        }
    }

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun serializeList(values: List<String>): String {
        return values.joinToString(",") { it.trim() }.trim().trimEnd(',')
    }
}
