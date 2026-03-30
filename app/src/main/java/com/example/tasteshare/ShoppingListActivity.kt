package com.example.tasteshare

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShoppingListActivity : BaseActivity() {

    private lateinit var shoppingListRecyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.shopping_list_activity)

        setupBottomNavigationMenu(R.id.action_cart)

        shoppingListRecyclerView = findViewById(R.id.shopping_list_recycler_view)
        shoppingListRecyclerView.layoutManager = LinearLayoutManager(this)

        fetchShoppingList()
    }

    private fun fetchShoppingList() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("ShoppingList")
            .get()
            .addOnSuccessListener { documents ->
                val shoppingList = documents.map { doc ->
                    val recipeName = doc.getString("recipe_name") ?: "Unknown Recipe"
                    val recipeId = doc.id
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val ingredients = items.map { item ->
                        val ingredientName = item["item"] as? String ?: "Unknown Ingredient"
                        val isChecked = item["isChecked"] as? Boolean ?: false
                        ingredientName to isChecked
                    }
                    ShoppingListRecipe(recipeId, recipeName, ingredients)
                }

                shoppingListRecyclerView.adapter = ShoppingListRecipeAdapter(shoppingList.toMutableList())
            }
    }
}
