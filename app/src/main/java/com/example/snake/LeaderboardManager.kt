package com.example.snake

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.games.Games
import com.google.android.gms.games.GamesClient
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.OnFailureListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LeaderboardManager(private val activity: Activity) {

    private val TAG = "LeaderboardManager"
    private var googleSignInClient: GoogleSignInClient? = null
    private var isSignedIn = false
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        initGoogleSignIn()
    }

    private fun initGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(activity, gso)

        // Check if already signed in
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        if (account != null) {
            isSignedIn = true
        }
    }

    fun signIn(onResult: (Boolean) -> Unit) {
        if (isSignedIn) {
            onResult(true)
            return
        }

        val intent = googleSignInClient?.signInIntent
        intent?.let {
            activity.startActivityForResult(it, 9001)
        }
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        isSignedIn = account != null
    }

    fun submitScore(mode: GameMode, score: Int) {
        if (!isSignedIn) return

        val leaderboardId = getLeaderboardId(mode)
        if (leaderboardId.isNullOrEmpty() || leaderboardId.startsWith("YOUR_")) {
            Log.w(TAG, "Leaderboard ID not configured for $mode")
            return
        }

        scope.launch {
            try {
                val client = Games.getLeaderboardsClient(activity, GoogleSignIn.getLastSignedInAccount(activity))
                client.submitScore(leaderboardId, score.toLong())
                    .addOnSuccessListener { Log.d(TAG, "Score submitted: $score to $leaderboardId") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to submit score", e) }
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting score", e)
            }
        }
    }

    fun unlockAchievement(achievementId: String) {
        if (!isSignedIn) return

        val id = activity.getString(getAchievementResId(achievementId))
        if (id.isNullOrEmpty() || id.startsWith("YOUR_")) {
            Log.w(TAG, "Achievement ID not configured: $achievementId")
            return
        }

        scope.launch {
            try {
                val client = Games.getAchievementsClient(activity, GoogleSignIn.getLastSignedInAccount(activity))
                client.unlock(id)
                    .addOnSuccessListener { Log.d(TAG, "Achievement unlocked: $id") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to unlock achievement", e) }
            } catch (e: Exception) {
                Log.e(TAG, "Error unlocking achievement", e)
            }
        }
    }

    fun incrementAchievement(achievementId: String, steps: Int) {
        if (!isSignedIn) return

        val id = activity.getString(getAchievementResId(achievementId))
        if (id.isNullOrEmpty() || id.startsWith("YOUR_")) return

        scope.launch {
            try {
                val client = Games.getAchievementsClient(activity, GoogleSignIn.getLastSignedInAccount(activity))
                client.increment(id, steps)
                    .addOnSuccessListener { Log.d(TAG, "Achievement incremented: $id by $steps") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to increment achievement", e) }
            } catch (e: Exception) {
                Log.e(TAG, "Error incrementing achievement", e)
            }
        }
    }

    fun showLeaderboard(mode: GameMode? = null) {
        if (!isSignedIn) {
            signIn { success ->
                if (success) showLeaderboard(mode)
            }
            return
        }

        val client = Games.getLeaderboardsClient(activity, GoogleSignIn.getLastSignedInAccount(activity))
        val intent = if (mode != null) {
            val leaderboardId = getLeaderboardId(mode)
            if (leaderboardId.isNullOrEmpty() || leaderboardId.startsWith("YOUR_")) {
                Log.w(TAG, "Leaderboard ID not configured for $mode")
                return
            }
            client.getLeaderboardIntent(leaderboardId)
        } else {
            client.getAllLeaderboardsIntent()
        }
        intent.addOnSuccessListener { activity.startActivityForResult(it, 9002) }
    }

    fun showAchievements() {
        if (!isSignedIn) {
            signIn { success ->
                if (success) showAchievements()
            }
            return
        }

        val client = Games.getAchievementsClient(activity, GoogleSignIn.getLastSignedInAccount(activity))
        client.getAchievementsIntent()
            .addOnSuccessListener { activity.startActivityForResult(it, 9003) }
    }

    fun isSignedIn(): Boolean = isSignedIn

    fun signOut() {
        scope.launch {
            googleSignInClient?.signOut()
            isSignedIn = false
        }
    }

    private fun getLeaderboardId(mode: GameMode): String {
        val resId = when (mode) {
            GameMode.CLASSIC -> R.string.leaderboard_classic
            GameMode.TIMED -> R.string.leaderboard_timed
            GameMode.SURVIVAL -> R.string.leaderboard_survival
            GameMode.CHALLENGE -> R.string.leaderboard_challenge
        }
        return activity.getString(resId)
    }

    private fun getAchievementResId(achievementId: String): Int = when (achievementId) {
        "achievement_first_food" -> R.string.achievement_first_food
        "achievement_score_10" -> R.string.achievement_score_10
        "achievement_score_50" -> R.string.achievement_score_50
        "achievement_score_100" -> R.string.achievement_score_100
        "achievement_survive_10" -> R.string.achievement_survive_10
        "achievement_combo_5" -> R.string.achievement_combo_5
        "achievement_ghost_mode" -> R.string.achievement_ghost_mode
        "achievement_all_modes" -> R.string.achievement_all_modes
        "achievement_daily_challenge" -> R.string.achievement_daily_challenge
        else -> R.string.achievement_first_food
    }
}