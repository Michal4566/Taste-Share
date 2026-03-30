package com.example.tasteshare

import android.net.Uri
import android.os.Bundle
import com.bumptech.glide.Glide
import com.google.android.material.textview.MaterialTextView

class RecipeGeneratedActivity :  BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_generated)

        setupBottomNavigationMenu(R.id.action_search)

        val recipeTextView = findViewById<MaterialTextView>(R.id.recipe_text_view)

        val recipeText = intent.getStringExtra("RECIPE_TEXT")
        recipeTextView.text = recipeText ?: "Recipe not available"

        val imageUriString = intent.getStringExtra("RECIPE_IMAGE_URI")
        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            Glide.with(this).load(imageUri).into(findViewById(R.id.recipe_image_view))
        }
    }
}


