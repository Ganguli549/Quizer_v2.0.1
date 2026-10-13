package com.example.ui.components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.SoftLineBreak

class TextDrawable(private val text: String, private val textSize: Float, private val textColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@TextDrawable.textColor
        this.textSize = this@TextDrawable.textSize
    }

    private fun getSanitizedLines(): List<String> {
        return text.replace("<br>", "\n").replace("<br/>", "\n").replace("\\n", "\n").split("\n")
    }

    override fun draw(canvas: Canvas) {
        val lines = getSanitizedLines()
        val lineHeight = paint.descent() - paint.ascent()
        var y = -paint.ascent()
        for (line in lines) {
            canvas.drawText(line, 0f, y, paint)
            y += lineHeight
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int {
        val lines = getSanitizedLines()
        val maxW = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        return maxW.toInt().coerceAtLeast(1)
    }

    override fun getIntrinsicHeight(): Int {
        val lines = getSanitizedLines()
        val lineHeight = (paint.descent() - paint.ascent()).toInt()
        return (lineHeight * lines.size.coerceAtLeast(1))
    }
}

fun sanitizeLatex(input: String): String {
    if (input.isBlank()) return input

    var s = input.trim()

    // 1. Normalize unicode accents and quotes that are not supported by TeX fonts
    s = s.replace("ö", "o")
        .replace("Ö", "O")
        .replace("ü", "u")
        .replace("Ü", "U")
        .replace("ä", "a")
        .replace("Ä", "A")
        .replace("é", "e")
        .replace("É", "E")
        .replace("è", "e")
        .replace("È", "E")
        .replace("à", "a")
        .replace("À", "A")
        .replace("ñ", "n")
        .replace("Ñ", "N")
        .replace("’", "'")
        .replace("‘", "'")
        .replace("“", "\"")
        .replace("”", "\"")

    // 2. Map unsupported macros to standard JLatexMath equivalents
    s = s.replace("\\varnothing", "\\emptyset")
        .replace("\\dots", "\\ldots")
        .replace("\\impliedby", "\\Longleftarrow")
        .replace("\\implies", "\\Longrightarrow")
        .replace("\\iff", "\\Longleftrightarrow")
        .replace("\\plusminus", "\\pm")

    // 3. Balance braces { and }, dropping extra unmatched closing braces
    val sb = StringBuilder()
    var braceDepth = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            val next = s[i + 1]
            if (next == '{' || next == '}') {
                sb.append(c).append(next)
                i += 2
                continue
            }
        }
        if (c == '{') {
            braceDepth++
            sb.append(c)
        } else if (c == '}') {
            if (braceDepth > 0) {
                braceDepth--
                sb.append(c)
            } else {
                // Drop unmatched extra closing brace
            }
        } else {
            sb.append(c)
        }
        i++
    }
    repeat(braceDepth) {
        sb.append('}')
    }
    s = sb.toString()

    // 4. Balance \left and \right
    val leftMatches = Regex("""\\left(?![a-zA-Z])""").findAll(s).toList()
    val rightMatches = Regex("""\\right(?![a-zA-Z])""").findAll(s).toList()
    if (rightMatches.size > leftMatches.size) {
        var excess = rightMatches.size - leftMatches.size
        val rDelimMatches = Regex("""\\right\s*([.\(\)\[\]\{\}|\\/]|\\vert|\\Vert|\\lceil|\\rceil|\\lfloor|\\rfloor)""").findAll(s).toList()
        for (m in rDelimMatches.reversed()) {
            if (excess <= 0) break
            val delim = m.groupValues[1]
            val range = m.range
            s = s.substring(0, range.first) + (if (delim == ".") "" else delim) + s.substring(range.last + 1)
            excess--
        }
    } else if (leftMatches.size > rightMatches.size) {
        repeat(leftMatches.size - rightMatches.size) {
            s += " \\right."
        }
    }

    return s
}

