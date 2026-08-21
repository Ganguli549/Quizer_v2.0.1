package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel, onHome: () -> Unit, onStartQuiz: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    
    LaunchedEffect(query) {
        viewModel.searchQuestions(query)
    }

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    val allSelected = selectedIds.size == searchResults.size && searchResults.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content Explorer") },
                navigationIcon = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.setQuizQuestionsByIds(viewModel.currentBookId.value, selectedIds.toList())
                        onStartQuiz()
                    },
                    icon = { Icon(Icons.Default.PlayArrow, "Practice") },
                    text = { Text("Practice Selected (${selectedIds.size})") }
                )
            }
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search word, category, exam...") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${searchResults.size} question(s) found", style = MaterialTheme.typography.bodySmall)
                if (searchResults.isNotEmpty()) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Select All", style = MaterialTheme.typography.bodySmall)
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = {
                                selectedIds = if (it) searchResults.map { q -> q.id }.toSet() else emptySet()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                val grouped = mutableListOf<Pair<String?, List<com.example.data.QuestionEntity>>>()
                var currentHeader: String? = null
                var currentGroup = mutableListOf<com.example.data.QuestionEntity>()
                
                for (q in searchResults) {
                    val h = q.questionHeader
                    val isPassage = !h.isNullOrBlank() && Regex("<p(?:\\s+name=\"[^\"]*\")?\\s*>").containsMatchIn(h)
                    if (isPassage) {
                        if (h == currentHeader) {
                            currentGroup.add(q)
                        } else {
                            if (currentGroup.isNotEmpty()) {
                                grouped.add(Pair(currentHeader, currentGroup))
                            }
                            currentHeader = h
                            currentGroup = mutableListOf(q)
                        }
                    } else {
                        if (currentGroup.isNotEmpty()) {
                            grouped.add(Pair(currentHeader, currentGroup))
                            currentGroup = mutableListOf()
                        }
                        currentHeader = null
                        grouped.add(Pair(null, listOf(q)))
                    }
                }
                if (currentGroup.isNotEmpty()) {
                    grouped.add(Pair(currentHeader, currentGroup))
                }
                
                grouped.forEach { (header, qs) ->
                    if (header != null && qs.size > 1) {
                        item(key = "group_${qs.first().globalId}") {
                            SearchPassageGroupCard(qs, query, header, selectedIds = selectedIds) { id, checked ->
                                selectedIds = if (checked) selectedIds + id else selectedIds - id
                            }
                        }
                    } else {
                        items(qs, key = { it.globalId }) { q ->
                            SearchQuestionCard(q, query, isSelected = selectedIds.contains(q.id)) { id, checked ->
                                selectedIds = if (checked) selectedIds + id else selectedIds - id
                            }
                        }
                    }
                }
            }
        }
       
        }
    }
}

