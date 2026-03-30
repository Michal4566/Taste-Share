package com.example.tasteshare

import android.os.Bundle
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FavoritesResultsActivity : BaseActivity() {

    private lateinit var recipeRecyclerView: RecyclerView
    private lateinit var recipeAdapter: RecipeAdapter
    private var recipes: List<Recipe> = emptyList()

    private val recipeViewModel: RecipeViewModel by viewModels()

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val db = FirebaseFirestore.getInstance()

    private var isHeaderVisible = true // Track if the header is currently visible
    private var totalScrolledDistance = 0 // Track the total scroll distance
    private val scrollThreshold = 3 // Define the minimum scroll distance to trigger hide/show
    private val scrollDelay = 1.5 // Minimum delay before header can shows again
    private var lastScrollTime = 0L // Track the last scroll time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_results)

        setupBottomNavigationMenu(R.id.action_favorites)

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

        val pageTitle = findViewById<MaterialTextView>(R.id.page_title_text_view)
        val ingredientsTextView = findViewById<MaterialTextView>(R.id.secondary_text_view)

        pageTitle.text = getString(R.string.your_favorites)
        ingredientsTextView.visibility = View.GONE // Hide secondary text

        recipeRecyclerView = findViewById(R.id.recipe_recycler_view)
        recipeRecyclerView.layoutManager = LinearLayoutManager(this)

        val headerLayout = findViewById<LinearLayout>(R.id.header_layout)

        headerLayout.post {
            val headerHeight = headerLayout.height
            recipeRecyclerView.setPadding(0, headerHeight, 0, 0)
            recipeRecyclerView.clipToPadding = false
        }

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

        if (currentUserId != null) {
            db.collection("users").document(currentUserId)
                .collection("favoriteRecipes")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) {
                        Log.e("FirestoreListener", "Error fetching data: ${e?.message}")
                        return@addSnapshotListener
                    }

                    val newRecipes = snapshot.documents.mapNotNull { it.toObject(Recipe::class.java) }
                    if (newRecipes.isNotEmpty()) {
                        recipes = newRecipes
                        recipeAdapter = RecipeAdapter(this, recipes, FavoriteDetailActivity::class.java)
                        recipeRecyclerView.adapter = recipeAdapter
                    } else {
                        Toast.makeText(this, "You have no favorite recipes.", Toast.LENGTH_SHORT).show()
                    }
                }

        } else {
            Toast.makeText(this, "Please log in to view favorite recipes.", Toast.LENGTH_SHORT).show()
        }
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
            recipeAdapter = RecipeAdapter(this, sortedRecipes, FavoriteDetailActivity::class.java)
            recipeRecyclerView.adapter = recipeAdapter
            recipeAdapter.notifyDataSetChanged()
        } else {
            Toast.makeText(this, "No results to sort", Toast.LENGTH_SHORT).show()
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
}
