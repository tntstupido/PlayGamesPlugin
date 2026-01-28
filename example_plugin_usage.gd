# Example GDScript for using the PlayGamesPlugin (v2 API)
# This script demonstrates how to use the plugin with Play Games Services v2

extends Node

# Plugin reference
var play_games: Variant = null

func _ready():
	# Get the plugin singleton (only available on Android)
	if Engine.has_singleton("PlayGamesPlugin"):
		play_games = Engine.get_singleton("PlayGamesPlugin")
		print("PlayGamesPlugin loaded successfully!")

		# Connect to signals for async events
		play_games.sign_in_success.connect(_on_sign_in_success)
		play_games.sign_in_failed.connect(_on_sign_in_failed)
		play_games.player_info_loaded.connect(_on_player_info_loaded)

		# Cloud Save signals
		play_games.save_game_success.connect(_on_save_game_success)
		play_games.save_game_failed.connect(_on_save_game_failed)
		play_games.load_game_success.connect(_on_load_game_success)
		play_games.load_game_failed.connect(_on_load_game_failed)
		play_games.delete_game_success.connect(_on_delete_game_success)
		play_games.delete_game_failed.connect(_on_delete_game_failed)

		# Test the plugin
		print("Plugin says: ", play_games.helloWorld())

		# With v2, automatic sign-in happens at app launch
		# Check if user is already signed in
		if play_games.isSignedIn():
			print("User already signed in!")
			print("Player: ", play_games.getPlayerDisplayName())
		else:
			print("User not signed in - show sign-in button")
	else:
		print("PlayGamesPlugin not found (only available on Android)")


# Signal handlers for async events

func _on_sign_in_success(player_id: String, player_name: String):
	"""Called when sign-in completes successfully"""
	print("Sign-in successful!")
	print("Player ID: ", player_id)
	print("Player Name: ", player_name)

	# Hide sign-in button, show player info
	# update_ui_for_signed_in_user(player_name)

func _on_sign_in_failed():
	"""Called when sign-in fails or user declines"""
	print("Sign-in failed or declined")

	# Keep showing sign-in button
	# show_sign_in_button()

func _on_player_info_loaded(player_id: String, player_name: String):
	"""Called when player info is loaded (after automatic sign-in)"""
	print("Player info loaded: ", player_name, " (", player_id, ")")


# Manual sign-in (for users who haven't signed in yet)

func request_sign_in():
	"""
	Call this when user presses a "Sign In" button.
	With v2, this shows the system sign-in UI.
	"""
	if play_games:
		print("Requesting sign-in...")
		play_games.signIn()
		# Result will come via sign_in_success or sign_in_failed signal


# Check authentication status

func check_auth_status() -> bool:
	"""Check if the user is currently signed in"""
	if play_games:
		return play_games.isSignedIn()
	return false

func refresh_auth_on_resume():
	"""
	Call this in _notification when NOTIFICATION_APPLICATION_RESUMED.
	Auth status can change while app is in background.
	"""
	if play_games:
		play_games.refreshAuthStatus()


# Get player information

func get_player_info() -> Dictionary:
	"""Get current player's info"""
	if play_games and play_games.isSignedIn():
		return {
			"id": play_games.getPlayerId(),
			"name": play_games.getPlayerDisplayName()
		}
	return {}


# Handle app lifecycle

func _notification(what):
	match what:
		NOTIFICATION_APPLICATION_RESUMED:
			# Re-check auth status when app resumes
			refresh_auth_on_resume()
		NOTIFICATION_APPLICATION_PAUSED:
			# App going to background
			pass


# Example game integration

func on_level_complete(level_id: String, score: int):
	"""Example: Called when player completes a level"""
	print("Level ", level_id, " complete with score: ", score)

	if check_auth_status():
		var player = get_player_info()
		print("Completed by: ", player.get("name", "Unknown"))

		# Future: Submit score to leaderboard
		# play_games.submitScore("leaderboard_" + level_id, score)

		# Future: Unlock achievement
		# if score >= 1000:
		#     play_games.unlockAchievement("achievement_high_scorer")

func on_achievement_unlocked(achievement_id: String):
	"""Example: Show achievement notification"""
	print("Achievement unlocked: ", achievement_id)
	# Future: play_games.unlockAchievement(achievement_id)


# UI Integration example

func _on_sign_in_button_pressed():
	"""Connect this to your Sign In button"""
	request_sign_in()

func _on_show_achievements_pressed():
	"""Connect this to your Achievements button"""
	# Future: play_games.showAchievementsUI()
	print("Achievements UI not yet implemented")

func _on_show_leaderboard_pressed():
	"""Connect this to your Leaderboard button"""
	# Future: play_games.showLeaderboardUI("leaderboard_id")
	print("Leaderboards UI not yet implemented")


# ==================== Cloud Save ====================

func _on_save_game_success(save_name: String):
	print("Game saved successfully: ", save_name)

func _on_save_game_failed(save_name: String, status_code: int, message: String):
	print("Save failed [", save_name, "]: ", status_code, " - ", message)

func _on_load_game_success(save_name: String, data: String):
	print("Game loaded [", save_name, "]: ", data)
	var parsed = JSON.parse_string(data)
	if parsed:
		print("HP: ", parsed.get("hp", 0))
		print("Level: ", parsed.get("level", 1))

func _on_load_game_failed(save_name: String, status_code: int, message: String):
	print("Load failed [", save_name, "]: ", status_code, " - ", message)

func _on_delete_game_success(save_name: String):
	print("Save deleted: ", save_name)

func _on_delete_game_failed(save_name: String, status_code: int, message: String):
	print("Delete failed [", save_name, "]: ", status_code, " - ", message)

func save_player_state(hp: int, level: int, gold: int):
	"""Example: Save player state to cloud"""
	if play_games and play_games.isSignedIn():
		var data = JSON.stringify({"hp": hp, "level": level, "gold": gold})
		play_games.saveGame("autosave", data, "Autosave - Level %d" % level)

func load_player_state():
	"""Example: Load player state from cloud"""
	if play_games and play_games.isSignedIn():
		play_games.loadGame("autosave")

func delete_save():
	"""Example: Delete a save slot"""
	if play_games and play_games.isSignedIn():
		play_games.deleteGame("autosave")
