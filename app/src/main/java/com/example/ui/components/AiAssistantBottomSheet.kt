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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

fun kotlinx.serialization.json.JsonObject.optCleanString(key: String): String? {
    val el = this[key] ?: return null
    if (el is kotlinx.serialization.json.JsonNull) return null
    if (el is kotlinx.serialization.json.JsonPrimitive) {
        val c = el.content.trim()
        return if (c.isNotEmpty() && !c.equals("null", ignoreCase = true) && !c.equals("undefined", ignoreCase = true) && !c.equals("none", ignoreCase = true)) c else null
    }
    return null
}

fun isHeaderOmissionRequested(cmd: String): Boolean {
    val lower = cmd.lowercase().trim()
    if (lower.contains("omit header") || lower.contains("omit headers") ||
        lower.contains("set header to null") || lower.contains("set headers to null") ||
        lower.contains("set header null") || lower.contains("set headers null") ||
        lower.contains("set it to null") || lower.contains("set them to null") ||
        lower.contains("header to null") || lower.contains("headers to null") ||
        lower.contains("header = null") || lower.contains("header: null") ||
        lower.contains("header null") || lower.contains("headers null") ||
        lower.contains("null header") || lower.contains("null headers") ||
        lower.contains("no header") || lower.contains("no headers") ||
        lower.contains("without header") || lower.contains("without headers") ||
        lower.contains("skip header") || lower.contains("skip headers") ||
        lower.contains("ignore header") || lower.contains("ignore headers") ||
        lower.contains("remove header") || lower.contains("remove headers") ||
        lower.contains("delete header") || lower.contains("delete headers") ||
        lower.contains("empty header") || lower.contains("empty headers") ||
        lower.contains("header empty") || lower.contains("headers empty") ||
        lower.contains("exclude header") || lower.contains("exclude headers") ||
        lower.contains("clear header") || lower.contains("clear headers") ||
        lower.contains("no question header") || lower.contains("no question headers") ||
        lower.contains("omit question header") || lower.contains("omit question headers") ||
        lower.contains("don't include header") || lower.contains("dont include header") ||
        lower.contains("do not include header") || lower.contains("don't extract header") ||
        lower.contains("dont extract header") || lower.contains("do not extract header") ||
        lower.contains("don't add header") || lower.contains("dont add header") ||
        lower.contains("do not add header") || lower.contains("don't set header") ||
        lower.contains("dont set header") || lower.contains("do not set header")
    ) return true

    val pattern1 = Regex("""(?i)\b(?:omit|no|without|skip|ignore|remove|delete|null|empty|exclude|clear|leave\s+out|don't\s+(?:include|extract|set|add|put|give)|do\s+not\s+(?:include|extract|set|add|put|give))\s+(?:any\s+)?(?:question\s*)?(?:headers?|headings?|passages?|directions?|instructions?)\b""")
    val pattern2 = Regex("""(?i)\b(?:headers?|headings?|passages?|directions?|instructions?)\s*(?:=|:|\bis\b|\bto\b|\bas\b)?\s*(?:null|empty|none|omit|skip|false|no|blank|zero)\b""")
    val pattern3 = Regex("""(?i)\bset\s+(?:it|them|header|headers)\s+(?:as\s+|to\s+)?(?:null|empty|blank|none)\b""")
    return pattern1.containsMatchIn(cmd) || pattern2.containsMatchIn(cmd) || pattern3.containsMatchIn(cmd)
}

