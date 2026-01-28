# PlayGamesPlugin Development Guide

Technical reference for developing and extending the PlayGamesPlugin.

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│ Android Studio Project                                           │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ PlayGamesPlugin.kt (GodotPlugin)                           │ │
│  │  - Extends GodotPlugin                                      │ │
│  │  - Uses @UsedByGodot annotation for exposed methods         │ │
│  │  - Emits signals via emitSignal()                           │ │
│  │  - Initializes PlayGamesSdk in onMainCreate()               │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓ builds to                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ AAR (Android Archive)                                       │ │
│  │  - Contains compiled Kotlin code                            │ │
│  │  - Contains godot_plugin.json in assets/                    │ │
│  │  - Contains AndroidManifest.xml                             │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              ↓ used by
┌──────────────────────────────────────────────────────────────────┐
│ Godot EditorExportPlugin                                         │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ play_games_plugin.gd                                        │ │
│  │  - Provides AAR paths via _get_android_libraries()          │ │
│  │  - Declares Maven deps via _get_android_dependencies()      │ │
│  │  - Injects manifest entries via _get_android_manifest_*()   │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/.../PlayGamesPlugin.kt` | Main plugin class |
| `app/src/main/assets/godot_plugin.json` | Plugin metadata for Godot |
| `app/src/main/AndroidManifest.xml` | Permissions + **v2 plugin registration** |
| `app/build.gradle.kts` | Dependencies and build config |
| `addons/play_games_plugin/play_games_plugin.gd` | Godot export plugin |

## Critical: Godot v2 Plugin Registration

For Godot 4.2+, the plugin **must** be registered in AndroidManifest.xml:

```xml
<application>
    <meta-data
        android:name="org.godotengine.plugin.v2.PlayGamesPlugin"
        android:value="com.mladenstojanovic.playgamesplugin.PlayGamesPlugin" />
</application>
```

Without this, `Engine.has_singleton("PlayGamesPlugin")` will return `false` even though the plugin code runs.

## Play Games Services v2 API

### SDK Initialization

```kotlin
// Must be called before any other Play Games APIs
PlayGamesSdk.initialize(activity)
```

### Authentication

```kotlin
// Get sign-in client
val gamesSignInClient = PlayGames.getGamesSignInClient(activity)

// Check if authenticated (silent check)
gamesSignInClient.isAuthenticated.addOnCompleteListener { task ->
    val isAuthenticated = task.isSuccessful && task.result.isAuthenticated
}

// Manual sign-in (shows UI)
gamesSignInClient.signIn().addOnCompleteListener { task ->
    if (task.isSuccessful && task.result.isAuthenticated) {
        // Success
    }
}
```

### Player Info

```kotlin
PlayGames.getPlayersClient(activity).currentPlayer
    .addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val player = task.result
            val playerId = player.playerId
            val displayName = player.displayName
        }
    }
```

### Cloud Save (Snapshots)

```kotlin
// Get snapshots client
val snapshotsClient = PlayGames.getSnapshotsClient(activity)

