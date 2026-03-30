package com.example.tasteshare

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream
import java.time.LocalTime

class SearchActivity : BaseActivity() {

    private val selectedDiets = mutableSetOf<String>()
    private val selectedHealths = mutableSetOf<String>()
    private val selectedCuisines = mutableSetOf<String>()
    private val selectedMealTypes = mutableSetOf<String>()
    private val selectedDishTypes = mutableSetOf<String>()
    private var selectedTime: String? = null
    private var alertDialog: AlertDialog? = null

    private lateinit var ingredientInput: TextInputEditText
    private lateinit var filtersLayout: ScrollView
    private lateinit var filtersButton: MaterialButton
    private lateinit var searchTopLayout: LinearLayout

    private var imagePathH: Uri? = null

    private var isFiltersVisible = false

    private lateinit var recipeApiService: RecipeApiService

    companion object {
        const val PICK_IMAGE_REQUEST_CODE = 100
        const val GENERATE_RECIPE_REQUEST_CODE = 101

        private const val KEY_RECIPE_SECTIONS = "KEY_RECIPE_SECTIONS"
    }

    private var savedRecipeSections: List<MainPageRecipeSection>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("is_first_run", true)

        if (isFirstRun) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        } else {
            setContentView(R.layout.search_activity)

            setupBottomNavigationMenu(R.id.action_search)

            val dietFilter = findViewById<MaterialButton>(R.id.diet_filter)
            val healthFilter = findViewById<MaterialButton>(R.id.health_filter)
            val cuisineFilter = findViewById<MaterialButton>(R.id.cuisine_filter)
            val mealTypeFilter = findViewById<MaterialButton>(R.id.meal_type_filter)
            val dishTypeFilter = findViewById<MaterialButton>(R.id.dish_type_filter)
            val maxTimeFilter = findViewById<MaterialButton>(R.id.max_time_filter)
            val newRecipeButton = findViewById<MaterialButton>(R.id.add_new_recipe_button)
            val imageFunctionsButton = findViewById<MaterialButton>(R.id.image_options_button)

            ingredientInput = findViewById(R.id.ingredient_input)
            filtersLayout = findViewById(R.id.filers_layout)
            filtersButton = findViewById(R.id.filters_button)
            searchTopLayout = findViewById(R.id.search_top_layout)

            filtersButton.setOnClickListener {
                toggleFilters()
            }

            loadFiltersFromPreferences()

            dietFilter.text = formatSelectedText(selectedDiets)
            healthFilter.text = formatSelectedText(selectedHealths)
            cuisineFilter.text = formatSelectedText(selectedCuisines)
            mealTypeFilter.text = formatSelectedText(selectedMealTypes)
            dishTypeFilter.text = formatSelectedText(selectedDishTypes)
            maxTimeFilter.text = selectedTime?.let { "$it min" } ?: "ANY"

            // Obsługa wyboru wielu opcji dla filtrów
            dietFilter.setOnClickListener {
                showMultiChoiceDialog(
                    getString(R.string.dialog_title_choose_diet),
                    StringArrays.dietOptions,
                    selectedDiets
                ) {
                    dietFilter.text = formatSelectedText(selectedDiets)
                }
            }

            healthFilter.setOnClickListener {
                showMultiChoiceDialog(
                    getString(R.string.dialog_title_choose_health),
                    StringArrays.healthOptions,
                    selectedHealths
                ) {
                    healthFilter.text = formatSelectedText(selectedHealths)
                }
            }

            cuisineFilter.setOnClickListener {
                showMultiChoiceDialog(
                    getString(R.string.dialog_title_choose_cuisine),
                    StringArrays.cuisineOptions,
                    selectedCuisines
                ) {
                    cuisineFilter.text = formatSelectedText(selectedCuisines)
                }
            }

            mealTypeFilter.setOnClickListener {
                showMultiChoiceDialog(
                    getString(R.string.dialog_title_choose_meal),
                    StringArrays.mealTypeOptions,
                    selectedMealTypes
                ) {
                    mealTypeFilter.text = formatSelectedText(selectedMealTypes)
                }
            }

            dishTypeFilter.setOnClickListener {
                showMultiChoiceDialog(
                    getString(R.string.dialog_title_choose_dish),
                    StringArrays.dishTypeOptions,
                    selectedDishTypes
                ) {
                    dishTypeFilter.text = formatSelectedText(selectedDishTypes)
                }
            }

            maxTimeFilter.setOnClickListener {
                showSingleChoiceDialog(
                    getString(R.string.dialog_title_choose_time),
                    StringArrays.maxTimeOptions,
                    selectedTime
                ) { selected ->
                    selectedTime = selected
                    maxTimeFilter.text = selected?.let { "$it min" } ?: "ANY"
                }
            }

            // Obsługa przycisku dodawania receptury
            newRecipeButton.setOnClickListener {
                val intent = Intent(this, EditRecipeActivity::class.java)
                startActivity(intent)
            }

            imageFunctionsButton.setOnClickListener {
                showImageOptionsDialog()
            }

            ingredientInput.setSingleLine(true)
            ingredientInput.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE

            ingredientInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val ingredientQuery = ingredientInput.text.toString().trim()
                    val isFilterSelected =
                        selectedDiets.isNotEmpty() || selectedHealths.isNotEmpty() ||
                                selectedCuisines.isNotEmpty() || selectedMealTypes.isNotEmpty() ||
                                selectedDishTypes.isNotEmpty() || selectedTime != null

                    if (ingredientQuery.isEmpty() && !isFilterSelected) {
                        ingredientInput.error = getString(R.string.no_ingredients_or_filter)
                    } else {
                        showGenerateOrSearchDialog()
                    }
                    true // Zatrzymuje domyślne działanie
                } else {
                    false
                }
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.edamam.com/api/recipes/v2/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            recipeApiService = retrofit.create(RecipeApiService::class.java)

            savedInstanceState?.let {
                val savedList = it.getParcelableArrayList<MainPageRecipeSection>(KEY_RECIPE_SECTIONS)
                if (!savedList.isNullOrEmpty()) {
                    savedRecipeSections = savedList
                }
            }

            createRecipeSections()
        }
    }

    private fun toggleFilters() {
        if (isFiltersVisible) {
            val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
            filtersLayout.startAnimation(slideUp)
            filtersLayout.visibility = View.GONE
        } else {
            filtersLayout.visibility = View.VISIBLE
            val slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down)
            filtersLayout.startAnimation(slideDown)
        }
        isFiltersVisible = !isFiltersVisible
    }

    override fun onBackPressed() {
        if (isFiltersVisible) {
            val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
            filtersLayout.startAnimation(slideUp)
            filtersLayout.visibility = View.GONE
            isFiltersVisible = false
        } else {
            super.onBackPressed()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            imageUri?.let {
                imagePathH = imageUri
                when (requestCode) {
                    PICK_IMAGE_REQUEST_CODE -> processSelectedImage(it)
                    GENERATE_RECIPE_REQUEST_CODE -> generateRecipeFromImage(it)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveFiltersToPreferences()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val recipeSectionsRecyclerView = findViewById<RecyclerView>(R.id.recipe_sections_recycler_view)
        val adapter = recipeSectionsRecyclerView.adapter
        if (adapter is MainPageRecipeSectionAdapter) {
            val sections = adapter.getSections()
            if (sections.isNotEmpty()) {
                outState.putParcelableArrayList(KEY_RECIPE_SECTIONS, ArrayList(sections))
            }
        }
    }

    private fun createRecipeSections() {
        val recipeSectionsRecyclerView = findViewById<RecyclerView>(R.id.recipe_sections_recycler_view)
        recipeSectionsRecyclerView.layoutManager = LinearLayoutManager(this)

        val adapter: MainPageRecipeSectionAdapter
        if (savedRecipeSections != null) {
            adapter = MainPageRecipeSectionAdapter(savedRecipeSections!!)
            recipeSectionsRecyclerView.adapter = adapter
        } else {
            val selectedCategories = getDailyCategories()
            val recipeSections = mutableListOf<MainPageRecipeSection>()
            adapter = MainPageRecipeSectionAdapter(recipeSections)
            recipeSectionsRecyclerView.adapter = adapter

            selectedCategories.forEach { category ->
                fetchRecipesForCategory(category) { recipes ->
                    val section = MainPageRecipeSection(
                        title = category.title,
                        recipes = recipes
                    )
                    recipeSections.add(section)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }


    private fun getDailyCategories(): List<RecipeCategory> {
        val time = LocalTime.now()
        val matchedTimeCategory = timeBasedCategories.firstOrNull { cat ->
            val (start, end) = cat.timeRange!!
            if (start < end) {
                time.isAfter(start) && time.isBefore(end)
            } else {
                time.isAfter(start) || time.isBefore(end)
            }
        }

        return if (matchedTimeCategory != null) {
            baseCategories + matchedTimeCategory
        } else {
            val randomCategory = randomRecipeCategories.shuffled().take(1)
            baseCategories + randomCategory.first()
        }
    }

    private fun fetchRecipesFromFirebase(category: RecipeCategory, onComplete: (List<MainPageRecipe>) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        fun queryRecipesByConditions(recipeIds: List<String>? = null) {
            var query = db.collection("recipes") as Query

            // Jeśli kategoria wymaga maksymalnego czasu (np. Quick Recipes)
            category.maxTime?.let { maxT ->
                query = query.whereLessThanOrEqualTo("totalTime", maxT)
                query = query.whereGreaterThan("totalTime", 0)
            }

            // Jeśli mamy ID przepisów z komentarzy (dla Highly Rated)
            if (!recipeIds.isNullOrEmpty()) {
                // Uwaga: Firestore w whereIn przyjmuje max 10 elementów na raz, jeśli receptur jest więcej, trzeba to inaczej rozwiązać.
                query = query.whereIn("id", recipeIds)
            } else if (category.minRatingScore != null && category.minRatingScore > 0) {
                // Potrzebowaliśmy ratingu, ale nie mamy żadnych recipeIds spełniających warunek ratingu?
                // Zwroc pustą listę
                onComplete(emptyList())
                return
            }

            query.get()
                .addOnSuccessListener { result ->
                    val filteredRecipes = result.documents.mapNotNull { doc ->
                        val recipe = doc.toObject(Recipe::class.java)
                        recipe?.let { MainPageRecipe(recipe = it) }
                    }.filter { mainPageRecipe ->
                        val r = mainPageRecipe.recipe
                        // Dodatkowe filtry po składnikach, kuchni, typie posiłku itp.
                        (category.ingredient?.let { r.label.contains(it, ignoreCase = true) } ?: true) &&
                                (category.cuisineType?.let { r.cuisineType.any { ct -> ct.contains(it, ignoreCase = true) } } ?: true) &&
                                (category.mealType?.let { r.mealType.any { mt -> mt.contains(it, ignoreCase = true) } } ?: true) &&
                                (category.healthOptions?.all { healthOption ->
                                    r.healthLabels.any { hl -> hl.equals(healthOption, ignoreCase = true) }
                                } ?: true)
                    }

                    onComplete(filteredRecipes.shuffled())
                }
                .addOnFailureListener {
                    onComplete(emptyList())
                }
        }

        val needsRatingFilter = category.minRatingScore != null && category.minRatingScore > 0

        if (needsRatingFilter) {
            // Najpierw pobieramy ID przepisów z kolekcji comments, gdzie dokumenty mają rating >= minRatingScore
            // i recipeId nie zawiera "_"
            db.collection("comments")
                .get()
                .addOnSuccessListener { commentResult ->
                    val recipeIds = commentResult.documents.mapNotNull { doc ->
                        val rating = doc.getDouble("rating")
                        val recipeId = doc.id
                        if (rating != null && rating >= category.minRatingScore!! && !recipeId.contains("_")) {
                            recipeId
                        } else null
                    }.distinct()

                    if (recipeIds.isEmpty()) {
                        // Nie ma żadnych przepisów spełniających kryterium ratingu i nazwy
                        onComplete(emptyList())
                    } else {
                        // Teraz pobieramy przepisy z kolekcji recipes według warunków
                        queryRecipesByConditions(recipeIds)
                    }
                }
                .addOnFailureListener {
                    onComplete(emptyList())
                }
        } else {
            // Nie potrzebujemy filtrów ratingu
            queryRecipesByConditions(null)
        }
    }


    private fun fetchRecipesFromAPI(category: RecipeCategory, onComplete: (List<MainPageRecipe>) -> Unit) {
        recipeApiService.searchRecipes(
            query = category.ingredient ?: "",
            appId = "REDACTED_EDAMAM_APP_ID",
            apiKey = "REDACTED_EDAMAM_API_KEY",
            diet = category.diet?.let { listOf(it) },
            dishType = category.dish?.let { listOf(it) },
            health = category.healthOptions,
            cuisineType = category.cuisineType?.let { listOf(it) },
            maxTime = category.maxTime?.toString(),
            mealType = category.mealType?.let { listOf(it) }
        ).enqueue(object : Callback<RecipeResponse> {
            override fun onResponse(call: Call<RecipeResponse>, response: Response<RecipeResponse>) {
                if (response.isSuccessful) {
                    val recipes = response.body()?.hits?.map { hit ->
                        val apiRecipe = hit.recipe
                        val convertedRecipe = Recipe(
                            id = generateIdFromUrl(apiRecipe.url),
                            userId = "",
                            label = apiRecipe.label,
                            image = apiRecipe.image,
                            url = apiRecipe.url,
                            ingredientLines = apiRecipe.ingredientLines,
                            calories = apiRecipe.calories,
                            totalTime = apiRecipe.totalTime,
                            dietLabels = apiRecipe.dietLabels ?: emptyList(),
                            healthLabels = apiRecipe.healthLabels ?: emptyList(),
                            cuisineType = apiRecipe.cuisineType ?: emptyList(),
                            mealType = apiRecipe.mealType ?: emptyList(),
                            dishType = apiRecipe.dishType ?: emptyList(),
                            totalNutrients = apiRecipe.totalNutrients,
                            co2EmissionsClass = apiRecipe.co2EmissionsClass,
                            instructions = "", // API może nie zwracać instrukcji, można zostawić puste
                            difficulty = null,
                            isFromFirebase = false,
                            rating = null,
                            ratingAmount = null
                        )

                        MainPageRecipe(recipe = convertedRecipe)
                    } ?: emptyList()
                    onComplete(recipes.shuffled())
                } else {
                    onComplete(emptyList())
                }
            }

            override fun onFailure(call: Call<RecipeResponse>, t: Throwable) {
                onComplete(emptyList())
            }
        })
    }

    private fun fetchRecipesForCategory(category: RecipeCategory, onComplete: (List<MainPageRecipe>) -> Unit) {
        fetchRecipesFromFirebase(category) { firebaseRecipes ->
            val firebaseNeeded = 5
            val apiNeeded = 5

            val firebaseSelected = firebaseRecipes.take(firebaseNeeded)
            val firebaseCount = firebaseSelected.size

            if (firebaseCount < firebaseNeeded) {
                val additionalNeeded = apiNeeded + (firebaseNeeded - firebaseCount)
                fetchRecipesFromAPI(category) { apiRecipes ->
                    val finalList = firebaseSelected + apiRecipes.take(additionalNeeded)
                    onComplete(finalList.take(10))
                }
            } else {
                fetchRecipesFromAPI(category) { apiRecipes ->
                    val finalList = firebaseSelected + apiRecipes.take(apiNeeded)
                    onComplete(finalList.take(10))
                }
            }
        }
    }

    private fun generateIdFromUrl(url: String): String {
        return url.replace("/", "_")
    }

    private fun saveFiltersToPreferences() {
        val sharedPreferences = getSharedPreferences("filters_prefs", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("ingredientInput", ingredientInput.text.toString())
            putStringSet("selectedDiets", selectedDiets)
            putStringSet("selectedHealths", selectedHealths)
            putStringSet("selectedCuisines", selectedCuisines)
            putStringSet("selectedMealTypes", selectedMealTypes)
            putStringSet("selectedDishTypes", selectedDishTypes)
            putString("selectedTime", selectedTime)
            apply()
        }
    }

    private fun loadFiltersFromPreferences() {
        val sharedPreferences = getSharedPreferences("filters_prefs", MODE_PRIVATE)
        ingredientInput.setText(sharedPreferences.getString("ingredientInput", ""))

        selectedDiets.clear()
        selectedDiets.addAll(sharedPreferences.getStringSet("selectedDiets", emptySet()) ?: emptySet())

        selectedHealths.clear()
        selectedHealths.addAll(sharedPreferences.getStringSet("selectedHealths", emptySet()) ?: emptySet())

        selectedCuisines.clear()
        selectedCuisines.addAll(sharedPreferences.getStringSet("selectedCuisines", emptySet()) ?: emptySet())

        selectedMealTypes.clear()
        selectedMealTypes.addAll(sharedPreferences.getStringSet("selectedMealTypes", emptySet()) ?: emptySet())

        selectedDishTypes.clear()
        selectedDishTypes.addAll(sharedPreferences.getStringSet("selectedDishTypes", emptySet()) ?: emptySet())

        selectedTime = sharedPreferences.getString("selectedTime", null)
    }


    private fun formatSelectedText(selectedItems: Set<String>): String {
        return when {
            selectedItems.isEmpty() -> "ANY"
            selectedItems.size == 1 -> selectedItems.first().capitalize()
            else -> "${selectedItems.first().capitalize()} +${selectedItems.size - 1}"
        }
    }

    private fun generateRecipeFromFilters() {
        val ingredientQuery = ingredientInput.text.toString().trim()

        val isFilterSelected = selectedDiets.isNotEmpty() || selectedHealths.isNotEmpty() ||
                selectedCuisines.isNotEmpty() || selectedMealTypes.isNotEmpty() ||
                selectedDishTypes.isNotEmpty() || selectedTime != null

        if (ingredientQuery.isEmpty() && !isFilterSelected) {
            ingredientInput.error = getString(R.string.no_ingredients_or_filter)
            return
        }

        val diets = selectedDiets.joinToString(", ") { it.capitalize() }
        val healths = selectedHealths.joinToString(", ") { it.capitalize() }
        val cuisines = selectedCuisines.joinToString(", ") { it.capitalize() }
        val mealTypes = selectedMealTypes.joinToString(", ") { it.capitalize() }
        val dishTypes = selectedDishTypes.joinToString(", ") { it.capitalize() }
        val maxTime = selectedTime?.let { "$it minutes" } ?: "Any time"

        val promptBuilder = StringBuilder("Generate a recipe with the following details:\n")
        if (ingredientQuery.isNotEmpty()) promptBuilder.append("Main ingredients: $ingredientQuery\n")
        if (diets.isNotEmpty()) promptBuilder.append("Dietary preferences: $diets\n")
        if (healths.isNotEmpty()) promptBuilder.append("Health preferences: $healths\n")
        if (cuisines.isNotEmpty()) promptBuilder.append("Cuisine type: $cuisines\n")
        if (mealTypes.isNotEmpty()) promptBuilder.append("Meal type: $mealTypes\n")
        if (dishTypes.isNotEmpty()) promptBuilder.append("Dish type: $dishTypes\n")
        promptBuilder.append("Preparation time: $maxTime\n")
        promptBuilder.append("List the ingredients and provide the recipe steps.")

        val prompt = promptBuilder.toString()

        sendImageToGemini(prompt = prompt, validationType = "recipe filters")
    }

    private fun processSelectedImage(imageUri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            sendImageToGemini(bitmap, "Analyze the image content. If it contains ingredients, list them without spaces in lowercase separated by a comma. If there are no ingredients, respond with 'No ingredients detected.'", "ingredients")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateRecipeFromImage(imageUri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            sendImageToGemini(bitmap, "Analyze the image content. If it shows a ready-to-eat dish or ingredients to make the dish, generate a recipe with ingredients and step-by-step instructions and other useful information such as time, difficulty, number of portions. Otherwise, respond with 'No dish detected.'", "recipe image")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendImageToGemini(image: Bitmap? = null, prompt: String, validationType: String) {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-pro",
            apiKey = "REDACTED_GEMINI_API_KEY"
        )

        val inputContent = content {
            image?.let { image(it) }
            text(prompt)
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)

        val progressMessage = dialogView.findViewById<MaterialTextView>(R.id.progress_message)
        progressMessage.text = if (validationType.contains("recipe")) {
            getString(R.string.generating_recipe)
        } else {
            getString(R.string.identifying_ingredients)
        }

        val progressDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(inputContent)

                val responseText = response.text?.lowercase()

                when (validationType) {
                    "ingredients" -> {
                        if (responseText != null) {
                            if (responseText.contains("no ingredients detected")) {
                                progressDialog.dismiss()
                                Toast.makeText(this@SearchActivity, "No ingredients detected in the image.", Toast.LENGTH_SHORT).show()
                            } else {
                                progressDialog.dismiss()
                                ingredientInput.setText(response.text)
                            }
                        }
                    }
                    "recipe image" -> {
                        if (responseText != null) {
                            if (responseText.contains("no dish detected")) {
                                progressDialog.dismiss()
                                Toast.makeText(this@SearchActivity, "The image does not show a dish or ingredients.", Toast.LENGTH_SHORT).show()
                            } else {
                                val intent = Intent(this@SearchActivity, RecipeGeneratedActivity::class.java).apply {
                                    putExtra("RECIPE_TEXT", response.text)
                                    image?.let {
                                        putExtra("RECIPE_IMAGE_URI", imagePathH.toString())
                                    }
                                }
                                progressDialog.dismiss()
                                startActivity(intent)
                            }
                        }
                    }
                    "recipe filters" -> {
                        val intent = Intent(this@SearchActivity, RecipeGeneratedActivity::class.java).apply {
                            putExtra("RECIPE_TEXT", response.text)
                        }
                        progressDialog.dismiss()
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                e.printStackTrace()
                Toast.makeText(this@SearchActivity, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImageOptionsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_two_buttons, null)

        val generateRecipeButton = dialogView.findViewById<MaterialButton>(R.id.dialog_button_top)
        val autoFillButton = dialogView.findViewById<MaterialButton>(R.id.dialog_button_bottom)

        generateRecipeButton.text = getString(R.string.generate_recipe_by_photo)
        autoFillButton.text = getString(R.string.auto_fill_by_image)

        generateRecipeButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_gemini_logo, 0)
        autoFillButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_camera, 0)

        generateRecipeButton.compoundDrawableTintList = ContextCompat.getColorStateList(this, R.color.text_primary)
        autoFillButton.compoundDrawableTintList = ContextCompat.getColorStateList(this, R.color.text_primary)

        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.dialog_title_choose)
            .setView(dialogView)
            .setNegativeButton(getString(R.string.close)) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        generateRecipeButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, GENERATE_RECIPE_REQUEST_CODE)

            dialog.dismiss()
        }

        autoFillButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE)

            dialog.dismiss()
        }

        dialog.show()
    }


    private fun showGenerateOrSearchDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_two_buttons, null)

        val generateRecipeButton = dialogView.findViewById<MaterialButton>(R.id.dialog_button_top)
        val searchRecipeButton = dialogView.findViewById<MaterialButton>(R.id.dialog_button_bottom)

        generateRecipeButton.text = getString(R.string.generate_recipe_by_ingredients)
        searchRecipeButton.text = getString(R.string.search_recipe)

        generateRecipeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent)
        searchRecipeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent)

        generateRecipeButton.setTextColor(ContextCompat.getColor(this, R.color.background))
        searchRecipeButton.setTextColor(ContextCompat.getColor(this, R.color.background))

        generateRecipeButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_gemini_logo, 0)
        searchRecipeButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_search, 0)

        generateRecipeButton.compoundDrawableTintList = ContextCompat.getColorStateList(this, R.color.background)
        searchRecipeButton.compoundDrawableTintList = ContextCompat.getColorStateList(this, R.color.background)


        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle(R.string.dialog_title_choose)
            .setView(dialogView)
            .setNegativeButton(getString(R.string.close)) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        generateRecipeButton.setOnClickListener {
            generateRecipeFromFilters()
            dialog.dismiss()
        }

        searchRecipeButton.setOnClickListener {
            val ingredientQuery = ingredientInput.text.toString().trim()

            val intent = Intent(this, RecipeResultsActivity::class.java)
            intent.putExtra("query", ingredientQuery.ifEmpty { null })
            intent.putStringArrayListExtra("diet", ArrayList(selectedDiets))
            intent.putStringArrayListExtra("health", ArrayList(selectedHealths))
            intent.putStringArrayListExtra("cuisineType", ArrayList(selectedCuisines))
            intent.putStringArrayListExtra("mealType", ArrayList(selectedMealTypes))
            intent.putStringArrayListExtra("dishType", ArrayList(selectedDishTypes))
            intent.putExtra("maxTime", selectedTime)
            startActivity(intent)

            dialog.dismiss()
        }

        dialog.show()
    }



    // Dialog wyboru wielu opcji
    private fun showMultiChoiceDialog(title: String, options: Array<String>, selectedItems: MutableSet<String>, onUpdate: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_choice, null)
        val optionsListView = dialogView.findViewById<ListView>(R.id.options_list)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)

        dialogTitle.text = title

        optionsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, options)
        optionsListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        options.forEachIndexed { index, option ->
            optionsListView.setItemChecked(index, option in selectedItems)
        }

        MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_positive)) { dialog, _ ->
                selectedItems.clear()
                for (i in options.indices) {
                    if (optionsListView.isItemChecked(i)) {
                        selectedItems.add(options[i])
                    }
                }
                onUpdate()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_negative_filters)) { dialog, _ ->
                selectedItems.clear()
                onUpdate()
                dialog.dismiss()
            }
            .show()
    }


    private fun showSingleChoiceDialog(title: String, options: Array<String>, currentSelection: String?, onSelect: (String?) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_choice, null)
        val optionsListView = dialogView.findViewById<ListView>(R.id.options_list)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)

        dialogTitle.text = title

        optionsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, options)
        optionsListView.choiceMode = ListView.CHOICE_MODE_SINGLE

        val selectedIndex = options.indexOf(currentSelection)
        if (selectedIndex >= 0) {
            optionsListView.setItemChecked(selectedIndex, true)
        }

        MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_positive)) { dialog, _ ->
                val selectedPosition = optionsListView.checkedItemPosition
                if (selectedPosition != ListView.INVALID_POSITION) {
                    onSelect(options[selectedPosition])
                } else {
                    onSelect(null)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_negative_filters)) { dialog, _ ->
                onSelect(null)
                dialog.dismiss()
            }
            .show()
    }


    override fun onDestroy() {
        alertDialog?.dismiss()
        alertDialog = null
        super.onDestroy()
    }
}
