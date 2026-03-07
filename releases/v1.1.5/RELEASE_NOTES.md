# PlayGamesPlugin v1.1.5

## Highlights
- Hardened leaderboard rank handling for unknown rank states (`rank = -1`) without treating them as request failures.
- Extended leaderboard payload diagnostics with:
  - `rank_known`
  - `rank_value`
  - `time_span`
  - `collection`
- Added structured leaderboard load logs for faster tester triage.
- Rebuilt and packaged fresh `debug` and `release` AAR files.

## Included in This Release Package
- `play_games_plugin/plugin.cfg` (`version="1.1.5"`)
- `play_games_plugin/play_games_plugin.gd`
- `play_games_plugin/PlayGamesPlugin-debug.aar`
- `play_games_plugin/PlayGamesPlugin-release.aar`

## Install
1. Download `PlayGamesPlugin-v1.1.5-addons.zip`.
2. Extract `play_games_plugin`.
3. Copy extracted folder into your Godot project at:
   - `addons/play_games_plugin`
4. Enable plugin in Godot Editor:
   - `Project Settings -> Plugins -> PlayGamesPlugin -> Enable`
