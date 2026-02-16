# PlayGamesPlugin - Godot Android Plugin

A Godot 4.2+ Android plugin for Google Play Games Services v2.

**Tested with:** Godot 4.5.1, Play Games Services v2 SDK 21.0.0, Android SDK 35

## Overview

This plugin integrates Google Play Games Services into Godot games using the modern v2 API. It uses the Godot v2 plugin format with EditorExportPlugin for seamless integration.

### Features

| Feature | Status | Description |
|---------|--------|-------------|
| Automatic Sign-in | ✅ | Users are signed in automatically at app launch |
| Manual Sign-in | ✅ | Trigger sign-in for users who declined initially |
| Player Info | ✅ | Get player ID and display name |
| Cloud Save | ✅ | Save/load/delete game data via Snapshots API |
| Signals | ✅ | Async events via Godot signals |
| Achievements | ⏳ | Planned for v1.1 |
| Leaderboards | ✅ | Submit scores + load top scores + player rank |

### Play Games Services v2 Benefits

- **No Play Games app required** - Users don't need to install the Play Games app
- **Automatic sign-in** - Returning users are signed in silently
- **OS-managed accounts** - Sign-out handled in Android settings, not in-app
- **Simplified API** - No deprecated GoogleSignIn classes

## Prerequisites

- Godot Engine 4.2+ (tested with 4.5.1)
- Android Studio (for building the plugin)
- Google Play Console account with Play Games Services configured
- JDK 17 or later

## Project Structure

```
PlayGamesPlugin/
├── app/
│   ├── build.gradle.kts              # Dependencies and build config
│   └── src/main/
│       ├── assets/
│       │   └── godot_plugin.json     # Plugin metadata (bundled in AAR)
│       ├── AndroidManifest.xml       # Permissions + v2 plugin registration
│       └── java/.../PlayGamesPlugin.kt
├── addons/
│   └── play_games_plugin/            # Copy this to your Godot project
│       ├── plugin.cfg                # EditorPlugin config
│       └── play_games_plugin.gd      # EditorExportPlugin
├── INSTALLATION_GUIDE.md             # Detailed installation steps
├── DEVELOPMENT.md                    # Developer reference
└── example_plugin_usage.gd           # Example GDScript
```

## Quick Start

### 0. Install From Release (No Build Required)

If you just want to use the plugin, download the release bundle and copy the folder into your project:

```bash
# Example for v1.1.2
unzip PlayGamesPlugin-v1.1.2-addons.zip
cp -r play_games_plugin /path/to/your_project/addons/
```

Expected target structure:
```
your_project/addons/play_games_plugin/
├── plugin.cfg
├── play_games_plugin.gd
├── PlayGamesPlugin-debug.aar
└── PlayGamesPlugin-release.aar
```

### 1. Build the Plugin

```bash
./gradlew clean assembleDebug assembleRelease
```

### 2. Install in Godot Project

Copy to your Godot project:
```
your_project/addons/play_games_plugin/
├── plugin.cfg
├── play_games_plugin.gd
├── PlayGamesPlugin-debug.aar    # from app/build/outputs/aar/app-debug.aar
└── PlayGamesPlugin-release.aar  # from app/build/outputs/aar/app-release.aar
```

### 3. Configure App ID

Edit `addons/play_games_plugin/play_games_plugin.gd`:
```gdscript
const PLAY_GAMES_APP_ID = "123456789012"  # Your App ID from Play Console
```

### 4. Enable Plugin

In Godot: **Project → Project Settings → Plugins → Enable "PlayGamesPlugin"**

### 5. Install Android Build Template

In Godot: **Project → Install Android Build Template**

This is required for Gradle-based Android plugins.

### 6. Use in GDScript

```gdscript
var play_games = null

func _ready():
    if Engine.has_singleton("PlayGamesPlugin"):
        play_games = Engine.get_singleton("PlayGamesPlugin")
        play_games.sign_in_success.connect(_on_sign_in_success)
        play_games.sign_in_failed.connect(_on_sign_in_failed)
        play_games.player_info_loaded.connect(_on_player_info_loaded)

        # Check if already signed in (automatic sign-in)
        if play_games.isSignedIn():
            print("Already signed in as: ", play_games.getPlayerDisplayName())

func _notification(what):
    # Refresh auth when app resumes (important!)
    if what == NOTIFICATION_APPLICATION_RESUMED and play_games:
        play_games.refreshAuthStatus()

func _on_sign_in_success(player_id: String, player_name: String):
    print("Signed in: ", player_name)

func _on_sign_in_failed():
    print("Sign-in failed or declined")

func _on_player_info_loaded(player_id: String, player_name: String):
    print("Player info loaded: ", player_name)

func _on_sign_in_button_pressed():
    play_games.signIn()
```


