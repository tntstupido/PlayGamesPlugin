package com.mladenstojanovic.playgamesplugin

import android.app.Activity
import android.util.Log
import android.view.View
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

@Suppress("unused")
class PlayGamesPlugin(godot: Godot) : GodotPlugin(godot) {

    companion object {
        private const val TAG = "PlayGamesPlugin"

        init {
            Log.d(TAG, "PlayGamesPlugin class loaded (static init)")
        }
    }

    init {
        Log.d(TAG, "PlayGamesPlugin instance created")
    }

    private var gamesSignInClient: GamesSignInClient? = null
    private var isAuthenticated: Boolean = false
    private var currentPlayerId: String = ""
    private var currentPlayerName: String = ""

    private fun getApiExceptionInfo(exception: Exception?): Pair<Int, String> {
        if (exception == null) {
            return Pair(-1, "Unknown error")
        }
        val apiException = exception as? ApiException
        return if (apiException != null) {
            Pair(apiException.statusCode, apiException.message ?: "ApiException")
        } else {
            Pair(-1, "${exception.javaClass.simpleName}: ${exception.message}")
        }
    }

    private fun logTaskFailure(context: String, exception: Exception?, isCanceled: Boolean = false) {
        if (isCanceled) {
            Log.e(TAG, "$context failed: canceled by user")
            return
        }
        if (exception == null) {
            Log.e(TAG, "$context failed with null exception")
            return
        }
        val apiException = exception as? ApiException
        if (apiException != null) {
            Log.e(TAG, "$context failed: statusCode=${apiException.statusCode}, message=${apiException.message}", apiException)
        } else {
            Log.e(TAG, "$context failed: ${exception.javaClass.simpleName}: ${exception.message}", exception)
        }
    }


