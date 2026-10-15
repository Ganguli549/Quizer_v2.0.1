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
import io.noties.markwon.core.MarkwonTheme
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

fun restoreCorruptedLatexEscapes(text: String): String {
    if (text.isEmpty()) return text
    var s = text
    // 1. Form feed \u000c (ASCII 12) from unescaped \f in JSON
    s = s.replace("\u000crac", "\\frac")
        .replace("\u000corall", "\\forall")
        .replace("\u000clat", "\\flat")
        .replace("\u000crown", "\\frown")
    
    // 2. Backspace \u0008 (ASCII 8) from unescaped \b in JSON
    s = s.replace("\u0008eta", "\\beta")
        .replace("\u0008ar", "\\bar")
        .replace("\u0008inom", "\\binom")
        .replace("\u0008ullet", "\\bullet")
        .replace("\u0008egin", "\\begin")
        .replace("\u0008f", "\\bf")
        .replace("\u0008oldsymbol", "\\boldsymbol")
        .replace("\u0008matrix", "\\bmatrix")
        .replace("\u0008mod", "\\bmod")
        .replace("\u0008ot", "\\bot")
        .replace("\u0008race", "\\brace")
        .replace("\u0008rack", "\\brack")
        .replace("\u0008cap", "\\bigcap")
        .replace("\u0008cup", "\\bigcup")
        
    // 3. Tab \t (\u0009) from unescaped \t in JSON
    s = s.replace("\u0009imes", "\\times")
        .replace("\u0009heta", "\\theta")
        .replace("\u0009ext", "\\text")
        .replace("\u0009an", "\\tan")
        .replace("\u0009au", "\\tau")
        .replace("\u0009o", "\\to")
        .replace("\u0009ilde", "\\tilde")
        .replace("\u0009herefore", "\\therefore")
        .replace("\u0009riangle", "\\triangle")
        .replace("\u0009frac", "\\tfrac")
        .replace("\u0009op", "\\top")
        .replace("\u0009extbf", "\\textbf")
        .replace("\u0009extit", "\\textit")
        
    // 4. Carriage return \r (\u000d) before LaTeX command keywords
    s = s.replace(Regex("""\r(?=ight|ho|rightarrow|Rightarrow|rangle|rfloor|rceil|Re|root)"""), "\\\\")
    
    // 5. Newline \n (\u000a) before LaTeX command keywords
    s = s.replace(Regex("""\n(?=eq|abla|nearrow|neg|nu(?![a-zA-Z])|ne(?![a-zA-Z])|not(?![a-zA-Z]))"""), "\\\\")

    return s
}

fun cleanExtractedMathText(text: String): String {
    if (text.isBlank()) return text
    // Normalize corrupted escapes first
    val s = restoreCorruptedLatexEscapes(text)
    // Run span-aware preprocessing: sanitizes math expressions in-place,
    // converts \( ... \) to $...$, \[ ... \] to display $$, and bare LaTeX only in plain text
    return preprocessMathSpans(s)
}

