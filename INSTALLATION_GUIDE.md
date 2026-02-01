# PlayGamesPlugin Installation Guide

This guide covers installing the PlayGamesPlugin into a Godot 4.x project.

## Plugin Architecture (v2)

Godot 4.2+ uses the **EditorExportPlugin** system for Android plugins. This replaces the deprecated `.gdap` file format.

### How It Works

```
┌─────────────────────────────────────────────────────────────┐
│ Godot Editor                                                │
│  ├── Loads plugin.cfg                                       │
│  ├── Runs play_games_plugin.gd (EditorPlugin)              │
│  └── Registers AndroidExportPlugin                          │
│       ├── _get_android_libraries() → AAR files              │
│       ├── _get_android_dependencies() → Maven deps          │
│       └── _get_android_manifest_*() → Manifest entries      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ Android Export                                              │
│  ├── Includes AAR in APK                                    │
│  ├── Resolves Maven dependencies                            │
│  └── Merges manifest with APP_ID                            │
└─────────────────────────────────────────────────────────────┘
```

## Installation Steps

### Step 1: Get the Plugin Files

You need these files from the PlayGamesPlugin project:

| File | Source | Purpose |
|------|--------|---------|
| `plugin.cfg` | `addons/play_games_plugin/` | EditorPlugin config |
| `play_games_plugin.gd` | `addons/play_games_plugin/` | Export logic |
| `PlayGamesPlugin-debug.aar` | `app/build/outputs/aar/app-debug.aar` | Debug build |
| `PlayGamesPlugin-release.aar` | `app/build/outputs/aar/app-release.aar` | Release build |

### Step 2: Create Directory Structure

In your Godot project, create:

```
your_godot_project/
└── addons/
    └── play_games_plugin/
        ├── plugin.cfg
        ├── play_games_plugin.gd
        ├── PlayGamesPlugin-debug.aar
        └── PlayGamesPlugin-release.aar
```

### Step 3: Copy Files

```bash
# From PlayGamesPlugin directory
cp -r addons/play_games_plugin YOUR_GODOT_PROJECT/addons/

# Copy AAR files (after building)
cp app/build/outputs/aar/app-debug.aar YOUR_GODOT_PROJECT/addons/play_games_plugin/PlayGamesPlugin-debug.aar
cp app/build/outputs/aar/app-release.aar YOUR_GODOT_PROJECT/addons/play_games_plugin/PlayGamesPlugin-release.aar
```

### Step 4: Configure Your App ID

Edit `addons/play_games_plugin/play_games_plugin.gd` in your Godot project:

```gdscript
# Line 24 - Replace with your App ID from Google Play Console
const PLAY_GAMES_APP_ID = "123456789012"
```

**Where to find your App ID:**
1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app
3. Navigate to: **Play Games Services** → **Setup and management** → **Configuration**
4. Copy the numeric ID (12+ digits)

**Debug keystore note (important for testing):**
- Add the **debug keystore SHA-1** to Play Console → Play Games Services, or sign-in/leaderboards will fail in debug builds.

### Step 5: Enable the Plugin

1. Open your Godot project
2. Go to **Project** → **Project Settings**
3. Click the **Plugins** tab
4. Find "PlayGamesPlugin" in the list
5. Check the **Enable** checkbox

### Step 6: Verify Installation

Create a test script:

```gdscript
extends Node

func _ready():
    if Engine.has_singleton("PlayGamesPlugin"):
        var plugin = Engine.get_singleton("PlayGamesPlugin")
        print("Plugin loaded: ", plugin.helloWorld())
    else:
        print("Plugin not available (only works on Android)")
```

**Note:** The plugin singleton is only available on Android. On desktop, `has_singleton()` returns false.

## Exporting for Android

### Export Prerequisites

1. Android export template installed
2. **Android Build Template installed** (Project → Install Android Build Template)
3. Debug keystore or release keystore configured
4. App ID configured in `play_games_plugin.gd`

### Export Steps

1. Go to **Project** → **Install Android Build Template** (if not done already)
2. Go to **Project** → **Export**
3. Select your Android preset (or create one)
4. Configure:
   - **Package** → **Unique Name**: Must match Play Console
   - **Keystore**: Debug or release keystore
5. Click **Export Project** or **Export APK**

The plugin automatically:
- Includes the appropriate AAR (debug/release)
- Adds the Play Games Services dependency
- Injects the APP_ID into AndroidManifest.xml

