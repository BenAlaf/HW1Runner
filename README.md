# 🏎️ Cyber Runner - Android Racing Game

A cyberpunk-themed endless car runner game built with Kotlin for Android.

![Android](https://img.shields.io/badge/Platform-Android-green) ![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple) ![API](https://img.shields.io/badge/Min%20SDK-24-blue)

---

## 📱 Screenshots

| Menu | Gameplay | Highscores |
|------|----------|------------|
| Game mode selection | 5-lane racing action | Top 10 with map locations |

---

## 🎮 Features

### Core Gameplay
- **5-Lane Road** - Wider road with 5 lanes for more challenging gameplay
- **Two Control Modes:**
  - 🎮 **Button Mode** - Tap left/right buttons to change lanes
  - 📱 **Sensor Mode** - Tilt your phone left/right to steer
- **3 Game Modes:**
  - Buttons - Slow (relaxed pace)
  - Buttons - Fast (intense speed)
  - Sensor Mode (tilt controls + speed control)

### Game Elements
- 🪙 **Coins** - Collect golden coins for bonus points (+5 each)
- 🚗 **Obstacles** - Dodge cars and trucks
- ❤️ **3 Lives** - Crash 3 times and it's game over
- 📏 **Odometer** - Track distance traveled (meters/km)
- 🔊 **Sound Effects** - Crash and coin collection sounds
- 📳 **Haptic Feedback** - Vibration on crash

### Bonus Feature
- **Tilt for Speed Control** (Sensor Mode only)
  - Tilt phone forward → Speed up (up to 150%)
  - Tilt phone back → Slow down (to 60%)

### Highscore System
- 🏆 **Top 10 Leaderboard** - Persistent storage with Room database
- 📍 **Location Tracking** - Records GPS location when score is achieved
- 🗺️ **Google Maps Integration** - View where each high score was recorded
- 📊 **Score Details** - Shows score, coins collected, distance traveled, date

---

## 🛠️ Technical Details

### Built With
- **Language:** Kotlin
- **UI:** XML Layouts with ViewBinding
- **Database:** Room (SQLite)
- **Maps:** Google Maps SDK for Android
- **Location:** Google Play Services Location API
- **Sensors:** Accelerometer for tilt controls

### Architecture
```
com.example.hw1runner/
├── MainActivity.kt          # Game screen
├── MenuActivity.kt          # Main menu
├── HighscoreActivity.kt     # Highscore screen
├── ScoreTableFragment.kt    # Top 10 list
├── ScoreMapFragment.kt      # Google Map
├── HighscoreAdapter.kt      # RecyclerView adapter
└── data/
    ├── HighscoreEntry.kt    # Room Entity
    ├── HighscoreDao.kt      # Room DAO
    └── AppDatabase.kt       # Room Database
```

### Permissions Required
- `VIBRATE` - Haptic feedback on crash
- `ACCESS_FINE_LOCATION` - Record score locations
- `ACCESS_COARSE_LOCATION` - Fallback location

---

## 🎨 Design

The game features a **Cyberpunk/Synthwave** aesthetic:
- Dark purple gradient background
- Neon cyan player car
- Neon magenta obstacles
- Golden coins with glow effect
- Animated road lines

---

## 🚀 How to Play

1. **Select Game Mode** from the menu
2. **Dodge obstacles** by changing lanes
3. **Collect coins** for bonus points
4. **Survive** as long as possible!
5. **Check highscores** to see your best runs and where you played

### Controls

| Mode | Steering | Speed |
|------|----------|-------|
| Button Slow | Tap ◀ ▶ buttons | Fixed (slow) |
| Button Fast | Tap ◀ ▶ buttons | Fixed (fast) |
| Sensor | Tilt phone left/right | Tilt forward/back |

---

## 📦 Installation

1. Clone this repository
2. Open in Android Studio
3. Add your Google Maps API key to `AndroidManifest.xml`
4. Build and run on device/emulator

---

## 👨‍💻 Author

Ben Alaf

---

## 📄 License

This project is for educational purposes.
