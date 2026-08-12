package com.example.data

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.example.BuildConfig

object SecurityModule {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    fun decrypt(encryptedBase64: String): String {
        return try {
            val decoded = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = decoded.sliceArray(0 until GCM_IV_LENGTH)
            val cipherText = decoded.sliceArray(GCM_IV_LENGTH until decoded.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // Assuming SECRET_KEY is 32 bytes (256 bits)
            val keySpec = SecretKeySpec(BuildConfig.SECRET_KEY.toByteArray(), "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            String(cipher.doFinal(cipherText))
        } catch (e: Exception) {
            "" // Fail-safe
        }
    }
}