// Open a snapshot (create if it doesn't exist)
snapshotsClient.open("save_name", true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
    .addOnCompleteListener { task ->
        val dataOrConflict = task.result
        if (!dataOrConflict.isConflict) {
            val snapshot = dataOrConflict.data!!

            // Write data
            snapshot.snapshotContents.writeBytes(data.toByteArray(Charsets.UTF_8))

            // Commit with metadata
            val metadataChange = SnapshotMetadataChange.Builder()
                .setDescription("Save description")
                .build()
            snapshotsClient.commitAndClose(snapshot, metadataChange)
        }
    }

// Read data from snapshot
snapshotsClient.open("save_name", false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
    .addOnCompleteListener { task ->
        val snapshot = task.result.data!!
        val bytes = snapshot.snapshotContents.readFully()
        val gameData = String(bytes, Charsets.UTF_8)
        snapshotsClient.discardAndClose(snapshot)
    }

// Delete snapshot
snapshotsClient.open("save_name", false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
    .addOnCompleteListener { task ->
        val snapshot = task.result.data!!
        snapshotsClient.delete(snapshot.metadata)
    }
```

**Conflict Resolution Policies:**
- `RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED` — keeps the newest save (default)
- `RESOLUTION_POLICY_LONGEST_PLAYTIME` — keeps the save with longest playtime
- `RESOLUTION_POLICY_MANUAL` — requires manual conflict resolution

### Achievements (To Implement)

```kotlin
// Unlock achievement
PlayGames.getAchievementsClient(activity)
    .unlock("achievement_id")

// Increment achievement
PlayGames.getAchievementsClient(activity)
    .increment("achievement_id", 1)

// Show achievements UI
PlayGames.getAchievementsClient(activity)
    .achievementsIntent
    .addOnSuccessListener { intent ->
        activity.startActivityForResult(intent, RC_ACHIEVEMENTS)
    }
```

### Leaderboards (To Implement)

```kotlin
// Submit score
PlayGames.getLeaderboardsClient(activity)
    .submitScore("leaderboard_id", 1000)

// Show leaderboard UI
PlayGames.getLeaderboardsClient(activity)
    .getLeaderboardIntent("leaderboard_id")
    .addOnSuccessListener { intent ->
        activity.startActivityForResult(intent, RC_LEADERBOARD)
    }

// Show all leaderboards
PlayGames.getLeaderboardsClient(activity)
    .allLeaderboardsIntent
    .addOnSuccessListener { intent ->
        activity.startActivityForResult(intent, RC_LEADERBOARD)
    }
```

## Godot Plugin Integration

### Exposing Methods to GDScript

Use the `@UsedByGodot` annotation:

```kotlin
@UsedByGodot
fun myMethod(): String {
    return "Hello from Kotlin"
}
```

**Note:** The old `getPluginMethods()` approach is deprecated. Use `@UsedByGodot` instead.

### Emitting Signals

1. Declare signals in `getPluginSignals()`:

```kotlin
override fun getPluginSignals(): Set<SignalInfo> {
    return setOf(
        SignalInfo("my_signal", String::class.java),
        SignalInfo("another_signal")
    )
}
```

2. Emit from async callbacks:

```kotlin
getActivity()?.runOnUiThread {
    emitSignal("my_signal", "data")
}
```

### Lifecycle Methods

```kotlin
override fun onMainCreate(activity: Activity?): View? {
    // Called when the main activity is created
    // Initialize SDK here
    return null
}

override fun onMainResume() {
    // Called when activity resumes
}

override fun onMainPause() {
    // Called when activity pauses
}

override fun onMainDestroy() {
    // Called when activity is destroyed
}
```

## Build Commands

```bash
# Clean build
./gradlew clean

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Both
./gradlew assembleDebug assembleRelease

# Output locations
# app/build/outputs/aar/app-debug.aar
# app/build/outputs/aar/app-release.aar
```

## Dependencies

Current dependencies in `app/build.gradle.kts`:

```kotlin
dependencies {
    // Use compileOnly to avoid bundling resources that conflict with Godot
    compileOnly("org.godotengine:godot:4.5.1.stable")
    compileOnly("com.google.android.gms:play-services-games-v2:21.0.0")
}
```

**Important Notes:**
- Use `compileOnly` instead of `implementation` to avoid bundling Material Components resources
- `play-services-auth` is NOT needed for v2 - the old GoogleSignIn API is deprecated
- The actual dependencies are provided at runtime via `_get_android_dependencies()` in the EditorExportPlugin

### Why compileOnly?

Using `implementation` bundles resources from transitive dependencies (Material Components, AppCompat) into the AAR. These conflict with Godot's resources, causing errors like:
- `attr/colorPrimary not found`
- `Theme.MaterialComponents.DayNight.DarkActionBar not found`

Using `compileOnly` keeps the AAR minimal - only your plugin code is bundled.

## Adding New Features

### 1. Add Kotlin Method

```kotlin
// In PlayGamesPlugin.kt
@UsedByGodot
fun newFeature(param: String): Boolean {
    // Implementation
    return true
}
```

### 2. Add Signal (if async)

```kotlin
override fun getPluginSignals(): Set<SignalInfo> {
    return setOf(
        // ... existing signals
        SignalInfo("new_feature_complete", Boolean::class.java)
    )
}
```

### 3. Rebuild

```bash
./gradlew clean assembleDebug assembleRelease
```

### 4. Update Godot Plugin

Copy new AAR files to `addons/play_games_plugin/` in Godot project.

### 5. Update Documentation

- Add method to README.md API Reference table
- Add signal to Signals table
- Update example code if needed

## Testing

### On Device

1. Build and export from Godot
2. Install APK: `adb install -r game.apk`
3. View logs: `adb logcat | grep -E "(PlayGames|Godot)"`

### Useful Logcat Filters

```bash
# All plugin logs
adb logcat | grep PlayGamesPlugin

# Play Games SDK logs
adb logcat | grep -E "(GamesSignIn|PlayGames)"

# Godot logs
adb logcat | grep godot
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `DEVELOPER_ERROR` | SHA-1 mismatch | Register debug/release SHA-1 in Play Console |
| `ClassNotFoundException` | Missing dependency | Check AAR includes all deps |
| Signal not received | Wrong thread | Use `runOnUiThread { emitSignal() }` |
| Method not found | Missing annotation | Add `@UsedByGodot` |
| Singleton not found | Missing v2 registration | Add meta-data to AndroidManifest.xml |
| Resource linking errors | Material Components in AAR | Use `compileOnly` instead of `implementation` |

## Version History

### v1.0.1 (Current)
- Cloud Save (Snapshots API): save, load, delete game data
- All Cloud Save operations with async signals

### v1.0.0
- Play Games Services v2 integration
- Automatic sign-in
- Manual sign-in
- Player info retrieval
- Godot signals for async events

### Planned: v1.1.0
- Achievements support

### Planned: v1.2.0
- Leaderboards support

## Resources

### Official Documentation

- [Play Games Services v2 Android](https://developer.android.com/games/pgs/android/android-signin)
- [Migration Guide v1 → v2](https://developer.android.com/games/pgs/android/migrate-to-v2)
- [Godot Android Plugins](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_plugin.html)
- [GodotPlugin API](https://docs.godotengine.org/en/stable/classes/class_editorexportplugin.html)

### API References

- [GamesSignInClient](https://developers.google.com/android/reference/com/google/android/gms/games/GamesSignInClient)
- [PlayersClient](https://developers.google.com/android/reference/com/google/android/gms/games/PlayersClient)
- [SnapshotsClient](https://developers.google.com/android/reference/com/google/android/gms/games/SnapshotsClient)
- [AchievementsClient](https://developers.google.com/android/reference/com/google/android/gms/games/AchievementsClient)
- [LeaderboardsClient](https://developers.google.com/android/reference/com/google/android/gms/games/LeaderboardsClient)

### Sample Code

- [Google Play Games Samples](https://github.com/playgameservices/android-basic-samples)
- [Godot Android Plugin Template](https://github.com/m4gr3d/godot-android-plugin-template)
