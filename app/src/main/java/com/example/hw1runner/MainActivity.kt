package com.example.hw1runner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hw1runner.databinding.ActivityMainBinding
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private lateinit var gameArea: FrameLayout
    private lateinit var roadLinesContainer: FrameLayout
    private lateinit var player: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private var spawnRunnable: Runnable? = null
    private var roadAnimRunnable: Runnable? = null
    private var difficultyRunnable: Runnable? = null

    // Road animation
    private val roadDashes = mutableListOf<View>()
    private var roadAnimOffset = 0f

    // Lanes
    private var lanesX = intArrayOf()
    private var laneIndex = 1

    // Game state
    private var score = 0
    private var highScore = 0
    private var lives = 3
    private var running = false
    private var invulnerable = false
    private var gameOverShown = false

    // Difficulty scaling
    private var currentSpawnInterval = 1100L
    private var currentObstacleDuration = 2000L
    private val minSpawnInterval = 500L
    private val minObstacleDuration = 1000L

    // Obstacle types
    private enum class ObstacleType { CAR, TRUCK }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("hw1runner_prefs", MODE_PRIVATE)
        highScore = prefs.getInt("high_score", 0)

        gameArea = binding.gameArea
        roadLinesContainer = binding.roadLinesContainer

        // Setup controls with press animations
        setupControlButton(binding.leftBtn) { movePlayer(-1) }
        setupControlButton(binding.rightBtn) { movePlayer(+1) }

        // Play again button
        binding.playAgainBtn.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            hideGameOver()
            resetGame()
        }

        // Wait for layout
        gameArea.post {
            setupLanes()
            setupPlayer()
            createRoadDashes()
            updateHud()
            startGame()
        }
    }

    override fun onDestroy() {
        stopGame()
        super.onDestroy()
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun setupControlButton(button: View, action: () -> Unit) {
        button.setOnClickListener {
            // Press animation
            it.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(50)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                }.start()
            action()
        }
    }

    private fun setupLanes() {
        val w = gameArea.width
        val laneW = w / 3
        lanesX = intArrayOf(
            laneW / 2,
            laneW + laneW / 2,
            2 * laneW + laneW / 2
        )

        // Update lane separator positions
        binding.laneSep1.translationX = (lanesX[0] + lanesX[1]) / 2f - gameArea.width / 2f
        binding.laneSep2.translationX = (lanesX[1] + lanesX[2]) / 2f - gameArea.width / 2f
    }

    private fun setupPlayer() {
        player = ImageView(this).apply {
            setImageResource(R.drawable.ic_car)
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(84))
            tag = "player"
            elevation = 8f
        }
        gameArea.addView(player)

        // Place near bottom
        player.translationY = (gameArea.height - dp(140)).toFloat()
        laneIndex = 1
        placePlayerInLane(laneIndex, animate = false)

        // Add subtle idle animation
        player.animate()
            .translationYBy(4f)
            .setDuration(800)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                animatePlayerIdle()
            }.start()
    }

    private fun animatePlayerIdle() {
        if (!running) return
        player.animate()
            .translationYBy(-4f)
            .setDuration(800)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                player.animate()
                    .translationYBy(4f)
                    .setDuration(800)
                    .setInterpolator(LinearInterpolator())
                    .withEndAction { animatePlayerIdle() }
                    .start()
            }.start()
    }

    private fun createRoadDashes() {
        roadDashes.clear()
        roadLinesContainer.removeAllViews()

        val dashHeight = dp(50)
        val dashGap = dp(60)
        val totalHeight = gameArea.height + dashHeight + dashGap
        val numDashes = (totalHeight / (dashHeight + dashGap)) + 2

        // Create dashes for left and right lane separators
        val leftX = (lanesX[0] + lanesX[1]) / 2f - dp(3)
        val rightX = (lanesX[1] + lanesX[2]) / 2f - dp(3)

        for (i in 0 until numDashes) {
            // Left lane dash
            val leftDash = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(6), dashHeight)
                setBackgroundResource(R.drawable.ic_road_dash)
                translationX = leftX
                translationY = (i * (dashHeight + dashGap)).toFloat() - dashHeight
                alpha = 0.7f
            }
            roadLinesContainer.addView(leftDash)
            roadDashes.add(leftDash)

            // Right lane dash
            val rightDash = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(6), dashHeight)
                setBackgroundResource(R.drawable.ic_road_dash)
                translationX = rightX
                translationY = (i * (dashHeight + dashGap)).toFloat() - dashHeight
                alpha = 0.7f
            }
            roadLinesContainer.addView(rightDash)
            roadDashes.add(rightDash)
        }
    }

    private fun startRoadAnimation() {
        val dashHeight = dp(50)
        val dashGap = dp(60)
        val cycleDistance = (dashHeight + dashGap).toFloat()

        roadAnimRunnable = object : Runnable {
            override fun run() {
                if (!running) return

                // Move based on current speed (tied to obstacle duration)
                val speedFactor = 2000f / currentObstacleDuration
                roadAnimOffset += 8f * speedFactor

                if (roadAnimOffset >= cycleDistance) {
                    roadAnimOffset = 0f
                }

                for ((index, dash) in roadDashes.withIndex()) {
                    val baseY = ((index / 2) * cycleDistance) - dashHeight
                    dash.translationY = baseY + roadAnimOffset
                }

                handler.postDelayed(this, 16) // ~60fps
            }
        }
        handler.post(roadAnimRunnable!!)
    }

    private fun placePlayerInLane(index: Int, animate: Boolean) {
        val xCenter = lanesX[index].toFloat()
        val w = player.layoutParams.width.toFloat()
        val targetX = xCenter - (w / 2f)

        if (!animate) {
            player.translationX = targetX
        } else {
            player.animate()
                .translationX(targetX)
                .setDuration(100)
                .setInterpolator(AccelerateInterpolator(0.5f))
                .start()
        }
    }

    private fun movePlayer(delta: Int) {
        if (!running || gameOverShown) return
        laneIndex = (laneIndex + delta).coerceIn(0, 2)
        placePlayerInLane(laneIndex, animate = true)
    }

    private fun startGame() {
        running = true
        gameOverShown = false
        currentSpawnInterval = 1100L
        currentObstacleDuration = 2000L

        startSpawning()
        startRoadAnimation()
        startDifficultyScaling()
        animatePlayerIdle()
    }

    private fun stopGame() {
        running = false
        spawnRunnable?.let { handler.removeCallbacks(it) }
        roadAnimRunnable?.let { handler.removeCallbacks(it) }
        difficultyRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startSpawning() {
        spawnRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                spawnObstacle()
                handler.postDelayed(this, currentSpawnInterval)
            }
        }
        handler.postDelayed(spawnRunnable!!, 500) // Small initial delay
    }

    private fun startDifficultyScaling() {
        difficultyRunnable = object : Runnable {
            override fun run() {
                if (!running) return

                // Gradually increase difficulty
                currentSpawnInterval = max(minSpawnInterval, currentSpawnInterval - 30)
                currentObstacleDuration = max(minObstacleDuration, currentObstacleDuration - 40)

                handler.postDelayed(this, 3000) // Increase difficulty every 3 seconds
            }
        }
        handler.postDelayed(difficultyRunnable!!, 5000) // Start scaling after 5 seconds
    }

    private fun spawnObstacle() {
        // Randomly choose car or truck
        val type = if (Random.nextBoolean()) ObstacleType.CAR else ObstacleType.TRUCK

        val (width, height, drawableRes) = when (type) {
            ObstacleType.CAR -> Triple(dp(56), dp(84), R.drawable.ic_obstacle_car)
            ObstacleType.TRUCK -> Triple(dp(64), dp(100), R.drawable.ic_obstacle_truck)
        }

        val obstacle = ImageView(this).apply {
            setImageResource(drawableRes)
            layoutParams = FrameLayout.LayoutParams(width, height)
            tag = "obstacle"
            elevation = 4f
        }
        gameArea.addView(obstacle)

        val lane = Random.nextInt(0, 3)
        obstacle.translationX = lanesX[lane] - (width / 2f)
        obstacle.translationY = (-height - dp(20)).toFloat()

        val anim = ValueAnimator.ofFloat(
            obstacle.translationY,
            (gameArea.height + height).toFloat()
        ).apply {
            duration = currentObstacleDuration
            interpolator = LinearInterpolator()

            addUpdateListener {
                obstacle.translationY = it.animatedValue as Float
                if (running && !invulnerable && intersects(player, obstacle)) {
                    onCrash(obstacle)
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (obstacle.parent != null) {
                        gameArea.removeView(obstacle)
                    }
                    if (running) {
                        score += 1
                        updateScoreDisplay()
                    }
                }
            })
        }

        anim.start()
    }

    private fun onCrash(obstacle: View) {
        if (obstacle.parent != null) {
            // Explosion effect - scale up and fade out
            obstacle.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    if (obstacle.parent != null) {
                        gameArea.removeView(obstacle)
                    }
                }.start()
        }

        lives -= 1
        updateHud()

        // Show crash notification
        Toast.makeText(this, "Crash! Lives left: $lives", Toast.LENGTH_SHORT).show()

        // Screen shake
        shakeScreen()

        // Red flash
        showCrashFlash()

        // Vibration
        vibrate(200)

        // Invulnerability window
        invulnerable = true
        flashPlayer()

        handler.postDelayed({
            invulnerable = false
            player.alpha = 1f
        }, 1000)

        if (lives <= 0) {
            gameOver()
        }
    }

    private fun shakeScreen() {
        val container = binding.gameContainer
        val shake = ObjectAnimator.ofFloat(
            container, "translationX",
            0f, 25f, -25f, 20f, -20f, 15f, -15f, 10f, -10f, 5f, -5f, 0f
        ).apply {
            duration = 400
            interpolator = LinearInterpolator()
        }
        shake.start()
    }

    private fun showCrashFlash() {
        binding.crashFlash.apply {
            visibility = View.VISIBLE
            alpha = 0.8f
            animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    visibility = View.GONE
                }.start()
        }
    }

    private fun flashPlayer() {
        val flashRunnable = object : Runnable {
            var count = 0
            override fun run() {
                if (count >= 8 || !invulnerable) {
                    player.alpha = 1f
                    return
                }
                player.alpha = if (player.alpha > 0.5f) 0.3f else 1f
                count++
                handler.postDelayed(this, 120)
            }
        }
        handler.post(flashRunnable)
    }

    private fun gameOver() {
        stopGame()
        gameOverShown = true

        // Check for high score
        val isNewHighScore = score > highScore
        if (isNewHighScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }

        // Show game over overlay with animation
        binding.finalScoreText.text = "Score: $score"
        binding.bestScoreText.text = "Best: $highScore"
        binding.newHighScoreText.visibility = if (isNewHighScore) View.VISIBLE else View.GONE

        binding.gameOverOverlay.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(400)
                .start()
        }

        // Animate the content
        val content = binding.gameOverOverlay.getChildAt(0)
        content.scaleX = 0.8f
        content.scaleY = 0.8f
        content.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setStartDelay(100)
            .start()
    }

    private fun hideGameOver() {
        binding.gameOverOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.gameOverOverlay.visibility = View.GONE
            }.start()
    }

    private fun resetGame() {
        // Clear all obstacles
        val toRemove = mutableListOf<View>()
        for (i in 0 until gameArea.childCount) {
            val v = gameArea.getChildAt(i)
            if (v.tag == "obstacle") toRemove.add(v)
        }
        toRemove.forEach { gameArea.removeView(it) }

        // Reset state
        lives = 3
        score = 0
        laneIndex = 1
        placePlayerInLane(laneIndex, animate = true)
        player.alpha = 1f
        updateHud()

        // Start fresh
        startGame()
    }

    private fun updateHud() {
        updateScoreDisplay()
        binding.highScoreText.text = highScore.toString()
        binding.heart1.setImageResource(if (lives >= 1) R.drawable.ic_heart_full else R.drawable.ic_heart_empty)
        binding.heart2.setImageResource(if (lives >= 2) R.drawable.ic_heart_full else R.drawable.ic_heart_empty)
        binding.heart3.setImageResource(if (lives >= 3) R.drawable.ic_heart_full else R.drawable.ic_heart_empty)

        // Animate heart loss
        if (lives < 3) animateHeartLoss(binding.heart3)
        if (lives < 2) animateHeartLoss(binding.heart2)
        if (lives < 1) animateHeartLoss(binding.heart1)
    }

    private fun updateScoreDisplay() {
        binding.scoreText.text = score.toString()

        // Pulse animation on score change
        binding.scoreText.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(80)
            .withEndAction {
                binding.scoreText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .start()
            }.start()
    }

    private fun animateHeartLoss(heart: ImageView) {
        heart.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(100)
            .withEndAction {
                heart.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }.start()
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    private fun intersects(a: View, b: View): Boolean {
        val ra = Rect()
        val rb = Rect()

        val aLoc = IntArray(2)
        val bLoc = IntArray(2)
        a.getLocationOnScreen(aLoc)
        b.getLocationOnScreen(bLoc)

        // Use slightly smaller hitboxes for better feel
        val shrink = dp(8)
        ra.set(
            aLoc[0] + shrink,
            aLoc[1] + shrink,
            aLoc[0] + a.width - shrink,
            aLoc[1] + a.height - shrink
        )
        rb.set(
            bLoc[0] + shrink,
            bLoc[1] + shrink,
            bLoc[0] + b.width - shrink,
            bLoc[1] + b.height - shrink
        )

        return Rect.intersects(ra, rb)
    }
}
