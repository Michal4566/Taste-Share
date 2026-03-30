package com.example.tasteshare

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.math.roundToInt

class RecipeDetailActivity :  BaseActivity() {

    private var alertDialog: AlertDialog? = null
    private var recipeWebView: WebView? = null

    companion object {
        private const val REQUEST_CODE_EDIT = 1001
    }

    private var isRated = false
    private var rate = 0

    private val comments = mutableListOf<Comment>()
    private lateinit var commentAdapter: CommentAdapter

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recipeId: String

    private var lastVisibleComment: DocumentSnapshot? = null // Przechowuje ostatni dokument z poprzedniego zapytania

    private val PAGE_SIZE = 2
    private var fetchedAllComments = false

    private lateinit var recipe: Recipe
    private var fetchedInstructions: String? = null

    private lateinit var fab: FloatingActionButton

    private var is_user_author_flag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        setupBottomNavigationMenu(R.id.action_search)

        commentAdapter = CommentAdapter(
            comments = comments,
            currentUserId = currentUserId,
            onEditClick = { comment ->
                val position = comments.indexOf(comment)
                if (position != -1) {
                    showEditCommentDialog(comment, position)
                }
            },
            onAvatarClick = { userId ->
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra("userId", userId)
                startActivity(intent)
            }
        )
        val recyclerView = findViewById<RecyclerView>(R.id.comments_recycler_view)
        recyclerView.adapter = commentAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Get the Recipe object from intent
        recipe = intent.getParcelableExtra("recipe") ?: return

        // Set the recipe ID
        recipeId = if (recipe.isFromFirebase) {
            recipe.id
        } else {
            // For API recipes, recipe.id is already a safe ID
            recipe.id
        }

        Log.e("RECIPE DETALI ACTIVITY", recipe.toString())

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

