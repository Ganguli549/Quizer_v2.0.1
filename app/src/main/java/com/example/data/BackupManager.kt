package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Serializable
data class AiAndSecureSettingsBackup(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val geminiApiKeys: String = "",
    val legacyGeminiApiKey: String = "",
    val geminiModel: String = "gemini-flash-lite-latest",
    val imageGenModel: String = "flux",
    val imageGenApiUrl: String = "",
    val imageGenApiKey: String = "",
    val apiUsageDataJson: String = "{}",
    val autoSaveEnabled: Boolean = true,
    val readingTheme: Int = 0,
    val extraStrings: Map<String, String> = emptyMap(),
    val extraBooleans: Map<String, Boolean> = emptyMap(),
    val extraInts: Map<String, Int> = emptyMap(),
    val extraLongs: Map<String, Long> = emptyMap(),
    val extraFloats: Map<String, Float> = emptyMap()
)

object BackupManager {
    private const val AI_SETTINGS_BACKUP_FILE = "quizer_ai_and_secure_settings.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Lightweight async helper that updates `quizer_ai_and_secure_settings.json` inside `Quizer/Backups`
     * whenever AI settings or AI usage change and a backup folder already exists.
     */
    fun backupAiSettingsAndUsageAsync(context: Context) {
        val appContext = context.applicationContext ?: context
        backgroundScope.launch {
            try {
                val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
                if (backupFolder.exists() || hasBackup()) {
                    if (!backupFolder.exists()) backupFolder.mkdirs()
                    MediaScannerHelper.ensureNoMediaInDir(backupFolder)
                    writeAiAndSecureSettingsBackup(appContext, backupFolder)
                }
            } catch (e: Exception) {
                Log.w("BackupManager", "Failed to sync AI settings/usage to backup", e)
            }
        }
    }