fun sanitizeLatex(input: String): String {
    if (input.isBlank()) return input

    var s = restoreCorruptedLatexEscapes(input.trim())

    // Normalize over-escaped LaTeX commands (e.g. \\tan -> \tan, \\frac -> \frac),
    // but preserve valid LaTeX row-break sequences like \\ followed by whitespace/newline/closing bracket
    s = s.replace(Regex("""\\\\(?=[a-zA-Z])""")) { "\\" }

    // 1. Normalize unicode math and typography characters to standard LaTeX
    s = s.replace("×", "\\times ")
        .replace("÷", "\\div ")
        .replace("±", "\\pm ")
        .replace("≤", "\\leq ")
        .replace("≥", "\\geq ")
        .replace("≠", "\\neq ")
        .replace("≈", "\\approx ")
        .replace("∞", "\\infty ")
        .replace("√", "\\sqrt ")
        .replace("π", "\\pi ")
        .replace("θ", "\\theta ")
        .replace("α", "\\alpha ")
        .replace("β", "\\beta ")
        .replace("γ", "\\gamma ")
        .replace("δ", "\\delta ")
        .replace("λ", "\\lambda ")
        .replace("μ", "\\mu ")
        .replace("σ", "\\sigma ")
        .replace("φ", "\\phi ")
        .replace("ϕ", "\\phi ")
        .replace("ω", "\\omega ")
        .replace("Δ", "\\Delta ")
        .replace("∑", "\\sum ")
        .replace("∫", "\\int ")
        .replace("∂", "\\partial ")
        .replace("°", "^{\\circ}")
        .replace("²", "^2")
        .replace("³", "^3")
        .replace("¹", "^1")
        .replace("⁰", "^0")
        .replace("⁴", "^4")
        .replace("⁵", "^5")
        .replace("⁶", "^6")
        .replace("⁷", "^7")
        .replace("⁸", "^8")
        .replace("⁹", "^9")
        .replace("½", "\\frac{1}{2}")
        .replace("¼", "\\frac{1}{4}")
        .replace("¾", "\\frac{3}{4}")
        .replace("⅓", "\\frac{1}{3}")
        .replace("⅔", "\\frac{2}{3}")
        .replace("→", "\\rightarrow ")
        .replace("←", "\\leftarrow ")
        .replace("↔", "\\leftrightarrow ")
        .replace("⇒", "\\Rightarrow ")
        .replace("∈", "\\in ")
        .replace("∉", "\\notin ")
        .replace("⊂", "\\subset ")
        .replace("⊆", "\\subseteq ")
        .replace("∪", "\\cup ")
        .replace("∩", "\\cap ")
        .replace("∅", "\\emptyset ")
        .replace("•", "\\cdot ")
        .replace("·", "\\cdot ")

    // Normalize accents and quotes
    s = s.replace("’", "'")
        .replace("‘", "'")
        .replace("“", "\"")
        .replace("”", "\"")

    // 2. Map unsupported macros to standard JLatexMath equivalents with command-boundary awareness (?![a-zA-Z])
    s = s.replace(Regex("""\\varnothing(?![a-zA-Z])""")) { "\\emptyset" }
        .replace(Regex("""\\dots(?![a-zA-Z])""")) { "\\ldots" }
        .replace(Regex("""\\impliedby(?![a-zA-Z])""")) { "\\Longleftarrow" }
        .replace(Regex("""\\implies(?![a-zA-Z])""")) { "\\Longrightarrow" }
        .replace(Regex("""\\iff(?![a-zA-Z])""")) { "\\Longleftrightarrow" }
        .replace(Regex("""\\plusminus(?![a-zA-Z])""")) { "\\pm" }
        .replace(Regex("""\\to(?![a-zA-Z])""")) { "\\rightarrow" }
        .replace(Regex("""\\degree(?![a-zA-Z])""")) { "^{\\circ}" }
        .replace(Regex("""\\not=(?![a-zA-Z])""")) { "\\neq" }
        .replace(Regex("""\\dfrac(?![a-zA-Z])""")) { "\\frac" }
        .replace(Regex("""\\tfrac(?![a-zA-Z])""")) { "\\frac" }
        .replace(Regex("""\\cfrac(?![a-zA-Z])""")) { "\\frac" }
        .replace(Regex("""\\textbf\{""")) { "\\mathbf{" }
        .replace(Regex("""\\textit\{""")) { "\\mathit{" }
        .replace(Regex("""\\boldsymbol\{""")) { "\\mathbf{" }
        .replace(Regex("""\\bm\{""")) { "\\mathbf{" }
        .replace(Regex("""\\operatorname\{""")) { "\\mathrm{" }
        .replace(Regex("""\\middle\s*[|/\\]""")) { "" }
        .replace(Regex("""\\tag\{[^}]*\}""")) { "" }
        .replace(Regex("""\\label\{[^}]*\}""")) { "" }

    // Preserve equation environments and alignment ampersands by dynamically determining column specification
    s = s.replace(Regex("""\\begin\{(aligned|align\*?)\}([\s\S]*?)\\end\{\1\}""")) { match ->
        val body = match.groupValues[2]
        val lines = body.split("\\\\")
        val maxAmp = lines.maxOfOrNull { line -> line.count { it == '&' } } ?: 0
        val colCount = (maxAmp + 1).coerceAtLeast(1)
        val colSpec = (0 until colCount).joinToString("") { if (it % 2 == 0) "r" else "l" }
        "\\begin{array}{$colSpec}$body\\end{array}"
    }.replace(Regex("""\\begin\{gather\*?\}([\s\S]*?)\\end\{gather\*?\}""")) { match ->
        val body = match.groupValues[1]
        "\\begin{array}{c}$body\\end{array}"
    }

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
                .usePlugin(MarkwonInlineParserPlugin.create { factoryBuilder ->
                    factoryBuilder.addInlineProcessor(SingleDollarInlineProcessor())
                })
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder.listItemColor(textColorArgb)
                        val thematicAlpha = 70
                        val thematicColor = android.graphics.Color.argb(
                            thematicAlpha,
                            android.graphics.Color.red(textColorArgb),
                            android.graphics.Color.green(textColorArgb),
                            android.graphics.Color.blue(textColorArgb)
                        )
                        builder.thematicBreakColor(thematicColor)
                    }

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

