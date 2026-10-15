package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

object MediaScannerHelper {

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    fun scanFile(context: Context?, file: File) {
        if (context == null || !file.exists() || !file.isFile) return
        val appContext = context.applicationContext
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val mimeType = getMimeType(file)
                MediaScannerConnection.scanFile(
                    appContext,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType)
                ) { path, uri ->
                    Log.d("MediaScannerHelper", "Scanned $path -> $uri")
                }

                try {
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    intent.data = Uri.fromFile(file)
                    appContext.sendBroadcast(intent)
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Quizer")
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scanFiles(context: Context?, files: List<File>) {
        if (context == null || files.isEmpty()) return
        val appContext = context.applicationContext
        val validFiles = files.filter { it.exists() && it.isFile }
        if (validFiles.isEmpty()) return
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val paths = validFiles.map { it.absolutePath }.toTypedArray()
                val mimeTypes = validFiles.map { getMimeType(it) }.toTypedArray()
                MediaScannerConnection.scanFile(
                    appContext,
                    paths,
                    mimeTypes
                ) { path, uri ->
                    Log.d("MediaScannerHelper", "Batch scanned $path -> $uri")
                }
                for (file in validFiles) {
                    try {
                        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        intent.data = Uri.fromFile(file)
                        appContext.sendBroadcast(intent)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scanAllQuizerFolderImages(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val rootDirs = listOf(
                    File(android.os.Environment.getExternalStorageDirectory(), "Quizer"),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Quizer"),
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Quizer")
                )
                val allImages = mutableListOf<File>()
                for (rootDir in rootDirs) {
                    if (rootDir.exists() && rootDir.isDirectory) {
                        rootDir.walkTopDown().forEach { file ->
                            if (file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")) {
                                allImages.add(file)
                            }
                        }
                    }
                }
                if (allImages.isNotEmpty()) {
                    scanFiles(appContext, allImages)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
