package com.example.hw1runner

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.hw1runner.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val EXTRA_GAME_MODE = "game_mode"
        const val EXTRA_SENSOR_MODE = "sensor_mode"

        const val MODE_BUTTON_SLOW = 0
        const val MODE_BUTTON_FAST = 1
        const val MODE_SENSOR = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("hw1runner_prefs", MODE_PRIVATE)

        setupUI()
        setupClickListeners()
        startBackgroundAnimations()
    }

    override fun onResume() {
        super.onResume()
        // Update high score display when returning from game
        val highScore = prefs.getInt("high_score", 0)
        binding.highScoreText.text = highScore.toString()
    }

    private fun setupUI() {
        val highScore = prefs.getInt("high_score", 0)
        binding.highScoreText.text = highScore.toString()

        // Animate title on entry
        animateTitleEntry()
    }

    private fun animateTitleEntry() {
        // Fade in and slide up animation for the whole content
        binding.root.getChildAt(2)?.let { content ->
            content.alpha = 0f
            content.translationY = 50f
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(200)
                .start()
        }
    }

    private fun setupClickListeners() {
        // Button Slow Mode
        binding.btnButtonSlow.setOnClickListener {
            animateButtonPress(it) {
                startGame(MODE_BUTTON_SLOW)
            }
        }

        // Button Fast Mode
        binding.btnButtonFast.setOnClickListener {
            animateButtonPress(it) {
                startGame(MODE_BUTTON_FAST)
            }
        }

        // Sensor Mode
        binding.btnSensorMode.setOnClickListener {
            animateButtonPress(it) {
                startGame(MODE_SENSOR)
            }
        }

        // Highscores
        binding.btnHighscores.setOnClickListener {
            animateButtonPress(it) {
                openHighscores()
            }
        }
    }

    private fun animateButtonPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .withEndAction {
                        onComplete()
                    }
                    .start()
            }
            .start()
    }

    private fun startGame(mode: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_GAME_MODE, mode)
            putExtra(EXTRA_SENSOR_MODE, mode == MODE_SENSOR)
        }
        startActivity(intent)
    }

    private fun openHighscores() {
        val intent = Intent(this, HighscoreActivity::class.java)
        startActivity(intent)
    }

    private fun startBackgroundAnimations() {
        // Animate the background lines
        animateBackgroundLine(binding.bgLine1, -20f, 20f)
        animateBackgroundLine(binding.bgLine2, 20f, -20f)

        // Pulse animation for title glow effect
        pulseHighScore()
    }

    private fun animateBackgroundLine(line: View, fromX: Float, toX: Float) {
        ObjectAnimator.ofFloat(line, "translationX", fromX, toX).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun pulseHighScore() {
        binding.highScoreText.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(1000)
            .withEndAction {
                binding.highScoreText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(1000)
                    .withEndAction {
                        pulseHighScore()
                    }
                    .start()
            }
            .start()
    }
}
