package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CustomModalBottomSheet(maxHeightFraction: Float = 0.8f, 
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var sheetHeight by remember { mutableStateOf(0f) }
    val offsetY = remember { Animatable(10000f) }
    var isVisible by remember { mutableStateOf(false) }
    
    // Popup receives the Activity WindowInsets properly.
    // safeDrawing includes the keyboard and navigation bar.
    val bottomPadding = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(sheetHeight) {
        if (sheetHeight > 0f && !isVisible) {
            offsetY.snapTo(sheetHeight)
            offsetY.animateTo(0f)
            isVisible = true
        }
    }

    fun dismiss() {
        coroutineScope.launch {
            offsetY.animateTo(sheetHeight)
            onDismissRequest()
        }
    }

    androidx.activity.compose.BackHandler(enabled = isVisible) {
        dismiss()
    }

    Popup(
        onDismissRequest = { dismiss() },
        properties = PopupProperties(focusable = true, excludeFromSystemGesture = true, clippingEnabled = false)
    ) {
        // Force the Popup content to fill the screen explicitly.
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        
        Box(
            modifier = Modifier
                .width(screenWidth)
                .height(screenHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismiss() }
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isVisible) 0.5f else 0f))
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeight = it.height.toFloat() }
                    .offset {
                        IntOffset(x = 0, y = max(0f, offsetY.value).roundToInt())
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * maxHeightFraction)
                        .padding(bottom = bottomPadding)
                ) {
                    val decay = rememberSplineBasedDecay<Float>()
                    val draggableState = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val newValue = max(0f, offsetY.value + delta)
                            offsetY.snapTo(newValue)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .draggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    val target = decay.calculateTargetValue(offsetY.value, velocity)
                                    if (target > sheetHeight / 2 || velocity > 1000f) {
                                        dismiss()
                                    } else {
                                        offsetY.animateTo(0f)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    content()
                }
            }
        }
    }
}
