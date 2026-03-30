package com.example.tasteshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.util.Patterns

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.register_activity)

        auth = FirebaseAuth.getInstance()

        val db = Firebase.firestore

        val usernameField = findViewById<TextInputEditText>(R.id.register_username)
        val emailField = findViewById<TextInputEditText>(R.id.register_email)
        val passwordField = findViewById<TextInputEditText>(R.id.register_password)
        val repeatPasswordField = findViewById<TextInputEditText>(R.id.register_password_repeat)
        val registerButton = findViewById<MaterialButton>(R.id.register_button)
        val backToLoginButton = findViewById<MaterialTextView>(R.id.back_to_login)

        registerButton.setOnClickListener {
            val username = usernameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val repeatPassword = repeatPasswordField.text.toString().trim()

            usernameField.error = null
            emailField.error = null
            passwordField.error = null
            repeatPasswordField.error = null

            var isValid = true

            if (username.isEmpty()) {
                usernameField.error = "Username is required."
                isValid = false
            }
            if (email.isEmpty()) {
                emailField.error = "Email is required."
                isValid = false
            }
            if (password.isEmpty()) {
                passwordField.error = "Password is required."
                isValid = false
            }
            if (repeatPassword.isEmpty()) {
                repeatPasswordField.error = "Please repeat the password."
                isValid = false
            }

            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailField.error = "Please enter a valid email address."
                isValid = false
            }

            if (password.isNotEmpty() && password.length < 8) {
                passwordField.error = "Password must be at least 8 characters."
                isValid = false
            }

            if (password != repeatPassword) {
                repeatPasswordField.error = "Passwords do not match."
                isValid = false
            }


            if (isValid) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            val userId = user?.uid

                            if (userId != null) {
                                val userMap = hashMapOf(
                                    "username" to username,
                                    "email" to email
                                )

                                // Dodawanie dokumentu z wygenerowanym ID do kolekcji "users"
                                db.collection("users").document(userId).set(userMap)
                                    .addOnSuccessListener {
                                        user.sendEmailVerification().addOnCompleteListener { verifyTask ->
                                            if (verifyTask.isSuccessful) {
                                                Toast.makeText(this, "Verification email sent to $email", Toast.LENGTH_SHORT).show()

                                                val intent = Intent(this, LoginActivity::class.java)
                                                startActivity(intent)
                                                finish()
                                            } else {
                                                Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w("RegisterActivity", "Error adding document", e)
                                        Toast.makeText(this, "Failed to save user data.", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(this, "User ID is null.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        backToLoginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
