package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class RelayState {
    ACTIVE,
    PAUSED,
    ABORTED
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)

    var relayState: RelayState
        get() = try {
            RelayState.valueOf(prefs.getString("relay_state", RelayState.ACTIVE.name) ?: RelayState.ACTIVE.name)
        } catch (e: Exception) {
            RelayState.ACTIVE
        }
        set(value) {
            prefs.edit().putString("relay_state", value.name).apply()
        }

    var targetNumber: String
        get() = prefs.getString("target_number", "") ?: ""
        set(value) {
            prefs.edit().putString("target_number", value).apply()
        }

    val masterKey: String = "Master@10"

    val secretKey: String = "7509147756"

    // Hardcoded remote URL for auto-connect
    val remoteConfigUrl: String = "https://ais-dev-kwuhoi4zeoqzqnpa5akmyw-14165984146.asia-southeast1.run.app"

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
