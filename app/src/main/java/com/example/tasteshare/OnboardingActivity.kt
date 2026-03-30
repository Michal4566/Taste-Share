package com.example.tasteshare

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var nextButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.view_pager)
        nextButton = findViewById(R.id.next_button)

        val slides = listOf(
            OnboardingSlide(R.drawable.onboarding_welcome, getString(R.string.onboarding_slide_welcome)),
            OnboardingSlide(R.drawable.onboarding_searching, getString(R.string.onboarding_slide_search)),
            OnboardingSlide(R.drawable.onboarding_cooking, getString(R.string.onboarding_slide_last))
        )

        val adapter = OnboardingAdapter(slides)
        viewPager.adapter = adapter

        nextButton.setOnClickListener {
            if (viewPager.currentItem < slides.size - 1) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun finishOnboarding() {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("is_first_run", false)
            apply()
        }

        startActivity(Intent(this, SearchActivity::class.java))
        finish()
    }
}
