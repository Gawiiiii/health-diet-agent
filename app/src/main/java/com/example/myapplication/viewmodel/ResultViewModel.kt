package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AnalysisEntity
import com.example.myapplication.data.model.AnalyzeResponse
import com.example.myapplication.data.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultUiState(
    val isLoading: Boolean = true,
    val entity: AnalysisEntity? = null,
    val response: AnalyzeResponse? = null,
    val errorMessage: String? = null
)

class ResultViewModel(
    private val analysisRepository: AnalysisRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private var lastId: Long? = null

    fun load(id: Long) {
        if (lastId == id && !uiState.value.isLoading) return
        lastId = id
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val entity = analysisRepository.getHistory(id)
                val response = entity?.analysisJson?.let { analysisRepository.parseResponse(it) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entity = entity,
                        response = response,
                        errorMessage = if (entity == null) "History not found." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load result: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }
}
