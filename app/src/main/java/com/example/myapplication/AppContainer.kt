package com.example.myapplication

import android.content.Context
import androidx.room.Room
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.network.AnalyzeApi
import com.example.myapplication.data.repository.AnalysisRepository
import com.example.myapplication.data.repository.PreferencesRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(context.getString(R.string.api_base_url))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(okHttpClient)
        .build()

    private val api = retrofit.create(AnalyzeApi::class.java)
    private val database = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "menu_analyzer.db"
    ).build()

    val analysisRepository = AnalysisRepository(api, database.analysisDao(), moshi)
    val preferencesRepository = PreferencesRepository(context)
}
