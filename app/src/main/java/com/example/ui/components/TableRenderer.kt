package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout
import kotlin.math.max

@Composable
fun TableRenderer(tableData: List<List<String>>, textColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.LocalContentColor.current, searchQuery: String = "") {
    if (tableData.isEmpty()) return
    val borderColor = textColor.copy(alpha = 0.2f)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    
    Box(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor)
    ) {
        Layout(
            content = {
                tableData.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { colIndex, cellText ->
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, borderColor)
                                .padding(8.dp)
                        ) {
                            RichText(
                                text = cellText,
                                fontSize = 16.sp,
                                color = textColor,
                                searchQuery = searchQuery
                            )
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val numRows = tableData.size
            val numCols = tableData.maxOfOrNull { it.size } ?: 0
            
            val screenWidthPx = constraints.maxWidth.let { 
                if (it == androidx.compose.ui.unit.Constraints.Infinity || it == 0) (screenWidthDp * 0.95f).roundToPx() else it 
            }
            
            val minColWidths = IntArray(numCols) { 0 }
            val maxColWidths = IntArray(numCols) { 0 }
            val colCharCounts = IntArray(numCols) { 0 }
            
            val absoluteMaxColumnWidth = (screenWidthDp * 0.55f).roundToPx()
            
            var i = 0
            for (r in 0 until numRows) {
                for (c in 0 until tableData[r].size) {
                    val minW = measurables[i].minIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
                    val maxW = measurables[i].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
                    
                    minColWidths[c] = max(minColWidths[c], minW)
                    maxColWidths[c] = max(maxColWidths[c], maxW)
                    colCharCounts[c] += tableData[r][c].length
                    i++
                }
            }
            
            // Calculate "Water Balloon" Expansion Factor (Square Root of Bulk)
            // Using Square Root causes Width to grow slower than Height for large text volumes.
            // This pulls the text vertically (like gravity) rather than spreading it perfectly horizontally.
            val balloonFactors = FloatArray(numCols) { 0f }
            var totalBalloonFactor = 0f
            for (c in 0 until numCols) {
                balloonFactors[c] = kotlin.math.sqrt(colCharCounts[c].toFloat())
                totalBalloonFactor += balloonFactors[c]
                
                maxColWidths[c] = kotlin.math.min(maxColWidths[c], absoluteMaxColumnWidth)
                minColWidths[c] = kotlin.math.min(minColWidths[c], absoluteMaxColumnWidth)
            }
            
            val totalMaxWidth = maxColWidths.sum()
            val totalMinWidth = minColWidths.sum()
            val colWidths = IntArray(numCols) { 0 }
            
            if (totalMaxWidth <= screenWidthPx) {
                // Table fits perfectly without wrapping
                for (c in 0 until numCols) colWidths[c] = maxColWidths[c]
            } else if (totalMinWidth >= screenWidthPx) {
                // Table is huge even at minimum widths, must horizontally scroll
                for (c in 0 until numCols) colWidths[c] = minColWidths[c]
            } else {
                // Table needs to wrap text. Distribute space based on "Balloon Factor".
                var remainingSpace = screenWidthPx - totalMinWidth
                var unallocatedFactor = totalBalloonFactor.coerceAtLeast(0.001f)
                val columnsToProcess = (0 until numCols).toMutableList()
                
                for (c in 0 until numCols) colWidths[c] = minColWidths[c]
                
                while (remainingSpace > 0 && columnsToProcess.isNotEmpty() && unallocatedFactor > 0f) {
                    val currentSpace = remainingSpace
                    var spaceAllocatedInRound = 0
                    
                    val iterator = columnsToProcess.iterator()
                    while (iterator.hasNext()) {
                        val c = iterator.next()
                        val extra = (currentSpace * (balloonFactors[c] / unallocatedFactor)).toInt()
                        val spaceNeeded = maxColWidths[c] - colWidths[c]
                        
                        if (extra >= spaceNeeded) {
                            colWidths[c] = maxColWidths[c]
                            spaceAllocatedInRound += spaceNeeded
                            unallocatedFactor -= balloonFactors[c]
                            iterator.remove()
                        } else {
                            colWidths[c] += extra
                            spaceAllocatedInRound += extra
                        }
                    }
                    
                    remainingSpace -= spaceAllocatedInRound
                    if (spaceAllocatedInRound == 0) break // prevent infinite loop from rounding errors
                }
            }
            
            // Second pass: Ask for intrinsic height given the calculated column width
            val rowHeights = IntArray(numRows) { 0 }
            i = 0
            for (r in 0 until numRows) {
                for (c in 0 until tableData[r].size) {
                    val h = measurables[i].minIntrinsicHeight(colWidths[c])
                    rowHeights[r] = max(rowHeights[r], h)
                    i++
                }
            }
            
            // Third pass: Actually measure with exact width AND exact height so borders align perfectly
            i = 0
            val finalPlaceables = measurables.map { measurable ->
                var r = 0
                var c = 0
                var tempIndex = i
                for (rowIndex in 0 until numRows) {
                    if (tempIndex < tableData[rowIndex].size) {
                        r = rowIndex
                        c = tempIndex
                        break
                    } else {
                        tempIndex -= tableData[rowIndex].size
                    }
                }
                val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(colWidths[c], rowHeights[r]))
                i++
                p
            }
            
            val totalWidth = colWidths.sum()
            val totalHeight = rowHeights.sum()
            
            layout(totalWidth, totalHeight) {
                var y = 0
                var j = 0
                for (r in 0 until numRows) {
                    var x = 0
                    val rowHeight = rowHeights[r]
                    for (c in 0 until tableData[r].size) {
                        val placeable = finalPlaceables[j++]
                        placeable.placeRelative(x, y)
                        x += colWidths[c]
                    }
                    y += rowHeight
                }
            }
        }
    }
}
