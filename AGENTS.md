# AGENTS.md

Instructions for AI agents working on this project.

## Project Overview

PlayGamesPlugin is a Godot 4.2+ Android plugin for Google Play Games Services v2. It is written in Kotlin and exposes methods/signals to GDScript via the Godot plugin system.

## Architecture

- **Kotlin plugin** (`app/src/main/java/.../PlayGamesPlugin.kt`) — extends `GodotPlugin`, uses `@UsedByGodot` annotations to expose methods and `emitSignal()` for async callbacks.
- **Godot EditorExportPlugin** (`addons/play_games_plugin/play_games_plugin.gd`) — bundles AAR files, declares Maven dependencies, injects manifest entries at export time.
- **AAR output** — built via `./gradlew :app:assembleRelease`. Dependencies use `compileOnly` to avoid bundling transitive resources that conflict with Godot.

## Key Conventions

### Adding New Features

1. Add `@UsedByGodot` methods in `PlayGamesPlugin.kt`
2. For async operations, declare signals in `getPluginSignals()` and emit them via `activity.runOnUiThread { emitSignal(...) }`
3. Use the existing `getApiExceptionInfo()` and `logTaskFailure()` helpers for error handling
4. All Play Games API calls follow the pattern: `PlayGames.getXxxClient(activity).method().addOnCompleteListener { ... }`
5. Update `example_plugin_usage.gd` with usage examples
6. Update README.md (API Reference tables) and DEVELOPMENT.md

### Signal Naming Convention

- Success signals: `<feature>_success` (e.g. `save_game_success`)
- Failure signals: `<feature>_failed` with parameters `(context: String, status_code: Int, message: String)`

### Dependencies

- All dependencies in `app/build.gradle.kts` must use `compileOnly` scope
- Runtime dependencies are declared in `play_games_plugin.gd` via `_get_android_dependencies()`
- Both files must stay in sync regarding dependency versions

### Build & Verify

```bash
./gradlew :app:assembleRelease
```

Build must succeed before any PR.

## File Map

| File | Purpose |
|------|---------|
| `app/src/main/java/.../PlayGamesPlugin.kt` | Main plugin — all exposed methods and signals |
| `app/build.gradle.kts` | Build config and dependencies |
| `app/src/main/AndroidManifest.xml` | Permissions and v2 plugin registration |
| `addons/play_games_plugin/play_games_plugin.gd` | Godot export plugin (AAR bundling, Maven deps, manifest injection) |
| `addons/play_games_plugin/plugin.cfg` | EditorPlugin metadata |
| `example_plugin_usage.gd` | GDScript usage examples |
| `README.md` | User-facing documentation and API reference |
| `DEVELOPMENT.md` | Developer reference (architecture, Play Games API examples) |
| `INSTALLATION_GUIDE.md` | Step-by-step installation for Godot projects |

## Current Features

- **Authentication**: automatic sign-in, manual sign-in, auth refresh
- **Player Info**: player ID, display name
- **Cloud Save**: save, load, delete game data via Snapshots API

## Planned Features

- Achievements (unlock, increment, show UI)
- Leaderboards (submit score, show UI)
