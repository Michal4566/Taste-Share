package com.example.tasteshare

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RecipeResultsActivity :  BaseActivity() {

    private lateinit var recipeRecyclerView: RecyclerView
    private lateinit var recipeApiService: RecipeApiService
    private lateinit var recipeAdapter: RecipeAdapter
    private var recipes: List<Recipe> = emptyList()

    private val recipeViewModel: RecipeViewModel by viewModels()

    private var apiRecipes: List<Recipe> = emptyList()
    private var firebaseRecipes: List<Recipe> = emptyList()

    private var apiSearchCompleted = false
    private var firebaseSearchCompleted = false

    private var isHeaderVisible = true // Track if the header is currently visible
    private var totalScrolledDistance = 0 // Track the total scroll distance
    private val scrollThreshold = 3 // Define the minimum scroll distance to trigger hide/show
    private val scrollDelay = 1.5 // Minimum delay before header can shows again
    private var lastScrollTime = 0L // Track the last scroll time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_results)

        setupBottomNavigationMenu(R.id.action_search)

        val sortBySpinner = findViewById<Spinner>(R.id.sort_by_spinner)

        val sortOptions = listOf("Choose an option") + resources.getStringArray(R.array.sort_options).toList()
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, sortOptions) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                view.setBackgroundColor(resources.getColor(R.color.secondary, null))
                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortBySpinner.adapter = adapter

        // Set the spinner to show the hint by default
        sortBySpinner.setSelection(0)
        sortBySpinner.setSelection(recipeViewModel.currentSortPosition)

        // Add the listener to handle selections
        sortBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    recipeViewModel.currentSortPosition = position
                    sortRecipes(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }


        // Tworzenie i konfiguracja HttpLoggingInterceptor
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Możesz zmienić na BASIC, HEADERS lub BODY
        }

        // Tworzenie klienta OkHttp z loggingiem
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.edamam.com/api/recipes/v2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val pageTitle = findViewById<MaterialTextView>(R.id.page_title_text_view)
        val ingredientsTextView = findViewById<MaterialTextView>(R.id.secondary_text_view)
        val headerLayout = findViewById<LinearLayout>(R.id.header_layout)
        recipeApiService = retrofit.create(RecipeApiService::class.java)

        recipeRecyclerView = findViewById(R.id.recipe_recycler_view)
        recipeRecyclerView.layoutManager = LinearLayoutManager(this)

        recipeRecyclerView = findViewById(R.id.recipe_recycler_view)
        recipeRecyclerView.layoutManager = LinearLayoutManager(this)

        headerLayout.post {
            val headerHeight = headerLayout.height
            recipeRecyclerView.setPadding(0, headerHeight, 0, 0)
            recipeRecyclerView.clipToPadding = false
        }

        // Odbieranie danych przekazanych z SearchActivity
        val query = intent.getStringExtra("query")
        val dietList = intent.getStringArrayListExtra("diet")
        val healthList = intent.getStringArrayListExtra("health")
        val cuisineTypeList = intent.getStringArrayListExtra("cuisineType")
        val mealTypeList = intent.getStringArrayListExtra("mealType")
        val dishTypeList = intent.getStringArrayListExtra("dishType")
        val maxTime = intent.getStringExtra("maxTime")

        pageTitle.text = getString(R.string.search_results_ingredients)
        ingredientsTextView.text = query ?: "No ingredients specified"

        // Set up RecyclerView OnScrollListener
        recipeRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val currentTime = System.currentTimeMillis()

                // Prevent scroll accumulation when at the top
                if (!recyclerView.canScrollVertically(-1) && dy < 0) {
                    totalScrolledDistance = 0
                    return
                }

                // Prevent scroll accumulation when at the bottom
                if (!recyclerView.canScrollVertically(1) && dy > 0) {
                    totalScrolledDistance = 0
                    return
                }

                if (dy > 0) {
                    // Accumulate scroll distance only when scrolling down
                    totalScrolledDistance += dy

                    // Hide header if scrolled down past threshold
                    if (isHeaderVisible && totalScrolledDistance > scrollThreshold) {
                        isHeaderVisible = false
                        hideHeader(headerLayout)
                        totalScrolledDistance = 0 // Reset scroll distance after hiding
                    }
                } else if (dy < 0) {
                    // Reset scroll distance when scrolling up
                    totalScrolledDistance = 0

                    // Show header when scrolling up past threshold and sufficient delay has passed
                    if (!isHeaderVisible && Math.abs(dy) > scrollThreshold && currentTime - lastScrollTime > scrollDelay) {
                        isHeaderVisible = true
                        showHeader(headerLayout)
                    }
                }

                // Update last scroll time for delay control
                lastScrollTime = currentTime
            }
        })


        if (recipeViewModel.recipes.isEmpty()) {
            searchRecipesInAPI(query, dietList, healthList, cuisineTypeList, mealTypeList, dishTypeList, maxTime)
            searchRecipesInFirebase(query, dietList, healthList, cuisineTypeList, mealTypeList, dishTypeList, maxTime)
        } else {
            recipes = recipeViewModel.recipes
            recipeAdapter = RecipeAdapter(this, recipes, RecipeDetailActivity::class.java)
            recipeRecyclerView.adapter = recipeAdapter
        }
    }

    private fun hideHeader(headerLayout: LinearLayout) {
        headerLayout.animate()
            .translationY(-headerLayout.height.toFloat()) // Move layout up by its height
            .alpha(0f) // Fade out
            .setDuration(300)
            .start()
    }

    private fun showHeader(headerLayout: LinearLayout) {
        headerLayout.animate()
            .translationY(0f) // Reset position
            .alpha(1f) // Fade in
            .setDuration(300)
            .start()
    }

    private fun sortRecipes(position: Int) {
        if (recipes.isNotEmpty()) {
            val sortedRecipes = when (position) {
                1 -> recipes.sortedBy { it.totalTime ?: Double.MAX_VALUE }
                2 -> recipes.sortedByDescending { it.totalTime ?: Double.MIN_VALUE }
                3 -> recipes.sortedBy { it.calories ?: Double.MAX_VALUE }
                4 -> recipes.sortedByDescending { it.calories ?: Double.MIN_VALUE }
                else -> recipes
            }

            // Update adapter after sorting
            recipeAdapter = RecipeAdapter(this, sortedRecipes, RecipeDetailActivity::class.java)
            recipeRecyclerView.adapter = recipeAdapter
            recipeAdapter.notifyDataSetChanged()
        } else {
            Toast.makeText(this, "No results to sort", Toast.LENGTH_SHORT).show()
        }
    }

    // Funkcja wysyłająca zapytanie do API Edamam
    private fun searchRecipesInAPI(
        query: String?,
        dietList: ArrayList<String>?,
        healthList: ArrayList<String>?,
        cuisineTypeList: ArrayList<String>?,
        mealTypeList: ArrayList<String>?,
        dishTypeList: ArrayList<String>?,
        maxTime: String?
    ) {
        recipeApiService.searchRecipes(
            query = query ?: "",
            appId = "REDACTED_EDAMAM_APP_ID",
            apiKey = "REDACTED_EDAMAM_API_KEY",
            diet = dietList?.filter { it.isNotEmpty() },
            health = healthList?.filter { it.isNotEmpty() },
            cuisineType = cuisineTypeList?.filter { it.isNotEmpty() },
            mealType = mealTypeList?.filter { it.isNotEmpty() },
            dishType = dishTypeList?.filter { it.isNotEmpty() },
            maxTime = maxTime.takeIf { !it.isNullOrEmpty() }
        ).enqueue(object : Callback<RecipeResponse> {
            override fun onResponse(call: Call<RecipeResponse>, response: Response<RecipeResponse>) {
                if (response.isSuccessful) {
                    apiRecipes = response.body()?.hits?.map { hit ->
                        val apiRecipe = hit.recipe
                        Recipe(
                            id = generateIdFromUrl(apiRecipe.url),
                            userId = "", // Brak userId dla API
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
                            instructions = "", // API nie dostarcza bezpośrednich instrukcji
                            difficulty = null,
                            isFromFirebase = false,
                            rating = null,
                            ratingAmount = null
                        )
                    } ?: emptyList()
                    apiSearchCompleted = true
                    checkAndDisplayResults()
                } else {
                    apiRecipes = emptyList()
                    apiSearchCompleted = true
                    checkAndDisplayResults()
                }
            }

            override fun onFailure(call: Call<RecipeResponse>, t: Throwable) {
                apiRecipes = emptyList()
                apiSearchCompleted = true
                checkAndDisplayResults()
            }
        })
    }

    private fun generateIdFromUrl(url: String): String {
        return url.replace("/", "_")
    }

    private fun searchRecipesInFirebase(
        query: String?,
        dietList: ArrayList<String>?,
        healthList: ArrayList<String>?,
        cuisineTypeList: ArrayList<String>?,
        mealTypeList: ArrayList<String>?,
        dishTypeList: ArrayList<String>?,
        maxTime: String?
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("recipes")
            .get()
            .addOnSuccessListener { result ->
                // Wczytujemy bezpośrednio do Recipe
                var filteredRecipes = result.documents.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)
                }

                // Filtracja po query (składnikach)
                if (!query.isNullOrEmpty()) {
                    val queryLower = query.lowercase()
                    filteredRecipes = filteredRecipes.filter { recipe ->
                        recipe.ingredientLines.any { it.lowercase().contains(queryLower) }
                    }
                }

                // Filtracja po dietLabels
                if (!dietList.isNullOrEmpty()) {
                    filteredRecipes = filteredRecipes.filter { recipe ->
                        recipe.dietLabels.any { dietList.contains(it) }
                    }
                }

                // Filtracja po healthLabels
                if (!healthList.isNullOrEmpty()) {
                    filteredRecipes = filteredRecipes.filter { recipe ->
                        recipe.healthLabels.any { healthList.contains(it) }
                    }
                }

                // Filtracja po cuisineType
                if (!cuisineTypeList.isNullOrEmpty()) {
                    filteredRecipes = filteredRecipes.filter { recipe ->
                        recipe.cuisineType.any { cuisineTypeList.contains(it) }
                    }
                }

                // Filtracja po mealType
                if (!mealTypeList.isNullOrEmpty()) {
                    filteredRecipes = filteredRecipes.filter { recipe ->
                        recipe.mealType.any { mealTypeList.contains(it) }
                    }
                }

                // Filtracja po dishType
                if (!dishTypeList.isNullOrEmpty()) {
                    filteredRecipes = filteredRecipes.filter { recipe ->
                        recipe.dishType.any { dishTypeList.contains(it) }
                    }
                }

                // Filtracja po maxTime
                if (!maxTime.isNullOrEmpty()) {
                    val maxTimeInt = maxTime.toIntOrNull()
                    if (maxTimeInt != null) {
                        filteredRecipes = filteredRecipes.filter { recipe ->
                            (recipe.totalTime?.toInt() ?: Int.MAX_VALUE) <= maxTimeInt
                        }
                    }
                }

                firebaseRecipes = filteredRecipes
                firebaseSearchCompleted = true
                checkAndDisplayResults()
            }
            .addOnFailureListener {
                firebaseRecipes = emptyList()
                firebaseSearchCompleted = true
                checkAndDisplayResults()
            }
    }

    private fun checkAndDisplayResults() {
        if (apiSearchCompleted && firebaseSearchCompleted) {
            // Combine both lists
            recipes = apiRecipes + firebaseRecipes

            // Sort recipes if necessary
            if (recipeViewModel.currentSortPosition > 0) {
                sortRecipes(recipeViewModel.currentSortPosition)
            }

            recipeViewModel.recipes = recipes
            recipeAdapter = RecipeAdapter(this@RecipeResultsActivity, recipes, RecipeDetailActivity::class.java)
            recipeRecyclerView.adapter = recipeAdapter
        }
    }

}
