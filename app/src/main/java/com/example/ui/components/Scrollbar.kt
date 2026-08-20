package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp

fun Modifier.scrollbar(
    state: LazyListState,
    horizontal: Boolean = false,
    color: Color = Color.Gray.copy(alpha = 0.5f)
): Modifier = this.drawWithContent {
    drawContent()
    val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
    val needDrawScrollbar = state.isScrollInProgress || firstVisibleElementIndex != null

    if (needDrawScrollbar && firstVisibleElementIndex != null) {
        val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
        val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

        drawRect(
            color = color,
            topLeft = Offset(this.size.width - 4.dp.toPx(), scrollbarOffsetY),
            size = Size(4.dp.toPx(), scrollbarHeight),
            style = Fill
        )
    }
}
