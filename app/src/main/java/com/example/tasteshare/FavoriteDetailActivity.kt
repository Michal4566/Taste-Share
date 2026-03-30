package com.example.tasteshare

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

class FavoriteDetailActivity : BaseActivity() {

    private var alertDialog: AlertDialog? = null
    private var recipeWebView: WebView? = null

    companion object {
        private const val REQUEST_CODE_EDIT = 1001
    }

    private val db = FirebaseFirestore.getInstance()

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var recipe: Recipe

    private lateinit var fab: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        setupBottomNavigationMenu(R.id.action_favorites)

        // Get the Recipe object from intent
        recipe = intent.getParcelableExtra("recipe") ?: return

        findViewById<View>(R.id.comments_section_layout).visibility = View.GONE

        // Set the recipe title
        findViewById<MaterialTextView>(R.id.recipe_title).text = recipe.label

        // Set the ingredients list
        findViewById<MaterialTextView>(R.id.ingredients_list).text =
            recipe.ingredientLines.joinToString("\n") ?: "No ingredients available"

        // Set the preparation time, if available
        findViewById<MaterialTextView>(R.id.recipe_time).text =
            recipe.totalTime?.takeIf { it > 0 }?.roundToInt()?.let { "Time: $it min" } ?: "Time: No data"

        // Set the calories, if available
        findViewById<MaterialTextView>(R.id.recipe_calories).text =
            recipe.calories?.takeIf { it > 0 }?.roundToInt()?.let { "Calories: $it kcal" } ?: "Calories: No data"

        // Set the meal type
        val mealTypeList = recipe.mealType
        val mealTypeText = if (!mealTypeList.isNullOrEmpty()) {
            mealTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "Meal Type: No data"
        }
        findViewById<MaterialTextView>(R.id.meal_type).text = mealTypeText

        // Set the cuisine type
        val cuisineTypeList = recipe.cuisineType
        val cuisineTypeText = if (!cuisineTypeList.isNullOrEmpty()) {
            cuisineTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "Cuisine: No data"
        }
        findViewById<MaterialTextView>(R.id.cuisine_type).text = cuisineTypeText

        // Set the dish type
        val dishTypeList = recipe.dishType
        val dishTypeText = if (!dishTypeList.isNullOrEmpty()) {
            dishTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "Dish Type: No data"
        }
        findViewById<MaterialTextView>(R.id.dish_type).text = dishTypeText

        // Set the recipe image using Glide
        Glide.with(this).load(recipe.image).into(findViewById(R.id.recipe_image))

        findViewById<MaterialTextView>(R.id.star_representation_text).visibility = View.GONE
        findViewById<LinearLayout>(R.id.rating_layout).visibility = View.GONE

