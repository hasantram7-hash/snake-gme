package com.example.snake

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var leaderboardManager: LeaderboardManager
    private lateinit var achievementTracker: AchievementTracker
    private var snakeView: SnakeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        snakeView = findViewById(R.id.snakeView)

        MobileAds.initialize(this) {}

        adView.loadAd(AdRequest.Builder().build())

        leaderboardManager = LeaderboardManager(this)
        achievementTracker = AchievementTracker(this, leaderboardManager)

        // Pass callbacks to SnakeView
        snakeView?.setLeaderboardManager(leaderboardManager)
        snakeView?.setAchievementTracker(achievementTracker)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9001) { // Sign in
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getOrNull()
            leaderboardManager.handleSignInResult(account)
            if (account != null) {
                Toast.makeText(this, "Signed in to Google Play Games", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // SnakeView handles sound manager release in onDetachedFromWindow
    }
}