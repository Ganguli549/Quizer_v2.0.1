package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {
    fun createBackup(context: Context): Boolean {
        return try {
            // 1. Flush WAL with TRUNCATE so that the main database file is 100% up-to-date and complete
            val checkpointOk = AppDatabase.checkpoint(context)
            if (!checkpointOk) {
                Log.e("BackupManager", "WAL checkpoint failed or returned busy, aborting backup creation")
                return false
            }

            val dbFile = context.getDatabasePath("quizer_database")
            if (!dbFile.exists() || dbFile.length() == 0L) return false

            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists() && !backupFolder.mkdirs()) {
                Log.e("BackupManager", "Failed to create backup directory: ${backupFolder.absolutePath}")
                return false
            }

            // 2. Write to a temporary file first
            val tempBackup = File(backupFolder, "quizer_database_temp.tmp")
            if (tempBackup.exists()) tempBackup.delete()
            copyFile(dbFile, tempBackup)

            // 3. Verify SQLite integrity of candidate backup before finalizing
            val isIntact = verifySqliteIntegrity(tempBackup)
            if (!isIntact) {
                tempBackup.delete()
                Log.e("BackupManager", "Backup verification failed: PRAGMA integrity_check is not ok")
                return false
            }

            val destDbFile = File(backupFolder, "quizer_database")
            val renamed = tempBackup.renameTo(destDbFile)
            if (!renamed) {
                // Fallback copy if rename fails across file systems
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

            // 4. Backup ordinary shared prefs, strictly EXCLUDING Keystore-bound EncryptedSharedPreferences (QuizerPrefs.xml)
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
            if (!backupDbFile.exists() || backupDbFile.length() == 0L) return false

            // 1. Verify candidate backup BEFORE touching live files!
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
                // 3. Remove live files
                walFile.delete()
                shmFile.delete()
                dbFile.delete()

                // 4. Copy verified backup
                copyFile(backupDbFile, dbFile)

                val backupWal = File(backupFolder, "quizer_database-wal")
                if (backupWal.exists() && backupWal.length() > 0) {
                    copyFile(backupWal, walFile)
                }

                // 5. Restore non-encrypted shared prefs
                if (!prefsDestFolder.exists()) prefsDestFolder.mkdirs()
                backupFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml") && !file.name.contains("QuizerPrefs")) {
                        copyFile(file, File(prefsDestFolder, file.name))
                    }
                }

                // 6. Reopen Room database and verify it opens cleanly
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
                // Rollback shared preferences
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
        return dbFile.exists() && dbFile.length() > 0
    }

    fun clearBackup() {
        val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
        if (backupFolder.exists()) {
            backupFolder.listFiles()?.forEach { it.delete() }
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
