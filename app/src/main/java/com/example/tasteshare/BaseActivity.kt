package com.example.tasteshare

import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

open class BaseActivity : AppCompatActivity() {

    fun setupBottomNavigationMenu(selectedItemId: Int) {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (bottomNavigationView != null) {
            bottomNavigationView.selectedItemId = selectedItemId

            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.action_search -> {
                        if (this !is SearchActivity) {
                            startActivity(Intent(this, SearchActivity::class.java))
                        }
                        true
                    }
                    R.id.action_cart -> {
                        if (this !is ShoppingListActivity) {
                            startActivity(Intent(this, ShoppingListActivity::class.java))
                        }
                        true
                    }
                    R.id.action_favorites -> {
                        if (this !is FavoritesResultsActivity) {
                            startActivity(Intent(this, FavoritesResultsActivity::class.java))
                        }
                        true
                    }
                    R.id.action_profile -> {
                        if (this !is ProfileActivity) {
                            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                            val profileIntent = Intent(this, ProfileActivity::class.java).apply {
                                putExtra("userId", currentUserId)
                            }
                            startActivity(profileIntent)
                        }
                        true
                    }
                    else -> false
                }
            }
        } else {
            Log.e("BaseActivity", "BottomNavigationView is null. Check your layout.")
        }
    }
}
