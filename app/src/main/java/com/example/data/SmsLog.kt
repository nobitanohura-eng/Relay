package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageFingerprint: String,
    val sender: String,
    val messageBody: String,
    val timestamp: Long,
    val status: String // "RECEIVED", "SENT", "DELIVERED", "FAILED", "DUPLICATE", "PAUSED", "ABORTED"
)