## Migrating from v1 (.gdap format)

If you previously used the v1 plugin format:

### Remove Old Files

Delete these deprecated files if present:
```
android/build/PlayGamesPlugin.gdap     # Delete
android/plugins/PlayGamesPlugin.aar    # Delete
```

### Install v2 Files

Follow the installation steps above to add the new `addons/play_games_plugin/` folder.

### Update Your Code

v2 API changes:
- `signOut()` is now a no-op (account management via OS settings)
- Added signals: `sign_in_success`, `sign_in_failed`, `player_info_loaded`
- Added `refreshAuthStatus()` for checking auth on resume

## Troubleshooting

### Plugin Not Showing in Project Settings

- Check that `plugin.cfg` exists and has correct format
- Ensure the folder is named exactly `play_games_plugin`
- Try restarting Godot

### Plugin Singleton Not Found on Android

This is the most common issue. The plugin loads but `Engine.has_singleton("PlayGamesPlugin")` returns false.

**Root cause:** Missing v2 plugin registration in AndroidManifest.xml

**Solution:** Ensure the AAR's AndroidManifest.xml contains:
```xml
<application>
    <meta-data
        android:name="org.godotengine.plugin.v2.PlayGamesPlugin"
        android:value="com.mladenstojanovic.playgamesplugin.PlayGamesPlugin" />
</application>
```

If you built the plugin yourself, check `app/src/main/AndroidManifest.xml` includes this meta-data.

### Export Fails with AAR Error

- Verify both AAR files exist in `addons/play_games_plugin/`
- Check AAR file names match exactly: `PlayGamesPlugin-debug.aar` and `PlayGamesPlugin-release.aar`

### Export Fails with Resource Linking Errors

If you see errors like `attr/colorPrimary not found` or `Theme.MaterialComponents.DayNight.DarkActionBar not found`:

- The AAR contains conflicting Material Components resources
- Rebuild the plugin with `compileOnly` dependencies instead of `implementation`
- Delete `android/build` folder in Godot project and reinstall Android Build Template

### Sign-in Fails at Runtime

1. Check App ID is configured correctly
2. Verify SHA-1 fingerprint in Play Console matches your keystore
3. Check logcat: `adb logcat | grep -E "(PlayGames|GamesSignIn)"`

### Common Logcat Errors

| Error | Solution |
|-------|----------|
| `DEVELOPER_ERROR` | SHA-1 mismatch or wrong App ID |
| `SIGN_IN_REQUIRED` | User hasn't signed in before |
| `NETWORK_ERROR` | No internet connection |

## File Reference

### plugin.cfg
```ini
[plugin]
name="PlayGamesPlugin"
description="Google Play Games Services integration for Godot"
author="Mladen Stojanovic"
version="1.0.0"
script="play_games_plugin.gd"
```

### play_games_plugin.gd Structure
```gdscript
@tool
extends EditorPlugin

# Registers the AndroidExportPlugin on load
func _enter_tree():
    add_export_plugin(AndroidExportPlugin.new())

class AndroidExportPlugin extends EditorExportPlugin:
    # Configure your App ID here
    const PLAY_GAMES_APP_ID = "YOUR_APP_ID_HERE"

    # Returns AAR file paths
    func _get_android_libraries(platform, debug) -> PackedStringArray

    # Returns Maven dependencies
    func _get_android_dependencies(platform, debug) -> PackedStringArray

    # Injects APP_ID into manifest
    func _get_android_manifest_application_element_contents(platform, debug) -> String
```

### godot_plugin.json (inside AAR)
```json
{
    "plugin_name": "PlayGamesPlugin",
    "plugin_main_class": "com.mladenstojanovic.playgamesplugin.PlayGamesPlugin"
}
```

This file is bundled inside the AAR and tells Godot which class to load.

### AndroidManifest.xml (inside AAR)

**Critical for Godot 4.2+:** The AAR must include v2 plugin registration:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application>
        <!-- This meta-data is REQUIRED for Godot to register the singleton -->
        <meta-data
            android:name="org.godotengine.plugin.v2.PlayGamesPlugin"
            android:value="com.mladenstojanovic.playgamesplugin.PlayGamesPlugin" />
    </application>

</manifest>
```

Without this meta-data entry, the plugin code will run (you may see Play Games sign-in) but `Engine.has_singleton()` will return false.
