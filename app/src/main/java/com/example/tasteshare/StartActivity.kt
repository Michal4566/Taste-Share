package com.example.tasteshare

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class StartActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            !viewModel.isReady.value
        }

        splashScreen.setOnExitAnimationListener { screen ->
            val iconView = screen.iconView ?: run {
                screen.remove()
                return@setOnExitAnimationListener
            }

            val zoomX = ObjectAnimator.ofFloat(
                iconView,
                View.SCALE_X,
                0.6f,
                0.0f
            ).apply {
                interpolator = OvershootInterpolator()
                duration = 500L
                doOnEnd { screen.remove() }
            }

            val zoomY = ObjectAnimator.ofFloat(
                iconView,
                View.SCALE_Y,
                0.6f,
                0.0f
            ).apply {
                interpolator = OvershootInterpolator()
                duration = 500L
                doOnEnd { screen.remove() }
            }

            zoomX.start()
            zoomY.start()

            zoomY.doOnEnd {
                screen.remove()
                navigateToNextScreen()
            }
        }

        auth = FirebaseAuth.getInstance()
    }

    private fun navigateToNextScreen() {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        } else {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
        finish()
    }
}
