package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val mathRegex = Regex(
        "(?s)(?:" +
        "```(?:math|latex)\\s*(.*?)\\s*```|" +
        "\\$\\$(.+?)\\$\\$|" +
        "\\\\\\[(.+?)\\\\\\]|" +
        "\\\\begin\\{(?:equation|align|aligned|gather|matrix|pmatrix|bmatrix)\\*?\\}(.*?)\\\\end\\{(?:equation|align|aligned|gather|matrix|pmatrix|bmatrix)\\*?\\}|" +
        "(?:^|\\r?\\n)[ \\t]*\\$([^\\n$]+?)\\$[ \\t]*(?=\\r?\\n|$)|" +
        "(?<!\\$)\\$([^\\n$]*?(?:\\\\frac|\\\\sqrt|\\\\sum|\\\\int|\\\\left|\\\\right|\\\\over|\\\\times|\\\\div)[^\\n$]*?)\\$(?!\\$)" +
        ")"
    )
    val matches = mathRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        RichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
        return
    }

    var lastIndex = 0
    Column(modifier = modifier) {
        for (match in matches) {
            val before = text.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) {
                RichText(before, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, isBookmarked, onBookmarkToggle, customMenuItems, onClick, onClickWithCoords)
            }
            
            val rawFormula = match.value.trim()
            val cleanMathStr = when {
                rawFormula.startsWith("```math") -> rawFormula.removePrefix("```math").removeSuffix("```").trim().let { "$$${sanitizeLatex(it)}$$" }
                rawFormula.startsWith("```latex") -> rawFormula.removePrefix("```latex").removeSuffix("```").trim().let { "$$${sanitizeLatex(it)}$$" }
                rawFormula.startsWith("\\[") && rawFormula.endsWith("\\]") -> rawFormula.removePrefix("\\[").removeSuffix("\\]").trim().let { "$$${sanitizeLatex(it)}$$" }
                rawFormula.startsWith("\\begin") -> "$$${sanitizeLatex(rawFormula)}$$"
                rawFormula.startsWith("$$") && rawFormula.endsWith("$$") -> "$$${sanitizeLatex(rawFormula.removePrefix("$$").removeSuffix("$$"))}$$"
                rawFormula.startsWith("$") && rawFormula.endsWith("$") -> "$$" + sanitizeLatex(rawFormula.removePrefix("$").removeSuffix("$").trim()) + "$$"
                else -> if (rawFormula.startsWith("$$")) "$$${sanitizeLatex(rawFormula.removePrefix("$$").removeSuffix("$$"))}$$" else "$$${sanitizeLatex(rawFormula)}$$"
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                RichText(
                    text = cleanMathStr, 
                    modifier = Modifier.wrapContentSize(), 
                    color = color, 
                    fontSize = fontSize, 
                    searchQuery = searchQuery, 
                    highlightColor = highlightColor, 
                    isSelectable = isSelectable, 
                    isBookmarked = isBookmarked,
                    onBookmarkToggle = onBookmarkToggle,
                    customMenuItems = customMenuItems,
                    onClick = onClick,
                    onClickWithCoords = onClickWithCoords,
                    isHorizontallyScrollable = true
                )
            }
            
            lastIndex = match.range.last + 1
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
    val tableRegex = Regex("(?m)^(?:[ \\t]*\\|.*\\|[ \\t]*(?:\\r?\\n|$))+")
    val matches = tableRegex.findAll(text).toList()
    
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
