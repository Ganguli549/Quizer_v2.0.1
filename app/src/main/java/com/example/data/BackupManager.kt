package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {
    fun createBackup(context: Context): Boolean {
        try {
            val dbFolder = context.getDatabasePath("quizer_database").parentFile ?: return false
            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists()) backupFolder.mkdirs()

            val dbs = listOf("quizer_database", "quizer_database-wal", "quizer_database-shm")
            for (dbName in dbs) {
                val dbFile = File(dbFolder, dbName)
                if (dbFile.exists()) {
                    val destFile = File(backupFolder, dbName)
                    copyFile(dbFile, destFile)
                }
            }
            
            // Backup prefs
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/secure_prefs.xml")
            if (prefsFile.exists()) {
                val destFile = File(backupFolder, "secure_prefs.xml")
                copyFile(prefsFile, destFile)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun restoreBackup(context: Context): Boolean {
        try {
            val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
            if (!backupFolder.exists()) return false

            val dbFolder = context.getDatabasePath("quizer_database").parentFile ?: return false
            if (!dbFolder.exists()) dbFolder.mkdirs()

            val dbs = listOf("quizer_database", "quizer_database-wal", "quizer_database-shm")
            for (dbName in dbs) {
                val backupFile = File(backupFolder, dbName)
                if (backupFile.exists()) {
                    val destFile = File(dbFolder, dbName)
                    copyFile(backupFile, destFile)
                }
            }
            
            val prefsBackup = File(backupFolder, "secure_prefs.xml")
            val prefsDest = File(context.applicationInfo.dataDir, "shared_prefs/secure_prefs.xml")
            if (prefsBackup.exists()) {
                val prefsFolder = prefsDest.parentFile
                if (prefsFolder != null && !prefsFolder.exists()) prefsFolder.mkdirs()
                copyFile(prefsBackup, prefsDest)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    fun hasBackup(): Boolean {
        val backupFolder = File(Environment.getExternalStorageDirectory(), "Quizer/Backups")
        return File(backupFolder, "quizer_database").exists()
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
                val buffer = ByteArray(1024)
                var length: Int
                while (inStream.read(buffer).also { length = it } > 0) {
                    outStream.write(buffer, 0, length)
                }
            }
        }
    }
}
