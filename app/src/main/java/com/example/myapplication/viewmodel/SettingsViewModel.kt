package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.UserPreferences
import com.example.myapplication.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun savePreferences(allergiesText: String, dislikesText: String, goalsText: String) {
        val preferences = UserPreferences(
            allergies = parseList(allergiesText),
            dislikes = parseList(dislikesText),
            healthGoals = parseList(goalsText)
        )
        viewModelScope.launch {
            preferencesRepository.updatePreferences(preferences)
        }
    }

    private fun parseList(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