class SingleDollarInlineProcessor : io.noties.markwon.inlineparser.InlineProcessor() {
    companion object {
        // Regex for inline math $...$
        // Must start with $ and not another $, not end with newline or preceded by backslash, ends with $ not followed by $ or digits
        private val RE = java.util.regex.Pattern.compile("^\\$([^\\$\\n]+?)\\$(?!\\$)")
    }

    override fun specialCharacter(): Char = '$'

    override fun parse(): org.commonmark.node.Node? {
        val matched = match(RE) ?: return null
        val inner = matched.substring(1, matched.length - 1).trim()
        if (inner.isEmpty()) return null
        val node = io.noties.markwon.ext.latex.JLatexMathNode()
        node.latex(inner)
        return node
    }
}

/**
 * Span-aware math preprocessing pipeline:
 * 1. Preserves $...$ as inline math (sanitizing content without converting to $$...$$).
 * 2. Converts \(...\) to single-dollar inline math $...$.
 * 3. Preserves $$...$$ as display math.
 * 4. Converts \[...\] into display math $$...$$ on its own block with appropriate newline boundaries.
 * 5. Converts fenced ```math and ```latex blocks to display math $$...$$.
 * 6. Sanitizes the CONTENT of a mathematical expression without changing delimiters or inserting nested delimiters.
 * 7. Applies bare-LaTeX auto-wrapping ONLY to plain-text spans outside existing formulas, code spans, and code blocks.
 */
