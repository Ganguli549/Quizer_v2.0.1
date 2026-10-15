package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BLOCK_MATH_REGEX = Regex(
    "(?s)```(?:math|latex)\\s*[\\s\\S]*?```" +
    "|\\\\\\[[\\s\\S]*?\\\\\\]" +
    "|\\\\begin\\{(?:equation|align|aligned|gather|matrix|pmatrix|bmatrix)\\*?\\}[\\s\\S]*?\\\\end\\{(?:equation|align|aligned|gather|matrix|pmatrix|bmatrix)\\*?\\}" +
    "|\\$\\$[\\s\\S]+?\\$\\$" +
    "|(?<![\\$\\w\\\\])\\$(?!\\$)(?:[^\\$\\n\\\\]|\\\\.)+?\\$(?![\\$0-9])"
)

private val TABLE_REGEX = Regex("(?m)^(?:[ \\t]*\\|.*\\|[ \\t]*(?:\\r?\\n|$))+")

private fun isScrollableMathFormula(matchStr: String): Boolean {
    if (matchStr.startsWith("```") || matchStr.startsWith("\\[") || matchStr.startsWith("\\begin") || matchStr.startsWith("$$")) {
        return true
    }
    val inner = matchStr.trim('$').trim()
    if (inner.length >= 25) return true
    val complexKeywords = listOf("\\int", "\\iiint", "\\sum", "\\frac", "\\sqrt", "\\lim", "\\oint", "\\prod", "\\matrix", "\\begin", "\\align", "\\partial", "\\nabla")
    return complexKeywords.any { inner.contains(it) }
}

