package com.example.myapplication.data.model

import com.squareup.moshi.Json

data class UserPreferences(
    val allergies: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    @Json(name = "health_goals")
    val healthGoals: List<String> = emptyList()
)

data class AnalyzeRequest(
    val text: String,
    val preferences: UserPreferences
)

data class MenuItem(
    val name: String,
    val ingredients: List<String> = emptyList()
)

data class RiskHit(
    val term: String,
    val reason: String,
    val level: String
)

data class AnalyzeResponse(
    @Json(name = "menu_items")
    val menuItems: List<MenuItem> = emptyList(),
    @Json(name = "risk_level")
    val riskLevel: String = "LOW",
    val hits: List<RiskHit> = emptyList(),
    val suggestions: List<String> = emptyList()
)
