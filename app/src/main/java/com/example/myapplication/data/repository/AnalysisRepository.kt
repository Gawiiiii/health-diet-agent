package com.example.myapplication.data.repository

import com.example.myapplication.data.local.AnalysisDao
import com.example.myapplication.data.local.AnalysisEntity
import com.example.myapplication.data.model.AnalyzeRequest
import com.example.myapplication.data.model.AnalyzeResponse
import com.example.myapplication.data.model.UserPreferences
import com.example.myapplication.data.network.AnalyzeApi
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(
    private val api: AnalyzeApi,
    private val dao: AnalysisDao,
    moshi: Moshi
) {
    private val adapter = moshi.adapter(AnalyzeResponse::class.java)
    private val preferencesAdapter = moshi.adapter(UserPreferences::class.java)

    suspend fun analyzeText(text: String, preferences: UserPreferences): AnalyzeResponse {
        return api.analyze(AnalyzeRequest(text = text, preferences = preferences))
    }

    suspend fun analyzeImage(
        imagePart: MultipartBody.Part,
        preferences: UserPreferences
    ): AnalyzeResponse {
        val json = preferencesAdapter.toJson(preferences)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        return api.analyzeImage(imagePart, body)
    }

    suspend fun saveHistory(imageUri: String?, ocrText: String, response: AnalyzeResponse): Long {
        val entity = AnalysisEntity(
            imageUri = imageUri,
            ocrText = ocrText,
            analysisJson = adapter.toJson(response),
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    fun historyFlow(): Flow<List<AnalysisEntity>> = dao.observeAll()

    suspend fun getHistory(id: Long): AnalysisEntity? = dao.getById(id)

    fun parseResponse(json: String): AnalyzeResponse? = adapter.fromJson(json)
}
