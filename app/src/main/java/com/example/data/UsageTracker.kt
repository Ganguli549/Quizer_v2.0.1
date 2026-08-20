package com.example.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class UsageRecord(
    val timestamp: Long,
    val model: String,
    val promptTokens: Int,
    val candidateTokens: Int,
    val totalTokens: Int
)

@Serializable
data class ModelUsage(
    var requests: Int = 0,
    var promptTokens: Int = 0,
    var candidateTokens: Int = 0,
    var totalTokens: Int = 0
)

@Serializable
data class UsageData(
    var totalRequests: Int = 0,
    var totalTokens: Int = 0,
    val models: MutableMap<String, ModelUsage> = mutableMapOf(),
    var history: List<UsageRecord> = emptyList()
)

object UsageTracker {
    private const val USAGE_KEY = "api_usage_data"
    
    // Configured Json to ignore unknown keys in case we add more fields later
    private val json = Json { ignoreUnknownKeys = true }

    fun recordUsage(context: Context, model: String, metadata: UsageMetadata?) {
        val prefs = SecurePrefs.get(context)
        val usageStr = prefs.getString(USAGE_KEY, "{}") ?: "{}"
        
        val usageData = try {
            json.decodeFromString<UsageData>(usageStr)
        } catch (e: Exception) {
            UsageData()
        }

        usageData.totalRequests += 1
        
        val modelUsage = usageData.models.getOrPut(model) { ModelUsage() }
        modelUsage.requests += 1

        var pTokens = 0
        var cTokens = 0
        var tTokens = 0

        if (metadata != null) {
            pTokens = metadata.promptTokenCount
            cTokens = metadata.candidatesTokenCount
            tTokens = metadata.totalTokenCount
            
            usageData.totalTokens += tTokens
            modelUsage.promptTokens += pTokens
            modelUsage.candidateTokens += cTokens
            modelUsage.totalTokens += tTokens
        }
        
        // Save to history (latest first) and keep maximum 1000 records
        val newRecord = UsageRecord(System.currentTimeMillis(), model, pTokens, cTokens, tTokens)
        val updatedHistory = usageData.history.toMutableList()
        updatedHistory.add(0, newRecord)
        usageData.history = updatedHistory.take(1000)

        prefs.edit().putString(USAGE_KEY, json.encodeToString(usageData)).apply()
    }

    fun getUsage(context: Context): UsageData {
        val prefs = SecurePrefs.get(context)
        val usageStr = prefs.getString(USAGE_KEY, "{}") ?: "{}"
        return try {
            json.decodeFromString<UsageData>(usageStr)
        } catch (e: Exception) {
            UsageData()
        }
    }
    
    fun clearUsage(context: Context) {
        val prefs = SecurePrefs.get(context)
        prefs.edit().remove(USAGE_KEY).apply()
    }
}