fun trySalvageLatex(rawLatex: String?, fontSizePx: Float, textColorArgb: Int): Drawable? {
    if (rawLatex.isNullOrBlank()) return null
    try {
        val s = sanitizeLatex(rawLatex)
        return ru.noties.jlatexmath.JLatexMathDrawable.builder(s)
            .textSize(fontSizePx)
            .color(textColorArgb)
            .fitCanvas(false)
            .build().apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
    } catch (e1: Exception) {
        try {
            // Aggressive salvage: strip \left and \right delimiters
            var s = rawLatex
                .replace(Regex("""\\left\s*([.\(\)\[\]\{\}|\\/]|\\vert|\\Vert|\\lceil|\\rceil|\\lfloor|\\rfloor)""")) {
                    val d = it.groupValues[1]
                    if (d == ".") "" else d
                }
                .replace(Regex("""\\right\s*([.\(\)\[\]\{\}|\\/]|\\vert|\\Vert|\\lceil|\\rceil|\\lfloor|\\rfloor)""")) {
                    val d = it.groupValues[1]
                    if (d == ".") "" else d
                }
            s = sanitizeLatex(s)
            return ru.noties.jlatexmath.JLatexMathDrawable.builder(s)
                .textSize(fontSizePx)
                .color(textColorArgb)
                .fitCanvas(false)
                .build().apply {
                    setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                }
        } catch (e2: Exception) {
            try {
                // Flatten array / environments to simple lines
                var s = rawLatex
                    .replace(Regex("""\\begin\{[a-zA-Z*]+\}(\{[^\}]*\})*"""), "")
                    .replace(Regex("""\\end\{[a-zA-Z*]+\}"""), "")
                    .replace("&", " ")
                s = sanitizeLatex(s)
                return ru.noties.jlatexmath.JLatexMathDrawable.builder(s)
                    .textSize(fontSizePx)
                    .color(textColorArgb)
                    .fitCanvas(false)
                    .build().apply {
                        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                    }
            } catch (e3: Exception) {
                return null
            }
        }
    }
}

object MarkwonCache {
    private val cache = mutableMapOf<Pair<Float, Color>, Markwon>()

    fun get(context: android.content.Context, fontSizePx: Float, color: Color): Markwon {
        val key = Pair(fontSizePx, color)
        return cache.getOrPut(key) {
            val textColorArgb = if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK
            Markwon.builder(context)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(CoilImagesPlugin.create(context))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                        builder.on(SoftLineBreak::class.java) { visitor, _ ->
                            visitor.builder().append('\n')
                        }
                    }
                })
                .usePlugin(
                    JLatexMathPlugin.create(
                        fontSizePx,
                        object : JLatexMathPlugin.BuilderConfigure {
                            override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                                builder.inlinesEnabled(true)
                                builder.blocksLegacy(true)
                                builder.blocksEnabled(true)
                                builder.theme().blockFitCanvas(false)
                                builder.theme().textColor(textColorArgb)
                                builder.theme().inlineTextColor(textColorArgb)
                                builder.theme().blockTextColor(textColorArgb)
                                builder.errorHandler { latex, error ->
                                    val salvaged = trySalvageLatex(latex, fontSizePx, textColorArgb)
                                    if (salvaged != null) {
                                        salvaged
                                    } else {
                                        android.util.Log.w("JLatexMath", "Could not parse LaTeX: ${latex?.take(80)}... (${error.message})")
                                        TextDrawable(latex ?: "MathError", fontSizePx, textColorArgb).apply {
                                            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                                        }
                                    }
                                }
                            }
                        }
                    )
                )
                .build()
        }
    }
}

data class CustomTextMenuItem(
    val id: Int,
    val title: String,
    val iconRes: Int? = null,
    val showText: Boolean = true,
    val order: Int = 0,
    val onClick: (selectedText: String) -> Unit
)

