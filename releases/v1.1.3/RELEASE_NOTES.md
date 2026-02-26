# PlayGamesPlugin v1.1.3

## Highlights
- Fixed Android startup crash path caused by cloud snapshot API calls while user auth was not yet confirmed.
- Snapshot APIs now fail gracefully when called while signed out instead of triggering an unstable Play Games resolution flow.
- Rebuilt and packaged fresh `debug` and `release` AAR files.

## Fix Details
- Added auth guard for Snapshots API methods:
  - `saveGame`
  - `loadGame`
  - `deleteGame`
- When signed out, plugin emits the matching `*_failed` signal with:
  - `status_code = -3`
  - message `"Not signed in"`
- Added documentation updates (README + AGENTS) describing auth requirements for cloud save calls.

## Included in This Release Package
- `play_games_plugin/plugin.cfg` (`version="1.1.3"`)
- `play_games_plugin/play_games_plugin.gd`
- `play_games_plugin/PlayGamesPlugin-debug.aar`
- `play_games_plugin/PlayGamesPlugin-release.aar`

## Install
1. Download `PlayGamesPlugin-v1.1.3-addons.zip`.
2. Extract `play_games_plugin`.
3. Copy extracted folder into your Godot project at:
   - `addons/play_games_plugin`
4. Enable plugin in Godot Editor:
   - `Project Settings -> Plugins -> PlayGamesPlugin -> Enable`