    override fun getPluginName(): String {
        return "PlayGamesPlugin"
    }

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("sign_in_success", String::class.java, String::class.java),
            SignalInfo("sign_in_failed", Int::class.java, String::class.java),
            SignalInfo("player_info_loaded", String::class.java, String::class.java),
            // Cloud Save signals
            SignalInfo("save_game_success", String::class.java),
            SignalInfo("save_game_failed", String::class.java, Int::class.java, String::class.java),
            SignalInfo("load_game_success", String::class.java, String::class.java),
            SignalInfo("load_game_failed", String::class.java, Int::class.java, String::class.java),
            SignalInfo("delete_game_success", String::class.java),
            SignalInfo("delete_game_failed", String::class.java, Int::class.java, String::class.java),
            // Leaderboard signals
            SignalInfo("leaderboard_submit_success", String::class.java),
            SignalInfo("leaderboard_submit_failed", String::class.java, Int::class.java, String::class.java),
            SignalInfo("leaderboard_top_scores_loaded", String::class.java, String::class.java),
            SignalInfo("leaderboard_top_scores_failed", String::class.java, Int::class.java, String::class.java),
            SignalInfo("leaderboard_player_score_loaded", String::class.java, String::class.java),
            SignalInfo("leaderboard_player_score_failed", String::class.java, Int::class.java, String::class.java)
        )
    }

    override fun onMainCreate(activity: Activity?): View? {
        Log.d(TAG, "PlayGamesPlugin initialized")

        if (activity == null) return null

        // Initialize PlayGamesSdk - must be called before any other Play Games APIs
        PlayGamesSdk.initialize(activity)

        // Get the sign-in client
        gamesSignInClient = PlayGames.getGamesSignInClient(activity)

        // Check authentication status silently
        checkAuthenticationStatus()

        return null
    }

    private fun checkAuthenticationStatus() {
        val activity = getActivity() ?: return

        gamesSignInClient?.isAuthenticated?.addOnCompleteListener { task ->
            isAuthenticated = task.isSuccessful && task.result.isAuthenticated
            Log.d(TAG, "Authentication check: $isAuthenticated")

            if (task.isSuccessful) {
                if (isAuthenticated) {
                    loadPlayerInfo()
                }
            } else {
                Log.d(TAG, "Authentication check failed: successful=${task.isSuccessful}, canceled=${task.isCanceled}, exception=${task.exception}")
                logTaskFailure("Authentication check", task.exception, task.isCanceled)
            }
        }
    }

    private fun loadPlayerInfo() {
        val activity = getActivity() ?: return

        PlayGames.getPlayersClient(activity).currentPlayer
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val player = task.result
                    currentPlayerId = player.playerId
                    currentPlayerName = player.displayName
                    Log.d(TAG, "Player loaded: $currentPlayerName ($currentPlayerId)")
                    emitSignal("player_info_loaded", currentPlayerId, currentPlayerName)
                } else {
                    Log.e(TAG, "Failed to load player info", task.exception)
                }
            }
    }

    private fun parseTimeSpan(value: String): Int {
        return when (value.lowercase()) {
            "daily" -> LeaderboardVariant.TIME_SPAN_DAILY
            "weekly" -> LeaderboardVariant.TIME_SPAN_WEEKLY
            else -> LeaderboardVariant.TIME_SPAN_ALL_TIME
        }
    }

    private fun parseCollection(value: String): Int {
        return when (value.lowercase()) {
            "friends" -> LeaderboardVariant.COLLECTION_FRIENDS
            else -> LeaderboardVariant.COLLECTION_PUBLIC
        }
    }

    private fun buildScoreJson(score: com.google.android.gms.games.leaderboard.LeaderboardScore): JSONObject {
        val obj = JSONObject()
        obj.put("rank", score.rank)
        obj.put("score", score.rawScore)
        obj.put("display_name", score.scoreHolderDisplayName)
        val playerId = score.scoreHolder?.playerId ?: ""
        obj.put("player_id", playerId)
        obj.put("is_player", playerId == currentPlayerId)
        return obj
    }

    /**
     * A simple hello world method for testing
     */
    @UsedByGodot
    fun helloWorld(): String {
        return "Hello from PlayGamesPlugin v2!"
    }

    /**
     * Sign in to Google Play Games.
     * With v2, sign-in happens automatically at app launch if the user has previously signed in.
     * This method triggers a manual sign-in for users who haven't signed in yet.
     */
    @UsedByGodot
    fun signIn() {
        Log.d(TAG, "SignIn called")

        gamesSignInClient?.signIn()?.addOnCompleteListener { task ->
            val result = task.result
            Log.d(TAG, "Sign in task: successful=${task.isSuccessful}, canceled=${task.isCanceled}, resultAuth=${result?.isAuthenticated}")
            if (task.isSuccessful && result != null && result.isAuthenticated) {
                isAuthenticated = true
                Log.d(TAG, "Sign in successful")
                loadPlayerInfo()
                getActivity()?.runOnUiThread {
                    emitSignal("sign_in_success", currentPlayerId, currentPlayerName)
                }
            } else {
                isAuthenticated = false
                val isCanceled = task.isCanceled
                val (statusCode, message) = if (isCanceled) {
                    Pair(-2, "Canceled")
                } else {
                    getApiExceptionInfo(task.exception)
                }
                logTaskFailure("Sign in", task.exception, isCanceled)
                getActivity()?.runOnUiThread {
                    emitSignal("sign_in_failed", statusCode, message)
                }
            }
        }
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun sign_in() {
        signIn()
    }

    /**
     * Sign out is no longer supported in Play Games Services v2.
     * Account management is handled in OS settings.
     * This method is kept for API compatibility but does nothing.
     */
    @UsedByGodot
    fun signOut() {
        Log.d(TAG, "SignOut called - Note: signOut is deprecated in Play Games Services v2")
        // In v2, sign-out is handled by the OS settings, not by the app
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun sign_out() {
        signOut()
    }

    /**
     * Check if the user is currently signed in
     * @return true if signed in, false otherwise
     */
    @UsedByGodot
    fun isSignedIn(): Boolean {
        Log.d(TAG, "IsSignedIn called: $isAuthenticated")
        return isAuthenticated
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun is_signed_in(): Boolean {
        return isSignedIn()
    }

    /**
     * Refresh authentication status asynchronously.
     * Call this in onResume to check if auth state changed while app was in background.
     */
    @UsedByGodot
    fun refreshAuthStatus() {
        Log.d(TAG, "Refreshing auth status")
        checkAuthenticationStatus()
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun refresh_auth_status() {
        refreshAuthStatus()
    }

    /**
     * Get the player ID
     * @return Player ID or empty string if not signed in
     */
    @UsedByGodot
    fun getPlayerId(): String {
        return currentPlayerId
    }

    /**
     * Get the player display name
     * @return Player display name or empty string if not signed in
     */
    @UsedByGodot
    fun getPlayerDisplayName(): String {
        return currentPlayerName
    }

    // ==================== Cloud Save (Snapshots) ====================

    /**
     * Save game data to a named snapshot.
     * @param saveName Unique name for the save slot (e.g. "slot1", "autosave")
     * @param data The data to save (e.g. JSON string)
     * @param description Human-readable description shown in Play Games UI
     */
    @UsedByGodot
    fun saveGame(saveName: String, data: String, description: String) {
        Log.d(TAG, "saveGame called: saveName=$saveName")
        val activity = getActivity() ?: run {
            emitSignal("save_game_failed", saveName, -1, "Activity not available")
            return
        }

        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(saveName, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnCompleteListener { openTask ->
                if (!openTask.isSuccessful) {
                    val (code, msg) = getApiExceptionInfo(openTask.exception)
                    logTaskFailure("saveGame open", openTask.exception, openTask.isCanceled)
                    activity.runOnUiThread { emitSignal("save_game_failed", saveName, code, msg) }
                    return@addOnCompleteListener
                }

                val dataOrConflict = openTask.result
                if (!dataOrConflict.isConflict) {
                    val snapshot = dataOrConflict.data!!
                    snapshot.snapshotContents.writeBytes(data.toByteArray(Charsets.UTF_8))

                    val metadataChange = SnapshotMetadataChange.Builder()
                        .setDescription(description)
                        .build()

                    snapshotsClient.commitAndClose(snapshot, metadataChange)
                        .addOnCompleteListener { commitTask ->
                            if (commitTask.isSuccessful) {
                                Log.d(TAG, "saveGame success: $saveName")
                                activity.runOnUiThread { emitSignal("save_game_success", saveName) }
                            } else {
                                val (code, msg) = getApiExceptionInfo(commitTask.exception)
                                logTaskFailure("saveGame commit", commitTask.exception, commitTask.isCanceled)
                                activity.runOnUiThread { emitSignal("save_game_failed", saveName, code, msg) }
                            }
                        }
                } else {
                    // Conflict resolved automatically by RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
                    Log.w(TAG, "saveGame: unexpected conflict for $saveName")
                    activity.runOnUiThread { emitSignal("save_game_failed", saveName, -1, "Unexpected conflict") }
                }
            }
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun save_game(saveName: String, data: String, description: String) {
        saveGame(saveName, data, description)
    }

    /**
     * Load game data from a named snapshot.
     * @param saveName The name of the save slot to load
     */
    @UsedByGodot
    fun loadGame(saveName: String) {
        Log.d(TAG, "loadGame called: saveName=$saveName")
        val activity = getActivity() ?: run {
            emitSignal("load_game_failed", saveName, -1, "Activity not available")
            return
        }

        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(saveName, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnCompleteListener { openTask ->
                if (!openTask.isSuccessful) {
                    val (code, msg) = getApiExceptionInfo(openTask.exception)
                    logTaskFailure("loadGame open", openTask.exception, openTask.isCanceled)
                    activity.runOnUiThread { emitSignal("load_game_failed", saveName, code, msg) }
                    return@addOnCompleteListener
                }

                val dataOrConflict = openTask.result
                if (!dataOrConflict.isConflict) {
                    val snapshot = dataOrConflict.data!!
                    val bytes = snapshot.snapshotContents.readFully()
                    val gameData = String(bytes, Charsets.UTF_8)

                    snapshotsClient.discardAndClose(snapshot)

                    Log.d(TAG, "loadGame success: $saveName (${bytes.size} bytes)")
                    activity.runOnUiThread { emitSignal("load_game_success", saveName, gameData) }
                } else {
                    Log.w(TAG, "loadGame: unexpected conflict for $saveName")
                    activity.runOnUiThread { emitSignal("load_game_failed", saveName, -1, "Unexpected conflict") }
                }
            }
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun load_game(saveName: String) {
        loadGame(saveName)
    }

    /**
     * Delete a saved game snapshot.
     * @param saveName The name of the save slot to delete
     */
    @UsedByGodot
    fun deleteGame(saveName: String) {
        Log.d(TAG, "deleteGame called: saveName=$saveName")
        val activity = getActivity() ?: run {
            emitSignal("delete_game_failed", saveName, -1, "Activity not available")
            return
        }

        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(saveName, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnCompleteListener { openTask ->
                if (!openTask.isSuccessful) {
                    val (code, msg) = getApiExceptionInfo(openTask.exception)
                    logTaskFailure("deleteGame open", openTask.exception, openTask.isCanceled)
                    activity.runOnUiThread { emitSignal("delete_game_failed", saveName, code, msg) }
                    return@addOnCompleteListener
                }

                val dataOrConflict = openTask.result
                if (!dataOrConflict.isConflict) {
                    val snapshot = dataOrConflict.data!!
                    val metadata = snapshot.metadata

                    snapshotsClient.delete(metadata)
                        .addOnCompleteListener { deleteTask ->
                            if (deleteTask.isSuccessful) {
                                Log.d(TAG, "deleteGame success: $saveName")
                                activity.runOnUiThread { emitSignal("delete_game_success", saveName) }
                            } else {
                                val (code, msg) = getApiExceptionInfo(deleteTask.exception)
                                logTaskFailure("deleteGame delete", deleteTask.exception, deleteTask.isCanceled)
                                activity.runOnUiThread { emitSignal("delete_game_failed", saveName, code, msg) }
                            }
                        }
                } else {
                    Log.w(TAG, "deleteGame: unexpected conflict for $saveName")
                    activity.runOnUiThread { emitSignal("delete_game_failed", saveName, -1, "Unexpected conflict") }
                }
            }
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun delete_game(saveName: String) {
        deleteGame(saveName)
    }

    // ==================== Leaderboards ====================

    @UsedByGodot
    fun submitScore(leaderboardId: String, score: Long) {
        Log.d(TAG, "submitScore called: leaderboardId=$leaderboardId, score=$score")
        val activity = getActivity() ?: run {
            emitSignal("leaderboard_submit_failed", leaderboardId, -1, "Activity not available")
            return
        }

        PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(leaderboardId, score)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    activity.runOnUiThread { emitSignal("leaderboard_submit_success", leaderboardId) }
                } else {
                    val (code, msg) = getApiExceptionInfo(task.exception)
                    logTaskFailure("submitScore", task.exception, task.isCanceled)
                    activity.runOnUiThread { emitSignal("leaderboard_submit_failed", leaderboardId, code, msg) }
                }
            }
    }

    @UsedByGodot
    fun loadTopScores(leaderboardId: String, timeSpan: String, collection: String, maxResults: Int, forceReload: Boolean) {
        Log.d(TAG, "loadTopScores called: leaderboardId=$leaderboardId")
        val activity = getActivity() ?: run {
            emitSignal("leaderboard_top_scores_failed", leaderboardId, -1, "Activity not available")
            return
        }

        val timeSpanConst = parseTimeSpan(timeSpan)
        val collectionConst = parseCollection(collection)

        PlayGames.getLeaderboardsClient(activity)
            .loadTopScores(leaderboardId, timeSpanConst, collectionConst, maxResults, forceReload)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val annotated = task.result
                    val scores = annotated.get()
                    val buffer = scores?.getScores()
                    val arr = JSONArray()
                    if (buffer != null) {
                        for (score in buffer) {
                            arr.put(buildScoreJson(score))
                        }
                        buffer.release()
                    }
                    val payload = JSONObject()
                    payload.put("leaderboard_id", leaderboardId)
                    payload.put("scores", arr)
                    activity.runOnUiThread { emitSignal("leaderboard_top_scores_loaded", leaderboardId, payload.toString()) }
                } else {
                    val (code, msg) = getApiExceptionInfo(task.exception)
                    logTaskFailure("loadTopScores", task.exception, task.isCanceled)
                    activity.runOnUiThread { emitSignal("leaderboard_top_scores_failed", leaderboardId, code, msg) }
                }
            }
    }

    @UsedByGodot
    fun loadPlayerScore(leaderboardId: String, timeSpan: String, collection: String, forceReload: Boolean) {
        Log.d(TAG, "loadPlayerScore called: leaderboardId=$leaderboardId")
        val activity = getActivity() ?: run {
            emitSignal("leaderboard_player_score_failed", leaderboardId, -1, "Activity not available")
            return
        }

        val timeSpanConst = parseTimeSpan(timeSpan)
        val collectionConst = parseCollection(collection)

        PlayGames.getLeaderboardsClient(activity)
            .loadCurrentPlayerLeaderboardScore(leaderboardId, timeSpanConst, collectionConst)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val annotated = task.result
                    val score = annotated.get()
                    if (score != null) {
                        val payload = buildScoreJson(score)
                        payload.put("leaderboard_id", leaderboardId)
                        activity.runOnUiThread { emitSignal("leaderboard_player_score_loaded", leaderboardId, payload.toString()) }
                    } else {
                        activity.runOnUiThread { emitSignal("leaderboard_player_score_failed", leaderboardId, -1, "No player score") }
                    }
                } else {
                    val (code, msg) = getApiExceptionInfo(task.exception)
                    logTaskFailure("loadPlayerScore", task.exception, task.isCanceled)
                    activity.runOnUiThread { emitSignal("leaderboard_player_score_failed", leaderboardId, code, msg) }
                }
            }
    }
}