class CustomSelectionActionModeCallback(
    var customItems: List<CustomTextMenuItem> = emptyList()
) : android.view.ActionMode.Callback {
    var textView: TextView? = null
    private var isDestroying = false

    companion object {
        const val ID_COPY = 10001
        const val ID_SELECT_ALL = 10002
        const val ID_SHARE = 10003
    }

    override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
        isDestroying = false
        if (mode == null || menu == null) return false
        populateCustomMenu(mode, menu)
        return true
    }

    override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
        if (mode == null || menu == null) return false
        populateCustomMenu(mode, menu)
        return true
    }

    private fun createItemTitle(context: android.content.Context?, customItem: CustomTextMenuItem): CharSequence {
        if (customItem.showText) {
            return customItem.title
        }
        val iconRes = customItem.iconRes
        if (context != null && iconRes != null) {
            try {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)?.mutate()
                if (drawable != null) {
                    val density = context.resources.displayMetrics.density
                    val sizePx = (22 * density).toInt().coerceAtLeast(24)
                    drawable.setBounds(0, 0, sizePx, sizePx)
                    val targetString = " "
                    val spannable = android.text.SpannableString(targetString)
                    val imageSpan = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.text.style.ImageSpan(drawable, android.text.style.ImageSpan.ALIGN_CENTER)
                    } else {
                        android.text.style.ImageSpan(drawable, android.text.style.ImageSpan.ALIGN_BOTTOM)
                    }
                    spannable.setSpan(imageSpan, 0, targetString.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    return spannable
                }
            } catch (e: Exception) {
                // Ignore and fall back
            }
        }
        return "🔖"
    }

    private fun populateCustomMenu(mode: android.view.ActionMode, menu: android.view.Menu) {
        menu.clear()

        for (customItem in customItems) {
            val titleFormatted = createItemTitle(textView?.context, customItem)
            val item = menu.add(android.view.Menu.NONE, customItem.id, customItem.order, titleFormatted)
            customItem.iconRes?.let { iconRes ->
                try {
                    item.setIcon(iconRes)
                } catch (e: Exception) {}
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                item.contentDescription = customItem.title
                item.tooltipText = customItem.title
            }
            item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        }

        val copyItem = menu.add(android.view.Menu.NONE, ID_COPY, 10, "Copy")
        try {
            copyItem.setIcon(com.example.R.drawable.ic_copy)
        } catch (e: Exception) {}
        copyItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)

        val selectAllItem = menu.add(android.view.Menu.NONE, ID_SELECT_ALL, 20, "Select all")
        try {
            selectAllItem.setIcon(com.example.R.drawable.ic_select_all)
        } catch (e: Exception) {}
        selectAllItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)

        val shareItem = menu.add(android.view.Menu.NONE, ID_SHARE, 30, "Share")
        try {
            shareItem.setIcon(com.example.R.drawable.ic_share)
        } catch (e: Exception) {}
        shareItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
    }

    override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
        if (item == null || mode == null) return false
        val tv = textView ?: return false

        val selStart = tv.selectionStart
        val selEnd = tv.selectionEnd
        val selectedText = if (selStart in 0..tv.text.length && selEnd in 0..tv.text.length && selStart != selEnd) {
            val start = minOf(selStart, selEnd)
            val end = maxOf(selStart, selEnd)
            tv.text.subSequence(start, end).toString()
        } else ""

        val customItem = customItems.find { it.id == item.itemId }
        if (customItem != null) {
            safeFinishActionMode(mode)
            tv.post {
                val spannable = tv.text as? android.text.Spannable
                if (spannable != null) {
                    android.text.Selection.removeSelection(spannable)
                }
                customItem.onClick(selectedText)
            }
            return true
        }

        when (item.itemId) {
            ID_COPY -> {
                if (selectedText.isNotEmpty()) {
                    val clipboard = tv.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Selected Text", selectedText))
                    android.widget.Toast.makeText(tv.context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
                safeFinishActionMode(mode)
                tv.post {
                    val spannable = tv.text as? android.text.Spannable
                    if (spannable != null) {
                        android.text.Selection.removeSelection(spannable)
                    }
                }
                return true
            }
            ID_SELECT_ALL -> {
                val spannable = tv.text as? android.text.Spannable
                if (spannable != null) {
                    android.text.Selection.setSelection(spannable, 0, spannable.length)
                }
                return true
            }
            ID_SHARE -> {
                if (selectedText.isNotEmpty()) {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, selectedText)
                    }
                    tv.context.startActivity(android.content.Intent.createChooser(shareIntent, "Share text"))
                }
                safeFinishActionMode(mode)
                tv.post {
                    val spannable = tv.text as? android.text.Spannable
                    if (spannable != null) {
                        android.text.Selection.removeSelection(spannable)
                    }
                }
                return true
            }
        }
        return false
    }

    private fun safeFinishActionMode(mode: android.view.ActionMode) {
        if (!isDestroying) {
            isDestroying = true
            try {
                mode.finish()
            } catch (e: Exception) {
                isDestroying = false
            }
        }
    }

    override fun onDestroyActionMode(mode: android.view.ActionMode?) {
        isDestroying = true
    }
}

