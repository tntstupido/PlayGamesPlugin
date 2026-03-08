# PlayGamesPlugin - Godot Android Plugin

A Godot 4.2+ Android plugin for Google Play Games Services v2.

**Tested with:** Godot 4.5.1, Play Games Services v2 SDK 21.0.0, Android SDK 35

## Overview

This plugin integrates Google Play Games Services into Godot games using the modern v2 API. It uses the Godot v2 plugin format with EditorExportPlugin for seamless integration.

### Features

| Feature | Status | Description |
|---------|--------|-------------|
| Automatic Sign-in | ✅ | Adaptive startup auth check (silent on most devices, deferred explicit flow on Xiaomi-family devices) |
| Manual Sign-in | ✅ | Trigger sign-in for users who declined initially |
| Player Info | ✅ | Get player ID and display name |
| Cloud Save | ✅ | Save/load/delete game data via Snapshots API |
| Signals | ✅ | Async events via Godot signals |
| Achievements | ⏳ | Planned for v1.2 |
| Leaderboards | ✅ | Submit scores + load top scores + player rank |

### Play Games Services v2 Benefits

- **No Play Games app required** - Users don't need to install the Play Games app
- **Automatic sign-in** - Returning users are signed in silently
- **OS-managed accounts** - Sign-out handled in Android settings, not in-app
- **Simplified API** - No deprecated GoogleSignIn classes

### Production Hardening Notes (March 2026)

- Failure status codes are emitted as Kotlin `Long` and declared as Godot signal `Long` (`int64`) to avoid bridge crashes such as `Invalid type for argument #1. Should be of type long`.
- Snapshot operations (`saveGame`, `loadGame`, `deleteGame`) are guarded when signed out and emit `*_failed` with status `-3` and `"Not signed in"` instead of entering unstable resolution flows.
- Leaderboard operations retry sign-in one time automatically when API returns `SIGN_IN_REQUIRED` (`statusCode=4`) and then retry the same request once.
- `loadPlayerScore(...)` always emits `leaderboard_player_score_loaded` (including fallback/error payloads with optional `status_code` and `error` fields) to keep UI rendering stable.
- `leaderboard_player_score_failed` remains declared for API compatibility but is currently not emitted by the runtime implementation.
- Startup auth check is intentionally skipped on Xiaomi/Redmi/Poco devices to avoid repeated profile chooser prompts; explicit login UI should be triggered from game UI when needed.
- Recommended app-side lifecycle hook: call `refreshAuthStatus()` on `NOTIFICATION_APPLICATION_RESUMED`.


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
# Example for v1.1.5
unzip PlayGamesPlugin-v1.1.5-addons.zip
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

