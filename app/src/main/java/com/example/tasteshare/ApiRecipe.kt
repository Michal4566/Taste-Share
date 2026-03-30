package com.example.tasteshare

data class ApiRecipe(
    val label: String,
    val image: String,
    val url: String,
    val ingredientLines: List<String>,
    val calories: Double,
    val totalTime: Double,
    val dietLabels: List<String>?,
    val healthLabels: List<String>?,
    val cuisineType: List<String>?,
    val mealType: List<String>?,
    val dishType: List<String>?,
    val totalNutrients: Map<String, Nutrient>,
    val co2EmissionsClass: String?
)
