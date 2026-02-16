# PlayGamesPlugin v1.1.1

## Highlights
- Added `snake_case` API aliases for better Godot integration compatibility.
- Kept existing `camelCase` API methods fully supported.
- Rebuilt and packaged fresh `debug` and `release` AAR files.

## New Compatibility Aliases
- Auth:
  - `sign_in()`, `sign_out()`, `is_signed_in()`, `refresh_auth_status()`
- Cloud Save:
  - `save_game(save_name, data, description)`
  - `load_game(save_name)`
  - `delete_game(save_name)`

## Included in This Release Package
- `play_games_plugin/plugin.cfg` (`version="1.1.1"`)
- `play_games_plugin/play_games_plugin.gd`
- `play_games_plugin/PlayGamesPlugin-debug.aar`
- `play_games_plugin/PlayGamesPlugin-release.aar`

## Install
1. Download `PlayGamesPlugin-v1.1.1-addons.zip`.
2. Extract `play_games_plugin`.
3. Copy extracted folder into your Godot project at:
   - `addons/play_games_plugin`
4. Enable plugin in Godot Editor:
   - `Project Settings -> Plugins -> PlayGamesPlugin -> Enable`
