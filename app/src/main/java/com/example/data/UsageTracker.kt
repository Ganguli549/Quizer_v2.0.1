package com.example.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    const val USAGE_KEY = "api_usage_data"

    // Configured Json to ignore unknown keys and encode default values
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

        // Keep backup's AI settings & usage snapshot up to date when auto-save is enabled
        if (prefs.getBoolean("auto_save_enabled", true)) {
            BackupManager.backupAiSettingsAndUsageAsync(context)
        }
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

    fun exportUsageJson(context: Context): String {
        val usageData = getUsage(context)
        return try {
            json.encodeToString(usageData)
        } catch (e: Exception) {
            "{}"
        }
    }

    /**
     * Restores or merges backed-up [UsageData] with any existing local usage data
     * so neither historical records nor recent records are lost.
     */
    fun restoreOrMergeUsageJson(context: Context, backupUsageJson: String?) {
        if (backupUsageJson.isNullOrBlank() || backupUsageJson == "{}") return
        try {
            val backupData = json.decodeFromString<UsageData>(backupUsageJson)
            val currentData = getUsage(context)

            val finalData = if (currentData.totalRequests == 0 && currentData.history.isEmpty()) {
                backupData
            } else {
                // Merge history records (deduplicated by timestamp + model + totalTokens)
                val mergedHistory = (currentData.history + backupData.history)
                    .distinctBy { Triple(it.timestamp, it.model, it.totalTokens) }
                    .sortedByDescending { it.timestamp }
                    .take(1000)

                val mergedModels = mutableMapOf<String, ModelUsage>()
                val allModelKeys = currentData.models.keys + backupData.models.keys
                for (modelKey in allModelKeys) {
                    val curM = currentData.models[modelKey]
                    val bakM = backupData.models[modelKey]
                    val historyForModel = mergedHistory.filter { it.model == modelKey }
                    val histReqs = historyForModel.size
                    val histPrompt = historyForModel.sumOf { it.promptTokens }
                    val histCand = historyForModel.sumOf { it.candidateTokens }
                    val histTotal = historyForModel.sumOf { it.totalTokens }

                    mergedModels[modelKey] = ModelUsage(
                        requests = maxOf(curM?.requests ?: 0, bakM?.requests ?: 0, histReqs),
                        promptTokens = maxOf(curM?.promptTokens ?: 0, bakM?.promptTokens ?: 0, histPrompt),
                        candidateTokens = maxOf(curM?.candidateTokens ?: 0, bakM?.candidateTokens ?: 0, histCand),
                        totalTokens = maxOf(curM?.totalTokens ?: 0, bakM?.totalTokens ?: 0, histTotal)
                    )
                }

                val sumModelRequests = mergedModels.values.sumOf { it.requests }
                val sumModelTokens = mergedModels.values.sumOf { it.totalTokens }

                UsageData(
                    totalRequests = maxOf(currentData.totalRequests, backupData.totalRequests, sumModelRequests),
                    totalTokens = maxOf(currentData.totalTokens, backupData.totalTokens, sumModelTokens),
                    models = mergedModels,
                    history = mergedHistory
                )
            }

            SecurePrefs.get(context).edit()
                .putString(USAGE_KEY, json.encodeToString(finalData))
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearUsage(context: Context) {
        val prefs = SecurePrefs.get(context)
        prefs.edit().remove(USAGE_KEY).apply()
        if (prefs.getBoolean("auto_save_enabled", true)) {
            BackupManager.backupAiSettingsAndUsageAsync(context)
        }
    }
}
