package com.example.tasteshare

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShoppingListIngredientAdapter(
    private val recipeId: String, // Pass recipe ID for context
    private var ingredients: MutableList<Pair<String, Boolean>>, // Mutable list for local updates
    private val onEditClick: (String) -> Unit,
    private val onCheckedChange: (String, Boolean) -> Unit,
    private val onDocumentDeleted: (String) -> Unit
) : RecyclerView.Adapter<ShoppingListIngredientAdapter.IngredientViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.shopping_list_ingredient_item, parent, false)
        return IngredientViewHolder(view)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        val (ingredient, isChecked) = ingredients[position]
        holder.bind(ingredient, isChecked)
    }

    override fun getItemCount(): Int = ingredients.size

    inner class IngredientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ingredientName: TextView = itemView.findViewById(R.id.ingredient_name)
        private val ingredientCheckbox: MaterialCheckBox = itemView.findViewById(R.id.ingredient_checkbox)
        private val editIcon: ImageView = itemView.findViewById(R.id.edit_ingredient_icon)
        private val ingredientContainer: LinearLayout = itemView.findViewById(R.id.ingredient_container)

        fun bind(ingredient: String, isChecked: Boolean) {
            ingredientName.text = ingredient
            ingredientCheckbox.setOnCheckedChangeListener(null)
            ingredientCheckbox.isChecked = isChecked

            ingredientCheckbox.setOnCheckedChangeListener { _, checked ->
                updateStyles(checked)
                updateIsCheckedInFirebase(recipeId, ingredient, checked)
                ingredients[adapterPosition] = ingredient to checked
                notifyItemChanged(adapterPosition)
            }

            updateStyles(isChecked)

            editIcon.setOnClickListener { showEditIngredientDialog(ingredient, adapterPosition) }
        }

        private fun updateStyles(isChecked: Boolean) {
            val accentColor = itemView.context.getColor(R.color.accent)
            val primaryColor = itemView.context.getColor(R.color.text_primary)

            ingredientCheckbox.buttonTintList = ColorStateList.valueOf(if (isChecked) accentColor else primaryColor)
            ingredientName.setTextColor(if (isChecked) accentColor else primaryColor)
            editIcon.setColorFilter(if (isChecked) accentColor else primaryColor, PorterDuff.Mode.SRC_IN)

            val background = ingredientContainer.background as GradientDrawable
            background.setStroke(3, if (isChecked) accentColor else primaryColor)
        }

        private fun showEditIngredientDialog(currentIngredient: String, position: Int) {
            val dialogView = LayoutInflater.from(itemView.context).inflate(R.layout.dialog_single_text_input, null)
            val inputField = dialogView.findViewById<TextInputEditText>(R.id.input_text_field)
            inputField.setText(currentIngredient)
            inputField.setHint(itemView.context.getString(R.string.ingredient_input_hint))

            MaterialAlertDialogBuilder(itemView.context, R.style.CustomAlertDialogThemeWithRedNegativeButton)
                .setTitle("Edit Ingredient")
                .setView(dialogView)
                .setPositiveButton(itemView.context.getString(R.string.dialog_button_save)) { dialog, _ ->
                    val newIngredient = inputField.text.toString().trim()
                    if (newIngredient.isNotEmpty()) {
                        updateIngredientInFirebase(recipeId, currentIngredient, newIngredient)
                        ingredients[position] = newIngredient to ingredients[position].second
                        notifyItemChanged(position)
                        dialog.dismiss()
                    } else {
                        inputField.error = itemView.context.getString(R.string.empty_ingredient_name)
                    }
                }
                .setNegativeButton(itemView.context.getString(R.string.dialog_button_delete)) { dialog, _ ->
                    deleteIngredientFromFirebase(recipeId, currentIngredient)
                    ingredients.removeAt(position)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, ingredients.size)
                    dialog.dismiss()
                }
                .setNeutralButton(itemView.context.getString(R.string.dialog_button_negative), null)
                .show()
        }

        private fun updateIsCheckedInFirebase(recipeId: String, ingredientName: String, isChecked: Boolean) {
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
                            val updatedItems = items.map { item ->
                                if (item["item"] == ingredientName) {
                                    item.toMutableMap().apply { this["isChecked"] = isChecked }
                                } else item
                            }

                            db.collection("users")
                                .document(userId)
                                .collection("ShoppingList")
                                .document(recipeId)
                                .update("items", updatedItems)
                        }
                    }
                }
        }

        private fun updateIngredientInFirebase(recipeId: String, oldIngredient: String, newIngredient: String) {
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
                            val updatedItems = items.map { item ->
                                if (item["item"] == oldIngredient) {
                                    item.toMutableMap().apply { this["item"] = newIngredient }
                                } else item
                            }

                            db.collection("users")
                                .document(userId)
                                .collection("ShoppingList")
                                .document(recipeId)
                                .update("items", updatedItems)
                        }
                    }
                }
        }

        private fun deleteIngredientFromFirebase(recipeId: String, ingredient: String) {
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
                                        onDocumentDeleted(recipeId)
                                        println("Document $recipeId deleted successfully as no items are left.")
                                    }
                                    .addOnFailureListener { e ->
                                        println("Error deleting document $recipeId: ${e.message}")
                                    }
                            } else {
                                db.collection("users")
                                    .document(userId)
                                    .collection("ShoppingList")
                                    .document(recipeId)
                                    .update("items", updatedItems)
                                    .addOnSuccessListener {
                                        println("Ingredient $ingredient removed from recipe $recipeId.")
                                    }
                                    .addOnFailureListener { e ->
                                        println("Error updating document $recipeId: ${e.message}")
                                    }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    println("Error retrieving document $recipeId: ${e.message}")
                }
        }
    }
}
