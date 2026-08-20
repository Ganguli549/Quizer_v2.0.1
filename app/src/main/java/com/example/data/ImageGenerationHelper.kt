package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ImageGenerationHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateImage(context: Context, prompt: String): String? = withContext(Dispatchers.IO) {
        val prefs = com.example.data.SecurePrefs.get(context)
        val apiUrl = prefs.getString("image_gen_api_url", "") ?: ""
        val apiKey = prefs.getString("image_gen_api_key", "") ?: ""
        val model = prefs.getString("image_gen_model", "flux") ?: "flux"

        if (apiUrl.isBlank() || apiKey.isBlank()) {
            // Fallback to Pollinations AI
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
            val seed = (0..1000000).random()
            return@withContext "https://image.pollinations.ai/prompt/$encodedPrompt?width=800&height=600&nologo=true&seed=$seed&model=$model"
        }

        // OpenAI compatible API
        try {
            val json = JSONObject()
            json.put("model", model)
            json.put("prompt", prompt)
            json.put("n", 1)
            json.put("size", "1024x1024")

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val resBody = response.body?.string()
                if (resBody != null) {
                    val resJson = JSONObject(resBody)
                    val data = resJson.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        return@withContext data.getJSONObject(0).optString("url")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext null
    }
}