        if (recipe.url == null) {
            // Handle Firebase recipe
            findViewById<WebView>(R.id.recipe_webview).visibility = View.GONE
            findViewById<MaterialTextView>(R.id.recipe_link).visibility = View.GONE

            // Display difficulty and instructions
            findViewById<MaterialTextView>(R.id.recipe_difficulty).apply {
                visibility = View.VISIBLE
                text = "Difficulty: ${if (!recipe.difficulty.isNullOrEmpty()) recipe.difficulty else "No data"}"
            }

            findViewById<MaterialTextView>(R.id.recipe_instructions).apply {
                visibility = View.VISIBLE
                text = recipe.instructions ?: "No instructions available"
            }

        } else {
            // Handle API recipe
            recipeWebView = findViewById(R.id.recipe_webview)
            recipeWebView?.let { webView ->
                webView.setOnTouchListener { _, _ ->
                    webView.parent.requestDisallowInterceptTouchEvent(true)
                    false
                }
            }
            setupWebView(recipe.url)

            // Set up the recipe instructions link
            val recipeUrlTextView = findViewById<MaterialTextView>(R.id.recipe_link)
            recipeUrlTextView.text = recipe.url
            recipeUrlTextView.setTextColor(getColor(R.color.accent))
            recipeUrlTextView.paintFlags = recipeUrlTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            recipeUrlTextView.setOnClickListener {
                recipe.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(recipe.url))
                    startActivity(intent)
                }
            }

            val recipeInstructionTextView = findViewById<MaterialTextView>(R.id.recipe_instructions)
            recipeInstructionTextView.text = recipe.instructions
        }

        fab = findViewById(R.id.edit_recipe_floating_button)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        bottomNavigation.post {
            val navHeight = bottomNavigation.height

            val marginInPixels = navHeight + dpToPx(16)
            val layoutParams = fab.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.bottomMargin = marginInPixels
            fab.layoutParams = layoutParams
        }

        findViewById<ImageView>(R.id.add_to_favourite_button).visibility = View.GONE

        fab.setOnClickListener {
            val intent = Intent(this, EditRecipeActivity::class.java)
            intent.putExtra("recipe", recipe)
            intent.putExtra("isFavorite", true)
            startActivityForResult(intent, REQUEST_CODE_EDIT)
        }

        // Set up the show_tags_button for both types of recipes
        findViewById<MaterialButton>(R.id.show_tags_button).setOnClickListener {
            showTagsDialog(
                recipe.dietLabels?.joinToString(", "),
                recipe.healthLabels?.joinToString(", ")
            )
        }

        // Set up "Add to Shopping List" button
        findViewById<MaterialButton>(R.id.add_to_shopping_list).setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                val db = FirebaseFirestore.getInstance()
                val shoppingListRef = db.collection("users").document(userId)
                    .collection("ShoppingList").document(recipe.label ?: "Unnamed Recipe")

                val ingredientsList = recipe.ingredientLines.map { ingredient ->
                    mapOf(
                        "item" to ingredient.trim(),
                        "isChecked" to false
                    )
                }

                val shoppingListData = mapOf(
                    "recipe_name" to recipe.label,
                    "items" to ingredientsList
                )

                shoppingListRef.set(shoppingListData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Added to shopping list!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to add to shopping list: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun setupWebView(url: String?) {
        recipeWebView?.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isVerticalScrollBarEnabled = true

            if (url != null) {
                loadUrl(url)
            } else {
                Toast.makeText(this@FavoriteDetailActivity, "Recipe URL is not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_EDIT && resultCode == RESULT_OK && data?.getBooleanExtra("recipeDeleted", false) == true) {
            finish()
        }
        else if (requestCode == REQUEST_CODE_EDIT && resultCode == RESULT_OK) {
            // Ponownie ładujemy przepis z Firestore
            reloadRecipeFromFirestore()
        }
    }

    private fun reloadRecipeFromFirestore() {
        if (currentUserId == null) return
        db.collection("users").document(currentUserId)
            .collection("favoriteRecipes").document(recipe.id)
            .get()
            .addOnSuccessListener { document ->
                val updatedRecipe = document.toObject(Recipe::class.java)
                if (updatedRecipe != null) {
                    recipe = updatedRecipe
                    updateUIWithRecipe(updatedRecipe)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reload updated recipe: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateUIWithRecipe(updatedRecipe: Recipe) {
        // Set the recipe title
        findViewById<MaterialTextView>(R.id.recipe_title).text = updatedRecipe.label

        // Set the ingredients list
        findViewById<MaterialTextView>(R.id.ingredients_list).text =
            updatedRecipe.ingredientLines.joinToString("\n").ifEmpty { "No ingredients available" }

        // Set the preparation time, if available
        val timeText = updatedRecipe.totalTime?.takeIf { it > 0 }?.roundToInt()?.let { "Time: $it min" } ?: "Time: No data"
        findViewById<MaterialTextView>(R.id.recipe_time).text = timeText

        // Set the calories, if available
        val caloriesText = updatedRecipe.calories?.takeIf { it > 0 }?.roundToInt()?.let { "Calories: $it kcal" } ?: "Calories: No data"
        findViewById<MaterialTextView>(R.id.recipe_calories).text = caloriesText

        // Set the meal type
        val mealTypeList = updatedRecipe.mealType
        val mealTypeText = if (mealTypeList.isNotEmpty()) {
            mealTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "Meal Type: No data"
        }
        findViewById<MaterialTextView>(R.id.meal_type).text = mealTypeText

        // Set the cuisine type
        val cuisineTypeList = updatedRecipe.cuisineType
        val cuisineTypeText = if (cuisineTypeList.isNotEmpty()) {
            cuisineTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "Cuisine: No data"
        }
        findViewById<MaterialTextView>(R.id.cuisine_type).text = cuisineTypeText

        // Set the dish type
        val dishTypeList = updatedRecipe.dishType
        val dishTypeText = if (dishTypeList.isNotEmpty()) {
            dishTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
        } else {
            "Dish Type: No data"
        }
        findViewById<MaterialTextView>(R.id.dish_type).text = dishTypeText

        // Set the recipe image using Glide
        Glide.with(this).load(updatedRecipe.image).into(findViewById(R.id.recipe_image))

        // Ukryj gwiazdki i oceny jeśli nie są używane
        findViewById<MaterialTextView>(R.id.star_representation_text).visibility = View.GONE
        findViewById<LinearLayout>(R.id.rating_layout).visibility = View.GONE

        val recipeDifficultyTextView = findViewById<MaterialTextView>(R.id.recipe_difficulty)
        val recipeInstructionsTextView = findViewById<MaterialTextView>(R.id.recipe_instructions)
        val recipeWebView = findViewById<WebView>(R.id.recipe_webview)
        val recipeLinkTextView = findViewById<MaterialTextView>(R.id.recipe_link)

        if (updatedRecipe.url == null) {
            // Handle Firebase recipe (no URL)
            recipeWebView.visibility = View.GONE
            recipeLinkTextView.visibility = View.GONE

            // Display difficulty and instructions
            recipeDifficultyTextView.visibility = View.VISIBLE
            recipeDifficultyTextView.text = "Difficulty: ${if (!updatedRecipe.difficulty.isNullOrEmpty()) updatedRecipe.difficulty else "No data"}"

            recipeInstructionsTextView.visibility = View.VISIBLE
            recipeInstructionsTextView.text = if (updatedRecipe.instructions.isNotEmpty()) updatedRecipe.instructions else "No instructions available"

        } else {
            // Handle API recipe
            recipeWebView.visibility = View.VISIBLE
            setupWebView(updatedRecipe.url)

            recipeLinkTextView.text = updatedRecipe.url
            recipeLinkTextView.setTextColor(getColor(R.color.accent))
            recipeLinkTextView.paintFlags = recipeLinkTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            recipeLinkTextView.visibility = View.VISIBLE
            recipeLinkTextView.setOnClickListener {
                updatedRecipe.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updatedRecipe.url))
                    startActivity(intent)
                }
            }

            recipeDifficultyTextView.visibility = View.GONE

            recipeInstructionsTextView.visibility = View.VISIBLE
            recipeInstructionsTextView.text = if (updatedRecipe.instructions.isNotEmpty()) updatedRecipe.instructions else "No instructions available"
        }
    }



    // Function to show the tags dialog
    private fun showTagsDialog(diet: String?, health: String?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tags, null)

        val dietTags = dialogView.findViewById<MaterialTextView>(R.id.diet_tags)
        val healthTags = dialogView.findViewById<MaterialTextView>(R.id.health_tags)
        val nutritionLabel = dialogView.findViewById<MaterialTextView>(R.id.nutrition_info)
        val nutritionDetails = dialogView.findViewById<MaterialTextView>(R.id.nutrition_details)
        val co2Emissions = dialogView.findViewById<MaterialTextView>(R.id.co2_emissions)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.close_button)

        val dietText = if (!diet.isNullOrEmpty()) diet else getString(R.string.no_data)
        dietTags.text = "${getString(R.string.dialog_message_recipe_details_diet_tags)} $dietText"

        val healthText = if (!health.isNullOrEmpty()) health else getString(R.string.no_data)
        healthTags.text = "${getString(R.string.dialog_message_recipe_details_health_tags)} $healthText"

        if (recipe.isFromFirebase) {
            nutritionLabel.visibility = View.GONE
            nutritionDetails.visibility = View.GONE
            co2Emissions.visibility = View.GONE
        } else {
            nutritionLabel.visibility = View.VISIBLE
            nutritionDetails.visibility = View.VISIBLE
            co2Emissions.visibility = View.VISIBLE

            val nutritionInfo = StringBuilder()

            val totalNutrients = recipe.totalNutrients
            if (!totalNutrients.isNullOrEmpty()) {
                totalNutrients.forEach { (_, nutrient) ->
                    if (nutrient.quantity.roundToInt() > 0) {
                        nutritionInfo.append("${nutrient.label}: ${nutrient.quantity.roundToInt()} ${nutrient.unit}\n")
                    }
                }
                if (nutritionInfo.isEmpty()) {
                    nutritionInfo.append(getString(R.string.no_data))
                }
            } else {
                nutritionInfo.append(getString(R.string.no_data))
            }
            nutritionDetails.text = nutritionInfo.toString()

            co2Emissions.text = "${getString(R.string.dialog_message_recipe_details_co2_emission)} ${recipe.co2EmissionsClass ?: getString(R.string.no_data)}"
        }

        val alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    override fun onDestroy() {
        alertDialog?.dismiss()
        alertDialog = null
        recipeWebView?.destroy()
        super.onDestroy()
    }
}
