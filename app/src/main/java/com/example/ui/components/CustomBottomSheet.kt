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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    maxHeightFraction: Float = 0.8f,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        contentWindowInsets = { WindowInsets.safeDrawing },
        dragHandle = { 
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Increase touch target for the handle
                    .padding(vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                coroutineScope.launch {
                                    sheetState.hide()
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var dragY = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                // If dragged down by at least 40 pixels, dismiss the sheet
                                if (dragY > 40f) {
                                    coroutineScope.launch {
                                        sheetState.hide()
                                        onDismissRequest()
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
            val maxHeight = screenHeight * maxHeightFraction
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
            ) {
                content()
            }
        }
    )
}