fun formatRichTextLineBreaks(raw: String): String {
    if (raw.isBlank()) return raw

    // 1. First standardize math wrappers
    val withStandardMath = raw
        .replace(Regex("(?s)```[a-zA-Z]*\\s*\\$\\$([^`]*?)\\$\\$\\s*```")) { "$$${sanitizeLatex(it.groupValues[1])}$$" }
        .replace(Regex("(?s)```[a-zA-Z]*\\s*\\$([^`]*?)\\$\\s*```")) { "$$${sanitizeLatex(it.groupValues[1])}$$" }
        .replace(Regex("(?s)`\\$\\$([^`]*?)\\$\\$`")) { "$$${sanitizeLatex(it.groupValues[1])}$$" }
        .replace(Regex("(?s)`\\$([^`]*?)\\$`")) { "$$${sanitizeLatex(it.groupValues[1])}$$" }
        .replace("\\(", "$$")
        .replace("\\)", "$$")
        .replace("\\[", "$$")
        .replace("\\]", "$$")
        .replace(Regex("(?s)\\$\\$([^$]+?)\\$\\$")) { "$$${sanitizeLatex(it.groupValues[1])}$$" }
        .replace(Regex("(?<!\\$)\\$([^$]+?)\\$(?!\\$)")) { "$$${sanitizeLatex(it.groupValues[1])}$$" }

    // 2. Identify all LaTeX math & code blocks to protect them from line-break transformation
    val protectedPattern = Regex("(?s)(?:```[a-zA-Z]*[\\s\\S]*?```|`[^`]+`|\\$\\$[^$]+?\\$\\$|(?<!\\$)\\$[^$\\n]+?\\$(?!\\$))")
    val matches = protectedPattern.findAll(withStandardMath).toList()

    if (matches.isEmpty()) {
        return transformNonMathLineBreaks(withStandardMath)
    }

    val sb = StringBuilder()
    var lastIdx = 0
    for (m in matches) {
        val nonMathPart = withStandardMath.substring(lastIdx, m.range.first)
        if (nonMathPart.isNotEmpty()) {
            sb.append(transformNonMathLineBreaks(nonMathPart))
        }
        sb.append(m.value)
        lastIdx = m.range.last + 1
    }
    if (lastIdx < withStandardMath.length) {
        sb.append(transformNonMathLineBreaks(withStandardMath.substring(lastIdx)))
    }
    return sb.toString()
}

private fun transformNonMathLineBreaks(text: String): String {
    if (text.isEmpty()) return text

    // 1. Avoid doubling if <br> was already directly followed by \n or \r\n or \\n
    var s = text
        .replace(Regex("(?i)<br\\s*/?>\\s*\\\\r\\\\n"), "<br>")
        .replace(Regex("(?i)<br\\s*/?>\\s*\\\\n"), "<br>")
        .replace(Regex("(?i)<br\\s*/?>\\s*\\r?\\n"), "<br>")

    // 2. Convert literal backslash-n strings: "\\r\\n", "\\n" into <br>
    s = s.replace("\\r\\n", "<br>").replace("\\n", "<br>")

    // 3. For actual newlines: if text has table rows (| ... |), keep newline for table rows
    val lines = s.split(Regex("\\r?\\n"))
    val result = StringBuilder()
    for (i in lines.indices) {
        val line = lines[i]
        result.append(line)
        if (i < lines.size - 1) {
            val isTableRow = line.trim().startsWith("|") && line.trim().endsWith("|")
            val nextIsTableRow = lines.getOrNull(i + 1)?.trim()?.startsWith("|") == true
            if (isTableRow && nextIsTableRow) {
                result.append("\n")
            } else if (line.trimEnd().endsWith("<br>", ignoreCase = true) || line.trimEnd().endsWith("<br/>", ignoreCase = true)) {
                // Already ended with <br>, don't double
            } else {
                result.append("<br>")
            }
        }
    }
    return result.toString()
}

