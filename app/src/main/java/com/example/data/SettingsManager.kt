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

    var remoteConfigUrl: String
        get() = prefs.getString("remote_config_url", "") ?: ""
        set(value) {
            prefs.edit().putString("remote_config_url", value).apply()
        }

    var pairingToken: String?
        get() = prefs.getString("pairing_token", null)
        set(value) {
            if (value == null) {
                prefs.edit().remove("pairing_token").apply()
            } else {
                prefs.edit().putString("pairing_token", value).apply()
            }
        }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
