package com.example.myapplication.data.network

import com.example.myapplication.data.model.AnalyzeRequest
import com.example.myapplication.data.model.AnalyzeResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AnalyzeApi {
    @POST("analyze")
    suspend fun analyze(@Body request: AnalyzeRequest): AnalyzeResponse

    @Multipart
    @POST("analyze-image")
    suspend fun analyzeImage(
        @Part image: MultipartBody.Part,
        @Part("preferences") preferences: RequestBody
    ): AnalyzeResponse
}
