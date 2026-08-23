package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    maxHeightFraction: Float = 0.8f,
    onDismissRequest: () -> Unit,
    enableDynamicHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentFraction by remember(maxHeightFraction, enableDynamicHeight) { 
        mutableStateOf(if (enableDynamicHeight) 0.45f else maxHeightFraction) 
    }
    val animatedFraction by animateFloatAsState(targetValue = currentFraction, label = "sheetHeightAnim")

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        contentWindowInsets = { WindowInsets.safeDrawing },
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (enableDynamicHeight) {
                                    currentFraction = if (currentFraction == 0.45f) maxHeightFraction else 0.45f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var dragY = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragY > 40f) { // dragged down
                                    if (enableDynamicHeight && currentFraction == maxHeightFraction) {
                                        currentFraction = 0.45f
                                    } else {
                                        coroutineScope.launch {
                                            sheetState.hide()
                                            onDismissRequest()
                                        }
                                    }
                                } else if (dragY < -40f) { // dragged up
                                    if (enableDynamicHeight && currentFraction == 0.45f) {
                                        currentFraction = maxHeightFraction
                                    }
                                }
                                dragY = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragY += dragAmount
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        content = {
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            val targetHeight = screenHeight * animatedFraction
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = targetHeight)
            ) {
                content()
            }
        }
    )
}
