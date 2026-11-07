package com.example.snake

import android.content.Context
import android.content.SharedPreferences

class AchievementTracker(private val context: Context, private val leaderboardManager: LeaderboardManager) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
    }

    private var foodsEaten = 0
    private var maxCombo = 0
    private var ghostModeUsed = false
    private var modesPlayed = mutableSetOf<GameMode>()
    private var dailyChallengePlayed = false

    fun onFoodEaten(score: Int, combo: Int, mode: GameMode) {
        foodsEaten++
        maxCombo = max(maxCombo, combo)
        modesPlayed.add(mode)

        checkAchievements(score, mode)
    }

    fun onGhostModeUsed() {
        ghostModeUsed = true
        checkGhostAchievement()
    }

    fun onDailyChallengePlayed() {
        dailyChallengePlayed = true
        checkDailyChallengeAchievement()
    }

    fun onGameEnd(score: Int, mode: GameMode) {
        modesPlayed.add(mode)
        checkAchievements(score, mode)
    }

    private fun checkAchievements(score: Int, mode: GameMode) {
        // First food
        if (foodsEaten == 1) unlock(LeaderboardManager.ACHIEVEMENT_FIRST_FOOD)

        // Score milestones
        if (score >= 10) unlock(LeaderboardManager.ACHIEVEMENT_SCORE_10)
        if (score >= 50) unlock(LeaderboardManager.ACHIEVEMENT_SCORE_50)
        if (score >= 100) unlock(LeaderboardManager.ACHIEVEMENT_SCORE_100)

        // Survival walls
        if (mode == GameMode.SURVIVAL && score >= 10) {
            unlock(LeaderboardManager.ACHIEVEMENT_SURVIVE_10)
        }

        // Combo
        if (maxCombo >= 5) unlock(LeaderboardManager.ACHIEVEMENT_COMBO_5)

        // All modes
        if (modesPlayed.size == GameMode.values().size) {
            unlock(LeaderboardManager.ACHIEVEMENT_ALL_MODES)
        }
    }

    private fun checkGhostAchievement() {
        if (ghostModeUsed) {
            unlock(LeaderboardManager.ACHIEVEMENT_GHOST_MODE)
        }
    }

    private fun checkDailyChallengeAchievement() {
        if (dailyChallengePlayed) {
            unlock(LeaderboardManager.ACHIEVEMENT_DAILY_CHALLENGE)
        }
    }

    private fun unlock(achievementId: String) {
        val key = "unlocked_$achievementId"
        if (!prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, true).apply()
            leaderboardManager.unlockAchievement(achievementId)
        }
    }

    fun isUnlocked(achievementId: String): Boolean {
        return prefs.getBoolean("unlocked_$achievementId", false)
    }

    fun getUnlockedCount(): Int {
        val all = arrayOf(
            LeaderboardManager.ACHIEVEMENT_FIRST_FOOD,
            LeaderboardManager.ACHIEVEMENT_SCORE_10,
            LeaderboardManager.ACHIEVEMENT_SCORE_50,
            LeaderboardManager.ACHIEVEMENT_SCORE_100,
            LeaderboardManager.ACHIEVEMENT_SURVIVE_10,
            LeaderboardManager.ACHIEVEMENT_COMBO_5,
            LeaderboardManager.ACHIEVEMENT_GHOST_MODE,
            LeaderboardManager.ACHIEVEMENT_ALL_MODES,
            LeaderboardManager.ACHIEVEMENT_DAILY_CHALLENGE
        )
        return all.count { isUnlocked(it) }
    }

    fun getTotalCount() = 9
}