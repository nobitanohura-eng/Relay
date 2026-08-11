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
        get() = RelayState.ACTIVE
        set(value) {
            prefs.edit().putString("relay_state", value.name).apply()
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