@Composable
fun SearchPassageGroupCard(qs: List<com.example.data.QuestionEntity>, query: String, header: String, selectedIds: Set<Long>, onCheckedChange: (Long, Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            val terms = remember(query) { query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
            val cleanHeader = remember(header) {
                val match = Regex("<p(?:\\s+name=\"([^\"]+)\")?\\s*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(header)
                if (match != null) {
                    val title = match.groupValues[1].takeIf { it.isNotBlank() } ?: "Passage"
                    title + " " + match.groupValues[2]
                } else {
                    header
                }
            }
            val containsQuery = remember(cleanHeader, terms) { terms.isNotEmpty() && terms.any { cleanHeader.contains(it, ignoreCase = true) } }
            
            com.example.ui.components.ExpandableQuestionHeader(
                headerText = header,
                textColor = MaterialTheme.colorScheme.onSurface,
                isQuizMode = true,
                initiallyExpanded = containsQuery,
                searchQuery = query,
                highlightColor = androidx.compose.ui.graphics.Color.Yellow,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            qs.forEachIndexed { index, q ->
                SearchQuestionContent(q = q, query = query, isSelected = selectedIds.contains(q.id), onCheckedChange = { id, checked -> onCheckedChange(id, checked) })
                if (index < qs.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun SearchQuestionCard(q: com.example.data.QuestionEntity, query: String, isSelected: Boolean, onCheckedChange: (Long, Boolean) -> Unit) {
    val terms = remember(query) { query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val matchInBody = remember(q, terms) {
        terms.isNotEmpty() && terms.any { term ->
            q.options.contains(term, ignoreCase = true) ||
            q.explanation.contains(term, ignoreCase = true) ||
            (q.detailedExplanation?.contains(term, ignoreCase = true) == true) ||
            (q.explanationTable?.contains(term, ignoreCase = true) == true)
        }
    }
    var expanded by remember(matchInBody) { mutableStateOf(matchInBody) }
    
    val header = q.questionHeader
    
    Column {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }.animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (!header.isNullOrBlank()) {
            val cleanHeader = remember(header) {
                val match = Regex("<p(?:\\s+name=\"([^\"]+)\")?\\s*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(header)
                if (match != null) {
                    val title = match.groupValues[1].takeIf { it.isNotBlank() } ?: "Passage"
                    title + " " + match.groupValues[2]
                } else {
                    header
                }
            }
            val containsQuery = remember(cleanHeader, terms) { terms.isNotEmpty() && terms.any { cleanHeader.contains(it, ignoreCase = true) } }
                    
                    com.example.ui.components.ExpandableQuestionHeader(
                        headerText = header,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        isQuizMode = true,
                        initiallyExpanded = containsQuery,
                        searchQuery = query,
                        highlightColor = androidx.compose.ui.graphics.Color.Yellow,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                
                SearchQuestionContent(q, query, isSelected, onCheckedChange, expanded) { expanded = !expanded }
            }
        }
    }
}

@Composable
fun SearchQuestionContent(q: com.example.data.QuestionEntity, query: String, isSelected: Boolean, onCheckedChange: (Long, Boolean) -> Unit, isExpanded: Boolean? = null, onExpandedToggle: (() -> Unit)? = null) {
    val terms = remember(query) { query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val matchInBody = remember(q, terms) {
        terms.isNotEmpty() && terms.any { term ->
            q.options.contains(term, ignoreCase = true) ||
            q.explanation.contains(term, ignoreCase = true) ||
            (q.detailedExplanation?.contains(term, ignoreCase = true) == true) ||
            (q.explanationTable?.contains(term, ignoreCase = true) == true)
        }
    }
    var internalExpanded by remember(matchInBody) { mutableStateOf(matchInBody) }
    val expanded = isExpanded ?: internalExpanded
    val toggle = onExpandedToggle ?: { internalExpanded = !internalExpanded }
    
    Column(modifier = Modifier.fillMaxWidth().clickable { toggle() }) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                com.example.ui.components.SmartRichText(text = q.question, color = MaterialTheme.colorScheme.onSurface, searchQuery = query, isSelectable = expanded, onClick = toggle)
                
                if (q.format == "table" && q.tableData != null) {
                    val tableData = androidx.compose.runtime.remember(q.id) {
                    try {
                        val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                        com.example.data.SharedGson.normal.fromJson<List<List<String>>>(q.tableData, tableType)
                    } catch (e: Exception) {
                        null
                    }
                }
                    if (tableData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.ui.components.TableRenderer(tableData, searchQuery = query)
                    }
                }
                
                if (!q.bottomText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.ui.components.SmartRichText(text = q.bottomText, color = MaterialTheme.colorScheme.onSurface, searchQuery = query, isSelectable = expanded, onClick = toggle)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${q.category} | ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.example.ui.components.ExamTagsRow(
                        examsJson = q.exams,
                        legacyExam = q.exam,
                        legacyYear = q.year,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onCheckedChange(q.id, it) }
            )
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            val options = androidx.compose.runtime.remember(q.id) {
                    try {
                        com.example.data.SharedGson.normal.fromJson(q.options, Array<String>::class.java).toList()
                    } catch(e: Exception) { emptyList() }
                }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            options.forEachIndexed { idx, opt ->
                val isCorrect = idx == q.correctIndex
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${idx + 1}. ", fontWeight = if (isCorrect) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal, color = if (isCorrect) androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface)
                    com.example.ui.components.SmartRichText(text = opt, color = if (isCorrect) androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface, searchQuery = query, isSelectable = expanded, onClick = toggle)
                }
            }
            if (q.explanation.isNotBlank() || !q.detailedExplanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (q.explanation.isNotBlank()) {
                    Text("Explanation:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    com.example.ui.components.SmartRichText(text = q.explanation, color = MaterialTheme.colorScheme.onSurface, searchQuery = query, isSelectable = expanded, onClick = toggle)
                }
                if (!q.detailedExplanation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Detailed:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    com.example.ui.components.SmartRichText(text = q.detailedExplanation, color = MaterialTheme.colorScheme.onSurface, searchQuery = query, isSelectable = expanded, onClick = toggle)
                }
                if (!q.explanationTable.isNullOrBlank()) {
                    val expTableData = androidx.compose.runtime.remember(q.id) {
                        try {
                            val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                            com.example.data.SharedGson.normal.fromJson<List<List<String>>>(q.explanationTable, tableType)
                        } catch (e: Exception) { null }
                    }
                    if (expTableData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.ui.components.TableRenderer(expTableData, MaterialTheme.colorScheme.onSurface, searchQuery = query)
                    }
                }
            }
        }
    }
}