    fun createBackup(context: Context): Boolean {
        return try {
            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists() && !backupFolder.mkdirs()) {
                Log.e("BackupManager", "Failed to create backup directory: ${backupFolder.absolutePath}")
                return false
            }
            MediaScannerHelper.ensureNoMediaInDir(backupFolder)

            // 1. Always export AI settings, API keys, models, and AI Usage statistics from SecurePrefs
            val aiBackupOk = writeAiAndSecureSettingsBackup(context, backupFolder)

            // 2. Flush WAL with TRUNCATE so that the main database file is 100% up-to-date and complete
            val checkpointOk = AppDatabase.checkpoint(context)
            if (!checkpointOk) {
                Log.e("BackupManager", "WAL checkpoint failed or returned busy, aborting DB backup creation")
                return false
            }

            val dbFile = context.getDatabasePath("quizer_database")
            if (!dbFile.exists() || dbFile.length() == 0L) return aiBackupOk

            // 3. Write database to a temporary file first
            val tempBackup = File(backupFolder, "quizer_database_temp.tmp")
            if (tempBackup.exists()) tempBackup.delete()
            copyFile(dbFile, tempBackup)

            // 4. Verify SQLite integrity of candidate backup before finalizing
            val isIntact = verifySqliteIntegrity(tempBackup)
            if (!isIntact) {
                tempBackup.delete()
                Log.e("BackupManager", "Backup verification failed: PRAGMA integrity_check is not ok")
                return false
            }

            val destDbFile = File(backupFolder, "quizer_database")
            val renamed = tempBackup.renameTo(destDbFile)
            if (!renamed) {
                copyFile(tempBackup, destDbFile)
                tempBackup.delete()
            }

            if (!destDbFile.exists() || destDbFile.length() == 0L) {
                Log.e("BackupManager", "Final destination backup file is invalid")
                return false
            }

            // Remove any old WAL or SHM from backup folder so backup is a clean, single standalone database
            File(backupFolder, "quizer_database-wal").delete()
            File(backupFolder, "quizer_database-shm").delete()

            // 5. Backup ordinary shared prefs XML files (excluding hardware Keystore-bound QuizerPrefs.xml,
            // which is portably backed up via quizer_ai_and_secure_settings.json in Step 1)
            val sharedPrefsFolder = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsFolder.exists()) {
                sharedPrefsFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml") && !file.name.contains("QuizerPrefs")) {
                        copyFile(file, File(backupFolder, file.name))
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Error creating backup", e)
            false
        }
    }

    private fun writeAiAndSecureSettingsBackup(context: Context, backupFolder: File): Boolean {
        return try {
            val prefs = SecurePrefs.get(context)
            val allEntries = prefs.all

            val extraStrings = mutableMapOf<String, String>()
            val extraBooleans = mutableMapOf<String, Boolean>()
            val extraInts = mutableMapOf<String, Int>()
            val extraLongs = mutableMapOf<String, Long>()
            val extraFloats = mutableMapOf<String, Float>()

            for ((k, v) in allEntries) {
                if (k == "isFirstRun" || k == "is_first_launch") continue
                when (v) {
                    is String -> extraStrings[k] = v
                    is Boolean -> extraBooleans[k] = v
                    is Int -> extraInts[k] = v
                    is Long -> extraLongs[k] = v
                    is Float -> extraFloats[k] = v
                }
            }

            val payload = AiAndSecureSettingsBackup(
                version = 1,
                timestamp = System.currentTimeMillis(),
                geminiApiKeys = prefs.getString("gemini_api_keys", "") ?: "",
                legacyGeminiApiKey = prefs.getString("gemini_api_key", "") ?: "",
                geminiModel = prefs.getString("gemini_model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest",
                imageGenModel = prefs.getString("image_gen_model", "flux") ?: "flux",
                imageGenApiUrl = prefs.getString("image_gen_api_url", "") ?: "",
                imageGenApiKey = prefs.getString("image_gen_api_key", "") ?: "",
                apiUsageDataJson = UsageTracker.exportUsageJson(context),
                autoSaveEnabled = prefs.getBoolean("auto_save_enabled", true),
                readingTheme = prefs.getInt("reading_theme", 0),
                extraStrings = extraStrings,
                extraBooleans = extraBooleans,
                extraInts = extraInts,
                extraLongs = extraLongs,
                extraFloats = extraFloats
            )

            val tempJsonFile = File(backupFolder, "${AI_SETTINGS_BACKUP_FILE}.tmp")
            val destJsonFile = File(backupFolder, AI_SETTINGS_BACKUP_FILE)
            tempJsonFile.writeText(json.encodeToString(payload))
            if (!tempJsonFile.renameTo(destJsonFile)) {
                copyFile(tempJsonFile, destJsonFile)
                tempJsonFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to write AI settings & usage backup", e)
            false
        }
    }

    private fun restoreAiAndSecureSettingsBackup(context: Context, backupFolder: File) {
        try {
            val aiBackupFile = File(backupFolder, AI_SETTINGS_BACKUP_FILE)
            if (!aiBackupFile.exists() || aiBackupFile.length() == 0L) return

            val rawJson = aiBackupFile.readText()
            val payload = json.decodeFromString<AiAndSecureSettingsBackup>(rawJson)
            val prefs = SecurePrefs.get(context)
            val editor = prefs.edit()

            // Restore generic SecurePrefs entries first
            for ((k, v) in payload.extraStrings) {
                if (k != UsageTracker.USAGE_KEY) editor.putString(k, v)
            }
            for ((k, v) in payload.extraBooleans) {
                if (k != "isFirstRun" && k != "is_first_launch") editor.putBoolean(k, v)
            }
            for ((k, v) in payload.extraInts) {
                editor.putInt(k, v)
            }
            for ((k, v) in payload.extraLongs) {
                editor.putLong(k, v)
            }
            for ((k, v) in payload.extraFloats) {
                editor.putFloat(k, v)
            }

            // Explicitly restore AI Settings keys
            if (payload.geminiApiKeys.isNotBlank() || !prefs.contains("gemini_api_keys")) {
                editor.putString("gemini_api_keys", payload.geminiApiKeys)
            }
            if (payload.legacyGeminiApiKey.isNotBlank()) {
                editor.putString("gemini_api_key", payload.legacyGeminiApiKey)
            }
            if (payload.geminiModel.isNotBlank()) {
                editor.putString("gemini_model", payload.geminiModel)
            }
            if (payload.imageGenModel.isNotBlank()) {
                editor.putString("image_gen_model", payload.imageGenModel)
            }
            if (payload.imageGenApiUrl.isNotBlank() || !prefs.contains("image_gen_api_url")) {
                editor.putString("image_gen_api_url", payload.imageGenApiUrl)
            }
            if (payload.imageGenApiKey.isNotBlank() || !prefs.contains("image_gen_api_key")) {
                editor.putString("image_gen_api_key", payload.imageGenApiKey)
            }

            editor.putBoolean("auto_save_enabled", payload.autoSaveEnabled)
            editor.putInt("reading_theme", payload.readingTheme)
            editor.putBoolean("isFirstRun", false)
            editor.putBoolean("is_first_launch", false)
            editor.apply()

            // Restore and merge AI Usage statistics (requests, tokens, per-model breakdown, and history log)
            UsageTracker.restoreOrMergeUsageJson(context, payload.apiUsageDataJson)
        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to restore AI settings & usage backup", e)
        }
    }

    /**
     * Parses a backed-up Android SharedPreferences XML file and applies its entries directly to
     * the live in-memory [SharedPreferences] instance so restored settings take effect immediately
     * without requiring a cold process restart.
     */
    private fun applySharedPrefsXmlToMemory(context: Context, xmlFile: File) {
        val prefName = xmlFile.nameWithoutExtension
        if (prefName.isBlank() || prefName.contains("QuizerPrefs")) return
        try {
            val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val editor = sp.edit()
            FileInputStream(xmlFile).use { inputStream ->
                val parser = Xml.newPullParser()
                parser.setInput(inputStream, "UTF-8")
                var eventType = parser.eventType
                var currentSetKey: String? = null
                var currentSetValues: MutableSet<String>? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tagName = parser.name
                            val key = parser.getAttributeValue(null, "name")
                            when (tagName) {
                                "string" -> {
                                    if (key != null && currentSetKey == null) {
                                        editor.putString(key, parser.nextText())
                                    } else if (currentSetKey != null) {
                                        currentSetValues?.add(parser.nextText())
                                    }
                                }
                                "boolean" -> {
                                    val value = parser.getAttributeValue(null, "value")?.toBoolean() ?: false
                                    if (key != null) editor.putBoolean(key, value)
                                }
                                "int" -> {
                                    val value = parser.getAttributeValue(null, "value")?.toIntOrNull()
                                    if (key != null && value != null) editor.putInt(key, value)
                                }
                                "long" -> {
                                    val value = parser.getAttributeValue(null, "value")?.toLongOrNull()
                                    if (key != null && value != null) editor.putLong(key, value)
                                }
                                "float" -> {
                                    val value = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                    if (key != null && value != null) editor.putFloat(key, value)
                                }
                                "set" -> {
                                    if (key != null) {
                                        currentSetKey = key
                                        currentSetValues = LinkedHashSet()
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "set" && currentSetKey != null) {
                                editor.putStringSet(currentSetKey, currentSetValues ?: emptySet())
                                currentSetKey = null
                                currentSetValues = null
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
            editor.apply()
        } catch (e: Exception) {
            Log.w("BackupManager", "Failed to sync $prefName.xml into in-memory SharedPreferences", e)
        }
    }

    private fun verifySqliteIntegrity(sqliteFile: File): Boolean {
        if (!sqliteFile.exists() || sqliteFile.length() < 100L) return false
        var db: android.database.sqlite.SQLiteDatabase? = null
        return try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(
                sqliteFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            var ok = false
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    ok = "ok".equals(result, ignoreCase = true)
                }
            }
            ok
        } catch (e: Exception) {
            Log.e("BackupManager", "SQLite integrity verification failed", e)
            false
        } finally {
            try { db?.close() } catch (ex: Exception) {}
        }
    }

    fun restoreBackup(context: Context): Boolean {
        return try {
            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists()) return false

            val backupDbFile = File(backupFolder, "quizer_database")
            val aiBackupFile = File(backupFolder, AI_SETTINGS_BACKUP_FILE)
            val hasDbBackup = backupDbFile.exists() && backupDbFile.length() > 0L
            val hasAiBackup = aiBackupFile.exists() && aiBackupFile.length() > 0L

            if (!hasDbBackup && !hasAiBackup) return false

            if (!hasDbBackup && hasAiBackup) {
                restoreAiAndSecureSettingsBackup(context, backupFolder)
                return true
            }

            // 1. Verify candidate database backup BEFORE touching live files!
            val isBackupValid = verifySqliteIntegrity(backupDbFile)
            if (!isBackupValid) {
                Log.e("BackupManager", "Cannot restore: candidate backup failed integrity check")
                return false
            }

            val dbFile = context.getDatabasePath("quizer_database")
            val dbFolder = dbFile.parentFile ?: return false
            if (!dbFolder.exists() && !dbFolder.mkdirs()) return false

            // 2. Keep rollback copies of live database and preference files
            val walFile = File(dbFolder, "quizer_database-wal")
            val shmFile = File(dbFolder, "quizer_database-shm")
            val rollbackDb = File(dbFolder, "quizer_database_rollback.bak")
            val rollbackWal = File(dbFolder, "quizer_database_wal_rollback.bak")

            val prefsDestFolder = File(context.applicationInfo.dataDir, "shared_prefs")
            val prefsRollbackFolder = File(context.cacheDir, "prefs_rollback_temp")
            if (prefsRollbackFolder.exists()) prefsRollbackFolder.deleteRecursively()
            prefsRollbackFolder.mkdirs()

            if (prefsDestFolder.exists()) {
                prefsDestFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml")) {
                        copyFile(file, File(prefsRollbackFolder, file.name))
                    }
                }
            }

            AppDatabase.closeAndReset()

            if (dbFile.exists()) copyFile(dbFile, rollbackDb)
            if (walFile.exists()) copyFile(walFile, rollbackWal)

            try {
                // 3. Remove live database files
                walFile.delete()
                shmFile.delete()
                dbFile.delete()

                // 4. Copy verified database backup
                copyFile(backupDbFile, dbFile)

                val backupWal = File(backupFolder, "quizer_database-wal")
                if (backupWal.exists() && backupWal.length() > 0) {
                    copyFile(backupWal, walFile)
                }

                // 5. Restore non-encrypted shared prefs both on disk and in live memory
                if (!prefsDestFolder.exists()) prefsDestFolder.mkdirs()
                backupFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml") && !file.name.contains("QuizerPrefs")) {
                        copyFile(file, File(prefsDestFolder, file.name))
                        applySharedPrefsXmlToMemory(context, file)
                    }
                }

                // 6. Restore AI Settings (API keys, models, image gen config) and AI Usages into SecurePrefs
                restoreAiAndSecureSettingsBackup(context, backupFolder)

                // 7. Reopen Room database and verify it opens cleanly
                val newDb = AppDatabase.getDatabase(context)
                var integrityOk = false
                newDb.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                    if (cursor.moveToFirst()) {
                        integrityOk = "ok".equals(cursor.getString(0), ignoreCase = true)
                    }
                }

                if (!integrityOk) {
                    throw IllegalStateException("Restored database failed runtime integrity check")
                }

                // Restore succeeded: clean up rollback copies
                rollbackDb.delete()
                rollbackWal.delete()
                prefsRollbackFolder.deleteRecursively()
                true
            } catch (restoreError: Exception) {
                Log.e("BackupManager", "Error replacing live database, rolling back to previous state", restoreError)
                AppDatabase.closeAndReset()
                if (rollbackDb.exists()) {
                    copyFile(rollbackDb, dbFile)
                    rollbackDb.delete()
                }
                if (rollbackWal.exists()) {
                    copyFile(rollbackWal, walFile)
                    rollbackWal.delete()
                }
                if (prefsRollbackFolder.exists()) {
                    prefsRollbackFolder.listFiles()?.forEach { file ->
                        copyFile(file, File(prefsDestFolder, file.name))
                    }
                    prefsRollbackFolder.deleteRecursively()
                }
                AppDatabase.getDatabase(context)
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasBackup(): Boolean {
        val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
        val dbFile = File(backupFolder, "quizer_database")
        val aiFile = File(backupFolder, AI_SETTINGS_BACKUP_FILE)
        return (dbFile.exists() && dbFile.length() > 0) || (aiFile.exists() && aiFile.length() > 0)
    }

    fun clearBackup() {
        val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
        if (backupFolder.exists()) {
            backupFolder.listFiles()?.forEach { file ->
                if (file.name != ".nomedia") {
                    file.delete()
                }
            }
        }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                val buffer = ByteArray(65536)
                var length: Int
                while (inStream.read(buffer).also { length = it } > 0) {
                    outStream.write(buffer, 0, length)
                }
            }
        }
    }
}
