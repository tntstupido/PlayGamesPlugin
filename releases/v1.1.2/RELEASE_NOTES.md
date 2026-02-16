# PlayGamesPlugin v1.1.2

## Highlights
- Fixed Android crash in leaderboard player-score flow (`loadPlayerScore`).
- Normalized failed-signal numeric typing to Godot-compatible long values.
- Rebuilt and packaged fresh `debug` and `release` AAR files.

## Fix Details
- `leaderboard_player_score_failed` no longer triggers runtime type mismatch crash path.
- `loadPlayerScore` now emits safe player-score payload fallback instead of crashing when player score is missing/unavailable.
- Updated failed signal status-code typing across plugin callbacks.

## Included in This Release Package
- `play_games_plugin/plugin.cfg` (`version="1.1.2"`)
- `play_games_plugin/play_games_plugin.gd`
- `play_games_plugin/PlayGamesPlugin-debug.aar`
- `play_games_plugin/PlayGamesPlugin-release.aar`

## Install
1. Download `PlayGamesPlugin-v1.1.2-addons.zip`.
2. Extract `play_games_plugin`.
3. Copy extracted folder into your Godot project at:
   - `addons/play_games_plugin`
4. Enable plugin in Godot Editor:
   - `Project Settings -> Plugins -> PlayGamesPlugin -> Enable`
