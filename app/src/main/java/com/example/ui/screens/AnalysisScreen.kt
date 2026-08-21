package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalysisData(
    val dueToday: Int,
    val overdue: Int,
    val mature: Int,
    val learning: Int,
    val newCount: Int,
    val weakest: List<Triple<String, Float, Int>>,
    val examAnalysis: List<Triple<String, Float, Int>>,
    val mostMistaken: List<Pair<com.example.data.QuestionStatEntity, String>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(viewModel: MainViewModel, onHome: () -> Unit, onStartQuiz: () -> Unit) {
    val allQuestions by viewModel.allBookQuestionSummaries.collectAsState()
    val stats by viewModel.allStats.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    
    val analysisData by produceState<AnalysisData?>(initialValue = null, allQuestions, stats, currentBookId) {
        value = withContext(Dispatchers.Default) {
            val bookStats = stats.filter { it.bookId == currentBookId }
            val now = System.currentTimeMillis()
            
            val dueToday = bookStats.count { it.nextReview <= now && it.nextReview > 0 }
            val overdue = bookStats.count { (now - it.nextReview) > 86400000 && it.nextReview > 0 } // > 1 day overdue
            val mature = bookStats.count { it.interval >= 21 }
            val learning = bookStats.count { it.attempts > 0 && it.interval < 21 }
            val newCount = allQuestions.size - bookStats.size
            
            val categories = allQuestions.map { it.category }.distinct()
            val topicAnalysis = categories.mapNotNull { cat ->
                val catQIds = allQuestions.filter { it.category == cat }.map { it.id }.toSet()
                val catStats = bookStats.filter { it.questionId in catQIds }
                val attempts = catStats.sumOf { it.attempts }
                val correct = catStats.sumOf { it.correct }
                if (attempts > 0) {
                    val acc = (correct.toFloat() / attempts) * 100
                    Triple(cat, acc, attempts)
                } else null
            }.sortedByDescending { it.second }
            val weakest = topicAnalysis.sortedBy { it.second }.take(3)
            
            val exams = allQuestions.flatMap { q ->
                val list = mutableListOf<String>()
                if (q.exam.isNotBlank()) list.add(q.exam)
                if (!q.exams.isNullOrBlank()) {
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookExam>>() {}.type
                        val parsed: List<com.example.data.QBookExam>? = com.example.data.SharedGson.normal.fromJson(q.exams, type)
                        parsed?.forEach { if (it.exam.isNotBlank()) list.add(it.exam) }
                    } catch(e: Exception) {}
                }
                list
            }.distinct()
            
            val examAnalysis = exams.mapNotNull { ex ->
                val exQIds = allQuestions.filter { q ->
                    var matches = q.exam == ex
                    if (!matches && !q.exams.isNullOrBlank()) {
                        try {
                            val type = object : com.google.gson.reflect.TypeToken<List<com.example.data.QBookExam>>() {}.type
                            val parsed: List<com.example.data.QBookExam>? = com.example.data.SharedGson.normal.fromJson(q.exams, type)
                            if (parsed?.any { it.exam == ex } == true) matches = true
                        } catch(e: Exception) {}
                    }
                    matches
                }.map { it.id }.toSet()
                val exStats = bookStats.filter { it.questionId in exQIds }
                val attempts = exStats.sumOf { it.attempts }
                val correct = exStats.sumOf { it.correct }
                if (attempts > 0) {
                    val acc = (correct.toFloat() / attempts) * 100
                    Triple(ex, acc, attempts)
                } else null
            }.sortedByDescending { it.second }
            
            val mostMistakenStats = bookStats.filter { it.wrong > 0 }.sortedByDescending { it.wrong }.take(5)
            val mostMistakenQs = viewModel.repository.getQuestionsByBookAndIds(currentBookId, mostMistakenStats.map { it.questionId })
            val mostMistakenMap = mostMistakenQs.associateBy { it.id }
            val mostMistaken = mostMistakenStats.map { Pair(it, mostMistakenMap[it.questionId]?.question ?: "Unknown") }
            
            AnalysisData(dueToday, overdue, mature, learning, newCount, weakest, examAnalysis, mostMistaken)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Center") },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            val data = analysisData
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        Text("Spaced Repetition Stats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatBox("Due Today", data.dueToday, MaterialTheme.colorScheme.primaryContainer)
                            StatBox("Overdue", data.overdue, MaterialTheme.colorScheme.errorContainer)
                            StatBox("Mature", data.mature, MaterialTheme.colorScheme.tertiaryContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatBox("Learning", data.learning, MaterialTheme.colorScheme.secondaryContainer)
                            StatBox("New", data.newCount, MaterialTheme.colorScheme.surfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    
                    item {
                        Text("Targeted Practice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ElevatedCard(onClick = { viewModel.playNavigation(); viewModel.setQuizQuestionsForDailyRevision(); onStartQuiz() }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Daily Revision", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Practice questions due today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ElevatedCard(onClick = { viewModel.playNavigation(); viewModel.setQuizQuestionsForWeakTopics(); onStartQuiz() }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Practice Weak Topics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Focus on areas with low accuracy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ElevatedCard(onClick = { viewModel.playNavigation(); viewModel.setQuizQuestionsForCritical(); onStartQuiz() }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Practice Critical Questions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Revisit most mistaken questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    if (data.weakest.isNotEmpty()) {
                        item {
                            Text("Weakest Sections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        items(items = data.weakest, key = { it.first }) { (topic, acc, attempts) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(topic, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text("Attempts: $attempts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${"%.1f".format(acc)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                    
                    if (data.examAnalysis.isNotEmpty()) {
                        item {
                            Text("Exam Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        items(items = data.examAnalysis, key = { it.first }) { (exam, acc, attempts) ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(exam, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${"%.1f".format(acc)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("$attempts attempts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }

                    if (data.mostMistaken.isNotEmpty()) {
                        item {
                            Text("Most Mistaken Questions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        items(items = data.mostMistaken, key = { it.first.questionKey }) { (stat, questionText) ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Failed ${stat.wrong} times", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    com.example.ui.components.SmartRichText(text = questionText, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
    }
}

@Composable
fun RowScope.StatBox(title: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.weight(1f).padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