fun preprocessMathSpans(raw: String): String {
    if (raw.isBlank()) return raw

    // Match code blocks, inline code, display math, bracket display, inline paren, and inline dollar
    val spanPattern = Regex(
        "(?s)(```(?:math|latex)\\s*[\\s\\S]*?```)" +             // Group 1: fenced math
        "|(```[a-zA-Z]*[\\s\\S]*?```)" +                         // Group 2: fenced code
        "|(`[^`\\n]+`)" +                                        // Group 3: inline code
        "|(\\$\\$[\\s\\S]+?\\$\\$)" +                            // Group 4: display dollar $$...$$
        "|(?<!\\\\left)(\\\\\\[[\\s\\S]+?\\\\\\])(?!\\\\right)" + // Group 5: display bracket \[...\]
        "|(?<!\\\\left)(\\\\\\([\\s\\S]+?\\\\\\))(?!\\\\right)" + // Group 6: inline paren \(...\)
        "|((?<![\\$\\w\\\\])\\$(?!\\$)(?:[^\\$\\n\\\\]|\\\\.)+?\\$(?![\\$0-9]))" // Group 7: inline dollar $...$
    )

    val out = StringBuilder(raw.length + 64)
    var lastIdx = 0

    for (m in spanPattern.findAll(raw)) {
        if (m.range.first > lastIdx) {
            val plainText = raw.substring(lastIdx, m.range.first)
            out.append(wrapBareLatexInPlainText(plainText))
        }

        val g1 = m.groups[1]?.value
        val g2 = m.groups[2]?.value
        val g3 = m.groups[3]?.value
        val g4 = m.groups[4]?.value
        val g5 = m.groups[5]?.value
        val g6 = m.groups[6]?.value
        val g7 = m.groups[7]?.value

        when {
            g1 != null -> {
                // Fenced math: ```math ... ``` or ```latex ... ```
                val inner = g1.replace(Regex("""^```(?:math|latex)\s*"""), "")
                    .replace(Regex("""\s*```$"""), "")
                out.append("\n\n$$").append(sanitizeLatex(inner)).append("$$\n\n")
            }
            g2 != null -> {
                // Fenced code block (e.g. ```kotlin ... ```) -> preserve exactly as-is
                out.append(g2)
            }
            g3 != null -> {
                // Inline code span -> preserve as-is unless it explicitly wraps math like `$$...$$` or `$...$`
                if (g3.startsWith("`$$") && g3.endsWith("$$`")) {
                    val inner = g3.removePrefix("`$$").removeSuffix("$$`")
                    out.append("$$").append(sanitizeLatex(inner)).append("$$")
                } else if (g3.startsWith("`$") && g3.endsWith("$`")) {
                    val inner = g3.removePrefix("`$").removeSuffix("$`")
                    out.append("$").append(sanitizeLatex(inner)).append("$")
                } else {
                    out.append(g3)
                }
            }
            g4 != null -> {
                // Display $$...$$ -> preserve as display math, sanitize inner content only
                val inner = g4.substring(2, g4.length - 2)
                out.append("$$").append(sanitizeLatex(inner)).append("$$")
            }
            g5 != null -> {
                // Display \[...\] -> convert to display math $$...$$ with newline boundaries
                val inner = g5.substring(2, g5.length - 2)
                out.append("\n\n$$").append(sanitizeLatex(inner)).append("$$\n\n")
            }
            g6 != null -> {
                // Inline \(...\) -> convert to inline math $...$
                val inner = g6.substring(2, g6.length - 2)
                out.append("$").append(sanitizeLatex(inner)).append("$")
            }
            g7 != null -> {
                // Inline $...$ -> preserve as inline math, sanitize inner content
                val inner = g7.substring(1, g7.length - 1)
                val isLikelyCurrency = inner.matches(Regex("""\d+(?:\.\d{1,2})?(?:\s*(?:million|billion|thousand|k|m|USD|EUR|GBP|INR))?"""))
                if (isLikelyCurrency) {
                    out.append(g7)
                } else {
                    out.append("$").append(sanitizeLatex(inner)).append("$")
                }
            }
            else -> {
                out.append(m.value)
            }
        }

        lastIdx = m.range.last + 1
    }

    if (lastIdx < raw.length) {
        val plainText = raw.substring(lastIdx)
        out.append(wrapBareLatexInPlainText(plainText))
    }

    return out.toString()
}

/**
 * Apply bare-LaTeX auto-wrapping ONLY to plain-text spans outside existing formulas,
 * code spans, and code blocks.
 */
