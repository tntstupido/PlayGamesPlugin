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
| Signals | ✅ | Async events via Godot signals |
| Achievements | ⏳ | Planned for v1.1 |
| Leaderboards | ⏳ | Planned for v2.0 |

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

### Signals

| Signal | Parameters | Description |
|--------|------------|-------------|
| `sign_in_success` | player_id: String, player_name: String | Emitted on successful manual sign-in |
| `sign_in_failed` | none | Emitted when sign-in fails/declined |
| `player_info_loaded` | player_id: String, player_name: String | Emitted when player info is loaded (auto sign-in) |

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

## Roadmap

### v1.1 - Achievements
- `unlockAchievement(id)`
- `incrementAchievement(id, steps)`
- `showAchievementsUI()`

### v2.0 - Leaderboards
- `submitScore(leaderboardId, score)`
- `showLeaderboardUI(leaderboardId)`
- `showAllLeaderboardsUI()`

## Resources

- [Play Games Services v2 Migration Guide](https://developer.android.com/games/pgs/android/migrate-to-v2)
- [Android Sign-in Documentation](https://developer.android.com/games/pgs/android/android-signin)
- [Godot Android Plugin Documentation](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [Google Play Console](https://play.google.com/console)
- [Godot Android Plugin Template](https://github.com/m4gr3d/Godot-Android-Plugin-Template)

## License

MIT License - Use freely in your Godot projects.
