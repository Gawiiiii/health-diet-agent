package com.example.myapplication.data.network

import com.example.myapplication.data.model.AnalyzeRequest
import com.example.myapplication.data.model.AnalyzeResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AnalyzeApi {
    @POST("analyze")
    suspend fun analyze(@Body request: AnalyzeRequest): AnalyzeResponse
}
