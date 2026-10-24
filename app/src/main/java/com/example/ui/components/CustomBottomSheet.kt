package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    maxHeightFraction: Float = 0.80f,
    onDismissRequest: () -> Unit,
    enableDynamicHeight: Boolean = true,
    fillMaxSheetHeight: Boolean = false,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hostView = androidx.compose.ui.platform.LocalView.current

    val rootWindowInsets = androidx.core.view.ViewCompat.getRootWindowInsets(hostView)
    val rootStatusBarPx = rootWindowInsets
        ?.getInsetsIgnoringVisibility(
            androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )?.top ?: 0
    val rootNavBarPx = rootWindowInsets
        ?.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        ?.bottom ?: 0
    val rootImePx = rootWindowInsets
        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
        ?.bottom ?: 0

    val composeStatusBarPx = WindowInsets.statusBars.getTop(density)
    val composeNavBarPx = WindowInsets.navigationBars.getBottom(density)
    val composeImePx = WindowInsets.ime.getBottom(density)

    val statusBarHeightDp = maxOf(
        with(density) { maxOf(rootStatusBarPx, composeStatusBarPx).toDp() },
        28.dp
    )
    val navBarHeightDp = with(density) { maxOf(rootNavBarPx, composeNavBarPx).toDp() }
    val imeHeightDp = with(density) { maxOf(rootImePx, composeImePx).toDp() }
    val isImeVisible = imeHeightDp > navBarHeightDp + 24.dp

    val screenHeightDp = configuration.screenHeightDp.dp
    val fullWindowHeightDp = with(density) {
        hostView.rootView.height.takeIf { it > 0 }?.toDp()
    } ?: (screenHeightDp + statusBarHeightDp + navBarHeightDp)
    val screenHeightPx = with(density) { fullWindowHeightDp.toPx().coerceAtLeast(1f) }

    val defaultExpandedFraction = maxHeightFraction.coerceIn(0.55f, 0.94f)
    val fullExpandedFraction = maxOf(defaultExpandedFraction, 0.92f)
    val halfExpandedFraction = 0.55f
    val dismissThresholdFraction = 0.28f

    val sheetFractionAnim = remember(defaultExpandedFraction) {
        Animatable(defaultExpandedFraction)
    }

    var isDraggingHandle by remember { mutableStateOf(false) }
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var measuredSheetHeightPx by remember { mutableIntStateOf(0) }

    // When content shrinks back to the mid-screen base state (e.g. after "Clear Chat") while keyboard is closed,
    // reset max cap to defaultExpandedFraction (90%) so the sheet smoothly expands from 55% up to 90% as new chat messages arrive.
    LaunchedEffect(measuredSheetHeightPx, isDraggingHandle, isImeVisible) {
        if (!isDraggingHandle && !isImeVisible && measuredSheetHeightPx > 0 && !fillMaxSheetHeight) {
            val visibleFrac = measuredSheetHeightPx.toFloat() / screenHeightPx
            if (visibleFrac <= halfExpandedFraction + 0.03f && sheetFractionAnim.value < defaultExpandedFraction) {
                sheetFractionAnim.snapTo(defaultExpandedFraction)
            }
        }
    }

    // NestedScrollConnection that swallows any unconsumed vertical scroll/fling inside the sheet content
    // so scrolling upwards or downwards inside LazyColumn / verticalScroll / RichText NEVER drags or closes the sheet.
    val contentScrollGuard = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                return Velocity(0f, available.y)
            }
        }
    }

    val view = LocalView.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun safeHideKeyboard() {
        try {
            if (view.windowToken != null) {
                keyboardController?.hide()
            }
        } catch (_: Exception) {}
        try {
            focusManager.clearFocus()
        } catch (_: Exception) {}
    }

    fun dismissSheetSmoothly() {
        coroutineScope.launch {
            safeHideKeyboard()
            try {
                sheetState.hide()
            } catch (_: Exception) {
            } finally {
                onDismissRequest()
            }
        }
    }

    fun snapSheetTo(targetFraction: Float) {
        coroutineScope.launch {
            sheetFractionAnim.animateTo(
                targetValue = targetFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun resolveEffectiveStartFraction(): Float {
        if (measuredSheetHeightPx <= 0) return sheetFractionAnim.value
        val actualVisibleFraction = (measuredSheetHeightPx.toFloat() / screenHeightPx)
            .coerceIn(0.14f, fullExpandedFraction)
        return minOf(sheetFractionAnim.value, actualVisibleFraction)
    }

    fun finishHandleDrag(startFraction: Float, endFraction: Float, velocityY: Float) {
        val fastFlingPxPerSec = with(density) { 1050.dp.toPx() }
        val hardDismissFlingPxPerSec = with(density) { 2100.dp.toPx() }
        val draggedDownFraction = startFraction - endFraction

        if (!enableDynamicHeight) {
            if (draggedDownFraction > 0.10f || velocityY > fastFlingPxPerSec) {
                dismissSheetSmoothly()
            } else {
                snapSheetTo(defaultExpandedFraction)
            }
            return
        }

        val shouldDismiss =
            endFraction < minOf(dismissThresholdFraction, startFraction - 0.06f) ||
            (startFraction <= halfExpandedFraction + 0.04f && draggedDownFraction > 0.08f) ||
            velocityY > hardDismissFlingPxPerSec ||
            (velocityY > fastFlingPxPerSec && (startFraction <= halfExpandedFraction + 0.06f || endFraction < halfExpandedFraction))

        when {
            shouldDismiss -> dismissSheetSmoothly()
            velocityY > fastFlingPxPerSec -> snapSheetTo(halfExpandedFraction)
            velocityY < -fastFlingPxPerSec -> {
                val upwardTarget = if (endFraction > defaultExpandedFraction + 0.03f) {
                    fullExpandedFraction
                } else {
                    defaultExpandedFraction
                }
                snapSheetTo(upwardTarget)
            }
            else -> {
                // If sheet was compact (e.g. empty chat) and user did not drag down enough to dismiss,
                // restore cap to defaultExpandedFraction so future chats can grow normally.
                if (startFraction < halfExpandedFraction - 0.05f && endFraction >= startFraction - 0.06f) {
                    snapSheetTo(defaultExpandedFraction)
                } else {
                    val midPoint = (halfExpandedFraction + defaultExpandedFraction) * 0.5f
                    val snapTarget = when {
                        endFraction < midPoint -> halfExpandedFraction
                        endFraction > (defaultExpandedFraction + fullExpandedFraction) * 0.5f -> fullExpandedFraction
                        else -> defaultExpandedFraction
                    }
                    snapSheetTo(snapTarget)
                }
            }
        }
    }

    val pillWidth by animateDpAsState(
        targetValue = if (isDraggingHandle) 46.dp else 34.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "handlePillWidth"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (isDraggingHandle) 0.85f else 0.42f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "handlePillAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        // Disable ModalBottomSheet's internal body swipe so scrolling inner content never closes the sheet
        sheetGesturesEnabled = false,
        // CRITICAL: Exclude WindowInsetsSides.Top so ModalBottomSheet NEVER adds status-bar height above the drag pill!
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .union(WindowInsets.ime)
        },
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp, bottom = 3.dp)
                    .onGloballyPositioned { handleCoords = it }
                    .pointerInput(enableDynamicHeight, defaultExpandedFraction, screenHeightPx) {
                        val touchSlopPx = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val initialScreenY = handleCoords?.localToScreen(down.position)?.y ?: down.position.y
                            val startFraction = resolveEffectiveStartFraction()
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, Offset(0f, initialScreenY))

                            var dragStarted = false
                            var lastHapticSnapZone = when {
                                startFraction < dismissThresholdFraction -> 0
                                startFraction < (halfExpandedFraction + defaultExpandedFraction) * 0.5f -> 1
                                else -> 2
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    val currentScreenY = handleCoords?.localToScreen(change.position)?.y
                                        ?: (initialScreenY + (change.position.y - down.position.y))
                                    val totalDeltaY = currentScreenY - initialScreenY

                                    if (!dragStarted && abs(totalDeltaY) > touchSlopPx * 0.5f) {
                                        dragStarted = true
                                        isDraggingHandle = true
                                    }

                                    if (dragStarted) {
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, currentScreenY))

                                        val deltaFraction = totalDeltaY / screenHeightPx
                                        val minAllowed = 0.10f
                                        val maxAllowed = if (enableDynamicHeight) fullExpandedFraction else defaultExpandedFraction
                                        val targetFraction = (startFraction - deltaFraction).coerceIn(minAllowed, maxAllowed)

                                        val currentZone = when {
                                            targetFraction < dismissThresholdFraction -> 0
                                            targetFraction < (halfExpandedFraction + defaultExpandedFraction) * 0.5f -> 1
                                            else -> 2
                                        }
                                        if (currentZone != lastHapticSnapZone) {
                                            lastHapticSnapZone = currentZone
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }

                                        coroutineScope.launch {
                                            sheetFractionAnim.snapTo(targetFraction)
                                        }
                                    } else if (change.positionChange() != Offset.Zero) {
                                        change.consume()
                                    }
                                }
                            } finally {
                                isDraggingHandle = false
                            }

                            if (!dragStarted) {
                                if (enableDynamicHeight) {
                                    val current = resolveEffectiveStartFraction()
                                    val nextTarget = if (abs(current - halfExpandedFraction) < 0.08f) {
                                        defaultExpandedFraction
                                    } else {
                                        halfExpandedFraction
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    snapSheetTo(nextTarget)
                                }
                            } else {
                                val velocityY = velocityTracker.calculateVelocity().y
                                finishHandleDrag(startFraction, sheetFractionAnim.value, velocityY)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(4.dp)
                        .background(
                            color = if (isDraggingHandle) {
                                MaterialTheme.colorScheme.primary.copy(alpha = pillAlpha)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pillAlpha)
                            },
                            shape = CircleShape
                        )
                )
            }
        },
        content = {
            val view = LocalView.current
            LaunchedEffect(view) {
                val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                if (window != null) {
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
            }

            val dialogInsets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            val dialogStatusBarDp = with(density) {
                (dialogInsets?.getInsetsIgnoringVisibility(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                )?.top ?: 0).toDp()
            }
            val dialogNavBarDp = with(density) {
                (dialogInsets?.getInsetsIgnoringVisibility(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                )?.bottom ?: 0).toDp()
            }
            val dialogImeDp = with(density) {
                (dialogInsets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0).toDp()
            }

            val effectiveStatusBarDp = maxOf(statusBarHeightDp, dialogStatusBarDp, 28.dp)
            val effectiveBottomInsetDp = maxOf(
                navBarHeightDp,
                imeHeightDp,
                dialogNavBarDp,
                dialogImeDp,
                WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
            val isKeyboardOpenNow = maxOf(imeHeightDp, dialogImeDp, WindowInsets.ime.asPaddingValues().calculateBottomPadding()) > 60.dp

            // Always stop cleanly BEFORE the notification/status bar (statusBar + 18.dp gap below status bar,
            // or the sheet's top margin (1f - sheetFractionAnim.value), whichever is larger).
            val minTopClearanceFromScreenTopDp = maxOf(
                effectiveStatusBarDp + 18.dp,
                fullWindowHeightDp * (1f - fullExpandedFraction)
            )
            val dragHandleHeightDp = 16.dp

            // Maximum height for the inner Column so that (Column + dragHandle + bottomInset) NEVER crosses
            // into the status/notification bar even when the software keyboard is open!
            val maxAllowedColumnHeightDp = (
                fullWindowHeightDp - minTopClearanceFromScreenTopDp - effectiveBottomInsetDp - dragHandleHeightDp
            ).coerceAtLeast(140.dp)

            val desiredColumnHeightDp = (
                (fullWindowHeightDp * sheetFractionAnim.value) - effectiveBottomInsetDp - dragHandleHeightDp
            ).coerceAtLeast(140.dp)

            val targetHeight = minOf(desiredColumnHeightDp, maxAllowedColumnHeightDp)
            val headerDragZonePx = with(density) { 44.dp.toPx() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (fillMaxSheetHeight) {
                            Modifier.height(targetHeight)
                        } else {
                            // Starts at the child's minimum height (55% mid-screen when keyboard is closed) and
                            // expands upward to targetHeight without fighting LazyColumn scroll frames!
                            Modifier.heightIn(max = targetHeight)
                        }
                    )
                    .onGloballyPositioned { contentCoords = it }
                    .onSizeChanged { measuredSheetHeightPx = it.height }
                    // Also allow dragging the sheet from the top 44.dp title/header bar right below the pill
                    // without interfering with taps on header buttons (menu, close, tabs).
                    .pointerInput(enableDynamicHeight, defaultExpandedFraction, screenHeightPx) {
                        val touchSlopPx = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(
                                pass = PointerEventPass.Initial,
                                requireUnconsumed = false
                            )
                            if (down.position.y > headerDragZonePx) {
                                return@awaitEachGesture
                            }

                            val initialScreenY = contentCoords?.localToScreen(down.position)?.y ?: down.position.y
                            val startFraction = resolveEffectiveStartFraction()
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, Offset(0f, initialScreenY))

                            var dragStarted = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        if (dragStarted) change.consume()
                                        break
                                    }

                                    val currentScreenY = contentCoords?.localToScreen(change.position)?.y
                                        ?: (initialScreenY + (change.position.y - down.position.y))
                                    val totalDeltaY = currentScreenY - initialScreenY
                                    val totalDeltaX = change.position.x - down.position.x

                                    if (!dragStarted) {
                                        if (abs(totalDeltaY) > touchSlopPx && abs(totalDeltaY) > abs(totalDeltaX) * 1.2f) {
                                            dragStarted = true
                                            isDraggingHandle = true
                                        } else if (abs(totalDeltaX) > touchSlopPx) {
                                            break
                                        }
                                    }

                                    if (dragStarted) {
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, currentScreenY))
                                        val deltaFraction = totalDeltaY / screenHeightPx
                                        val minAllowed = 0.10f
                                        val maxAllowed = if (enableDynamicHeight) fullExpandedFraction else defaultExpandedFraction
                                        val targetFraction = (startFraction - deltaFraction).coerceIn(minAllowed, maxAllowed)
                                        coroutineScope.launch {
                                            sheetFractionAnim.snapTo(targetFraction)
                                        }
                                    }
                                }
                            } finally {
                                if (dragStarted) {
                                    isDraggingHandle = false
                                }
                            }

                            if (dragStarted) {
                                val velocityY = velocityTracker.calculateVelocity().y
                                finishHandleDrag(startFraction, sheetFractionAnim.value, velocityY)
                            }
                        }
                    }
                    .nestedScroll(contentScrollGuard)
            ) {
                content()
            }
        }
    )
}
