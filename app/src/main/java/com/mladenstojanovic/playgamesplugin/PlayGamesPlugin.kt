package com.mladenstojanovic.playgamesplugin

import android.app.Activity
import android.util.Log
import android.view.View
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
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

    override fun getPluginName(): String {
        return "PlayGamesPlugin"
    }

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("sign_in_success", String::class.java, String::class.java),
            SignalInfo("sign_in_failed"),
            SignalInfo("player_info_loaded", String::class.java, String::class.java)
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

            if (isAuthenticated) {
                loadPlayerInfo()
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
            if (task.isSuccessful && task.result.isAuthenticated) {
                isAuthenticated = true
                Log.d(TAG, "Sign in successful")
                loadPlayerInfo()
                getActivity()?.runOnUiThread {
                    emitSignal("sign_in_success", currentPlayerId, currentPlayerName)
                }
            } else {
                isAuthenticated = false
                Log.e(TAG, "Sign in failed", task.exception)
                getActivity()?.runOnUiThread {
                    emitSignal("sign_in_failed")
                }
            }
        }
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
     * Check if the user is currently signed in
     * @return true if signed in, false otherwise
     */
    @UsedByGodot
    fun isSignedIn(): Boolean {
        Log.d(TAG, "IsSignedIn called: $isAuthenticated")
        return isAuthenticated
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
}
