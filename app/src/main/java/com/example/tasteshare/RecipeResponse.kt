package com.example.tasteshare

data class RecipeResponse(
    val hits: List<Hit>
)

data class Hit(
    val recipe: ApiRecipe
)
