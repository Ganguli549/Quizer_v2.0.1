package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

@Composable
fun ExpandableQuestionHeader(
    headerText: String,
    rangeStr: String = "",
    textColor: Color,
    modifier: Modifier = Modifier,
    isQuizMode: Boolean = false,
    initiallyExpanded: Boolean = false,
    searchQuery: String = "",
    highlightColor: Color = Color.Yellow,
    onClick: (() -> Unit)? = null
) {
    val pTagRegex = Regex("<p(?:\\s+name=\"([^\"]+)\")?\\s*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    val match = pTagRegex.find(headerText)

    if (match != null) {
        val beforeP = headerText.substring(0, match.range.first).trim()
        val insideP = match.value.trim()
        val afterP = headerText.substring(match.range.last + 1).trim()
        val headerTitle = match.groupValues[1].takeIf { it.isNotBlank() } ?: "Passage"
        
        var isExpanded by remember(headerText, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
        
        Column(modifier = modifier.fillMaxWidth()) {
            val rangeDisplay = if (rangeStr.isNotBlank()) " <b>$rangeStr</b>" else ""
            if (beforeP.isNotBlank()) {
                RichText(
                    text = "$beforeP$rangeDisplay",
                    fontSize = if (isQuizMode) 20.sp else 22.sp,
                    color = textColor,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    onClick = onClick
                )
            } else if (rangeStr.isNotBlank()) {
                RichText(
                    text = "<b>$rangeStr</b>",
                    fontSize = if (isQuizMode) 20.sp else 22.sp,
                    color = textColor,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    onClick = onClick
                )
            }
            
            if (insideP.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (!isQuizMode) {
                    RichText(
                        text = insideP,
                        fontSize = 20.sp,
                        color = textColor,
                        searchQuery = searchQuery,
                        highlightColor = highlightColor,
                        onClick = onClick
                    )
                } else {
                    // Expandable with modern aesthetic
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isExpanded = !isExpanded }
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = "Passage Icon",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = headerTitle,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Toggle passage",
                                        modifier = Modifier.padding(4.dp).size(18.dp)
                                    )
                                }
                            }
                            
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))
                                RichText(
                                    text = insideP,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    searchQuery = searchQuery,
                                    highlightColor = highlightColor,
                                    onClick = onClick
                                )
                            }
                        }
                    }
                }
            }
            
            if (afterP.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                RichText(
                    text = if (beforeP.isNotBlank()) afterP else "$afterP$rangeDisplay",
                    fontSize = if (isQuizMode) 20.sp else 22.sp,
                    color = textColor,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor,
                    onClick = onClick
                )
            }
        }
    } else {
        val rangeDisplay = if (rangeStr.isNotBlank()) " <b>$rangeStr</b>" else ""
        RichText(
            text = "$headerText$rangeDisplay",
            fontSize = if (isQuizMode) 20.sp else 22.sp,
            color = textColor,
            modifier = modifier,
            searchQuery = searchQuery,
            highlightColor = highlightColor,
            onClick = onClick
        )
    }
}
