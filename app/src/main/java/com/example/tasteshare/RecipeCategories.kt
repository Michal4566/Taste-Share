package com.example.tasteshare

import com.example.tasteshare.StringArrays.Companion.cuisineOptions
import java.time.LocalTime

val randomCuisine = cuisineOptions.random()

val baseCategories = listOf(
    RecipeCategory(
        title = "Quick Recipes",
        maxTime = 15
    ),
    RecipeCategory(
        title = "Highly Rated",
        minRatings = 0,
        minRatingScore = 4.5
    ),
    RecipeCategory(
        title = "World Cuisine - $randomCuisine",
        cuisineType = randomCuisine
    )
)

val timeBasedCategories = listOf(
    RecipeCategory(
        title = "French Breakfast",
        cuisineType = "French",
        mealType = "Breakfast",
        timeRange = Pair(LocalTime.of(6, 0), LocalTime.of(10, 0))
    ),
    RecipeCategory(
        title = "Afternoon Snacks",
        mealType = "Snack",
        timeRange = Pair(LocalTime.of(14, 0), LocalTime.of(17, 0))
    ),
    RecipeCategory(
        title = "Late Night Meals",
        mealType = "Dinner",
        timeRange = Pair(LocalTime.of(22, 0), LocalTime.of(2, 0))
    )
)

val randomRecipeCategories = listOf(
    RecipeCategory(
        title = "Tofu Recipes",
        ingredient = "tofu"
    ),
    RecipeCategory(
        title = "Balanced Dinners",
        diet = "balanced",
        mealType = "Dinner"
    ),
    RecipeCategory(
        title = "Vegan Desserts",
        dish = "Desserts",
        healthOptions = listOf("vegan")
    ),
    RecipeCategory(
        title = "Mexican Tacos",
        cuisineType = "Mexican",
        ingredient = "taco"
    ),
    RecipeCategory(
        title = "Gluten-Free Baking",
        healthOptions = listOf("gluten-free"),
        dish = "Desserts"
    )
)
