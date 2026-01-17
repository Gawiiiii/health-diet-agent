package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AnalysisRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryItemUi(
    val id: Long,
    val createdAt: Long,
    val title: String,
    val riskLevel: String,
    val summary: String
)

class HistoryViewModel(
    analysisRepository: AnalysisRepository
) : ViewModel() {
    val items: StateFlow<List<HistoryItemUi>> = analysisRepository.historyFlow()
        .map { entities ->
            entities.map { entity ->
                val response = analysisRepository.parseResponse(entity.analysisJson)
                val title = response?.menuItems?.firstOrNull()?.name ?: "Menu Analysis"
                val riskLevel = response?.riskLevel ?: "UNKNOWN"
                val summary = response?.suggestions?.firstOrNull() ?: "No suggestions"
                HistoryItemUi(
                    id = entity.id,
                    createdAt = entity.createdAt,
                    title = title,
                    riskLevel = riskLevel,
                    summary = summary
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
