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
            // First flush WAL with TRUNCATE so that the main database file is 100% up-to-date and complete
            AppDatabase.checkpoint(context)

            val dbFile = context.getDatabasePath("quizer_database")
            if (!dbFile.exists()) return false

            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists()) backupFolder.mkdirs()

            val destDbFile = File(backupFolder, "quizer_database")
            copyFile(dbFile, destDbFile)

            // Remove any old WAL or SHM from backup folder so backup is a clean, single standalone database
            File(backupFolder, "quizer_database-wal").delete()
            File(backupFolder, "quizer_database-shm").delete()

            // Backup prefs
            val sharedPrefsFolder = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsFolder.exists()) {
                sharedPrefsFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xml")) {
                        copyFile(file, File(backupFolder, file.name))
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreBackup(context: Context): Boolean {
        return try {
            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists()) return false

            val backupDbFile = File(backupFolder, "quizer_database")
            if (!backupDbFile.exists() || backupDbFile.length() == 0L) return false

            // 1. Close active database connections and reset singleton so files can be safely replaced
            AppDatabase.closeAndReset()

            val dbFile = context.getDatabasePath("quizer_database")
            val dbFolder = dbFile.parentFile ?: return false
            if (!dbFolder.exists()) dbFolder.mkdirs()

            // 2. Remove existing database, WAL and SHM files to prevent corruption or mismatched state
            File(dbFolder, "quizer_database-wal").delete()
            File(dbFolder, "quizer_database-shm").delete()
            File(dbFolder, "quizer_database").delete()

            // 3. Copy the backup database file
            copyFile(backupDbFile, dbFile)

            // If backup has WAL, copy it too; otherwise ensure no old wal/shm exists
            val backupWal = File(backupFolder, "quizer_database-wal")
            if (backupWal.exists() && backupWal.length() > 0) {
                copyFile(backupWal, File(dbFolder, "quizer_database-wal"))
            } else {
                File(dbFolder, "quizer_database-wal").delete()
            }
            File(dbFolder, "quizer_database-shm").delete()

            // 4. Restore shared prefs
            val prefsDestFolder = File(context.applicationInfo.dataDir, "shared_prefs")
            if (!prefsDestFolder.exists()) prefsDestFolder.mkdirs()
            backupFolder.listFiles()?.forEach { file ->
                if (file.name.endsWith(".xml")) {
                    copyFile(file, File(prefsDestFolder, file.name))
                }
            }

            // 5. Reopen database instance to verify integrity and rebuild Room connection pool
            val newDb = AppDatabase.getDatabase(context)
            newDb.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                cursor.moveToFirst()
            }

            true
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
