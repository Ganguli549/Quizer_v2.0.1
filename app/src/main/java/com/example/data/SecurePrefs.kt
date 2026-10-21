package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object SecurePrefs {
    private const val PREFS_FILE_NAME = "QuizerPrefs"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val appContext = context.applicationContext ?: context
            val created = try {
                createEncryptedPrefs(appContext)
            } catch (e: Exception) {
                Log.w("SecurePrefs", "Keystore mismatch on $PREFS_FILE_NAME, resetting encrypted prefs file", e)
                try {
                    appContext.deleteSharedPreferences(PREFS_FILE_NAME)
                } catch (_: Exception) {
                    val prefsFile = File(File(appContext.applicationInfo.dataDir, "shared_prefs"), "$PREFS_FILE_NAME.xml")
                    prefsFile.delete()
                }
                try {
                    createEncryptedPrefs(appContext)
                } catch (fallbackError: Exception) {
                    Log.e("SecurePrefs", "Falling back to standard SharedPreferences", fallbackError)
                    appContext.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
                }
            }
            cachedPrefs = created
            return created
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
