package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.data.SettingsManager
import com.example.data.SmsDatabase

class RelayService : Service() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var database: SmsDatabase

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        database = SmsDatabase.getDatabase(this)
        
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "relay_service_channel",
                "Relay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "relay_service_channel")
            .setContentTitle("Relay")
            .setContentText("Relay is running in the background")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .build()
    }
}
