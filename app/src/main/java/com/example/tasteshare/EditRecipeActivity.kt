package com.example.tasteshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlin.math.roundToInt

class EditRecipeActivity : BaseActivity() {

    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null
    private var originalImageUrl: String? = null

    private val ingredientList = mutableListOf<Ingredient>()
    private val selectedDiets = mutableSetOf<String>()
    private val selectedHealths = mutableSetOf<String>()
    private val selectedCuisines = mutableSetOf<String>()
    private val selectedMealTypes = mutableSetOf<String>()
    private val selectedDishTypes = mutableSetOf<String>()
    private var selectedDifficulty: String? = null

    private var isFavoriteRecipe = false
    private var isEditing = false
    private lateinit var recipeId: String

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use your appropriate layout file
        setContentView(R.layout.edit_recipe_activity)

        storage = FirebaseStorage.getInstance()

        val recipeNameEditText = findViewById<TextInputEditText>(R.id.recipe_name)
        val ingredientsListLayout = findViewById<LinearLayout>(R.id.ingredients_list)
        val addIngredientButton = findViewById<MaterialButton>(R.id.add_ingredient_button)
        val changePhotoButton = findViewById<ImageView>(R.id.change_photo_icon)
        val recipeImageView = findViewById<ImageView>(R.id.recipe_image)
        val recipeInstructionsEditText = findViewById<TextInputEditText>(R.id.recipe_instructions)
        val saveRecipeButton = findViewById<MaterialButton>(R.id.save_recipe_button)
        val deleteRecipeButton = findViewById<MaterialButton>(R.id.delete_recipe_button)

        val difficultyFilterLabel = findViewById<LinearLayout>(R.id.difficulty_filter_label)
        val dietFilterLabel = findViewById<LinearLayout>(R.id.diet_filter_label)
        val healthFilterLabel = findViewById<LinearLayout>(R.id.health_filter_label)
        val cuisineFilterLabel = findViewById<LinearLayout>(R.id.cuisine_filter_label)
        val mealTypeLabel = findViewById<LinearLayout>(R.id.meal_type_label)
        val dishTypeLabel = findViewById<LinearLayout>(R.id.dish_type_label)
        val timeLabel = findViewById<LinearLayout>(R.id.time_label)
        val caloriesLabel = findViewById<LinearLayout>(R.id.calories_label)

        val difficultyFilterValue = findViewById<TextView>(R.id.difficulty_filter_value)
        val dietFilterValue = findViewById<TextView>(R.id.diet_filter_value)
        val healthFilterValue = findViewById<TextView>(R.id.health_filter_value)
        val cuisineFilterValue = findViewById<TextView>(R.id.cuisine_filter_value)
        val mealTypeValue = findViewById<TextView>(R.id.meal_type_value)
        val dishTypeValue = findViewById<TextView>(R.id.dish_type_value)
        val timeValue = findViewById<TextView>(R.id.time_value)
        val caloriesValue = findViewById<TextView>(R.id.calories_value)

        // Check if we're editing an existing recipe
        val recipe = intent.getParcelableExtra<Recipe>("recipe")
        if (recipe != null) {
            Log.e("RECIPE", recipe.toString())
            isEditing = true
            isFavoriteRecipe = intent.getBooleanExtra("isFavorite", false)
            recipeId = recipe.id

            // Pre-fill UI components with recipe data
            recipeNameEditText.setText(recipe.label)
            recipeInstructionsEditText.setText(recipe.instructions)
            originalImageUrl = recipe.image

            Glide.with(this).load(recipe.image).into(recipeImageView)

            recipe.ingredientLines.forEach { ingredientLine ->
                val ingredient = parseIngredientLine(ingredientLine)
                addIngredientToList(ingredientsListLayout, ingredient)
            }

            selectedDifficulty = recipe.difficulty
            difficultyFilterValue.text = selectedDifficulty ?: getString(R.string.none)

            recipe.dietLabels?.let { selectedDiets.addAll(it) }
            dietFilterValue.text = formatSelectedText(selectedDiets)

            recipe.healthLabels?.let { selectedHealths.addAll(it) }
            healthFilterValue.text = formatSelectedText(selectedHealths)

            recipe.cuisineType?.let { selectedCuisines.addAll(it) }
            cuisineFilterValue.text = formatSelectedText(selectedCuisines)

            recipe.mealType?.let { selectedMealTypes.addAll(it) }
            mealTypeValue.text = formatSelectedText(selectedMealTypes)

            recipe.dishType?.let { selectedDishTypes.addAll(it) }
            dishTypeValue.text = formatSelectedText(selectedDishTypes)

            timeValue.text = recipe.totalTime?.let { "${it.roundToInt()} min" } ?: getString(R.string.none)
            caloriesValue.text = recipe.calories?.let { "${it.roundToInt()} kcal" } ?: getString(R.string.none)

        } else {
            // Adding a new recipe
            isEditing = false
            recipeId = UUID.randomUUID().toString()
            // Set a default placeholder image initially
            Glide.with(this)
                .load(R.drawable.default_recipe_image)
                .into(recipeImageView)
        }


