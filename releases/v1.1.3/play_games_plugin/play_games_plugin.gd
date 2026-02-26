@tool
extends EditorPlugin

const PLUGIN_NAME = "PlayGamesPlugin"

var export_plugin: AndroidExportPlugin


func _enter_tree() -> void:
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)


func _exit_tree() -> void:
	remove_export_plugin(export_plugin)
	export_plugin = null


class AndroidExportPlugin extends EditorExportPlugin:
	const PLUGIN_NAME = "PlayGamesPlugin"

	# Set your Play Games App ID here (from Google Play Console)
	# This is the numeric ID, not the full package name
	const PLAY_GAMES_APP_ID = "YOUR_APP_ID_HERE"

	func _get_name() -> String:
		return PLUGIN_NAME

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		if platform is EditorExportPlatformAndroid:
			return true
		return false

	func _get_android_libraries(platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		if debug:
			return PackedStringArray(["res://addons/play_games_plugin/PlayGamesPlugin-debug.aar"])
		else:
			return PackedStringArray(["res://addons/play_games_plugin/PlayGamesPlugin-release.aar"])

	func _get_android_dependencies(platform: EditorExportPlatform, debug: bool) -> PackedStringArray:
		# Play Games Services v2 - no longer needs play-services-auth
		return PackedStringArray([
			"com.google.android.gms:play-services-games-v2:20.1.0"
		])

	func _get_android_manifest_application_element_contents(platform: EditorExportPlatform, debug: bool) -> String:
		# Add the Play Games App ID to the manifest
		# Users should replace YOUR_APP_ID_HERE with their actual App ID from Google Play Console
		return """
		<meta-data
			android:name="com.google.android.gms.games.APP_ID"
			android:value="%s" />
		""" % PLAY_GAMES_APP_ID

	func _get_android_manifest_activity_element_contents(platform: EditorExportPlatform, debug: bool) -> String:
		return ""

	func _get_android_manifest_element_contents(platform: EditorExportPlatform, debug: bool) -> String:
		return ""
