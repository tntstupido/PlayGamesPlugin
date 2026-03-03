# PlayGamesPlugin v1.1.4

## Highlights
- Improved stability for signal emission between Kotlin and Godot by enforcing 64-bit failure status typing.
- Added safe signal bridge wrapper (`emitSignalSafe`) to avoid runtime bridge crashes from unexpected argument mismatches.
- Added leaderboard auth recovery: one retry sign-in path when Play Games returns `SIGN_IN_REQUIRED`.
- Added Xiaomi/Redmi/Poco startup auth deferral to avoid repeated profile chooser prompts on affected devices.
- Rebuilt and packaged fresh `debug` and `release` AAR files.

## Included in This Release Package
- `play_games_plugin/plugin.cfg` (`version="1.1.4"`)
- `play_games_plugin/play_games_plugin.gd`
- `play_games_plugin/PlayGamesPlugin-debug.aar`
- `play_games_plugin/PlayGamesPlugin-release.aar`

## Install
1. Download `PlayGamesPlugin-v1.1.4-addons.zip`.
2. Extract `play_games_plugin`.
3. Copy extracted folder into your Godot project at:
   - `addons/play_games_plugin`
4. Enable plugin in Godot Editor:
   - `Project Settings -> Plugins -> PlayGamesPlugin -> Enable`