@Composable
fun RichText(
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
    onClickWithCoords: ((Float, Float) -> Unit)? = null,
    isHorizontallyScrollable: Boolean = false
) {
    val context = LocalContext.current
    val fontSizePx = fontSize.value * context.resources.displayMetrics.density
    val markwon = remember(fontSizePx) {
        MarkwonCache.get(context, fontSizePx, color)
    }

    val processedText = remember(text) {
        formatRichTextLineBreaks(text)
    }

    var zoomableImageUrl by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val spannableToDisplay = remember(processedText, markwon, searchQuery, highlightColor) {
        val markdownNode = markwon.toMarkdown(processedText)
        val spannable = if (markdownNode is Spannable) markdownNode else SpannableString(markdownNode)
        
        if (searchQuery.isNotBlank()) {
            val lowerText = spannable.toString().lowercase()
            val lowerQuery = searchQuery.lowercase()
            var startIndex = 0
            while (true) {
                val index = lowerText.indexOf(lowerQuery, startIndex)
                if (index == -1) break
                spannable.setSpan(
                    BackgroundColorSpan(highlightColor.toArgb()),
                    index,
                    index + lowerQuery.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = index + lowerQuery.length
            }
        }
        
        try {
            val imageSpans = spannable.getSpans(0, spannable.length, io.noties.markwon.image.AsyncDrawableSpan::class.java)
            for (span in imageSpans) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val url = span.drawable.destination
                if (url.startsWith("http") || url.startsWith("data:") || url.startsWith("file:")) {
                    spannable.setSpan(object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            zoomableImageUrl = url
                        }
                    }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        spannable
    }

    val resolvedCustomItems = remember(customMenuItems, onBookmarkToggle, isBookmarked) {
        if (customMenuItems.isNotEmpty()) {
            customMenuItems
        } else if (onBookmarkToggle != null) {
            listOf(
                CustomTextMenuItem(
                    id = 99991,
                    title = if (isBookmarked) "Unbookmark" else "Bookmark",
                    iconRes = if (isBookmarked) com.example.R.drawable.ic_bookmark else com.example.R.drawable.ic_bookmark_border,
                    showText = false,
                    order = 0,
                    onClick = { onBookmarkToggle.invoke() }
                )
            )
        } else {
            emptyList()
        }
    }

    val customSelectionCallback = remember {
        CustomSelectionActionModeCallback()
    }
    customSelectionCallback.customItems = resolvedCustomItems

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK)
                textSize = fontSize.value
                setTextIsSelectable(isSelectable)
                setHorizontallyScrolling(isHorizontallyScrollable)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
                }
                customSelectionCallback.textView = this
                customSelectionActionModeCallback = customSelectionCallback
                markwon.setParsedMarkdown(this, spannableToDisplay)
                tag = spannableToDisplay
                if (onClick != null || onClickWithCoords != null) {
                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                            onClickWithCoords?.invoke(e.rawX, e.rawY)
                            onClick?.invoke()
                            return false
                        }
                    })
                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        false
                    }
                }
            }
        },
        update = { textView ->
            textView.setTextColor(if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK)
            textView.textSize = fontSize.value
            textView.setTextIsSelectable(isSelectable)
            textView.setHorizontallyScrolling(isHorizontallyScrollable)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                textView.setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
            }
            customSelectionCallback.textView = textView
            customSelectionCallback.customItems = resolvedCustomItems
            textView.customSelectionActionModeCallback = customSelectionCallback
            if (textView.tag != spannableToDisplay) {
                markwon.setParsedMarkdown(textView, spannableToDisplay)
                textView.tag = spannableToDisplay
            }
            if (onClick != null || onClickWithCoords != null) {
                val gestureDetector = android.view.GestureDetector(textView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                        onClickWithCoords?.invoke(e.rawX, e.rawY)
                        onClick?.invoke()
                        return false
                    }
                })
                textView.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
            } else {
                textView.setOnTouchListener(null)
            }
        },
        onReset = { textView ->
            try {
                val spannable = textView.text as? android.text.Spannable
                if (spannable != null) {
                    android.text.Selection.removeSelection(spannable)
                }
                textView.clearFocus()
            } catch (e: Exception) {}
        }
    )
    
    if (zoomableImageUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { zoomableImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                var scale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
                var offset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val state = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, offsetChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    offset += offsetChange
                }
                
                coil.compose.AsyncImage(
                    model = zoomableImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = state),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                
                androidx.compose.material3.IconButton(
                    onClick = { zoomableImageUrl = null },
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(16.dp)
                        .padding(top = 32.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
