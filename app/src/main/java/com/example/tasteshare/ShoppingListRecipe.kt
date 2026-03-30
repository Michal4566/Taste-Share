package com.example.tasteshare

data class ShoppingListRecipe(
    val id: String,
    val name: String,
    val ingredients: List<Pair<String, Boolean>> // Pair<ingredient name, isChecked>
)