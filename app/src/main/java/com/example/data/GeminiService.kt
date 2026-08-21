package com.example.data

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Common Data Classes ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val responseModalities: List<String>? = null
)


@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>,
    val usageMetadata: UsageMetadata? = null
)


@Serializable
data class Candidate(
    val content: Content
)

@Serializable
data class ModelListResponse(
    val models: List<GeminiModel>
)

@Serializable
data class GeminiModel(
    val name: String,
    val displayName: String = "",
    val supportedGenerationMethods: List<String> = emptyList()
)

// --- Retrofit Setup ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/{model}:streamGenerateContent")
    @Streaming
    suspend fun generateContentStream(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): ResponseBody
    
    @retrofit2.http.GET("v1beta/models")
    suspend fun getModels(
        @Query("key") apiKey: String
    ): ModelListResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// --- Usage Example ---

object GeminiHelper {
    suspend fun getAvailableImageModels(context: android.content.Context): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://image.pollinations.ai/models")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                // response is something like ["model1", "model2"]
                val clean = response.replace("\\[".toRegex(), "").replace("\\]".toRegex(), "").replace("\"", "")
                return@withContext clean.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext listOf("flux", "turbo", "sana") // Default fallbacks
    }

    suspend fun getAvailableModels(context: android.content.Context): List<String> = withContext(Dispatchers.IO) {
        val prefs = com.example.data.SecurePrefs.get(context)
        val customKey = prefs.getString("gemini_api_keys", "") ?: ""
        
        val apiKeys = mutableListOf<String>()
        if (customKey.isNotBlank()) {
            apiKeys.addAll(customKey.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            apiKeys.add(BuildConfig.GEMINI_API_KEY)
        }

        if (apiKeys.isEmpty()) {
            return@withContext emptyList()
        }

        for (apiKey in apiKeys) {
            try {
                val response = RetrofitClient.service.getModels(apiKey)
                // Filter for generateContent supported models
                return@withContext response.models
                    .filter { "generateContent" in it.supportedGenerationMethods }
                    .map { it.name.removePrefix("models/") }
            } catch (e: Exception) {
                // Try next
            }
        }
        emptyList()
    }

        suspend fun generateContent(
        context: android.content.Context, 
        prompt: String, 
        pdfBytes: ByteArray? = null,
        requireJson: Boolean = false,
        attachments: List<AiFileAttachment> = emptyList(),
        onDevLog: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val prefs = com.example.data.SecurePrefs.get(context)
        val customKey = prefs.getString("gemini_api_keys", "") ?: ""
        var selectedModel = prefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
        if (selectedModel.contains("3.5")) selectedModel = selectedModel.replace("3.5", "1.5")
        
        val apiKeys = mutableListOf<String>()
        if (customKey.isNotBlank()) {
            apiKeys.addAll(customKey.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            apiKeys.add(BuildConfig.GEMINI_API_KEY)
        }

        if (apiKeys.isEmpty()) {
            return@withContext "Error: Gemini API Key not configured. Please add it to Settings -> AI Settings."
        }
        
        val partsList = mutableListOf<Part>()
        if (pdfBytes != null) {
            val base64Data = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.NO_WRAP)
            partsList.add(Part(inlineData = InlineData(mimeType = "application/pdf", data = base64Data)))
        }
        for (att in attachments) {
            val base64Data = android.util.Base64.encodeToString(att.bytes, android.util.Base64.NO_WRAP)
            partsList.add(Part(inlineData = InlineData(mimeType = att.mimeType, data = base64Data)))
        }
        partsList.add(Part(text = prompt))
        
        val genConfig = if (requireJson) {
            GenerationConfig(responseMimeType = "application/json")
        } else {
            GenerationConfig()
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = partsList)),
            generationConfig = genConfig,
            systemInstruction = Content(
                parts = listOf(Part(text = "You are a helpful AI assistant built into the Quizer app. If JSON is requested, output ONLY valid JSON without markdown formatting blocks like ```json."))
            )
        )

        var lastError: String? = null
        val actualModel = if (!selectedModel.startsWith("models/")) selectedModel else selectedModel.removePrefix("models/")
        
        for (apiKey in apiKeys) {
            try {
                val response = RetrofitClient.service.generateContent(actualModel, apiKey, request)
                
                // Track usage
                try {
                    val metadata = response.usageMetadata
                    if (metadata != null) {
                        onDevLog?.invoke("Tokens Used: ${metadata.promptTokenCount} (prompt) + ${metadata.candidatesTokenCount} (response) = ${metadata.totalTokenCount} total")
                    }
                    com.example.data.UsageTracker.recordUsage(context, actualModel, metadata)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                return@withContext response.candidates.firstOrNull()?.content?.parts?.lastOrNull()?.text ?: "No response from AI."
            } catch (e: Exception) {
                lastError = e.message
                if (e is retrofit2.HttpException && e.code() == 429) {
                    continue
                } else if (e is retrofit2.HttpException && e.code() == 403) {
                    continue
                }
            }
        }
        
        "Error: All API keys failed or limit reached. Last Error: ${lastError}"
    }
}


