package com.example.tasteshare

import androidx.lifecycle.ViewModel

class RecipeViewModel : ViewModel() {
    var recipes: List<Recipe> = emptyList()
    var currentSortPosition: Int = 0
}
