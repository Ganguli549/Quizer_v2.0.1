package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

private fun isScrollableMathFormula(matchStr: String, isStandaloneOnLine: Boolean): Boolean {
    if (matchStr.startsWith("```") || matchStr.startsWith("\\[") || matchStr.startsWith("\\begin") || matchStr.startsWith("$$")) {
        return true
    }
    val inner = matchStr.trim('$').trim()
    // If an inline $...$ formula is embedded inside a sentence with prose on the same line,
    // keep it inline inside the sentence unless it is extremely long (>= 120 chars).
    if (!isStandaloneOnLine) {
        return inner.length >= 120
    }
    if (inner.length >= 45) return true
    val blockComplexKeywords = listOf("\\begin", "\\matrix", "\\pmatrix", "\\bmatrix", "\\align", "\\iiint", "\\oint")
    return blockComplexKeywords.any { inner.contains(it) }
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
    if (allMatches.isEmpty()) {
        RichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        return
    }

    val markers = listOf("**", "__", "*", "_", "~~", "`")
    val listRegex = Regex("""(?m)^[ \t]*(?:\*|\+|\-|\d+\.)[ \t]*$""")

    data class MathChunk(val fullRange: IntRange, val rawMath: String)
    val chunks = mutableListOf<MathChunk>()

    for (m in allMatches) {
        val start = m.range.first
        val end = m.range.last + 1

        // 1. Inspect text on the same line before the formula
        val prefix = text.substring(0, start)
        val lastNewlineBefore = prefix.lastIndexOf('\n')
        val lineStart = if (lastNewlineBefore == -1) 0 else lastNewlineBefore + 1
        val proseOnLine = text.substring(lineStart, start)

        var expandedStart = start
        var foundMarker: String? = null

        val proseTrimmed = proseOnLine.trimEnd()
        val trailingWs = proseOnLine.length - proseTrimmed.length

        for (marker in markers) {
            if (proseTrimmed.endsWith(marker)) {
                foundMarker = marker
                expandedStart = start - (marker.length + trailingWs)
                break
            }
        }

        val remainingProseBefore = text.substring(lineStart, expandedStart)
        val hasNoLeadingProse = remainingProseBefore.isBlank() || listRegex.matches(remainingProseBefore.trimEnd())
        if (hasNoLeadingProse) {
            expandedStart = lineStart
        }

        // 2. Inspect text on the same line after the formula
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

        val finalSuffix = text.substring(expandedEnd, lineEnd).trim()
        val hasNoTrailingProse = finalSuffix.length <= 2 && finalSuffix.all { !it.isLetterOrDigit() }
        if (hasNoTrailingProse) {
            expandedEnd = lineEnd
        }

        val isStandaloneOnLine = hasNoLeadingProse && hasNoTrailingProse
        if (isScrollableMathFormula(m.value, isStandaloneOnLine)) {
            chunks.add(MathChunk(expandedStart until expandedEnd, m.value))
        }
    }

    if (chunks.isEmpty()) {
        RichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        return
    }

    // Merge overlapping or contiguous math chunks if necessary
    val mergedChunks = mutableListOf<MathChunk>()
    var current = chunks[0]
    for (i in 1 until chunks.size) {
        val next = chunks[i]
        if (next.fullRange.first <= current.fullRange.last + 1) {
            current = MathChunk(
                current.fullRange.first until maxOf(current.fullRange.last, next.fullRange.last) + 1,
                current.rawMath + " " + next.rawMath
            )
        } else {
            mergedChunks.add(current)
            current = next
        }
    }
    mergedChunks.add(current)

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
                TableRenderer(
                    tableData = tableData,
                    textColor = color,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    isSelectable = isSelectable,
                    customMenuItems = customMenuItems,
                    onClick = onClick,
                    onClickWithCoords = onClickWithCoords
                )
            }
            lastIndex = match.range.last + 1
        }
        val after = text.substring(lastIndex)
        if (after.isNotBlank()) {
            MathSplitterRichText(after, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        }
    }
}
