package com.example.snake

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SnakeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cols = 24
    private val rows = 24
    private var cellSize = 0f

    // Game state
    private var snake = mutableListOf<Point>()
    private var prevSnake = mutableListOf<Point>()
    private var dir = Point(1, 0)
    private var dirQueue = mutableListOf<Point>()
    private var food: Point? = null
    private var powerUp: PowerUp? = null
    private var powerUpTimer = 0L
    private var obstacles = mutableListOf<Point>()
    private var wallsInset = 0
    private var score = 0
    private var bestClassic = 0
    private var bestTimed = 0
    private var bestSurvival = 0
    private var bestChallenge = 0
    private var tickMs = 140L
    private var lastTick = 0L
    private var state = State.START
    private var win = false
    private var gameMode = GameMode.CLASSIC
    private var timeLeft = 60000L // 60 seconds for timed mode
    private var combo = 0
    private var lastEatTime = 0L

    // Visual effects
    private var particles = mutableListOf<Particle>()
    private var screenShake = 0
    private var screenShakeX = 0f
    private var screenShakeY = 0f

    // UI
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }
    private val tinyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        color = Color.parseColor("#8494A6")
    }

    // Touch
    private var touchStartX = 0f
    private var touchStartY = 0f

    // Services
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
    }
    private val soundManager = SoundManager(context)
    private val vibrator: Vibrator? by lazy {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    }
    private var leaderboardManager: LeaderboardManager? = null
    private var achievementTracker: AchievementTracker? = null

    enum class State { START, MODE_SELECT, PLAYING, PAUSED, OVER }

    data class Point(val x: Int, val y: Int) {
        operator fun plus(other: Point) = Point(x + other.x, y + other.y)
        operator fun unaryMinus() = Point(-x, -y)
    }

    enum class PowerUpType { SPEED, SLOW, SHRINK, GHOST, SCORE_MULTI }

    data class PowerUp(
        val type: PowerUpType,
        val position: Point,
        val spawnTime: Long,
        val duration: Long = 8000
    )

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float,
        val color: Int, val size: Float
    )

    init {
        loadBests()
        reset()
    }

    private fun loadBests() {
        bestClassic = prefs.getInt("best_classic", 0)
        bestTimed = prefs.getInt("best_timed", 0)
        bestSurvival = prefs.getInt("best_survival", 0)
        bestChallenge = prefs.getInt("best_challenge", 0)
    }

    private fun saveBest() {
        val key = when (gameMode) {
            GameMode.CLASSIC -> "best_classic"
            GameMode.TIMED -> "best_timed"
            GameMode.SURVIVAL -> "best_survival"
            GameMode.CHALLENGE -> "best_challenge"
        }
        prefs.edit().putInt(key, getCurrentBest()).apply()
    }

    private fun getCurrentBest(): Int = when (gameMode) {
        GameMode.CLASSIC -> bestClassic
        GameMode.TIMED -> bestTimed
        GameMode.SURVIVAL -> bestSurvival
        GameMode.CHALLENGE -> bestChallenge
    }

    private fun updateBest() {
        val currentBest = getCurrentBest()
        if (score > currentBest) {
            saveBest()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = minOf(w, h).toFloat() / cols
    }

    private fun reset() {
        snake = listOf(
            Point(12, 12), Point(11, 12), Point(10, 12), Point(9, 12)
        ).toMutableList()
        prevSnake = snake.map { it.copy() }.toMutableList()
        dir = Point(1, 0)
        dirQueue.clear()
        score = 0
        combo = 0
        tickMs = 140
        lastTick = System.currentTimeMillis()
        wallsInset = 0
        obstacles.clear()
        powerUp = null
        particles.clear()
        screenShake = 0
        timeLeft = if (gameMode == GameMode.TIMED) 60000L else 0
        spawnFood()
        spawnObstacles()
    }

    private fun spawnFood() {
        val free = getFreeCells()
        if (free.isNotEmpty()) {
            food = free.random()
        } else {
            food = null
            endGame(true)
        }
    }

    private fun spawnObstacles() {
        obstacles.clear()
        if (gameMode == GameMode.SURVIVAL) return // walls handle this
        if (gameMode == GameMode.CHALLENGE) {
            // Daily challenge: fixed pattern based on date
            val seed = gameMode.getDailySeed()
            val rng = java.util.Random(seed)
            for (i in 0 until 8) {
                val free = getFreeCells()
                if (free.isNotEmpty()) obstacles.add(free.random())
            }
            achievementTracker?.onDailyChallengePlayed()
        }
    }

    private fun getFreeCells(): List<Point> {
        val free = mutableListOf<Point>()
        for (y in wallsInset until rows - wallsInset) {
            for (x in wallsInset until cols - wallsInset) {
                if (!snake.any { it.x == x && it.y == y }
                    && (food == null || !(food!!.x == x && food!!.y == y))
                    && !obstacles.any { it.x == x && it.y == y }
                    && (powerUp == null || !(powerUp!!.position.x == x && powerUp!!.position.y == y))) {
                    free.add(Point(x, y))
                }
            }
        }
        return free
    }

    private fun maybeSpawnPowerUp() {
        if (powerUp != null) return
        if (score > 0 && score % 7 == 0 && Math.random() < 0.4) {
            val free = getFreeCells()
            if (free.isNotEmpty()) {
                val type = PowerUpType.values().random()
                powerUp = PowerUp(type, free.random(), System.currentTimeMillis())
            }
        }
    }

    private fun step() {
        // Process queued direction changes
        while (dirQueue.isNotEmpty()) {
            val d = dirQueue.removeAt(0)
            if (d != -dir && d != dir) { dir = d; break }
        }

        prevSnake = snake.map { it.copy() }.toMutableList()
        val head = snake[0] + dir

        // Check wall collision
        val hitWall = head.x < wallsInset || head.x >= cols - wallsInset
            || head.y < wallsInset || head.y >= rows - wallsInset

        // Check obstacle collision
        val hitObstacle = obstacles.any { it.x == head.x && it.y == head.y }

        // Check self collision (unless ghost powerup)
        val hasGhost = powerUp?.type == PowerUpType.GHOST && !powerUpExpired()
        val body = if (food != null && head.x == food!!.x && head.y == food!!.y) snake else snake.dropLast(1)
        val hitSelf = body.any { it.x == head.x && it.y == head.y }

        if (hitWall || hitObstacle || hitSelf) {
            triggerScreenShake(12)
            soundManager.play(SoundManager.Sound.CRASH)
            vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)
            endGame(false)
            return
        }

        snake.add(0, head)

        // Check food
        val eating = food != null && head.x == food!!.x && head.y == food!!.y
        if (eating) {
            handleFoodEaten()
        } else {
            snake.removeAt(snake.lastIndex)
        }

        // Check power-up
        if (powerUp != null && head.x == powerUp!!.position.x && head.y == powerUp!!.position.y) {
            collectPowerUp()
        }

        // Update power-up expiry
        if (powerUp != null && powerUpExpired()) {
            powerUp = null
        }

        // Timed mode countdown
        if (gameMode == GameMode.TIMED) {
            timeLeft -= tickMs
            if (timeLeft <= 0) {
                endGame(false)
            }
        }

        // Survival mode: walls close in
        if (gameMode == GameMode.SURVIVAL && score > 0 && score % 10 == 0 && wallsInset < 5) {
            wallsInset++
            soundManager.play(SoundManager.Sound.COUNTDOWN)
            vibrate(VibrationEffect.EFFECT_TICK)
        }

        // Update particles
        updateParticles()

        // Maybe spawn power-up
        maybeSpawnPowerUp()
    }

    private fun handleFoodEaten() {
        score++
        combo++
        lastEatTime = System.currentTimeMillis()

        // Track for achievements
        achievementTracker?.onFoodEaten(score, combo, gameMode)
        if (powerUp?.type == PowerUpType.GHOST && !powerUpExpired()) {
            achievementTracker?.onGhostModeUsed()
        }

        // Score multiplier from power-up
        var points = 1
        if (powerUp?.type == PowerUpType.SCORE_MULTI && !powerUpExpired()) {
            points = 3
            score += 2 // bonus
        }

        // Speed adjustments
        val baseSpeed = when (gameMode) {
            GameMode.TIMED -> 100L
            GameMode.SURVIVAL -> 120L
            else -> 140L
        }
        var newTickMs = maxOf(50L, baseSpeed - score * 2)

        // Apply speed power-up
        if (powerUp?.type == PowerUpType.SPEED && !powerUpExpired()) {
            newTickMs = (newTickMs * 0.7).toLong()
        } else if (powerUp?.type == PowerUpType.SLOW && !powerUpExpired()) {
            newTickMs = (newTickMs * 1.5).toLong()
        }

        tickMs = newTickMs

        // Effects
        spawnParticles(food!!, Color.parseColor("#F87171"), 12)
        soundManager.playEat(combo)
        vibrate(VibrationEffect.EFFECT_TICK)

        // Shrink power-up
        if (powerUp?.type == PowerUpType.SHRINK && !powerUpExpired() && snake.size > 3) {
            snake.removeAt(snake.lastIndex)
            snake.removeAt(snake.lastIndex)
        }

        spawnFood()
        updateBest()
    }

    private fun collectPowerUp() {
        soundManager.play(SoundManager.Sound.POWERUP)
        vibrate(VibrationEffect.EFFECT_DOUBLE_CLICK)
        spawnParticles(powerUp!!.position, getPowerUpColor(powerUp!!.type), 20)
        powerUp = null
    }

    private fun powerUpExpired(): Boolean {
        return powerUp != null && System.currentTimeMillis() - powerUp!!.spawnTime > powerUp!!.duration
    }

    private fun getPowerUpColor(type: PowerUpType): Int = when (type) {
        PowerUpType.SPEED -> Color.parseColor("#FBBF24")      // amber
        PowerUpType.SLOW -> Color.parseColor("#60A5FA")       // blue
        PowerUpType.SHRINK -> Color.parseColor("#F472B6")     // pink
        PowerUpType.GHOST -> Color.parseColor("#A78BFA")      // purple
        PowerUpType.SCORE_MULTI -> Color.parseColor("#34D399") // green
    }

    private fun endGame(isWin: Boolean) {
        state = State.OVER
        win = isWin
        updateBest()

        // Submit score to leaderboard
        leaderboardManager?.submitScore(gameMode, score)

        // Track achievements
        achievementTracker?.onGameEnd(score, gameMode)

        if (isWin) {
            soundManager.play(SoundManager.Sound.WIN)
            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 100), -1))
        }
    }

    private fun vibrate(effect: VibrationEffect?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(30)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply screen shake
        val shakeX = if (screenShake > 0) screenShakeX else 0f
        val shakeY = if (screenShake > 0) screenShakeY else 0f
        canvas.translate(shakeX, shakeY)

        drawBackground(canvas)
        drawWalls(canvas)
        drawObstacles(canvas)
        drawGrid(canvas)
        food?.let { drawFood(canvas, it) }
        powerUp?.let { drawPowerUp(canvas, it) }
        drawSnake(canvas)
        drawParticles(canvas)
        drawHUD(canvas)
        drawOverlay(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#0A0E14"))
    }

    private fun drawWalls(canvas: Canvas) {
        if (wallsInset <= 0) return
        paint.color = Color.parseColor("#EF4444")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        val rect = RectF(
            wallsInset * cellSize, wallsInset * cellSize,
            (cols - wallsInset) * cellSize, (rows - wallsInset) * cellSize
        )
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        // Inner glow
        paint.color = Color.parseColor("#44EF4444")
        paint.style = Paint.Style.FILL
        canvas.drawRect(rect, paint)
    }

    private fun drawObstacles(canvas: Canvas) {
        paint.color = Color.parseColor("#374151")
        paint.style = Paint.Style.FILL
        for (obs in obstacles) {
            val rect = RectF(
                obs.x * cellSize + 2, obs.y * cellSize + 2,
                (obs.x + 1) * cellSize - 2, (obs.y + 1) * cellSize - 2
            )
            canvas.drawRoundRect(rect, 4f, 4f, paint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        paint.color = Color.parseColor("#FFFFFF0C")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        for (i in 1 until cols) {
            val x = i * cellSize
            canvas.drawLine(x, 0f, x, rows * cellSize, paint)
        }
        for (j in 1 until rows) {
            val y = j * cellSize
            canvas.drawLine(0f, y, cols * cellSize, y, paint)
        }
    }

    private fun drawFood(canvas: Canvas, f: Point) {
        val cx = (f.x + 0.5f) * cellSize
        val cy = (f.y + 0.5f) * cellSize
        val r = cellSize * 0.34f
        val pulse = 0.75f + 0.25f * sin((System.currentTimeMillis() % 2000) / 2000f * 2 * 3.14159f)

        val glow = android.graphics.RadialGradient(cx, cy, cellSize * 0.9f,
            intArrayOf(Color.parseColor("#CCF87171"), Color.parseColor("#00F87171")),
            null, android.graphics.Shader.TileMode.CLAMP)
        paint.shader = glow
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, cellSize * 0.9f * pulse, paint)

        paint.shader = null
        paint.color = Color.parseColor("#F87171")
        canvas.drawCircle(cx, cy, r * pulse, paint)

        paint.color = Color.parseColor("#80FFFFFF")
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.22f, paint)
    }

    private fun drawPowerUp(canvas: Canvas, p: PowerUp) {
        val cx = (p.position.x + 0.5f) * cellSize
        val cy = (p.position.y + 0.5f) * cellSize
        val r = cellSize * 0.3f
        val timeLeft = max(0f, (p.duration - (System.currentTimeMillis() - p.spawnTime)).toFloat() / p.duration)
        val pulse = 0.8f + 0.2f * sin((System.currentTimeMillis() % 1000) / 1000f * 2 * 3.14159f)

        val color = getPowerUpColor(p.type)
        val glow = android.graphics.RadialGradient(cx, cy, cellSize * 0.8f,
            intArrayOf(color, Color.parseColor("#00000000")),
            null, android.graphics.Shader.TileMode.CLAMP)
        paint.shader = glow
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, cellSize * 0.8f * pulse * timeLeft, paint)

        paint.shader = null
        paint.color = color
        canvas.drawCircle(cx, cy, r * pulse, paint)

        // Icon indicator
        textPaint.color = Color.WHITE
        textPaint.textSize = cellSize * 0.5f
        val icon = when (p.type) {
            PowerUpType.SPEED -> "⚡"
            PowerUpType.SLOW -> "🐢"
            PowerUpType.SHRINK -> "📉"
            PowerUpType.GHOST -> "👻"
            PowerUpType.SCORE_MULTI -> "×3"
        }
        canvas.drawText(icon, cx, cy + cellSize * 0.18f, textPaint)
        textPaint.textSize = 48f
    }

    private fun drawSnake(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val t = if (state == State.PLAYING) minOf(1f, (now - lastTick).toFloat() / tickMs) else 1f
        val pad = cellSize * 0.16f
        val segSize = cellSize - pad * 2
        val radius = cellSize * 0.26f

        // Ghost effect
        val ghostAlpha = if (powerUp?.type == PowerUpType.GHOST && !powerUpExpired()) 0.5f else 1f

        for (i in snake.indices.reversed()) {
            val from = if (i < prevSnake.size) prevSnake[i] else snake[i]
            val to = snake[i]
            val lx = lerp(from.x, to.x, t)
            val ly = lerp(from.y, to.y, t)
            val px = lx * cellSize + pad
            val py = ly * cellSize + pad

            val rect = RectF(px, py, px + segSize, py + segSize)
            if (i == 0) {
                paint.color = Color.parseColor("#A7F3D0")
                paint.alpha = (255 * ghostAlpha).toInt()
                paint.shadowLayer = cellSize * 0.5f
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.shadowLayer = 0f
                paint.alpha = 255
            } else {
                val k = i.toFloat() / maxOf(1, snake.size - 1)
                val lightness = 48 - k * 22
                paint.color = android.graphics.Color.HSVToColor(floatArrayOf(158f, 0.7f, lightness / 100f))
                paint.alpha = (255 * ghostAlpha).toInt()
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.alpha = 255
            }
        }

        // Eyes
        if (state != State.START && state != State.MODE_SELECT) {
            val from = if (prevSnake.isNotEmpty()) prevSnake[0] else snake[0]
            val to = snake[0]
            val lx = lerp(from.x, to.x, t)
            val ly = lerp(from.y, to.y, t)
            val hx = (lx + 0.5f) * cellSize
            val hy = (ly + 0.5f) * cellSize
            val eye = cellSize * 0.09f
            val off = cellSize * 0.12f
            val pe = cellSize * 0.16f
            val ex = hx + dir.x * off
            val ey = hy + dir.y * off
            val pdx = -dir.y.toFloat()
            val pdy = dir.x.toFloat()
            paint.color = Color.parseColor("#06331F")
            paint.shadowLayer = 0f
            canvas.drawCircle(ex + pdx * pe, ey + pdy * pe, eye, paint)
            canvas.drawCircle(ex - pdx * pe, ey - pdy * pe, eye, paint)
        }
    }

    private fun spawnParticles(pos: Point, color: Int, count: Int) {
        for (i in 0 until count) {
            val angle = Math.random() * 2 * Math.PI
            val speed = (50 + Math.random() * 150).toFloat()
            particles.add(Particle(
                x = (pos.x + 0.5f) * cellSize,
                y = (pos.y + 0.5f) * cellSize,
                vx = (speed * cos(angle)).toFloat(),
                vy = (speed * sin(angle)).toFloat(),
                life = 1f,
                maxLife = 0.6f + Math.random().toFloat() * 0.4f,
                color = color,
                size = (4 + Math.random() * 6).toFloat()
            ))
        }
    }

    private fun updateParticles() {
        val dt = 16f / 1000f // ~60fps
        for (i in particles.indices.reversed()) {
            val p = particles[i]
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 200f * dt // gravity
            p.life -= dt
            if (p.life <= 0) particles.removeAt(i)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (p in particles) {
            val alpha = (255 * (p.life / p.maxLife)).toInt()
            paint.color = p.color
            paint.alpha = alpha
            canvas.drawCircle(p.x, p.y, p.size * (p.life / p.maxLife), paint)
        }
        paint.alpha = 255
    }

    private fun drawHUD(canvas: Canvas) {
        // Score
        textPaint.color = Color.parseColor("#DBE4EE")
        textPaint.textSize = 36f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: $score", 16f, 50f, textPaint)

        // Best
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Best: ${getCurrentBest()}", width - 16f, 50f, textPaint)

        // Timed mode countdown
        if (gameMode == GameMode.TIMED && state == State.PLAYING) {
            val seconds = (timeLeft / 1000).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = if (seconds <= 10) Color.parseColor("#EF4444") else Color.parseColor("#FBBF24")
            textPaint.textSize = 32f
            canvas.drawText("${seconds}s", width / 2f, 50f, textPaint)
        }

        // Survival walls indicator
        if (gameMode == GameMode.SURVIVAL && state == State.PLAYING) {
            tinyTextPaint.color = Color.parseColor("#EF4444")
            tinyTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Walls: $wallsInset/5", width / 2f, 80f, tinyTextPaint)
        }

        // Active power-up indicator
        if (powerUp != null && !powerUpExpired() && state == State.PLAYING) {
            tinyTextPaint.color = getPowerUpColor(powerUp!!.type)
            tinyTextPaint.textAlign = Paint.Align.CENTER
            val timeLeft = (powerUp!!.duration - (System.currentTimeMillis() - powerUp!!.spawnTime)) / 1000
            canvas.drawText("${getPowerUpName(powerUp!!.type)}: ${timeLeft}s", width / 2f, height - 100f, tinyTextPaint)
        }

        // Combo
        if (combo > 1 && state == State.PLAYING) {
            tinyTextPaint.color = Color.parseColor("#FBBF24")
            tinyTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Combo x$combo!", 16f, height - 100f, tinyTextPaint)
        }
    }

    private fun getPowerUpName(type: PowerUpType): String = when (type) {
        PowerUpType.SPEED -> "Speed Boost"
        PowerUpType.SLOW -> "Slow Motion"
        PowerUpType.SHRINK -> "Shrink"
        PowerUpType.GHOST -> "Ghost Mode"
        PowerUpType.SCORE_MULTI -> "Score ×3"
    }

    private fun drawOverlay(canvas: Canvas) {
        if (state == State.PLAYING) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Dim background
        paint.color = Color.parseColor("#B8080B10")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)

        when (state) {
            State.START -> drawStartScreen(canvas, w, h)
            State.MODE_SELECT -> drawModeSelect(canvas, w, h)
            State.PAUSED -> drawPauseScreen(canvas, w, h)
            State.OVER -> drawGameOver(canvas, w, h)
        }
    }

    private fun drawStartScreen(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#DBE4EE")
        textPaint.textSize = 48f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SNAKE", w / 2, h / 2 - 120, textPaint)

        smallTextPaint.color = Color.parseColor("#8494A6")
        canvas.drawText("Swipe to move • Tap to start", w / 2, h / 2 - 40, smallTextPaint)

        // Mode select button
        drawButton(canvas, w / 2 - 100, h / 2 + 20, 200, 60, "Play", Color.parseColor("#10B981"))
        drawButton(canvas, w / 2 - 100, h / 2 + 100, 200, 50, "Game Modes", Color.parseColor("#6366F1"))
    }

    private fun drawModeSelect(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#DBE4EE")
        textPaint.textSize = 40f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SELECT MODE", w / 2, 80, textPaint)

        val modes = GameMode.values()
        val btnH = 70f
        val btnW = w - 40
        val startY = 140f

        for (i in modes.indices) {
            val m = modes[i]
            val y = startY + i * (btnH + 12)
            val isSelected = m == gameMode
            val bgColor = if (isSelected) Color.parseColor("#10B981") else Color.parseColor("#1F2A36")
            val borderColor = if (isSelected) Color.parseColor("#34D399") else Color.parseColor("#374151")

            val rect = RectF(20f, y, 20f + btnW, y + btnH)
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRoundRect(rect, 12f, 12f, paint)

            paint.color = bgColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect.shrink(2f), 10f, 10f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 22f
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(m.title, 40f, y + 30f, textPaint)

            tinyTextPaint.color = Color.parseColor("#9CA3AF")
            tinyTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(m.description, 40f, y + 52f, tinyTextPaint)

            // Best score for this mode
            val best = when (m) {
                GameMode.CLASSIC -> bestClassic
                GameMode.TIMED -> bestTimed
                GameMode.SURVIVAL -> bestSurvival
                GameMode.CHALLENGE -> bestChallenge
            }
            tinyTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Best: $best", w - 40f, y + 52f, tinyTextPaint)
        }

        // Back button
        drawButton(canvas, w / 2 - 100, h - 100, 200, 50, "Back", Color.parseColor("#6B7280"))
    }

    private fun drawPauseScreen(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#DBE4EE")
        textPaint.textSize = 48f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("PAUSED", w / 2, h / 2 - 80, textPaint)

        smallTextPaint.color = Color.parseColor("#8494A6")
        canvas.drawText("Take a breath", w / 2, h / 2 - 20, smallTextPaint)

        drawButton(canvas, w / 2 - 100, h / 2 + 40, 200, 60, "Resume", Color.parseColor("#10B981"))
    }

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = if (win) Color.parseColor("#34D399") else Color.parseColor("#EF4444")
        textPaint.textSize = 48f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (win) "YOU WIN!" else "GAME OVER", w / 2, h / 2 - 100, textPaint)

        smallTextPaint.color = Color.parseColor("#8494A6")
        canvas.drawText(if (win) "Perfect run!" else "Watch the walls and your tail", w / 2, h / 2 - 40, smallTextPaint)

        // Scores
        textPaint.color = Color.parseColor("#DBE4EE")
        textPaint.textSize = 36f
        canvas.drawText("Score: $score", w / 2, h / 2 + 20, textPaint)
        canvas.drawText("Best: ${getCurrentBest()}", w / 2, h / 2 + 70, textPaint)

        if (win) {
            smallTextPaint.color = Color.parseColor("#34D399")
            canvas.drawText("🏆 New Record!", w / 2, h / 2 + 110, smallTextPaint)
        }

        drawButton(canvas, w / 2 - 100, h / 2 + 160, 200, 60, "Play Again", Color.parseColor("#10B981"))
        drawButton(canvas, w / 2 - 100, h / 2 + 240, 200, 50, "Change Mode", Color.parseColor("#6366F1"))
        drawButton(canvas, w / 2 - 100, h / 2 + 310, 200, 50, "Leaderboard", Color.parseColor("#F59E0B"))
    }

    private fun drawButton(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, color: Int) {
        val rect = RectF(x, y, x + w, y + h)
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 12f, 12f, paint)

        textPaint.color = Color.parseColor("#052E1C")
        textPaint.textSize = 22f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x + w / 2, y + h / 2 + 8, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val dist = hypot(dx, dy)

                // Handle overlay buttons
                if (state != State.PLAYING) {
                    handleOverlayTouch(event.x, event.y, dist)
                    return true
                }

                if (dist < 40) return true // tap ignored during play

                val d = if (abs(dx) > abs(dy))
                    if (dx > 0) Point(1, 0) else Point(-1, 0)
                else
                    if (dy > 0) Point(0, 1) else Point(0, -1)

                val last = if (dirQueue.isNotEmpty()) dirQueue.last() else dir
                if (dirQueue.size < 3 && d != -last && d != last) {
                    dirQueue.add(d)
                    soundManager.play(SoundManager.Sound.TURN, 0.3f)
                }
            }
        }
        return true
    }

    private fun handleOverlayTouch(x: Float, y: Float, dist: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        when (state) {
            State.START -> {
                // Play button
                if (y > h / 2 + 20 && y < h / 2 + 80 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    startGame()
                }
                // Modes button
                else if (y > h / 2 + 100 && y < h / 2 + 150 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    state = State.MODE_SELECT
                    invalidate()
                }
            }
            State.MODE_SELECT -> {
                val modes = GameMode.values()
                val btnH = 70f
                val btnW = w - 40
                val startY = 140f
                for (i in modes.indices) {
                    val y1 = startY + i * (btnH + 12)
                    if (y > y1 && y < y1 + btnH && x > 20 && x < 20 + btnW) {
                        soundManager.play(SoundManager.Sound.BUTTON)
                        gameMode = modes[i]
                        reset()
                        state = State.START
                        invalidate()
                        return
                    }
                }
                // Back button
                if (y > h - 100 && y < h - 50 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    state = State.START
                    invalidate()
                }
            }
            State.PAUSED -> {
                if (y > h / 2 + 40 && y < h / 2 + 100 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    resumeGame()
                }
            }
            State.OVER -> {
                // Play again
                if (y > h / 2 + 160 && y < h / 2 + 220 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    startGame()
                }
                // Change mode
                else if (y > h / 2 + 240 && y < h / 2 + 290 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    state = State.MODE_SELECT
                    invalidate()
                }
                // Leaderboard
                else if (y > h / 2 + 310 && y < h / 2 + 360 && x > w / 2 - 100 && x < w / 2 + 100) {
                    soundManager.play(SoundManager.Sound.BUTTON)
                    leaderboardManager?.showLeaderboard(gameMode)
                }
            }
        }
    }

    fun setLeaderboardManager(lm: LeaderboardManager) {
        leaderboardManager = lm
    }

    fun setAchievementTracker(at: AchievementTracker) {
        achievementTracker = at
    }

    fun startGame() {
        reset()
        state = State.PLAYING
        lastTick = System.currentTimeMillis()
        invalidate()
    }

    fun pauseGame() {
        if (state == State.PLAYING) {
            state = State.PAUSED
            invalidate()
        }
    }

    fun resumeGame() {
        if (state == State.PAUSED) {
            state = State.PLAYING
            lastTick = System.currentTimeMillis()
            invalidate()
        }
    }

    fun triggerScreenShake(intensity: Int) {
        screenShake = intensity
        screenShakeX = (Math.random() * intensity - intensity / 2).toFloat()
        screenShakeY = (Math.random() * intensity - intensity / 2).toFloat()
        postDelayed({ screenShake = 0 }, 100)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        gameLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundManager.release()
    }

    private fun gameLoop() {
        postDelayed({ gameLoop() }, 16)
        val now = System.currentTimeMillis()

        if (screenShake > 0) {
            screenShake--
            screenShakeX = (Math.random() * screenShake - screenShake / 2).toFloat()
            screenShakeY = (Math.random() * screenShake - screenShake / 2).toFloat()
        }

        if (state == State.PLAYING && now - lastTick >= tickMs) {
            step()
            lastTick = now
        }
        invalidate()
    }

    companion object {
        private fun lerp(a: Int, b: Int, t: Float): Float = a + (b - a) * t
        private fun hypot(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy).toFloat()
        private fun sin(x: Float): Float = kotlin.math.sin(x)
        private fun cos(x: Float): Float = kotlin.math.cos(x)
    }
}