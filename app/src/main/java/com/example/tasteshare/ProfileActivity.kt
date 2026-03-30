package com.example.tasteshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import com.google.android.material.textview.MaterialTextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : BaseActivity(), MainPageRecipeAdapter.OnRecipeClickListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null
    private var alertDialog: androidx.appcompat.app.AlertDialog? = null

    private var currentRecipes = mutableListOf<Recipe>()
    private lateinit var mainPageRecipeSectionAdapter: MainPageRecipeSectionAdapter

    private val REQUEST_CODE_RECIPE_DETAIL = 2001

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val TAKE_PHOTO_REQUEST = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_activity)

        setupBottomNavigationMenu(R.id.action_profile)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        val db = Firebase.firestore

        val userId = intent.getStringExtra("userId") ?: auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "No user ID provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val usernameTextView = findViewById<MaterialTextView>(R.id.username)
        val emailTextView = findViewById<MaterialTextView>(R.id.user_email)
        val avatarImageView = findViewById<ImageView>(R.id.user_avatar)
        val changePhotoButton = findViewById<MaterialButton>(R.id.change_photo_button)
        val logoutButton = findViewById<MaterialButton>(R.id.logout_button)

        val currentUser = auth.currentUser

        // Pobieranie danych użytkownika
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val username = document.getString("username") ?: "Unknown User"
                    val email = document.getString("email") ?: "Unknown Email"
                    val avatarUrl = document.getString("avatarUrl")

                    usernameTextView.text = username
                    emailTextView.text = obfuscateEmail(email)
                    avatarUrl?.let { url ->
                        Glide.with(this).load(url).into(avatarImageView)
                    }
                } else {
                    Toast.makeText(this, "No such document found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user data.", Toast.LENGTH_SHORT).show()
            }

        if (userId == auth.currentUser?.uid) {
            emailTextView.visibility = View.VISIBLE
            logoutButton.visibility = View.VISIBLE

            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_pencil)
            drawable?.setTint(ContextCompat.getColor(this, R.color.text_primary))
            usernameTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
            usernameTextView.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.drawable_padding)

            val hideButtonHandler = Handler(Looper.getMainLooper())
            val hideButtonRunnable = Runnable {
                changePhotoButton.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { changePhotoButton.visibility = View.GONE }
                    .start()
            }

            usernameTextView.setOnClickListener {
                showChangeUsernameDialog { newUsername ->
                    val userId = currentUser?.uid ?: return@showChangeUsernameDialog
                    db.collection("users").document(userId).update("username", newUsername)
                        .addOnSuccessListener {
                            usernameTextView.text = newUsername
                            Toast.makeText(this, "Username updated successfully!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to update username.", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            avatarImageView.setOnClickListener {
                if (changePhotoButton.visibility == View.VISIBLE) {
                    changePhotoButton.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { changePhotoButton.visibility = View.GONE }
                        .start()

                    hideButtonHandler.removeCallbacks(hideButtonRunnable)
                } else {
                    changePhotoButton.visibility = View.VISIBLE
                    changePhotoButton.alpha = 0f
                    changePhotoButton.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()

                    hideButtonHandler.postDelayed(hideButtonRunnable, 7000)
                }
            }

            changePhotoButton.setOnClickListener {
                hideButtonHandler.removeCallbacks(hideButtonRunnable)
                hideButtonHandler.postDelayed(hideButtonRunnable, 7000)
            }

            // Obsługa kliknięcia przycisku zmiany zdjęcia
            changePhotoButton.setOnClickListener {
                showPhotoOptionsDialog()
            }

            // Wylogowanie
            logoutButton.setOnClickListener {
                auth.signOut()
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        fetchUserRecipesFromFirebase(userId)
    }

    private fun fetchUserRecipesFromFirebase(userId: String) {
        val db = FirebaseFirestore.getInstance()
        val recipesSectionRecyclerView = findViewById<RecyclerView>(R.id.user_recipes_section)

        // Ustawienie layout managera dla RecyclerView
        recipesSectionRecyclerView.layoutManager = LinearLayoutManager(this)

        db.collection("recipes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                val recipes = result.documents.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)
                }.toMutableList()

                currentRecipes = recipes

                if (recipes.isNotEmpty()) {
                    val sections = listOf(
                        MainPageRecipeSection(
                            title = "User Recipes",
                            recipes = currentRecipes.map { MainPageRecipe(it) }.toMutableList()
                        )
                    )
                    mainPageRecipeSectionAdapter = MainPageRecipeSectionAdapter(sections, this)
                    recipesSectionRecyclerView.adapter =  mainPageRecipeSectionAdapter
                    recipesSectionRecyclerView.visibility = View.VISIBLE
                } else {
                    recipesSectionRecyclerView.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user recipes.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onRecipeClicked(recipe: Recipe) {
        val intent = Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra("recipe", recipe)
        }
        startActivityForResult(intent, REQUEST_CODE_RECIPE_DETAIL)
    }

    private fun showChangeUsernameDialog(onUsernameChanged: (String) -> Unit) {
        // Utworzenie widoku dialogu
        val dialogView = layoutInflater.inflate(R.layout.dialog_single_text_input, null)
        val newUsernameField = dialogView.findViewById<TextInputEditText>(R.id.input_text_field)
        val newUsernameLayout = dialogView.findViewById<TextInputLayout>(R.id.text_input_layout)

        newUsernameField.hint = getString(R.string.new_username_hint)

        val dialogBuilder = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
        dialogBuilder.setView(dialogView)
            .setTitle("Change Username")
            .setPositiveButton("Save") { dialog, _ ->
                val newUsername = newUsernameField.text.toString().trim()
                if (newUsername.isNotEmpty()) {
                    onUsernameChanged(newUsername)
                    dialog.dismiss()
                } else {
                    newUsernameLayout.error = getString(R.string.invalid_username)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()

        newUsernameField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) newUsernameLayout.error = null
        }
    }

    // Funkcja do zniekształcenia e-maila
    private fun obfuscateEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 3) {
            return email
        }
        val prefix = email.substring(0, 3)
        val domain = email.substring(atIndex)
        return "$prefix***$domain"
    }

    // Wyświetlenie dialogu z opcjami wyboru zdjęcia
    private fun showPhotoOptionsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_choice, null)
        val optionsListView = dialogView.findViewById<ListView>(R.id.options_list)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)

        dialogTitle.text = getString(R.string.dialog_title_profile_photo_change_profile_picture)
        val options = arrayOf(getString(R.string.dialog_option_profile_photo_choose_photo_from_gallery), getString(R.string.dialog_option_profile_photo_delete_photo))

        optionsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)

        optionsListView.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> openGallery()
                1 -> deleteProfilePicture()
            }
            alertDialog?.dismiss()
        }

            alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
                .setView(dialogView)
                .create()
        alertDialog?.show()
    }


    // Otwieranie galerii
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Usunięcie zdjęcia profilowego
    private fun deleteProfilePicture() {
        val userId = auth.currentUser?.uid ?: return
        val db = Firebase.firestore
        val storageRef = storage.reference.child("profile_images/$userId/profile.jpg")

        storageRef.delete().addOnSuccessListener {
            db.collection("users").document(userId).update("avatarUrl", null)
            findViewById<ImageView>(R.id.user_avatar).setImageResource(R.drawable.profile)
            Toast.makeText(this, "Profile picture removed.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to remove profile picture.", Toast.LENGTH_SHORT).show()
        }
    }

    // Obsługa wyników wybranych zdjęć
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    selectedImageUri = data?.data
                    confirmAndUploadImage()
                }
                TAKE_PHOTO_REQUEST -> {
                    confirmAndUploadImage()
                }
            }

            val recipeDeleted = data?.getBooleanExtra("recipeDeleted", false) ?: false
            val recipeUpdated = data?.getBooleanExtra("recipeUpdated", false) ?: false

            if (recipeDeleted || recipeUpdated) {
                val userId = auth.currentUser?.uid ?: return
                val db = FirebaseFirestore.getInstance()
                db.collection("recipes")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { result ->
                        val newRecipes = result.documents.mapNotNull { doc ->
                            doc.toObject(Recipe::class.java)
                        }

                        val oldRecipesMap = currentRecipes.associateBy { it.id }.toMutableMap()
                        val newRecipesMap = newRecipes.associateBy { it.id }

                        newRecipesMap.forEach { (id, newRecipe) ->
                            val oldRecipe = oldRecipesMap[id]
                            if (oldRecipe == null) {
                                oldRecipesMap[id] = newRecipe
                            } else if (oldRecipe != newRecipe) {
                                oldRecipesMap[id] = newRecipe
                            }
                        }

                        val idsInNewSet = newRecipesMap.keys
                        val iterator = oldRecipesMap.keys.iterator()
                        while (iterator.hasNext()) {
                            val id = iterator.next()
                            if (!idsInNewSet.contains(id)) {
                                iterator.remove()
                            }
                        }

                        currentRecipes = oldRecipesMap.values.toMutableList()

                        val recipesSectionRecyclerView = findViewById<RecyclerView>(R.id.user_recipes_section)
                        if (currentRecipes.isNotEmpty()) {
                            val sections = listOf(
                                MainPageRecipeSection(
                                    title = "User Recipes",
                                    recipes = currentRecipes.map { MainPageRecipe(it) }.toMutableList()
                                )
                            )
                            mainPageRecipeSectionAdapter = MainPageRecipeSectionAdapter(sections, this)
                            recipesSectionRecyclerView.adapter = mainPageRecipeSectionAdapter
                            recipesSectionRecyclerView.visibility = View.VISIBLE
                        } else {
                            recipesSectionRecyclerView.visibility = View.GONE
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to update user recipes.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }


    // Potwierdzenie przed zapisaniem zdjęcia
    private fun confirmAndUploadImage() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_with_message, null)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_yes)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_no)
        val title = dialogView.findViewById<TextView>(R.id.dialog_title)
        val message = dialogView.findViewById<TextView>(R.id.dialog_message)

        title.text = getString(R.string.dialog_title_profile_photo_change_profile_picture)
        message.text = getString(R.string.dialog_message_profile_photo_change_confirm)

        val alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .create()

        buttonYes.setOnClickListener {
            uploadImageToFirebaseStorage()
            alertDialog.dismiss()
        }

        buttonNo.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    // Wgrywanie zdjęcia do Firebase Storage
    private fun uploadImageToFirebaseStorage() {
        val userId = auth.currentUser?.uid ?: return
        val fileName = "profile_images/$userId/profile.jpg"
        val storageRef = storage.reference.child(fileName)

        selectedImageUri?.let { uri ->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        updateUserProfileImage(downloadUri.toString())
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserProfileImage(imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        val db = Firebase.firestore

        db.collection("users").document(userId).update("avatarUrl", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                Glide.with(this).load(imageUrl).into(findViewById(R.id.user_avatar))
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile picture.", Toast.LENGTH_SHORT).show()
            }
    }
}
