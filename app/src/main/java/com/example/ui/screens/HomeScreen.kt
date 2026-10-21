package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onStartQuiz: () -> Unit,
    onNavigateStats: () -> Unit = {},
    onNavigateHistory: () -> Unit = {},
    onNavigateSearch: () -> Unit = {},
    onNavigateBookmarks: () -> Unit = {},
    onNavigateLearn: () -> Unit = {},
    onNavigateAnalysis: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateReadBook: () -> Unit = {}
) {
    val books by viewModel.books.collectAsState()
    val currentBookId by viewModel.currentBookId.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val allQuestions by viewModel.allBookQuestionSummaries.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val questionLimit by viewModel.questionLimit.collectAsState()
    val stats by viewModel.allStats.collectAsState()

    var showBookDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var detailBook by remember { mutableStateOf<com.example.data.BookEntity?>(null) }
    
    val allCategories = allQuestions.map { it.category }.distinct()
    
    val sequenceOffset by viewModel.sequenceOffset.collectAsState()
    var mode by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getQuizMode(currentBookId)) }
    var showExamConfigDialog by remember { mutableStateOf(false) }
    val flowMode by viewModel.quizFlowMode.collectAsState()
    var questionTimer by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getQuestionTimerSeconds(currentBookId)) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val fixRequiredMessage by viewModel.fixRequiredMessage.collectAsState()

    var showUpdateDialog by remember { mutableStateOf<Pair<android.net.Uri, com.example.data.RawBookIdentity>?>(null) }
    var showReplaceDialog by remember { mutableStateOf<Pair<android.net.Uri, com.example.data.RawBookIdentity>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val identity: com.example.data.RawBookIdentity? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.DataParser.peekIdentity(context, uri)
                }
                if (identity != null) {
                    val existing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        viewModel.repository.getBookSync(identity.id ?: "")
                    }
                    if (existing != null && identity.dataVariable != "UPDATE") {
                        showReplaceDialog = Pair(uri, identity)
                    } else if (identity.dataVariable == "UPDATE") {
                        showUpdateDialog = Pair(uri, identity)
                    } else {
                        viewModel.addCustomBook(context, uri)
                    }
                } else {
                    viewModel.addCustomBook(context, uri)
                }
            }
        }
    }
    

    
    LaunchedEffect(Unit) {
        viewModel.syncBooks(context)
        viewModel.generateQuizQuestions()
    }

    if (showReplaceDialog != null) {
        val (uri, identity) = showReplaceDialog!!
        AlertDialog(
            onDismissRequest = { showReplaceDialog = null },
            title = { Text("Book Already Exists") },
            text = {
                Column {
                    Text("A book with ID '${identity.id}' already exists.")
                    Text("Name: ${identity.name}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Do you want to replace the existing book?", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val id = identity.id ?: ""
                    showReplaceDialog = null
                    viewModel.replaceBook(context, id, uri)
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = null }) { Text("Cancel") }
            }
        )
    }

    
    if (fixRequiredMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelFix() },
            title = { Text("Syntax Errors Found") },
            text = {
                Column {
                    Text("The imported book contains invalid syntax.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(fixRequiredMessage ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Do you want the app to automatically fix these errors and continue importing?")
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmFix(context) }) { Text("Fix & Import") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelFix() }) { Text("Cancel") }
            }
        )
    }

    if (showUpdateDialog != null) {
        val (uri, identity) = showUpdateDialog!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = null },
            title = { Text("Approve Update") },
            text = {
                Column {
                    Text("Target Book ID: ${identity.id ?: "Unknown"}")
                    Text("Update ID: ${identity.updateId ?: "Unknown"}")
                    Text("Update Details:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("• Version: ${identity.bookVersion ?: "N/A"}")
                    if (identity.updateVersion != null) {
                        Text("• Includes ${identity.updateVersion.size} updates arrays")
                    } else if (identity.questionIdStart != null && identity.questionIdEnd != null) {
                        Text("• Range: ${identity.questionIdStart} to ${identity.questionIdEnd}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Do you want to apply this update?", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    androidx.compose.material3.OutlinedButton(onClick = {
                        viewModel.addCustomBook(context, uri, isPermanentUpdate = true)
                        showUpdateDialog = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("Permanent Apply (No restore points)", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Button(onClick = {
                        viewModel.addCustomBook(context, uri, isPermanentUpdate = false)
                        showUpdateDialog = null
                    }, modifier = Modifier.fillMaxWidth()) { Text("Reversible Apply (Creates restore points)", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    TextButton(onClick = { showUpdateDialog = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    val currentDetailBook = detailBook
    if (currentDetailBook != null) {
        AlertDialog(
            onDismissRequest = { detailBook = null },
            title = { Text(currentDetailBook.name) },
            text = {
                Column {
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("ID: ${currentDetailBook.id}", modifier = Modifier.weight(1f))
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        IconButton(onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentDetailBook.id))
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy ID", modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("Version: ${currentDetailBook.bookVersion}")
                    Text("Questions: ${currentDetailBook.totalQuestions}")
                    Text("Categories: ${currentDetailBook.totalCategories}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Updates:", fontWeight = FontWeight.Bold)
                    
                    val updatesStr = currentDetailBook.updateVersions
                    val updateList = androidx.compose.runtime.remember(updatesStr) {
                        val gson = com.example.data.SharedGson.normal
                        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, List<Long>>>>() {}.type
                        val list: List<Map<String, List<Long>>> = gson.fromJson(updatesStr, type) ?: emptyList()
                        list
                    }
                    
                    if (updateList.isEmpty()) {
                        Text("No updates applied.")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(updateList) { upMap ->
                                upMap.forEach { (updateId, range) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(updateId, modifier = Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(onClick = {
                                                viewModel.makeUpdatePermanent(context, currentDetailBook.id, updateId)
                                                detailBook = null
                                            }) {
                                                Text("Make Permanent")
                                            }
                                            Button(onClick = {
                                                viewModel.reverseUpdate(currentDetailBook.id, updateId)
                                                detailBook = null
                                            }) {
                                                Text("Reverse")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detailBook = null }) { Text("Close") } }
        )
    }

    var showTrashDialog by remember { mutableStateOf(false) }
    if (showTrashDialog) {
        TrashDialog(onDismiss = { showTrashDialog = false })
    }

    LaunchedEffect(showBookDialog) {
        if (showBookDialog) {
            viewModel.syncBooks(context)
        }
    }

    if (showBookDialog) {
        var localBooks by remember { mutableStateOf(books) }
        var draggedBookKey by remember { mutableStateOf<String?>(null) }
        var settlingBookKey by remember { mutableStateOf<String?>(null) }
        var settleAnim by remember {
            mutableStateOf<androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>?>(null)
        }
        // Stationary viewport Y of the user's finger inside the outer Box wrapping LazyColumn
        var latestFingerViewportY by remember { mutableFloatStateOf(0f) }
        // Target visual top Y of the dragged card in LazyColumn viewport coordinates (px)
        var draggedVisualTop by remember { mutableFloatStateOf(0f) }
        // Finger offset from the top of the card at the moment drag started (px)
        var grabOffsetInCard by remember { mutableFloatStateOf(0f) }
        // Measured height of the dragged card (px)
        var draggedCardHeightPx by remember { mutableFloatStateOf(0f) }

        val listState = rememberLazyListState()
        val haptic = LocalHapticFeedback.current
        val dragCoroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current

        LaunchedEffect(books) {
            if (draggedBookKey == null && settlingBookKey == null) {
                localBooks = books
            }
        }

        fun startDrag(id: String) {
            settlingBookKey = null
            settleAnim = null
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
            val initialTop = itemInfo?.offset?.toFloat() ?: 0f
            val initialSize = itemInfo?.size?.toFloat() ?: with(density) { 76.dp.toPx() }

            draggedCardHeightPx = initialSize
            grabOffsetInCard = (latestFingerViewportY - initialTop).coerceIn(0f, initialSize)
            draggedVisualTop = initialTop
            draggedBookKey = id
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        fun handleDragToViewportY(fingerViewportY: Float) {
            val currentKey = draggedBookKey ?: return
            var currIdx = localBooks.indexOfFirst { it.id == currentKey }
            if (currIdx == -1) return

            val layoutInfo = listState.layoutInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .toFloat()
                .coerceAtLeast(1f)

            val currItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentKey }
            if (currItemInfo != null && currItemInfo.size > 0) {
                draggedCardHeightPx = currItemInfo.size.toFloat()
            }

            val contentBottom = layoutInfo.visibleItemsInfo
                .maxOfOrNull { it.offset + it.size }
                ?.toFloat() ?: viewportHeight
            val maxTop = (minOf(viewportHeight, contentBottom) - draggedCardHeightPx).coerceAtLeast(0f)

            // Position the dragged card 1:1 under the finger in stationary viewport coordinates
            draggedVisualTop = (fingerViewportY - grabOffsetInCard).coerceIn(0f, maxTop)
            val draggedCenterY = draggedVisualTop + (draggedCardHeightPx * 0.5f)

            // Compare draggedCenterY against physical slot positions in LazyColumn
            val visibleSlots = layoutInfo.visibleItemsInfo.sortedBy { it.index }
            var swapped = false

            while (currIdx < localBooks.lastIndex) {
                val nextSlot = visibleSlots.firstOrNull { it.index == currIdx + 1 } ?: break
                val nextThreshold = nextSlot.offset + (nextSlot.size * 0.52f)
                if (draggedCenterY > nextThreshold) {
                    val mutable = localBooks.toMutableList()
                    val moved = mutable.removeAt(currIdx)
                    mutable.add(currIdx + 1, moved)
                    localBooks = mutable
                    currIdx += 1
                    swapped = true
                } else {
                    break
                }
            }

            if (!swapped) {
                while (currIdx > 0) {
                    val prevSlot = visibleSlots.firstOrNull { it.index == currIdx - 1 } ?: break
                    val prevThreshold = prevSlot.offset + (prevSlot.size * 0.48f)
                    if (draggedCenterY < prevThreshold) {
                        val mutable = localBooks.toMutableList()
                        val moved = mutable.removeAt(currIdx)
                        mutable.add(currIdx - 1, moved)
                        localBooks = mutable
                        currIdx -= 1
                        swapped = true
                    } else {
                        break
                    }
                }
            }

            if (swapped) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            // Smooth auto-scroll when dragging near the top or bottom edge of a scrollable book list
            val edgeZonePx = with(density) { 36.dp.toPx() }
            val scrollStepPx = with(density) { 14.dp.toPx() }
            if (draggedVisualTop + draggedCardHeightPx > viewportHeight - edgeZonePx && listState.canScrollForward) {
                dragCoroutineScope.launch { listState.scrollBy(scrollStepPx) }
            } else if (draggedVisualTop < edgeZonePx && listState.canScrollBackward) {
                dragCoroutineScope.launch { listState.scrollBy(-scrollStepPx) }
            }
        }

        fun endDrag() {
            val key = draggedBookKey ?: return
            val targetIdx = localBooks.indexOfFirst { it.id == key }
            val targetSlotOffset = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == targetIdx }?.offset?.toFloat()
                ?: listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == key }?.offset?.toFloat()
                ?: draggedVisualTop
            val releaseDeltaY = draggedVisualTop - targetSlotOffset

            // Construct Animatable synchronously with releaseDeltaY so there is zero 1-frame snap on release
            val anim = androidx.compose.animation.core.Animatable(releaseDeltaY)
            settleAnim = anim
            draggedBookKey = null
            settlingBookKey = key

            val order = localBooks.map { it.id }
            viewModel.reorderBooks(order)

            dragCoroutineScope.launch {
                try {
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    )
                } finally {
                    if (settlingBookKey == key) {
                        settlingBookKey = null
                        settleAnim = null
                    }
                }
            }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                if (draggedBookKey != null) endDrag()
                showBookDialog = false
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 24.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Select Book", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            IconButton(onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                                showBookDialog = false
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Book")
                            }
                            IconButton(onClick = { showTrashDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Trash")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Outer stationary Box tracks pointer Y in fixed viewport coordinates on PointerEventPass.Initial
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 500.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                                        requireUnconsumed = false
                                    )
                                    latestFingerViewportY = down.position.y
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            latestFingerViewportY = change.position.y
                                            if (!change.pressed) {
                                                if (draggedBookKey != null) {
                                                    change.consume()
                                                    endDrag()
                                                }
                                                break
                                            }
                                            if (draggedBookKey != null) {
                                                change.consume()
                                                handleDragToViewportY(change.position.y)
                                            }
                                        }
                                    } finally {
                                        if (draggedBookKey != null) {
                                            endDrag()
                                        }
                                    }
                                }
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = draggedBookKey == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(localBooks, key = { it.id }) { book ->
                                val isSelected = book.id == currentBookId
                                val isDragging = book.id == draggedBookKey
                                val isSettling = book.id == settlingBookKey
                                val isElevated = isDragging || isSettling

                                val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (isDragging) 1.03f else 1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    ),
                                    label = "dragCardScale"
                                )

                                Card(
                                    colors = if (isSelected) {
                                        androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        androidx.compose.material3.CardDefaults.cardColors()
                                    },
                                    elevation = androidx.compose.material3.CardDefaults.cardElevation(
                                        defaultElevation = if (isElevated) 8.dp else 1.dp
                                    ),
                                    // CRITICAL: .zIndex and .animateItem MUST be placed BEFORE .padding(vertical = 4.dp)
                                    // so LazyColumn receives their ParentDataModifiers directly!
                                    modifier = Modifier
                                        .zIndex(if (isElevated) 10f else 1f)
                                        .animateItem(
                                            fadeInSpec = null,
                                            placementSpec = if (isElevated) {
                                                null
                                            } else {
                                                androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                                )
                                            },
                                            fadeOutSpec = null
                                        )
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .graphicsLayer {
                                            translationY = when {
                                                isDragging -> {
                                                    val currentLayoutOffset = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == book.id }?.offset?.toFloat()
                                                        ?: draggedVisualTop
                                                    draggedVisualTop - currentLayoutOffset
                                                }
                                                isSettling -> settleAnim?.value ?: 0f
                                                else -> 0f
                                            }
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                            shadowElevation = if (isElevated) 16f else 0f
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.toggleBookFavorite(book.id, !book.isFavorite) },
                                            enabled = draggedBookKey == null
                                        ) {
                                            Icon(
                                                imageVector = if (book.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "Favorite",
                                                tint = if (book.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .pointerInput(book.id) {
                                                    awaitEachGesture {
                                                        awaitFirstDown(requireUnconsumed = false)
                                                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                                        var isLong = false
                                                        val up = try {
                                                            withTimeout(longPressTimeout) {
                                                                waitForUpOrCancellation()
                                                            }
                                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                                            isLong = true
                                                            null
                                                        }

                                                        if (isLong) {
                                                            startDrag(book.id)
                                                        } else if (up != null && !up.isConsumed && draggedBookKey == null && settlingBookKey == null) {
                                                            viewModel.selectBook(book.id)
                                                            showBookDialog = false
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                book.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${book.totalQuestions} Questions",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { detailBook = book },
                                            enabled = draggedBookKey == null
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = "Details")
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteBook(context, book.id)
                                                android.widget.Toast.makeText(context, "Deleted ${book.name}", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            enabled = draggedBookKey == null
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            if (draggedBookKey != null) endDrag()
                            showBookDialog = false
                        }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    val topicTreeRoot by viewModel.topicTreeRoot.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()

    if (showExamConfigDialog) {
        com.example.ui.components.ExamConfigDialog(
            bookId = currentBookId,
            questionLimit = questionLimit ?: questions.size,
            settingsManager = viewModel.settingsManager,
            onDismiss = { showExamConfigDialog = false },
            onStart = { settings ->
                showExamConfigDialog = false
                viewModel.clearActiveQuizState()
                viewModel.currentExamSettings = settings
                viewModel.setOnlyBookmarks(false)
                viewModel.isLearnMode = false
                onStartQuiz()
            }
        )
    }

    if (showCategoryDialog) {
        var expandedPaths by remember(currentBookId) {
            mutableStateOf(viewModel.settingsManager.getHomeExpandedPaths(currentBookId))
        }
        
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Select Scope") },
            text = {
                if (topicTreeRoot == null) {
                    Text("Loading topics...")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("All Topics") },
                                leadingContent = {
                                    Checkbox(
                                        checked = selectedPaths.isEmpty(),
                                        onCheckedChange = { 
                                            // clear all selections to mean "all"
                                            if (it) {
                                                selectedPaths.forEach { p -> viewModel.togglePath(p) }
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    selectedPaths.forEach { p -> viewModel.togglePath(p) }
                                }
                            )
                            HorizontalDivider()
                        }
                        
                        val rootChildren = topicTreeRoot!!.children.values.toList()
                        val flatList = buildFlatList(rootChildren, 0, expandedPaths)
                        
                        items(flatList, key = { it.node.path }) { item ->
                            val isExpanded = expandedPaths.contains(item.node.path)
                            val isSelected = selectedPaths.any { item.node.path.startsWith(it) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.togglePath(item.node.path) }
                                    .padding(start = (item.level * 16).dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                if (item.node.children.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val updated = if (isExpanded) expandedPaths - item.node.path else expandedPaths + item.node.path
                                            expandedPaths = updated
                                            viewModel.settingsManager.setHomeExpandedPaths(currentBookId, updated)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand/Collapse"
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(36.dp))
                                }
                                
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.togglePath(item.node.path) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(item.node.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showCategoryDialog = false 
                    viewModel.generateQuizQuestions()
                }) { Text("Done") }
            }
        )
    }

    Scaffold(
        topBar = {
            val appTheme by viewModel.themeState.collectAsState()
            val topBarTheme = com.example.ui.theme.getAppColorScheme(
                darkTheme = true, 
                dynamicColor = true, 
                appTheme = appTheme
            )
            TopAppBar(
                title = { 
                    Column {
                        Text("Quizer", fontWeight = FontWeight.Bold)
                        Text("Practice • Analyze • Improve", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarTheme.primaryContainer,
                    titleContentColor = topBarTheme.onPrimaryContainer,
                    actionIconContentColor = topBarTheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val vStatus by viewModel.verificationStatus.collectAsState()
                val vMsg by viewModel.verificationMessage.collectAsState()
                
                
                if (vStatus != null) {
                    if (vStatus == "error" || vStatus == "success") {
                        AlertDialog(
                            onDismissRequest = { viewModel.clearVerificationStatus() },
                            title = { Text(if (vStatus == "error") "Validation Failed" else "Success") },
                            text = { Text(vMsg ?: "") },
                            confirmButton = {
                                TextButton(onClick = { viewModel.clearVerificationStatus() }) {
                                    Text("OK")
                                }
                            }
                        )
                    } else {
                        val color = MaterialTheme.colorScheme.primaryContainer
                        val textColor = MaterialTheme.colorScheme.onPrimaryContainer
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = color)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = textColor, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(vMsg ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showBookDialog = true },
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Current Book", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(books.find { it.id == currentBookId }?.name ?: "No book selected", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        
                        val bookStats = stats.filter { it.bookId == currentBookId }
                        val learnedCount = bookStats.count { com.example.ui.QuestionPoolProcessor.isQuestionMastered(it) }
                        val visitedCount = bookStats.count { it.attempts > 0 }
                        val unvisitedCount = maxOf(0, allQuestions.size - visitedCount)
                        val total = maxOf(1, allQuestions.size)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Book Progress", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                            Surface(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(maxOf(0.01f, learnedCount.toFloat() / total)).fillMaxHeight()) {}
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(maxOf(0.01f, (visitedCount - learnedCount).toFloat() / total)).fillMaxHeight()) {}
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(maxOf(0.01f, unvisitedCount.toFloat() / total)).fillMaxHeight()) {}
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Learned: $learnedCount", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            Text("Visited: $visitedCount", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            Text("Unvisited: $unvisitedCount", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Quiz Selection", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(onClick = { showCategoryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (selectedPaths.isEmpty()) "All Scopes Selected" else "${selectedPaths.size} Scopes Selected")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Question Count", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                            FilterChip(selected = questionLimit == 10, onClick = { viewModel.setQuestionLimit(10) }, label = { Text("10") })
                            FilterChip(selected = questionLimit == 25, onClick = { viewModel.setQuestionLimit(25) }, label = { Text("25") })
                            FilterChip(selected = questionLimit == 50, onClick = { viewModel.setQuestionLimit(50) }, label = { Text("50") })
                            FilterChip(selected = questionLimit == null, onClick = { viewModel.setQuestionLimit(null) }, label = { Text("All") })
                        }
                        Text("${questions.size} Questions matched", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        
                        if (questionLimit != null) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(selected = flowMode == "sequence", onClick = { viewModel.setQuizFlowMode("sequence") })
                                Text("Sequence", modifier = Modifier.padding(start = 0.dp, end = 8.dp))
                                
                                RadioButton(selected = flowMode == "progressive", onClick = { viewModel.setQuizFlowMode("progressive") })
                                Text("Progressive", modifier = Modifier.padding(start = 0.dp))
                            }
                            
                            if (flowMode == "sequence") {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)) {
                                    OutlinedTextField(
                                        value = sequenceOffset.toString(),
                                        onValueChange = { newVal -> val idx = newVal.toIntOrNull(); if (idx != null) viewModel.setSequenceOffset(idx) },
                                        modifier = Modifier.width(64.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    )
                                    Text(" : ", modifier = Modifier.padding(horizontal = 8.dp))
                                    OutlinedTextField(
                                        value = (sequenceOffset + (questionLimit ?: questions.size) - 1).toString(),
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier.width(64.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                        enabled = false
                                    )
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    var autoSequence by remember(currentBookId) {
                                        mutableStateOf(viewModel.settingsManager.getAutoSequence(currentBookId))
                                    }
                                    Checkbox(
                                        checked = autoSequence,
                                        onCheckedChange = {
                                            autoSequence = it
                                            viewModel.settingsManager.setAutoSequence(currentBookId, it)
                                        }
                                    )
                                    Text("Auto")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mode", style = MaterialTheme.typography.labelLarge)
                        Row {
                            RadioButton(
                                selected = mode == "practice",
                                onClick = { mode = "practice"; viewModel.settingsManager.setQuizMode(currentBookId, "practice") }
                            )
                            Text("Practice", modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 16.dp))

                            RadioButton(
                                selected = mode == "exam",
                                onClick = { mode = "exam"; viewModel.settingsManager.setQuizMode(currentBookId, "exam") }
                            )
                            Text("Exam", modifier = Modifier.padding(top = 12.dp, start = 8.dp))
                        }
                    }
                }
            }

            item {
                val hasActiveQuiz by viewModel.hasActiveQuiz.collectAsState()
                if (hasActiveQuiz) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.playButtonPress()
                            coroutineScope.launch {
                                if (viewModel.loadActiveQuizState()) {
                                    viewModel.isLearnMode = false
                                    onStartQuiz()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("RESUME ACTIVE QUIZ", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        viewModel.playButtonPress()
                        if (mode == "exam") {
                            showExamConfigDialog = true
                        } else {
                            viewModel.currentExamSettings = null
                            viewModel.clearActiveQuizState()
                            viewModel.setOnlyBookmarks(false)
                            viewModel.isLearnMode = false
                onStartQuiz()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = questions.isNotEmpty()
                ) {
                    Text("START NEW QUIZ", fontWeight = FontWeight.Bold)
                }
            }

            item {
                var showOptions by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().clickable { showOptions = !showOptions }) {
                            Text("Advanced Quiz Options", style = MaterialTheme.typography.titleMedium)
                            Icon(if (showOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle")
                        }
                        
                        if (showOptions) {
                            Spacer(modifier = Modifier.height(8.dp))

                            val shuffleQ by viewModel.shuffleQuestions.collectAsState()
                            var shuffleOpts by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getShuffleOptions(currentBookId)) }
                            var useDistractors by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getUseDistractors(currentBookId)) }
                            var questionFlow by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getQuestionFlow(currentBookId)) }

                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = shuffleQ,
                                    onClick = { viewModel.setShuffleQuestions(!shuffleQ) },
                                    label = { Text("Shuffle Questions") }
                                )
                                FilterChip(
                                    selected = shuffleOpts,
                                    onClick = { shuffleOpts = !shuffleOpts; viewModel.settingsManager.setShuffleOptions(currentBookId, shuffleOpts) },
                                    label = { Text("Shuffle Options") }
                                )
                                FilterChip(
                                    selected = useDistractors,
                                    onClick = { useDistractors = !useDistractors; viewModel.settingsManager.setUseDistractors(currentBookId, useDistractors) },
                                    label = { Text("Dynamic Distractors") }
                                )
                                FilterChip(
                                    selected = questionFlow,
                                    onClick = { questionFlow = !questionFlow; viewModel.settingsManager.setQuestionFlow(currentBookId, questionFlow) },
                                    label = { Text("Question Flow") }
                                )
                            }

                            var autoNextSeconds by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getAutoNextSeconds(currentBookId)) }

                            if (mode != "exam") {
                                var correctMark by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getCorrectMark(currentBookId)) }
                                var wrongMark by remember(currentBookId) { mutableStateOf(viewModel.settingsManager.getWrongMark(currentBookId)) }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Marking Scheme", style = MaterialTheme.typography.labelLarge)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val correctMarks = listOf(1f, 2f, 3f, 4f)
                                    correctMarks.forEach { m ->
                                        FilterChip(
                                            selected = correctMark == m,
                                            onClick = { correctMark = m; viewModel.settingsManager.setCorrectMark(currentBookId, m) },
                                            label = { Text("+$m") }
                                        )
                                    }
                                    VerticalDivider(modifier = Modifier.height(32.dp))
                                    val wrongMarks = listOf(0f, 0.25f, 0.5f, 0.66f, 1f)
                                    wrongMarks.forEach { m ->
                                        FilterChip(
                                            selected = wrongMark == m,
                                            onClick = { wrongMark = m; viewModel.settingsManager.setWrongMark(currentBookId, m) },
                                            label = { Text("-$m") }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Timers", style = MaterialTheme.typography.labelLarge)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val autoNexts = listOf(0, 1, 2, 3, 5)
                                    Text("Auto-Next:", modifier = Modifier.padding(top = 10.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                    autoNexts.forEach { t ->
                                        FilterChip(
                                            selected = autoNextSeconds == t,
                                            onClick = { autoNextSeconds = t; viewModel.settingsManager.setAutoNextSeconds(currentBookId, t) },
                                            label = { Text(if (t == 0) "Off" else "${t}s") }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Per Question:", modifier = Modifier.padding(top = 10.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                                val qTimes = listOf(0, 10, 15, 30) // 0 = Off
                                qTimes.forEach { t ->
                                    FilterChip(
                                        selected = questionTimer == t,
                                        onClick = {
                                            if (questionTimer == 0 && t > 0) {
                                                questionFlow = true
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, true)
                                            } else if (t == 0) {
                                                questionFlow = false
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, false)
                                            }
                                            questionTimer = t
                                            viewModel.settingsManager.setQuestionTimerSeconds(currentBookId, t)
                                        },
                                        label = { Text(if (t == 0) "Off" else "${t}s") }
                                    )
                                }
                                var customTime by remember(questionTimer) { mutableStateOf(if (qTimes.contains(questionTimer)) "" else questionTimer.toString()) }
                                OutlinedTextField(
                                    value = customTime,
                                    onValueChange = { newVal ->
                                        val filtered = newVal.filter { it.isDigit() }
                                        customTime = filtered
                                        filtered.toIntOrNull()?.let {
                                            if (questionTimer == 0 && it > 0) {
                                                questionFlow = true
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, true)
                                            } else if (it == 0) {
                                                questionFlow = false
                                                viewModel.settingsManager.setQuestionFlow(currentBookId, false)
                                            }
                                            questionTimer = it
                                            viewModel.settingsManager.setQuestionTimerSeconds(currentBookId, it)
                                        }
                                    },
                                    placeholder = { Text("Custom") },
                                    modifier = Modifier.width(90.dp).height(50.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                HomeActionRow("Read Book", Icons.AutoMirrored.Filled.MenuBook) { viewModel.playNavigation(); onNavigateReadBook() }
                HomeActionRow("Statistics", Icons.Default.Analytics) { viewModel.playNavigation(); onNavigateStats() }
                HomeActionRow("Learn", Icons.AutoMirrored.Filled.MenuBook) { viewModel.playNavigation(); onNavigateLearn() }
                HomeActionRow("Analysis", Icons.Default.Assessment) { viewModel.playNavigation(); onNavigateAnalysis() }
                HomeActionRow("Content Explorer", Icons.Default.Search) { viewModel.playNavigation(); onNavigateSearch() }
                HomeActionRow("Bookmarks", Icons.Default.Bookmark) { viewModel.playNavigation(); onNavigateBookmarks() }
                HomeActionRow("Quiz History", Icons.Default.History) { viewModel.playNavigation(); onNavigateHistory() }
            }
        }
    
        }
    }
}

@Composable
fun HomeActionRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title)
        }
    }
}

@Composable
fun TrashDialog(onDismiss: () -> Unit) {
    var trashedItems by remember { mutableStateOf<List<com.example.data.RawBookIdentity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val trashDir = java.io.File(basePath, "Quizer/.trash")
            val items = mutableListOf<com.example.data.RawBookIdentity>()
            if (trashDir.exists()) {
                val files = trashDir.listFiles { file -> file.isFile && file.name.endsWith(".qbook") } ?: emptyArray()
                for (file in files) {
                    try {
                        val uri = android.net.Uri.fromFile(file)
                        val identity = com.example.data.DataParser.peekIdentity(context, uri)
                        if (identity != null) {
                            items.add(identity.copy(id = file.name)) // Use filename as ID to show timestamp
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            trashedItems = items
            isLoading = false
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            visible = false
            coroutineScope.launch {
                kotlinx.coroutines.delay(300)
                onDismiss()
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.scaleIn(animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = androidx.compose.animation.scaleOut(animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Trash", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (trashedItems.isEmpty()) {
                            Text("Trash is empty.", modifier = Modifier.padding(vertical = 16.dp))
                        } else {
                            LazyColumn {
                                items(trashedItems, key = { it.id ?: "" }) { item ->
                                    val parts = item.id?.split("_")
                                    val timestamp = if (parts != null && parts.size >= 2) parts[1].removeSuffix(".qbook") else "Unknown Date"
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(item.name ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Deleted: $timestamp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("ID: ${item.id}\nVersion: ${item.bookVersion ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { 
                            visible = false
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(300)
                                onDismiss()
                            }
                        }) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                                        val trashDir = java.io.File(basePath, "Quizer/.trash")
                                        if (trashDir.exists()) {
                                            trashDir.deleteRecursively()
                                            trashDir.mkdirs()
                                        }
                                    }
                                    trashedItems = emptyList()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Trash")
                        }
                    }
                }
            }
        }
    }
}
