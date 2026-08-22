package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onHome: () -> Unit, onReview: () -> Unit, onStartQuiz: () -> Unit = {}) {
    val history by viewModel.history.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    val hasActiveQuiz by viewModel.hasActiveQuiz.collectAsState()
    val allBooks by viewModel.books.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Quiz History") })
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (hasActiveQuiz) {
                if (hasActiveQuiz) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            var progressText = "Unknown progress"
                            var bookLoadedId = ""
                            try {
                                val stateStr = viewModel.settingsManager.getActiveQuizState(currentBookId)
                                if (stateStr != null) {
                                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                    val state: Map<String, Any> = com.example.data.SharedGson.normal.fromJson(stateStr, type)
                                    val qIdsRaw = (state["questions"] as? List<*>) ?: emptyList<Any>()
                                    val currentIndex = (state["currentIndex"] as? Number)?.toInt() ?: 0
                                    bookLoadedId = state["bookId"] as? String ?: ""
                                    progressText = "${currentIndex} / ${qIdsRaw.size} answered"
                                }
                            } catch (e: Exception) {}
                            
                            val bookName = allBooks.find { it.id == bookLoadedId }?.name ?: "Saved Quiz"
                            
                            Text("You have a saved quiz in progress: $bookName", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("Progress: $progressText", style = MaterialTheme.typography.bodyMedium)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                                Button(onClick = {
                                    coroutineScope.launch {
                                        if (viewModel.loadActiveQuizState()) {
                                            onStartQuiz()
                                        }
                                    }
                                }) {
                                    Text("Resume")
                                }
                                OutlinedButton(onClick = {
                                    viewModel.clearActiveQuizState()
                                    
                                }) {
                                    Text("Discard")
                                }
                            }
                        }
                    }
                }
            }
            
            val displayHistory = history.filter { !it.status.startsWith("practice_mistakes") }
            
            if (displayHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No quiz history yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(displayHistory, key = { it.historyId }) { entry ->
                        val bName = allBooks.find { it.id == entry.bookId }?.name ?: "Unknown Book"
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                            onClick = {
                                viewModel.selectResultForReview(entry.historyId)
                                onReview()
                            }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.quizLabel, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(bName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(entry.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        CircularProgressIndicator(
                                            progress = { (entry.accuracy / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.size(48.dp),
                                            color = if (entry.accuracy >= 80f) androidx.compose.ui.graphics.Color(0xFF4CAF50) else if (entry.accuracy >= 50f) androidx.compose.ui.graphics.Color(0xFFFF9800) else MaterialTheme.colorScheme.error,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text(
                                            "${entry.correct}/${entry.total}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                Text("Home")
            }
        }
    
        }
    }
}
