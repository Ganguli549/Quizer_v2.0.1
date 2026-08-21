package com.example.ui.components
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import coil.compose.AsyncImage
import com.example.data.GeminiHelper
import com.example.data.QuestionEntity
import com.example.data.ChatMessageEntity
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity


enum class AiChatMode {
    GLOBAL_EDITOR,
    SINGLE_EDITOR,
    READ_BOOK
}

data class PendingQuestionUpdate(
    val originalId: Long,
    val updatedQuestion: QuestionEntity,
    val imageOptions: List<String> = emptyList(),
    val isDelete: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    mode: AiChatMode,
    questions: List<QuestionEntity>,
    currentBookId: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onQuestionsUpdated: (List<QuestionEntity>) -> Unit,
    onQuestionsDeleted: (List<QuestionEntity>) -> Unit = {},
    currentContextInfo: String = "",
    visibleQuestionNumbers: List<Int> = emptyList(),
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {}
) {
    val chatContextId = remember(currentBookId, mode) {
        when (mode) {
            AiChatMode.READ_BOOK -> "${currentBookId}_read"
            AiChatMode.GLOBAL_EDITOR, AiChatMode.SINGLE_EDITOR -> "${currentBookId}_edit"
        }
    }
    val promptMap by viewModel.aiPromptTexts.collectAsState()
    val prompt = promptMap[chatContextId] ?: ""

    val allPendingUpdates by viewModel.pendingUpdates.collectAsState()
    val aiProcessingStatus by viewModel.aiProcessingStatus.collectAsState()
    val aiProcessingProgressMap by viewModel.aiProcessingProgress.collectAsState()
    val isProcessing = aiProcessingStatus[chatContextId] != null
    var pendingUpdates = allPendingUpdates[chatContextId]
    var editingPendingQuestion by remember { mutableStateOf<PendingQuestionUpdate?>(null) }
    var showDevLogs by remember { mutableStateOf(false) }
    val devLogs by viewModel.devLogs.collectAsState()
    
    val chatHistory by viewModel.chatHistory.collectAsState()
    val listState = rememberLazyListState()
    
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(currentBookId) {
        viewModel.loadChatHistory(chatContextId)
    }

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    
    val sessionAttachments by viewModel.sessionAttachments.collectAsState()
    val attachedPdfUri = sessionAttachments[chatContextId]
    
    val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setSessionAttachment(chatContextId, uri)
        }
    }

    val logExportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(devLogs.joinToString("\n\n").toByteArray())
                }
                android.widget.Toast.makeText(context, "Logs saved successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to save logs", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    
    val isolateScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                return available
            }
            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                return available
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .imePadding()
                .padding(bottom = 16.dp)
                .fillMaxHeight(0.9f)
                .nestedScroll(connection = isolateScrollConnection, dispatcher = null)
                .draggable(androidx.compose.foundation.gestures.rememberDraggableState { }, orientation = androidx.compose.foundation.gestures.Orientation.Vertical)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Sparkle AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More Options", modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = { 
                                coroutineScope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            }, 
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("AI Settings") },
                            onClick = { 
                                menuExpanded = false
                                onDismiss()
                                onNavigateToAiSettings() 
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, "Settings") }
                        )
                        DropdownMenuItem(
                            text = { Text("API Usages") },
                            onClick = { 
                                menuExpanded = false
                                onDismiss()
                                onNavigateToUsage() 
                            },
                            leadingIcon = { Icon(Icons.Default.Info, "API Usages") }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat") },
                            onClick = { 
                                menuExpanded = false
                                viewModel.clearChatHistory(chatContextId) 
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, "Clear Chat") }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showDevLogs) "Hide Dev Logs" else "Show Dev Logs") },
                            onClick = {
                                menuExpanded = false
                                showDevLogs = !showDevLogs
                            },
                            leadingIcon = { Icon(Icons.Default.BugReport, "Developer Logs") }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            

            
            // Chat history area
            if (showDevLogs) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Developer Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = { logExportLauncher.launch("sparkle_logs.txt") }) {
                                Text("Download")
                            }
                            TextButton(onClick = { viewModel.clearDevLogs() }) {
                                Text("Clear")
                            }
                        }
                    }
                    val devListState = rememberLazyListState()
                    LaunchedEffect(devLogs.size) {
                        if (devLogs.isNotEmpty()) {
                            devListState.animateScrollToItem(devLogs.size - 1)
                        }
                    }
                    LazyColumn(
                        state = devListState, 
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items(devLogs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp).fillMaxWidth()
                            )
                        }
                        if (devLogs.isEmpty()) {
                            item { Text("No logs yet.", modifier = Modifier.padding(16.dp)) }
                        }
                    }
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        Text(
                            text = if (mode == AiChatMode.READ_BOOK) "Say hi or ask questions." else "Say hi, ask questions, or request edits (e.g., 'Add distractors to question 1').",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                items(chatHistory) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {

                        
                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .widthIn(max = configuration.screenWidthDp.dp * 0.85f)
                        ) {
                            ChatMessageContent(content = msg.content, isUser = isUser)
                        }
                    }
                }
                
                if (isProcessing && pendingUpdates == null) {
                    val statusText = aiProcessingStatus[chatContextId] ?: "Thinking..."
                    val progressVal = aiProcessingProgressMap[chatContextId] ?: 0f
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .animateContentSize()
                                ) {
                                    Column(modifier = Modifier.widthIn(min = 120.dp, max = 320.dp)) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (progressVal == 0f) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }
                                            Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        if (progressVal > 0f) {
                                            LinearProgressIndicator(
                                                progress = { progressVal },
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
            }
            
            } // close else block
            
            // Pending updates UI
            if (pendingUpdates != null) {
                Text("Review & Confirm Updates", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    val currentUpdates = pendingUpdates ?: emptyList()
                    items(currentUpdates) { pending ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    if (pending.isDelete) {
                                        Text("Delete Q${pending.updatedQuestion.id}: ${pending.updatedQuestion.question.take(60)}...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                    } else {
                                        Text("Q${pending.updatedQuestion.id}: ${pending.updatedQuestion.question.take(60)}...", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { editingPendingQuestion = pending }) {
                                            Text("Review")
                                        }
                                    }
                                }
                                if (pending.imageOptions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Select an image:", style = MaterialTheme.typography.labelMedium)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(pending.imageOptions) { rawImgUrl ->
                                            var imgUrl = rawImgUrl
                                            if (imgUrl.startsWith("https://image.pollinations.ai/prompt/")) {
                                                val prefix = "https://image.pollinations.ai/prompt/"
                                                val rest = imgUrl.substring(prefix.length)
                                                val parts = rest.split("?", limit = 2)
                                                val promptPart = parts[0]
                                                val query = if (parts.size > 1) "?" + parts[1] else ""
                                                val decoded = try { java.net.URLDecoder.decode(promptPart, "UTF-8") } catch(e: Exception) { promptPart }
                                                val encoded = java.net.URLEncoder.encode(decoded, "UTF-8").replace("+", "%20")
                                                imgUrl = prefix + encoded + query
                                            }
                                            val isSelected = pending.updatedQuestion.explanationImage == imgUrl || pending.updatedQuestion.explanationImage == rawImgUrl
                                            Box(modifier = Modifier.size(100.dp)) {
                                                coil.compose.SubcomposeAsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = "Generated Option",
                                                    loading = {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                        }
                                                    },
                                                    error = {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                                                        .clickable {
                                                            pendingUpdates?.let { currentList ->
                                                                val updatedList = currentList.toMutableList()
                                                                val idx = updatedList.indexOfFirst { it.updatedQuestion.id == pending.updatedQuestion.id }
                                                                if (idx != -1) {
                                                                    updatedList[idx] = pending.copy(updatedQuestion = pending.updatedQuestion.copy(explanationImage = imgUrl))
                                                                    viewModel.setPendingUpdates(chatContextId, updatedList)
                                                                }
                                                            }
                                                        }
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.setPendingUpdates(chatContextId, null) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val currentUpdates = pendingUpdates?.toList() ?: emptyList()
                            viewModel.setPendingUpdates(chatContextId, null)
                            viewModel.setAiProcessingStatus(chatContextId, "Saving updates...")
                            coroutineScope.launch {
                                val updateList = mutableListOf<QuestionEntity>()
                                val deleteList = mutableListOf<QuestionEntity>()
                                currentUpdates.forEach { pending ->
                                    if (pending.isDelete) {
                                        deleteList.add(pending.updatedQuestion)
                                    } else {
                                        var updated = pending.updatedQuestion
                                        if (updated.explanationImage?.startsWith("http") == true) {
                                            val downloaded = com.example.data.ImageDownloader.downloadImage(context, currentBookId, updated.explanationImage!!)
                                            if (downloaded != null) {
                                                updated = updated.copy(explanationImage = downloaded)
                                            }
                                        }
                                        if (updated.img?.startsWith("http") == true) {
                                            val downloaded = com.example.data.ImageDownloader.downloadImage(context, currentBookId, updated.img)
                                            if (downloaded != null) {
                                                updated = updated.copy(img = downloaded)
                                            }
                                        }
                                        updateList.add(updated)
                                    }
                                }
                                if (updateList.isNotEmpty()) {
                                    onQuestionsUpdated(updateList)
                                }
                                if (deleteList.isNotEmpty()) {
                                    onQuestionsDeleted(deleteList)
                                }
                                viewModel.addChatMessage(
                                    ChatMessageEntity(bookId = chatContextId, role = "model", content = "Applied ${updateList.size} updates and deleted ${deleteList.size} questions.", timestamp = System.currentTimeMillis())
                                )
                                viewModel.setAiProcessingStatus(chatContextId, null)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save Updates") }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Suggestions
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = if (mode == AiChatMode.READ_BOOK) {
                    if (visibleQuestionNumbers.isNotEmpty()) {
                        val list = mutableListOf<String>()
                        for (num in visibleQuestionNumbers.take(3)) {
                            list.add("Explain question $num")
                        }
                        if (list.isEmpty()) {
                            list.add("Explain this in detail")
                        }
                        list
                    } else {
                        listOf("Explain this in detail", "Give an example")
                    }
                } else {
                    listOf("Add distractors to question 1", "Fix typos", "Make it harder", "Generate image for question")
                }
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { viewModel.setAiPromptText(chatContextId, suggestion) },
                        label = { Text(suggestion) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            

            if (attachedPdfUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_agenda), 
                            contentDescription = "PDF Document", 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Document Attached", 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                                .clickable { viewModel.setSessionAttachment(chatContextId, null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close, 
                                contentDescription = "Remove File", 
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, 
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            // Input Area
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { viewModel.setAiPromptText(chatContextId, it) },
                    placeholder = { Text("Ask Sparkle...") },
                    leadingIcon = {
                        IconButton(
                            onClick = { pdfLauncher.launch("*/*") },
                            enabled = !isProcessing,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Add, "Upload PDF")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    maxLines = 10,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isProcessing) {
                    IconButton(
                        onClick = {
                            viewModel.cancelAiProcessing(chatContextId)
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Close, "Stop")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (prompt.isNotBlank()) {
                                val currentPrompt = prompt
                                viewModel.setAiPromptText(chatContextId, "")
                                viewModel.startAiProcessing(
                                    context = context.applicationContext,
                                    chatContextId = chatContextId,
                                    prompt = currentPrompt,
                                    allQuestions = questions,
                                    mode = mode,
                                    currentContextInfo = currentContextInfo,
                                    pdfUri = attachedPdfUri
                                )
                                // We no longer clear the attachment automatically so it persists for subsequent queries!
                            }
                        },
                        enabled = !isProcessing && prompt.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    }

    if (editingPendingQuestion != null) {
        com.example.ui.screens.EditQuestionDialog(viewModel = viewModel, bookId = currentBookId, isReviewMode = true,
            question = editingPendingQuestion!!.updatedQuestion,
            onDismiss = { editingPendingQuestion = null },
            onSave = { savedQ, _, _ ->
                pendingUpdates?.let { currentList ->
                    val updatedList = currentList.toMutableList()
                    val idx = updatedList.indexOfFirst { it.updatedQuestion.id == savedQ.id }
                    if (idx != -1) {
                        updatedList[idx] = updatedList[idx].copy(updatedQuestion = savedQ)
                        viewModel.setPendingUpdates(chatContextId, updatedList)
                    }
                }
                editingPendingQuestion = null
            }
        )
    }
}

suspend fun processAiChatCommand(
    context: android.content.Context,
    command: String, 
    allQuestions: List<QuestionEntity>,
    mode: AiChatMode,
    chatHistory: List<ChatMessageEntity>,
    currentContextInfo: String = "",
    pdfBytes: ByteArray? = null,
    extractedText: String? = null,
    attachments: List<com.example.data.AiFileAttachment> = emptyList(),
    onStatusUpdate: (String, Float) -> Unit = {_,_ ->},
    onDevLog: (String) -> Unit = {}
): Pair<List<PendingQuestionUpdate>, String> {
    val gson = com.example.data.SharedGson.normal
    
    onStatusUpdate("Analyzing request...", 0f)
    val intentPrompt = """
        User command: "$command"
        Mode: $mode
        Current context info: $currentContextInfo
        
        Determine if the user wants to CHAT, EDIT, EXTRACT_QUESTIONS, BATCH_EDIT, or REMOVE.
        - CHAT: General conversation, answering ANY general knowledge or coding questions, asking for explanations, generating images, or requesting examples. (e.g. "provide an image", "explain this", "hi", "what is this?", "who is the pm of india?", "write a python script")
        - EDIT: Explicitly modifying the book/questions (e.g. "add distractors", "fix typos", "format table", "add an image to this question").
        - EXTRACT_QUESTIONS: User attached a document/image and wants to extract questions into the book.
        - BATCH_EDIT: Changing a specific field (e.g., questionHeader, path, category, format) to a specific value for a range of questions, without needing to analyze the text of the questions. (e.g., "change question header to 'Choose OWS' for question 5 to 25", "change path to 'Maths' for 1 to 25").
        - REMOVE: Explicitly asking to delete or remove questions. (e.g., "remove question 5", "delete questions 10 to 20").
        
        CRITICAL RULE: If the mode is READ_BOOK, ONLY return "edit", "batch_edit", or "remove" if the user uses explicit modification verbs. Default to "chat" unless editing is undeniable.
        
        Note: The app supports tabular questions natively. The question object can have 'format'="table" and 'tableData'=JSON string array of string arrays.
        Extract the following information and return ONLY a JSON object:
        {
          "intent": "chat", "edit", "extract_questions", "batch_edit", or "remove",
          "startId": <integer or null (if no specific start)>,
          "endId": <integer or null (if no specific end)>,
          "action": "<summary of action>",
          "isExpansion": <boolean, true if they want to expand 1 question into multiple questions>,
          "requiresQuestions": <boolean, true ONLY if the command needs access to the question text/options (e.g. asking for explanation of a question), false for general chat like 'hi'>,
          "requiredLocalNumbers": <array of integers, e.g. [5], list exact local question numbers mentioned>,
          "batchActions": [ <ONLY IF intent IS batch_edit, an array of objects like {"field": "questionHeader", "value": "Choose OWS", "startId": 5, "endId": 25}> ]
        }
        If the user doesn't specify a range, return null for startId and endId to target all questions.
        Do not return markdown, just the JSON.
    """.trimIndent()
    
    onDevLog("[INTENT PARSER PROMPT]\n" + intentPrompt)
    val parseRes = GeminiHelper.generateContent(context, intentPrompt, onDevLog = onDevLog)
    onDevLog("[INTENT PARSER RESPONSE]\n" + parseRes)
    if (parseRes.startsWith("Error:")) {
        return Pair(emptyList(), parseRes)
    }
    
    var intent = "chat"
    var startId = 1
    var endId = 1
    var isExpansion = false
    var actionText = "Processing"
    var requiresQuestions = true
    var requiredLocalNumbers = emptyList<Int>()
    
    try {
        var cleanJson = parseRes.replace("```json", "").replace("```", "").trim()
        val jsonStart = cleanJson.indexOf('{')
        val jsonEnd = cleanJson.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd >= jsonStart) {
            cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1)
        }
        val parsed = Json.parseToJsonElement(cleanJson).jsonObject
        intent = parsed["intent"]?.jsonPrimitive?.content ?: "chat"
        requiresQuestions = parsed["requiresQuestions"]?.jsonPrimitive?.booleanOrNull ?: true
        
        requiredLocalNumbers = parsed["requiredLocalNumbers"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
        val maxPossibleId = allQuestions.maxOfOrNull { it.id.toInt() } ?: 1
        val minPossibleId = allQuestions.minOfOrNull { it.id.toInt() } ?: 1
        
        startId = parsed["startId"]?.jsonPrimitive?.intOrNull ?: minPossibleId
        endId = parsed["endId"]?.jsonPrimitive?.intOrNull ?: maxPossibleId
        isExpansion = parsed["isExpansion"]?.jsonPrimitive?.booleanOrNull ?: false
        actionText = parsed["action"]?.jsonPrimitive?.content ?: "Processing"
        
        if (intent == "batch_edit") {
            val batchActions = parsed["batchActions"]?.jsonArray
            if (batchActions != null) {
                val updates = mutableListOf<PendingQuestionUpdate>()
                for (action in batchActions) {
                    val actObj = action.jsonObject
                    val field = actObj["field"]?.jsonPrimitive?.content ?: continue
                    val value = actObj["value"]?.jsonPrimitive?.content ?: ""
                    val actStart = actObj["startId"]?.jsonPrimitive?.intOrNull ?: minPossibleId
                    val actEnd = actObj["endId"]?.jsonPrimitive?.intOrNull ?: maxPossibleId
                    
                    val targeted = allQuestions.filter { it.id in actStart..actEnd }
                    for (q in targeted) {
                        val existingIdx = updates.indexOfFirst { it.originalId == q.id }
                        val baseQ = if (existingIdx != -1) updates[existingIdx].updatedQuestion else q
                        val modifiedQ = when (field) {
                            "questionHeader" -> baseQ.copy(questionHeader = value)
                            "path" -> baseQ.copy(path = value)
                            "category" -> baseQ.copy(category = value)
                            "format" -> baseQ.copy(format = value)
                            else -> baseQ
                        }
                        if (existingIdx != -1) {
                            updates[existingIdx] = updates[existingIdx].copy(updatedQuestion = modifiedQ)
                        } else {
                            updates.add(PendingQuestionUpdate(originalId = q.id, updatedQuestion = modifiedQ))
                        }
                    }
                }
                if (updates.isNotEmpty()) {
                    return Pair(updates, actionText + "\nGenerated ${updates.size} batch updates.")
                }
            }
        } else if (intent == "remove") {
            val targeted = allQuestions.filter { it.id in startId..endId }
            val updates = targeted.map { 
                PendingQuestionUpdate(originalId = it.id, updatedQuestion = it, isDelete = true) 
            }
            if (updates.isNotEmpty()) {
                return Pair(updates, actionText + "\nPending deletion for ${updates.size} questions.")
            } else {
                return Pair(emptyList(), "No questions found in range $startId to $endId to remove.")
            }
        }
        
    } catch (e: Exception) {
        return Pair(emptyList(), "Failed to parse command intent. AI said: $parseRes")
    }
    
    onStatusUpdate(if (intent == "chat") "Generating response..." else actionText, 0f)
    

    if (intent == "extract_questions") {
        if (pdfBytes == null && extractedText == null && attachments.isEmpty()) {
            return Pair(emptyList(), "You asked to extract questions from a document, but no file was attached. Please tap the '+' button to attach a file (PDF, TXT, ZIP, or Image).")
        }
        onStatusUpdate("Extracting questions via AI...", 0.3f)
        
        val sourceText = if (extractedText != null) "extracted text document:\n$extractedText\n\n" else "attached PDF/Image."
        val prompt = """
            You are extracting questions from the $sourceText based on the user's instructions.
            User's Instructions: "$command"
            
            Rules for Extraction:
            1. By default, extract ONLY: questions (including text, tables, images if present), options, answers, path, question header, exams, and explanation from the source itself.
            2. DO NOT generate AI explanations or extraOptions UNLESS the user explicitly asked for them in their instructions.
            3. If the user provided specific instructions for the path, follow them.
            4. **Table Format**: If the question or data involves a table (grid lines or simple listing), extract the table data and set 'format' to "table". Construct 'tableData' as a JSON string representation of a 2D array of strings (e.g. '[["Col1", "Col2"], ["Val1", "Val2"]]'). DO NOT put the table data in the 'question' text; keep the 'question' text clean.
            5. **Images from Source**: If the user provides a ZIP file with images, or explicitly references extracting existing images, map the image filenames provided in the System Note to the appropriate fields (e.g. 'img', 'explanationImageOptions'). Follow the user's exact instructions for how to map them.
            6. **AI/Web Images**: If the user explicitly asks for "AI generated explanation image", generate 3 different highly detailed image generation prompts based on the context. Return the prompts as a JSON string array in a field called 'explanationImagePrompts'. If asked for "embedding img from web" or "web image", return 3 relevant image URLs using this format: "https://loremflickr.com/800/600/keyword" (replace 'keyword' with a specific single word describing the context) in a field called 'explanationImageOptions'.
            7. **Math & Style (STRICT MANDATE)**: You MUST preserve mathematical symbols and format them correctly. You MUST wrap ALL inline mathematical expressions, formulas, and variables in single dollar signs `${"$"}`. (e.g., `${"$"}E = mc^2${"$"}` or `${"$"}x=1${"$"}`). Block equations MUST use double dollar signs `${"$$"}`. NEVER output bare LaTeX like `\frac{1}{2}` without wrapping it in dollar signs. NEVER use `\(` or `\[` for math.  Use HTML for bold/italics if necessary.
            **CRITICAL JSON ESCAPING RULE**: You are generating a JSON string. You MUST properly double-escape all backslashes in LaTeX formulas so they do not break JSON parsing. For example, output `\\\\frac{1}{2}` instead of `\\frac{1}{2}`, and `\\\\alpha` instead of `\\alpha`.
            8. Extract ALL questions from the document. Take as much space as you need.
            9. Format the output strictly as a JSON array containing objects matching this schema:
            [
              {
                "question": "question text",
                "options": ["A", "B", "C", "D"],
                "extraOptions": ["E", "F"] (ONLY IF REQUESTED),
                "correctIndex": integer index of correct option,
                "explanation": "Brief or ai generated explanation (ONLY IF REQUESTED)",
                "detailedExplanation": "Explanation explicitly extracted from the book",
                "path": "Topic/Subtopic based on PDF headers or user instructions",
                "format": "text or table",
                "tableData": "JSON string of 2D array (ONLY if format is table)",
                "explanationImagePrompts": ["prompt1", "prompt2"] (ONLY IF REQUESTED),
                "explanationImageOptions": ["url1", "url2"] (ONLY IF REQUESTED),
                "section": "question header if present",
                "exam": "exam tags if present"
              }
            ]
            Return ONLY the JSON array. Do not use markdown blocks like ```json.
        """.trimIndent()
        
        onDevLog("[EXTRACT PROMPT]\n" + prompt)
        var rawResult = ""
        try {
            val result = com.example.data.GeminiHelper.generateContent(
                context = context, 
                prompt = prompt, 
                pdfBytes = pdfBytes,
                requireJson = true,
                attachments = attachments,
                onDevLog = onDevLog
            )
            rawResult = result
            
            if (result.startsWith("Error")) {
                return Pair(emptyList(), result)
            }
            
            onStatusUpdate("Parsing AI response...", 0.8f)
            val updates = mutableListOf<PendingQuestionUpdate>()
            
            val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(result).jsonArray
            val gson = com.example.data.SharedGson.normal
            val baseBookId = allQuestions.firstOrNull()?.bookId ?: "ExtractedBook"
            val maxPossibleId = allQuestions.maxOfOrNull { it.id } ?: 0L
            
            for (item in jsonArray) {
                val obj = item.jsonObject
                val qOptions = obj["options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val qExtraOptions = obj["extraOptions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                
                val imgOptions = mutableListOf<String>()
                val imgOptionsElement = obj["explanationImageOptions"]
                if (imgOptionsElement != null) {
                    when {
                        imgOptionsElement is kotlinx.serialization.json.JsonArray -> imgOptions.addAll(imgOptionsElement.map { it.jsonPrimitive.content })
                        imgOptionsElement is kotlinx.serialization.json.JsonPrimitive -> {
                            val str = imgOptionsElement.content
                            if (str.startsWith("[") && str.endsWith("]")) {
                                try {
                                    imgOptions.addAll(kotlinx.serialization.json.Json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content })
                                } catch(e: Exception) { imgOptions.add(str) }
                            } else imgOptions.add(str)
                        }
                    }
                }
                
                val imgPrompts = mutableListOf<String>()
                val imgPromptsElement = obj["explanationImagePrompts"]
                if (imgPromptsElement != null) {
                    when {
                        imgPromptsElement is kotlinx.serialization.json.JsonArray -> imgPrompts.addAll(imgPromptsElement.map { it.jsonPrimitive.content })
                        imgPromptsElement is kotlinx.serialization.json.JsonPrimitive -> {
                            val str = imgPromptsElement.content
                            if (str.startsWith("[") && str.endsWith("]")) {
                                try {
                                    imgPrompts.addAll(kotlinx.serialization.json.Json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content })
                                } catch(e: Exception) { imgPrompts.add(str) }
                            } else imgPrompts.add(str)
                        }
                    }
                }
                
                val generatedOptions1 = qOptions.map { com.example.data.QBookOption(id = java.util.UUID.randomUUID().toString(), text = it) }
                val cIndex1 = obj["correctIndex"]?.jsonPrimitive?.int ?: 0
                val updatedQ = com.example.data.QuestionEntity(
                    globalId = java.util.UUID.randomUUID().toString(),
                    id = maxPossibleId + 1 + updates.size.toLong(),
                    bookId = baseBookId,
                    category = "AI Extracted",
                    question = obj["question"]?.jsonPrimitive?.content ?: "",
                    options = gson.toJson(qOptions),
                    advancedOptions = gson.toJson(generatedOptions1),
                    advancedCorrectIds = gson.toJson(listOfNotNull(generatedOptions1.getOrNull(cIndex1)?.id)),
                    extraOptions = gson.toJson(qExtraOptions),
                    correctIndex = cIndex1,
                    explanation = obj["explanation"]?.jsonPrimitive?.content ?: "",
                    detailedExplanation = obj["detailedExplanation"]?.jsonPrimitive?.content,
                    format = obj["format"]?.jsonPrimitive?.content ?: "text",
                    tableData = obj["tableData"]?.jsonPrimitive?.content,
                    section = obj["section"]?.jsonPrimitive?.content ?: "",
                    exam = obj["exam"]?.jsonPrimitive?.content ?: "",
                    year = "",
                    path = obj["path"]?.jsonPrimitive?.content ?: ""
                )
                updates.add(PendingQuestionUpdate(
                    originalId = -1L, 
                    updatedQuestion = updatedQ,
                    imageOptions = imgPrompts.takeIf { it.isNotEmpty() } ?: imgOptions
                ))
            }
            
            if (updates.isNotEmpty()) {
                return Pair(updates, "I extracted ${updates.size} questions from the PDF. Please review the pending updates above.")
            } else {
                return Pair(emptyList(), "I couldn't find or generate any questions from the PDF.")
            }
        } catch (e: Exception) {
            onDevLog("[JSON PARSE ERROR] ${e.message}\nRAW:\n$rawResult")
            return Pair(emptyList(), "The AI returned invalid JSON. Check Developer Logs for details.")
        }
    }

    if (intent == "chat") {
        val recentHistory = chatHistory.takeLast(10).map { "${if (it.role == "user") "User" else "AI"}: ${it.content}" }.joinToString("\n")
        
        val contextContext = if (requiresQuestions) {
            "All Book Questions (Filtered to Current Topic/Context): \n" + gson.toJson(allQuestions)
        } else {
            "All Book Questions: (Not provided for this simple query to save tokens)"
        }
        val contextInfoString = if (currentContextInfo.isNotBlank()) "Current User Context:\n$currentContextInfo" else ""
        
        val chatPrompt = """
            You are Sparkle, a helpful educational AI Co-Pilot for the Quizer app.\n\nCRITICAL INSTRUCTION: You are also a versatile, unrestricted conversational AI just like ChatGPT or Gemini. You MUST answer ANY question the user asks, whether it is about the book, general knowledge, coding, logic, or casual chat. DO NOT restrict yourself to only discussing the Quizer app or the book's content. If they ask a general question, answer it fully and naturally!
            
            $contextInfoString
            
            $contextContext
            
            Recent Chat History:
            $recentHistory
            
            User: $command
            
            Respond helpfully, politely, and concisely. You can explain questions, provide examples, and chat freely. 
            Note: The user might ask to explain a question by its local number in their current topic. Use the 'Current User Context' and 'All Book Questions' to find the exact question they mean.
            FORMATTING RULES:
            - Options: When explaining a question or referring to options, ALWAYS use their letter designations (e.g. Option A, Option B, Option C, Option D) corresponding to their order (index 0 = A, index 1 = B, etc.). DO NOT use index numbers like Option 0 or Option 1.
            - Use Markdown for bold (**text**), italics (*text*), and lists.
            - Use Markdown code blocks (`code` or ```language\n code \n```) for code snippets.
            - You can use Markdown tables if you need to present tabular data.
            - MATH FORMATTING (STRICT MANDATE): All inline math, variables, and formulas MUST be wrapped in single dollar signs `${"$"}`. Block equations must use double dollar signs `${"$$"}`.
              Examples: `${"$"}E = mc^2${"$"}` or `${"$$"}\int x dx${"$$"}`. 
              NEVER output naked equations like `x=1` or `a^2+b^2=c^2`. You MUST wrap them: `${"$"}x=1${"$"}`.
              NEVER use `\(` or `\[`.
            - IMAGES: If illustrating a spatial, structural, or complex visual concept would significantly help understanding, you can include an AI-generated image by using this exact tag: [AI_IMAGE: <detailed prompt for image generation here>]. Use this sparingly and ONLY when a visual aid adds real value. Do NOT use standard Markdown image syntax.
        """.trimIndent()
        
        val chatResponse = GeminiHelper.generateContent(context, chatPrompt, pdfBytes = pdfBytes, attachments = attachments, onDevLog = onDevLog)
        
        // Extract AI_IMAGE prompts if any
        val imagePromptRegex = Regex("\\[AI_IMAGE:(.*?)\\]")
        var finalResponse = chatResponse
        val matches = imagePromptRegex.findAll(chatResponse).toList()
        for (match in matches) {
            val imgPrompt = match.groupValues[1].trim()
            val imgUrl = com.example.data.ImageGenerationHelper.generateImage(context, imgPrompt)
            if (imgUrl != null) {
                finalResponse = finalResponse.replace(match.value, "[IMG_URL:$imgUrl]")
            } else {
                finalResponse = finalResponse.replace(match.value, "(Failed to generate image)")
            }
        }
        
        return Pair(emptyList(), finalResponse)
    }
    
    // EDIT INTENT
    val targetQuestions = if (requiredLocalNumbers.isNotEmpty()) {
        allQuestions.filter { it.id.toInt() in requiredLocalNumbers }
    } else {
        allQuestions.filter { it.id in startId..endId }
    }
    if (targetQuestions.isEmpty()) {
        return Pair(emptyList(), "I couldn't find any questions matching that range to edit.")
    }
    
    val chunkSize = 1
    val chunks = targetQuestions.chunked(chunkSize)
    val totalChunks = chunks.size
    
    val pendingUpdates = mutableListOf<PendingQuestionUpdate>()
    
    for ((index, chunk) in chunks.withIndex()) {
        val progress = if (totalChunks > 0) ((index + 1).toFloat() / totalChunks).coerceAtLeast(0.05f) else 0f
        val msg = if (chunk.size == 1 && totalChunks == 1) {
            "Editing question ${chunk[0].id}..."
        } else {
            "Editing question ${chunk.first().id} from ${targetQuestions.first().id} to ${targetQuestions.last().id}..."
        }
        onStatusUpdate(msg, progress)
        val qData = chunk.map {
            mapOf(
                "id" to it.id,
                "question" to it.question,
                "options" to it.options,
                "extraOptions" to it.extraOptions,
                "explanation" to it.explanation,
                "detailedExplanation" to (it.detailedExplanation ?: ""),
                "format" to (it.format ?: ""),
                "tableData" to (it.tableData ?: ""),
                "questionHeader" to (it.questionHeader ?: ""),
                "path" to (it.path ?: "")
            )
        }
        
        onStatusUpdate(msg, progress)
        val taskPrompt = """
            User command: "$command"
            You are an educational AI Co-Pilot. You must process the input questions based on the command.
            
            Guidelines:
            1. **Distractors (extraOptions)**: If asked for distractors or extraOptions, generate highly plausible but incorrect options (at least 3-4). Format them as a JSON string array like '["Distractor 1", "Distractor 2"]'.
            2. **Explanations & MATH**: If asked for detailed explanations, use HTML or Markdown. When explaining a question or referring to options, ALWAYS use their letter designations (e.g. Option A, Option B) corresponding to their order (index 0 = A, index 1 = B, etc.). DO NOT use index numbers like Option 0 or Option 1. Wrap mathematical expressions, formula, and variable in single dollar signs `${"$"}`. Examples: `${"$"}E = mc^2${"$"}` or `${"$"}x=1${"$"}`. Block equations must use double dollar signs `${"$$"}`. NEVER write math in plain text like `\int` or `x=1` or `a^2+b^2=c^2` without the `${"$"}` wrapper! Do NOT use `\(` or `\[`. NEVER wrap math expressions in backticks (e.g. NEVER use backticks around the dollar signs).  Use ONLY dollar signs.
            **CRITICAL JSON ESCAPING RULE**: You are generating a JSON string. You MUST properly double-escape all backslashes in LaTeX formulas so they do not break JSON parsing. For example, output `\\\\frac{1}{2}` instead of `\\frac{1}{2}`.
            3. **Images**: If asked for "AI generated explanation image", generate 3 different highly detailed image generation prompts based on the context. Return the prompts as a JSON string array in a field called 'explanationImagePrompts'. If asked for "embedding img from web" or "web image", return 3 relevant image URLs using this format: "https://loremflickr.com/800/600/keyword" (replace 'keyword' with a specific single word describing the context) in a field called 'explanationImageOptions'.
            4. **Expansion (Single to Multiple)**: If asked to expand a question into multiple questions, you should return the original question PLUS the newly generated questions. For new questions, assign them "id": -1.
            5. **Table Format**: If asked to convert tabular data to 'table' format, set 'format' to "table", and construct 'tableData' as a JSON string representation of a 2D array of strings (e.g. '[["Col1", "Col2"], ["Val1", "Val2"]]'). Clean up the 'question' text to remove the table data.
            6. **Question Header**: If asked to edit the header or extract common context, use the 'questionHeader' field. To apply a header to a range of questions, format the 'questionHeader' string as a JSON object: '{"startId": 1, "endId": 5, "text": "Header text here"}'. IMPORTANT: If editing an existing header, you MUST preserve its original startId and endId unless the user explicitly tells you to change them. Do not use plain text if it applies to multiple questions.
            7. **STRICT USER RULES**: The user command may contain specific rules, filters, or conditions (e.g. "only edit questions containing X", "keep original startId"). You MUST follow these instructions strictly and only modify the fields or questions that match the user's conditions.
            
            Input Questions:
            ${gson.toJson(qData)}
            
            Return a JSON array containing the modified/new questions. 
            Ensure all original keys are preserved: 'id', 'question', 'options', 'extraOptions', 'explanation', 'detailedExplanation', 'format', 'tableData', 'questionHeader'. Add 'explanationImagePrompts' if AI images requested, or 'explanationImageOptions' if web images requested. For new questions, also provide 'correctIndex' (integer 0-based).
            IMPORTANT: Return ONLY the raw JSON array. Do not use markdown backticks like ```json.
        """.trimIndent()
        
        val resultJson = GeminiHelper.generateContent(context, taskPrompt, onDevLog = onDevLog)
        
        if (resultJson.startsWith("Error:")) {
            continue
        }

        try {
            var cleanResult = resultJson.replace("```json", "").replace("```", "").trim()
            val jsonStart = cleanResult.indexOf('[')
            val jsonEnd = cleanResult.lastIndexOf(']')
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd >= jsonStart) {
                cleanResult = cleanResult.substring(jsonStart, jsonEnd + 1)
            }
            
            val element = Json.parseToJsonElement(cleanResult)
            val resultArray = when (element) {
                is kotlinx.serialization.json.JsonArray -> element
                is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonArray(listOf(element))
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val contentStr = element.content.trim()
                    if (contentStr.startsWith("[")) {
                        Json.parseToJsonElement(contentStr).jsonArray
                    } else {
                        throw Exception("AI returned a string instead of an array: $contentStr")
                    }
                }
            }
            
            for (item in resultArray) {
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.int ?: continue
                
                val imgOptions = mutableListOf<String>()
                val imgOptionsElement = obj["explanationImageOptions"]
                if (imgOptionsElement != null) {
                    when {
                        imgOptionsElement is kotlinx.serialization.json.JsonArray -> imgOptions.addAll(imgOptionsElement.map { it.jsonPrimitive.content })
                        imgOptionsElement is kotlinx.serialization.json.JsonPrimitive -> {
                            val str = imgOptionsElement.content
                            if (str.startsWith("[") && str.endsWith("]")) {
                                try {
                                    imgOptions.addAll(Json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content })
                                } catch(e: Exception) { imgOptions.add(str) }
                            } else imgOptions.add(str)
                        }
                    }
                }
                
                val imgPromptsElement = obj["explanationImagePrompts"]
                if (imgPromptsElement != null) {
                    val prompts = mutableListOf<String>()
                    when {
                        imgPromptsElement is kotlinx.serialization.json.JsonArray -> prompts.addAll(imgPromptsElement.map { it.jsonPrimitive.content })
                        imgPromptsElement is kotlinx.serialization.json.JsonPrimitive -> {
                            val str = imgPromptsElement.content
                            if (str.startsWith("[") && str.endsWith("]")) {
                                try {
                                    prompts.addAll(Json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content })
                                } catch(e: Exception) { prompts.add(str) }
                            } else prompts.add(str)
                        }
                    }
                    
                    // Ensure we always generate at least 3 options even if AI only gave 1 prompt
                    val finalPrompts = if (prompts.size == 1) {
                        listOf(prompts[0], prompts[0], prompts[0])
                    } else {
                        prompts
                    }
                    for (promptText in finalPrompts.take(3)) {
                        val generatedUrl = com.example.data.ImageGenerationHelper.generateImage(context, promptText)
                        if (generatedUrl != null) {
                            imgOptions.add(generatedUrl)
                        }
                    }
                }

                val optionsElement = obj["options"]
                val parsedOptions = when {
                    optionsElement is kotlinx.serialization.json.JsonArray -> optionsElement.map { it.jsonPrimitive.content }
                    optionsElement is kotlinx.serialization.json.JsonPrimitive -> {
                        val str = optionsElement.content
                        if (str.startsWith("[") && str.endsWith("]")) {
                            try {
                                Json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content }
                            } catch(e: Exception) { listOf(str) }
                        } else listOf(str)
                    }
                    else -> emptyList()
                }

                val extraOptionsElement = obj["extraOptions"]
                val parsedExtraOptions = when {
                    extraOptionsElement is kotlinx.serialization.json.JsonArray -> extraOptionsElement.map { it.jsonPrimitive.content }
                    extraOptionsElement is kotlinx.serialization.json.JsonPrimitive -> {
                        val str = extraOptionsElement.content
                        if (str.startsWith("[") && str.endsWith("]")) {
                            try {
                                Json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content }
                            } catch(e: Exception) { listOf(str) }
                        } else listOf(str)
                    }
                    else -> emptyList()
                }
                
                // Find matching original question to preserve data not modified by AI
                val originalQ = allQuestions.find { it.id.toInt() == id }
                
                val gson = com.example.data.SharedGson.normal
                val generatedOptions2 = parsedOptions.map { com.example.data.QBookOption(id = java.util.UUID.randomUUID().toString(), text = it) }
                val cIndex2 = obj["correctIndex"]?.jsonPrimitive?.int ?: originalQ?.correctIndex ?: 0
                val updatedQ = QuestionEntity(
                    globalId = originalQ?.globalId ?: java.util.UUID.randomUUID().toString(),
                    id = if (id == -1) System.currentTimeMillis() else id.toLong(),
                    bookId = originalQ?.bookId ?: allQuestions.firstOrNull()?.bookId ?: "",
                    category = originalQ?.category ?: "AI Generated",
                    question = obj["question"]?.jsonPrimitive?.content ?: originalQ?.question ?: "",
                    options = gson.toJson(parsedOptions),
                    advancedOptions = gson.toJson(generatedOptions2),
                    advancedCorrectIds = gson.toJson(listOfNotNull(generatedOptions2.getOrNull(cIndex2)?.id)),
                    extraOptions = gson.toJson(parsedExtraOptions),
                    correctIndex = cIndex2,
                    explanation = obj["explanation"]?.jsonPrimitive?.content ?: originalQ?.explanation ?: "",
                    detailedExplanation = obj["detailedExplanation"]?.jsonPrimitive?.content ?: originalQ?.detailedExplanation,
                    img = originalQ?.img,
                    explanationImage = originalQ?.explanationImage,
                    section = originalQ?.section ?: "",
                    exam = originalQ?.exam ?: "",
                    year = originalQ?.year ?: "",
                    format = obj["format"]?.jsonPrimitive?.content ?: originalQ?.format,
                    tableData = obj["tableData"]?.jsonPrimitive?.content ?: originalQ?.tableData,
                    questionHeader = obj["questionHeader"]?.let { 
                        if (it is kotlinx.serialization.json.JsonObject) it.toString() 
                        else {
                            val raw = it.jsonPrimitive.content
                            if (raw.trim().startsWith("{")) {
                                raw
                            } else if (raw.isNotBlank()) {
                                "{\"startId\": ${originalQ?.id ?: 1}, \"endId\": ${originalQ?.id ?: 1}, \"text\": \"${raw.replace("\"", "\\\"").replace("\n", "\\n")}\"}"
                            } else null
                        }
                    } ?: originalQ?.questionHeader,
                    path = obj["path"]?.jsonPrimitive?.content ?: originalQ?.path ?: ""
                )

                pendingUpdates.add(PendingQuestionUpdate(originalQ?.id ?: -1L, updatedQ, imgOptions))
            }
        } catch (e: Exception) {
            // continue
        }
    }
    
    return Pair(pendingUpdates, "I've successfully processed the request.")
}

