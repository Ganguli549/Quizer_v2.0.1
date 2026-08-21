package com.example.ui.screens

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation

import com.example.data.resolveHeaders
import androidx.compose.ui.graphics.ShaderBrush
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.BitmapShader
import java.util.Random
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind

import androidx.compose.foundation.lazy.itemsIndexed
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.animation.*
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import com.example.data.BookEntity
import com.example.data.QuestionEntity
import com.example.ui.MainViewModel
import com.google.gson.Gson
import com.example.ui.components.RichText
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import android.app.Activity


enum class ReadingTheme(val label: String) {
    System("System"),
    Day("Day"),
    Night("Night"),
    Sepia("Sepia"),
    Green("E-Ink"),
    BookPage("Book Page")
}

data class ThemeColors(val background: Color, val surface: Color, val text: Color, val primary: Color)


@Composable
fun ReadingMaterialTheme(themeColors: ThemeColors, isDark: Boolean, isSystem: Boolean, content: @Composable () -> Unit) {
    if (isSystem) {
        // Just use the app's default MaterialTheme for System mode
        content()
        return
    }
    
    val baseScheme = if (isDark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    
    // Create a modern look for custom reading themes (generate tonal surfaces)
    val colorScheme = baseScheme.copy(
        background = themeColors.background,
        surface = themeColors.background,
        surfaceVariant = themeColors.surface,
        onBackground = themeColors.text,
        onSurface = themeColors.text,
        onSurfaceVariant = themeColors.text,
        primary = themeColors.primary,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        outline = themeColors.text.copy(alpha = 0.5f),
        outlineVariant = themeColors.text.copy(alpha = 0.2f),
        surfaceContainer = themeColors.surface,
        // Fake tonal steps by blending slightly with primary or text
        surfaceContainerHigh = androidx.compose.ui.graphics.Color(
            androidx.core.graphics.ColorUtils.blendARGB(themeColors.surface.toArgb(), themeColors.text.toArgb(), 0.05f)
        ),
        surfaceContainerHighest = androidx.compose.ui.graphics.Color(
            androidx.core.graphics.ColorUtils.blendARGB(themeColors.surface.toArgb(), themeColors.text.toArgb(), 0.1f)
        ),
        surfaceContainerLow = themeColors.background,
        surfaceContainerLowest = themeColors.background
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun isReadingThemeDark(theme: ReadingTheme): Boolean {
    return when(theme) {
        ReadingTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ReadingTheme.Night -> true
        else -> false
    }
}

@Composable
fun getThemeColors(theme: ReadingTheme): ThemeColors {
    val sysBg = MaterialTheme.colorScheme.background
    val sysSurface = MaterialTheme.colorScheme.surfaceContainer
    val sysText = MaterialTheme.colorScheme.onBackground
    val sysPrimary = MaterialTheme.colorScheme.primary
    return when(theme) {
        ReadingTheme.System -> ThemeColors(sysBg, sysSurface, sysText, sysPrimary)
        ReadingTheme.Day -> ThemeColors(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF1976D2))
        ReadingTheme.Night -> ThemeColors(Color(0xFF000000), Color(0xFF000000), Color(0xFFB0B0B0), Color(0xFF90CAF9))
        ReadingTheme.Sepia -> ThemeColors(Color(0xFFDCCAB3), Color(0xFFDCCAB3), Color(0xFF1F1A14), Color(0xFF6B5542))
        ReadingTheme.Green -> ThemeColors(Color(0xFFCEE5D2), Color(0xFFCEE5D2), Color(0xFF2E4C34), Color(0xFF388E3C))
        ReadingTheme.BookPage -> ThemeColors(Color(0xFFFAFAFA), Color(0xFFFAFAFA), Color(0xFF1A1A1A), Color(0xFF555555))
    }
}



@androidx.compose.runtime.Composable
fun rememberPaperTextureBrush(baseColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Brush {
    return androidx.compose.runtime.remember(baseColor) {
        val size = 512
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(baseColor.toArgb())
        
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        val random = java.util.Random(42) // Fixed seed for consistent texture
        
        val isWhite = baseColor.red > 0.95f && baseColor.green > 0.95f && baseColor.blue > 0.95f
        
        // Define realistic paper texture colors
        val darkerColor = if (isWhite) android.graphics.Color.parseColor("#EAEAEA") else android.graphics.Color.parseColor("#D4C4AB")
        val darkestColor = if (isWhite) android.graphics.Color.parseColor("#CCCCCC") else android.graphics.Color.parseColor("#A69680")
        val lighterColor = if (isWhite) android.graphics.Color.parseColor("#FFFFFF") else android.graphics.Color.parseColor("#EFE5D5")
        
        // 0. Soft mottling for uneven paper coloring
        for (i in 0..15) {
            val cx = random.nextInt(size).toFloat()
            val cy = random.nextInt(size).toFloat()
            val radius = random.nextFloat() * 150f + 100f
            val mottleColor = if (random.nextBoolean()) darkerColor else lighterColor
            val alpha = random.nextInt(15) + 5
            
            val mottlePaint = android.graphics.Paint().apply {
                val colorWithAlpha = android.graphics.Color.argb(
                    alpha, 
                    android.graphics.Color.red(mottleColor), 
                    android.graphics.Color.green(mottleColor), 
                    android.graphics.Color.blue(mottleColor)
                )
                shader = android.graphics.RadialGradient(
                    cx, cy, radius, 
                    intArrayOf(colorWithAlpha, android.graphics.Color.TRANSPARENT), 
                    null, 
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, radius, mottlePaint)
        }

        // 1. High-density subtle base noise to give organic feel
        paint.strokeWidth = 1f
        for (i in 0..30000) {
            val x = random.nextInt(size).toFloat()
            val y = random.nextInt(size).toFloat()
            paint.color = if (random.nextBoolean()) darkerColor else lighterColor
            paint.alpha = random.nextInt(6) + 1 // Faint but noticeable alpha
            canvas.drawPoint(x, y, paint)
        }
        
        // 2. Mid-sized grains for texture depth
        for (i in 0..400) {
            val x = random.nextInt(size).toFloat()
            val y = random.nextInt(size).toFloat()
            val radius = random.nextFloat() * 1.2f + 0.3f 
            paint.color = darkestColor
            paint.alpha = random.nextInt(10) + 2
            canvas.drawCircle(x, y, radius, paint)
        }

        // 3. Realistic fibers/imperfections
        paint.strokeWidth = 1.0f
        for (i in 0..80) {
            val x = random.nextInt(size).toFloat()
            val y = random.nextInt(size).toFloat()
            paint.color = darkestColor
            paint.alpha = random.nextInt(12) + 3
            
            val path = android.graphics.Path()
            path.moveTo(x, y)
            val length = random.nextFloat() * 6f + 2f
            val curveX = x + (random.nextFloat() * length - length / 2)
            val curveY = y + (random.nextFloat() * length - length / 2)
            path.quadTo(curveX, curveY, x + length, y + random.nextFloat() * 4f - 2.0f)
            
            paint.style = android.graphics.Paint.Style.STROKE
            canvas.drawPath(path, paint)
            paint.style = android.graphics.Paint.Style.FILL
        }
        
        val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        androidx.compose.ui.graphics.ShaderBrush(shader)
    }
}

sealed class ReadBookItem {
    abstract val key: String
    data class PathHeader(val label: String, val title: String, val level: Int, val firstQuestionId: String) : ReadBookItem() {
        override val key = "PathHeader_${label}_${title}_${level}_${firstQuestionId}"
    }
    data class QuestionGroupHeader(val text: String, val range: String, val firstQuestionId: String) : ReadBookItem() {
        override val key = "QuestionGroupHeader_${firstQuestionId}"
    }
    data class QuestionData(val question: QuestionEntity, val localNumber: Int, val absoluteNumber: Int = 0) : ReadBookItem() {
        override val key = "QuestionData_${question.globalId}"

    }
}


fun highlightText(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    var startIndex = 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    while (true) {
        val index = lowerText.indexOf(lowerQuery, startIndex)
        if (index == -1) break
        builder.addStyle(
            style = SpanStyle(background = highlightColor, color = Color.Black),
            start = index,
            end = index + query.length
        )
        startIndex = index + query.length
    }
    return builder.toAnnotatedString()
}

@OptIn(ExperimentalMaterial3Api::class)
fun isBitmapGrayscale(bmp: android.graphics.Bitmap): Boolean {
    val scaled = try {
        android.graphics.Bitmap.createScaledBitmap(bmp, 64, 64, true)
    } catch (e: Exception) {
        return false
    }
    val pixels = IntArray(64 * 64)
    scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
    var isGray = true
    for (pixel in pixels) {
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        if (Math.abs(r - g) > 25 || Math.abs(r - b) > 25 || Math.abs(g - b) > 25) {
            isGray = false
            break
        }
    }
    scaled.recycle()
    return isGray
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartThemedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    isDarkTheme: Boolean,
    themeColors: ThemeColors? = null
) {
    var isGrayscale by remember { mutableStateOf(false) }
    
    // Apply the matrix for ANY theme if it's grayscale! This perfectly tints the image to match Day, Night, Sepia, and E-Ink modes!
    val colorFilter = if (isGrayscale && themeColors != null) {
        val bg = themeColors.background
        val txt = themeColors.text
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    bg.red - txt.red, 0f, 0f, 0f, txt.red * 255f,
                    0f, bg.green - txt.green, 0f, 0f, txt.green * 255f,
                    0f, 0f, bg.blue - txt.blue, 0f, txt.blue * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    } else if (isDarkTheme && isGrayscale) {
        val invertMatrix = floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        )
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(invertMatrix))
    } else null

    AsyncImage(
        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(model)
            .crossfade(true)
            .allowHardware(false) // Disable hardware bitmaps so we can read pixels
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        onSuccess = { state ->
            val drawable = state.result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bmp = drawable.bitmap
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    val gray = isBitmapGrayscale(bmp)
                    isGrayscale = gray
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadBookScreen(
    viewModel: MainViewModel,
    onHome: () -> Unit,
    onEditBook: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {}
) {
    val currentBookId by viewModel.currentBookId.collectAsState()
    val context = LocalContext.current
    var selectedThemeIndex by remember { mutableStateOf(com.example.data.SecurePrefs.get(context).getInt("reading_theme", 0)) }
    val readingTheme = ReadingTheme.values()[selectedThemeIndex.coerceIn(0, ReadingTheme.values().size - 1)]
    val themeColors = getThemeColors(readingTheme)
    val isDarkTheme = isReadingThemeDark(readingTheme)
    var showThemeMenu by remember { mutableStateOf(false) }
    var allQuestions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var currentBook by remember { mutableStateOf<BookEntity?>(null) }
    var hierarchyLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var inBookQuizMode by remember { mutableStateOf(viewModel.settingsManager.readBookQuizMode) }
    LaunchedEffect(inBookQuizMode) {
        viewModel.settingsManager.readBookQuizMode = inBookQuizMode
    }
    var showChapterIndex by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var showBriefExplanation by remember { mutableStateOf(false) }
    
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val listState = remember(currentBookId) {
        androidx.compose.foundation.lazy.LazyListState(
            firstVisibleItemIndex = viewModel.settingsManager.getBookScrollIndex(currentBookId),
            firstVisibleItemScrollOffset = viewModel.settingsManager.getBookScrollOffset(currentBookId)
        )
    }
    
    DisposableEffect(currentBookId, listState) {
        onDispose {
            viewModel.settingsManager.setBookScrollPosition(
                currentBookId,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }
    
    // Also save periodically or when scrolling stops to be safe, but DisposableEffect onDispose handles when composable leaves the composition.
    // However, if the app is killed, onDispose might not run.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            viewModel.settingsManager.setBookScrollPosition(
                currentBookId,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val itemOffsets = remember { mutableMapOf<Int, Int>() }
    val view = LocalView.current
    
    var isBarsVisible by remember { mutableStateOf(true) }

    val window = (view.context as? android.app.Activity)?.window
    DisposableEffect(Unit) {
        onDispose {
            val w = (view.context as? android.app.Activity)?.window
            if (w != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(w, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(isBarsVisible) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (!isBarsVisible) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    
    var isScrollerActive by remember { mutableStateOf(false) }
    val scrollDirectionConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < -3f && isBarsVisible && !isScrollerActive) {
                    isBarsVisible = false
                } else if (available.y > 3f && !isBarsVisible && !isScrollerActive) {
                    isBarsVisible = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    LaunchedEffect(currentBookId) {
        if (currentBookId != "") {
            allQuestions = viewModel.repository.getQuestionsForBookSync(currentBookId).resolveHeaders()
            currentBook = viewModel.repository.getBookSync(currentBookId)
            try {
                val type = object : TypeToken<List<String>>() {}.type
                hierarchyLabels = com.example.data.SharedGson.normal.fromJson(currentBook?.hierarchyLabels ?: "[]", type)
            } catch (e: Exception) {
                hierarchyLabels = emptyList()
            }
        }
    }

    var bookItems by remember { mutableStateOf<List<ReadBookItem>>(emptyList()) }
    var isLoadingBookItems by remember { mutableStateOf(false) }

    LaunchedEffect(allQuestions, hierarchyLabels) {
        if (allQuestions.isEmpty()) {
            bookItems = emptyList()
            return@LaunchedEffect
        }
        isLoadingBookItems = true
        bookItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val items = mutableListOf<ReadBookItem>()
            var currentPathParts = emptyList<String>()
            var currentLocalNumber = 1
            var currentQHeader: String? = null
            var qHeaderStartIndex = 0
            
            allQuestions.forEachIndexed { index, q ->
                val pathParts = q.path.split(">").map { it.trim() }
                for (level in pathParts.indices) {
                    val part = pathParts[level]
                    if (level >= currentPathParts.size || part != currentPathParts[level]) {
                        for (j in level until pathParts.size) {
                            if (j > 0) {
                                val label = hierarchyLabels.getOrNull(j) ?: "Level ${j + 1}"
                                items.add(ReadBookItem.PathHeader(label, pathParts[j], j, q.globalId))
                            }
                        }
                        currentLocalNumber = 1 
                        currentQHeader = null
                        break
                    }
                }
                currentPathParts = pathParts

                if (q.questionHeader != currentQHeader) {
                    currentQHeader = q.questionHeader
                    if (!currentQHeader.isNullOrBlank()) {
                        qHeaderStartIndex = currentLocalNumber
                        var endLocalNum = currentLocalNumber
                        for (k in index + 1 until allQuestions.size) {
                            if (allQuestions[k].questionHeader == currentQHeader && allQuestions[k].path == q.path) {
                                endLocalNum++
                            } else {
                                break
                            }
                        }
                        val rangeStr = if (qHeaderStartIndex == endLocalNum) "($qHeaderStartIndex)" else "($qHeaderStartIndex - $endLocalNum)"
                        items.add(ReadBookItem.QuestionGroupHeader(currentQHeader!!, rangeStr, q.globalId))
                    }
                }
                items.add(ReadBookItem.QuestionData(q, currentLocalNumber, index + 1))
                currentLocalNumber++
            }
            items
        }
        isLoadingBookItems = false
    }
    
    val searchMatches = remember(bookItems, searchQuery) {
        if (searchQuery.isBlank()) return@remember emptyList<Int>()
        val q = searchQuery.lowercase()
        bookItems.mapIndexedNotNull { index, item ->
            when (item) {
                is ReadBookItem.QuestionData -> {
                    if (item.question.question.lowercase().contains(q) ||
                        item.question.options.lowercase().contains(q) ||
                        item.question.explanation.lowercase().contains(q) ||
                        (item.question.detailedExplanation?.lowercase()?.contains(q) == true)
                    ) index else null
                }
                is ReadBookItem.PathHeader -> {
                    if (item.label.lowercase().contains(q) || item.title.lowercase().contains(q)) index else null
                }
                is ReadBookItem.QuestionGroupHeader -> {
                    if (item.text.lowercase().contains(q)) index else null
                }
                else -> null
            }
        }
    }
    
    var currentMatchIndex by remember(searchMatches) { mutableStateOf(if (searchMatches.isNotEmpty()) 0 else -1) }

    LaunchedEffect(searchMatches) {
        if (searchMatches.isNotEmpty() && currentMatchIndex == 0) {
            listState.animateScrollToItem(searchMatches[0])
        }
    }

    val bottomBarInfo by remember(bookItems, allQuestions.size) {
        androidx.compose.runtime.derivedStateOf {
            val index = listState.firstVisibleItemIndex
            if (index >= 0 && index < bookItems.size) {
                val item = bookItems[index]
                var topic = "Book"
                var number = 0
                when (item) {
                    is ReadBookItem.QuestionData -> {
                        topic = item.question.path.split(">").lastOrNull()?.trim() ?: "Book"
                        number = item.absoluteNumber
                    }
                    else -> {
                        for (i in index until bookItems.size) {
                            val ahead = bookItems[i]
                            if (ahead is ReadBookItem.QuestionData) {
                                topic = ahead.question.path.split(">").lastOrNull()?.trim() ?: "Book"
                                number = ahead.absoluteNumber
                                break
                            }
                        }
                    }
                }
                Pair(topic, number)
            } else {
                Pair("Book", 0)
            }
        }
    }

    val themeMode by viewModel.themeModeState.collectAsState()
    val appIsDark = when(themeMode) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val isDark = readingTheme == ReadingTheme.Night || (readingTheme == ReadingTheme.System && appIsDark)
    
    val paperBrush = rememberPaperTextureBrush(themeColors.background)
    
    ReadingMaterialTheme(themeColors = themeColors, isDark = isDark, isSystem = readingTheme == ReadingTheme.System) {
        Scaffold(
        modifier = Modifier.drawBehind {
            if (readingTheme == ReadingTheme.BookPage || readingTheme == ReadingTheme.Sepia) {
                drawRect(paperBrush)
                
                // Add vignette effect for book pages
                val vColor = Color(0x10302015)
                val edgeSize = 80f
                // Top
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(vColor, Color.Transparent), startY = 0f, endY = edgeSize))
                // Bottom
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, vColor), startY = size.height - edgeSize, endY = size.height))
                // Left
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(vColor, Color.Transparent), startX = 0f, endX = edgeSize))
                // Right
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, vColor), startX = size.width - edgeSize, endX = size.width))
                
                val brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0x00000000), Color(0x153E2723)),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2),
                    radius = size.width.coerceAtLeast(size.height) * 0.8f
                )
                drawRect(brush)
            }
        },
        containerColor = if (readingTheme == ReadingTheme.BookPage || readingTheme == ReadingTheme.Sepia) Color.Transparent else themeColors.background,
        contentColor = themeColors.text,
        floatingActionButton = {
            AnimatedVisibility(visible = isBarsVisible, enter = scaleIn(), exit = scaleOut()) {
                FloatingActionButton(onClick = { showAiAssistant = true }) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant")
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                BottomAppBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = themeColors.background.copy(alpha = 0.95f),
                    contentColor = themeColors.text,
                    tonalElevation = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bottomBarInfo.first,
                            style = MaterialTheme.typography.titleSmall,
                            color = themeColors.text,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = themeColors.primary.copy(alpha = 0.1f),
                            contentColor = themeColors.primary
                        ) {
                            Text(
                                text = "${if (bottomBarInfo.second > 0) bottomBarInfo.second else "-"}/${allQuestions.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (isSearchActive) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            if (searchMatches.isNotEmpty()) {
                                Text("${currentMatchIndex + 1}/${searchMatches.size}", style = MaterialTheme.typography.labelLarge)
                                IconButton(onClick = { 
                                    if (currentMatchIndex > 0) {
                                        currentMatchIndex--
                                        coroutineScope.launch { listState.animateScrollToItem(searchMatches[currentMatchIndex]) }
                                    }
                                }) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Previous")
                                }
                                IconButton(onClick = { 
                                    if (currentMatchIndex < searchMatches.size - 1) {
                                        currentMatchIndex++
                                        coroutineScope.launch { listState.animateScrollToItem(searchMatches[currentMatchIndex]) }
                                    }
                                }) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Next")
                                }
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text(currentBook?.name ?: "Read Book", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = onHome) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showChapterIndex = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, "Index")
                            }
                            IconButton(onClick = { inBookQuizMode = !inBookQuizMode }) {
                                Icon(if (inBookQuizMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, "In-Book Quiz Mode")
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Search") },
                                    onClick = { showMenu = false; isSearchActive = true; isBarsVisible = true },
                                    leadingIcon = { Icon(Icons.Default.Search, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Full Screen") },
                                    onClick = { showMenu = false; isBarsVisible = false },
                                    leadingIcon = { Icon(Icons.Default.Fullscreen, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Theme") },
                                    onClick = { showMenu = false; showThemeMenu = true },
                                    leadingIcon = { Icon(Icons.Default.Palette, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Book") },
                                    onClick = { showMenu = false; onEditBook() },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Brief Explanation") },
                                    onClick = { showMenu = false; showBriefExplanation = !showBriefExplanation },
                                    leadingIcon = { Icon(if (showBriefExplanation) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null) }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                                .then(
                    if (readingTheme == ReadingTheme.BookPage || readingTheme == ReadingTheme.Sepia) 
                        Modifier.background(Color.Transparent)
                    else 
                        Modifier.background(themeColors.background)
                )

                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isBarsVisible = !isBarsVisible
                        }
                    )
                }
        ) {
            val totalItems = bookItems.size
            if (isLoadingBookItems) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollDirectionConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 80.dp,
                    )
                ) {
                    itemsIndexed(
                        items = bookItems,
                        key = { _, item -> item.key }
                    ) { index, item ->
                        when (item) {
                            is ReadBookItem.PathHeader -> {
                                val textSize = when (item.level) {
                                    0 -> 28.sp
                                    1 -> 26.sp
                                    2 -> 24.sp
                                    else -> 22.sp
                                }
                                val textColor = when (item.level) {
                                    0 -> MaterialTheme.colorScheme.primary
                                    1 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    if (item.level == 0 && index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 24.dp, top = 8.dp),
                                            thickness = 2.dp,
                                            color = themeColors.primary.copy(alpha = 0.3f)
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.level == 0) {
                                            Box(modifier = Modifier.width(4.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Column {
                                            Text(
                                                text = item.label.uppercase(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor.copy(alpha = 0.7f),
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = item.title,
                                                fontSize = textSize,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                            is ReadBookItem.QuestionGroupHeader -> {
                                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                                    com.example.ui.components.ExpandableQuestionHeader(
                                        headerText = item.text,
                                        rangeStr = item.range,
                                        textColor = themeColors.text,
                                        modifier = Modifier.clickable { isBarsVisible = !isBarsVisible },
                                        isQuizMode = false,
                                        initiallyExpanded = true,
                                        onClick = { isBarsVisible = !isBarsVisible }
                                    )
                                }
                            }
                            is ReadBookItem.QuestionData -> {
                                val isHighlighted = searchMatches.isNotEmpty() && currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size && searchMatches[currentMatchIndex] == index
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        
                                        .background(if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                ) {
                                    ReadBookQuestionItem(
                                        question = item.question,
                                        localNumber = item.localNumber,
                                        inBookQuizMode = inBookQuizMode,
                                        gson = com.example.data.SharedGson.normal,
                                        context = context,
                                        isDarkTheme = isDarkTheme,
                                        themeColors = themeColors,
                                        searchQuery = searchQuery,
                                        highlightColor = Color.Yellow,
                                        onTextClick = { isBarsVisible = !isBarsVisible },
                                        showBriefExplanation = showBriefExplanation
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = themeColors.text.copy(alpha = 0.1f))
                            }
                        }
                    }
                }

            
            }
            // Fast Scroller
            if (totalItems > 0 && isBarsVisible) {
                val trackHeight = constraints.maxHeight.toFloat()
                val thumbHeight = 40.dp
                val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeight.toPx() }
                
                var dragY by remember { mutableStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                
                val thumbY by remember(totalItems, trackHeight, thumbHeightPx) {
                    androidx.compose.runtime.derivedStateOf {
                        if (isDragging) {
                            dragY.coerceIn(0f, trackHeight - thumbHeightPx)
                        } else {
                            val firstVisible = listState.firstVisibleItemIndex
                            ((firstVisible.toFloat() / maxOf(1, totalItems - 1)) * (trackHeight - thumbHeightPx)).coerceIn(0f, trackHeight - thumbHeightPx)
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    isScrollerActive = true
                                    val y = offset.y - (thumbHeightPx / 2)
                                    val targetIndex = ((y / (trackHeight - thumbHeightPx)) * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        isScrollerActive = false
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    isScrollerActive = true
                                    dragY = offset.y - (thumbHeightPx / 2)
                                },
                                onDragEnd = { 
                                    isDragging = false 
                                    isScrollerActive = false
                                },
                                onDragCancel = { 
                                    isDragging = false 
                                    isScrollerActive = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragY = change.position.y - (thumbHeightPx / 2)
                                    val targetIndex = ((dragY / (trackHeight - thumbHeightPx)) * totalItems).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(16.dp.toPx().toInt(), thumbY.toInt()) }
                            .size(6.dp, thumbHeight)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    )
                }
            }
        } // End of BoxWithConstraints
        
        
        if (showThemeMenu) {
            AlertDialog(
                onDismissRequest = { showThemeMenu = false },
                title = { Text("Select Reading Theme") },
                text = {
                    Column {
                        ReadingTheme.values().forEachIndexed { index, theme ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedThemeIndex = index
                                        com.example.data.SecurePrefs.get(context).edit().putInt("reading_theme", index).apply()
                                        showThemeMenu = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(getThemeColors(theme).background, RoundedCornerShape(4.dp))
                                ) {
                                    Text(
                                        "Aa",
                                        color = getThemeColors(theme).text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(theme.label, style = MaterialTheme.typography.bodyLarge)
                                if (selectedThemeIndex == index) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeMenu = false }) { Text("Close") }
                }
            )
        }

        if (showChapterIndex) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            ModalBottomSheet(onDismissRequest = { showChapterIndex = false }, sheetState = sheetState) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.8f)
                    .nestedScroll(connection = isolateScrollConnection, dispatcher = null)
                .draggable(androidx.compose.foundation.gestures.rememberDraggableState { }, orientation = androidx.compose.foundation.gestures.Orientation.Vertical)
                ) {
                    var chapterSearchQuery by remember { mutableStateOf("") }
                    val indexListState = rememberLazyListState()
                    
                    Text("Index", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = chapterSearchQuery,
                        onValueChange = { chapterSearchQuery = it },
                        placeholder = { Text("Search chapters...")  },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        trailingIcon = {
                            if (chapterSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { chapterSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(17.dp))
                    
                    val indexItems = remember(bookItems, chapterSearchQuery) {
                        if (chapterSearchQuery.isBlank()) {
                            bookItems.filterIsInstance<ReadBookItem.PathHeader>()
                        } else {
                            val q = chapterSearchQuery.lowercase()
                            bookItems.filterIsInstance<ReadBookItem.PathHeader>().filter { 
                                it.label.lowercase().contains(q) || it.title.lowercase().contains(q) 
                            }
                        }
                    }
                    
                    // Auto-scroll logic if search query yields results
                    LaunchedEffect(chapterSearchQuery) {
                        if (chapterSearchQuery.isNotBlank() && indexItems.isNotEmpty()) {
                            indexListState.animateScrollToItem(0)
                        }
                    }
                    
                    LazyColumn(state = indexListState) {
                        items(items = indexItems, key = { it.key }) { pathItem ->
                            val indent = (pathItem.level * 16).dp
                            Text(
                                text = "${pathItem.label}: ${pathItem.title}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val idx = bookItems.indexOf(pathItem)
                                        if (idx != -1) {
                                            coroutineScope.launch {
                                                listState.scrollToItem(idx)
                                            }
                                            showChapterIndex = false
                                        }
                                    }
                                    .padding(start = indent, top = 12.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (pathItem.level == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (pathItem.level == 0) MaterialTheme.colorScheme.primary else themeColors.text
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = indent))
                        }
                    }
                }
            }
        }

        if (showAiAssistant) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            
            var contextInfo = ""
            var filteredQuestions = allQuestions
            val visibleLocalNumbers = mutableListOf<Int>()
            
            if (bookItems.isNotEmpty() && visibleItems.isNotEmpty()) {
                var bestQItem: ReadBookItem.QuestionData? = null
                var maxVisibleHeight = 0
                
                var currentPath = ""
                
                for (info in visibleItems) {
                    val index = info.index
                    if (index in bookItems.indices) {
                        val item = bookItems[index]
                        if (item is ReadBookItem.QuestionData) {
                            if (currentPath.isEmpty()) currentPath = item.question.path
                            visibleLocalNumbers.add(item.localNumber)
                            
                            val itemTop = maxOf(viewportStart, info.offset)
                            val itemBottom = minOf(viewportEnd, info.offset + info.size)
                            val visibleHeight = maxOf(0, itemBottom - itemTop)
                            
                            if (visibleHeight > maxVisibleHeight) {
                                maxVisibleHeight = visibleHeight
                                bestQItem = item
                            }
                        }
                    }
                }
                
                if (bestQItem != null) {
                    currentPath = bestQItem.question.path
                    val currentQ = bestQItem.question
                    val localNum = bestQItem.localNumber
                    contextInfo = "The user is currently viewing the topic/path: '$currentPath'. The most prominent question on their screen is question ID ${currentQ.id} (which is question $localNum in this topic). They can also see questions: ${visibleLocalNumbers.joinToString(", ")}."
                    filteredQuestions = allQuestions.filter { it.path == currentPath }
                }
            }

            com.example.ui.components.AiAssistantBottomSheet(
                mode = com.example.ui.components.AiChatMode.READ_BOOK,
                questions = filteredQuestions,
                currentBookId = currentBookId,
                viewModel = viewModel,
                onDismiss = { showAiAssistant = false },
                onNavigateToAiSettings = onNavigateToAiSettings,
                onNavigateToUsage = onNavigateToUsage,
                                onQuestionsUpdated = { updatedQuestions ->
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.ui.screens.createRestorePoint(context, viewModel.repository, currentBookId, "Pre-AI Update")
                        updatedQuestions.forEach { 
                            viewModel.repository.updateQuestion(it)
                        }
                        val newQuestions = viewModel.repository.getQuestionsForBookSync(currentBookId).resolveHeaders()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            allQuestions = newQuestions
                        }
                        // Save back to original file as well
                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                        val targetFile = java.io.File(basePath, "Quizer/${currentBookId}.qbook")
                        com.example.ui.screens.exportQBook(context, viewModel.repository, currentBookId, targetFile)
                    }
                },
                currentContextInfo = contextInfo,
                visibleQuestionNumbers = visibleLocalNumbers.distinct()
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadBookQuestionItem(
    question: QuestionEntity,
    localNumber: Int,
    inBookQuizMode: Boolean,
    gson: Gson,
    context: android.content.Context,
    isDarkTheme: Boolean,
    themeColors: ThemeColors,
    searchQuery: String = "",
    highlightColor: Color = Color.Yellow,
    onTextClick: (() -> Unit)? = null,
    showBriefExplanation: Boolean = false
) {
    var isRevealed by remember(question.id, inBookQuizMode) { mutableStateOf(!inBookQuizMode) }
    var selectedOptionIndices by remember(question.id, inBookQuizMode) { mutableStateOf(setOf<Int>()) }

    val optionsList = remember(question) {
        val type = object : TypeToken<List<com.example.data.QBookOption>>() {}.type
        val advanced = gson.fromJson<List<com.example.data.QBookOption>>(question.advancedOptions ?: "[]", type)
        if (advanced != null && advanced.isNotEmpty()) {
            advanced.mapIndexed { idx, opt -> Pair(idx, opt.text) }
        } else {
            try {
                val strType = object : TypeToken<List<String>>() {}.type
                val strOptions = gson.fromJson<List<String>>(question.options, strType)
                strOptions.mapIndexed { idx, opt -> Pair(idx, opt) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    val correctIndices = remember(question) {
        val strType = object : TypeToken<List<String>>() {}.type
        val correctIds = gson.fromJson<List<String>>(question.advancedCorrectIds ?: "[]", strType) ?: emptyList()
        val type = object : TypeToken<List<com.example.data.QBookOption>>() {}.type
        val advancedOpts = gson.fromJson<List<com.example.data.QBookOption>>(question.advancedOptions ?: "[]", type) ?: emptyList()
        
        if (correctIds.isNotEmpty() && advancedOpts.isNotEmpty()) {
            correctIds.mapNotNull { id -> advancedOpts.indexOfFirst { it.id == id }.takeIf { it != -1 } }.toSet()
        } else {
            setOf(question.correctIndex)
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().animateContentSize()) {
        
        
        com.example.ui.components.SmartRichText(
            text = "<b>Q.$localNumber</b> ${question.question}",
            fontSize = 20.sp,
            color = themeColors.text,
            searchQuery = searchQuery,
            highlightColor = highlightColor,
            onClick = onTextClick
        )
        
        if (question.format == "table" && question.tableData != null) {
            val tableData = androidx.compose.runtime.remember(question.id) {
                try {
                    val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                    com.example.data.SharedGson.normal.fromJson<List<List<String>>>(question.tableData, tableType)
                } catch (e: Exception) {
                    null
                }
            }
            if (tableData != null) {
                Spacer(modifier = Modifier.height(17.dp))
                com.example.ui.components.TableRenderer(tableData, themeColors.text)
            }
        }
        
        if (!question.bottomText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(17.dp))
            com.example.ui.components.SmartRichText(
                text = question.bottomText!!,
                fontSize = 18.sp,
                color = themeColors.text,
                searchQuery = searchQuery,
                highlightColor = highlightColor,
                onClick = onTextClick
            )
        }
        
        com.example.ui.components.ExamTagsRow(
            examsJson = question.exams,
            legacyExam = question.exam,
            legacyYear = question.year,
            color = themeColors.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (!question.img.isNullOrEmpty()) {
            val imgPath = question.img
            val imageModel = remember(imgPath) {
                if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                    imgPath
                } else {
                    val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/${question.bookId}/images/${imgPath.substringAfterLast("/")}")
                    val f2 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                    if (f1.exists()) f1 else if (f2.exists()) f2 else "file:///android_asset/data/$imgPath"
                }
            }
            SmartThemedImage(
                model = imageModel,
                contentDescription = "Question Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Inside,
                isDarkTheme = isDarkTheme
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        optionsList.forEachIndexed { displayIndex, (index, optionText) ->
            val isCorrect = correctIndices.contains(index)
            val isSelected = selectedOptionIndices.contains(index)
            
            val textColor = if (isRevealed) {
                if (isCorrect) Color(0xFF4CAF50)
                else if (isSelected && !isCorrect) Color(0xFFE53935)
                else themeColors.text
            } else {
                themeColors.text
            }
            
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = inBookQuizMode && !isRevealed) {
                        if (question.multipleCorrect) {
                            selectedOptionIndices = selectedOptionIndices + index
                            isRevealed = true
                        } else {
                            selectedOptionIndices = setOf(index)
                            isRevealed = true
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(65 + displayIndex).toChar()}.", 
                    fontWeight = FontWeight.Normal, 
                    color = textColor, 
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                com.example.ui.components.SmartRichText(
                    text = optionText,
                    fontSize = 18.sp,
                    color = textColor,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    isSelectable = false,
                    onClick = {
                        if (inBookQuizMode && !isRevealed) {
                            if (question.multipleCorrect) {
                                selectedOptionIndices = selectedOptionIndices + index
                                isRevealed = true
                            } else {
                                selectedOptionIndices = setOf(index)
                                isRevealed = true
                            }
                        } else {
                            onTextClick?.invoke()
                        }
                    }
                )
            }
        }
        
        AnimatedVisibility(visible = isRevealed && ((question.explanation.isNotBlank() && (showBriefExplanation || question.detailedExplanation.isNullOrEmpty())) || !question.detailedExplanation.isNullOrEmpty())) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                val hasBrief = question.explanation.isNotBlank() && (showBriefExplanation || question.detailedExplanation.isNullOrEmpty())
                val hasDetailed = !question.detailedExplanation.isNullOrEmpty()
                
                Text(
                    text = "Explanation:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = themeColors.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                if (hasBrief && hasDetailed) {
                    Text("Brief:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = themeColors.text)
                    com.example.ui.components.SmartRichText(
                        text = question.explanation,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        onClick = onTextClick
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Detailed:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = themeColors.text)
                    com.example.ui.components.SmartRichText(
                        text = question.detailedExplanation!!,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        onClick = onTextClick
                    )
                } else if (hasBrief) {
                    com.example.ui.components.SmartRichText(
                        text = question.explanation,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        onClick = onTextClick
                    )
                } else if (hasDetailed) {
                    com.example.ui.components.SmartRichText(
                        text = question.detailedExplanation!!,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        onClick = onTextClick
                    )
                }
                if (!question.explanationTable.isNullOrEmpty()) {
                    val expTableData = androidx.compose.runtime.remember(question.id) {
                        try {
                            val tableType = object : com.google.gson.reflect.TypeToken<List<List<String>>>() {}.type
                            com.example.data.SharedGson.normal.fromJson<List<List<String>>>(question.explanationTable, tableType)
                        } catch (e: Exception) { null }
                    }
                    if (expTableData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.ui.components.TableRenderer(expTableData, themeColors.text)
                    }
                }
                
                if (!question.explanationImage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val imgPath = question.explanationImage
                    var imageModel by remember(imgPath) { mutableStateOf<String?>(null) }
                    LaunchedEffect(imgPath) {
                        if (imgPath.startsWith("/") || imgPath.startsWith("http")) {
                            imageModel = imgPath
                        } else {
                            imageModel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val f1 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/${question.bookId}/images/${imgPath.substringAfterLast("/")}")
                                val f2 = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Quizer/$imgPath")
                                when {
                                    f1.exists() -> f1.absolutePath
                                    f2.exists() -> f2.absolutePath
                                    else -> "file:///android_asset/data/$imgPath"
                                }
                            }
                        }
                    }
                    if (imageModel != null) {
                        SmartThemedImage(
                            model = imageModel,
                            contentDescription = "Explanation Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Inside,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }
    }
}

