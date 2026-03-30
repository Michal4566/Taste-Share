package com.example.tasteshare
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MainPageRecipe(
    val recipe: Recipe
) : Parcelable
@Parcelize
data class MainPageRecipeSection(
    val title: String,
    val recipes: List<MainPageRecipe>
) : Parcelable
