package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CustomModalBottomSheet(
    maxHeightFraction: Float = 0.8f,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val animateTo = remember { Animatable(1f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateTo.animateTo(0f, animationSpec = tween(250))
    }

    fun dismissWithAnimation() {
        if (isDismissing) return
        isDismissing = true
        coroutineScope.launch {
            animateTo.animateTo(1f, animationSpec = tween(250))
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = { onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val configuration = LocalConfiguration.current
        val screenHeightDp = configuration.screenHeightDp.dp
        val density = LocalDensity.current
        val sheetHeightDp = screenHeightDp * maxHeightFraction
        val sheetHeightPx = with(density) { sheetHeightDp.toPx() }
        
        val dragOffsetY = remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * (1f - animateTo.value)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismissWithAnimation() }
                    )
            )

            // Sheet Content
            val currentOffsetY = (sheetHeightPx * animateTo.value) + dragOffsetY.value
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .offset { IntOffset(0, currentOffsetY.roundToInt()) }
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
                        .fillMaxSize()
                        .navigationBarsPadding() 
                ) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    val newVal = dragOffsetY.value + delta
                                    dragOffsetY.value = max(0f, newVal)
                                },
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    if (dragOffsetY.value > sheetHeightPx / 3 || velocity > 1000f) {
                                        dismissWithAnimation()
                                    } else {
                                        coroutineScope.launch {
                                            val snapAnim = Animatable(dragOffsetY.value)
                                            snapAnim.animateTo(0f, tween(300)) {
                                                dragOffsetY.value = value
                                            }
                                        }
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
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
