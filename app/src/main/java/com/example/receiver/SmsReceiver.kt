package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.example.BuildConfig
import com.example.data.RelayState
import com.example.data.SettingsManager
import com.example.data.SmsDatabase
import com.example.data.SmsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class SmsReceiver : BroadcastReceiver() {

    companion object {
        fun processSmsData(
            context: Context,
            sender: String,
            messageBody: String,
            onComplete: (String) -> Unit = {}
        ) {
            val settingsManager = SettingsManager(context)
            val database = SmsDatabase.getDatabase(context)
            
            val destinationNumber = settingsManager.targetNumber
            if (destinationNumber.isBlank()) {
                android.util.Log.e("SmsReceiver", "No target number configured. SMS ignored.")
                return
            }
            
            val timestamp = System.currentTimeMillis()

            // Generate fingerprint
            val rawData = "$sender|$messageBody"
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(rawData.toByteArray())
            val fingerprint = hashBytes.joinToString("") { "%02x".format(it) }

            CoroutineScope(Dispatchers.IO).launch {
                var resultMessage = "Unknown error"
                try {
                    // Check state
                    val currentState = settingsManager.relayState
                    if (currentState == RelayState.ABORTED) {
                        resultMessage = "Ignored: App is in ABORTED state."
                        return@launch
                    }

                    // Check duplicate
                    val count = database.smsDao().countFingerprint(fingerprint)
                    if (count > 0) {
                        resultMessage = "Ignored: Duplicate SMS detected."
                        logAndSync(database, fingerprint, sender, messageBody, timestamp, "DUPLICATE")
                        return@launch
                    }

                    if (currentState == RelayState.PAUSED) {
                        resultMessage = "Ignored: App is PAUSED."
                        logAndSync(database, fingerprint, sender, messageBody, timestamp, "PAUSED")
                        return@launch
                    }

                    if (currentState == RelayState.ACTIVE) {
                        if (destinationNumber.isBlank() || !destinationNumber.matches(Regex("^[+]?[0-9]{10,15}\$"))) {
                             resultMessage = "Ignored: No configuration."
                             logAndSync(database, fingerprint, sender, messageBody, timestamp, "FAILED_CONFIG")
                             return@launch
                        }

                        // Forward SMS
                        val smsManager = context.getSystemService(SmsManager::class.java)
                        if (smsManager != null) {
                            try {
                                val parts = smsManager.divideMessage(messageBody)
                                if (parts.size > 1) {
                                    smsManager.sendMultipartTextMessage(destinationNumber, null, parts, null, null)
                                } else {
                                    smsManager.sendTextMessage(destinationNumber, null, messageBody, null, null)
                                }
                                resultMessage = "Processed successfully."
                                logAndSync(database, fingerprint, sender, messageBody, timestamp, "SYNCED")
                            } catch (e: Exception) {
                                resultMessage = "Process failed."
                                logAndSync(database, fingerprint, sender, messageBody, timestamp, "ERROR")
                            }
                        } else {
                            resultMessage = "Failed: System unavailable."
                            logAndSync(database, fingerprint, sender, messageBody, timestamp, "ERROR")
                        }
                    }
                } finally {
                    onComplete(resultMessage)
                }
            }
        }

        private suspend fun logAndSync(
            database: SmsDatabase,
            fingerprint: String,
            sender: String,
            body: String,
            timestamp: Long,
            status: String
        ) {
            val log = SmsLog(
                messageFingerprint = fingerprint,
                sender = sender,
                messageBody = body,
                timestamp = timestamp,
                status = status
            )
            database.smsDao().insertLog(log)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val messageBody = messages.joinToString(separator = "") { it.messageBody }

        val pendingResult = goAsync()
        processSmsData(context, sender, messageBody) { _ ->
            pendingResult.finish()
        }
    }
}