@Composable
fun ChatMessageContent(content: String, isUser: Boolean = false) {
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val imageRegex = Regex("\\[IMG_URL:(.*?)\\]")
    val matches = imageRegex.findAll(content).toList()
    
    androidx.compose.foundation.text.selection.SelectionContainer {
        if (matches.isEmpty()) {
            com.example.ui.components.SmartRichText(
                text = content,
                color = textColor,
                fontSize = 16.sp
            )
        } else {
            Column {
                var lastIndex = 0
                for (match in matches) {
                    val textPart = content.substring(lastIndex, match.range.first)
                    if (textPart.isNotBlank()) {
                        com.example.ui.components.SmartRichText(
                            text = textPart,
                            color = textColor,
                            fontSize = 16.sp
                        )
                    }
                    
                    val imgUrl = match.groupValues[1]
                    coil.compose.SubcomposeAsyncImage(
                model = imgUrl,
                contentDescription = "AI Generated Image",
                loading = {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .padding(vertical = 8.dp)
            )
            
            lastIndex = match.range.last + 1
        }
        
        val remainingText = content.substring(lastIndex)
        if (remainingText.isNotBlank()) {
            com.example.ui.components.SmartRichText(
                text = remainingText,
                color = textColor,
                fontSize = 16.sp
            )
        }
    }
}
}
}
