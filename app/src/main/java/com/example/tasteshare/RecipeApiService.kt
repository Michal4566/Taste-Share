package com.example.tasteshare

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface RecipeApiService {
    @GET("?type=public")
    fun searchRecipes(
        @Query("q") query: String,
        @Query("app_id") appId: String,
        @Query("app_key") apiKey: String,
        @Query("diet") diet: List<String>?,        // Lista diet
        @Query("health") health: List<String>?,    // Lista filtrów zdrowotnych
        @Query("cuisineType") cuisineType: List<String>?, // Typ kuchni
        @Query("mealType") mealType: List<String>?,       // Typ posiłku
        @Query("dishType") dishType: List<String>?,       // Typ dania
        @Query("time") maxTime: String?            // Czas przygotowania
    ): Call<RecipeResponse>
}