fun processExtractedQuestionHeaders(updates: List<PendingQuestionUpdate>): List<PendingQuestionUpdate> {
    if (updates.isEmpty()) return updates

    // Step 1: Extract clean text headers from each question and detect explicit ranges
    val workingList = updates.map { pending ->
        val q = pending.updatedQuestion
        val rawHeader = q.questionHeader
        val cleanHeader = if (rawHeader != null && rawHeader.trimStart().startsWith("{")) {
            try {
                com.example.data.SharedGson.normal.fromJson(rawHeader, com.example.data.QBookHeader::class.java).text
            } catch (e: Exception) { rawHeader }
        } else {
            rawHeader
        }
        pending.copy(updatedQuestion = q.copy(questionHeader = cleanHeader?.trim()?.takeIf { it.isNotBlank() }))
    }.toMutableList()

    // Step 2: Propagate header if it contains explicit question range references
    // Examples:
    // "Choose one word Substitution (for question from 1 to 10)"
    // "Read Passages And Answer Questions (1 -10)"
    // "Directions (Q. 1 to 5): ..."
    // "Questions 1-10: In the following questions..."
    val rangeRegex = Regex("""(?i)(?:for\s+questions?\s*(?:from\s*)?|questions?\s*|\bQ\.?(?:\s*Nos?\.?)?\s*|\()\s*(\d+)\s*(?:to|-|–)\s*(\d+)\s*\)?""")

    var i = 0
    while (i < workingList.size) {
        val currentHeader = workingList[i].updatedQuestion.questionHeader
        if (!currentHeader.isNullOrBlank()) {
            val match = rangeRegex.find(currentHeader)
            if (match != null) {
                val startNum = match.groupValues[1].toIntOrNull()
                val endNum = match.groupValues[2].toIntOrNull()
                if (startNum != null && endNum != null && endNum >= startNum) {
                    val count = endNum - startNum + 1
                    for (k in 1 until count) {
                        val targetIdx = i + k
                        if (targetIdx < workingList.size) {
                            val nextQ = workingList[targetIdx].updatedQuestion
                            // Only propagate if the next question doesn't have a different non-blank header
                            if (nextQ.questionHeader.isNullOrBlank()) {
                                workingList[targetIdx] = workingList[targetIdx].copy(
                                    updatedQuestion = nextQ.copy(questionHeader = currentHeader)
                                )
                            } else {
                                break // A new header block has started
                            }
                        }
                    }
                }
            }
        }
        i++
    }

    // Step 3: Format contiguous question groups sharing the same header into QBookHeader JSON format
    // {"startId": startId, "endId": endId, "text": "..."}
    val finalList = workingList.toMutableList()
    var groupStartIdx = 0
    while (groupStartIdx < finalList.size) {
        val headerText = finalList[groupStartIdx].updatedQuestion.questionHeader
        if (!headerText.isNullOrBlank()) {
            var groupEndIdx = groupStartIdx
            while (groupEndIdx + 1 < finalList.size && finalList[groupEndIdx + 1].updatedQuestion.questionHeader == headerText) {
                groupEndIdx++
            }
            val startId = finalList[groupStartIdx].updatedQuestion.id
            val endId = finalList[groupEndIdx].updatedQuestion.id
            val escapedText = headerText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
            val headerJson = "{\"startId\": $startId, \"endId\": $endId, \"text\": \"$escapedText\"}"
            for (idx in groupStartIdx..groupEndIdx) {
                val q = finalList[idx].updatedQuestion
                finalList[idx] = finalList[idx].copy(
                    updatedQuestion = q.copy(
                        questionHeader = headerJson,
                        section = headerText
                    )
                )
            }
            groupStartIdx = groupEndIdx + 1
        } else {
            groupStartIdx++
        }
    }

    return finalList
}

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
    onNavigateToUsage: () -> Unit = {},
    autoSend: Boolean = false,
    onAutoSendConsumed: () -> Unit = {}
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
    
    val allChatHistory by viewModel.chatHistory.collectAsState()
    val chatHistory = allChatHistory[chatContextId] ?: emptyList()
    val listState = rememberLazyListState()
    
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, chatContextId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START || event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.setChatVisible(chatContextId, context)
                viewModel.onAppForegrounded(context)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP || event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.setChatVisible(null, context)
                viewModel.onAppMinimized(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setChatVisible(chatContextId, context)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setChatVisible(null, context)
            viewModel.postActiveTaskNotifications(context)
        }
    }

    LaunchedEffect(currentBookId) {
        viewModel.loadChatHistory(chatContextId)
    }

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    var hasAutoSent by remember { mutableStateOf(!autoSend) }
    val sessionAttachments by viewModel.sessionAttachments.collectAsState()
    val attachedPdfUri = sessionAttachments[chatContextId]

    LaunchedEffect(autoSend, prompt, isProcessing) {
        if (autoSend && !hasAutoSent && prompt.isNotBlank() && !isProcessing) {
            hasAutoSent = true
            onAutoSendConsumed()
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
        }
    }
    
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

    var attachedFileName by remember(attachedPdfUri) { mutableStateOf("Document Attached") }
    LaunchedEffect(attachedPdfUri) {
        if (attachedPdfUri != null) {
            val cursor = context.contentResolver.query(attachedPdfUri!!, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        attachedFileName = it.getString(index)
                    }
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val closeSheetAnim: () -> Unit = {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } catch (e: Exception) {
            } finally {
                onDismiss()
            }
        }
    }
    
    val handleNativeDismiss: () -> Unit = {
        onDismiss()
    }
    
    CustomModalBottomSheet(
        sheetState = sheetState,
        maxHeightFraction = 0.90f,
        enableDynamicHeight = true,
        onDismissRequest = handleNativeDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.8.dp)
                .weight(1f, fill = true)
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
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, "More Options", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("AI Settings") },
                                onClick = { 
                                    menuExpanded = false
                                    coroutineScope.launch {
                                        try {
                                            sheetState.hide()
                                        } catch (e: Exception) {
                                        } finally {
                                            onDismiss()
                                            onNavigateToAiSettings()
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, "Settings") }
                            )
                            DropdownMenuItem(
                                text = { Text("API Usages") },
                                onClick = { 
                                    menuExpanded = false
                                    coroutineScope.launch {
                                        try {
                                            sheetState.hide()
                                        } catch (e: Exception) {
                                        } finally {
                                            onDismiss()
                                            onNavigateToUsage()
                                        }
                                    }
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = closeSheetAnim,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            

            
            // Chat history area
            if (showDevLogs) {
                Column(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
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
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
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
            val latestAiMessage = remember(chatHistory) { chatHistory.findLast { it.role != "user" } }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
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
                
                items(chatHistory, key = { "${it.id}_${it.timestamp}" }) { msg ->
                    val isUser = msg.role == "user"
                    val isLatestAi = !isUser && (msg == latestAiMessage)
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val maxBubbleWidth = configuration.screenWidthDp.dp * 0.85f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            modifier = if (isUser) Modifier.widthIn(max = maxBubbleWidth).wrapContentWidth() else Modifier.fillMaxWidth().padding(end = 8.dp)
                        ) {
                            val sheetBg = MaterialTheme.colorScheme.surface
                            val onSurf = MaterialTheme.colorScheme.onSurface
                            val isDark = androidx.core.graphics.ColorUtils.calculateLuminance(sheetBg.toArgb()) < 0.5

                            val aiBubbleColor = run {
                                if (isDark) {
                                    Color(androidx.core.graphics.ColorUtils.blendARGB(sheetBg.toArgb(), onSurf.toArgb(), 0.08f))
                                } else {
                                    Color(androidx.core.graphics.ColorUtils.blendARGB(sheetBg.toArgb(), onSurf.toArgb(), 0.05f))
                                }
                            }

                            val aiBorderColor = run {
                                val outlineVariant = MaterialTheme.colorScheme.outlineVariant
                                val outline = MaterialTheme.colorScheme.outline
                                if (isDark) {
                                    outline.copy(alpha = 0.25f)
                                } else {
                                    outline.copy(alpha = 0.15f)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .then(
                                        if (!isUser) {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = aiBorderColor,
                                                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
                                            )
                                        } else Modifier
                                    )
                                    .background(
                                        color = if (isUser) MaterialTheme.colorScheme.primary else aiBubbleColor,
                                        shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                ChatMessageContent(content = msg.content, isUser = isUser)
                            }

                            // Action buttons row (copy for user; copy and retry for AI)
                            Row(
                                modifier = Modifier.padding(
                                    top = 2.dp,
                                    start = if (isUser) 0.dp else 4.dp,
                                    end = if (isUser) 4.dp else 0.dp
                                ),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isCopied by remember { mutableStateOf(false) }

                                if (isUser) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(msg.content))
                                            isCopied = true
                                            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(2000)
                                                isCopied = false
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy message",
                                            modifier = Modifier.size(15.dp),
                                            tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val cleanText = msg.content.replace(Regex("\\[IMG_URL:.*?\\]"), "").trim()
                                            clipboardManager.setText(AnnotatedString(cleanText))
                                            isCopied = true
                                            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(2000)
                                                isCopied = false
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy response",
                                            modifier = Modifier.size(15.dp),
                                            tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        )
                                    }

                                    if (isLatestAi) {
                                        Spacer(modifier = Modifier.width(2.dp))

                                        IconButton(
                                            onClick = {
                                                if (!isProcessing) {
                                                    val msgIndex = chatHistory.indexOf(msg)
                                                    val targetPrompt = (if (msgIndex > 0) {
                                                        chatHistory.take(msgIndex).findLast { it.role == "user" }?.content
                                                    } else null) ?: chatHistory.findLast { it.role == "user" }?.content

                                                    if (!targetPrompt.isNullOrBlank()) {
                                                        android.widget.Toast.makeText(context, "Retrying response...", android.widget.Toast.LENGTH_SHORT).show()
                                                        viewModel.deleteChatMessage(msg.id, chatContextId)
                                                        viewModel.startAiProcessing(
                                                            context = context.applicationContext,
                                                            chatContextId = chatContextId,
                                                            prompt = targetPrompt,
                                                            allQuestions = questions,
                                                            mode = mode,
                                                            currentContextInfo = currentContextInfo,
                                                            pdfUri = attachedPdfUri,
                                                            isRetry = true
                                                        )
                                                    } else {
                                                        android.widget.Toast.makeText(context, "No prompt found to retry", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            enabled = !isProcessing,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Retry response",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isProcessing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                            )
                                        }
                                    }
                                }
                            }
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
                                val statusBubbleColor = run {
                                    val surf = MaterialTheme.colorScheme.surface
                                    val onSurf = MaterialTheme.colorScheme.onSurface
                                    val isDark = androidx.core.graphics.ColorUtils.calculateLuminance(surf.toArgb()) < 0.5
                                    val factor = if (isDark) 0.08f else 0.05f
                                    Color(androidx.core.graphics.ColorUtils.blendARGB(surf.toArgb(), onSurf.toArgb(), factor))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = if (androidx.core.graphics.ColorUtils.calculateLuminance(MaterialTheme.colorScheme.surface.toArgb()) < 0.5) 0.25f else 0.15f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .background(statusBubbleColor)
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
                                        Text("Delete Q${pending.updatedQuestion.id}: ${pending.updatedQuestion.question.take(60)}...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
                                    } else {
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            if (!pending.updatedQuestion.questionHeader.isNullOrBlank()) {
                                                val headerText = remember(pending.updatedQuestion.questionHeader) {
                                                    val raw = pending.updatedQuestion.questionHeader!!
                                                    if (raw.trimStart().startsWith("{")) {
                                                        try {
                                                            com.example.data.SharedGson.normal.fromJson(raw, com.example.data.QBookHeader::class.java).text
                                                        } catch(e: Exception) { raw }
                                                    } else raw
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(bottom = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Header: ${headerText.take(70)}${if (headerText.length > 70) "..." else ""}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text("Q${pending.updatedQuestion.id}: ${pending.updatedQuestion.question.take(60)}...", fontWeight = FontWeight.Bold)
                                        }
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
                                                                    viewModel.setPendingUpdates(context, chatContextId, updatedList)
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
                        onClick = { viewModel.setPendingUpdates(context, chatContextId, null) },
                        modifier = Modifier.weight(1f, fill = false)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val currentUpdates = pendingUpdates?.toList() ?: emptyList()
                            viewModel.setPendingUpdates(context, chatContextId, null)
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
                        modifier = Modifier.weight(1f, fill = false)
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
                        visibleQuestionNumbers.map { "Explain $it" }
                    } else {
                        listOf("Explain 1", "Explain 2", "Explain 3", "Explain 4")
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
                            attachedFileName, 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 6.6.dp, top = 4.4.dp)
            ) {
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
                    modifier = Modifier.weight(1f, fill = false),
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
                            viewModel.cancelAiProcessing(context, chatContextId)
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
                        viewModel.setPendingUpdates(context, chatContextId, updatedList)
                    }
                }
                editingPendingQuestion = null
            }
        )
    }
}

/**
 * Resolves question numbers (1-based local topic numbers in READ_BOOK mode or global IDs in Editor mode)
 * to pairs of `(1-based localNumber, QuestionEntity)`.
 */
private fun resolveTargetQuestionsForAi(
    allQuestions: List<QuestionEntity>,
    mode: AiChatMode,
    command: String,
    requiredLocalNumbers: List<Int>,
    parsedStartId: Int?,
    parsedEndId: Int?,
    currentContextInfo: String = ""
): List<Pair<Int, QuestionEntity>> {
    if (allQuestions.isEmpty()) return emptyList()

    val explicitGlobalIdRequested = Regex("""\bid\s*[:#]?\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(command)

    fun resolveSingleNumber(num: Int): Pair<Int, QuestionEntity>? {
        if (mode == AiChatMode.READ_BOOK && !explicitGlobalIdRequested) {
            // In ReadBookScreen, questions are displayed as Q.1 .. Q.N within the current topic.
            if (num in 1..allQuestions.size) {
                return Pair(num, allQuestions[num - 1])
            }
            val byGlobal = allQuestions.withIndex().firstOrNull { it.value.id == num.toLong() }
            if (byGlobal != null) {
                return Pair(byGlobal.index + 1, byGlobal.value)
            }
        } else {
            val byGlobal = allQuestions.withIndex().firstOrNull { it.value.id == num.toLong() }
            if (byGlobal != null) {
                return Pair(byGlobal.index + 1, byGlobal.value)
            }
            if (num in 1..allQuestions.size) {
                return Pair(num, allQuestions[num - 1])
            }
        }
        return null
    }

    val candidateNumbers = LinkedHashSet<Int>()

    // 1. Numbers explicitly extracted by Intent Parser in requiredLocalNumbers
    candidateNumbers.addAll(requiredLocalNumbers)

    // 2. If parsedStartId / parsedEndId were explicitly returned by Intent Parser
    if (parsedStartId != null && parsedEndId != null && parsedStartId <= parsedEndId) {
        // Guard against full-book default range unless user asked for "all"
        val isFullSpan = parsedStartId == 1 && (parsedEndId == allQuestions.size || parsedEndId == (allQuestions.maxOfOrNull { it.id.toInt() } ?: 1))
        val askedForAll = Regex("""\b(all|every|entire|whole)\b""", RegexOption.IGNORE_CASE).containsMatchIn(command)
        if (!isFullSpan || allQuestions.size == 1 || askedForAll) {
            for (n in parsedStartId..parsedEndId) {
                candidateNumbers.add(n)
            }
        }
    } else if (parsedStartId != null) {
        candidateNumbers.add(parsedStartId)
    } else if (parsedEndId != null) {
        candidateNumbers.add(parsedEndId)
    }

    // 3. Direct regex fallback on user command (e.g. "Explain 99", "Q.99", "question 99", "95 to 100")
    if (candidateNumbers.isEmpty()) {
        val rangeMatch = Regex("""\b(\d{1,5})\s*(?:to|-|through|and)\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE).find(command)
        if (rangeMatch != null) {
            val s = rangeMatch.groupValues[1].toIntOrNull()
            val e = rangeMatch.groupValues[2].toIntOrNull()
            if (s != null && e != null && s <= e && (e - s) <= 100) {
                for (n in s..e) candidateNumbers.add(n)
            }
        }
        Regex("""(?:q(?:uestion)?\.?\s*|explain\s+|edit\s+|remove\s+|delete\s+|#)(\d{1,5})\b""", RegexOption.IGNORE_CASE)
            .findAll(command)
            .forEach { m ->
                m.groupValues[1].toIntOrNull()?.let { candidateNumbers.add(it) }
            }
        if (candidateNumbers.isEmpty()) {
            Regex("""^\s*(\d{1,5})\s*$""").find(command)?.groupValues?.get(1)?.toIntOrNull()?.let {
                candidateNumbers.add(it)
            }
        }
    }

    val resolved = candidateNumbers.mapNotNull { resolveSingleNumber(it) }
        .distinctBy { it.second.id }

    if (resolved.isNotEmpty()) {
        return resolved
    }

    // 4. If user said "explain this" / "solve this" without a number, resolve the most prominent question from currentContextInfo
    val refersToCurrentOnScreen = Regex("""\b(this|current|screen|visible|here)\b""", RegexOption.IGNORE_CASE).containsMatchIn(command) ||
        command.trim().equals("explain", ignoreCase = true)
    if (refersToCurrentOnScreen && currentContextInfo.isNotBlank()) {
        val prominentLocalMatch = Regex("""most prominent question on their screen is Q\.(\d+)""", RegexOption.IGNORE_CASE).find(currentContextInfo)
            ?: Regex("""\(which is question (\d+) in this topic\)""", RegexOption.IGNORE_CASE).find(currentContextInfo)
        val prominentLocal = prominentLocalMatch?.groupValues?.get(1)?.toIntOrNull()
        if (prominentLocal != null) {
            resolveSingleNumber(prominentLocal)?.let { return listOf(it) }
        }
    }

    return emptyList()
}

/**
 * Formats a QuestionEntity into a clean map with explicit 1-based local number (`Q.99`),
 * global Book ID (`2569`), lettered options (`Option A`..`Option D`), and correct answer key.
 */
private fun formatQuestionForAiPrompt(localNum: Int, q: QuestionEntity): Map<String, Any?> {
    val gson = com.example.data.SharedGson.normal
    val rawOpts: List<String> = try {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        gson.fromJson<List<String>>(q.options, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
    val letteredOptions = rawOpts.mapIndexed { idx, text ->
        "Option ${(65 + idx).toChar()}: $text"
    }
    val correctLetter = if (q.correctIndex in rawOpts.indices) {
        "Option ${(65 + q.correctIndex).toChar()} (${rawOpts[q.correctIndex]})"
    } else {
        "Option ${(65 + q.correctIndex.coerceAtLeast(0)).toChar()}"
    }

    val map = LinkedHashMap<String, Any?>()
    map["localQuestionNumber"] = "Q.$localNum"
    map["localNumber"] = localNum
    map["bookQuestionId"] = q.id
    map["topicPath"] = q.path
    if (!q.questionHeader.isNullOrBlank() && !q.questionHeader.equals("null", ignoreCase = true)) {
        map["questionHeader"] = q.questionHeader
    }
    map["question"] = q.question
    if (!q.tableData.isNullOrBlank() && !q.tableData.equals("null", ignoreCase = true)) {
        map["tableData"] = q.tableData
    }
    map["options"] = letteredOptions
    map["correctAnswerInBook"] = correctLetter
    if (q.explanation.isNotBlank() && !q.explanation.equals("null", ignoreCase = true)) {
        map["bookExplanation"] = q.explanation
    }
    if (!q.detailedExplanation.isNullOrBlank() && !q.detailedExplanation.equals("null", ignoreCase = true)) {
        map["detailedExplanation"] = q.detailedExplanation
    }
    if (q.exam.isNotBlank() && !q.exam.equals("null", ignoreCase = true)) {
        map["exam"] = q.exam
    }
    if (q.year.isNotBlank() && !q.year.equals("null", ignoreCase = true)) {
        map["year"] = q.year
    }
    return map
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
    
    val hasAttachedFile = pdfBytes != null || extractedText != null || attachments.isNotEmpty()
    
    onStatusUpdate("Analyzing request...", 0f)
    val intentPrompt = """
        User command: "$command"
        Mode: $mode
        Current context info: $currentContextInfo
        File/Document Attached: $hasAttachedFile
        
        Determine if the user wants to CHAT, EDIT, EXTRACT_QUESTIONS, BATCH_EDIT, or REMOVE.
        - CHAT: General conversation, answering ANY general knowledge or coding questions, asking for explanations, generating images, or requesting examples. (e.g. "provide an image", "explain this", "hi", "what is this?", "who is the pm of india?", "write a python script")
        - EDIT: Explicitly modifying the book/questions (e.g. "add distractors", "fix typos", "format table", "add an image to this question").
        - EXTRACT_QUESTIONS: User attached a document/image/PDF and wants to extract questions into the book or process questions with specific commands (e.g. "omit header", "set header to null", "extract all", "parse this document").
        - BATCH_EDIT: Changing a specific field (e.g., questionHeader, path, category, format) to a specific value for a range of existing questions, without an uploaded document. (e.g., "change question header to 'Choose OWS' for question 5 to 25", "change path to 'Maths' for 1 to 25").
        - REMOVE: Explicitly asking to delete or remove questions. (e.g., "remove question 5", "delete questions 10 to 20").
        
        CRITICAL RULE: If a file is attached (File/Document Attached: true) and the user gives extraction or document instructions (e.g., "extract questions", "omit header", "set header to null", "no headers", "parse document", "import questions"), you MUST return "intent": "extract_questions"!
        
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
    var parsedStartId: Int? = null
    var parsedEndId: Int? = null
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
        val maxPossibleId = if (mode == AiChatMode.READ_BOOK) allQuestions.size.coerceAtLeast(1) else (allQuestions.maxOfOrNull { it.id.toInt() } ?: 1)
        val minPossibleId = if (mode == AiChatMode.READ_BOOK) 1 else (allQuestions.minOfOrNull { it.id.toInt() } ?: 1)
        
        parsedStartId = parsed["startId"]?.jsonPrimitive?.intOrNull
        parsedEndId = parsed["endId"]?.jsonPrimitive?.intOrNull
        startId = parsedStartId ?: minPossibleId
        endId = parsedEndId ?: maxPossibleId
        isExpansion = parsed["isExpansion"]?.jsonPrimitive?.booleanOrNull ?: false
        actionText = parsed["action"]?.jsonPrimitive?.content ?: "Processing"
        
        if (hasAttachedFile && intent != "extract_questions") {
            val cmdLower = command.lowercase()
            val isOmission = isHeaderOmissionRequested(command)
            if (isOmission || cmdLower.contains("extract") || cmdLower.contains("omit") || cmdLower.contains("header") || 
                cmdLower.contains("null") || cmdLower.contains("parse") || cmdLower.contains("pdf") || 
                cmdLower.contains("document") || cmdLower.contains("file") || cmdLower.contains("question") ||
                cmdLower.contains("import") || allQuestions.isEmpty()) {
                intent = "extract_questions"
            }
        }
        
        if (intent == "batch_edit") {
            val batchActions = parsed["batchActions"]?.jsonArray
            if (batchActions != null) {
                val updates = mutableListOf<PendingQuestionUpdate>()
                for (action in batchActions) {
                    val actObj = action.jsonObject
                    val field = actObj["field"]?.jsonPrimitive?.content ?: continue
                    val value = actObj["value"]?.jsonPrimitive?.content ?: ""
                    val actStart = actObj["startId"]?.jsonPrimitive?.intOrNull
                    val actEnd = actObj["endId"]?.jsonPrimitive?.intOrNull
                    
                    val targetedPairs = resolveTargetQuestionsForAi(
                        allQuestions = allQuestions,
                        mode = mode,
                        command = command,
                        requiredLocalNumbers = emptyList(),
                        parsedStartId = actStart ?: minPossibleId,
                        parsedEndId = actEnd ?: maxPossibleId,
                        currentContextInfo = currentContextInfo
                    )
                    val targeted = if (targetedPairs.isNotEmpty()) {
                        targetedPairs.map { it.second }
                    } else {
                        allQuestions
                    }
                    val firstGlobalId = targeted.firstOrNull()?.id ?: 1L
                    val lastGlobalId = targeted.lastOrNull()?.id ?: firstGlobalId

                    for (q in targeted) {
                        val existingIdx = updates.indexOfFirst { it.originalId == q.id }
                        val baseQ = if (existingIdx != -1) updates[existingIdx].updatedQuestion else q
                        val modifiedQ = when (field) {
                            "questionHeader" -> {
                                val headerVal = value.trim()
                                val headerJson = if (headerVal.startsWith("{")) headerVal else "{\"startId\": $firstGlobalId, \"endId\": $lastGlobalId, \"text\": \"${headerVal.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\"}"
                                baseQ.copy(questionHeader = headerJson, section = headerVal)
                            }
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
            val targeted = resolveTargetQuestionsForAi(
                allQuestions = allQuestions,
                mode = mode,
                command = command,
                requiredLocalNumbers = requiredLocalNumbers,
                parsedStartId = parsedStartId,
                parsedEndId = parsedEndId,
                currentContextInfo = currentContextInfo
            ).map { it.second }
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
    
    onStatusUpdate(if (intent == "chat") "Responding..." else actionText, 0f)
    

    if (intent == "extract_questions") {
        if (pdfBytes == null && extractedText == null && attachments.isEmpty()) {
            return Pair(emptyList(), "You asked to extract questions from a document, but no file was attached. Please tap the '+' button to attach a file (PDF, TXT, ZIP, or Image).")
        }
        onStatusUpdate("Extracting questions via AI...", 0.3f)
        
        val sourceText = if (extractedText != null) "extracted text document:\n$extractedText\n\n" else "attached PDF/Image."
        val shouldOmitHeaders = isHeaderOmissionRequested(command)

        val headerExtractionRule = if (shouldOmitHeaders) {
            """
            8. **CRITICAL USER OVERRIDE (HIGHEST PRIORITY) - OMIT ALL HEADERS / SET HEADERS TO NULL**:
               - The user explicitly commanded: "$command".
               - You MUST set "questionHeader": "" and "section": "" for ALL questions extracted!
               - DO NOT analyze, extract, copy, or synthesize any question headers, instructions, directions, reading passages, or section headings from the document into 'questionHeader' or 'section'.
               - Leave both 'questionHeader': "" and 'section': "" as empty strings "" for every single question!
            """.trimIndent()
        } else {
            """
            8. **Question Header Extraction & Passage `<p>` Tag**:
               - Documents sometimes contain question headers, which are instructions, directions, reading passages, or guidelines applying to a number of questions (e.g. 'Choose one word Substitution (for question from 1 to 10)', 'Read Passages And Answer Questions (1 -10)', 'Directions (Q. 1 to 5): Read the following passage and answer the questions that follow: ...').
               - When present, extract these instructions/headers into the 'questionHeader' field.
               - For reading comprehension passages or stories, wrap the passage content inside `<p>` or `<p name="Passage Title">` tags (e.g. `Directions (Q. 1 to 5): Read the passage:\n<p name="Passage Title">Passage body text here...</p>`).
               - If an instruction, passage, or direction applies to a group/range of questions, set that EXACT instruction text in the 'questionHeader' field for ALL questions belonging to that group/range.
               - DO NOT merge or append the question header instructions into the 'question' text; keep the 'question' field clean and dedicated to the individual question.
               - If a question has no preceding instruction or group header, set 'questionHeader' to "".
            """.trimIndent()
        }

        val prompt = """
            You are extracting questions from the $sourceText based on the user's instructions.
            User's Instructions: "$command"
            
            Rules for Extraction:
            0. **CRITICAL MANDATE - USER COMMAND OVERRIDES DEFAULT RULES (HIGHEST PRIORITY)**:
               - The user's explicit instructions above in 'User's Instructions: "$command"' STRICTLY OVERRIDE ANY built-in rule below!
               ${if (shouldOmitHeaders) "- MANDATORY: USER COMMANDED NO HEADERS / SET HEADERS TO NULL. Set \"questionHeader\": \"\" and \"section\": \"\" for ALL questions. DO NOT analyze or extract headers from the document." else ""}
               - If the user instructs to omit explanations, format differently, or customize any field, follow the user's instructions completely.
            1. By default, extract ONLY: questions (including text, tables, images if present), options, answers, path, question header, exams, and explanation from the source itself.
            2. DO NOT generate AI explanations or extraOptions UNLESS the user explicitly asked for them in their instructions.
            3. If the user provided specific instructions for the path, follow them.
            4. **Table Format**: If the question or data involves a table (grid lines or simple listing), extract the table data and set 'format' to "table". Construct 'tableData' as a JSON string representation of a 2D array of strings (e.g. '[["Col1", "Col2"], ["Val1", "Val2"]]'). DO NOT put the table data in the 'question' text; keep the 'question' text clean.
            5. **Images from Source**: If the user provides a ZIP file with images, or explicitly references extracting existing images, map the image filenames provided in the System Note to the appropriate fields (e.g. 'img', 'explanationImageOptions'). Follow the user's exact instructions for how to map them.
            6. **AI/Web Images**: If the user explicitly asks for "AI generated explanation image", generate 3 different highly detailed image generation prompts based on the context. Return the prompts as a JSON string array in a field called 'explanationImagePrompts'. If asked for "embedding img from web" or "web image", return 3 relevant image URLs using this format: "https://loremflickr.com/800/600/keyword" (replace 'keyword' with a specific single word describing the context) in a field called 'explanationImageOptions'.
            7. **Math & Style (STRICT MANDATE)**: Wrap mathematical expressions, equations, formulas, and math variables in single dollar signs `${"$"}` (e.g., `${"$"}E = mc^2${"$"}` or `${"$"}x=1${"$"}`), and block equations in double dollar signs `${"$$"}`. NEVER output bare LaTeX like `\frac{1}{2}` without dollar signs. **CRITICAL: NEVER wrap plain text words, vocabulary words, sentences, HTML tags (like `<strong>word</strong>`), or line breaks (`\n`) inside dollar signs `${"$"}`!** Use standard `\n` (single backslash) for line breaks in JSON strings, and double-escape only LaTeX command backslashes (e.g. `\\frac{1}{2}`, `\\times`, `\\theta`).
            **CRITICAL JSON ESCAPING RULE**: You are generating a JSON string. You MUST properly double-escape all backslashes in LaTeX formulas so they do not break JSON parsing (e.g. output `\\frac{1}{2}`, `\\times`, `\\theta`, `\\beta`, `\\neq`). In valid JSON source, two backslashes represent one literal LaTeX backslash. DO NOT output quad backslashes.
            $headerExtractionRule
            9. Extract ALL questions from the document. Take as much space as you need.
            10. Format the output strictly as a JSON array containing objects matching this schema:
            [
              {
                "question": "question text",
                "options": ["A", "B", "C", "D"],
                "extraOptions": ["E", "F"] (ONLY IF REQUESTED),
                "correctIndex": integer index of correct option,
                "explanation": "Brief or ai generated explanation (empty string \"\" if none/not requested)",
                "detailedExplanation": "Explanation explicitly extracted from the book (empty string \"\" if none)",
                "path": "Topic/Subtopic based on PDF headers or user instructions (empty string \"\" if none)",
                "format": "text or table",
                "tableData": "JSON string of 2D array (empty string \"\" if not table)",
                "explanationImagePrompts": ["prompt1", "prompt2"] (ONLY IF REQUESTED),
                "explanationImageOptions": ["url1", "url2"] (ONLY IF REQUESTED),
                "questionHeader": ${if (shouldOmitHeaders) "\"\" (User commanded to omit headers - MUST be empty string \"\")" else "\"Instruction / header / passage applying to this question or group. Wrap reading passages in <p>...</p> tags. Empty string \\\"\\\" if none.\""},
                "section": ${if (shouldOmitHeaders) "\"\"" else "\"question header if present, else \\\"\\\"\""},
                "exam": "exam tags if present (e.g. 'SSC CGL 2023'), else \"\"",
                "year": "exam year if present, else \"\""
              }
            ]
            11. **NO NULL VALUES (CRITICAL)**: For any parameter with no value or empty content (such as exam, year, explanation, detailedExplanation, questionHeader, section, tableData, etc.), ALWAYS output an empty string "" (or empty list []). NEVER output null or "null"!
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
            
            val cleanResult = com.example.data.DataParser.autoFixJson(result)
            val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(cleanResult).jsonArray
            val gson = com.example.data.SharedGson.normal
            val baseBookId = allQuestions.firstOrNull()?.bookId ?: "ExtractedBook"
            val maxPossibleId = allQuestions.maxOfOrNull { it.id } ?: 0L
            
            for (item in jsonArray) {
                val obj = item.jsonObject
                val rawOptions = obj["options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val qOptions = rawOptions.map { cleanExtractedMathText(it) }
                val rawExtraOptions = obj["extraOptions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val qExtraOptions = rawExtraOptions.map { cleanExtractedMathText(it) }
                
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
                val rawHeader = when (val el = obj["questionHeader"] ?: obj["section"] ?: obj["header"]) {
                    is kotlinx.serialization.json.JsonObject -> el.toString()
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        if (el !is kotlinx.serialization.json.JsonNull) {
                            val c = el.content.trim()
                            if (c.isNotBlank() && !c.equals("null", ignoreCase = true) && !c.equals("undefined", ignoreCase = true) && !c.equals("none", ignoreCase = true)) c else null
                        } else null
                    }
                    else -> null
                }
                val rawQuestion = obj.optCleanString("question") ?: ""
                val questionText = cleanExtractedMathText(rawQuestion)
                val rawExplanation = obj.optCleanString("explanation") ?: ""
                val explanation = cleanExtractedMathText(rawExplanation)
                val rawDetailed = obj.optCleanString("detailedExplanation")
                val detailedExplanation = rawDetailed?.let { cleanExtractedMathText(it) }?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                val shouldOmit = shouldOmitHeaders || isHeaderOmissionRequested(command)
                val cleanedHeader = if (shouldOmit) null else rawHeader?.let { cleanExtractedMathText(it) }?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                val exam = obj.optCleanString("exam") ?: ""
                val year = obj.optCleanString("year") ?: ""
                val format = obj.optCleanString("format")?.takeIf { it.equals("table", ignoreCase = true) } ?: "text"
                val tableData = obj.optCleanString("tableData")?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                val bottomText = obj.optCleanString("bottomText")?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                val exams = obj.optCleanString("exams")?.takeIf { it.isNotBlank() && it != "[]" && !it.equals("null", ignoreCase = true) }
                val section = if (shouldOmit) "" else (cleanedHeader ?: obj.optCleanString("section") ?: "")
                val path = obj.optCleanString("path") ?: ""

                val updatedQ = com.example.data.QuestionEntity(
                    globalId = java.util.UUID.randomUUID().toString(),
                    id = maxPossibleId + 1 + updates.size.toLong(),
                    bookId = baseBookId,
                    category = "AI Extracted",
                    question = questionText,
                    options = gson.toJson(qOptions),
                    advancedOptions = gson.toJson(generatedOptions1),
                    advancedCorrectIds = gson.toJson(listOfNotNull(generatedOptions1.getOrNull(cIndex1)?.id)),
                    extraOptions = gson.toJson(qExtraOptions),
                    correctIndex = cIndex1,
                    explanation = explanation,
                    detailedExplanation = detailedExplanation,
                    format = format,
                    tableData = tableData,
                    bottomText = bottomText,
                    section = section,
                    exam = exam,
                    exams = exams,
                    year = year,
                    path = path,
                    questionHeader = cleanedHeader
                )
                updates.add(PendingQuestionUpdate(
                    originalId = -1L, 
                    updatedQuestion = updatedQ,
                    imageOptions = imgPrompts.takeIf { it.isNotEmpty() } ?: imgOptions
                ))
            }
            
            val shouldOmit = shouldOmitHeaders || isHeaderOmissionRequested(command)

            val processedUpdates = if (shouldOmit) {
                updates.map { pending ->
                    val q = pending.updatedQuestion
                    pending.copy(updatedQuestion = q.copy(questionHeader = null, section = ""))
                }
            } else {
                processExtractedQuestionHeaders(updates)
            }
            if (processedUpdates.isNotEmpty()) {
                return Pair(processedUpdates, "I extracted ${processedUpdates.size} questions from the document. Please review the pending updates above.")
            } else {
                return Pair(emptyList(), "I couldn't find or generate any questions from the document.")
            }
        } catch (e: Exception) {
            onDevLog("[JSON PARSE ERROR] ${e.message}\nRAW:\n$rawResult")
            return Pair(emptyList(), "The AI returned invalid JSON. Check Developer Logs for details.")
        }
    }

    if (intent == "chat") {
        val recentHistory = chatHistory.takeLast(10).map { "${if (it.role == "user") "User" else "AI"}: ${it.content}" }.joinToString("\n")

        val targetedForChat = if (requiresQuestions) {
            resolveTargetQuestionsForAi(
                allQuestions = allQuestions,
                mode = mode,
                command = command,
                requiredLocalNumbers = requiredLocalNumbers,
                parsedStartId = parsedStartId,
                parsedEndId = parsedEndId,
                currentContextInfo = currentContextInfo
            )
        } else {
            emptyList()
        }

        val contextContext = when {
            !requiresQuestions -> {
                "Book Questions: (Not required for this general query)"
            }
            targetedForChat.isNotEmpty() -> {
                val formattedTargets = targetedForChat.map { (localNum, q) ->
                    formatQuestionForAiPrompt(localNum, q)
                }
                "Target Question(s) Requested by User (Exact Match — Explain/Use ONLY these question(s)):\n" +
                    gson.toJson(formattedTargets)
            }
            else -> {
                val allFormatted = allQuestions.mapIndexed { idx, q ->
                    formatQuestionForAiPrompt(idx + 1, q)
                }
                "All Questions in Current Topic/Context (Each has 'localQuestionNumber' Q.1..Q.${allQuestions.size} and 'bookQuestionId'):\n" +
                    gson.toJson(allFormatted)
            }
        }
        val contextInfoString = if (currentContextInfo.isNotBlank()) "Current User Context:\n$currentContextInfo" else ""
        
        val chatPrompt = """
            You are Sparkle, an intelligent, inspiring educational study companion and co-pilot (similar to the Gemini app).
            You are versatile and knowledgeable: answer any question the user asks, whether it is about the book, curriculum, science, history, coding, logic, or casual conversation.
            
            $contextInfoString
            
            $contextContext
            
            Recent Chat History:
            $recentHistory
            
            User: $command
            
            GUIDELINES FOR BEAUTIFUL EXPLANATIONS:
            - Speak directly to the user in second person ("you"). NEVER output robotic third-person meta-sentences like "The user is currently viewing question ID...".
            - If 'Target Question(s) Requested by User' is provided above, explain that EXACT question (referring to it naturally as **Q.<localNumber>**, e.g. **Q.99**). Never substitute a different question!
            - When explaining a question, concept, or topic:
              1. Structure the explanation beautifully with clear sections:
                 • **Quick Answer / Core Concept**: A clear, concise statement of the correct option (e.g. Option B) and the core principle.
                 • **Step-by-Step Breakdown**: Walk through the reasoning clearly. Explain why the correct option is right, and why the other options (Option A, C, D, etc.) are incorrect or distractors.
                 • **Key Takeaways & Memory Tips**: An intuitive summary, practical analogy, or mnemonic to help remember the concept.
              2. Always refer to options with letter designations (Option A, Option B, Option C, Option D).
              3. If a question in the book has an incorrect answer key or typo, clarify what the correct answer should be.
            - ILLUSTRATIONS & VISUAL AIDS (SMART & SELECTIVE RELEVANCE):
              First, evaluate whether the question or concept genuinely benefits from a visual illustration:
              • STRICT RULE: DO NOT include an illustration for vocabulary words, word meanings, synonyms, antonyms, idioms, grammar rules, spelling, parts of speech, abstract words, or linguistic concepts (e.g. words like "amble", "abate", "benevolent", "metaphor", "noun", "clause"). A visual illustration for vocabulary or word meaning is NOT relevant, out of context, and distracting.
              • STRICT RULE: DO NOT include an illustration for pure math calculations, formulas, or logic problems without physical or geometric figures.
              • DO include an illustration ONLY when a visual aid genuinely helps the user understand or visualize the concept:
                - Scientific diagrams, cycles, or processes (e.g., "seismic waves diagram", "nitrogen cycle", "human heart anatomy")
                - Concrete physical objects, laboratory instruments, or specialized equipment (e.g., "stethoscope", "aquarium", "pendulum")
                - Biological organisms, animals, plants, or cellular structures (e.g., "amoeba", "mitochondria", "platypus")
                - Geographic maps, astronomical phenomena, or architectural monuments
              • If an illustration is genuinely helpful, output:
                [WEB_IMAGE: query="<concise subject with descriptive keyword e.g. 'seismic waves diagram' or 'stethoscope instrument'>", source="<wikipedia | commons | web>", caption="<friendly caption>"]
                Choose the source that best suits the topic:
                • "wikipedia": Best for encyclopedic subjects, concrete objects, animals, places, history, and science.
                • "commons": Best for scientific diagrams, schematics, cycles, animated GIFs, and educational short videos.
                • "web": Best for real-world photos, charts, and modern examples.
              • If the question is about word meanings, definitions, grammar, or does not need a visual, DO NOT include any [WEB_IMAGE] tag.
            - Math & formulas: format equations cleanly with standard notation (${'$'}`formula`${'$'}).
            - Use Markdown (bold, bullet points, headers, tables, code blocks) to make the text clean, scannable, and engaging. Keep the tone encouraging, clear, and friendly.
        """.trimIndent()
        
        onStatusUpdate("Responding...", 0.25f)
        val chatResponse = GeminiHelper.generateContent(context, chatPrompt, pdfBytes = pdfBytes, attachments = attachments, onDevLog = onDevLog)
        
        var finalResponse = chatResponse

        // 1. Process WEB_IMAGE tags for Web & Wikipedia illustrations (AI source selection + live status + verification)
        val webImageRegex = Regex("\\[WEB_IMAGE:(.*?)\\]", RegexOption.IGNORE_CASE)
        val webMatches = webImageRegex.findAll(chatResponse).toList()
        for (match in webMatches) {
            val rawContent = match.groupValues[1].trim()
            var query = rawContent
            var source: String? = null
            var caption: String? = null

            val queryAttr = Regex("query\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(rawContent)?.groupValues?.get(1)
            val sourceAttr = Regex("source\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(rawContent)?.groupValues?.get(1)
            val captionAttr = Regex("caption\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(rawContent)?.groupValues?.get(1)
            if (queryAttr != null) {
                query = queryAttr.trim()
                source = sourceAttr?.trim()
                caption = captionAttr?.trim()
            } else if (rawContent.contains(",")) {
                val parts = rawContent.split(",", limit = 3)
                query = parts[0].trim().removeSurrounding("\"").removeSurrounding("'")
                caption = parts.getOrNull(1)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
                source = parts.getOrNull(2)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
            } else {
                query = rawContent.removeSurrounding("\"").removeSurrounding("'")
            }

            val isExplicitVisualReq = command.contains("video", ignoreCase = true) ||
                    command.contains("gif", ignoreCase = true) ||
                    command.contains("animation", ignoreCase = true) ||
                    command.contains("diagram", ignoreCase = true) ||
                    command.contains("illustrat", ignoreCase = true) ||
                    rawContent.contains("video", ignoreCase = true) ||
                    rawContent.contains("gif", ignoreCase = true)
            val conceptContext = "$command | $currentContextInfo | $query ${caption ?: ""}".trim()
            if (!isExplicitVisualReq && !com.example.data.WikipediaImageHelper.isConceptSuitableForIllustration(query, conceptContext)) {
                onDevLog?.invoke("Skipping illustration: '$query' is vocabulary or abstract concept not suitable for illustration.")
                finalResponse = finalResponse.replace(match.value, "")
                continue
            }

            val illustration = com.example.data.WikipediaImageHelper.searchIllustrationWithAiValidation(
                context = context,
                query = query,
                preferredSource = source,
                customCaption = caption,
                conceptContext = conceptContext,
                onStatusUpdate = onStatusUpdate,
                onDevLog = onDevLog
            )
            if (illustration != null) {
                val eUrl = java.net.URLEncoder.encode(illustration.imageUrl, "UTF-8")
                val eTitle = java.net.URLEncoder.encode(illustration.title, "UTF-8")
                val eCap = java.net.URLEncoder.encode(illustration.caption, "UTF-8")
                val eSrc = java.net.URLEncoder.encode(illustration.sourceUrl, "UTF-8")
                val eProv = java.net.URLEncoder.encode(illustration.provider, "UTF-8")
                val eType = java.net.URLEncoder.encode(illustration.mediaType, "UTF-8")
                val eVid = java.net.URLEncoder.encode(illustration.videoUrl ?: "", "UTF-8")
                finalResponse = finalResponse.replace(
                    match.value,
                    "\n\n[WEB_ILLUSTRATION:url=$eUrl&title=$eTitle&caption=$eCap&source=$eSrc&provider=$eProv&type=$eType&video=$eVid]\n\n"
                )
            } else if (isExplicitVisualReq) {
                val isVidReq = command.contains("video", true) || rawContent.contains("video", true)
                val isGifReq = command.contains("gif", true) || rawContent.contains("gif", true) || command.contains("animation", true)
                val mType = if (isVidReq) "video" else if (isGifReq) "gif" else "image"
                val mTitle = if (isVidReq) "Educational Video: $query" else if (isGifReq) "Animation (GIF): $query" else "Illustration: $query"
                val mSource = "https://commons.wikimedia.org/w/index.php?search=" + java.net.URLEncoder.encode(query, "UTF-8")
                val eUrl = ""
                val eTitle = java.net.URLEncoder.encode(mTitle, "UTF-8")
                val eCap = java.net.URLEncoder.encode(caption ?: "Educational $mType for $query", "UTF-8")
                val eSrc = java.net.URLEncoder.encode(mSource, "UTF-8")
                val eProv = java.net.URLEncoder.encode("Wikimedia Commons", "UTF-8")
                val eType = java.net.URLEncoder.encode(mType, "UTF-8")
                val eVid = java.net.URLEncoder.encode(mSource, "UTF-8")
                finalResponse = finalResponse.replace(
                    match.value,
                    "\n\n[WEB_ILLUSTRATION:url=$eUrl&title=$eTitle&caption=$eCap&source=$eSrc&provider=$eProv&type=$eType&video=$eVid]\n\n"
                )
            } else {
                // If illustration is not found or not suitable, omit completely
                finalResponse = finalResponse.replace(match.value, "")
            }
        }

        // 2. Process [AI_IMAGE: ...] tags
        val imagePromptRegex = Regex("\\[AI_IMAGE:(.*?)\\]")
        val aiMatches = imagePromptRegex.findAll(finalResponse).toList()
        for (match in aiMatches) {
            val imgPrompt = match.groupValues[1].trim()
            val imgUrl = com.example.data.ImageGenerationHelper.generateImage(context, imgPrompt)
            if (imgUrl != null) {
                finalResponse = finalResponse.replace(match.value, "[IMG_URL:$imgUrl]")
            } else {
                finalResponse = finalResponse.replace(match.value, "")
            }
        }

        // 3. Fallback: only if user explicitly asked for a diagram/visual illustration and no image was output yet
        val userExplicitlyAskedForVisual = command.contains("illustrat", ignoreCase = true) ||
                command.contains("diagram", ignoreCase = true) ||
                command.contains("with picture", ignoreCase = true) ||
                command.contains("with image", ignoreCase = true) ||
                command.contains("chart", ignoreCase = true) ||
                command.contains("video", ignoreCase = true) ||
                command.contains("gif", ignoreCase = true)

        if (userExplicitlyAskedForVisual && !finalResponse.contains("[WEB_ILLUSTRATION:") && !finalResponse.contains("[IMG_URL:")) {
            var subjectCandidate = ""
            val targetQ = targetedForChat.firstOrNull()?.second
                ?: resolveTargetQuestionsForAi(
                    allQuestions = allQuestions,
                    mode = mode,
                    command = command,
                    requiredLocalNumbers = requiredLocalNumbers,
                    parsedStartId = parsedStartId,
                    parsedEndId = parsedEndId,
                    currentContextInfo = currentContextInfo
                ).firstOrNull()?.second
            if (targetQ != null) {
                val correctOpt = try {
                    val arr = org.json.JSONArray(targetQ.options)
                    if (targetQ.correctIndex in 0 until arr.length()) arr.getString(targetQ.correctIndex) else ""
                } catch (e: Exception) { "" }
                subjectCandidate = correctOpt.ifBlank { targetQ.question }
            }
            if (subjectCandidate.isBlank() && currentContextInfo.contains("viewing the topic/path: '")) {
                subjectCandidate = currentContextInfo.substringAfter("viewing the topic/path: '").substringBefore("'")
            }
            if (subjectCandidate.isBlank()) {
                subjectCandidate = command.replace(Regex("explain|with|illustration|diagram|picture|image|for|please|about|the|chart|video|gif", RegexOption.IGNORE_CASE), "").trim()
            }

            val cleanQuery = subjectCandidate
                .replace(Regex("<.*?>"), " ")
                .replace(Regex("[^a-zA-Z0-9 ]"), " ")
                .split(" ")
                .filter { it.length > 2 && it.lowercase() !in listOf("what", "which", "following", "about", "statement", "correct", "incorrect", "option", "question", "where") }
                .take(3)
                .joinToString(" ")

            if (cleanQuery.isNotBlank()) {
                val conceptContext = "$command | $currentContextInfo | $cleanQuery"
                val illustration = com.example.data.WikipediaImageHelper.searchIllustrationWithAiValidation(
                    context = context,
                    query = cleanQuery,
                    preferredSource = "auto",
                    customCaption = null,
                    conceptContext = conceptContext,
                    onStatusUpdate = onStatusUpdate,
                    onDevLog = onDevLog
                )
                if (illustration != null) {
                    val eUrl = java.net.URLEncoder.encode(illustration.imageUrl, "UTF-8")
                    val eTitle = java.net.URLEncoder.encode(illustration.title, "UTF-8")
                    val eCap = java.net.URLEncoder.encode(illustration.caption, "UTF-8")
                    val eSrc = java.net.URLEncoder.encode(illustration.sourceUrl, "UTF-8")
                    val eProv = java.net.URLEncoder.encode(illustration.provider, "UTF-8")
                    val eType = java.net.URLEncoder.encode(illustration.mediaType, "UTF-8")
                    val eVid = java.net.URLEncoder.encode(illustration.videoUrl ?: "", "UTF-8")
                    finalResponse += "\n\n[WEB_ILLUSTRATION:url=$eUrl&title=$eTitle&caption=$eCap&source=$eSrc&provider=$eProv&type=$eType&video=$eVid]"
                }
            }
        }
        
        onStatusUpdate("Finalizing response...", 0.98f)
        
        return Pair(emptyList(), finalResponse)
    }
    
    // EDIT INTENT
    val resolvedEditTargets = resolveTargetQuestionsForAi(
        allQuestions = allQuestions,
        mode = mode,
        command = command,
        requiredLocalNumbers = requiredLocalNumbers,
        parsedStartId = parsedStartId,
        parsedEndId = parsedEndId,
        currentContextInfo = currentContextInfo
    ).map { it.second }

    val targetQuestions = if (resolvedEditTargets.isNotEmpty()) {
        resolvedEditTargets
    } else if (parsedStartId == null && parsedEndId == null && requiredLocalNumbers.isEmpty()) {
        allQuestions
    } else {
        emptyList()
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
            **CRITICAL JSON ESCAPING RULE**: You are generating a JSON string. You MUST properly double-escape all backslashes in LaTeX formulas so they do not break JSON parsing (e.g. output `\\frac{1}{2}`, `\\times`, `\\theta`, `\\beta`, `\\neq`). In valid JSON source, two backslashes represent one literal LaTeX backslash. DO NOT output quad backslashes.
            3. **Images**: If asked for "AI generated explanation image", generate 3 different highly detailed image generation prompts based on the context. Return the prompts as a JSON string array in a field called 'explanationImagePrompts'. If asked for "embedding img from web" or "web image", return 3 relevant image URLs using this format: "https://loremflickr.com/800/600/keyword" (replace 'keyword' with a specific single word describing the context) in a field called 'explanationImageOptions'.
            4. **Expansion (Single to Multiple)**: If asked to expand a question into multiple questions, you should return the original question PLUS the newly generated questions. For new questions, assign them "id": -1.
            5. **Table Format**: If asked to convert tabular data to 'table' format, set 'format' to "table", and construct 'tableData' as a JSON string representation of a 2D array of strings (e.g. '[["Col1", "Col2"], ["Val1", "Val2"]]'). Clean up the 'question' text to remove the table data.
            6. **Question Header**: If asked to edit the header or extract common context, use the 'questionHeader' field. To apply a header to a range of questions, format the 'questionHeader' string as a JSON object: '{"startId": 1, "endId": 5, "text": "Header text here"}'. When the header contains a reading passage or context story, wrap the passage text inside '<p>...</p>' or '<p name=\"Passage Title\">...</p>' tags (with directions placed before the tag). IMPORTANT: If editing an existing header, you MUST preserve its original startId and endId unless the user explicitly tells you to change them. Do not use plain text if it applies to multiple questions.
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
            
            val element = Json.parseToJsonElement(com.example.data.DataParser.autoFixJson(cleanResult))
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
                    question = obj.optCleanString("question")?.let { cleanExtractedMathText(it) } ?: originalQ?.question ?: "",
                    options = gson.toJson(parsedOptions),
                    advancedOptions = gson.toJson(generatedOptions2),
                    advancedCorrectIds = gson.toJson(listOfNotNull(generatedOptions2.getOrNull(cIndex2)?.id)),
                    extraOptions = gson.toJson(parsedExtraOptions),
                    correctIndex = cIndex2,
                    explanation = obj.optCleanString("explanation")?.let { cleanExtractedMathText(it) } ?: originalQ?.explanation?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                    detailedExplanation = obj.optCleanString("detailedExplanation")?.let { cleanExtractedMathText(it) }?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: originalQ?.detailedExplanation?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                    img = originalQ?.img,
                    explanationImage = originalQ?.explanationImage,
                    section = obj.optCleanString("section") ?: originalQ?.section?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                    exam = obj.optCleanString("exam") ?: originalQ?.exam?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                    year = obj.optCleanString("year") ?: originalQ?.year?.takeIf { !it.equals("null", ignoreCase = true) } ?: "",
                    format = obj.optCleanString("format") ?: originalQ?.format,
                    tableData = obj.optCleanString("tableData")?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: originalQ?.tableData?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                    questionHeader = obj["questionHeader"]?.let { 
                        if (it is kotlinx.serialization.json.JsonObject) it.toString() 
                        else {
                            val raw = it.jsonPrimitive.content.trim()
                            if (raw.startsWith("{")) {
                                raw
                            } else if (raw.isNotBlank() && !raw.equals("null", ignoreCase = true)) {
                                "{\"startId\": ${originalQ?.id ?: 1}, \"endId\": ${originalQ?.id ?: 1}, \"text\": \"${raw.replace("\"", "\\\"").replace("\n", "\\n")}\"}"
                            } else null
                        }
                    } ?: originalQ?.questionHeader?.takeIf { !it.equals("null", ignoreCase = true) },
                    path = obj.optCleanString("path") ?: originalQ?.path?.takeIf { !it.equals("null", ignoreCase = true) } ?: ""
                )

                pendingUpdates.add(PendingQuestionUpdate(originalQ?.id ?: -1L, updatedQ, imgOptions))
            }
        } catch (e: Exception) {
            // continue
        }
    }
    
    return Pair(pendingUpdates, "I've successfully processed the request.")
}

private sealed class MessageChunk {
    data class Text(val content: String) : MessageChunk()
    data class WebImage(
        val url: String,
        val title: String,
        val caption: String,
        val source: String,
        val provider: String,
        val mediaType: String = "image", // "image", "gif", "chart", "video"
        val videoUrl: String = ""
    ) : MessageChunk()
    data class SimpleImage(val url: String) : MessageChunk()
}

@Composable
fun ChatMessageContent(content: String, isUser: Boolean = false) {
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    if (isUser) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            com.example.ui.components.SmartRichText(
                text = content,
                color = textColor,
                fontSize = 16.sp
            )
        }
        return
    }

    val chunks = remember(content) {
        val list = mutableListOf<MessageChunk>()
        val webIllRegex = Regex("\\[WEB_ILLUSTRATION:(.*?)\\]")
        val imgUrlRegex = Regex("\\[IMG_URL:(.*?)\\]")
        val mdImageRegex = Regex("!\\[(.*?)\\]\\((https?://\\S+)\\)")

        data class TagMatch(val range: IntRange, val chunk: MessageChunk)
        val tagMatches = mutableListOf<TagMatch>()

        for (m in webIllRegex.findAll(content)) {
            val paramsStr = m.groupValues[1]
            var url = ""
            var title = ""
            var caption = ""
            var source = ""
            var provider = "Wikipedia"
            var mediaType = "image"
            var videoUrl = ""

            paramsStr.split("&").forEach { pair ->
                val kv = pair.split("=", limit = 2)
                if (kv.size == 2) {
                    val key = kv[0]
                    val value = try { URLDecoder.decode(kv[1], "UTF-8") } catch (e: Exception) { kv[1] }
                    when (key) {
                        "url" -> url = value
                        "title" -> title = value
                        "caption" -> caption = value
                        "source" -> source = value
                        "provider" -> provider = value
                        "type" -> mediaType = value
                        "video" -> videoUrl = value
                    }
                }
            }
            if (url.isNotBlank() || videoUrl.isNotBlank() || mediaType == "video" || mediaType == "gif") {
                tagMatches.add(TagMatch(m.range, MessageChunk.WebImage(url, title, caption, source, provider, mediaType, videoUrl)))
            }
        }

        for (m in imgUrlRegex.findAll(content)) {
            val url = m.groupValues[1].trim()
            if (url.isNotBlank()) {
                tagMatches.add(TagMatch(m.range, MessageChunk.SimpleImage(url)))
            }
        }

        val mdMediaLinkRegex = Regex("\\[(.*?)\\]\\((https?://\\S+?\\.(?:webm|mp4|ogv|mov|gif)(?:\\?\\S*)?)\\)", RegexOption.IGNORE_CASE)
        for (m in mdMediaLinkRegex.findAll(content)) {
            if (tagMatches.none { it.range.first <= m.range.first && it.range.last >= m.range.last }) {
                val title = m.groupValues[1].trim()
                val url = m.groupValues[2].trim()
                val lower = url.lowercase()
                val isVid = lower.endsWith(".webm") || lower.endsWith(".mp4") || lower.endsWith(".ogv") || lower.endsWith(".mov")
                val isGif = lower.endsWith(".gif") || lower.contains(".gif?")
                val mType = if (isVid) "video" else if (isGif) "gif" else "image"
                val displayImg = if (isVid) "" else url
                val videoUrl = if (isVid) url else ""
                tagMatches.add(TagMatch(m.range, MessageChunk.WebImage(displayImg, title = title.ifBlank { if (isVid) "Educational Video" else "Animation" }, caption = title, source = url, provider = if (isVid) "Video Link" else "GIF Animation", mediaType = mType, videoUrl = videoUrl)))
            }
        }

        for (m in mdImageRegex.findAll(content)) {
            if (tagMatches.none { it.range.first <= m.range.first && it.range.last >= m.range.last }) {
                val caption = m.groupValues[1].trim()
                val url = m.groupValues[2].trim()
                val lower = url.lowercase()
                val isVid = lower.endsWith(".webm") || lower.endsWith(".mp4") || lower.endsWith(".ogv") || lower.contains("/video/")
                val isG = lower.endsWith(".gif") || lower.contains(".gif?")
                val mediaType = if (isVid) "video" else if (isG) "gif" else if (lower.endsWith(".svg")) "chart" else "image"
                val videoUrl = if (isVid) url else ""
                val displayImg = if (isVid) "" else url
                tagMatches.add(TagMatch(m.range, MessageChunk.WebImage(displayImg, title = caption.ifBlank { if (isVid) "Short Video" else "Animation" }, caption = caption, source = url, provider = if (isVid) "Video" else "Web Media", mediaType = mediaType, videoUrl = videoUrl)))
            }
        }

        val standaloneMediaRegex = Regex("(?<!\\()(https?://\\S+?\\.(?:webm|mp4|ogv|mov|gif)(?:\\?\\S*)?)(?!\\))", RegexOption.IGNORE_CASE)
        for (m in standaloneMediaRegex.findAll(content)) {
            if (tagMatches.none { it.range.first <= m.range.first && it.range.last >= m.range.last }) {
                val url = m.groupValues[1].trim()
                val lower = url.lowercase()
                val isVid = lower.endsWith(".webm") || lower.endsWith(".mp4") || lower.endsWith(".ogv") || lower.endsWith(".mov")
                val isG = lower.endsWith(".gif") || lower.contains(".gif?")
                val mediaType = if (isVid) "video" else if (isG) "gif" else "image"
                val videoUrl = if (isVid) url else ""
                val displayImg = if (isVid) "" else url
                tagMatches.add(TagMatch(m.range, MessageChunk.WebImage(displayImg, title = if (isVid) "Short Video" else "Animation (GIF)", caption = "", source = url, provider = if (isVid) "Video" else "Animation", mediaType = mediaType, videoUrl = videoUrl)))
            }
        }

        tagMatches.sortBy { it.range.first }

        var cursor = 0
        for (tm in tagMatches) {
            if (tm.range.first > cursor) {
                val textPart = content.substring(cursor, tm.range.first)
                if (textPart.isNotBlank()) {
                    list.add(MessageChunk.Text(textPart))
                }
            }
            list.add(tm.chunk)
            cursor = tm.range.last + 1
        }
        if (cursor < content.length) {
            val remainingText = content.substring(cursor)
            if (remainingText.isNotBlank()) {
                list.add(MessageChunk.Text(remainingText))
            }
        }

        if (list.isEmpty() && content.isNotBlank()) {
            list.add(MessageChunk.Text(content))
        }
        list
    }

    androidx.compose.foundation.text.selection.SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            chunks.forEach { chunk ->
                when (chunk) {
                    is MessageChunk.Text -> {
                        com.example.ui.components.SmartRichText(
                            text = chunk.content,
                            color = textColor,
                            fontSize = 16.sp
                        )
                    }
                    is MessageChunk.WebImage -> {
                        WebIllustrationCard(
                            imageUrl = chunk.url,
                            title = chunk.title,
                            caption = chunk.caption,
                            sourceUrl = chunk.source,
                            provider = chunk.provider,
                            mediaType = chunk.mediaType,
                            videoUrl = chunk.videoUrl
                        )
                    }
                    is MessageChunk.SimpleImage -> {
                        WebIllustrationCard(
                            imageUrl = chunk.url,
                            title = "Generated Illustration",
                            caption = "",
                            sourceUrl = "",
                            provider = "AI Illustration",
                            mediaType = "image",
                            videoUrl = ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebIllustrationCard(
    imageUrl: String,
    title: String,
    caption: String,
    sourceUrl: String,
    provider: String,
    mediaType: String = "image",
    videoUrl: String = ""
) {
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }

    val isVideo = mediaType == "video" || videoUrl.isNotBlank() || imageUrl.endsWith(".webm") || imageUrl.endsWith(".mp4") || imageUrl.endsWith(".ogv") || imageUrl.endsWith(".mov")
    val isGif = mediaType == "gif" || imageUrl.contains(".gif", ignoreCase = true) || videoUrl.contains(".gif", ignoreCase = true)
    val effectiveVideoUrl = videoUrl.ifBlank { if (imageUrl.endsWith(".webm") || imageUrl.endsWith(".mp4") || imageUrl.endsWith(".ogv") || imageUrl.endsWith(".mov")) imageUrl else "" }
    val effectiveGifUrl = if (isGif) (imageUrl.ifBlank { videoUrl }) else imageUrl
    val effectiveMediaUrl = if (isGif) effectiveGifUrl else imageUrl

    // IMPORTANT: Videos and GIFs must NEVER be hidden or removed from the chat bubble!
    if (!isVideo && !isGif && loadFailed && imageUrl.isBlank() && title.isBlank() && caption.isBlank()) {
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var showDialog by remember { mutableStateOf(false) }

    val isDirectVideoFile = effectiveMediaUrl.endsWith(".webm") || effectiveMediaUrl.endsWith(".mp4") || effectiveMediaUrl.endsWith(".ogv") || effectiveMediaUrl.endsWith(".mov")
    val imageRequest = remember(effectiveMediaUrl) {
        if (effectiveMediaUrl.isNotBlank() && !isDirectVideoFile) {
            coil.request.ImageRequest.Builder(context)
                .data(effectiveMediaUrl)
                .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; QuizerEducationalApp/1.0; contact@quizer.app) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .crossfade(true)
                .build()
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Source & Attribution Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val (typeLabel, typeIcon) = when {
                    isVideo -> Pair("Short Video", Icons.Default.Videocam)
                    isGif -> Pair("Animation (GIF)", Icons.Default.Animation)
                    mediaType == "chart" -> Pair("Chart & Diagram", Icons.Default.BarChart)
                    else -> Pair(if (provider.isNotBlank()) provider else "Wikipedia", Icons.Default.Public)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = typeLabel,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (provider.isNotBlank() && mediaType != "image" && provider != typeLabel) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• $provider",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (sourceUrl.isNotBlank()) {
                    Surface(
                        onClick = {
                            try {
                                uriHandler.openUri(sourceUrl)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Source",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open Source Link",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Media Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 280.dp)
                    .background(Color.Black.copy(alpha = 0.04f))
                    .clickable { showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {
                    if (imageRequest != null && !loadFailed) {
                        coil.compose.SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = title.ifBlank { caption },
                            contentScale = ContentScale.Fit,
                            onError = { loadFailed = true },
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp)
                        )
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        // Sleek video fallback banner: NEVER removed or hidden!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = title.ifBlank { "Educational Video" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to play video",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (isGif) {
                    if (imageRequest != null && !loadFailed) {
                        coil.compose.SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = title.ifBlank { caption },
                            contentScale = ContentScale.Fit,
                            onError = { loadFailed = true },
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp)
                        )
                    } else {
                        // Animated GIF fallback container: NEVER removed or hidden!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Animation,
                                    contentDescription = "Animation GIF",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = title.ifBlank { "Animated Demonstration (GIF)" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tap to view animation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Regular Image or Chart
                    if (imageRequest != null && !loadFailed) {
                        coil.compose.SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = title.ifBlank { caption },
                            contentScale = ContentScale.Fit,
                            onError = { loadFailed = true },
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp)
                        )
                    } else if (title.isNotBlank() || caption.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Illustration",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Illustration Reference",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Action / Interaction Badge in bottom right
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    val badgeText = when {
                        isVideo -> "Play video"
                        isGif -> "GIF animation"
                        mediaType == "chart" -> "Zoom diagram"
                        else -> "Tap to zoom"
                    }
                    val badgeIcon = when {
                        isVideo -> Icons.Default.PlayArrow
                        isGif -> Icons.Default.Animation
                        else -> Icons.Default.ZoomIn
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = badgeText,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Description / Caption
            if (title.isNotBlank() || caption.isNotBlank()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (caption.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        FullMediaViewerDialog(
            imageUrl = imageUrl,
            title = title,
            caption = caption,
            mediaType = mediaType,
            videoUrl = videoUrl,
            sourceUrl = sourceUrl,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun FullMediaViewerDialog(
    imageUrl: String,
    title: String,
    caption: String,
    mediaType: String = "image",
    videoUrl: String = "",
    sourceUrl: String = "",
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        val effectiveDisplayUrl = if (mediaType == "gif") imageUrl.ifBlank { videoUrl } else imageUrl
        val imageRequest = remember(effectiveDisplayUrl) {
            if (effectiveDisplayUrl.isNotBlank()) {
                coil.request.ImageRequest.Builder(context)
                    .data(effectiveDisplayUrl)
                    .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; QuizerEducationalApp/1.0; contact@quizer.app) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .crossfade(true)
                    .build()
            } else {
                null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.ifBlank { if (mediaType == "video") "Short Video" else "Illustration" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sourceUrl.isNotBlank()) {
                        IconButton(onClick = {
                            try { uriHandler.openUri(sourceUrl) } catch (e: Exception) { e.printStackTrace() }
                        }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open Source",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            // Media View Area
            val effectiveVid = videoUrl.ifBlank { if (imageUrl.endsWith(".webm") || imageUrl.endsWith(".mp4") || imageUrl.endsWith(".ogv")) imageUrl else "" }
            if (mediaType == "video" || effectiveVid.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 70.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (effectiveVid.isNotBlank()) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        setVideoURI(android.net.Uri.parse(effectiveVid))
                                        val controller = android.widget.MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        val linkToOpen = effectiveVid.ifBlank { sourceUrl }
                        if (linkToOpen.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    try { uriHandler.openUri(linkToOpen) } catch (e: Exception) { e.printStackTrace() }
                                },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play in External Player / Browser")
                            }
                        }
                    }
                }
            } else {
                // Interactive Zoom & Pan Area for Images, GIFs, Charts, Graphs
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 70.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            // Bottom Caption
            if (caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