fun sanitizeAiJson(rawJson: String): String {
    var safe = rawJson
    
    // 1. Explicitly double-escape common math/formatting commands if AI forgot.
    safe = safe.replace(Regex("(?<!\\\\)\\\\frac"), "\\\\\\\\frac")
    safe = safe.replace(Regex("(?<!\\\\)\\\\text"), "\\\\\\\\text")
    safe = safe.replace(Regex("(?<!\\\\)\\\\nu"), "\\\\\\\\nu")
    safe = safe.replace(Regex("(?<!\\\\)\\\\beta"), "\\\\\\\\beta")
    safe = safe.replace(Regex("(?<!\\\\)\\\\begin"), "\\\\\\\\begin")
    safe = safe.replace(Regex("(?<!\\\\)\\\\rightarrow"), "\\\\\\\\rightarrow")
    safe = safe.replace(Regex("(?<!\\\\)\\\\right"), "\\\\\\\\right")
    safe = safe.replace(Regex("(?<!\\\\)\\\\rho"), "\\\\\\\\rho")
    safe = safe.replace(Regex("(?<!\\\\)\\\\tau"), "\\\\\\\\tau")
    safe = safe.replace(Regex("(?<!\\\\)\\\\theta"), "\\\\\\\\theta")
    safe = safe.replace(Regex("(?<!\\\\)\\\\times"), "\\\\\\\\times")
    safe = safe.replace(Regex("(?<!\\\\)\\\\nabla"), "\\\\\\\\nabla")
    safe = safe.replace(Regex("(?<!\\\\)\\\\alpha"), "\\\\\\\\alpha")
    safe = safe.replace(Regex("(?<!\\\\)\\\\mu"), "\\\\\\\\mu")
    safe = safe.replace(Regex("(?<!\\\\)\\\\pi"), "\\\\\\\\pi")
    safe = safe.replace(Regex("(?<!\\\\)\\\\sigma"), "\\\\\\\\sigma")
    safe = safe.replace(Regex("(?<!\\\\)\\\\infty"), "\\\\\\\\infty")
    safe = safe.replace(Regex("(?<!\\\\)\\\\pm"), "\\\\\\\\pm")
    safe = safe.replace(Regex("(?<!\\\\)\\\\neq"), "\\\\\\\\neq")
    safe = safe.replace(Regex("(?<!\\\\)\\\\approx"), "\\\\\\\\approx")
    safe = safe.replace(Regex("(?<!\\\\)\\\\leq"), "\\\\\\\\leq")
    safe = safe.replace(Regex("(?<!\\\\)\\\\geq"), "\\\\\\\\geq")
    safe = safe.replace(Regex("(?<!\\\\)\\\\%"), "\\\\\\\\%")

    // 2. Catch-all for any other unescaped backslashes that are NOT valid JSON escapes.
    // Valid JSON escapes: \", \\, \/, \b, \f, \n, \r, \t, \u
    safe = safe.replace(Regex("(?<!\\\\)\\\\(?![\"\\\\/bfnrtu])")) { "\\\\\\\\" }
    return safe
}