@Composable
fun MathSplitterRichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.LocalContentColor.current,
    fontSize: TextUnit = 16.sp,
    searchQuery: String = "",
    highlightColor: Color = Color.Yellow,
    isSelectable: Boolean = true,
    isBookmarked: Boolean = false,
    onBookmarkToggle: (() -> Unit)? = null,
    customMenuItems: List<CustomTextMenuItem> = emptyList(),
    onClick: (() -> Unit)? = null,
    onClickWithCoords: ((Float, Float) -> Unit)? = null
) {
    val allMatches = BLOCK_MATH_REGEX.findAll(text).toList()
    val scrollableMatches = allMatches.filter { isScrollableMathFormula(it.value) }

    if (scrollableMatches.isEmpty()) {
        RichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        return
    }

    // data class to store expanded ranges for math blocks
    data class MathChunk(val fullRange: IntRange, val rawMath: String)
    val chunks = mutableListOf<MathChunk>()
    
    val markers = listOf("**", "__", "*", "_", "~~", "`")
    val listRegex = Regex("""(?m)^[ \t]*(?:\*|\+|\-|\d+\.)[ \t]*$""")

    for (m in scrollableMatches) {
        var start = m.range.first
        var end = m.range.last + 1
        
        // 1. Look for leading markers/list items on the same line
        val prefix = text.substring(0, start)
        val lastNewlineBefore = prefix.lastIndexOf('\n')
        val lineStart = if (lastNewlineBefore == -1) 0 else lastNewlineBefore + 1
        val proseOnLine = text.substring(lineStart, start)
        
        var expandedStart = start
        var foundMarker: String? = null
        
        // Check for matching marker immediately before (ignoring whitespace)
        val proseTrimmed = proseOnLine.trimEnd()
        val trailingWs = proseOnLine.length - proseTrimmed.length
        
        for (marker in markers) {
            if (proseTrimmed.endsWith(marker)) {
                foundMarker = marker
                expandedStart = start - (marker.length + trailingWs)
                break
            }
        }
        
        // Check if the remaining text on line is just a list marker
        val remainingProse = text.substring(lineStart, expandedStart)
        if (remainingProse.isBlank() || listRegex.matches(remainingProse.trimEnd())) {
            expandedStart = lineStart
        }
        
        // 2. Look for trailing markers/punctuation on the same line
        var expandedEnd = end
        val suffix = text.substring(end)
        val firstNewlineAfter = suffix.indexOf('\n')
        val lineEnd = if (firstNewlineAfter == -1) text.length else end + firstNewlineAfter
        val proseAfterOnLine = text.substring(end, lineEnd)
        
        val proseAfterTrimmed = proseAfterOnLine.trimStart()
        val leadingWs = proseAfterOnLine.length - proseAfterTrimmed.length
        
        if (foundMarker != null && proseAfterTrimmed.startsWith(foundMarker)) {
            expandedEnd = end + leadingWs + foundMarker.length
        }
        
        // Include trailing colon or period if it's the only thing left on line
        val finalSuffix = text.substring(expandedEnd, lineEnd).trim()
        if (finalSuffix.length <= 2 && finalSuffix.all { !it.isLetterOrDigit() }) {
            expandedEnd = lineEnd
        }
        
        chunks.add(MathChunk(expandedStart until expandedEnd, m.value))
    }

    // Merge overlapping or contiguous math chunks if necessary
    val mergedChunks = mutableListOf<MathChunk>()
    if (chunks.isNotEmpty()) {
        var current = chunks[0]
        for (i in 1 until chunks.size) {
            val next = chunks[i]
            if (next.fullRange.first <= current.fullRange.last + 1) {
                current = MathChunk(current.fullRange.first until maxOf(current.fullRange.last, next.fullRange.last) + 1, current.rawMath + " " + next.rawMath)
            } else {
                mergedChunks.add(current)
                current = next
            }
        }
        mergedChunks.add(current)
    }

    Column(modifier = modifier) {
        var lastIndex = 0
        for (chunk in mergedChunks) {
            val before = text.substring(lastIndex, chunk.fullRange.first)
            if (before.isNotBlank()) {
                RichText(before, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
            }
            
            val fullMathContent = text.substring(chunk.fullRange)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                RichText(
                    text = fullMathContent, 
                    modifier = Modifier.wrapContentWidth(unbounded = true), 
                    color = color, 
                    fontSize = fontSize, 
                    searchQuery = searchQuery, 
                    highlightColor = highlightColor, 
                    isSelectable = false, 
                    isBookmarked = isBookmarked,
                    onBookmarkToggle = onBookmarkToggle,
                    customMenuItems = customMenuItems,
                    onClick = onClick,
                    onClickWithCoords = onClickWithCoords,
                    isHorizontallyScrollable = true
                )
            }
            
            lastIndex = chunk.fullRange.last + 1
        }
        val after = text.substring(lastIndex)
        if (after.isNotBlank()) {
            RichText(after, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        }
    }
}

@Composable
fun SmartRichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.LocalContentColor.current,
    fontSize: TextUnit = 16.sp,
    searchQuery: String = "",
    highlightColor: Color = Color.Yellow,
    isSelectable: Boolean = true,
    isBookmarked: Boolean = false,
    onBookmarkToggle: (() -> Unit)? = null,
    customMenuItems: List<CustomTextMenuItem> = emptyList(),
    onClick: (() -> Unit)? = null,
    onClickWithCoords: ((Float, Float) -> Unit)? = null
) {
    val matches = TABLE_REGEX.findAll(text).toList()
    
    if (matches.isEmpty()) {
        MathSplitterRichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        return
    }
    
    var lastIndex = 0
    Column(modifier = modifier) {
        for (match in matches) {
            val before = text.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) {
                MathSplitterRichText(before, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
            }
            
            // Parse table
            val tableStr = match.value.trim()
            val rows = tableStr.split(Regex("\\r?\\n")).map { it.trim() }
            val tableData = mutableListOf<List<String>>()
            var skipNext = false
            for (i in rows.indices) {
                if (skipNext) { skipNext = false; continue }
                val row = rows[i]
                if (row.contains(Regex("^[\\s|\\-:]+$"))) {
                    continue
                }
                if (i == 1 && rows[i].contains(Regex("^[\\s|\\-:]+$"))) {
                    continue
                }
                val cols = row.split("|").drop(1).dropLast(1).map { it.trim() }
                if (cols.isNotEmpty()) {
                    tableData.add(cols)
                }
            }
            
            if (tableData.isNotEmpty()) {
                TableRenderer(tableData, color, searchQuery)
            }
            lastIndex = match.range.last + 1
        }
        val after = text.substring(lastIndex)
        if (after.isNotBlank()) {
            MathSplitterRichText(after, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        }
    }
}
