package com.example.snake

enum class GameMode(val id: String, val title: String, val description: String, val iconRes: Int) {
    CLASSIC("classic", "Classic", "Traditional snake — eat, grow, survive", 0),
    TIMED("timed", "Timed", "60 seconds to score as high as possible", 1),
    SURVIVAL("survival", "Survival", "Walls close in every 10 points", 2),
    CHALLENGE("challenge", "Daily Challenge", "New board every day, global leaderboard", 3);

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id } ?: CLASSIC
    }

    fun getDailySeed(): Long {
        if (this != CHALLENGE) return System.currentTimeMillis()
        val today = java.time.LocalDate.now()
        return today.toEpochDay()
    }
}

sealed class GameState {
    data class Playing(
        val mode: GameMode,
        val score: Int,
        val timeLeft: Long? = null,
        val wallsInset: Int = 0
    ) : GameState()

    data class Paused(val previous: GameState.Playing) : GameState()
    data class GameOver(val mode: GameMode, val score: Int, val isWin: Boolean, val isNewRecord: Boolean) : GameState()
    object Start : GameState()
    object ModeSelect : GameState()
}