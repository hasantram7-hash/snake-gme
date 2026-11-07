# Snake Game with AdMob, Leaderboards & Achievements

A feature-rich native Android Snake game with Google AdMob banner ads, Google Play Games leaderboards, achievements, sound effects, haptic feedback, particle effects, power-ups, and multiple game modes.

## AdMob Configuration
- **App ID**: `ca-app-pub-1352153538954102~4561513683`
- **Banner Ad Unit ID**: `ca-app-pub-1352153538954102/4988889091`

## Features

### 🎮 Game Modes
1. **Classic** - Traditional snake, eat and grow forever
2. **Timed** - 60 seconds to score as high as possible
3. **Survival** - Walls close in every 10 points (max 5 levels)
4. **Daily Challenge** - New board layout every day, global leaderboard

### 🎵 Sound & Haptics
- **SoundPool** generated tones (no asset files needed): eat, move, turn, crash, win, power-up, countdown, button
- **Haptic feedback**: light tick on move/food, heavy click on crash, double click on power-up, waveform on win

### ✨ Visual Effects
- Particle explosions on food/power-up collection
- Screen shake on collision
- Pulsing food and power-ups
- Ghost mode transparency
- Combo counter
- Active power-up indicator

### ⚡ Power-Ups (spawn every ~7 foods, 40% chance)
- ⚡ **Speed Boost** - 30% faster for 8s
- 🐢 **Slow Motion** - 50% slower for 8s
- 📉 **Shrink** - Removes 2 tail segments
- 👻 **Ghost Mode** - Pass through self for 8s
- ×3 **Score Multiplier** - Triple points for 8s

### 🏆 Google Play Games Integration
- **4 Leaderboards** (one per game mode)
- **9 Achievements**:
  - First Food
  - Score 10, 50, 100
  - Survive 10 walls (Survival mode)
  - Combo x5
  - Use Ghost Mode
  - Play all 4 modes
  - Complete Daily Challenge

### 💾 Persistence
- High scores per mode in SharedPreferences
- Unlocked achievements cached locally

## Project Structure
```
snake-game/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/snake/
│   │   │   ├── MainActivity.kt           # Activity with AdMob + Play Games init
│   │   │   ├── SnakeView.kt              # Custom View with full game logic
│   │   │   ├── GameMode.kt               # Game mode definitions
│   │   │   ├── SoundManager.kt           # SoundPool tone generator
│   │   │   ├── LeaderboardManager.kt     # Play Games leaderboards
│   │   │   └── AchievementTracker.kt     # Achievement tracking
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/strings.xml, colors.xml, themes.xml, games-ids.xml
│   │   │   └── xml/backup_rules.xml, data_extraction_rules.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
├── gradlew / gradlew.bat
└── README.md
```

## Build Instructions

### Prerequisites
1. **JDK 17+**
2. **Android SDK** (API 34, Build Tools 34.x)
3. **Gradle 8.7** (the wrapper downloads it automatically)

### Option 1: Android Studio (Recommended)
1. Open Android Studio → **File > Open** → select this folder
2. Wait for Gradle sync
3. Connect device or start emulator
4. **Build > Make Project** (Ctrl+F9)
5. **Run > Run 'app'** (Shift+F10)
6. For release: **Build > Generate Signed Bundle / APK** → APK → Release

### Option 2: Command Line
```bash
# Linux/macOS
chmod +x gradlew
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release-unsigned.apk

# Windows
gradlew.bat assembleDebug
gradlew.bat assembleRelease
```

### Option 3: GitHub Actions
Push to `main` or `master`, open a pull request, or run the **Build Android APK**
workflow manually from the repository's **Actions** tab. Download the
`snake-game-debug-apk` artifact from the completed workflow run. The debug APK
is signed for testing and can be installed directly on an Android device.

## Play Games Setup (Required for Leaderboards/Achievements)

1. Create app in **Google Play Console** → **Play Games Services** → **Setup and management** → **Configuration**
2. Add your 4 leaderboards (Classic, Timed, Survival, Challenge) and 9 achievements
3. Copy the generated resource IDs (format: `CgkI...`)
4. Edit `app/src/main/res/values/games-ids.xml` and replace all `YOUR_..._ID` placeholders
5. Add your SHA-1 fingerprint in Play Console (for OAuth)
6. Test with a signed APK (debug keystore won't work for Play Games)

## AdMob Notes
- Uses provided real IDs (not test IDs)
- For testing, add your device as test device:
  ```kotlin
  val request = AdRequest.Builder()
      .addTestDevice("YOUR_DEVICE_ID") // from logcat
      .build()
  adView.loadAd(request)
  ```
- Review [AdMob Policies](https://support.google.com/admob/answer/6128543) before publishing

## Game Controls
- **Swipe** in any direction to turn
- **Tap** to start / pause / resume / restart
- **Tap** overlay buttons for menus

## Publishing to APKPure
1. Generate **signed release APK** (or AAB) in Android Studio
2. Test on multiple devices
3. Create APKPure developer account
4. Upload APK + screenshots + description
5. Wait for review

## Version History
- **v2.0** - 4 game modes, sound, haptics, particles, power-ups, leaderboards, achievements
- **v1.0** - Basic snake with AdMob banner