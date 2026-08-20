package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(viewModel: MainViewModel, onHome: () -> Unit, onReview: () -> Unit, onPracticeWrong: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val lastResult = history.firstOrNull() // since we insert and order by DESC
    
    val isPracticeMistakes = lastResult?.status?.startsWith("practice_mistakes_for_") == true
    val originalResultId = if (isPracticeMistakes) lastResult?.status?.removePrefix("practice_mistakes_for_")?.toLongOrNull() else lastResult?.historyId
    val originalResult = if (isPracticeMistakes) history.find { it.historyId == originalResultId } else lastResult
    val practiceMistakesResult = if (isPracticeMistakes) lastResult else history.find { it.status == "practice_mistakes_for_${lastResult?.historyId}" }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Quiz Result", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (originalResult != null) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(originalResult.quizLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    ResultCard(viewModel, originalResult)
                    
                    if (practiceMistakesResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Practice Mistakes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        ResultCard(viewModel, practiceMistakesResult)
                    }
                }
            } else {
                Text("Loading results...", modifier = Modifier.weight(1f))
            }
                        
            Spacer(modifier = Modifier.height(8.dp))
                        
            if (originalResult != null && practiceMistakesResult == null && (originalResult.wrong > 0 || originalResult.skipped > 0)) {
                Button(onClick = {
                    viewModel.practiceWrongQuiz(originalResult.historyId)
                    onPracticeWrong()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Practice Mistakes (${originalResult.wrong + originalResult.skipped})")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
                        
            Button(onClick = {
                viewModel.selectResultForReview(lastResult?.historyId)
                onReview()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Review Questions")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                Text("Return Home")
            }
        }
            
        }
    }
}

@Composable
fun ResultCard(viewModel: MainViewModel, result: com.example.data.QuizHistoryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                
            // Top Highlight Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${result.finalScore}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Accuracy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.1f".format(result.accuracy)}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val mins = result.timeUsed / 60000
                    val secs = (result.timeUsed % 60000) / 1000
                    Text("${String.format("%02d:%02d", mins, secs)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
            }
                                
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                
            // Detailed Breakdown
            val correctMarks = result.correct * viewModel.settingsManager.correctMark
            val wrongMarks = result.wrong * viewModel.settingsManager.wrongMark
                                
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Correct (${result.correct})", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Text("+${"%.2f".format(correctMarks)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Wrong (${result.wrong})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                Text("-${"%.2f".format(wrongMarks)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Skipped (${result.skipped})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
