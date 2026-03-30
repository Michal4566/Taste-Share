package com.example.tasteshare

import java.time.LocalTime

data class RecipeCategory(
    val title: String,
    val maxTime: Int? = null,
    val minRatings: Int? = null,
    val minRatingScore: Double? = null,
    val diet: String? = null,
    val dish: String? = null,
    val cuisineType: String? = null,
    val mealType: String? = null,
    val ingredient: String? = null,
    val healthOptions: List<String>? = null,
    val timeRange: Pair<LocalTime, LocalTime>? = null
)