func _on_sign_in_failed(status_code: int64, message: String):
    print("Sign-in failed: ", status_code, " ", message)

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
  "time_span": "daily",
  "collection": "public",
  "scores": [
    {
      "rank": 1,
      "rank_value": 1,
      "rank_known": true,
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
  "time_span": "daily",
  "collection": "public",
  "rank": 12,
  "rank_value": 12,
  "rank_known": true,
  "score": 987,
  "display_name": "You",
  "player_id": "abc",
  "is_player": true
}
```

`rank = -1` with `rank_known = false` means Google Play Games returned an unknown rank (for example, score exists but public rank is hidden).

### Cloud Save Notes

- Cloud Save methods require the user to be authenticated (`isSignedIn() == true`).
- **Saved Games must be enabled in Play Console** for the linked Play Games project. If disabled, snapshot operations fail with `IllegalStateException: Cannot use snapshots without enabling the 'Saved Game' feature`.
- If called while signed out, the plugin fails gracefully and emits `*_failed` with status code `-3` and message `"Not signed in"` (instead of triggering Play Games resolution UI unexpectedly).
- On some device/account states, Games APIs can still return `SIGN_IN_REQUIRED` after a successful sign-in callback. Guard cloud/leaderboard UI and allow manual retry from the player.
- Prefer waiting for `sign_in_success` or `player_info_loaded` before calling `loadGame`, `saveGame`, or `deleteGame`.

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
    if not play_games.isSignedIn():
        return
    play_games.loadGame("autosave")

func _on_save_game_success(save_name: String):
    print("Saved: ", save_name)

func _on_save_game_failed(save_name: String, status_code: int64, message: String):
    print("Save failed: ", message)

func _on_load_game_success(save_name: String, data: String):
    var state = JSON.parse_string(data)
    print("HP: ", state.get("hp", 100))

func _on_load_game_failed(save_name: String, status_code: int64, message: String):
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
| `loadTopScores(leaderboardId, timeSpan, collection, maxResults, forceReload)` | void | Load top scores (returns JSON via signal). `timeSpan`: `daily`/`weekly`/`all_time` (others fallback to `all_time`), `collection`: `public`/`friends` (others fallback to `public`) |
| `loadPlayerScore(leaderboardId, timeSpan, collection, forceReload)` | void | Load current player score/rank (returns JSON via signal). Uses same `timeSpan`/`collection` normalization; `forceReload` is currently accepted for API symmetry |

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
| `sign_in_success` | player_id: String, player_name: String | Emitted when manual sign-in succeeds; `player_info_loaded` is the authoritative profile refresh signal |
| `sign_in_failed` | status_code: int64, message: String | Emitted when sign-in fails/declined |
| `player_info_loaded` | player_id: String, player_name: String | Emitted when player info is loaded (auto sign-in) |
| `save_game_success` | save_name: String | Emitted when cloud save succeeds |
| `save_game_failed` | save_name: String, status_code: int64, message: String | Emitted when cloud save fails |
| `load_game_success` | save_name: String, data: String | Emitted when cloud load succeeds (data is the saved string) |
| `load_game_failed` | save_name: String, status_code: int64, message: String | Emitted when cloud load fails |
| `delete_game_success` | save_name: String | Emitted when cloud delete succeeds |
| `delete_game_failed` | save_name: String, status_code: int64, message: String | Emitted when cloud delete fails |
| `leaderboard_submit_success` | leaderboard_id: String | Emitted when score submit succeeds |
| `leaderboard_submit_failed` | leaderboard_id: String, status_code: int64, message: String | Emitted when score submit fails |
| `leaderboard_top_scores_loaded` | leaderboard_id: String, json: String | Emitted with top scores JSON |
| `leaderboard_top_scores_failed` | leaderboard_id: String, status_code: int64, message: String | Emitted when top scores load fails |
| `leaderboard_player_score_loaded` | leaderboard_id: String, json: String | Emitted with player score JSON on both success and fallback/error paths |
| `leaderboard_player_score_failed` | leaderboard_id: String, status_code: int64, message: String | Declared for API compatibility; currently not emitted |

> Note: failure status values are emitted as 64-bit numeric values from Kotlin (`Long`) for reliable Godot bridge marshalling. In GDScript, handling them as `int` or `Variant` is safe.
>
> Common status sentinels used by the plugin: `-2` (user canceled), `-3` (snapshot call while signed out), `-1` (local/unknown failure). Non-negative values usually map to Play Games `ApiException.statusCode`.

## Configuration

### SDK Versions

| Component | Version |
|-----------|---------|
| Compile SDK | 35 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 |
| Play Games SDK (AAR compileOnly) | 21.0.0 |
| Play Games SDK (Godot export dependency) | 20.1.0 |
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
- Ensure your exported app declares `INTERNET` permission (`export_presets.cfg`).
- Verify that your test account has an initialized Play Games profile on that specific device/account state.
- Check logcat for detailed error messages:
  ```bash
  adb logcat | grep -E "(PlayGames|GamesSignIn)"
  ```

### Xiaomi / MIUI Specific Notes

- Xiaomi-family devices (Xiaomi/Redmi/Poco) can repeatedly show account/profile chooser dialogs if auth checks run too early at startup.
- Current plugin behavior intentionally defers startup auth check on those devices; it waits for explicit `signIn()` calls from UI flow.
- If you require auto-login UX, trigger it after first menu frame/UI settle, not during earliest app bootstrap.

### Common Logcat Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `DEVELOPER_ERROR` | SHA-1 mismatch or wrong App ID | Register debug/release SHA-1 in Play Console |
| `SIGN_IN_REQUIRED` | No valid active Games auth session for that API call | Trigger `signIn()`; plugin retries once for leaderboard calls |
| `NETWORK_ERROR` | No internet connection | Check device connectivity |
| `IllegalStateException: Cannot use snapshots without enabling the 'Saved Game' feature` | Saved Games disabled in Play Console | Enable **Saved games** in Play Games Services configuration |
| `java.lang.IllegalArgumentException: Invalid type for argument #1. Should be of type long` | Signal argument type mismatch (`Int` emitted for signal declared as `long`) | Emit Kotlin `Long` (`toLong()`), declare signal parameter as `Long::class.javaObjectType` |
| `Not signed in` with status `-3` on snapshot calls | Cloud operation called before confirmed auth | Wait for `sign_in_success`/`player_info_loaded` or gate with `isSignedIn()` |

### Leaderboard Player Score Fallback Payload

`loadPlayerScore(...)` is resilient by design: if API lookup fails, plugin still emits `leaderboard_player_score_loaded` with a fallback payload (`rank: "-"`, `score: 0`) and includes optional `status_code` / `error` fields for UI diagnostics.

This keeps leaderboard UI rendering stable without forcing a hard failure signal path.

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

### v1.2 - Achievements
- `unlockAchievement(id)`
- `incrementAchievement(id, steps)`
- `showAchievementsUI()`

### v1.3 - Leaderboard UX Extensions
- Optional native leaderboard intent wrappers (`showLeaderboardUI`, `showAllLeaderboardsUI`)
- Optional paging/filter helpers for large custom leaderboard views

## Resources

- [Play Games Services v2 Migration Guide](https://developer.android.com/games/pgs/android/migrate-to-v2)
- [Android Sign-in Documentation](https://developer.android.com/games/pgs/android/android-signin)
- [Godot Android Plugin Documentation](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [Google Play Console](https://play.google.com/console)
- [Godot Android Plugin Template](https://github.com/m4gr3d/Godot-Android-Plugin-Template)

## License

MIT License - Use freely in your Godot projects.
