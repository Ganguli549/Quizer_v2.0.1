package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: MainViewModel, onHome: () -> Unit) {
    val stats by viewModel.allStats.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    val allQuestions by viewModel.allBookQuestionSummaries.collectAsState()
    
    val bookStats = stats.filter { it.bookId == currentBookId }
    val totalAttempts = bookStats.sumOf { it.attempts }
    val totalCorrect = bookStats.sumOf { it.correct }
    val totalIncorrect = totalAttempts - totalCorrect
    val accuracy = if (totalAttempts > 0) (totalCorrect.toFloat() / totalAttempts) * 100 else 0f
    
    val uniqueQuestionsStudied = bookStats.count { it.attempts > 0 }
    
    // Advanced stats
    val matureCards = bookStats.count { it.interval >= 21 }
    val learningCards = bookStats.count { it.attempts > 0 && it.interval < 21 }
    val newCardsCount = allQuestions.size - bookStats.size
    
    val avgEase = if (bookStats.isNotEmpty()) bookStats.map { it.easeFactor }.average().toFloat() else 2.5f
    val bestStreak = if (bookStats.isNotEmpty()) bookStats.maxOf { it.streak } else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(
            modifier = Modifier
                
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(Modifier.padding(16.dp)) {
                
                // Header section
                val animatedAccuracy by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = accuracy,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "accuracy"
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Overall Accuracy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = trackColor,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 40f, cap = StrokeCap.Round)
                                )
                                val correctSweep = (animatedAccuracy / 100f) * 360f
                                drawArc(
                                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                        colors = listOf(primaryColor.copy(alpha = 0.5f), primaryColor),
                                        center = androidx.compose.ui.geometry.Offset(size.width/2, size.height/2)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = correctSweep,
                                    useCenter = false,
                                    style = Stroke(width = 40f, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${animatedAccuracy.toInt()}%",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Performance Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        title = "Correct",
                        value = "$totalCorrect",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Incorrect",
                        value = "$totalIncorrect",
                        icon = Icons.Default.Cancel,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        title = "Attempts",
                        value = "$totalAttempts",
                        icon = Icons.Default.BarChart,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Studied",
                        value = "$uniqueQuestionsStudied",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text("Advanced Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        AdvancedStatRow("Mature Questions", "$matureCards", "Interval >= 21 days")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        AdvancedStatRow("Learning Questions", "$learningCards", "Attempted, Interval < 21 days")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        AdvancedStatRow("New Questions", "$newCardsCount", "Never attempted")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        AdvancedStatRow("Average Ease Factor", String.format("%.2f", avgEase), "Higher is easier")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        AdvancedStatRow("Best Streak", "$bestStreak", "Consecutive correct answers")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AdvancedStatRow(title: String, value: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