        if (recipe.url == null) {
            // Handle Firebase recipe
            findViewById<WebView>(R.id.recipe_webview).visibility = View.GONE
            findViewById<MaterialTextView>(R.id.recipe_instructions).visibility = View.GONE
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
            recipeUrlTextView.text = getString(R.string.recipe_link)
            //recipeUrlTextView.text = recipe.url
            recipeUrlTextView.setTextColor(getColor(R.color.accent))
            recipeUrlTextView.paintFlags = recipeUrlTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            recipeUrlTextView.setOnClickListener {
                recipe.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(recipe.url))
                    startActivity(intent)
                }
            }

            val recipeInstructionTextView = findViewById<MaterialTextView>(R.id.recipe_instructions)

            CoroutineScope(Dispatchers.Main).launch {
                val url = recipe.url
                val instructions = url?.let { fetchCookingInstructions(it) }
                if (!instructions.isNullOrEmpty()) {
                    // Wyświetl instrukcje z numerami kroków
                    val numberedInstructions = instructions.mapIndexed { index, instruction ->
                        "${index + 1}. $instruction"
                    }
                    val instructionsText = numberedInstructions.joinToString("\n")
                    recipeInstructionTextView.text = instructionsText
                    fetchedInstructions = instructionsText
                } else {
                    recipeInstructionTextView.text = "No instructions available."
                }
            }
        }

        fab = findViewById(R.id.edit_recipe_floating_button)

        val favouriteIcon = findViewById<ImageView>(R.id.add_to_favourite_button)

        val favoriteRecipeId = if (recipe.isFromFirebase) {
            recipe.id
        } else {
            recipe.url?.replace("/", "_") ?: recipe.id
        }

        if (currentUserId != null) {
            val favoriteRecipeDocRef = db.collection("users").document(currentUserId)
                .collection("favoriteRecipes").document(favoriteRecipeId)
            favoriteRecipeDocRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    favouriteIcon.setImageResource(R.drawable.ic_heart_full_with_border)
                    favouriteIcon.isClickable = false
                } else {
                    favouriteIcon.setImageResource(R.drawable.ic_heart_with_border)
                    favouriteIcon.setOnClickListener {
                        addRecipeToFavorites(favoriteRecipeId, favouriteIcon)
                    }
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(this, "Error checking favorites: ${exception.message}", Toast.LENGTH_SHORT).show()
            }


            if (currentUserId == recipe.userId) {
                fab.setOnClickListener {
                    val intent = Intent(this, EditRecipeActivity::class.java)
                    intent.putExtra("recipe", recipe)
                    startActivityForResult(intent, RecipeDetailActivity.REQUEST_CODE_EDIT)
                }

                val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

                bottomNavigation.post {
                    val navHeight = bottomNavigation.height

                    val marginInPixels = navHeight + dpToPx(16)
                    val layoutParams = fab.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.bottomMargin = marginInPixels
                    fab.layoutParams = layoutParams
                }
            }
            else {
                fab.visibility = View.GONE
            }
        } else {
            favouriteIcon.setOnClickListener {
                Toast.makeText(this, "Please log in to add favorites.", Toast.LENGTH_SHORT).show()
            }

            fab.visibility = View.GONE
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

        findViewById<TextView>(R.id.write_comment_placeholder).setOnClickListener {
            showCommentInputDialog()
        }

        findViewById<MaterialButton>(R.id.load_more_button).setOnClickListener {
            if (!fetchedAllComments) {
                loadOtherComments()
            } else {
                Toast.makeText(this, "No more comments to load", Toast.LENGTH_SHORT).show()
            }
        }

        val ratingStars = listOf(
            R.id.rate_star_1_button to 1,
            R.id.rate_star_2_button to 2,
            R.id.rate_star_3_button to 3,
            R.id.rate_star_4_button to 4,
            R.id.rate_star_5_button to 5
        )

        for ((starId, rating) in ratingStars) {
            findViewById<ImageView>(starId).setOnClickListener {
                setUserRating(rating)
            }
        }

        // Initialize Firestore data
        initializeFirestoreData()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_EDIT && resultCode == RESULT_OK) {
            val recipeDeleted = data?.getBooleanExtra("recipeDeleted", false) ?: false
            val recipeUpdated = data?.getBooleanExtra("recipeUpdated", false) ?: false

            if (recipeDeleted) {
                val resultIntent = Intent().apply {
                    putExtra("recipeDeleted", true)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else if (recipeUpdated) {
                reloadRecipeFromFirestore()
                val resultIntent = Intent().apply {
                    putExtra("recipeUpdated", true)
                }
                setResult(RESULT_OK, resultIntent)
            }
        }
    }

    private fun reloadRecipeFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.collection("recipes").document(recipeId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    recipe = document.toObject(Recipe::class.java) ?: return@addOnSuccessListener

                    findViewById<MaterialTextView>(R.id.recipe_title).text = recipe.label

                    findViewById<MaterialTextView>(R.id.ingredients_list).text =
                        recipe.ingredientLines.joinToString("\n") ?: "No ingredients available"

                    findViewById<MaterialTextView>(R.id.recipe_time).text =
                        recipe.totalTime?.takeIf { it > 0 }?.roundToInt()?.let { "Time: $it min" } ?: "Time: No data"

                    findViewById<MaterialTextView>(R.id.recipe_calories).text =
                        recipe.calories?.takeIf { it > 0 }?.roundToInt()?.let { "Calories: $it kcal" } ?: "Calories: No data"

                    val mealTypeList = recipe.mealType
                    val mealTypeText = if (!mealTypeList.isNullOrEmpty()) {
                        mealTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
                    } else {
                        "Meal Type: No data"
                    }
                    findViewById<MaterialTextView>(R.id.meal_type).text = mealTypeText

                    val cuisineTypeList = recipe.cuisineType
                    val cuisineTypeText = if (!cuisineTypeList.isNullOrEmpty()) {
                        cuisineTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
                    } else {
                        "Cuisine: No data"
                    }
                    findViewById<MaterialTextView>(R.id.cuisine_type).text = cuisineTypeText

                    val dishTypeList = recipe.dishType
                    val dishTypeText = if (!dishTypeList.isNullOrEmpty()) {
                        dishTypeList.joinToString(", ").replaceFirstChar { it.uppercase() }
                    } else {
                        "Dish Type: No data"
                    }
                    findViewById<MaterialTextView>(R.id.dish_type).text = dishTypeText

                    Glide.with(this).load(recipe.image).into(findViewById(R.id.recipe_image))

                    if (recipe.url == null) {
                        findViewById<MaterialTextView>(R.id.recipe_difficulty).apply {
                            visibility = View.VISIBLE
                            text =
                                "Difficulty: ${if (!recipe.difficulty.isNullOrEmpty()) recipe.difficulty else "No data"}"
                        }

                        findViewById<MaterialTextView>(R.id.recipe_instructions).apply {
                            visibility = View.VISIBLE
                            text = recipe.instructions ?: "No instructions available"
                        }
                    }

                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to reload recipe", Toast.LENGTH_SHORT).show()
            }
    }

    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun addRecipeToFavorites(favoriteRecipeId: String, favouriteIcon: ImageView) {
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to add favorites.", Toast.LENGTH_SHORT).show()
            return
        }

        val favoriteRecipeDocRef = db.collection("users").document(currentUserId)
            .collection("favoriteRecipes").document(favoriteRecipeId)

        if (!recipe.isFromFirebase && recipe.image.isNotEmpty()) {


            val validDifficulties = StringArrays.difficultyOptions.toSet()
            val validDietOptions = StringArrays.dietOptions.toSet()
            val validHealthOptions = StringArrays.healthOptions.toSet()
            val validCuisineOptions = StringArrays.cuisineOptions.toSet()
            val validMealTypeOptions = StringArrays.mealTypeOptions.toSet()
            val validDishTypeOptions = StringArrays.dishTypeOptions.toSet()

            // Normalizujemy listy
            val filteredDietLabels = normalizeAndMatch(recipe.dietLabels, validDietOptions)
            val filteredHealthLabels = normalizeAndMatch(recipe.healthLabels, validHealthOptions)
            val filteredCuisineType = normalizeAndMatch(recipe.cuisineType, validCuisineOptions)
            val filteredMealType = normalizeAndMatch(recipe.mealType, validMealTypeOptions)
            val filteredDishType = normalizeAndMatch(recipe.dishType, validDishTypeOptions)

            // Jeśli przepis pochodzi z API to najpierw upload obrazu
            uploadImageToFirebaseStorage(recipe.image) { uploadedImageUrl ->
                if (uploadedImageUrl == null) {
                    Toast.makeText(this, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                    return@uploadImageToFirebaseStorage
                }

                val favoriteRecipeData = mapOf(
                    "id" to recipe.id,
                    "userId" to recipe.userId,
                    "label" to recipe.label,
                    "image" to uploadedImageUrl,
                    "url" to recipe.url,
                    "ingredientLines" to recipe.ingredientLines,
                    "calories" to recipe.calories,
                    "totalTime" to recipe.totalTime,
                    "dietLabels" to filteredDietLabels,
                    "healthLabels" to filteredHealthLabels,
                    "cuisineType" to filteredCuisineType,
                    "mealType" to filteredMealType,
                    "dishType" to filteredDishType,
                    "totalNutrients" to recipe.totalNutrients,
                    "co2EmissionsClass" to recipe.co2EmissionsClass,
                    "instructions" to (fetchedInstructions ?: recipe.instructions),
                    "difficulty" to recipe.difficulty,
                    "isFromFirebase" to recipe.isFromFirebase,
                    "rating" to recipe.rating,
                    "ratingAmount" to recipe.ratingAmount
                )

                favoriteRecipeDocRef.set(favoriteRecipeData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Added to favorites!", Toast.LENGTH_SHORT).show()
                        favouriteIcon.setImageResource(R.drawable.ic_heart_full_with_border)
                        favouriteIcon.isClickable = false
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to add to favorites: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            // Jeśli przepis jest już z Firebase, obraz jest tam hostowany lokalnie
            val favoriteRecipeData = mapOf(
                "id" to recipe.id,
                "userId" to recipe.userId,
                "label" to recipe.label,
                "image" to recipe.image,
                "url" to recipe.url,
                "ingredientLines" to recipe.ingredientLines,
                "calories" to recipe.calories,
                "totalTime" to recipe.totalTime,
                "dietLabels" to recipe.dietLabels,
                "healthLabels" to recipe.healthLabels,
                "cuisineType" to recipe.cuisineType,
                "mealType" to recipe.mealType,
                "dishType" to recipe.dishType,
                "totalNutrients" to recipe.totalNutrients,
                "co2EmissionsClass" to recipe.co2EmissionsClass,
                "instructions" to recipe.instructions,
                "difficulty" to recipe.difficulty,
                "isFromFirebase" to recipe.isFromFirebase,
                "rating" to recipe.rating,
                "ratingAmount" to recipe.ratingAmount
            )

            favoriteRecipeDocRef.set(favoriteRecipeData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Added to favorites!", Toast.LENGTH_SHORT).show()
                    favouriteIcon.setImageResource(R.drawable.ic_heart_full_with_border)
                    favouriteIcon.isClickable = false
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to add to favorites: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun normalizeAndMatch(input: List<String>, validValues: Set<String>): List<String> {
        val normalized = mutableListOf<String>()
        for (value in input) {
            val parts = value
                .replace("/", ",")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (part in parts) {
                val matched = validValues.find { it.equals(part, ignoreCase = true) }
                if (matched != null) {
                    normalized.add(matched)
                }
            }
        }
        return normalized.distinct() // można usunąć duplikaty
    }

    // Funkcja do pobrania obrazu z URL i załadowania do Firebase Storage
    private fun uploadImageToFirebaseStorage(imageUrl: String, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = Glide.with(this@RecipeDetailActivity)
                    .asBitmap()
                    .load(imageUrl)
                    .submit()
                    .get()

                withContext(Dispatchers.Main) {
                    val storage = FirebaseStorage.getInstance()
                    val storageRef = storage.reference
                    val imagesRef = storageRef.child("recipes_images/api/$recipeId/recipeApiImage.jpg")

                    val baos = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                    val data = baos.toByteArray()

                    val uploadTask = imagesRef.putBytes(data)
                    uploadTask.addOnSuccessListener {
                        imagesRef.downloadUrl.addOnSuccessListener { uri ->
                            callback(uri.toString())
                        }.addOnFailureListener {
                            callback(null)
                        }
                    }.addOnFailureListener {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    private fun initializeFirestoreData() {
        val recipeDoc = db.collection("comments").document(recipeId)
        recipeDoc.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                // Create the document for the recipe if it doesn't exist
                recipeDoc.set(mapOf("rating" to 0.0, "ratingAmount" to 0))
                    .addOnSuccessListener {
                        loadCommentsAndRatings()
                    }
            } else {
                loadCommentsAndRatings()
            }
        }
    }

    private suspend fun fetchCookingInstructions(url: String): List<String>? {
        return withContext(Dispatchers.IO) {
            try {
                val document: Document = Jsoup.connect(url).get()
                when {
                    "davidlebovitz.com" in url -> {
                        // Selektor dla davidlebovitz.com
                        document.select(".wprm-recipe-instructions li .wprm-recipe-instruction-text").map { it.text() }
                    }
                    "cafefernando.com" in url -> {
                        // Selektor dla cafefernando.com
                        document.select("#recipe ol li").map { it.text() }
                    }
                    "seriouseats.com" in url -> {
                        // Pierwsza próba selektora
                        val steps = document.select(".comp.mntl-sc-block-group--LI").map { it.text() }

                        steps.ifEmpty {
                            // Jeśli nie znaleziono kroków, szukamy linku do przepisu
                            val recipeLink =
                                document.select("a.comp.mntl-card-list-items").firstOrNull()
                                    ?.attr("href")

                            if (recipeLink != null) {
                                // Pobieramy treść strony pod tym linkiem
                                val recipeDocument = Jsoup.connect(recipeLink).get()

                                // Szukamy kroków na nowej stronie
                                recipeDocument.select(".comp.mntl-sc-block-group--LI")
                                    .map { it.text() }
                            } else {
                                // Jeśli link również nie znaleziony, zwracamy pustą listę
                                emptyList()
                            }
                        }
                    }
                    "norecipes.com" in url -> {
                        // Selektor dla norecipes.com
                        document.select(".wprm-recipe-instructions li .wprm-recipe-instruction-text").map { it.text() }
                    }
                    "epicurious.com" in url -> {
                        // Selektor dla epicurious.com
                        document.select(".InstructionGroupWrapper-bqiIwp li p").map { it.text() }
                    }
                    "bonappetit.com" in url -> {
                        // Selektor dla bonappetit.com
                        document.select(".InstructionGroupWrapper-bqiIwp li p").map { it.text() }
                    }
                    "marthastewart.com" in url -> {
                        // Selektor dla marthastewart.com
                        document.select(".mntl-sc-block-group--OL li p").map { it.text() }
                    }
                    "saveur.com" in url -> {
                        // Selektor dla saveur.com z eliminacją duplikatów
                        document.select("div.MuiBox-root.css-zalpa5 ol li")
                            .map { it.text() }
                            .distinct()
                    }
                    "honestcooking.com" in url -> {
                        // Selektor dla honestcooking.com
                        document.select("div.tasty-recipes-instructions ol li").map { it.text() }
                    }
                    "food52.com" in url -> {
                        // Selektor dla food52.com
                        document.select("div#recipeDirectionsRoot ol li span").map { it.text() }
                    }
                    "bbcgoodfood.com" in url -> {
                        // Selektor dla bbcgoodfood.com
                        document.select("div.js-piano-recipe-method ul.method-steps__list li p").map { it.text() }
                    }
                    "101cookbooks.com" in url -> {
                        val instructions = document.select("div.cb101-recipe-instructions-container ol.cb101-recipe-instructions li.cb101-recipe-instruction div.cb101-recipe-instruction-text").map { it.text() }
                        instructions.ifEmpty {
                            // Wcześniejszy selektor dla 101cookbooks z pominięciem blockquote
                            document.select("div#recipe > p:not(blockquote p)").map { it.text() }
                        }
                    }
                    "delish.com" in url -> {
                        // Selektor dla Delish.com
                        document.select("div.css-1ihj444 ul.directions li ol li").map { it.text() }
                    }
                    "latimes.com" in url -> {
                        // Selektor dla LA Times Recipes
                        document.select("div.recipe-steps div.recipe-step-container div.recipe-step-body p").map { it.text() }
                    }
                    "eatingwell.com" in url -> {
                        // Selektor dla EatingWell
                        document.select("div#mm-recipes-steps__content_1-0 ol li p").map { it.text() }
                    }
                    "pinchofyum.com" in url -> {
                        // Selektor dla Pinch of Yum
                        document.select("div.tasty-recipes-instructions ol li").map { it.text() }
                    }
                    "thekitchn.com" in url -> {
                        // Selektor dla The Kitchn
                        document.select("section.Recipe__instructionsSection ol.Recipe__instructions li.Recipe__instructionStep div.Recipe__instructionStepContent span p")
                            .map { it.text() }
                    }
                    "thedailymeal.com" in url -> {
                        // Selektor dla The Daily Meal
                        document.select("div.recipe-card ol.recipe-directions li").map { it.text() }
                    }

                    else -> {
                        println("Nieobsługiwana strona: $url")
                        null
                    }
                }
            } catch (e: IOException) {
                println("Błąd podczas pobierania strony: ${e.message}")
                null
            }
        }
    }

    private fun loadCommentsAndRatings() {
        val recipeDoc = db.collection("comments").document(recipeId)

        // Pobierz oceny
        recipeDoc.get().addOnSuccessListener { document ->
            val rating = document.getDouble("rating") ?: 0.0
            val ratingAmount = document.getLong("ratingAmount")?.toInt() ?: 0
            setReviewCountText(ratingAmount)
            setStarRating(rating.toFloat())
        }

        // Pobierz ocenę użytkownika
        if (currentUserId != null) {
            val userRatingDoc = recipeDoc.collection("ratings").document(currentUserId)
            userRatingDoc.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val userRating = document.getLong("rating")?.toInt() ?: 0
                    rate = userRating
                    isRated = true
                    // Ustaw gwiazdki użytkownika
                    val ratingStars = listOf(
                        R.id.rate_star_1_button,
                        R.id.rate_star_2_button,
                        R.id.rate_star_3_button,
                        R.id.rate_star_4_button,
                        R.id.rate_star_5_button
                    )

                    for (i in ratingStars.indices) {
                        val starView = findViewById<ImageView>(ratingStars[i])
                        if (i < userRating) {
                            starView.setImageResource(R.drawable.ic_star_full)
                        } else {
                            starView.setImageResource(R.drawable.ic_star_empty)
                        }
                    }
                }
            }
        }
        // Pobierz komentarz użytkownika
        if (currentUserId != null) {
            recipeDoc.collection("userComments").document(currentUserId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val userComment = doc.toObject(Comment::class.java)
                        if (userComment != null) {
                            comments.removeAll { it.userId == currentUserId }
                            comments.add(0, userComment)
                            commentAdapter.notifyDataSetChanged()
                            findViewById<TextView>(R.id.write_comment_placeholder).visibility = View.GONE
                        }
                    } else {
                        findViewById<TextView>(R.id.write_comment_placeholder).visibility = View.VISIBLE
                    }
                    // Wczytaj pozostałe komentarze z paginacją
                    loadOtherComments()
                }
                .addOnFailureListener {
                    findViewById<TextView>(R.id.write_comment_placeholder).visibility = View.VISIBLE
                    loadOtherComments()
                }
        } else {
            findViewById<TextView>(R.id.write_comment_placeholder).visibility = View.VISIBLE
            loadOtherComments()
        }
    }

    private fun loadOtherComments() {
        val recipeDoc = db.collection("comments").document(recipeId)

        // Create query with pagination, without the whereNotEqualTo filter
        var query: Query = recipeDoc.collection("userComments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE.toLong())

        // Add pagination cursor if exists
        lastVisibleComment?.let {
            query = query.startAfter(it)
        }

        query.get().addOnSuccessListener { querySnapshot ->
            if (querySnapshot.isEmpty) {
                fetchedAllComments = true
                // Hide the load more button
                findViewById<MaterialButton>(R.id.load_more_button).visibility = View.GONE
            } else {
                lastVisibleComment = querySnapshot.documents.lastOrNull()
                val fetchedComments = querySnapshot.documents.mapNotNull { doc ->
                    val comment = doc.toObject(Comment::class.java)
                    comment?.apply {
                        userId = doc.getString("userId") ?: ""
                    }
                }.filter { it.userId != currentUserId } // Filter out current user's comment

                // Add new comments to the list
                comments.addAll(fetchedComments)
                commentAdapter.notifyDataSetChanged()

                // Hide the load more button if all comments are loaded
                if (querySnapshot.size() < PAGE_SIZE) {
                    fetchedAllComments = true
                    findViewById<MaterialButton>(R.id.load_more_button).visibility = View.GONE
                }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to load comments: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                Toast.makeText(this@RecipeDetailActivity, "Recipe URL is not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Funkcja wyświetlająca dialog z tagami dietetycznymi i wartościami odżywczymi
    private fun showTagsDialog(diet: String?, health: String?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tags, null)

        val dietTags = dialogView.findViewById<TextView>(R.id.diet_tags)
        val healthTags = dialogView.findViewById<TextView>(R.id.health_tags)
        val nutritionLabel = dialogView.findViewById<TextView>(R.id.nutrition_info)
        val nutritionDetails = dialogView.findViewById<TextView>(R.id.nutrition_details)
        val co2Emissions = dialogView.findViewById<TextView>(R.id.co2_emissions)
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


    private fun setReviewCountText(reviewCount: Int) {
        val reviewText = when (reviewCount) {
            0 -> "0 ${getString(R.string.review_plural)}"
            1 -> "1 ${getString(R.string.review_singular)}"
            else -> "$reviewCount ${getString(R.string.review_plural)}"
        }
        findViewById<TextView>(R.id.star_representation_text).text = reviewText
    }

    private fun setStarRating(rating: Float) {
        val roundedRating = (rating * 2).roundToInt() / 2.0f
        val stars = listOf(
            R.id.star_1,
            R.id.star_2,
            R.id.star_3,
            R.id.star_4,
            R.id.star_5
        )

        for (i in stars.indices) {
            val starView = findViewById<ImageView>(stars[i])
            when {
                i + 1 <= roundedRating -> starView.setImageResource(R.drawable.ic_star_full)
                i + 0.5 == roundedRating.toDouble() -> starView.setImageResource(R.drawable.ic_star_half)
                else -> starView.setImageResource(R.drawable.ic_star_empty)
            }
        }
    }

    private fun setUserRating(userRating: Int) {
        if (rate == userRating) return

        val ratingStars = listOf(
            R.id.rate_star_1_button,
            R.id.rate_star_2_button,
            R.id.rate_star_3_button,
            R.id.rate_star_4_button,
            R.id.rate_star_5_button
        )

        for (i in ratingStars.indices) {
            val starView = findViewById<ImageView>(ratingStars[i])
            if (i < userRating) {
                starView.setImageResource(R.drawable.ic_star_full)
            } else {
                starView.setImageResource(R.drawable.ic_star_empty)
            }
        }

        val starText = if (userRating == 1) getString(R.string.star_singular) else getString(R.string.star_plural)

        if (!isRated) {
            Toast.makeText(this, "${getString(R.string.rate_recipe_toast)} $userRating $starText", Toast.LENGTH_SHORT).show()
            isRated = true
        } else {
            Toast.makeText(this, "${getString(R.string.rerate_recipe_toast)} $userRating $starText", Toast.LENGTH_SHORT).show()
        }

        rate = userRating

        // Save rating to Firestore
        addRatingToFirestore(userRating)
    }

    private fun addRatingToFirestore(userRating: Int) {
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to rate.", Toast.LENGTH_SHORT).show()
            return
        }

        val recipeDoc = db.collection("comments").document(recipeId)

        recipeDoc.collection("ratings").document(currentUserId)
            .set(mapOf("rating" to userRating))
            .addOnSuccessListener {
                // Przelicz średnią ocenę
                recipeDoc.collection("ratings").get().addOnSuccessListener { querySnapshot ->
                    val ratings = querySnapshot.documents.mapNotNull { it.getLong("rating")?.toInt() }
                    val newRating = ratings.average()
                    val ratingAmount = ratings.size

                    recipeDoc.update(mapOf("rating" to newRating, "ratingAmount" to ratingAmount))
                        .addOnSuccessListener {
                            setReviewCountText(ratingAmount)
                            setStarRating(newRating.toFloat())
                        }
                }
            }
    }

    private fun showCommentInputDialog() {
        // Inflate dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_single_text_input, null)
        val inputField = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.input_text_field)

        // Build the dialog
        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setTitle(getString(R.string.dialog_comment_title))
            .setPositiveButton(getString(R.string.submit)) { _, _ ->
                val commentText = inputField.text?.toString()?.trim()
                if (!commentText.isNullOrEmpty()) {
                    // Fetch user data and add the comment
                    fetchUserProfile { username, avatarUrl ->
                        addCommentToFirestore(username, commentText, avatarUrl)
                    }
                } else {
                    Toast.makeText(this, getString(R.string.comment_empty_error), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_button_negative), null)
            .create()

        // Show the dialog and focus the input field
        dialog.setOnShowListener {
            inputField.requestFocus()
            inputField.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        dialog.setOnDismissListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(inputField.windowToken, 0)
        }

        dialog.show()
    }

    private fun fetchUserProfile(onUserFetched: (username: String, avatarUrl: String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val username = document.getString("username") ?: "Unknown User"
                        val avatarUrl = document.getString("avatarUrl")
                        onUserFetched(username, avatarUrl)
                    } else {
                        Toast.makeText(this, "Failed to fetch user profile.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error fetching user data.", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCommentToFirestore(username: String, commentText: String, avatarUrl: String?) {
        if (currentUserId == null) {
            Toast.makeText(this, "Please log in to comment.", Toast.LENGTH_SHORT).show()
            return
        }

        val newComment = Comment(
            userId = currentUserId,
            username = username,
            text = commentText,
            imageUrl = avatarUrl,
            timestamp = Timestamp.now()
        )

        val recipeDoc = db.collection("comments").document(recipeId)

        recipeDoc.collection("userComments").document(currentUserId)
            .set(newComment)
            .addOnSuccessListener {
                // Dodaj lub zaktualizuj komentarz lokalnie na początku listy
                val existingIndex = comments.indexOfFirst { it.userId == currentUserId }
                if (existingIndex != -1) {
                    comments[existingIndex] = newComment
                    commentAdapter.notifyItemChanged(existingIndex)
                } else {
                    comments.add(0, newComment)
                    commentAdapter.notifyItemInserted(0)
                    findViewById<TextView>(R.id.write_comment_placeholder).visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add comment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditCommentDialog(comment: Comment, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_single_text_input, null)
        val inputField = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.input_text_field)
        inputField.setText(comment.text)
        inputField.hint = getString(R.string.dialog_comment_edit_hint)

        MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogThemeWithRedNegativeButton)
            .setTitle(getString(R.string.dialog_comment_edit_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_save)) { dialog, _ ->
                val newCommentText = inputField.text.toString().trim()
                if (newCommentText.isNotEmpty()) {
                    // Update comment in Firestore
                    comment.text = newCommentText
                    val recipeDoc = db.collection("comments").document(recipeId)
                    recipeDoc.collection("userComments").document(comment.userId)
                        .set(comment)
                        .addOnSuccessListener {
                            // Update comment locally
                            comments[position] = comment
                            commentAdapter.notifyItemChanged(position)
                            dialog.dismiss()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update comment: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    inputField.error = getString(R.string.comment_empty_error)
                }
            }
            .setNegativeButton(getString(R.string.dialog_button_delete)) { dialog, _ ->
                // Remove comment from Firestore
                val recipeDoc = db.collection("comments").document(recipeId)
                recipeDoc.collection("userComments").document(comment.userId)
                    .delete()
                    .addOnSuccessListener {
                        comments.removeAt(position)
                        commentAdapter.notifyItemRemoved(position)
                        if (comments.isEmpty() || comments.none { it.userId == currentUserId }) {
                            findViewById<TextView>(R.id.write_comment_placeholder).visibility = View.VISIBLE
                        }
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete comment: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNeutralButton(getString(R.string.dialog_button_negative), null)
            .show()
    }

    override fun onDestroy() {
        alertDialog?.dismiss()
        alertDialog = null
        recipeWebView?.destroy()
        super.onDestroy()
    }
}
