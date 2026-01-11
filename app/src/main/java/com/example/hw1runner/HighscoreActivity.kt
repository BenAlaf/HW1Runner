package com.example.hw1runner

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.hw1runner.databinding.ActivityHighscoreBinding

class HighscoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHighscoreBinding
    private lateinit var scoreTableFragment: ScoreTableFragment
    private lateinit var scoreMapFragment: ScoreMapFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHighscoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupClickListeners()
    }

    private fun setupFragments() {
        // Create fragments
        scoreTableFragment = ScoreTableFragment.newInstance()
        scoreMapFragment = ScoreMapFragment.newInstance()

        // Add fragments to containers
        supportFragmentManager.beginTransaction()
            .replace(R.id.scoreTableContainer, scoreTableFragment)
            .replace(R.id.mapContainer, scoreMapFragment)
            .commit()

        // Setup communication between fragments
        scoreTableFragment.setOnScoreSelectedListener { entry ->
            scoreMapFragment.showScoreLocation(entry)
        }
    }

    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener {
            animateButtonPress(it) {
                finish()
            }
        }
    }

    private fun animateButtonPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
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
}