### Leaderboard Example

```gdscript
func submit_score(score: int):
    if play_games and play_games.isSignedIn():
        play_games.submitScore("leaderboard_id", score)

func load_daily_leaderboard():
    play_games.loadTopScores("leaderboard_id", "daily", "public", 10, true)
    play_games.loadPlayerScore("leaderboard_id", "daily", "public", true)

func _on_leaderboard_top_scores_loaded(leaderboard_id: String, json: String):
    var data = JSON.parse_string(json)
    print(data)
```

### Leaderboard JSON Format

**Top Scores Payload:**
```json
{
  "leaderboard_id": "leaderboard_id",
  "scores": [
    {
      "rank": "1",
      "score": 1234,
      "display_name": "Player",
      "player_id": "abc",
      "is_player": false
    }
  ]
}
```

**Player Score Payload:**
```json
{
  "leaderboard_id": "leaderboard_id",
  "rank": "12",
  "score": 987,
  "display_name": "You",
  "player_id": "abc",
  "is_player": true
}
```

### Cloud Save Example

```gdscript
func _ready():
    # ... after getting the singleton ...
    play_games.save_game_success.connect(_on_save_game_success)
    play_games.save_game_failed.connect(_on_save_game_failed)
    play_games.load_game_success.connect(_on_load_game_success)
    play_games.load_game_failed.connect(_on_load_game_failed)

func save_player_state():
    var data = JSON.stringify({"hp": 85, "level": 12, "gold": 500})
    play_games.saveGame("autosave", data, "Autosave - Level 12")

func load_player_state():
    play_games.loadGame("autosave")

func _on_save_game_success(save_name: String):
    print("Saved: ", save_name)

func _on_save_game_failed(save_name: String, status_code: int, message: String):
    print("Save failed: ", message)

func _on_load_game_success(save_name: String, data: String):
    var state = JSON.parse_string(data)
    print("HP: ", state.get("hp", 100))

func _on_load_game_failed(save_name: String, status_code: int, message: String):
    print("Load failed: ", message)
```

## API Reference

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `helloWorld()` | String | Test method, returns "Hello from PlayGamesPlugin v2!" |
| `signIn()` | void | Trigger manual sign-in UI |
| `signOut()` | void | No-op (v2 handles via OS settings) |
| `isSignedIn()` | bool | Check current auth status |
| `refreshAuthStatus()` | void | Re-check auth (call on app resume) |
| `getPlayerId()` | String | Get player's unique ID |
| `getPlayerDisplayName()` | String | Get player's display name |
| `saveGame(saveName, data, description)` | void | Save data to a named cloud snapshot |
| `loadGame(saveName)` | void | Load data from a named cloud snapshot |
| `deleteGame(saveName)` | void | Delete a cloud snapshot |
| `submitScore(leaderboardId, score)` | void | Submit score to a leaderboard |
| `loadTopScores(leaderboardId, timeSpan, collection, maxResults, forceReload)` | void | Load top scores (returns JSON via signal) |
| `loadPlayerScore(leaderboardId, timeSpan, collection, forceReload)` | void | Load current player score/rank (returns JSON via signal) |

Snake_case compatibility aliases are also exposed for integrations that prefer Godot-style naming:
`sign_in`, `sign_out`, `is_signed_in`, `refresh_auth_status`, `save_game`, `load_game`, `delete_game`.

### Method Naming Compatibility

The plugin exposes both camelCase and snake_case for core auth/cloud methods:

| camelCase | snake_case |
|-----------|------------|
| `signIn()` | `sign_in()` |
| `signOut()` | `sign_out()` |
| `isSignedIn()` | `is_signed_in()` |
| `refreshAuthStatus()` | `refresh_auth_status()` |
| `saveGame(...)` | `save_game(...)` |
| `loadGame(...)` | `load_game(...)` |
| `deleteGame(...)` | `delete_game(...)` |

### Signals

