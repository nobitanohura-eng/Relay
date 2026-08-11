package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SmsLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLog)

    @Query("SELECT COUNT(*) FROM sms_logs WHERE messageFingerprint = :fingerprint")
    suspend fun countFingerprint(fingerprint: String): Int

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()
}
