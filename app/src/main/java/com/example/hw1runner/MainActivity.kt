package com.example.hw1runner

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hw1runner.data.AppDatabase
import com.example.hw1runner.data.HighscoreEntry
import com.example.hw1runner.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private lateinit var gameArea: FrameLayout
    private lateinit var roadLinesContainer: FrameLayout
    private lateinit var player: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private var spawnRunnable: Runnable? = null
    private var coinSpawnRunnable: Runnable? = null
    private var roadAnimRunnable: Runnable? = null
    private var difficultyRunnable: Runnable? = null
    private var odometerRunnable: Runnable? = null

    // Road animation
    private val roadDashes = mutableListOf<View>()
    private var roadAnimOffset = 0f

    // Lanes - now 5 lanes instead of 3
    private var lanesX = intArrayOf()
    private var laneIndex = 2  // Start in middle lane (index 2 of 5)
    private val laneCount = 5

    // Game state
    private var score = 0
    private var highScore = 0
    private var lives = 3
    private var coins = 0
    private var distance = 0f  // in meters
    private var running = false
    private var invulnerable = false
    private var gameOverShown = false

    // Game mode settings
    private var gameMode = MenuActivity.MODE_BUTTON_SLOW
    private var useSensorControls = false

    // Base difficulty values (will be adjusted based on game mode)
    private var baseSpawnInterval = 1100L
    private var baseObstacleDuration = 2000L

    // Current difficulty values
    private var currentSpawnInterval = 1100L
    private var currentObstacleDuration = 2000L
    private var minSpawnInterval = 500L
    private var minObstacleDuration = 1000L

    // Coin spawn settings
    private var coinSpawnInterval = 2000L

    // Sensor controls
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastSensorLaneChange = 0L
    private val sensorLaneChangeDelay = 300L  // Minimum delay between sensor lane changes

    // Tilt thresholds for steering
    private val tiltThreshold = 2.5f  // Minimum tilt to trigger lane change

    // Tilt-for-speed feature (bonus)
    private var tiltSpeedMultiplier = 1.0f
    private val minTiltSpeedMultiplier = 0.6f   // Tilt back = slow down to 60%
    private val maxTiltSpeedMultiplier = 1.5f   // Tilt forward = speed up to 150%
    private val tiltSpeedThreshold = 2.0f       // Minimum tilt to affect speed
    private val maxTiltForSpeed = 8.0f          // Maximum tilt angle for speed calculation

    // Sound
    private var toneGenerator: ToneGenerator? = null

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null
    private val locationPermissionCode = 1001

    // Database
    private lateinit var database: AppDatabase

    // Obstacle types
    private enum class ObstacleType { CAR, TRUCK }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("hw1runner_prefs", MODE_PRIVATE)
        highScore = prefs.getInt("high_score", 0)

        // Initialize database
        database = AppDatabase.getDatabase(this)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get game mode from intent
        gameMode = intent.getIntExtra(MenuActivity.EXTRA_GAME_MODE, MenuActivity.MODE_BUTTON_SLOW)
        useSensorControls = intent.getBooleanExtra(MenuActivity.EXTRA_SENSOR_MODE, false)

        // Configure difficulty based on game mode
        configureGameMode()

        gameArea = binding.gameArea
        roadLinesContainer = binding.roadLinesContainer

        // Initialize sensor
        setupSensor()

        // Initialize sound
        initSound()

        // Request location permission
        requestLocationPermission()

        // Setup controls based on mode
        if (useSensorControls) {
            binding.controlsContainer.visibility = View.GONE
            binding.sensorModeText.visibility = View.VISIBLE
            binding.sensorModeText.text = "📱 TILT TO STEER • LEAN FOR SPEED"
        } else {
            setupControlButton(binding.leftBtn) { movePlayer(-1) }
            setupControlButton(binding.rightBtn) { movePlayer(+1) }
        }

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

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationPermissionCode
            )
        } else {
            getLastLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            }
        }
    }

    private fun getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // Get current location
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                lastKnownLocation = location
            }.addOnFailureListener {
                // Try to get last known location as fallback
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    lastKnownLocation = location
                }
            }
        }
    }

    private fun configureGameMode() {
        when (gameMode) {
            MenuActivity.MODE_BUTTON_SLOW -> {
                // Relaxed pace
                baseSpawnInterval = 1300L
                baseObstacleDuration = 2400L
                minSpawnInterval = 700L
                minObstacleDuration = 1400L
            }
            MenuActivity.MODE_BUTTON_FAST -> {
                // Intense speed
                baseSpawnInterval = 800L
                baseObstacleDuration = 1400L
                minSpawnInterval = 400L
                minObstacleDuration = 800L
            }
            MenuActivity.MODE_SENSOR -> {
                // Medium pace with tilt speed control
                baseSpawnInterval = 1100L
                baseObstacleDuration = 2000L
                minSpawnInterval = 500L
                minObstacleDuration = 1000L
            }
        }

        currentSpawnInterval = baseSpawnInterval
        currentObstacleDuration = baseObstacleDuration
    }

    override fun onResume() {
        super.onResume()
        if (useSensorControls) {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        // Update location when resuming
        getLastLocation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        stopGame()
        toneGenerator?.release()
        super.onDestroy()
    }

    private fun initSound() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playCrashSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playCoinSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            // Device doesn't have accelerometer
            useSensorControls = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!running || gameOverShown || !useSensorControls) return
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]  // Tilt left/right (-10 to +10 approximately)
        val y = event.values[1]  // Tilt forward/back

        // Handle steering (left/right tilt)
        handleTiltSteering(x)

        // Handle speed control (forward/back tilt) - BONUS FEATURE
        handleTiltSpeed(y)
    }

    private fun handleTiltSteering(x: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSensorLaneChange < sensorLaneChangeDelay) return

        // Negative x = tilt right, Positive x = tilt left
        when {
            x < -tiltThreshold -> {
                // Tilted right - move right
                if (laneIndex < laneCount - 1) {
                    movePlayer(1)
                    lastSensorLaneChange = currentTime
                }
            }
            x > tiltThreshold -> {
                // Tilted left - move left
                if (laneIndex > 0) {
                    movePlayer(-1)
                    lastSensorLaneChange = currentTime
                }
            }
        }
    }

    private fun handleTiltSpeed(y: Float) {
        // Y-axis: positive = tilted back (phone facing up), negative = tilted forward (phone facing down)
        // We want: tilt forward = speed up, tilt back = slow down

        if (abs(y) < tiltSpeedThreshold) {
            // No significant tilt - normal speed
            tiltSpeedMultiplier = 1.0f
        } else {
            // Calculate speed multiplier based on tilt
            val normalizedTilt = ((y - tiltSpeedThreshold) / (maxTiltForSpeed - tiltSpeedThreshold))
                .coerceIn(-1f, 1f)

            // Positive y (tilted back) = slow down, Negative y (tilted forward) = speed up
            tiltSpeedMultiplier = if (y > 0) {
                // Tilted back - slow down
                1.0f - (normalizedTilt * (1.0f - minTiltSpeedMultiplier))
            } else {
                // Tilted forward - speed up
                1.0f + (abs(normalizedTilt) * (maxTiltSpeedMultiplier - 1.0f))
            }

            tiltSpeedMultiplier = tiltSpeedMultiplier.coerceIn(minTiltSpeedMultiplier, maxTiltSpeedMultiplier)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
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
        val laneW = w / laneCount
        lanesX = IntArray(laneCount) { i ->
            laneW / 2 + (i * laneW)
        }

        // Update lane separator positions (4 separators for 5 lanes)
        val centerX = w / 2f
        val separatorPositions = listOf(
            (lanesX[0] + lanesX[1]) / 2f,
            (lanesX[1] + lanesX[2]) / 2f,
            (lanesX[2] + lanesX[3]) / 2f,
            (lanesX[3] + lanesX[4]) / 2f
        )

        binding.laneSep1.translationX = separatorPositions[0] - centerX
        binding.laneSep2.translationX = separatorPositions[1] - centerX
        binding.laneSep3.translationX = separatorPositions[2] - centerX
        binding.laneSep4.translationX = separatorPositions[3] - centerX
    }

    private fun setupPlayer() {
        player = ImageView(this).apply {
            setImageResource(R.drawable.ic_car)
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(72))  // Slightly smaller for 5 lanes
            tag = "player"
            elevation = 8f
        }
        gameArea.addView(player)

        // Place near bottom
        player.translationY = (gameArea.height - dp(120)).toFloat()
        laneIndex = 2  // Start in middle lane (index 2 of 5)
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

        // Create dashes for all 4 lane separators
        val separatorXPositions = listOf(
            (lanesX[0] + lanesX[1]) / 2f - dp(3),
            (lanesX[1] + lanesX[2]) / 2f - dp(3),
            (lanesX[2] + lanesX[3]) / 2f - dp(3),
            (lanesX[3] + lanesX[4]) / 2f - dp(3)
        )

        for (i in 0 until numDashes) {
            for (xPos in separatorXPositions) {
                val dash = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(6), dashHeight)
                    setBackgroundResource(R.drawable.ic_road_dash)
                    translationX = xPos
                    translationY = (i * (dashHeight + dashGap)).toFloat() - dashHeight
                    alpha = 0.7f
                }
                roadLinesContainer.addView(dash)
                roadDashes.add(dash)
            }
        }
    }

    private fun startRoadAnimation() {
        val dashHeight = dp(50)
        val dashGap = dp(60)
        val cycleDistance = (dashHeight + dashGap).toFloat()

        roadAnimRunnable = object : Runnable {
            override fun run() {
                if (!running) return

                // Move based on current speed (tied to obstacle duration) and tilt multiplier
                val speedFactor = (baseObstacleDuration.toFloat() / currentObstacleDuration) * tiltSpeedMultiplier
                roadAnimOffset += 8f * speedFactor

                if (roadAnimOffset >= cycleDistance) {
                    roadAnimOffset = 0f
                }

                for ((index, dash) in roadDashes.withIndex()) {
                    val separatorIndex = index % 4  // 4 separators
                    val dashIndex = index / 4
                    val baseY = (dashIndex * cycleDistance) - dashHeight
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
        laneIndex = (laneIndex + delta).coerceIn(0, laneCount - 1)
        placePlayerInLane(laneIndex, animate = true)
    }

    private fun startGame() {
        running = true
        gameOverShown = false
        currentSpawnInterval = baseSpawnInterval
        currentObstacleDuration = baseObstacleDuration
        tiltSpeedMultiplier = 1.0f

        startSpawning()
        startCoinSpawning()
        startRoadAnimation()
        startDifficultyScaling()
        startOdometer()
        animatePlayerIdle()

        // Register sensor if in sensor mode
        if (useSensorControls) {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    private fun stopGame() {
        running = false
        spawnRunnable?.let { handler.removeCallbacks(it) }
        coinSpawnRunnable?.let { handler.removeCallbacks(it) }
        roadAnimRunnable?.let { handler.removeCallbacks(it) }
        difficultyRunnable?.let { handler.removeCallbacks(it) }
        odometerRunnable?.let { handler.removeCallbacks(it) }

        // Unregister sensor
        sensorManager?.unregisterListener(this)
    }

    private fun startSpawning() {
        spawnRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                spawnObstacle()

                // Adjust spawn interval based on tilt speed in sensor mode
                val adjustedInterval = if (useSensorControls) {
                    (currentSpawnInterval / tiltSpeedMultiplier).toLong().coerceIn(minSpawnInterval, baseSpawnInterval * 2)
                } else {
                    currentSpawnInterval
                }

                handler.postDelayed(this, adjustedInterval)
            }
        }
        handler.postDelayed(spawnRunnable!!, 500) // Small initial delay
    }

    private fun startCoinSpawning() {
        coinSpawnRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                spawnCoin()
                handler.postDelayed(this, coinSpawnInterval)
            }
        }
        handler.postDelayed(coinSpawnRunnable!!, 1000) // Start spawning coins after 1 second
    }

    private fun startOdometer() {
        odometerRunnable = object : Runnable {
            override fun run() {
                if (!running) return

                // Calculate speed based on obstacle duration and tilt multiplier
                val speedFactor = (baseObstacleDuration.toFloat() / currentObstacleDuration) * tiltSpeedMultiplier
                distance += 0.5f * speedFactor  // meters per tick

                updateOdometerDisplay()
                handler.postDelayed(this, 50)  // Update every 50ms
            }
        }
        handler.post(odometerRunnable!!)
    }

    private fun updateOdometerDisplay() {
        val displayDistance = if (distance >= 1000) {
            String.format("%.1fkm", distance / 1000f)
        } else {
            String.format("%.0fm", distance)
        }
        binding.odometerText.text = displayDistance
    }

    private fun startDifficultyScaling() {
        difficultyRunnable = object : Runnable {
            override fun run() {
                if (!running) return

                // Gradually increase difficulty
                currentSpawnInterval = max(minSpawnInterval, currentSpawnInterval - 30)
                currentObstacleDuration = max(minObstacleDuration, currentObstacleDuration - 40)

                // Also increase coin spawn rate slightly
                coinSpawnInterval = max(1200L, coinSpawnInterval - 20)

                handler.postDelayed(this, 3000) // Increase difficulty every 3 seconds
            }
        }
        handler.postDelayed(difficultyRunnable!!, 5000) // Start scaling after 5 seconds
    }

    private fun spawnObstacle() {
        // Randomly choose car or truck
        val type = if (Random.nextBoolean()) ObstacleType.CAR else ObstacleType.TRUCK

        val (width, height, drawableRes) = when (type) {
            ObstacleType.CAR -> Triple(dp(48), dp(72), R.drawable.ic_obstacle_car)
            ObstacleType.TRUCK -> Triple(dp(54), dp(88), R.drawable.ic_obstacle_truck)
        }

        val obstacle = ImageView(this).apply {
            setImageResource(drawableRes)
            layoutParams = FrameLayout.LayoutParams(width, height)
            tag = "obstacle"
            elevation = 4f
        }
        gameArea.addView(obstacle)

        val lane = Random.nextInt(0, laneCount)
        obstacle.translationX = lanesX[lane] - (width / 2f)
        obstacle.translationY = (-height - dp(20)).toFloat()

        // Adjust duration based on tilt speed in sensor mode
        val adjustedDuration = if (useSensorControls) {
            (currentObstacleDuration / tiltSpeedMultiplier).toLong().coerceIn(minObstacleDuration, baseObstacleDuration * 2)
        } else {
            currentObstacleDuration
        }

        val anim = ValueAnimator.ofFloat(
            obstacle.translationY,
            (gameArea.height + height).toFloat()
        ).apply {
            duration = adjustedDuration
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

    private fun spawnCoin() {
        val coinSize = dp(32)

        val coin = ImageView(this).apply {
            setImageResource(R.drawable.ic_coin)
            layoutParams = FrameLayout.LayoutParams(coinSize, coinSize)
            tag = "coin"
            elevation = 6f
        }
        gameArea.addView(coin)

        val lane = Random.nextInt(0, laneCount)
        coin.translationX = lanesX[lane] - (coinSize / 2f)
        coin.translationY = (-coinSize - dp(10)).toFloat()

        // Add spinning animation to coin
        coin.animate()
            .rotationBy(360f)
            .setDuration(1000)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                spinCoin(coin)
            }.start()

        // Adjust duration based on tilt speed
        val adjustedDuration = if (useSensorControls) {
            ((currentObstacleDuration + 200) / tiltSpeedMultiplier).toLong()
        } else {
            currentObstacleDuration + 200
        }

        val anim = ValueAnimator.ofFloat(
            coin.translationY,
            (gameArea.height + coinSize).toFloat()
        ).apply {
            duration = adjustedDuration
            interpolator = LinearInterpolator()

            addUpdateListener {
                coin.translationY = it.animatedValue as Float
                if (running && coin.tag == "coin" && intersects(player, coin)) {
                    collectCoin(coin)
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (coin.parent != null) {
                        gameArea.removeView(coin)
                    }
                }
            })
        }

        anim.start()
    }

    private fun spinCoin(coin: ImageView) {
        if (coin.parent == null || coin.tag != "coin") return
        coin.animate()
            .rotationBy(360f)
            .setDuration(1000)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                spinCoin(coin)
            }.start()
    }

    private fun collectCoin(coin: ImageView) {
        if (coin.tag != "coin") return
        coin.tag = "collected"  // Prevent double collection

        coins += 1
        score += 5  // Bonus points for coins
        updateCoinsDisplay()
        updateScoreDisplay()

        // Play coin sound
        playCoinSound()

        // Collection animation
        coin.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .alpha(0f)
            .translationYBy(-dp(30).toFloat())
            .setDuration(200)
            .withEndAction {
                if (coin.parent != null) {
                    gameArea.removeView(coin)
                }
            }.start()

        // Show +5 floating text animation
        showFloatingText("+5", coin.translationX + dp(16), coin.translationY)
    }

    private fun showFloatingText(text: String, x: Float, y: Float) {
        val floatingText = android.widget.TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(resources.getColor(R.color.coin_color, null))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, resources.getColor(R.color.coin_color, null))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            translationX = x
            translationY = y
            elevation = 10f
        }
        gameArea.addView(floatingText)

        floatingText.animate()
            .translationYBy(-dp(50).toFloat())
            .alpha(0f)
            .setDuration(600)
            .withEndAction {
                if (floatingText.parent != null) {
                    gameArea.removeView(floatingText)
                }
            }.start()
    }

    private fun updateCoinsDisplay() {
        binding.coinsText.text = coins.toString()

        // Pulse animation
        binding.coinsText.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(80)
            .withEndAction {
                binding.coinsText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .start()
            }.start()
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

        // Play crash sound
        playCrashSound()

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

        // Get current location for saving
        getLastLocation()

        // Save score to database
        saveScoreToDatabase()

        // Check for high score
        val isNewHighScore = score > highScore
        if (isNewHighScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }

        // Show game over overlay with animation
        binding.finalScoreText.text = "Score: $score"
        binding.finalCoinsText.text = "Coins: $coins"
        binding.finalDistanceText.text = "Distance: ${formatDistance(distance)}"
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

    private fun saveScoreToDatabase() {
        val lat = lastKnownLocation?.latitude ?: 0.0
        val lng = lastKnownLocation?.longitude ?: 0.0

        val entry = HighscoreEntry(
            score = score,
            coins = coins,
            distance = distance,
            latitude = lat,
            longitude = lng
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database.highscoreDao().insertScore(entry)
                // Keep only top 10 scores
                database.highscoreDao().keepOnlyTopTen()
            }
        }
    }

    private fun formatDistance(d: Float): String {
        return if (d >= 1000) {
            String.format("%.2fkm", d / 1000f)
        } else {
            String.format("%.0fm", d)
        }
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
        // Clear all obstacles and coins
        val toRemove = mutableListOf<View>()
        for (i in 0 until gameArea.childCount) {
            val v = gameArea.getChildAt(i)
            if (v.tag == "obstacle" || v.tag == "coin" || v.tag == "collected") {
                toRemove.add(v)
            }
        }
        toRemove.forEach { gameArea.removeView(it) }

        // Reset state
        lives = 3
        score = 0
        coins = 0
        distance = 0f
        laneIndex = 2  // Middle lane of 5
        placePlayerInLane(laneIndex, animate = true)
        player.alpha = 1f
        updateHud()
        updateOdometerDisplay()
        updateCoinsDisplay()

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

    override fun onBackPressed() {
        // Return to menu instead of closing app
        super.onBackPressed()
        finish()
    }
}
