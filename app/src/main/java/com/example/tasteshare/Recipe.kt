package com.example.tasteshare

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Nutrient(
    val label: String = "",
    val quantity: Double = 0.0,
    val unit: String = ""
) : Parcelable

@Parcelize
data class Recipe(
    val id: String = "",
    val userId: String = "",
    val label: String = "",
    val image: String = "",
    val url: String? = null,
    val ingredientLines: List<String> = emptyList(),
    val calories: Double? = null,
    val totalTime: Double? = null,
    val dietLabels: List<String> = emptyList(),
    val healthLabels: List<String> = emptyList(),
    val cuisineType: List<String> = emptyList(),
    val mealType: List<String> = emptyList(),
    val dishType: List<String> = emptyList(),
    val totalNutrients: Map<String, Nutrient>? = null,
    val co2EmissionsClass: String? = null,
    val instructions: String = "",
    val difficulty: String? = null,
    val isFromFirebase: Boolean = false,
    val rating: Float? = null,
    val ratingAmount: Int? = null
) : Parcelable
