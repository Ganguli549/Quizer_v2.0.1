package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TopicNode
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(viewModel: MainViewModel, onHome: () -> Unit, onStartQuiz: () -> Unit) {
    val topicTreeRoot by viewModel.topicTreeRoot.collectAsState()
    val allQuestions by viewModel.allBookQuestionSummaries.collectAsState()
    val stats by viewModel.allStats.collectAsState()
    val favoriteTopics by viewModel.favoriteTopics.collectAsState()
    
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    var showLearnConfigDialog by remember { mutableStateOf(false) }
    val currentBookId by viewModel.currentBookId.collectAsState()
    
    LaunchedEffect(currentBookId) {
        val saved = viewModel.settingsManager.getExamSettings(currentBookId + "_learn", 0)
        if (saved != null) {
            try {
                viewModel.currentLearnSettings = com.example.data.SharedGson.normal.fromJson(saved, com.example.ui.ExamSettings::class.java)
            } catch(e: Exception) {}
        }
    }

    
    val toggleExpand = { path: String ->
        expandedPaths = if (expandedPaths.contains(path)) expandedPaths - path else expandedPaths + path
    }

    if (showLearnConfigDialog) {
        var limit by remember { mutableStateOf(viewModel.settingsManager.learnQuestionLimit) }
        val flow by remember { mutableStateOf(viewModel.settingsManager.learnFlowMode) } // Permanent progressive
        
        var shuffleQ by remember { mutableStateOf(viewModel.settingsManager.learnShuffleQuestions) }
        var shuffleOpts by remember { mutableStateOf(viewModel.settingsManager.learnShuffleOptions) }
        var useDistractors by remember { mutableStateOf(viewModel.settingsManager.learnUseDistractors) }
        var questionFlow by remember { mutableStateOf(viewModel.settingsManager.learnQuestionFlow) }
        
        AlertDialog(
            onDismissRequest = { showLearnConfigDialog = false },
            title = { Text("Learn Settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Question Count", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = limit == 10, onClick = { limit = 10 }, label = { Text("10") })
                        FilterChip(selected = limit == 25, onClick = { limit = 25 }, label = { Text("25") })
                        FilterChip(selected = limit == 50, onClick = { limit = 50 }, label = { Text("50") })
                        FilterChip(selected = limit == -1, onClick = { limit = -1 }, label = { Text("All") })
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Advanced Quiz Options", style = MaterialTheme.typography.labelLarge)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = shuffleQ,
                            onClick = { shuffleQ = !shuffleQ },
                            label = { Text("Shuffle Questions") }
                        )
                        FilterChip(
                            selected = shuffleOpts,
                            onClick = { shuffleOpts = !shuffleOpts },
                            label = { Text("Shuffle Options") }
                        )
                        FilterChip(
                            selected = useDistractors,
                            onClick = { useDistractors = !useDistractors },
                            label = { Text("Dynamic Distractors") }
                        )
                        FilterChip(
                            selected = questionFlow,
                            onClick = { questionFlow = !questionFlow },
                            label = { Text("Question Flow") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scope selection automatically adjusts when you play a topic from the roadmap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.settingsManager.learnQuestionLimit = limit
                    // permanently progressive
                    viewModel.settingsManager.learnFlowMode = "progressive"
                    
                    viewModel.settingsManager.learnShuffleQuestions = shuffleQ
                    viewModel.settingsManager.learnShuffleOptions = shuffleOpts
                    viewModel.settingsManager.learnUseDistractors = useDistractors
                    viewModel.settingsManager.learnQuestionFlow = questionFlow
                    
                    showLearnConfigDialog = false
                }) {
                    Text("Save")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learn Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLearnConfigDialog = true }) {
                        Icon(Icons.Default.Settings, "Learn Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(
            modifier = Modifier
                
                .fillMaxSize()
        ) {
            Text(
                text = "Roadmap & Topics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            if (topicTreeRoot == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val flatList = buildFlatList(topicTreeRoot!!.children.values.toList(), 0, expandedPaths)
                if (flatList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No topics available in this book.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(flatList, key = { it.node.path }) { item ->
                            TopicNodeCard(
                                node = item.node,
                                level = item.level,
                                isExpanded = expandedPaths.contains(item.node.path),
                                onToggleExpand = { toggleExpand(item.node.path) },
                                onPlayTopic = {
                                    viewModel.selectedCategories.value.forEach { c -> viewModel.toggleCategory(c) }
                                    viewModel.setQuizQuestionsByTopic(item.node)
                                    viewModel.currentExamSettings = viewModel.currentLearnSettings
                                    onStartQuiz()
                                },
                                isFavorite = favoriteTopics.contains(item.node.path),
                                onToggleFavorite = { viewModel.toggleFavoriteTopic(item.node.path) },
                                isCompleted = item.node.masteryLevel == "Mastered"
                            )
                        }
                    }
                }
            }
        }
    
        }
    }
}

@Composable
fun TopicNodeCard(
    node: TopicNode,
    level: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayTopic: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isCompleted: Boolean
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val masteryColor = when (node.masteryLevel) {
        "New" -> Color(0xFF9E9E9E)
        "Started" -> Color(0xFFBA68C8)
        "Learning" -> Color(0xFFE53935)
        "Developing" -> Color(0xFFFFB300)
        "Learnt" -> Color(0xFF8BC34A)
        "Strong" -> Color(0xFF43A047)
        "Mastered" -> Color(0xFF1E88E5)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (node.children.isNotEmpty()) onToggleExpand() else onPlayTopic() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (node.children.isNotEmpty()) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(24.dp).padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowBack, // using arrow back and rotating or just KeyboardArrowRight
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.background(Color.Transparent).then(
                                if (isExpanded) Modifier else Modifier.rotate(180f)
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }
                
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Favorite
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp).minimumInteractiveComponentSize()) {
                    Icon(
                        imageVector = if (isFavorite) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mastery Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = masteryColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = node.masteryLevel,
                        style = MaterialTheme.typography.labelSmall,
                        color = masteryColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Play Button
                IconButton(onClick = onPlayTopic, modifier = Modifier.size(32.dp).minimumInteractiveComponentSize()) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                        contentDescription = "Start Topic",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress Bar
            val pctExplored = if (node.questionCount > 0) (node.attemptedCount.toFloat() / node.questionCount.toFloat()) else 0f
            
            LinearProgressIndicator(
                progress = { pctExplored },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${(pctExplored * 100).toInt()}% Explored",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${node.correctCount} / ${node.questionCount} Correct",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MasteryBar(label: String, count: Int, total: Int, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(label)
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) count.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun borderStroke() = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)

data class FlatTopic(val node: TopicNode, val level: Int)

fun buildFlatList(nodes: List<TopicNode>, level: Int, expandedPaths: Set<String>): List<FlatTopic> {
    val list = mutableListOf<FlatTopic>()
    for (node in nodes) {
        list.add(FlatTopic(node, level))
        if (expandedPaths.contains(node.path)) {
            list.addAll(buildFlatList(node.children.values.toList(), level + 1, expandedPaths))
        }
    }
    return list
}
