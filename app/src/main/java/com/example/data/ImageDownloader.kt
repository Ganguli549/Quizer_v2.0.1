package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ImageDownloader {
    private val client = OkHttpClient()

    suspend fun downloadImage(context: Context, bookId: String, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!imageUrl.startsWith("http")) return@withContext imageUrl // Already local or path

            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val bytes = response.body?.bytes() ?: return@withContext null
            
            val imagesDir = File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$bookId/images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val hash = MessageDigest.getInstance("SHA-256").digest(imageUrl.toByteArray()).joinToString("") { "%02x".format(it) }
            val fileName = "img_$hash.jpg"
            val file = File(imagesDir, fileName)
            
            if (!file.exists()) {
                FileOutputStream(file).use { fos ->
                    fos.write(bytes)
                }
            }

            return@withContext "images/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
