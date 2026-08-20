package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.geometry.Rect

class CustomTextToolbar(
    val onShow: (Rect, (() -> Unit)?, (() -> Unit)?, (() -> Unit)?, (() -> Unit)?) -> Unit,
    val onHide: () -> Unit
) : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() { onHide() }
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        onShow(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    
    var isToolbarVisible by remember { mutableStateOf(false) }
    var onCopy by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onPaste by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onCut by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onSelectAll by remember { mutableStateOf<(() -> Unit)?>(null) }

    val customTextToolbar = remember {
        CustomTextToolbar(
            onShow = { rect, copy, paste, cut, selectAll ->
                onCopy = copy
                onPaste = paste
                onCut = cut
                onSelectAll = selectAll
                isToolbarVisible = true
            },
            onHide = {
                isToolbarVisible = false
            }
        )
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    fun wrapSelection(tfv: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
        val selStart = tfv.selection.min
        val selEnd = tfv.selection.max
        if (selStart == selEnd) return tfv
        val before = tfv.text.substring(0, selStart)
        val selected = tfv.text.substring(selStart, selEnd)
        val after = tfv.text.substring(selEnd)
        
        val newText = before + prefix + selected + suffix + after
        val newCursor = selStart + prefix.length + selected.length + suffix.length
        return TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    CompositionLocalProvider(
        LocalTextToolbar provides customTextToolbar
    ) {
        Column(modifier = modifier) {
            AnimatedVisibility(visible = isToolbarVisible || !textFieldValue.selection.collapsed) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onCut != null) {
                            IconButton(onClick = { onCut?.invoke() }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.ContentCut, "Cut") }
                        }
                        if (onCopy != null) {
                            IconButton(onClick = { onCopy?.invoke() }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.ContentCopy, "Copy") }
                        }
                        if (onPaste != null) {
                            IconButton(onClick = { onPaste?.invoke() }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.ContentPaste, "Paste") }
                        }
                        if (onSelectAll != null) {
                            IconButton(onClick = { onSelectAll?.invoke() }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.SelectAll, "Select All") }
                        }
                        
                        Box(modifier = Modifier.width(1.dp).height(24.dp).padding(horizontal = 4.dp))
                        
                        IconButton(onClick = { 
                            textFieldValue = wrapSelection(textFieldValue, "<b>", "</b>")
                            onValueChange(textFieldValue.text)
                        }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.FormatBold, "Bold") }
                        IconButton(onClick = { 
                            textFieldValue = wrapSelection(textFieldValue, "<i>", "</i>")
                            onValueChange(textFieldValue.text)
                        }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.FormatItalic, "Italic") }
                        IconButton(onClick = { 
                            textFieldValue = wrapSelection(textFieldValue, "<u>", "</u>")
                            onValueChange(textFieldValue.text)
                        }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Icon(Icons.Default.FormatUnderlined, "Underline") }
                        IconButton(onClick = { 
                            textFieldValue = wrapSelection(textFieldValue, "\\(", "\\)")
                            onValueChange(textFieldValue.text)
                        }, modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()) { Text("∑") }
                    }
                }
            }
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { 
                    textFieldValue = it
                    onValueChange(it.text) 
                },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
