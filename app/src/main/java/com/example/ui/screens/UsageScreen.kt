package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.UsageData
import com.example.data.UsageTracker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

fun getDailyKey(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy")
    sdf.timeZone = TimeZone.getTimeZone("America/Los_Angeles")
    return sdf.format(Date(timestamp))
}

fun getWeeklyKey(timestamp: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"))
    cal.timeInMillis = timestamp
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    val startOfWeek = cal.time
    cal.add(Calendar.DAY_OF_WEEK, 6)
    val endOfWeek = cal.time
    
    val sdf = SimpleDateFormat("MMM dd")
    sdf.timeZone = TimeZone.getTimeZone("America/Los_Angeles")
    return "${sdf.format(startOfWeek)} - ${sdf.format(endOfWeek)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var usageData by remember { mutableStateOf(UsageTracker.getUsage(context)) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Daily", "Weekly", "Recent")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Usage (PT)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        UsageTracker.clearUsage(context)
                        usageData = UsageTracker.getUsage(context)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Usage")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> OverviewTab(usageData)
                1 -> DailyTab(usageData)
                2 -> WeeklyTab(usageData)
                3 -> RecentTab(usageData)
            }
        }
    }
}

@Composable
fun OverviewTab(usageData: UsageData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Lifetime Usage", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Total Requests: ${usageData.totalRequests}", style = MaterialTheme.typography.bodyLarge)
                    Text("Total Tokens: ${usageData.totalTokens}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (usageData.models.isEmpty()) {
            item {
                Text("No usage recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item {
                Text("Usage by Model", style = MaterialTheme.typography.titleMedium)
            }
            
            items(usageData.models.toList()) { (modelName, modelUsage) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(modelName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Requests: ${modelUsage.requests}")
                        Text("Prompt Tokens: ${modelUsage.promptTokens}")
                        Text("Candidate Tokens: ${modelUsage.candidateTokens}")
                        Text("Total Tokens: ${modelUsage.totalTokens}")
                    }
                }
            }
        }
    }
}

@Composable
fun DailyTab(usageData: UsageData) {
    val dailyStats = remember(usageData) {
        usageData.history.groupBy { getDailyKey(it.timestamp) }
            .mapValues { entry -> 
                Pair(entry.value.size, entry.value.sumOf { it.totalTokens })
            }.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (dailyStats.isEmpty()) {
            item { Text("No daily data available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(dailyStats) { (day, stats) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(day, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${stats.first} reqs", style = MaterialTheme.typography.bodySmall)
                        Text("${stats.second} tokens", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyTab(usageData: UsageData) {
    val weeklyStats = remember(usageData) {
        usageData.history.groupBy { getWeeklyKey(it.timestamp) }
            .mapValues { entry -> 
                Pair(entry.value.size, entry.value.sumOf { it.totalTokens })
            }.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (weeklyStats.isEmpty()) {
            item { Text("No weekly data available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(weeklyStats) { (week, stats) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(week, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${stats.first} reqs", style = MaterialTheme.typography.bodySmall)
                        Text("${stats.second} tokens", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTab(usageData: UsageData) {
    val recent = usageData.history.take(10)
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (recent.isEmpty()) {
            item { Text("No recent requests.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(recent) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val time = remember(record.timestamp) {
                            val sdf = SimpleDateFormat("MMM dd, HH:mm:ss")
                            sdf.timeZone = TimeZone.getTimeZone("America/Los_Angeles")
                            sdf.format(Date(record.timestamp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(record.model, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(time, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Prompt: ${record.promptTokens}", style = MaterialTheme.typography.bodySmall)
                            Text("Output: ${record.candidateTokens}", style = MaterialTheme.typography.bodySmall)
                            Text("Total: ${record.totalTokens}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
