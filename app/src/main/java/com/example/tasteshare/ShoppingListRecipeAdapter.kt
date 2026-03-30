package com.example.tasteshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShoppingListRecipeAdapter(
    private val recipes: MutableList<ShoppingListRecipe>
) : RecyclerView.Adapter<ShoppingListRecipeAdapter.RecipeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.shopping_list_recipe_item, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.bind(recipe)
    }

    override fun getItemCount(): Int = recipes.size

    fun removeRecipeById(recipeId: String) {
        val position = recipes.indexOfFirst { it.id == recipeId }
        if (position != -1) {
            recipes.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, recipes.size)
        }
    }

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val recipeTitle: TextView = itemView.findViewById(R.id.recipe_title)
        private val ingredientRecyclerView: RecyclerView = itemView.findViewById(R.id.ingredient_list_recycler_view)

        fun bind(recipe: ShoppingListRecipe) {
            recipeTitle.text = recipe.name
            ingredientRecyclerView.layoutManager = LinearLayoutManager(itemView.context)

            ingredientRecyclerView.adapter = ShoppingListIngredientAdapter(
                recipeId = recipe.id,
                recipe.ingredients.toMutableList(),
                onEditClick = { /* Handle edit ingredient here */ },
                onCheckedChange = { ingredient, isChecked ->
                    handleIngredientCheckChange(recipe.id, ingredient, isChecked)
                },
                onDocumentDeleted = { deletedRecipeId ->
                    removeRecipeById(deletedRecipeId)
                }
            )

            itemView.setOnLongClickListener {
                MaterialAlertDialogBuilder(itemView.context, R.style.CustomAlertDialogAreYouSure)
                    .setTitle(itemView.context.getString(R.string.dialog_title_shopping_list_remove_recipe_confirm))
                    .setMessage(itemView.context.getString(R.string.dialog_message_shopping_list_remove_recipe_confirm))
                    .setPositiveButton(itemView.context.getString(R.string.yes)) { _, _ ->
                        deleteRecipeFromFirebase(recipe.id)
                    }
                    .setNegativeButton(itemView.context.getString(R.string.no), null)
                    .show()
                true
            }
        }

        private fun handleIngredientCheckChange(
            recipeId: String,
            ingredient: String,
            isChecked: Boolean
        ) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            db.collection("users")
                .document(userId)
                .collection("ShoppingList")
                .document(recipeId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val items = document.get("items") as? MutableList<Map<String, Any>>
                        if (items != null) {
                            val updatedItems = items.filter { it["item"] != ingredient }
                            if (updatedItems.isEmpty()) {
                                db.collection("users")
                                    .document(userId)
                                    .collection("ShoppingList")
                                    .document(recipeId)
                                    .delete()
                                    .addOnSuccessListener {
                                        removeRecipeById(recipeId)
                                    }
                            } else {
                                db.collection("users")
                                    .document(userId)
                                    .collection("ShoppingList")
                                    .document(recipeId)
                                    .update("items", updatedItems)
                            }
                        }
                    }
                }
        }

        private fun deleteRecipeFromFirebase(recipeId: String) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            db.collection("users")
                .document(userId)
                .collection("ShoppingList")
                .document(recipeId)
                .delete()
                .addOnSuccessListener {
                    removeRecipeById(recipeId)
                }
                .addOnFailureListener { e ->
                    println("Error deleting document: ${e.message}")
                }
        }
    }
}
