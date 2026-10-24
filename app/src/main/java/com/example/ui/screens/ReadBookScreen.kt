package com.example.ui.screens

import com.example.data.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures

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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    
    val bgArgb = themeColors.background.toArgb()
    val surfaceArgb = themeColors.surface.toArgb()
    val textArgb = themeColors.text.toArgb()
    
    // Contrast factors: blend surface towards text for tonal elevations & variants
    val blendLow = if (isDark) 0.05f else 0.04f
    val blendNormal = if (isDark) 0.10f else 0.07f
    val blendHigh = if (isDark) 0.16f else 0.11f
    val blendHighest = if (isDark) 0.22f else 0.16f
    val blendVariant = if (isDark) 0.18f else 0.12f
    
    val computedSurfaceVariant = Color(androidx.core.graphics.ColorUtils.blendARGB(surfaceArgb, textArgb, blendVariant))
    val computedContainer = Color(androidx.core.graphics.ColorUtils.blendARGB(surfaceArgb, textArgb, blendNormal))
    val computedContainerHigh = Color(androidx.core.graphics.ColorUtils.blendARGB(surfaceArgb, textArgb, blendHigh))
    val computedContainerHighest = Color(androidx.core.graphics.ColorUtils.blendARGB(surfaceArgb, textArgb, blendHighest))
    val computedContainerLow = themeColors.surface

    val onPrimaryColor = if (androidx.core.graphics.ColorUtils.calculateLuminance(themeColors.primary.toArgb()) > 0.5) Color.Black else Color.White

    // Create a modern look for custom reading themes (generate tonal surfaces)
    val colorScheme = baseScheme.copy(
        background = themeColors.background,
        surface = themeColors.surface,
        surfaceVariant = computedSurfaceVariant,
        onBackground = themeColors.text,
        onSurface = themeColors.text,
        onSurfaceVariant = themeColors.text,
        primary = themeColors.primary,
        onPrimary = onPrimaryColor,
        outline = themeColors.text.copy(alpha = 0.50f),
        outlineVariant = themeColors.text.copy(alpha = 0.28f),
        surfaceContainer = computedContainer,
        surfaceContainerHigh = computedContainerHigh,
        surfaceContainerHighest = computedContainerHighest,
        surfaceContainerLow = computedContainerLow,
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
        ReadingTheme.Day -> ThemeColors(Color(0xFFF8F9FA), Color(0xFFECEEF2), Color(0xFF181C22), Color(0xFF1A56DB))
        ReadingTheme.Night -> ThemeColors(Color(0xFF111317), Color(0xFF1B1E26), Color(0xFFE5E7EB), Color(0xFF6895F5))
        ReadingTheme.Sepia -> ThemeColors(Color(0xFFDCCAB3), Color(0xFFCCB8A0), Color(0xFF1F1A14), Color(0xFF6B5542))
        ReadingTheme.Green -> ThemeColors(Color(0xFFEAF1EB), Color(0xFFD9E4DC), Color(0xFF16321C), Color(0xFF246C34))
        ReadingTheme.BookPage -> ThemeColors(Color(0xFFFAFAFA), Color(0xFFECECEC), Color(0xFF1A1A1A), Color(0xFF555555))
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
    data class PathHeader(
        val label: String, 
        val title: String, 
        val level: Int, 
        val firstQuestionId: String,
        val fullPath: String = "",
        val itemIndex: Int = -1
    ) : ReadBookItem() {
        override val key = "PathHeader_${label}_${title}_${level}_${firstQuestionId}_${itemIndex}"
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
    val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
    val bookmarkedQuestionIds by remember(bookmarks) {
        derivedStateOf { bookmarks.map { it.questionId }.toSet() }
    }
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
    var chapterSheetOpenCount by remember { mutableIntStateOf(0) }
    var showAiAssistant by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var autoSendAiPrompt by remember { mutableStateOf(false) }
    var showBriefExplanation by remember { mutableStateOf(false) }
    var showBookmarks by remember(currentBookId) {
        mutableStateOf(viewModel.settingsManager.getShowBookmarks(currentBookId))
    }
    var editingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
    
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }
    
    val listState = remember(currentBookId) {
        androidx.compose.foundation.lazy.LazyListState(
            firstVisibleItemIndex = viewModel.settingsManager.getBookScrollIndex(currentBookId),
            firstVisibleItemScrollOffset = viewModel.settingsManager.getBookScrollOffset(currentBookId)
        )
    }
    
    var hasRestoredScrollPosition by remember(currentBookId) { mutableStateOf(false) }

    DisposableEffect(currentBookId, listState) {
        onDispose {
            if (hasRestoredScrollPosition) {
                viewModel.settingsManager.setBookScrollPosition(
                    currentBookId,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset
                )
            }
        }
    }
    
    // Also save periodically or when scrolling stops to be safe, but DisposableEffect onDispose handles when composable leaves the composition.
    // However, if the app is killed, onDispose might not run.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && hasRestoredScrollPosition) {
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
            if (w != null && w.decorView.isAttachedToWindow) {
                try {
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(isBarsVisible, isDarkTheme) {
        if (window != null) {
            try {
                val targetView = if (view.isAttachedToWindow) view else window.decorView
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, targetView)
                if (!isBarsVisible) {
                    insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    
    var isScrollerActive by remember { mutableStateOf(false) }
    val scrollDirectionConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // Scrolling downwards: enter full screen (read mode)
                if (available.y < -5f && isBarsVisible && !isScrollerActive) {
                    isBarsVisible = false
                }
                // Scrolling upwards: should NOT trigger full screen exit
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // When reached top of the book via scrolling, top bar and bottom bar appear
                if (available.y > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 0) {
                    if (!isBarsVisible) {
                        isBarsVisible = true
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // When reaching the top of the book, top bar and bottom bar should appear
    LaunchedEffect(listState) {
        var wasScrolledDown = false
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { isAtTop ->
            if (!isAtTop) {
                wasScrolledDown = true
            } else if (wasScrolledDown) {
                isBarsVisible = true
                wasScrolledDown = false
            }
        }
    }

    val displayMetrics = context.resources.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    val screenHeightPx = displayMetrics.heightPixels.toFloat()

    val onMiddleClickCoords: (Float, Float) -> Unit = remember(screenWidthPx, screenHeightPx) {
        { rawX, rawY ->
            if (!isBarsVisible) {
                // When in Full Screen: tapping ANYWHERE on the ReadBook content exits full screen
                isBarsVisible = true
            } else {
                // When bars are visible: tapping in the middle zone enters full screen
                val inHorizontal = rawX >= screenWidthPx * 0.12f && rawX <= screenWidthPx * 0.88f
                val inVertical = rawY >= screenHeightPx * 0.15f && rawY <= screenHeightPx * 0.85f
                if (inHorizontal && inVertical) {
                    isBarsVisible = false
                }
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
                                val currentIdx = items.size
                                val fullPath = pathParts.take(j + 1).joinToString(" > ")
                                items.add(ReadBookItem.PathHeader(
                                    label = label,
                                    title = pathParts[j],
                                    level = j,
                                    firstQuestionId = q.globalId,
                                    fullPath = fullPath,
                                    itemIndex = currentIdx
                                ))
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
                        val rangeStr = if (qHeaderStartIndex == endLocalNum) "" else "($qHeaderStartIndex - $endLocalNum)"
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

    LaunchedEffect(bookItems, currentBookId) {
        if (bookItems.isNotEmpty() && !hasRestoredScrollPosition) {
            val savedIdx = viewModel.settingsManager.getBookScrollIndex(currentBookId)
            val savedOffset = viewModel.settingsManager.getBookScrollOffset(currentBookId)
            if (savedIdx in bookItems.indices) {
                listState.scrollToItem(savedIdx, savedOffset)
            }
            hasRestoredScrollPosition = true
        }
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

    val allPathHeaders = remember(bookItems) {
        bookItems.filterIsInstance<ReadBookItem.PathHeader>()
    }

    val currentPathHeader by remember(bookItems) {
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex.coerceIn(0, (bookItems.size - 1).coerceAtLeast(0))
            var found: ReadBookItem.PathHeader? = null
            for (i in currentIndex downTo 0) {
                val item = bookItems.getOrNull(i)
                if (item is ReadBookItem.PathHeader) {
                    found = item
                    break
                }
            }
            if (found == null) {
                for (i in currentIndex until bookItems.size) {
                    val item = bookItems.getOrNull(i)
                    if (item is ReadBookItem.PathHeader) {
                        found = item
                        break
                    }
                }
            }
            found
        }
    }

    val currentTopicIndex by remember(allPathHeaders) {
        derivedStateOf {
            val header = currentPathHeader
            if (header != null) {
                val idx = allPathHeaders.indexOfFirst {
                    it.itemIndex == header.itemIndex
                }.takeIf { it != -1 } ?: allPathHeaders.indexOfFirst {
                    it.key == header.key
                }
                if (idx != null && idx != -1) idx else 0
            } else 0
        }
    }

    val currentReadingQuestion by remember(bookItems, allQuestions) {
        derivedStateOf {
            if (bookItems.isEmpty()) {
                allQuestions.firstOrNull()
            } else {
                val currentIndex = listState.firstVisibleItemIndex.coerceIn(0, (bookItems.size - 1).coerceAtLeast(0))
                val currentItem = bookItems.getOrNull(currentIndex)
                if (currentItem is ReadBookItem.QuestionData) {
                    currentItem.question
                } else {
                    var found: QuestionEntity? = null
                    for (i in currentIndex until bookItems.size) {
                        val item = bookItems[i]
                        if (item is ReadBookItem.QuestionData) {
                            found = item.question
                            break
                        }
                    }
                    if (found == null) {
                        for (i in currentIndex downTo 0) {
                            val item = bookItems[i]
                            if (item is ReadBookItem.QuestionData) {
                                found = item.question
                                break
                            }
                        }
                    }
                    found ?: allQuestions.firstOrNull()
                }
            }
        }
    }

    val openEditBookWithQuestion = { targetQuestion: QuestionEntity? ->
        val target = targetQuestion ?: currentReadingQuestion
        viewModel.setTargetEditQuestion(target?.id, target?.globalId)
        onEditBook()
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
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal),
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = themeColors.surface,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = themeColors.text.copy(alpha = 0.15f)
                        )
                        BottomAppBar(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = themeColors.surface,
                            contentColor = themeColors.text,
                            tonalElevation = 0.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
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
                                    color = themeColors.primary.copy(alpha = 0.12f),
                                    contentColor = themeColors.primary,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            openEditBookWithQuestion(currentReadingQuestion)
                                        }
                                ) {
                                    Text(
                                        text = "${if (bottomBarInfo.second > 0) bottomBarInfo.second else "-"}/${allQuestions.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = themeColors.surface,
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isSearchActive) {
                            TopAppBar(
                                title = {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search...", color = themeColors.text.copy(alpha = 0.6f)) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedTextColor = themeColors.text,
                                            unfocusedTextColor = themeColors.text,
                                            cursorColor = themeColors.primary,
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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = themeColors.text)
                                    }
                                },
                                actions = {
                                    if (searchMatches.isNotEmpty()) {
                                        Text("${currentMatchIndex + 1}/${searchMatches.size}", style = MaterialTheme.typography.labelLarge, color = themeColors.text)
                                        IconButton(onClick = { 
                                            if (currentMatchIndex > 0) {
                                                currentMatchIndex--
                                                coroutineScope.launch { listState.animateScrollToItem(searchMatches[currentMatchIndex]) }
                                            }
                                        }) {
                                            Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = themeColors.text)
                                        }
                                        IconButton(onClick = { 
                                            if (currentMatchIndex < searchMatches.size - 1) {
                                                currentMatchIndex++
                                                coroutineScope.launch { listState.animateScrollToItem(searchMatches[currentMatchIndex]) }
                                            }
                                        }) {
                                            Icon(Icons.Default.KeyboardArrowDown, "Next", tint = themeColors.text)
                                        }
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, "Clear", tint = themeColors.text)
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = themeColors.surface,
                                    scrolledContainerColor = themeColors.surface,
                                    titleContentColor = themeColors.text,
                                    navigationIconContentColor = themeColors.text,
                                    actionIconContentColor = themeColors.text
                                )
                            )
                        } else {
                            TopAppBar(
                                title = { Text(currentBook?.name ?: "Read Book", color = themeColors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                navigationIcon = {
                                    IconButton(onClick = onHome) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = themeColors.text)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        chapterSheetOpenCount++
                                        showChapterIndex = true
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.List, "Index", tint = themeColors.text)
                                    }
                                    IconButton(onClick = { inBookQuizMode = !inBookQuizMode }) {
                                        Icon(if (inBookQuizMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, "In-Book Quiz Mode", tint = themeColors.text)
                                    }
                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.MoreVert, "More Options", tint = themeColors.text)
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                            shape = RoundedCornerShape(16.dp),
                                            containerColor = themeColors.surface,
                                            tonalElevation = 4.dp,
                                            shadowElevation = 8.dp,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.text.copy(alpha = 0.15f))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Search", color = themeColors.text) },
                                                onClick = { showMenu = false; isSearchActive = true; isBarsVisible = true },
                                                leadingIcon = { Icon(Icons.Default.Search, null, tint = themeColors.text) },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = themeColors.text,
                                                    leadingIconColor = themeColors.text
                                                )
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Theme", color = themeColors.text) },
                                                onClick = { showMenu = false; showThemeMenu = true },
                                                leadingIcon = { Icon(Icons.Default.Palette, null, tint = themeColors.text) },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = themeColors.text,
                                                    leadingIconColor = themeColors.text
                                                )
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Edit Book", color = themeColors.text) },
                                                onClick = { 
                                                    showMenu = false
                                                    openEditBookWithQuestion(currentReadingQuestion)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = themeColors.text) },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = themeColors.text,
                                                    leadingIconColor = themeColors.text
                                                )
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (showBookmarks) "Hide bookmarks" else "Show bookmarks", color = themeColors.text) },
                                                onClick = {
                                                    showMenu = false
                                                    val newShow = !showBookmarks
                                                    showBookmarks = newShow
                                                    viewModel.settingsManager.setShowBookmarks(currentBookId, newShow)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        if (newShow) {
                                                            if (bookmarkedQuestionIds.isEmpty()) "Bookmarks shown (0 bookmarks)" else "Bookmarks shown"
                                                        } else {
                                                            "Bookmarks hidden"
                                                        },
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        if (showBookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                        null,
                                                        tint = themeColors.text
                                                    )
                                                },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = themeColors.text,
                                                    leadingIconColor = themeColors.text
                                                )
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Brief Explanation", color = themeColors.text) },
                                                onClick = { showMenu = false; showBriefExplanation = !showBriefExplanation },
                                                leadingIcon = { Icon(if (showBriefExplanation) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null, tint = themeColors.text) },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = themeColors.text,
                                                    leadingIconColor = themeColors.text
                                                )
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = themeColors.surface,
                                    scrolledContainerColor = themeColors.surface,
                                    titleContentColor = themeColors.text,
                                    navigationIconContentColor = themeColors.text,
                                    actionIconContentColor = themeColors.text
                                )
                            )
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = themeColors.text.copy(alpha = 0.15f)
                        )
                    }
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
                        onTap = { offset ->
                            if (!isBarsVisible) {
                                // When in Full Screen: tapping anywhere on headers, topics, images, dividers, or margins exits full screen
                                isBarsVisible = true
                            } else {
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val inHorizontal = offset.x >= width * 0.12f && offset.x <= width * 0.88f
                                val inVertical = offset.y >= height * 0.15f && offset.y <= height * 0.85f
                                if (inHorizontal && inVertical) {
                                    isBarsVisible = false
                                }
                            }
                        }
                    )
                }
        ) {
            val totalItems = bookItems.size
            if (isLoadingBookItems) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (bookItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = themeColors.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No questions in this book yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColors.text
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the Sparkle AI button at the bottom right to attach a document (PDF, image, or text) and extract questions into this QBook.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = themeColors.text.copy(alpha = 0.75f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
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
                                        isQuizMode = false,
                                        initiallyExpanded = true,
                                        searchQuery = searchQuery,
                                        highlightColor = Color.Yellow,
                                        onClickWithCoords = onMiddleClickCoords
                                    )
                                }
                            }
                            is ReadBookItem.QuestionData -> {
                                val isHighlighted = searchMatches.isNotEmpty() && currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size && searchMatches[currentMatchIndex] == index
                                val isQuestionBookmarked = bookmarkedQuestionIds.contains(item.question.id)
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
                                        readingTheme = readingTheme,
                                        themeColors = themeColors,
                                        searchQuery = searchQuery,
                                        highlightColor = Color.Yellow,
                                        isBookmarked = isQuestionBookmarked,
                                        showBookmarkHighlight = showBookmarks,
                                        onBookmarkToggle = {
                                            val willBookmark = !isQuestionBookmarked
                                            viewModel.toggleBookmark(item.question.bookId, item.question.id)
                                            android.widget.Toast.makeText(
                                                context,
                                                if (willBookmark) "Question #${item.localNumber} bookmarked" else "Bookmark removed",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onEditQuestion = {
                                            editingQuestion = item.question
                                        },
                                        onAskAiQuestion = { _, localNum ->
                                            val chatContextId = "${currentBookId}_read"
                                            viewModel.setAiPromptText(chatContextId, "Explain $localNum")
                                            autoSendAiPrompt = true
                                            showAiAssistant = true
                                        },
                                        onTextClickWithCoords = onMiddleClickCoords,
                                        showBriefExplanation = showBriefExplanation
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = themeColors.text.copy(alpha = 0.1f))
                            }
                        }
                    }
                }

            
            }
            // Fast Scroller - positioned strictly between top bar and bottom bar
            AnimatedVisibility(
                visible = totalItems > 0 && isBarsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        top = padding.calculateTopPadding() + 6.dp,
                        bottom = padding.calculateBottomPadding() + 6.dp
                    )
                    .fillMaxHeight()
                    .width(36.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val trackHeight = constraints.maxHeight.toFloat()
                    val thumbHeight = 44.dp
                    val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }
                    
                    var dragY by remember { mutableStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    
                    val thumbY by remember(totalItems, trackHeight, thumbHeightPx) {
                        derivedStateOf {
                            val usableTrack = maxOf(1f, trackHeight - thumbHeightPx)
                            if (isDragging) {
                                dragY.coerceIn(0f, usableTrack)
                            } else {
                                val firstVisible = listState.firstVisibleItemIndex
                                ((firstVisible.toFloat() / maxOf(1, totalItems - 1)) * usableTrack).coerceIn(0f, usableTrack)
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(totalItems, trackHeight) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val usableTrack = maxOf(1f, trackHeight - thumbHeightPx)
                                        if (usableTrack > 0f) {
                                            isScrollerActive = true
                                            val y = offset.y - (thumbHeightPx / 2)
                                            val targetIndex = ((y / usableTrack) * totalItems).toInt().coerceIn(0, totalItems - 1)
                                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                isScrollerActive = false
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(totalItems, trackHeight) {
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
                                        val usableTrack = maxOf(1f, trackHeight - thumbHeightPx)
                                        if (usableTrack > 0f) {
                                            dragY = change.position.y - (thumbHeightPx / 2)
                                            val targetIndex = ((dragY / usableTrack) * totalItems).toInt().coerceIn(0, totalItems - 1)
                                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                        }
                                    }
                                )
                            }
                    ) {
                        // Subtle track guide line centered behind thumb
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 5.dp)
                                .fillMaxHeight()
                                .width(2.dp)
                                .background(
                                    color = themeColors.text.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                        // Scroll thumb
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 3.dp)
                                .offset { IntOffset(0, thumbY.toInt()) }
                                .size(width = if (isDragging) 8.dp else 6.dp, height = thumbHeight)
                                .background(
                                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        } // End of BoxWithConstraints
        
        
        if (showThemeMenu) {
            AlertDialog(
                onDismissRequest = { showThemeMenu = false },
                shape = RoundedCornerShape(22.dp),
                containerColor = themeColors.surface,
                titleContentColor = themeColors.text,
                textContentColor = themeColors.text,
                title = {
                    Text(
                        "Select Reading Theme",
                        fontWeight = FontWeight.Bold,
                        color = themeColors.text
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReadingTheme.values().forEachIndexed { index, theme ->
                            val isSelected = selectedThemeIndex == index
                            val swatchColors = getThemeColors(theme)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) themeColors.primary.copy(alpha = 0.14f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedThemeIndex = index
                                        com.example.data.SecurePrefs.get(context).edit().putInt("reading_theme", index).apply()
                                        showThemeMenu = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = swatchColors.background,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        swatchColors.text.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "Aa",
                                            color = swatchColors.text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = theme.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) themeColors.primary else themeColors.text
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = themeColors.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeMenu = false }) {
                        Text("Close", color = themeColors.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        if (showChapterIndex) {
            val targetTopicIndex = currentTopicIndex

            com.example.ui.components.CustomModalBottomSheet(
                maxHeightFraction = 0.75f,
                enableDynamicHeight = true,
                fillMaxSheetHeight = true,
                onDismissRequest = { showChapterIndex = false }
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().weight(1f, fill = true)) {
                    var chapterSearchQuery by remember { mutableStateOf("") }
                    val indexListState = remember(chapterSheetOpenCount) {
                        androidx.compose.foundation.lazy.LazyListState(
                            firstVisibleItemIndex = targetTopicIndex,
                            firstVisibleItemScrollOffset = 0
                        )
                    }
                    
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
                    
                    val indexItems = remember(allPathHeaders, chapterSearchQuery) {
                        if (chapterSearchQuery.isBlank()) {
                            allPathHeaders
                        } else {
                            val q = chapterSearchQuery.lowercase()
                            allPathHeaders.filter { 
                                it.label.lowercase().contains(q) || it.title.lowercase().contains(q) 
                            }
                        }
                    }
                    
                    LaunchedEffect(chapterSheetOpenCount, targetTopicIndex) {
                        snapshotFlow { indexListState.layoutInfo.viewportSize.height }
                            .filter { it > 0 }
                            .first()
                        if (targetTopicIndex in allPathHeaders.indices) {
                            indexListState.scrollToItem(targetTopicIndex)
                        }
                    }

                    LaunchedEffect(chapterSearchQuery) {
                        if (chapterSearchQuery.isNotBlank() && indexItems.isNotEmpty()) {
                            indexListState.scrollToItem(0)
                        } else if (chapterSearchQuery.isBlank() && targetTopicIndex in indexItems.indices) {
                            snapshotFlow { indexListState.layoutInfo.viewportSize.height }
                                .filter { it > 0 }
                                .first()
                            indexListState.scrollToItem(targetTopicIndex)
                        }
                    }
                    
                    LazyColumn(
                        state = indexListState,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(items = indexItems, key = { it.key }) { pathItem ->
                            val isCurrent = (currentPathHeader != null) && (
                                (pathItem.itemIndex != -1 && pathItem.itemIndex == currentPathHeader?.itemIndex) ||
                                (pathItem.key == currentPathHeader?.key)
                            )
                            val indent = (pathItem.level * 16).dp
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val idx = if (pathItem.itemIndex in bookItems.indices && bookItems[pathItem.itemIndex] is ReadBookItem.PathHeader) {
                                            pathItem.itemIndex
                                        } else {
                                            bookItems.indexOfFirst {
                                                it is ReadBookItem.PathHeader && it.key == pathItem.key
                                            }
                                        }
                                        if (idx != -1) {
                                            coroutineScope.launch {
                                                listState.scrollToItem(idx)
                                            }
                                            showChapterIndex = false
                                        }
                                    },
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${pathItem.label}: ${pathItem.title}",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else if (pathItem.level == 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else if (pathItem.level == 0) MaterialTheme.colorScheme.primary else themeColors.text
                                    )
                                }
                            }
                            if (!isCurrent) {
                                HorizontalDivider(modifier = Modifier.padding(start = indent))
                            }
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
                autoSend = autoSendAiPrompt,
                onAutoSendConsumed = {
                    autoSendAiPrompt = false
                },
                onDismiss = {
                    showAiAssistant = false
                    autoSendAiPrompt = false
                },
                onNavigateToAiSettings = onNavigateToAiSettings,
                onNavigateToUsage = onNavigateToUsage,
                                onQuestionsUpdated = { updatedQuestions ->
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.ui.screens.createRestorePoint(context, viewModel.repository, currentBookId, "Pre-AI Update")
                        val normalized = updatedQuestions.map { q ->
                            if (currentBookId.isNotBlank() && q.bookId != currentBookId) {
                                q.copy(bookId = currentBookId)
                            } else q
                        }
                        viewModel.repository.insertQuestions(normalized)
                        viewModel.repository.updateBookStats(currentBookId)
                        val newQuestions = viewModel.repository.getQuestionsForBookSync(currentBookId).resolveHeaders()
                        val updatedBookEntity = viewModel.repository.getBookSync(currentBookId)
                        // Save back to original file as well
                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                        val targetFile = java.io.File(basePath, "Quizer/${currentBookId}.qbook")
                        com.example.ui.screens.exportQBook(context, viewModel.repository, currentBookId, targetFile)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            allQuestions = newQuestions
                            currentBook = updatedBookEntity
                            viewModel.selectBook(currentBookId, forceRefresh = true)
                        }
                    }
                },
                currentContextInfo = contextInfo,
                visibleQuestionNumbers = visibleLocalNumbers.distinct()
            )
        }

        if (editingQuestion != null) {
            com.example.ui.screens.EditQuestionDialog(
                viewModel = viewModel,
                question = editingQuestion!!,
                bookId = currentBookId,
                onDismiss = { editingQuestion = null },
                onSave = { updatedQuestion, startId, endId ->
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        var finalQuestion = updatedQuestion
                        if (finalQuestion.img?.startsWith("http") == true) {
                            val downloaded = com.example.data.ImageDownloader.downloadImage(context, currentBookId, finalQuestion.img!!)
                            if (downloaded != null) finalQuestion = finalQuestion.copy(img = downloaded)
                        }
                        if (finalQuestion.explanationImage?.startsWith("http") == true) {
                            val downloaded = com.example.data.ImageDownloader.downloadImage(context, currentBookId, finalQuestion.explanationImage!!)
                            if (downloaded != null) finalQuestion = finalQuestion.copy(explanationImage = downloaded)
                        }
                        if (startId != null && endId != null) {
                            val sIdx = allQuestions.indexOfFirst { it.id == startId }.takeIf { it != -1 } ?: 0
                            val eIdx = allQuestions.indexOfFirst { it.id == endId }.takeIf { it != -1 } ?: (allQuestions.size - 1)
                            if (sIdx <= eIdx) {
                                val text = finalQuestion.questionHeader ?: ""
                                val headerText = text.takeIf { it.isNotBlank() }
                                val headerJson = if (headerText != null) "{\"startId\": $startId, \"endId\": $endId, \"text\": \"${headerText.replace("\"", "\\\"").replace("\n", "\\n")}\"}" else null
                                
                                for (i in sIdx..eIdx) {
                                    val q = allQuestions[i]
                                    val updatedQ = if (q.id == finalQuestion.id) finalQuestion.copy(questionHeader = headerJson) else q.copy(questionHeader = headerJson)
                                    viewModel.repository.updateQuestion(updatedQ)
                                }
                                if (allQuestions.indexOfFirst { it.id == finalQuestion.id } !in sIdx..eIdx) {
                                    viewModel.repository.updateQuestion(finalQuestion)
                                }
                            }
                        } else {
                            viewModel.repository.updateQuestion(finalQuestion)
                        }

                        // Create restore point
                        com.example.ui.screens.createRestorePoint(context, viewModel.repository, currentBookId, "Edited Q${finalQuestion.id}")

                        // Save back to original file as well
                        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                        val targetFile = java.io.File(basePath, "Quizer/${currentBookId}.qbook")
                        com.example.ui.screens.exportQBook(context, viewModel.repository, currentBookId, targetFile)

                        // Refresh allQuestions in ReadBookScreen
                        val refreshed = viewModel.repository.getQuestionsForBookSync(currentBookId).resolveHeaders()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            allQuestions = refreshed
                            editingQuestion = null
                            android.widget.Toast.makeText(context, "Q.${finalQuestion.id} saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onNavigateToAiSettings = onNavigateToAiSettings,
                onNavigateToUsage = onNavigateToUsage
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
    readingTheme: ReadingTheme = ReadingTheme.Day,
    themeColors: ThemeColors,
    searchQuery: String = "",
    highlightColor: Color = Color.Yellow,
    isBookmarked: Boolean = false,
    showBookmarkHighlight: Boolean = true,
    onBookmarkToggle: (() -> Unit)? = null,
    onEditQuestion: (() -> Unit)? = null,
    onAskAiQuestion: ((QuestionEntity, Int) -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
    onTextClickWithCoords: ((Float, Float) -> Unit)? = null,
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
    
    val correctIndices: Set<Int> = remember(question) {
        question.resolveCorrectIndices(includeExtraOptions = false)
    }
    val isMultiCorrect: Boolean = remember(question, correctIndices) {
        question.isEffectivelyMultipleCorrect(includeExtraOptions = false)
    }

    val highlighterBgColor = when {
        readingTheme == ReadingTheme.Night || isDarkTheme -> Color(0x35FFE082)
        readingTheme == ReadingTheme.Sepia || readingTheme == ReadingTheme.BookPage -> Color(0x35FFD54F)
        else -> Color(0x40FFEB3B)
    }
    val markerBorderColor = Color(0xFFFFB300)
    val shouldHighlight = isBookmarked && showBookmarkHighlight

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .then(
                if (shouldHighlight) {
                    Modifier
                        .background(highlighterBgColor, RoundedCornerShape(10.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = markerBorderColor,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                        }
                } else Modifier
            )
            .padding(horizontal = if (shouldHighlight) 10.dp else 8.dp, vertical = 6.dp)
            .animateContentSize()
    ) {
        val questionCustomMenuItems = remember(isBookmarked, localNumber, onBookmarkToggle, onEditQuestion, onAskAiQuestion) {
            val list = mutableListOf<com.example.ui.components.CustomTextMenuItem>()
            if (onAskAiQuestion != null) {
                list.add(
                    com.example.ui.components.CustomTextMenuItem(
                        id = 99993,
                        title = "Ask AI Q$localNumber",
                        iconRes = com.example.R.drawable.ic_sparkle,
                        showText = false,
                        order = 0,
                        onClick = {
                            onAskAiQuestion.invoke(question, localNumber)
                        }
                    )
                )
            }
            if (onBookmarkToggle != null) {
                list.add(
                    com.example.ui.components.CustomTextMenuItem(
                        id = 99991,
                        title = if (isBookmarked) "Unbookmark" else "Bookmark Q$localNumber",
                        iconRes = if (isBookmarked) com.example.R.drawable.ic_bookmark else com.example.R.drawable.ic_bookmark_border,
                        showText = false,
                        order = 1,
                        onClick = {
                            onBookmarkToggle.invoke()
                        }
                    )
                )
            }
            if (onEditQuestion != null) {
                list.add(
                    com.example.ui.components.CustomTextMenuItem(
                        id = 99992,
                        title = "Edit Q$localNumber",
                        iconRes = com.example.R.drawable.ic_edit,
                        showText = false,
                        order = 2,
                        onClick = {
                            onEditQuestion.invoke()
                        }
                    )
                )
            }
            list
        }
        
        com.example.ui.components.SmartRichText(
            text = "<b>Q.$localNumber</b> ${question.question}",
            fontSize = 20.sp,
            color = themeColors.text,
            searchQuery = searchQuery,
            highlightColor = highlightColor,
            isBookmarked = isBookmarked,
            onBookmarkToggle = onBookmarkToggle,
            customMenuItems = questionCustomMenuItems,
            onClick = onTextClick,
            onClickWithCoords = onTextClickWithCoords
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
                com.example.ui.components.TableRenderer(
                    tableData = tableData,
                    textColor = themeColors.text,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    customMenuItems = questionCustomMenuItems,
                    onClick = onTextClick,
                    onClickWithCoords = onTextClickWithCoords
                )
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
                customMenuItems = questionCustomMenuItems,
                onClick = onTextClick,
                onClickWithCoords = onTextClickWithCoords
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
        
        val isOptionClickable = inBookQuizMode && !isRevealed

        optionsList.forEachIndexed { displayIndex, (index, optionText) ->
            val isCorrect = correctIndices.contains(index)
            val isSelected = selectedOptionIndices.contains(index)
            // In in-book quiz mode, an unselected option remains clickable until the question is fully revealed
            val isThisOptionClickable = inBookQuizMode && !isRevealed && !isSelected

            val textColor = when {
                isRevealed -> {
                    // Full reveal: all correct options turn green; any wrong option the user clicked turns red
                    if (isCorrect) Color(0xFF4CAF50)
                    else if (isSelected) Color(0xFFE53935)
                    else themeColors.text
                }
                isSelected && isCorrect -> {
                    // Progressive multi-correct selection before full reveal:
                    // selected correct option turns green immediately while unselected correct options remain hidden!
                    Color(0xFF4CAF50)
                }
                isSelected && !isCorrect -> {
                    Color(0xFFE53935)
                }
                else -> themeColors.text
            }

            val selectOptionAction = {
                if (isMultiCorrect) {
                    if (!isSelected && !isRevealed) {
                        val updatedSelection = selectedOptionIndices + index
                        selectedOptionIndices = updatedSelection
                        val pickedWrong = !isCorrect
                        val allCorrectPicked = correctIndices.isNotEmpty() && correctIndices.all { updatedSelection.contains(it) }
                        // Reveal and expand explanation ONLY if:
                        // - User picked a wrong option (Case 1 & Case 2), OR
                        // - User has now selected ALL correct options without picking a wrong one (Case 3)
                        if (pickedWrong || allCorrectPicked) {
                            isRevealed = true
                        }
                    }
                } else {
                    selectedOptionIndices = setOf(index)
                    isRevealed = true
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isThisOptionClickable) {
                            Modifier.clickable { selectOptionAction() }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(65 + displayIndex).toChar()}.",
                    fontWeight = FontWeight.Normal,
                    color = textColor,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .align(Alignment.Top)
                        .padding(end = 8.dp)
                )
                com.example.ui.components.SmartRichText(
                    text = optionText,
                    fontSize = 18.sp,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    customMenuItems = questionCustomMenuItems,
                    isSelectable = !isThisOptionClickable,
                    onClick = if (isThisOptionClickable) {
                        null
                    } else {
                        onTextClick
                    },
                    onClickWithCoords = if (isThisOptionClickable) {
                        null
                    } else {
                        onTextClickWithCoords
                    }
                )
            }
        }
        
        val safeExplanation = question.explanation.takeIf { it.isNotBlank() && !it.trim().equals("null", ignoreCase = true) }
        val safeDetailed = question.detailedExplanation?.takeIf { it.isNotBlank() && !it.trim().equals("null", ignoreCase = true) }
        val hasBrief = safeExplanation != null && (showBriefExplanation || safeDetailed == null)
        val hasDetailed = safeDetailed != null

        AnimatedVisibility(visible = isRevealed && (hasBrief || hasDetailed)) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
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
                        text = safeExplanation!!,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        customMenuItems = questionCustomMenuItems,
                        onClick = onTextClick,
                        onClickWithCoords = onTextClickWithCoords
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Detailed:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = themeColors.text)
                    com.example.ui.components.SmartRichText(
                        text = safeDetailed!!,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        customMenuItems = questionCustomMenuItems,
                        onClick = onTextClick,
                        onClickWithCoords = onTextClickWithCoords
                    )
                } else if (hasBrief) {
                    com.example.ui.components.SmartRichText(
                        text = safeExplanation!!,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        customMenuItems = questionCustomMenuItems,
                        onClick = onTextClick,
                        onClickWithCoords = onTextClickWithCoords
                    )
                } else if (hasDetailed) {
                    com.example.ui.components.SmartRichText(
                        text = safeDetailed!!,
                        fontSize = 16.sp,
                        color = themeColors.text,
                        modifier = Modifier.padding(top = 4.dp),
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        customMenuItems = questionCustomMenuItems,
                        onClick = onTextClick,
                        onClickWithCoords = onTextClickWithCoords
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
                        com.example.ui.components.TableRenderer(
                            tableData = expTableData,
                            textColor = themeColors.text,
                            searchQuery = searchQuery,
                            highlightColor = highlightColor,
                            customMenuItems = questionCustomMenuItems,
                            onClick = onTextClick,
                            onClickWithCoords = onTextClickWithCoords
                        )
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

        if (isRevealed && safeExplanation == null && safeDetailed == null && onAskAiQuestion != null) {
            Surface(
                onClick = { onAskAiQuestion.invoke(question, localNumber) },
                shape = RoundedCornerShape(12.dp),
                color = themeColors.primary.copy(alpha = 0.1f),
                modifier = Modifier.padding(top = 8.dp).height(30.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.AutoAwesome,
                        contentDescription = "Ask AI",
                        tint = themeColors.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ask AI for Explanation",
                        style = MaterialTheme.typography.labelMedium,
                        color = themeColors.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