private fun wrapBareLatexInPlainText(plainText: String): String {
    if (plainText.isEmpty()) return plainText

    val latexTrigger = Regex("""\\[a-zA-Z]+""")
    if (!latexTrigger.containsMatchIn(plainText)) return plainText

    val proseWords = setOf(
        "and", "or", "if", "then", "is", "are", "was", "were", "where", "when", "which", "what", "how",
        "the", "a", "an", "this", "that", "these", "those", "for", "with", "from", "to", "in", "on", "at",
        "by", "of", "equal", "equals", "yields", "gives", "so", "such", "solution", "curve",
        "option", "options", "question", "statement", "value", "values", "constant", "constants",
        "rewriting", "solving", "testing", "lies", "integrating", "differentiating"
    )

    val tokenPattern = Regex(
        """(\\[a-zA-Z]+(?:\s*\{[^{}]*\}|\s*\[[^\[\]]*\]|\s*_\{[^{}]*\}|\s*_\w+|\s*\^\{[^{}]*\}|\s*\^\w+)*)""" +
        """|([a-zA-Z]+)""" +
        """|([0-9]+(?:\.[0-9]+)?)""" +
        """|(_\{[^{}]*\}|_\w+|\^\{[^{}]*\}|\^\w+)""" +
        """|([\+\-\*\/=><,;\.!|\(\)\[\]~\:\?])""" +
        """|(\$)""" +
        """|(\s+)"""
    )

    val matches = tokenPattern.findAll(plainText).toList()
    if (matches.isEmpty()) return plainText

    val result = StringBuilder()
    val matchStrings = matches.map { it.value }
    val matchTypes = matches.map { m ->
        when {
            m.groups[1] != null -> 1 // LaTeX command
            m.groups[2] != null -> 2 // Word
            m.groups[3] != null -> 3 // Number
            m.groups[4] != null -> 4 // Sub/Super
            m.groups[5] != null -> 5 // Operator / Punctuation
            m.groups[6] != null -> 6 // Dollar $
            else -> 7 // Whitespace
        }
    }

    var i = 0
    val n = matches.size

    while (i < n) {
        val t = matchTypes[i]
        val s = matchStrings[i]

        if (t == 1 || t == 4 || (t == 6 && i + 1 < n && matchTypes[i + 1] == 1)) {
            val hasOpeningDollar = (t == 6)
            if (hasOpeningDollar) {
                i++
            }

            var leadStart = result.length
            val prevText = result.toString()

            val leadMatch = Regex("""(?:[a-zA-Z0-9\(\)]+\s*=\s*)$""").find(prevText)
            if (leadMatch != null) {
                val leadStr = leadMatch.value
                val leadProse = leadStr.trim().split(Regex("""\s+""")).any { it.lowercase() in proseWords }
                if (!leadProse) {
                    leadStart = prevText.length - leadStr.length
                }
            }

            val mathBuf = StringBuilder()
            if (leadStart < result.length) {
                mathBuf.append(result.substring(leadStart))
                result.delete(leadStart, result.length)
            }

            if (!hasOpeningDollar) {
                mathBuf.append(s)
            } else if (i < n) {
                mathBuf.append(matchStrings[i])
            }
            i++

            while (i < n) {
                val nxtType = matchTypes[i]
                val nxtStr = matchStrings[i]

                if (nxtType == 2 && proseWords.contains(nxtStr.lowercase())) {
                    break
                }

                if (nxtType == 6) {
                    i++
                    break
                }

                if (nxtType == 5 && nxtStr == ".") {
                    if (i + 1 == n || (matchTypes[i + 1] == 2 && proseWords.contains(matchStrings[i + 1].lowercase()))) {
                        break
                    }
                }

                mathBuf.append(nxtStr)
                i++
            }

            val cleaned = mathBuf.toString().trimEnd()
            val trailingWs = mathBuf.substring(cleaned.length)

            var periodSuffix = ""
            var finalFormula = cleaned
            if (finalFormula.endsWith(".") && !finalFormula.endsWith("..")) {
                finalFormula = finalFormula.dropLast(1).trimEnd()
                periodSuffix = "."
            }

            result.append("$").append(sanitizeLatex(finalFormula)).append("$").append(periodSuffix).append(trailingWs)
        } else if (t == 6) {
            i++
        } else {
            result.append(s)
            i++
        }
    }

    val lastEnd = if (matches.isNotEmpty()) matches.last().range.last + 1 else 0
    if (lastEnd < plainText.length) {
        val tail = plainText.substring(lastEnd)
        if (tail != "$") {
            result.append(tail)
        }
    }

    return result.toString()
}

fun formatRichTextLineBreaks(raw: String): String {
    if (raw.isBlank()) return raw

    // 1. Restore corrupted escapes from JSON
    val restored = restoreCorruptedLatexEscapes(raw)

    // 2. Preprocess math spans: standardizes delimiters ($ for inline, $$ for display),
    // sanitizes inner content without modifying outer delimiters or nesting dollars,
    // and wraps bare LaTeX only in eligible plain text spans.
    val text = preprocessMathSpans(restored)

    // 3. Protect ALL mathematical expressions (both inline $...$ and display $$...$$) and code blocks
    // so transformNonMathLineBreaks NEVER transforms or mangles content inside a protected mathematical expression!
    val protectedPattern = Regex("(?s)(?:```[a-zA-Z]*[\\s\\S]*?```|`[^`]+`|\\$\\$[\\s\\S]+?\\$\\$|(?<![\\$\\w\\\\])\\$(?!\\$)(?:[^\\$\\n\\\\]|\\\\.)+?\\$(?![\\$0-9]))")
    val matches = protectedPattern.findAll(text).toList()

    if (matches.isEmpty()) {
        return transformNonMathLineBreaks(text)
    }

    val sb = StringBuilder(text.length + 32)
    var lastIdx = 0
    for (m in matches) {
        val nonMathPart = text.substring(lastIdx, m.range.first)
        if (nonMathPart.isNotEmpty()) {
            sb.append(transformNonMathLineBreaks(nonMathPart))
        }
        sb.append(m.value)
        lastIdx = m.range.last + 1
    }
    if (lastIdx < text.length) {
        sb.append(transformNonMathLineBreaks(text.substring(lastIdx)))
    }
    return sb.toString()
}