        if (isEditing) {
            deleteRecipeButton.visibility = View.VISIBLE

            deleteRecipeButton.setOnClickListener {
                MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogAreYouSure)
                    .setTitle(getString(R.string.dialog_title_shopping_list_remove_recipe_confirm))
                    .setMessage(getString(R.string.dialog_message_shopping_list_remove_recipe_confirm))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        deleteRecipe(recipeId)
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            }
        } else {
            deleteRecipeButton.visibility = View.GONE
        }

        // Show ingredient dialog when "Add Ingredient" button is clicked
        addIngredientButton.setOnClickListener {
            showAddIngredientDialog(ingredientsListLayout)
        }

        // Handle image selection
        changePhotoButton.setOnClickListener {
            openImagePicker()
        }

        difficultyFilterLabel.setOnClickListener {
            showSingleChoiceDialog(
                title = "Select Difficulty",
                options = StringArrays.difficultyOptions,
                currentSelection = selectedDifficulty
            ) { selected ->
                selectedDifficulty = selected
                difficultyFilterValue.text = selected ?: getString(R.string.none)
            }
        }

        dietFilterLabel.setOnClickListener {
            showMultiChoiceDialog(
                title = "Select Diet",
                options = StringArrays.dietOptions,
                selectedItems = selectedDiets
            ) {
                dietFilterValue.text = formatSelectedText(selectedDiets)
            }
        }

        healthFilterLabel.setOnClickListener {
            showMultiChoiceDialog(
                title = "Select Health Preferences",
                options = StringArrays.healthOptions,
                selectedItems = selectedHealths
            ) {
                healthFilterValue.text = formatSelectedText(selectedHealths)
            }
        }

        cuisineFilterLabel.setOnClickListener {
            showMultiChoiceDialog(
                title = "Select Cuisine Type",
                options = StringArrays.cuisineOptions,
                selectedItems = selectedCuisines
            ) {
                cuisineFilterValue.text = formatSelectedText(selectedCuisines)
            }
        }

        mealTypeLabel.setOnClickListener {
            showMultiChoiceDialog(
                title = "Select Meal Type",
                options = StringArrays.mealTypeOptions,
                selectedItems = selectedMealTypes
            ) {
                mealTypeValue.text = formatSelectedText(selectedMealTypes)
            }
        }

        dishTypeLabel.setOnClickListener {
            showMultiChoiceDialog(
                title = "Select Dish Type",
                options = StringArrays.dishTypeOptions,
                selectedItems = selectedDishTypes
            ) {
                dishTypeValue.text = formatSelectedText(selectedDishTypes)
            }
        }

        timeLabel.setOnClickListener {
            val currentTime = extractIntValue(timeValue.text.toString(), "min")
            showValueInputDialog("Enter Cooking Time", "min", currentTime) { value ->
                timeValue.text = value?.let { "$it min" } ?: getString(R.string.none)
            }
        }


        caloriesLabel.setOnClickListener {
            val currentCalories = extractIntValue(caloriesValue.text.toString(), "kcal")
            showValueInputDialog("Enter Calories", "kcal", currentCalories) { value ->
                caloriesValue.text = value?.let { "$it kcal" } ?: getString(R.string.none)
            }
        }


        // Save recipe on button click
        saveRecipeButton.setOnClickListener {
            val recipeName = recipeNameEditText.text.toString().trim()
            val instructions = recipeInstructionsEditText.text.toString().trim()
            val ingredients = getIngredientsFromLayout()

            if (recipeName.isEmpty() || instructions.isEmpty() || ingredients.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                val time = extractIntValue(timeValue.text.toString(), "min")
                val calories = extractIntValue(caloriesValue.text.toString(), "kcal")

                saveRecipeToFirebase(
                    name = recipeName,
                    ingredients = ingredients,
                    instructions = instructions,
                    time = time,
                    calories = calories
                )
            }
        }
    }

    private fun extractIntValue(text: String, unit: String): Int? {
        return if (text.endsWith(" $unit")) {
            text.removeSuffix(" $unit").trim().toIntOrNull()
        } else {
            null
        }
    }

    private fun formatSelectedText(selectedItems: Set<String>): String {
        return if (selectedItems.isEmpty()) getString(R.string.none) else selectedItems.joinToString(", ")
    }

    // Show SingleChoice Dialog with pre-selection and custom layout
    private fun showSingleChoiceDialog(
        title: String,
        options: Array<String>,
        currentSelection: String?,
        onSelect: (String?) -> Unit
    ) {
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
                dialog.dismiss()
            }
            .show()
    }

    // Show MultiChoice Dialog with pre-selection and custom layout
    private fun showMultiChoiceDialog(
        title: String,
        options: Array<String>,
        selectedItems: MutableSet<String>,
        onUpdate: () -> Unit
    ) {
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
                dialog.dismiss()
            }
            .show()
    }

    // Show dialog to add time or calories
    private fun showValueInputDialog(title: String, unit: String, initialValue: Int?, onValueEntered: (Int?) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_calories_input, null)
        val valueInputField = dialogView.findViewById<TextInputEditText>(R.id.value_input_field)
        val unitText = dialogView.findViewById<TextView>(R.id.unit_text)

        unitText.text = unit

        if (initialValue != null && initialValue > 0) {
            valueInputField.setText(initialValue.toString())
        }

        MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_positive)) { dialog, _ ->
                val value = valueInputField.text.toString().toIntOrNull()
                onValueEntered(value)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_negative_filters)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    // Show dialog to add ingredient
    private fun showAddIngredientDialog(
        ingredientsListLayout: LinearLayout,
        ingredient: Ingredient? = null,
        onIngredientUpdated: ((Ingredient) -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_ingredient, null)
        val dialogBuilder = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
        dialogBuilder.setView(dialogView)

        val ingredientNameField = dialogView.findViewById<AutoCompleteTextView>(R.id.ingredient_name_dropdown)
        val ingredientQuantityField = dialogView.findViewById<TextInputEditText>(R.id.ingredient_quantity_field)
        val ingredientUnitField = dialogView.findViewById<TextInputEditText>(R.id.ingredient_unit_field)

        val ingredients = StringArrays.ingredientsOptions
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ingredients)
        ingredientNameField.setAdapter(adapter)
        ingredientNameField.threshold = 1

        ingredient?.let {
            ingredientNameField.setText(it.name)
            ingredientQuantityField.setText(it.quantity.toString())
            ingredientUnitField.setText(it.unit)
        }

        val dialog = dialogBuilder
            .setTitle(
                if (ingredient == null) getString(R.string.dialog_title_edit_recipe_add_ingredient) else getString(
                    R.string.dialog_title_edit_recipe_edit_ingredient
                )
            )
            .setNegativeButton(if (ingredient == null) getString(R.string.dialog_button_negative) else getString(R.string.dialog_button_delete)) { dialog, _ ->
                if (ingredient != null) {
                    removeIngredientById(ingredientsListLayout, ingredient.id)
                }
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.dialog_button_save), null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton?.setOnClickListener {
                val name = ingredientNameField.text.toString().trim()
                val quantity = ingredientQuantityField.text.toString().toDoubleOrNull() ?: 0.0
                val unit = ingredientUnitField.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.empty_ingredient_name), Toast.LENGTH_SHORT).show()
                } else if (quantity <= 0.0) {
                    Toast.makeText(this, "Please enter a valid quantity greater than 0.", Toast.LENGTH_SHORT).show()
                } else {
                    val duplicateIngredient = isDuplicateIngredient(name)
                    if (ingredient == null) {
                        if (duplicateIngredient != null) {
                            Toast.makeText(this, "Ingredient already exists. Editing...", Toast.LENGTH_SHORT).show()
                            showAddIngredientDialog(ingredientsListLayout, duplicateIngredient) { updatedIngredient ->
                                updateIngredientInList(ingredientsListLayout, updatedIngredient)
                            }
                        } else {
                            val newIngredient = Ingredient(name = name, quantity = quantity, unit = unit)
                            addIngredientToList(ingredientsListLayout, newIngredient)
                        }
                    } else {
                        ingredient.name = name
                        ingredient.quantity = quantity
                        ingredient.unit = unit
                        onIngredientUpdated?.invoke(ingredient)
                    }
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }
    // Add ingredient details to the ingredients layout
    private fun addIngredientToList(container: LinearLayout, ingredient: Ingredient) {
        if (!ingredientList.any { it.id == ingredient.id }) {
            ingredientList.add(ingredient)
        }

        val ingredientView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val quantityText = if (ingredient.quantity > 0) {
                if (ingredient.quantity % 1.0 == 0.0) ingredient.quantity.toInt().toString() else ingredient.quantity.toString()
            } else ""

            val ingredientText = listOfNotNull(quantityText.takeIf { it.isNotEmpty() }, ingredient.unit, ingredient.name).joinToString(" ")
            val ingredientTextView = TextView(this@EditRecipeActivity).apply {
                text = ingredientText
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val editIcon = ImageView(this@EditRecipeActivity).apply {
                setImageResource(R.drawable.ic_pencil)
                setColorFilter(resources.getColor(R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    width = 36
                    height = 36
                    marginStart = 8
                }
                setOnClickListener {
                    showAddIngredientDialog(container, ingredient) { updatedIngredient  ->
                        updateIngredientInList(container, updatedIngredient)
                    }
                }
            }

            setOnClickListener {
                showAddIngredientDialog(container, ingredient) { updatedIngredient  ->
                    updateIngredientInList(container, updatedIngredient)
                }
            }

            addView(ingredientTextView)
            addView(editIcon)
        }

        container.addView(ingredientView)
    }

    private fun updateIngredientInList(container: LinearLayout, updatedIngredient: Ingredient) {
        // Find the ingredient in the list by its ID
        val ingredientIndex = ingredientList.indexOfFirst { it.id == updatedIngredient.id }

        if (ingredientIndex != -1) {
            // Update the ingredient in the list
            ingredientList[ingredientIndex] = updatedIngredient

            // Update the corresponding view in the LinearLayout
            val childView = container.getChildAt(ingredientIndex) as LinearLayout
            val ingredientTextView = childView.getChildAt(0) as TextView

            // Format the updated ingredient text
            val quantityText = if (updatedIngredient.quantity > 0) {
                if (updatedIngredient.quantity % 1.0 == 0.0) updatedIngredient.quantity.toInt().toString() else updatedIngredient.quantity.toString()
            } else ""

            val ingredientText = listOfNotNull(
                quantityText.takeIf { it.isNotEmpty() },
                updatedIngredient.unit,
                updatedIngredient.name
            ).joinToString(" ")

            // Update the ingredient text
            ingredientTextView.text = ingredientText
        }
    }

    private fun isDuplicateIngredient(name: String): Ingredient? {
        return ingredientList.find { it.name.equals(name, ignoreCase = true) }
    }

    private fun removeIngredientById(container: LinearLayout, id: String) {
        val ingredient = ingredientList.find { it.id == id }
        ingredient?.let {
            ingredientList.remove(it)

            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i) as LinearLayout
                val textView = child.getChildAt(0) as TextView
                if (textView.text.contains(it.name)) {
                    container.removeViewAt(i)
                    break
                }
            }
        }
    }

    // Extract ingredients as a list of strings from the layout
    private fun getIngredientsFromLayout(): List<String> {
        return ingredientList.map { ingredient ->
            val quantityText = if (ingredient.quantity > 0) {
                if (ingredient.quantity == ingredient.quantity.toInt().toDouble()) { //quantity % 1.0 == 0.0
                    ingredient.quantity.toInt().toString()
                } else {
                    ingredient.quantity.toString()
                }
            } else ""

            "$quantityText ${ingredient.unit} ${ingredient.name}".trim()
        }
    }

    // Open the gallery to select an image
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Handle the result of image selection
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            Glide.with(this).load(selectedImageUri).into(findViewById(R.id.recipe_image))
        }
    }

    // Parse ingredient line into Ingredient object
    private fun parseIngredientLine(ingredientLine: String): Ingredient {
        // Simple parsing logic; you may need to adjust this based on your data
        val parts = ingredientLine.trim().split(" ")
        val quantity = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val unit = parts.getOrNull(1) ?: ""
        val name = parts.drop(2).joinToString(" ")
        return Ingredient(name = name, quantity = quantity, unit = unit)
    }
    // Save or update recipe in Firebase
    private fun saveRecipeToFirebase(
        name: String,
        ingredients: List<String>,
        instructions: String,
        time: Int?,
        calories: Int?
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to add a recipe.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        // Sprawdzenie czy jest zdjęcie
        if (selectedImageUri == null && originalImageUrl == null) {
            Toast.makeText(this, "Please select an image for the recipe.", Toast.LENGTH_SHORT).show()
            return
        }

        val recipe = hashMapOf(
            "id" to recipeId,
            "userId" to userId,
            "label" to name,
            "image" to "", // wypełnimy po pobraniu downloadUri
            "url" to null,
            "ingredientLines" to ingredients,
            "calories" to (calories?.toDouble() ?: 0.0),
            "totalTime" to (time?.toDouble() ?: 0.0),
            "dietLabels" to selectedDiets.toList(),
            "healthLabels" to selectedHealths.toList(),
            "cuisineType" to selectedCuisines.toList(),
            "mealType" to selectedMealTypes.toList(),
            "dishType" to selectedDishTypes.toList(),
            "totalNutrients" to null,
            "co2EmissionsClass" to null,
            "instructions" to instructions,
            "difficulty" to (selectedDifficulty ?: ""),
            "isFromFirebase" to true,
            "rating" to 0.0f,
            "ratingAmount" to 0
        )

        val storagePath = if (isFavoriteRecipe) {
            "recipes_images/favourites/$userId/$recipeId.jpg"
        } else {
            "recipes_images/users/$userId/$recipeId.jpg"
        }

        if (selectedImageUri != null) {
            val storageRef = storage.reference.child(storagePath)
            storageRef.putFile(selectedImageUri!!)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        recipe["image"] = downloadUri.toString()
                        when {
                            isEditing && isFavoriteRecipe -> updateFavoriteRecipe(userId, recipeId, recipe)
                            isEditing && !isFavoriteRecipe -> updateOriginalRecipe(recipeId, recipe)
                            else -> saveRecipeDocument(recipeId, recipe)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                }
        } else if (originalImageUrl != null) {
            recipe["image"] = originalImageUrl!!
            when {
                isEditing && isFavoriteRecipe -> updateFavoriteRecipe(userId, recipeId, recipe)
                isEditing && !isFavoriteRecipe -> updateOriginalRecipe(recipeId, recipe)
                else -> saveRecipeDocument(recipeId, recipe)
            }
        }
    }

    private fun updateOriginalRecipe(
        recipeId: String,
        recipe: HashMap<String, Any?>
    ) {
        db.collection("recipes").document(recipeId)
            .set(recipe)
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe updated successfully!", Toast.LENGTH_SHORT).show()
                val resultIntent = Intent()
                resultIntent.putExtra("recipeUpdated", true)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update recipe.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateFavoriteRecipe(
        userId: String,
        recipeId: String,
        recipe: HashMap<String, Any?>
    ) {
        val favoriteRecipeId = recipeId
        db.collection("users").document(userId)
            .collection("favoriteRecipes").document(favoriteRecipeId)
            .set(recipe)
            .addOnSuccessListener {
                Toast.makeText(this, "Favorite Recipe updated!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update favorite recipe.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveRecipeDocument(
        recipeId: String,
        recipe: HashMap<String, Any?>
    ) {
        db.collection("recipes").document(recipeId)
            .set(recipe)
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save recipe.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteRecipe(recipeId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to delete a recipe.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        val collectionPath = if (isFavoriteRecipe && isEditing) {
            "users/$userId/favoriteRecipes"
        } else if (isEditing) {
            "recipes"
        } else {
            return
        }

        db.collection(collectionPath).document(recipeId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Recipe deleted successfully!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK, Intent().putExtra("recipeDeleted", true))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete recipe.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString("recipeName", findViewById<TextInputEditText>(R.id.recipe_name).text.toString())
        outState.putString("instructions", findViewById<TextInputEditText>(R.id.recipe_instructions).text.toString())

        outState.putParcelable("selectedImageUri", selectedImageUri)

        outState.putParcelableArrayList("ingredientList", ArrayList(ingredientList))

        outState.putString("selectedDifficulty", selectedDifficulty)
        outState.putStringArrayList("selectedDiets", ArrayList(selectedDiets))
        outState.putStringArrayList("selectedHealths", ArrayList(selectedHealths))
        outState.putStringArrayList("selectedCuisines", ArrayList(selectedCuisines))
        outState.putStringArrayList("selectedMealTypes", ArrayList(selectedMealTypes))
        outState.putStringArrayList("selectedDishTypes", ArrayList(selectedDishTypes))

        val timeText = findViewById<TextView>(R.id.time_value).text.toString().replace(" min", "")
        val caloriesText = findViewById<TextView>(R.id.calories_value).text.toString().replace(" kcal", "")

        outState.putInt("totalTime", timeText.toIntOrNull() ?: 0)
        outState.putInt("calories", caloriesText.toIntOrNull() ?: 0)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        findViewById<TextInputEditText>(R.id.recipe_name).setText(savedInstanceState.getString("recipeName"))
        findViewById<TextInputEditText>(R.id.recipe_instructions).setText(savedInstanceState.getString("instructions"))

        selectedImageUri = savedInstanceState.getParcelable("selectedImageUri")
        selectedImageUri?.let {
            Glide.with(this).load(it).into(findViewById(R.id.recipe_image))
        }

        val restoredIngredients = savedInstanceState.getParcelableArrayList<Ingredient>("ingredientList")
        restoredIngredients?.let {
            ingredientList.clear()
            ingredientList.addAll(it)

            val ingredientsListLayout = findViewById<LinearLayout>(R.id.ingredients_list)
            ingredientsListLayout.removeAllViews()
            it.forEach { ingredient -> addIngredientToList(ingredientsListLayout, ingredient) }
        }

        selectedDifficulty = savedInstanceState.getString("selectedDifficulty")
        selectedDiets.clear()
        selectedDiets.addAll(savedInstanceState.getStringArrayList("selectedDiets") ?: emptyList())
        selectedHealths.clear()
        selectedHealths.addAll(savedInstanceState.getStringArrayList("selectedHealths") ?: emptyList())
        selectedCuisines.clear()
        selectedCuisines.addAll(savedInstanceState.getStringArrayList("selectedCuisines") ?: emptyList())
        selectedMealTypes.clear()
        selectedMealTypes.addAll(savedInstanceState.getStringArrayList("selectedMealTypes") ?: emptyList())
        selectedDishTypes.clear()
        selectedDishTypes.addAll(savedInstanceState.getStringArrayList("selectedDishTypes") ?: emptyList())

        val time = savedInstanceState.getInt("totalTime", 0)
        val calories = savedInstanceState.getInt("calories", 0)

        findViewById<TextView>(R.id.time_value).text = if (time > 0) "$time min" else getString(R.string.none)
        findViewById<TextView>(R.id.calories_value).text = if (calories > 0) "$calories kcal" else getString(R.string.none)

        findViewById<TextView>(R.id.difficulty_filter_value).text = selectedDifficulty ?: getString(R.string.none)
        findViewById<TextView>(R.id.diet_filter_value).text = formatSelectedText(selectedDiets)
        findViewById<TextView>(R.id.health_filter_value).text = formatSelectedText(selectedHealths)
        findViewById<TextView>(R.id.cuisine_filter_value).text = formatSelectedText(selectedCuisines)
        findViewById<TextView>(R.id.meal_type_value).text = formatSelectedText(selectedMealTypes)
        findViewById<TextView>(R.id.dish_type_value).text = formatSelectedText(selectedDishTypes)
    }
}
