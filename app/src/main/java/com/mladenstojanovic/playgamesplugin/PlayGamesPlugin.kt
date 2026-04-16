package com.mladenstojanovic.playgamesplugin

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
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
        private const val META_SERVER_CLIENT_ID = "com.mladenstojanovic.playgamesplugin.SERVER_CLIENT_ID"
        private const val META_SERVER_CLIENT_ID_ALT = "com.mladenstojanovic.playgamesplugin.server_client_id"
        private const val META_STARTUP_AUTH_CHECK = "com.mladenstojanovic.playgamesplugin.STARTUP_AUTH_CHECK"
        private const val META_STARTUP_AUTH_CHECK_ALT = "com.mladenstojanovic.playgamesplugin.startup_auth_check"

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
    private var currentServerAuthCode: String = ""
    private var lastServerAuthCodeAtMs: Long = 0L
    private var signInInFlight: Boolean = false
    private var interactiveSignInAllowedUntilMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastResolvedServerClientId: String = ""
    private var lastResolvedServerClientIdSource: String = "unresolved"
    private var lastServerClientResolutionPath: String = ""

    private data class ServerClientIdResolution(
        val value: String,
        val source: String,
        val trace: String
    )

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

    private fun emitSignalSafe(signalName: String, vararg args: Any?) {
        try {
            emitSignal(signalName, *args)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "emitSignal failed for $signalName with args=${args.contentToString()}", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "emitSignal runtime failure for $signalName with args=${args.contentToString()}", e)
        }
    }


    private fun isXiaomiFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ||
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }

    private fun retrySignInForStatusCode(
        statusCode: Int,
        operation: String,
        onRetryReady: () -> Unit,
        onRetryFailed: (Int, String) -> Unit
    ): Boolean {
        if (statusCode != 4) {
            return false
        }
        val client = gamesSignInClient ?: run {
            onRetryFailed(-1, "Games sign-in client unavailable")
            return true
        }
        Log.w(TAG, "$operation got SIGN_IN_REQUIRED, retrying sign-in once")
        client.signIn().addOnCompleteListener { task ->
            val result = task.result
            if (task.isSuccessful && result != null && result.isAuthenticated) {
                isAuthenticated = true
                loadPlayerInfo()
                onRetryReady()
            } else {
                isAuthenticated = false
                val (retryCode, retryMsg) = if (task.isCanceled) {
                    Pair(-2, "Canceled")
                } else {
                    getApiExceptionInfo(task.exception)
                }
                logTaskFailure("$operation retrySignIn", task.exception, task.isCanceled)
                onRetryFailed(retryCode, retryMsg)
            }
        }
        return true
    }

    private fun ensureAuthenticatedForSnapshotOp(activity: Activity, failureSignal: String, saveName: String, operation: String): Boolean {
        if (isAuthenticated) {
            return true
        }
        Log.w(TAG, "$operation blocked: user not authenticated")
        activity.runOnUiThread {
            emitSignalSafe(failureSignal, saveName, -3L, "Not signed in")
        }
        return false
    }

    private fun shouldRunStartupAuthCheck(activity: Activity): Boolean {
        val appInfo = try {
            activity.packageManager.getApplicationInfo(activity.packageName, android.content.pm.PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.w(TAG, "Startup auth-check flag lookup failed; defaulting to enabled", e)
            null
        }
        val metadata = appInfo?.metaData ?: return true
        if (metadata.containsKey(META_STARTUP_AUTH_CHECK)) {
            return metadata.getBoolean(META_STARTUP_AUTH_CHECK, false)
        }
        if (metadata.containsKey(META_STARTUP_AUTH_CHECK_ALT)) {
            return metadata.getBoolean(META_STARTUP_AUTH_CHECK_ALT, false)
        }
        return true
    }


    override fun getPluginName(): String {
        return "PlayGamesPlugin"
    }

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("sign_in_success", String::class.java, String::class.java),
            SignalInfo("sign_in_failed", Long::class.javaObjectType, String::class.java),
            SignalInfo("player_info_loaded", String::class.java, String::class.java),
            // Cloud Save signals
            SignalInfo("save_game_success", String::class.java),
            SignalInfo("save_game_failed", String::class.java, Long::class.javaObjectType, String::class.java),
            SignalInfo("load_game_success", String::class.java, String::class.java),
            SignalInfo("load_game_failed", String::class.java, Long::class.javaObjectType, String::class.java),
            SignalInfo("delete_game_success", String::class.java),
            SignalInfo("delete_game_failed", String::class.java, Long::class.javaObjectType, String::class.java),
            // Leaderboard signals
            SignalInfo("leaderboard_submit_success", String::class.java),
            SignalInfo("leaderboard_submit_failed", String::class.java, Long::class.javaObjectType, String::class.java),
            SignalInfo("leaderboard_top_scores_loaded", String::class.java, String::class.java),
            SignalInfo("leaderboard_top_scores_failed", String::class.java, Long::class.javaObjectType, String::class.java),
            SignalInfo("leaderboard_player_score_loaded", String::class.java, String::class.java),
            SignalInfo("leaderboard_player_score_failed", String::class.java, Long::class.javaObjectType, String::class.java)
        )
    }

    override fun onMainCreate(activity: Activity?): View? {
        Log.d(TAG, "PlayGamesPlugin initialized")

        if (activity == null) return null

        // Initialize PlayGamesSdk - must be called before any other Play Games APIs
        PlayGamesSdk.initialize(activity)

        // Get the sign-in client
        gamesSignInClient = PlayGames.getGamesSignInClient(activity)

        // Startup auth check is enabled by default so PGS v2 can auto-sign users
        // when the platform/session already has an authenticated account.
        val startupAuthCheckEnabled = shouldRunStartupAuthCheck(activity)
        if (!startupAuthCheckEnabled) {
            Log.d(TAG, "Startup auth check disabled; waiting for explicit sign-in")
        } else {
            checkAuthenticationStatus()
        }

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
                    emitSignalSafe("player_info_loaded", currentPlayerId, currentPlayerName)
                    requestServerAuthCodeInternal("player_info_loaded")
                } else {
                    Log.e(TAG, "Failed to load player info", task.exception)
                }
            }
    }

    private fun resolveServerClientId(activity: Activity): String {
        return resolveServerClientIdDetailed(activity).value
    }

    private fun resolveServerClientIdDetailed(activity: Activity): ServerClientIdResolution {
        val appRes = activity.resources
        val appPkg = activity.packageName
        val trace = mutableListOf<String>()
        val names = listOf("default_web_client_id", "server_client_id")
        for (name in names) {
            trace.add("res:$name:lookup")
            val id = appRes.getIdentifier(name, "string", appPkg)
            if (id != 0) {
                trace.add("res:$name:id=$id")
                val value = appRes.getString(id).trim()
                if (value.isNotEmpty()) {
                    trace.add("res:$name:non_empty")
                    return ServerClientIdResolution(value, "resource:$name", trace.joinToString(" -> "))
                }
                trace.add("res:$name:empty")
            } else {
                trace.add("res:$name:missing")
            }
        }
        val appInfo = try {
            activity.packageManager.getApplicationInfo(appPkg, android.content.pm.PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            trace.add("manifest:metadata:error")
            null
        }
        val metadata = appInfo?.metaData
        if (metadata != null) {
            trace.add("manifest:metadata:present")
            val manifestCandidates = listOf(META_SERVER_CLIENT_ID, META_SERVER_CLIENT_ID_ALT)
            for (metaName in manifestCandidates) {
                trace.add("manifest:$metaName:lookup")
                val value = metadata.getString(metaName)?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    trace.add("manifest:$metaName:non_empty")
                    return ServerClientIdResolution(value, "manifest:$metaName", trace.joinToString(" -> "))
                }
                trace.add("manifest:$metaName:empty_or_missing")
            }
        } else {
            trace.add("manifest:metadata:missing")
        }
        return ServerClientIdResolution("", "unresolved", trace.joinToString(" -> "))
    }

    private fun requestServerAuthCodeInternal(reason: String) {
        if (!isAuthenticated) {
            Log.d(TAG, "Skipping auth code request ($reason): not authenticated")
            return
        }
        val activity = getActivity() ?: return
        val client = gamesSignInClient ?: return
        val resolution = resolveServerClientIdDetailed(activity)
        lastResolvedServerClientId = resolution.value
        lastResolvedServerClientIdSource = resolution.source
        lastServerClientResolutionPath = resolution.trace
        if (resolution.value.isEmpty()) {
            Log.w(
                TAG,
                "Skipping auth code request ($reason): unresolved server client id source=${resolution.source} trace=${resolution.trace}"
            )
            return
        }
        Log.d(
            TAG,
            "Auth code request ($reason): resolved server client id='${resolution.value}' source=${resolution.source} trace=${resolution.trace}"
        )
        client.requestServerSideAccess(resolution.value, false).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val authCode = task.result ?: ""
                if (authCode.isNotEmpty()) {
                    currentServerAuthCode = authCode
                    lastServerAuthCodeAtMs = System.currentTimeMillis()
                    Log.d(
                        TAG,
                        "Server auth code refreshed ($reason) source=${resolution.source} serverClientId='${resolution.value}' refreshedAtMs=$lastServerAuthCodeAtMs"
                    )
                } else {
                    Log.w(
                        TAG,
                        "Server auth code request returned empty code ($reason) source=${resolution.source} serverClientId='${resolution.value}'"
                    )
                }
            } else {
                Log.w(
                    TAG,
                    "requestServerSideAccess failed ($reason) source=${resolution.source} serverClientId='${resolution.value}' trace=${resolution.trace}"
                )
                logTaskFailure("requestServerSideAccess($reason)", task.exception, task.isCanceled)
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
        val rankValue = score.rank
        val rankKnown = rankValue >= 0
        obj.put("rank", rankValue)
        obj.put("rank_value", rankValue)
        obj.put("rank_known", rankKnown)
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
        val nowMs = System.currentTimeMillis()
        val interactiveWindowArmed = interactiveSignInAllowedUntilMs > 0L
        if (interactiveWindowArmed && nowMs > interactiveSignInAllowedUntilMs) {
            Log.w(TAG, "SignIn rejected: not in user-initiated window")
            emitSignalSafe("sign_in_failed", -6L, "Sign-in must be user initiated")
            interactiveSignInAllowedUntilMs = 0L
            return
        }
        if (signInInFlight) {
            Log.d(TAG, "SignIn ignored: request already in flight")
            return
        }
        val activity = getActivity() ?: run {
            Log.e(TAG, "SignIn failed: activity is null")
            emitSignalSafe("sign_in_failed", -1L, "Activity not available")
            return
        }
        signInInFlight = true
        activity.runOnUiThread {
            val client = gamesSignInClient
            if (client == null) {
                signInInFlight = false
                Log.e(TAG, "SignIn failed: gamesSignInClient is null")
                emitSignalSafe("sign_in_failed", -1L, "Games sign-in client unavailable")
                return@runOnUiThread
            }

            mainHandler.postDelayed({
                if (!signInInFlight) {
                    return@postDelayed
                }
                Log.w(TAG, "SignIn callback timeout reached; checking isAuthenticated fallback")
                client.isAuthenticated.addOnCompleteListener { authTask ->
                    if (!signInInFlight) {
                        return@addOnCompleteListener
                    }
                    val authOk = authTask.isSuccessful && authTask.result?.isAuthenticated == true
                    if (authOk) {
                        isAuthenticated = true
                        signInInFlight = false
                        loadPlayerInfo()
                        requestServerAuthCodeInternal("sign_in_timeout_fallback")
                        emitSignalSafe("sign_in_success", currentPlayerId, currentPlayerName)
                    } else {
                        isAuthenticated = false
                        signInInFlight = false
                        emitSignalSafe("sign_in_failed", -4L, "Sign-in timeout")
                    }
                }
            }, 12000L)

            client.signIn().addOnCompleteListener { task ->
                signInInFlight = false
                val result = task.result
                Log.d(TAG, "Sign in task: successful=${task.isSuccessful}, canceled=${task.isCanceled}, resultAuth=${result?.isAuthenticated}")
                if (task.isSuccessful && result != null && result.isAuthenticated) {
                    isAuthenticated = true
                    Log.d(TAG, "Sign in successful")
                    loadPlayerInfo()
                    requestServerAuthCodeInternal("sign_in")
                    activity.runOnUiThread {
                        emitSignalSafe("sign_in_success", currentPlayerId, currentPlayerName)
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
                    activity.runOnUiThread {
                        emitSignalSafe("sign_in_failed", statusCode.toLong(), message)
                    }
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
     * Arm a short explicit-login window to allow interactive sign-in.
     * Prevents accidental startup/background sign-in popups.
     */
    @UsedByGodot
    fun armInteractiveSignInWindow(windowMs: Long = 15000L) {
        val clamped = when {
            windowMs < 1000L -> 1000L
            windowMs > 60000L -> 60000L
            else -> windowMs
        }
        interactiveSignInAllowedUntilMs = System.currentTimeMillis() + clamped
        Log.d(TAG, "Interactive sign-in window armed for ${clamped}ms")
    }

    @UsedByGodot
    fun arm_interactive_sign_in_window(window_ms: Long = 15000L) {
        armInteractiveSignInWindow(window_ms)
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

    @UsedByGodot
    fun getDebugStateJson(): String {
        val obj = JSONObject()
        obj.put("plugin_version", "2026-04-04-signin-debug-6")
        obj.put("is_authenticated", isAuthenticated)
        obj.put("sign_in_in_flight", signInInFlight)
        obj.put("has_sign_in_client", gamesSignInClient != null)
        obj.put("player_id", currentPlayerId)
        obj.put("player_name", currentPlayerName)
        obj.put("has_server_auth_code", currentServerAuthCode.isNotEmpty())
        obj.put("server_auth_code_refreshed_at_ms", lastServerAuthCodeAtMs)
        obj.put("resolved_server_client_id", lastResolvedServerClientId)
        obj.put("resolved_server_client_id_source", lastResolvedServerClientIdSource)
        obj.put("server_client_id_resolution_trace", lastServerClientResolutionPath)
        return obj.toString()
    }

    @UsedByGodot
    fun get_debug_state_json(): String {
        return getDebugStateJson()
    }

    /**
     * Request a fresh server auth code for backend proof verification.
     */
    @UsedByGodot
    fun requestServerAuthCode() {
        requestServerAuthCodeInternal("manual")
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun request_server_auth_code() {
        requestServerAuthCode()
    }

    /**
     * Returns the last successfully cached Play Games server auth code.
     */
    @UsedByGodot
    fun getServerAuthCode(): String {
        return currentServerAuthCode
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun get_server_auth_code(): String {
        return getServerAuthCode()
    }

    /**
     * Returns the Unix milliseconds timestamp of the cached auth code refresh.
     */
    @UsedByGodot
    fun getServerAuthCodeRefreshedAtMs(): Long {
        return lastServerAuthCodeAtMs
    }

    /**
     * Snake_case alias for compatibility with integrations expecting Godot-style names.
     */
    @UsedByGodot
    fun get_server_auth_code_refreshed_at_ms(): Long {
        return getServerAuthCodeRefreshedAtMs()
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
            emitSignalSafe("save_game_failed", saveName, -1L, "Activity not available")
            return
        }

        if (!ensureAuthenticatedForSnapshotOp(activity, "save_game_failed", saveName, "saveGame")) {
            return
        }

        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(saveName, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnCompleteListener { openTask ->
                if (!openTask.isSuccessful) {
                    val (code, msg) = getApiExceptionInfo(openTask.exception)
                    logTaskFailure("saveGame open", openTask.exception, openTask.isCanceled)
                    activity.runOnUiThread { emitSignalSafe("save_game_failed", saveName, code.toLong(), msg) }
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
                                activity.runOnUiThread { emitSignalSafe("save_game_success", saveName) }
                            } else {
                                val (code, msg) = getApiExceptionInfo(commitTask.exception)
                                logTaskFailure("saveGame commit", commitTask.exception, commitTask.isCanceled)
                                activity.runOnUiThread { emitSignalSafe("save_game_failed", saveName, code.toLong(), msg) }
                            }
                        }
                } else {
                    // Conflict resolved automatically by RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
                    Log.w(TAG, "saveGame: unexpected conflict for $saveName")
                    activity.runOnUiThread { emitSignalSafe("save_game_failed", saveName, -1L, "Unexpected conflict") }
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
            emitSignalSafe("load_game_failed", saveName, -1L, "Activity not available")
            return
        }

        if (!ensureAuthenticatedForSnapshotOp(activity, "load_game_failed", saveName, "loadGame")) {
            return
        }

        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(saveName, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnCompleteListener { openTask ->
                if (!openTask.isSuccessful) {
                    val (code, msg) = getApiExceptionInfo(openTask.exception)
                    logTaskFailure("loadGame open", openTask.exception, openTask.isCanceled)
                    activity.runOnUiThread { emitSignalSafe("load_game_failed", saveName, code.toLong(), msg) }
                    return@addOnCompleteListener
                }

                val dataOrConflict = openTask.result
                if (!dataOrConflict.isConflict) {
                    val snapshot = dataOrConflict.data!!
                    val bytes = snapshot.snapshotContents.readFully()
                    val gameData = String(bytes, Charsets.UTF_8)

                    snapshotsClient.discardAndClose(snapshot)

                    Log.d(TAG, "loadGame success: $saveName (${bytes.size} bytes)")
                    activity.runOnUiThread { emitSignalSafe("load_game_success", saveName, gameData) }
                } else {
                    Log.w(TAG, "loadGame: unexpected conflict for $saveName")
                    activity.runOnUiThread { emitSignalSafe("load_game_failed", saveName, -1L, "Unexpected conflict") }
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
            emitSignalSafe("delete_game_failed", saveName, -1L, "Activity not available")
            return
        }

        if (!ensureAuthenticatedForSnapshotOp(activity, "delete_game_failed", saveName, "deleteGame")) {
            return
        }

        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(saveName, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnCompleteListener { openTask ->
                if (!openTask.isSuccessful) {
                    val (code, msg) = getApiExceptionInfo(openTask.exception)
                    logTaskFailure("deleteGame open", openTask.exception, openTask.isCanceled)
                    activity.runOnUiThread { emitSignalSafe("delete_game_failed", saveName, code.toLong(), msg) }
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
                                activity.runOnUiThread { emitSignalSafe("delete_game_success", saveName) }
                            } else {
                                val (code, msg) = getApiExceptionInfo(deleteTask.exception)
                                logTaskFailure("deleteGame delete", deleteTask.exception, deleteTask.isCanceled)
                                activity.runOnUiThread { emitSignalSafe("delete_game_failed", saveName, code.toLong(), msg) }
                            }
                        }
                } else {
                    Log.w(TAG, "deleteGame: unexpected conflict for $saveName")
                    activity.runOnUiThread { emitSignalSafe("delete_game_failed", saveName, -1L, "Unexpected conflict") }
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
        submitScoreInternal(leaderboardId, score, true)
    }

    private fun submitScoreInternal(leaderboardId: String, score: Long, allowAuthRetry: Boolean) {
        Log.d(TAG, "submitScore called: leaderboardId=$leaderboardId, score=$score")
        val activity = getActivity() ?: run {
            emitSignalSafe("leaderboard_submit_failed", leaderboardId, -1L, "Activity not available")
            return
        }

        PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(leaderboardId, score)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    activity.runOnUiThread { emitSignalSafe("leaderboard_submit_success", leaderboardId) }
                } else {
                    val (code, msg) = getApiExceptionInfo(task.exception)
                    logTaskFailure("submitScore", task.exception, task.isCanceled)
                    if (allowAuthRetry && retrySignInForStatusCode(
                            code,
                            "submitScore",
                            { submitScoreInternal(leaderboardId, score, false) },
                            { retryCode, retryMsg ->
                                activity.runOnUiThread {
                                    emitSignalSafe("leaderboard_submit_failed", leaderboardId, retryCode.toLong(), retryMsg)
                                }
                            }
                        )) {
                        return@addOnCompleteListener
                    }
                    activity.runOnUiThread { emitSignalSafe("leaderboard_submit_failed", leaderboardId, code.toLong(), msg) }
                }
            }
    }

    @UsedByGodot
    fun loadTopScores(leaderboardId: String, timeSpan: String, collection: String, maxResults: Int, forceReload: Boolean) {
        loadTopScoresInternal(leaderboardId, timeSpan, collection, maxResults, forceReload, true)
    }

    private fun loadTopScoresInternal(
        leaderboardId: String,
        timeSpan: String,
        collection: String,
        maxResults: Int,
        forceReload: Boolean,
        allowAuthRetry: Boolean
    ) {
        Log.d(TAG, "loadTopScores called: leaderboardId=$leaderboardId")
        val activity = getActivity() ?: run {
            emitSignalSafe("leaderboard_top_scores_failed", leaderboardId, -1L, "Activity not available")
            return
        }

        val timeSpanConst = parseTimeSpan(timeSpan)
        val collectionConst = parseCollection(collection)
        val normalizedTimeSpan = timeSpan.lowercase()
        val normalizedCollection = collection.lowercase()

        PlayGames.getLeaderboardsClient(activity)
            .loadTopScores(leaderboardId, timeSpanConst, collectionConst, maxResults, forceReload)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val annotated = task.result
                    val scores = annotated.get()
                    val buffer = scores?.getScores()
                    val arr = JSONArray()
                    var unknownRankCount = 0
                    if (buffer != null) {
                        for (score in buffer) {
                            if (score.rank < 0) {
                                unknownRankCount += 1
                            }
                            arr.put(buildScoreJson(score))
                        }
                        buffer.release()
                    }
                    val payload = JSONObject()
                    payload.put("leaderboard_id", leaderboardId)
                    payload.put("time_span", normalizedTimeSpan)
                    payload.put("collection", normalizedCollection)
                    payload.put("scores", arr)
                    Log.d(TAG, "loadTopScores result: leaderboardId=$leaderboardId timeSpan=$normalizedTimeSpan collection=$normalizedCollection entries=${arr.length()} unknownRanks=$unknownRankCount")
                    activity.runOnUiThread { emitSignalSafe("leaderboard_top_scores_loaded", leaderboardId, payload.toString()) }
                } else {
                    val (code, msg) = getApiExceptionInfo(task.exception)
                    logTaskFailure("loadTopScores", task.exception, task.isCanceled)
                    if (allowAuthRetry && retrySignInForStatusCode(
                            code,
                            "loadTopScores",
                            { loadTopScoresInternal(leaderboardId, timeSpan, collection, maxResults, forceReload, false) },
                            { retryCode, retryMsg ->
                                activity.runOnUiThread {
                                    emitSignalSafe("leaderboard_top_scores_failed", leaderboardId, retryCode.toLong(), retryMsg)
                                }
                            }
                        )) {
                        return@addOnCompleteListener
                    }
                    activity.runOnUiThread { emitSignalSafe("leaderboard_top_scores_failed", leaderboardId, code.toLong(), msg) }
                }
            }
    }

    @UsedByGodot
    fun loadPlayerScore(leaderboardId: String, timeSpan: String, collection: String, forceReload: Boolean) {
        loadPlayerScoreInternal(leaderboardId, timeSpan, collection, forceReload, true)
    }

    private fun loadPlayerScoreInternal(
        leaderboardId: String,
        timeSpan: String,
        collection: String,
        forceReload: Boolean,
        allowAuthRetry: Boolean
    ) {
        Log.d(TAG, "loadPlayerScore called: leaderboardId=$leaderboardId")
        val normalizedTimeSpan = timeSpan.lowercase()
        val normalizedCollection = collection.lowercase()
        val activity = getActivity() ?: run {
            val payload = JSONObject()
            payload.put("leaderboard_id", leaderboardId)
            payload.put("time_span", normalizedTimeSpan)
            payload.put("collection", normalizedCollection)
            payload.put("rank", "-")
            payload.put("rank_value", -1)
            payload.put("rank_known", false)
            payload.put("display_name", "You")
            payload.put("score", 0)
            payload.put("is_player", true)
            emitSignalSafe("leaderboard_player_score_loaded", leaderboardId, payload.toString())
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
                        payload.put("time_span", normalizedTimeSpan)
                        payload.put("collection", normalizedCollection)
                        val rankValue = score.rank
                        val rankKnown = rankValue >= 0
                        Log.d(TAG, "loadPlayerScore result: leaderboardId=$leaderboardId timeSpan=$normalizedTimeSpan collection=$normalizedCollection rank=$rankValue rankKnown=$rankKnown rawScore=${score.rawScore}")
                        activity.runOnUiThread { emitSignalSafe("leaderboard_player_score_loaded", leaderboardId, payload.toString()) }
                    } else {
                        val payload = JSONObject()
                        payload.put("leaderboard_id", leaderboardId)
                        payload.put("time_span", normalizedTimeSpan)
                        payload.put("collection", normalizedCollection)
                        payload.put("rank", "-")
                        payload.put("rank_value", -1)
                        payload.put("rank_known", false)
                        payload.put("display_name", "You")
                        payload.put("score", 0)
                        payload.put("is_player", true)
                        Log.d(TAG, "loadPlayerScore result: leaderboardId=$leaderboardId timeSpan=$normalizedTimeSpan collection=$normalizedCollection score=null")
                        activity.runOnUiThread { emitSignalSafe("leaderboard_player_score_loaded", leaderboardId, payload.toString()) }
                    }
                } else {
                    val (code, msg) = getApiExceptionInfo(task.exception)
                    logTaskFailure("loadPlayerScore", task.exception, task.isCanceled)
                    if (allowAuthRetry && retrySignInForStatusCode(
                            code,
                            "loadPlayerScore",
                            { loadPlayerScoreInternal(leaderboardId, timeSpan, collection, forceReload, false) },
                            { retryCode, retryMsg ->
                                val retryPayload = JSONObject()
                                retryPayload.put("leaderboard_id", leaderboardId)
                                retryPayload.put("time_span", normalizedTimeSpan)
                                retryPayload.put("collection", normalizedCollection)
                                retryPayload.put("rank", "-")
                                retryPayload.put("rank_value", -1)
                                retryPayload.put("rank_known", false)
                                retryPayload.put("display_name", "You")
                                retryPayload.put("score", 0)
                                retryPayload.put("is_player", true)
                                retryPayload.put("status_code", retryCode)
                                retryPayload.put("error", retryMsg)
                                activity.runOnUiThread {
                                    emitSignalSafe("leaderboard_player_score_loaded", leaderboardId, retryPayload.toString())
                                }
                            }
                        )) {
                        return@addOnCompleteListener
                    }
                    val payload = JSONObject()
                    payload.put("leaderboard_id", leaderboardId)
                    payload.put("time_span", normalizedTimeSpan)
                    payload.put("collection", normalizedCollection)
                    payload.put("rank", "-")
                    payload.put("rank_value", -1)
                    payload.put("rank_known", false)
                    payload.put("display_name", "You")
                    payload.put("score", 0)
                    payload.put("is_player", true)
                    payload.put("status_code", code)
                    payload.put("error", msg)
                    activity.runOnUiThread { emitSignalSafe("leaderboard_player_score_loaded", leaderboardId, payload.toString()) }
                }
            }
    }
}
