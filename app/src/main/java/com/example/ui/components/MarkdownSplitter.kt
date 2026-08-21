package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
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
    onClick: (() -> Unit)? = null
) {
    val mathRegex = Regex("(?s)(?:^|\\n)[ \\t]*(?:\\$\\$|\\\\\\[|\\$)(.*?)(?:\\$\\$|\\\\\\]|\\$)[ \\t]*(?=\\n|$)")
    val matches = mathRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        RichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, onClick)
        return
    }

    var lastIndex = 0
    Column(modifier = modifier) {
        for (match in matches) {
            val before = text.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) {
                RichText(before, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, onClick)
            }
            
            val mathStr = match.value.trim()
            val scrollState = rememberScrollState()
            RichText(
                text = mathStr, 
                modifier = Modifier.horizontalScroll(scrollState), 
                color = color, 
                fontSize = fontSize, 
                searchQuery = searchQuery, 
                highlightColor = highlightColor, 
                isSelectable = isSelectable, 
                onClick = onClick
            )
            
            lastIndex = match.range.last + 1
        }
        val after = text.substring(lastIndex)
        if (after.isNotBlank()) {
            RichText(after, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, onClick)
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
    onClick: (() -> Unit)? = null
) {
    val tableRegex = Regex("(?m)^(?:[ \\t]*\\|.*\\|[ \\t]*(?:\\r?\\n|$))+")
    val matches = tableRegex.findAll(text).toList()
    
    if (matches.isEmpty()) {
        MathSplitterRichText(text, modifier, color, fontSize, searchQuery, highlightColor, isSelectable, onClick)
        return
    }
    
    var lastIndex = 0
    Column(modifier = modifier) {
        for (match in matches) {
            val before = text.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) {
                MathSplitterRichText(before, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, onClick)
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
            MathSplitterRichText(after, Modifier, color, fontSize, searchQuery, highlightColor, isSelectable, onClick)
        }
    }
}
