package com.example.myapplication.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.image.ImagePreprocessor
import com.example.myapplication.data.ocr.OcrProcessor
import com.example.myapplication.data.repository.AnalysisRepository
import com.example.myapplication.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.HttpException

data class CaptureUiState(
    val imageUri: Uri? = null,
    val ocrText: String = "",
    val isOcrRunning: Boolean = false,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null
)

sealed interface CaptureEvent {
    data class NavigateToResult(val id: Long) : CaptureEvent
}

class CaptureViewModel(
    application: Application,
    private val analysisRepository: AnalysisRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CaptureEvent>()
    val events = _events.asSharedFlow()

    fun updateOcrText(text: String) {
        _uiState.update { it.copy(ocrText = text, errorMessage = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun fillDemoText() {
        _uiState.update {
            it.copy(
                imageUri = null,
                ocrText = DEMO_TEXT,
                isOcrRunning = false,
                isAnalyzing = false,
                errorMessage = null
            )
        }
    }

    fun processImage(uri: Uri) {
        _uiState.update { it.copy(imageUri = uri, isOcrRunning = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val text = OcrProcessor.recognizeText(getApplication(), uri)
                _uiState.update { it.copy(ocrText = text, isOcrRunning = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isOcrRunning = false,
                        errorMessage = "OCR failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun analyzeCurrentText() {
        val text = uiState.value.ocrText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "OCR text is empty.") }
            return
        }
        _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val preferences = preferencesRepository.preferencesFlow.first()
                val response = analysisRepository.analyzeText(text, preferences)
                val historyId = analysisRepository.saveHistory(
                    imageUri = uiState.value.imageUri?.toString(),
                    ocrText = text,
                    response = response
                )
                _uiState.update { it.copy(isAnalyzing = false) }
                _events.emit(CaptureEvent.NavigateToResult(historyId))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = "Analyze failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun analyzeCurrentImage() {
        val uri = uiState.value.imageUri
        if (uri == null) {
            _uiState.update { it.copy(errorMessage = "No image selected.") }
            return
        }
        _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val preferences = preferencesRepository.preferencesFlow.first()
                val imagePart = withContext(Dispatchers.IO) { buildImagePart(uri) }
                val response = analysisRepository.analyzeImage(imagePart, preferences)
                val historyId = analysisRepository.saveHistory(
                    imageUri = uri.toString(),
                    ocrText = "[Image Analysis]",
                    response = response
                )
                _uiState.update { it.copy(isAnalyzing = false) }
                _events.emit(CaptureEvent.NavigateToResult(historyId))
            } catch (e: Exception) {
                val errorDetail = if (e is HttpException) {
                    val body = e.response()?.errorBody()?.string()
                    if (!body.isNullOrBlank()) {
                        body
                    } else {
                        e.message()
                    }
                } else {
                    e.message
                }
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = "Image analyze failed: ${errorDetail ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun buildImagePart(uri: Uri): MultipartBody.Part {
        val payload = ImagePreprocessor.prepareForUpload(
            context = getApplication(),
            uri = uri
        )
        val body = payload.bytes.toRequestBody(payload.mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", payload.fileName, body)
    }

    private companion object {
        private const val DEMO_TEXT = "Spicy Noodles: peanut, soy, chili\n" +
            "Chicken Salad: lettuce, chicken, milk dressing\n" +
            "Fruit Tea: sugar, honey, citrus"
    }
}
