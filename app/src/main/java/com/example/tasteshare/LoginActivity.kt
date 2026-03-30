package com.example.tasteshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    companion object {
        private const val REQ_ONE_TAP = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        setContentView(R.layout.login_activity)

        val emailField = findViewById<TextInputEditText>(R.id.login_email)
        val passwordField = findViewById<TextInputEditText>(R.id.login_password)
        val loginButton = findViewById<MaterialButton>(R.id.login_button)
        val createAccount = findViewById<MaterialTextView>(R.id.create_account)
        val forgotPassword = findViewById<MaterialTextView>(R.id.forgot_password)
        val googleSignInButton = findViewById<MaterialButton>(R.id.google_sign_in_button)


        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId("693723734535-n6ru2tokhlmv9l3jiitvusmttq0p7tt7.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        googleSignInButton.setOnClickListener {
            oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener { result ->
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender,
                        REQ_ONE_TAP,
                        null, 0, 0, 0
                    )
                }
                .addOnFailureListener { e ->
                    Log.e("LoginActivity", "Google Sign-In Failed", e)
                }
        }

        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            emailField.error = null
            passwordField.error = null

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            if (auth.currentUser?.isEmailVerified == true) {
                                val intent = Intent(this, SearchActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this, "Please verify your email address.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            emailField.error = "Invalid email or password"
                            passwordField.error = "Invalid email or password"
                        }
                    }
            } else {
                if (email.isEmpty()) {
                    emailField.error = "Email cannot be empty"
                }
                if (password.isEmpty()) {
                    passwordField.error = "Password cannot be empty"
                }
            }
        }

        createAccount.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        forgotPassword.setOnClickListener {
            val email = emailField.text.toString().trim()

            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Reset password email sent to $email", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error in sending reset password email.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ONE_TAP) {
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                checkUserInFirestore(user)
                                startActivity(Intent(this, SearchActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Log.d("LoginActivity", "No ID token!")
                }
            } catch (e: ApiException) {
                Log.e("LoginActivity", "Google sign in failed", e)
            }
        }
    }

    private fun checkUserInFirestore(user: FirebaseUser?) {
        val db = Firebase.firestore
        user?.let {
            val userRef = db.collection("users").document(it.uid)
            userRef.get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        val userMap = hashMapOf(
                            "username" to (it.displayName ?: "Unknown"),
                            "email" to it.email
                        )
                        userRef.set(userMap)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("LoginActivity", "Error checking user in Firestore", e)
                }
        }
    }
}
