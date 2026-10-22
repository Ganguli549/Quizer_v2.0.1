package com.example.ui.components

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx().coerceAtLeast(1f) }

    val defaultExpandedFraction = maxHeightFraction.coerceIn(0.55f, 0.94f)
    val fullExpandedFraction = maxOf(defaultExpandedFraction, 0.92f)
    val halfExpandedFraction = 0.50f
    val dismissThresholdFraction = 0.30f

    val sheetFractionAnim = remember(defaultExpandedFraction) {
        Animatable(defaultExpandedFraction)
    }

    var isDraggingHandle by remember { mutableStateOf(false) }
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // NestedScrollConnection that swallows any unconsumed vertical scroll/fling inside the sheet content
    // so scrolling upwards or downwards inside LazyColumn / verticalScroll / RichText NEVER drags or closes the sheet.
    val contentScrollGuard = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Consume any leftover vertical scroll at the top/bottom edges of inner scrollables
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // Consume leftover vertical fling velocity so it never propagates to ModalBottomSheet
                return Velocity(0f, available.y)
            }
        }
    }

    fun dismissSheetSmoothly() {
        coroutineScope.launch {
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

    val pillWidth by animateDpAsState(
        targetValue = if (isDraggingHandle) 48.dp else 36.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "handlePillWidth"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (isDraggingHandle) 0.85f else 0.45f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "handlePillAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // Disable ModalBottomSheet's internal body swipe so scrolling inner content never closes the sheet
        sheetGesturesEnabled = false,
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)
                .union(WindowInsets.ime)
        },
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .onGloballyPositioned { handleCoords = it }
                    .pointerInput(enableDynamicHeight, defaultExpandedFraction, screenHeightPx) {
                        val touchSlopPx = viewConfiguration.touchSlop
                        val fastFlingPxPerSec = with(density) { 1050.dp.toPx() }
                        val hardDismissFlingPxPerSec = with(density) { 2100.dp.toPx() }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val initialScreenY = handleCoords?.localToScreen(down.position)?.y ?: down.position.y
                            val startFraction = sheetFractionAnim.value
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

                                    if (!dragStarted && abs(totalDeltaY) > touchSlopPx * 0.6f) {
                                        dragStarted = true
                                        isDraggingHandle = true
                                    }

                                    if (dragStarted) {
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, currentScreenY))

                                        // Positive totalDeltaY = finger moved DOWN (shrink sheet)
                                        // Negative totalDeltaY = finger moved UP (expand sheet)
                                        val deltaFraction = totalDeltaY / screenHeightPx
                                        val minAllowed = if (enableDynamicHeight) 0.14f else (defaultExpandedFraction * 0.25f)
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
                                // Single tap on drag handle: toggle smoothly between Half-Expanded (50%) and Default Expanded
                                if (enableDynamicHeight) {
                                    val current = sheetFractionAnim.value
                                    val nextTarget = if (abs(current - halfExpandedFraction) < 0.08f) {
                                        defaultExpandedFraction
                                    } else {
                                        halfExpandedFraction
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    snapSheetTo(nextTarget)
                                }
                            } else {
                                val velocityY = velocityTracker.calculateVelocity().y // +px/s = downward, -px/s = upward
                                val endFraction = sheetFractionAnim.value

                                if (!enableDynamicHeight) {
                                    if (endFraction < defaultExpandedFraction * 0.65f || velocityY > fastFlingPxPerSec) {
                                        dismissSheetSmoothly()
                                    } else {
                                        snapSheetTo(defaultExpandedFraction)
                                    }
                                } else {
                                    when {
                                        // 1. Dragged below dismiss threshold or flung hard downward -> Dismiss
                                        endFraction < dismissThresholdFraction ||
                                            velocityY > hardDismissFlingPxPerSec ||
                                            (velocityY > fastFlingPxPerSec && (startFraction <= halfExpandedFraction + 0.06f || endFraction < halfExpandedFraction)) -> {
                                            dismissSheetSmoothly()
                                        }
                                        // 2. Moderate downward fling from expanded state -> Snap to Half-Expanded (50%)
                                        velocityY > fastFlingPxPerSec -> {
                                            snapSheetTo(halfExpandedFraction)
                                        }
                                        // 3. Upward fling -> Snap to Default Expanded (or Full 92% if dragged above defaultExpandedFraction)
                                        velocityY < -fastFlingPxPerSec -> {
                                            val upwardTarget = if (endFraction > defaultExpandedFraction + 0.03f) {
                                                fullExpandedFraction
                                            } else {
                                                defaultExpandedFraction
                                            }
                                            snapSheetTo(upwardTarget)
                                        }
                                        // 4. Positional snap based on release height
                                        else -> {
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
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(4.5.dp)
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
        containerColor = MaterialTheme.colorScheme.surface,
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

            val targetHeight = screenHeightDp * sheetFractionAnim.value

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (enableDynamicHeight) {
                            Modifier.height(targetHeight)
                        } else {
                            Modifier.heightIn(max = targetHeight)
                        }
                    )
                    .nestedScroll(contentScrollGuard)
            ) {
                content()
            }
        }
    )
}