| Signal | Parameters | Description |
|--------|------------|-------------|
| `sign_in_success` | player_id: String, player_name: String | Emitted on successful manual sign-in |
| `sign_in_failed` | none | Emitted when sign-in fails/declined |
| `player_info_loaded` | player_id: String, player_name: String | Emitted when player info is loaded (auto sign-in) |
| `save_game_success` | save_name: String | Emitted when cloud save succeeds |
| `save_game_failed` | save_name: String, status_code: int, message: String | Emitted when cloud save fails |
| `load_game_success` | save_name: String, data: String | Emitted when cloud load succeeds (data is the saved string) |
| `load_game_failed` | save_name: String, status_code: int, message: String | Emitted when cloud load fails |
| `delete_game_success` | save_name: String | Emitted when cloud delete succeeds |
| `delete_game_failed` | save_name: String, status_code: int, message: String | Emitted when cloud delete fails |
| `leaderboard_submit_success` | leaderboard_id: String | Emitted when score submit succeeds |
| `leaderboard_submit_failed` | leaderboard_id: String, status_code: int, message: String | Emitted when score submit fails |
| `leaderboard_top_scores_loaded` | leaderboard_id: String, json: String | Emitted with top scores JSON |
| `leaderboard_top_scores_failed` | leaderboard_id: String, status_code: int, message: String | Emitted when top scores load fails |
| `leaderboard_player_score_loaded` | leaderboard_id: String, json: String | Emitted with player score JSON |
| `leaderboard_player_score_failed` | leaderboard_id: String, status_code: int, message: String | Emitted when player score load fails |

## Configuration

### SDK Versions

| Component | Version |
|-----------|---------|
| Compile SDK | 35 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 |
| Play Games SDK | 21.0.0 |
| Godot Library | 4.5.1.stable |
| JDK | 17 |

### Permissions (Automatic)
- `INTERNET` - Required for Play Games Services
- `ACCESS_NETWORK_STATE` - Network connectivity check

### Getting Your App ID

1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app → **Play Games Services** → **Setup and management** → **Configuration**
3. Copy the numeric **Project ID** (12+ digits, not the OAuth client ID)

## Troubleshooting

### Plugin Singleton Not Found on Android

This is the most common issue. Check the following:

1. **AndroidManifest.xml must have v2 plugin registration:**
   ```xml
   <application>
       <meta-data
           android:name="org.godotengine.plugin.v2.PlayGamesPlugin"
           android:value="com.mladenstojanovic.playgamesplugin.PlayGamesPlugin" />
   </application>
   ```

2. **AAR files must be in the correct location** with correct names:
   - `addons/play_games_plugin/PlayGamesPlugin-debug.aar`
   - `addons/play_games_plugin/PlayGamesPlugin-release.aar`

3. **Plugin must be enabled** in Project Settings → Plugins

4. **Android Build Template must be installed** (Project → Install Android Build Template)

### Sign-in Always Fails

- Verify your App ID is correct in `play_games_plugin.gd`
- Check that your app's SHA-1 fingerprint is registered in Play Console
- Ensure Play Games Services is properly configured in Play Console
- Check logcat for detailed error messages:
  ```bash
  adb logcat | grep -E "(PlayGames|GamesSignIn)"
  ```

### Common Logcat Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `DEVELOPER_ERROR` | SHA-1 mismatch or wrong App ID | Register debug/release SHA-1 in Play Console |
| `SIGN_IN_REQUIRED` | User hasn't signed in before | Call `signIn()` to show sign-in UI |
| `NETWORK_ERROR` | No internet connection | Check device connectivity |

### "Play Games Services not available"

- Device must have Google Play Services installed
- Some emulators don't include Play Services (use a physical device)

### Resource Linking Errors (attr/colorPrimary, etc.)

If you see errors about missing Material Components resources:
- The AAR should NOT include Material Components resources
- Rebuild the plugin with `compileOnly` dependencies (not `implementation`)
- Current build.gradle.kts uses:
  ```kotlin
  dependencies {
      compileOnly("org.godotengine:godot:4.5.1.stable")
      compileOnly("com.google.android.gms:play-services-games-v2:21.0.0")
  }
  ```

### Debug Builds: Sign-in/Leaderboard Failures

If leaderboard calls crash or fail in **debug** builds:
- Make sure your **debug keystore SHA-1** is added in Play Console → Play Games Services.
- If the user is not signed in, leaderboard calls will fail. Guard with `isSignedIn()` in GDScript.
- Physical device is recommended; some emulators don’t support Play Games sign-in.

## Roadmap

### v1.1 - Achievements
- `unlockAchievement(id)`
- `incrementAchievement(id, steps)`
- `showAchievementsUI()`

### v1.2 - Leaderboards
- `submitScore(leaderboardId, score)`
- `loadTopScores(leaderboardId, timeSpan, collection, maxResults, forceReload)`
- `loadPlayerScore(leaderboardId, timeSpan, collection, forceReload)`

## Resources

- [Play Games Services v2 Migration Guide](https://developer.android.com/games/pgs/android/migrate-to-v2)
- [Android Sign-in Documentation](https://developer.android.com/games/pgs/android/android-signin)
- [Godot Android Plugin Documentation](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [Google Play Console](https://play.google.com/console)
- [Godot Android Plugin Template](https://github.com/m4gr3d/Godot-Android-Plugin-Template)

## License

MIT License - Use freely in your Godot projects.
