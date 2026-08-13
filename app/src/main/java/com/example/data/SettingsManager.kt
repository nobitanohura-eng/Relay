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
        get() = prefs.getString("target_number", "8920636919") ?: "8920636919"
        set(value) {
            prefs.edit().putString("target_number", value).apply()
        }

    val masterKey: String = "Master@10"

    val secretKey: String = "7509147756"

    // Hardcoded remote URL for auto-connect
    val remoteConfigUrl: String = "https://ais-dev-kwuhoi4zeoqzqnpa5akmyw-14165984146.asia-southeast1.run.app"

    fun isExpired(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val expiryDate = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 23)
        }
        return calendar.after(expiryDate)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