private fun transformNonMathLineBreaks(text: String): String {
    if (text.isEmpty()) return text

    // 1. Unescape literal backslash newlines from JSON or raw text strings
    var s = text
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")

    // 2. Convert <br> or <br/> tags to newlines so CommonMark recognizes line breaks and block boundaries
    s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")

    // 3. Normalize CRLF to LF
    s = s.replace("\r\n", "\n")

    // 4. Ensure Markdown block boundaries (Headings, Horizontal Rules, Lists, Code Blocks, Blockquotes)
    // are preceded by an empty line so CommonMark treats them as distinct blocks rather than inline text.
    val lines = s.split("\n")
    val result = StringBuilder()
    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trim()
        val isHeading = trimmed.startsWith("#") && trimmed.matches(Regex("^#{1,6}\\s+.*"))
        val isHorizontalRule = trimmed.matches(Regex("^[-*_]{3,}$"))
        val isListItem = trimmed.matches(Regex("^([*+\\-]\\s+|\\d+\\.\\s+).*"))
        val isCodeBlock = trimmed.startsWith("```")
        val isBlockQuote = trimmed.startsWith(">")

        if (i > 0) {
            val prevTrimmed = lines[i - 1].trim()
            val prevIsListItem = prevTrimmed.matches(Regex("^([*+\\-]\\s+|\\d+\\.\\s+).*"))
            val prevIsBlockQuote = prevTrimmed.startsWith(">")

            val needsBlankLineBefore = when {
                isHeading -> prevTrimmed.isNotEmpty()
                isHorizontalRule -> prevTrimmed.isNotEmpty()
                isCodeBlock -> prevTrimmed.isNotEmpty()
                isListItem -> prevTrimmed.isNotEmpty() && !prevIsListItem
                isBlockQuote -> prevTrimmed.isNotEmpty() && !prevIsBlockQuote
                else -> false
            }

            if (needsBlankLineBefore) {
                result.append("\n")
            }
        }

        result.append(line)

        if (isHorizontalRule && i < lines.size - 1) {
            val nextTrimmed = lines[i + 1].trim()
            if (nextTrimmed.isNotEmpty()) {
                result.append("\n")
            }
        }

        if (i < lines.size - 1) {
            result.append("\n")
        }
    }
    return result.toString()
}

object MathFormattingCache {
    private val formattedTextCache = android.util.LruCache<String, String>(1000)
    private val spannableCache = android.util.LruCache<Triple<String, Float, Int>, Spannable>(500)

    fun getFormattedText(raw: String): String {
        if (raw.isBlank()) return raw
        val cached = formattedTextCache.get(raw)
        if (cached != null) return cached
        val formatted = formatRichTextLineBreaks(raw)
        formattedTextCache.put(raw, formatted)
        return formatted
    }

    fun getSpannable(
        markwon: Markwon,
        processedText: String,
        fontSizePx: Float,
        colorArgb: Int
    ): Spannable {
        val key = Triple(processedText, fontSizePx, colorArgb)
        val cached = spannableCache.get(key)
        if (cached != null) {
            return SpannableString(cached)
        }
        val markdownNode = markwon.toMarkdown(processedText)
        val spannable = if (markdownNode is Spannable) markdownNode else SpannableString(markdownNode)
        spannableCache.put(key, SpannableString(spannable))
        return SpannableString(spannable)
    }
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
        MathFormattingCache.getFormattedText(text)
    }

    val textColorArgb = if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK

    var zoomableImageUrl by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val spannableToDisplay = remember(processedText, markwon, searchQuery, highlightColor, textColorArgb) {
        val baseSpannable = MathFormattingCache.getSpannable(markwon, processedText, fontSizePx, textColorArgb)
        val spannable = SpannableString(baseSpannable)
        
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
                if (isHorizontallyScrollable) {
                    setHorizontallyScrolling(true)
                    setTextIsSelectable(false)
                    movementMethod = null
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                } else {
                    setTextIsSelectable(isSelectable)
                }
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
            if (isHorizontallyScrollable) {
                textView.setHorizontallyScrolling(true)
                textView.setTextIsSelectable(false)
                textView.movementMethod = null
                textView.layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                textView.setTextIsSelectable(isSelectable)
            }
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
