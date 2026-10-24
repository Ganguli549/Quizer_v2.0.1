package com.example.ui.components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ReplacementSpan
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import io.noties.markwon.ext.latex.JLatexMathNode
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
import kotlin.math.roundToInt

class TextDrawable(private val text: String, private val textSize: Float, private val textColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@TextDrawable.textColor
        this.textSize = this@TextDrawable.textSize
    }

    private fun getSanitizedLines(): List<String> {
        return text
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace(Regex("""<\/?[a-zA-Z][a-zA-Z0-9]*(?:\s+[^<>]*)?>"""), "")
            .split("\n")
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

/**
 * Custom synchronous ReplacementSpan for rendering JLatexMath drawables:
 * 1. Eliminates Markwon AsyncDrawable callback stealing when duplicate formulas appear on screen.
 * 2. Centers formulas on the font's math axis ((ascent + descent) / 2) while preserving the
 *    native font metrics floor so math-only lines (like Option "20") align with "A.", "B.", "C.", "D.".
 * 3. Proportionally scales down wide inline formulas if they exceed maxWidthPx so they never clip.
 */
class AlignedLatexSpan(
    private val drawable: Drawable,
    private val maxWidthPx: Int = Int.MAX_VALUE
) : ReplacementSpan() {

    private val rawWidth: Int = drawable.intrinsicWidth.coerceAtLeast(1)
    private val rawHeight: Int = drawable.intrinsicHeight.coerceAtLeast(1)

    private val scale: Float = if (maxWidthPx in 1 until rawWidth) {
        maxWidthPx.toFloat() / rawWidth.toFloat()
    } else {
        1f
    }

    private val scaledWidth: Int = (rawWidth * scale).roundToInt().coerceAtLeast(1)
    private val scaledHeight: Int = (rawHeight * scale).roundToInt().coerceAtLeast(1)

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val paintFm = paint.fontMetricsInt
            val textCenter = (paintFm.ascent + paintFm.descent) / 2
            val halfH = scaledHeight / 2
            val spanTop = textCenter - halfH
            val spanBottom = textCenter + (scaledHeight - halfH)

            // Preserve native font metrics as a minimum floor so math-only TextViews (e.g. options)
            // have the exact same baseline and line height as standard text, while tall fractions
            // expand symmetrically above and below the math axis.
            fm.ascent = minOf(paintFm.ascent, spanTop)
            fm.top = minOf(paintFm.top, spanTop)
            fm.descent = maxOf(paintFm.descent, spanBottom)
            fm.bottom = maxOf(paintFm.bottom, spanBottom)
        }
        return scaledWidth
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val paintFm = paint.fontMetricsInt
        val textCenter = y + (paintFm.ascent + paintFm.descent) / 2f
        val transY = textCenter - (scaledHeight / 2f)

        canvas.save()
        canvas.translate(x, transY)
        if (scale != 1f) {
            canvas.scale(scale, scale)
        }
        drawable.draw(canvas)
        canvas.restore()
    }
}

object LatexDrawableCache {
    private val cache = android.util.LruCache<Triple<String, Int, Int>, Drawable>(600)

    fun clearCache() {
        cache.evictAll()
    }

    fun getOrCreate(rawLatex: String, fontSizePx: Float, textColorArgb: Int): Drawable {
        val cleanKey = rawLatex.trim()
        val sizeKey = (fontSizePx * 10f).roundToInt()
        val key = Triple(cleanKey, sizeKey, textColorArgb)
        val cached = cache.get(key)
        if (cached != null) return cached

        val created = trySalvageLatex(cleanKey, fontSizePx, textColorArgb)
            ?: TextDrawable(cleanKey.ifEmpty { "MathError" }, fontSizePx, textColorArgb).apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
        cache.put(key, created)
        return created
    }
}

private const val KNOWN_LATEX_COMMAND_NAMES =
    "frac|dfrac|tfrac|cfrac|sqrt|root|sum|prod|coprod|int|iint|iiint|oint|lim|limsup|liminf|" +
    "sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|sinh|cosh|tanh|coth|" +
    "log|ln|lg|exp|det|dim|ker|hom|arg|deg|gcd|lcm|min|max|sup|inf|bmod|pmod|" +
    "alpha|beta|gamma|delta|epsilon|varepsilon|zeta|eta|theta|vartheta|iota|kappa|lambda|mu|nu|xi|pi|varpi|" +
    "rho|varrho|sigma|varsigma|tau|upsilon|phi|varphi|chi|psi|omega|" +
    "Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega|" +
    "times|div|pm|mp|cdot|cdots|ldots|ddots|vdots|ast|star|circ|bullet|oplus|ominus|otimes|oslash|odot|" +
    "leq|geq|neq|approx|equiv|cong|sim|simeq|asymp|propto|ll|gg|prec|succ|preceq|succeq|" +
    "subset|supset|subseteq|supseteq|nsubseteq|nsupseteq|in|notin|ni|cup|cap|setminus|uplus|sqsubset|sqsupset|sqsubseteq|sqsupseteq|sqcup|sqcap|" +
    "wedge|vee|land|lor|neg|lnot|forall|exists|nexists|emptyset|varnothing|infty|partial|nabla|aleph|hbar|ell|wp|Re|Im|" +
    "therefore|because|angle|measuredangle|sphericalangle|triangle|triangledown|triangleleft|triangleright|perp|parallel|nparallel|mid|nmid|" +
    "rightarrow|leftarrow|leftrightarrow|Rightarrow|Leftarrow|Leftrightarrow|implies|impliedby|iff|to|mapsto|longmapsto|" +
    "longrightarrow|longleftarrow|longleftrightarrow|Longrightarrow|Longleftarrow|Longleftrightarrow|" +
    "uparrow|downarrow|updownarrow|Uparrow|Downarrow|Updownarrow|nearrow|searrow|swarrow|nwarrow|rightharpoonup|rightharpoondown|leftharpoonup|leftharpoondown|rightleftharpoons|" +
    "vec|hat|widehat|bar|overline|underline|tilde|widetilde|dot|ddot|acute|grave|check|breve|mathring|" +
    "mathbf|mathrm|mathit|mathcal|mathbb|mathfrak|mathsf|mathtt|textbf|textit|textrm|texttt|textsf|text|mbox|boldsymbol|bm|opt|operatorname|" +
    "binom|tbinom|dbinom|choose|brack|brace|left|right|big|Big|bigg|Bigg|bigl|bigr|Bigl|Bigr|biggl|biggr|" +
    "begin|end|array|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|aligned|align|gather|cases|" +
    "lfloor|rfloor|lceil|rceil|langle|rangle|vert|Vert|backslash|" +
    "degree|prime|quad|qquad|hspace|vspace|phantom|hphantom|vphantom|not|cancel|bcancel|xcancel|boxed|color|textcolor|" +
    "le|ge|ne|lt|gt|dots|plusminus|natural|nless|ngtr|nleq|ngeq|ncong|nsim|nvdash|nvDash"

private val KNOWN_LATEX_COMMAND_REGEX = Regex("""\\(?:$KNOWN_LATEX_COMMAND_NAMES)(?![a-zA-Z])""")

private val NON_LATEX_ESCAPED_NEWLINE_REGEX = Regex(
    """\\r\\n(?!(?:eq|abla|earrow|eg|atural|less|gtr|leq|geq|subseteq|supseteq|mid|parallel|warrow|otin|exists|cong|sim|vdash|vDash|Leftarrow|Rightarrow|Leftrightarrow|leftarrow|rightarrow|leftrightarrow|ewline)(?![a-zA-Z])|(?:u|e|i|ot)(?![a-zA-Z.\):]))""" +
    """|\\n(?!(?:eq|abla|earrow|eg|atural|less|gtr|leq|geq|subseteq|supseteq|mid|parallel|warrow|otin|exists|cong|sim|vdash|vDash|Leftarrow|Rightarrow|Leftrightarrow|leftarrow|rightarrow|leftrightarrow|ewline)(?![a-zA-Z])|(?:u|e|i|ot)(?![a-zA-Z.\):]))"""
)

private val NON_LATEX_ESCAPED_TAB_REGEX = Regex(
    """\\t(?!(?:imes|heta|ext|extbf|extit|extrm|exttt|extsf|extcolor|an|anh|au|o|op|ilde|herefore|riangle|riangleq|riangleleft|riangleright|frac|binom|hickapprox|hicksim|woheadrightarrow|woheadleftarrow|ag)(?![a-zA-Z]))"""
)

private val HTML_TAG_REGEX = Regex("""<\/?[a-zA-Z][a-zA-Z0-9]*(?:\s+[^<>]*)?>""")
private val INLINE_SINGLE_DOLLAR_REGEX = Regex("""(?<!\$)\$(?!\$)([^\$]+?)\$(?!\$)""")

fun unescapeLiteralNewlinesAndTabs(text: String): String {
    if (text.isEmpty() || !text.contains('\\')) return text
    return text
        .replace(NON_LATEX_ESCAPED_NEWLINE_REGEX, "\n")
        .replace(NON_LATEX_ESCAPED_TAB_REGEX, "\t")
}

fun repairCorruptedMathAndEscapes(raw: String): String {
    if (raw.isEmpty()) return raw
    var s = raw
    if (s.contains('$')) {
        s = INLINE_SINGLE_DOLLAR_REGEX.replace(s) { m ->
            val inner = m.groupValues[1]
            val hasEscapedNl = NON_LATEX_ESCAPED_NEWLINE_REGEX.containsMatchIn(inner)
            val hasRealNl = inner.contains('\n') || inner.contains('\r')
            val hasHtml = HTML_TAG_REGEX.containsMatchIn(inner)

            if (hasEscapedNl || hasRealNl || hasHtml) {
                val unescaped = unescapeLiteralNewlinesAndTabs(inner)
                val withoutHtml = unescaped.replace(HTML_TAG_REGEX, " ")
                val hasLatex = KNOWN_LATEX_COMMAND_REGEX.containsMatchIn(withoutHtml)
                val hasSubSup = withoutHtml.contains('^') || withoutHtml.contains('_')
                if (!hasLatex && !hasSubSup) {
                    unescaped
                } else if (unescaped.startsWith("\n") || unescaped.startsWith("\r")) {
                    val stripped = unescaped.trimStart('\r', '\n', ' ')
                    val lead = unescaped.substring(0, unescaped.length - stripped.length)
                    "$lead$$stripped$"
                } else {
                    m.value
                }
            } else {
                m.value
            }
        }
    }
    return unescapeLiteralNewlinesAndTabs(s)
}

fun sanitizeCorruptedQuestionEntity(q: com.example.data.QuestionEntity): com.example.data.QuestionEntity {
    val fixedQuestion = repairCorruptedMathAndEscapes(q.question)
    val fixedExplanation = repairCorruptedMathAndEscapes(q.explanation)
    val fixedDetailed = q.detailedExplanation?.let { repairCorruptedMathAndEscapes(it) }
    val fixedHeader = q.questionHeader?.let {
        if (it.trimStart().startsWith("{")) it else repairCorruptedMathAndEscapes(it)
    }
    val fixedSection = if (q.section.trimStart().startsWith("{")) q.section else repairCorruptedMathAndEscapes(q.section)
    val fixedBottomText = q.bottomText?.let { repairCorruptedMathAndEscapes(it) }

    return if (
        fixedQuestion != q.question ||
        fixedExplanation != q.explanation ||
        fixedDetailed != q.detailedExplanation ||
        fixedHeader != q.questionHeader ||
        fixedSection != q.section ||
        fixedBottomText != q.bottomText
    ) {
        q.copy(
            question = fixedQuestion,
            explanation = fixedExplanation,
            detailedExplanation = fixedDetailed,
            questionHeader = fixedHeader,
            section = fixedSection,
            bottomText = fixedBottomText
        )
    } else {
        q
    }
}

fun restoreCorruptedLatexEscapes(text: String): String {
    if (text.isEmpty()) return text
    var s = text
    // 1. Form feed \u000c (ASCII 12) from unescaped \f in JSON (e.g. \frac, \forall, \flat, \frown)
    s = s.replace(Regex("""\u000c(?=[a-zA-Z])"""), "\\\\f")

    // 2. Backspace \u0008 (ASCII 8) from unescaped \b in JSON (e.g. \beta, \bar, \binom, \begin, \mathbf)
    s = s.replace(Regex("""\u0008(?=[a-zA-Z])"""), "\\\\b")

    // 3. Tab \t (\u0009) from unescaped \t in JSON before LaTeX command names
    s = s.replace(
        Regex("""\t(?=(?:imes|heta|ext|extbf|extit|extrm|an|au|o|op|ilde|herefore|riangle|frac|hick|wohead|ag)(?![a-zA-Z]))"""),
        "\\\\t"
    )

    // 4. Carriage return \r (\u000d) before LaTeX command keywords
    s = s.replace(
        Regex("""\r(?=(?:ight|ho|rightarrow|Rightarrow|rangle|rfloor|rceil|Re|root|mathrm|mathbf|mathit|mathcal|mathbb|rightharpoonup|rtimes)(?![a-zA-Z]))"""),
        "\\\\r"
    )

    // 5. Newline \n (\u000a) before explicit multi-letter LaTeX command keywords
    s = s.replace(
        Regex("""\n(?=(?:eq|abla|nearrow|neg|atural|less|gtr|leq|geq|subseteq|supseteq|mid|parallel|warrow|otin)(?![a-zA-Z]))"""),
        "\\\\n"
    )

    return s
}

fun cleanExtractedMathText(text: String): String {
    if (text.isBlank()) return ""
    val trimmed = text.trim()
    if (trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true) || trimmed.equals("none", ignoreCase = true)) {
        return ""
    }
    val repaired = repairCorruptedMathAndEscapes(text)
    val restored = restoreCorruptedLatexEscapes(repaired)
    return preprocessMathSpans(restored)
}

fun sanitizeLatex(input: String): String {
    if (input.isBlank()) return input

    var s = restoreCorruptedLatexEscapes(input.trim())

    s = s.replace(Regex("""(?is)<(strong|b)\b[^>]*>(.*?)</\1>""")) { "\\mathbf{${it.groupValues[2]}}" }
        .replace(Regex("""(?is)<(i|em)\b[^>]*>(.*?)</\1>""")) { "\\mathit{${it.groupValues[2]}}" }
        .replace(Regex("""(?is)<sub\b[^>]*>(.*?)</sub>""")) { "_{${it.groupValues[1]}}" }
        .replace(Regex("""(?is)<sup\b[^>]*>(.*?)</sup>""")) { "^{${it.groupValues[1]}}" }
        .replace(HTML_TAG_REGEX, "")

    // Normalize over-escaped LaTeX commands (e.g. \\tan -> \tan, \\frac -> \frac, \\% -> \%),
    // but preserve valid LaTeX row-break sequences like \\ followed by whitespace/newline/closing bracket
    s = s.replace(Regex("""\\\\(?=[a-zA-Z%])""")) { "\\" }

    // Escape bare % inside LaTeX math mode so JLatexMath does not treat % as a comment character
    // that discards the rest of the expression
    s = s.replace(Regex("""(?<!\\)%"""), "\\\\%")

    // 1. Normalize unicode math and typography characters to standard LaTeX
    s = s.replace("×", "\\times ")
        .replace("÷", "\\div ")
        .replace("±", "\\pm ")
        .replace("∓", "\\mp ")
        .replace("≤", "\\leq ")
        .replace("≥", "\\geq ")
        .replace("≠", "\\neq ")
        .replace("≈", "\\approx ")
        .replace("≡", "\\equiv ")
        .replace("∝", "\\propto ")
        .replace("∞", "\\infty ")
        .replace("√", "\\sqrt ")
        .replace("π", "\\pi ")
        .replace("θ", "\\theta ")
        .replace("α", "\\alpha ")
        .replace("β", "\\beta ")
        .replace("γ", "\\gamma ")
        .replace("δ", "\\delta ")
        .replace("ε", "\\epsilon ")
        .replace("λ", "\\lambda ")
        .replace("μ", "\\mu ")
        .replace("σ", "\\sigma ")
        .replace("φ", "\\phi ")
        .replace("ϕ", "\\phi ")
        .replace("ω", "\\omega ")
        .replace("Δ", "\\Delta ")
        .replace("Ω", "\\Omega ")
        .replace("∑", "\\sum ")
        .replace("∏", "\\prod ")
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
        .replace("⅕", "\\frac{1}{5}")
        .replace("⅖", "\\frac{2}{5}")
        .replace("⅗", "\\frac{3}{5}")
        .replace("⅘", "\\frac{4}{5}")
        .replace("⅙", "\\frac{1}{6}")
        .replace("⅚", "\\frac{5}{6}")
        .replace("⅛", "\\frac{1}{8}")
        .replace("⅜", "\\frac{3}{8}")
        .replace("⅝", "\\frac{5}{8}")
        .replace("⅞", "\\frac{7}{8}")
        .replace("→", "\\rightarrow ")
        .replace("←", "\\leftarrow ")
        .replace("↔", "\\leftrightarrow ")
        .replace("⇒", "\\Rightarrow ")
        .replace("⇔", "\\Leftrightarrow ")
        .replace("∈", "\\in ")
        .replace("∉", "\\notin ")
        .replace("⊂", "\\subset ")
        .replace("⊆", "\\subseteq ")
        .replace("∪", "\\cup ")
        .replace("∩", "\\cap ")
        .replace("∅", "\\emptyset ")
        .replace("•", "\\cdot ")
        .replace("·", "\\cdot ")
        .replace("−", "-")
        .replace("–", "-")
        .replace("′", "'")
        .replace("″", "''")
        .replace("₹", "\\text{Rs. }")

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
        .replace(Regex("""\\le(?![a-zA-Z])""")) { "\\leq" }
        .replace(Regex("""\\ge(?![a-zA-Z])""")) { "\\geq" }
        .replace(Regex("""\\ne(?![a-zA-Z])""")) { "\\neq" }
        .replace(Regex("""\\lt(?![a-zA-Z])""")) { "<" }
        .replace(Regex("""\\gt(?![a-zA-Z])""")) { ">" }
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
            if (next == '{' || next == '}' || next == '%') {
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
    s = sb.toString().trim()

    // Ensure standalone superscript/subscript at start of expression has an empty base group {}
    if (s.startsWith("^") || s.startsWith("_")) {
        s = "{}$s"
    }

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
    private val cache = mutableMapOf<Triple<Float, Color, Int>, Markwon>()
    @Volatile
    private var jlatexInitialized = false

    private fun ensureJLatexInitialized(context: android.content.Context) {
        if (!jlatexInitialized) {
            synchronized(this) {
                if (!jlatexInitialized) {
                    try {
                        ru.noties.jlatexmath.JLatexMathAndroid.init(context.applicationContext ?: context)
                    } catch (_: Exception) {
                    }
                    jlatexInitialized = true
                }
            }
        }
    }

    fun get(
        context: android.content.Context,
        fontSizePx: Float,
        color: Color,
        maxFormulaWidthPx: Int = Int.MAX_VALUE
    ): Markwon {
        ensureJLatexInitialized(context)
        val key = Triple(fontSizePx, color, maxFormulaWidthPx)
        return synchronized(cache) {
            cache.getOrPut(key) {
                val textColorArgb = if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK
                Markwon.builder(context)
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(CoilImagesPlugin.create(context))
                    .usePlugin(MarkwonInlineParserPlugin.create { factoryBuilder ->
                        factoryBuilder.addInlineProcessor(SingleDollarInlineProcessor())
                    })
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(
                        JLatexMathPlugin.create(
                            fontSizePx,
                            object : JLatexMathPlugin.BuilderConfigure {
                                override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                                    // Disable JLatexMathPlugin's buggy async parser/loader; SingleDollarInlineProcessor
                                    // and our synchronous JLatexMathNode visitor below handle both $...$ and $$...$$.
                                    builder.inlinesEnabled(false)
                                    builder.blocksEnabled(false)
                                }
                            }
                        )
                    )
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
                            // Synchronous, math-axis-aligned rendering for all $...$ and $$...$$ nodes
                            builder.on(JLatexMathNode::class.java) { visitor, node ->
                                val rawLatex = node.latex()
                                if (!rawLatex.isNullOrBlank()) {
                                    val drawable = LatexDrawableCache.getOrCreate(rawLatex, fontSizePx, textColorArgb)
                                    val start = visitor.length()
                                    val singleLinePlaceholder = rawLatex.replace('\n', ' ').replace('\r', ' ')
                                    visitor.builder().append(singleLinePlaceholder)
                                    visitor.setSpans(start, AlignedLatexSpan(drawable, maxFormulaWidthPx))
                                }
                            }
                        }
                    })
                    .build()
            }
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

data class SelectionMenuUiState(
    val selectedText: String,
    val selectionBoundsInView: android.graphics.Rect
)

class CustomSelectionActionModeCallback(
    var customItems: List<CustomTextMenuItem> = emptyList()
) : android.view.ActionMode.Callback2() {
    var textView: TextView? = null
    var activeActionMode: android.view.ActionMode? = null
    var onSelectionStateUpdated: (() -> Unit)? = null
    var onSelectionDestroyed: (() -> Unit)? = null
    private var isDestroying = false

    companion object {
        const val ID_COPY = 10001
        const val ID_SELECT_ALL = 10002
        const val ID_SHARE = 10003
    }

    override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
        isDestroying = false
        if (mode == null || menu == null) return false
        activeActionMode = mode
        // Suppress the un-themed system FloatingToolbar while keeping native TextView selection handles active!
        menu.clear()
        onSelectionStateUpdated?.invoke()
        return true
    }

    override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
        if (mode == null || menu == null) return false
        activeActionMode = mode
        menu.clear()
        onSelectionStateUpdated?.invoke()
        return true
    }

    override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
        return false
    }

    override fun onGetContentRect(
        mode: android.view.ActionMode?,
        view: android.view.View?,
        outRect: android.graphics.Rect?
    ) {
        val tv = textView
        if (tv is RichTextView && outRect != null) {
            val bounds = tv.computeSelectionBoundsInView()
            if (bounds != null) {
                outRect.set(bounds)
            } else {
                outRect.set(0, 0, tv.width, tv.height)
            }
        }
        onSelectionStateUpdated?.invoke()
    }

    fun finishSelection() {
        val tv = textView
        val mode = activeActionMode
        if (mode != null && !isDestroying) {
            isDestroying = true
            try {
                mode.finish()
            } catch (e: Exception) {
                isDestroying = false
            }
        }
        activeActionMode = null
        if (tv != null) {
            val spannable = tv.text as? android.text.Spannable
            if (spannable != null) {
                android.text.Selection.removeSelection(spannable)
            }
            tv.clearFocus()
        }
        onSelectionDestroyed?.invoke()
    }

    override fun onDestroyActionMode(mode: android.view.ActionMode?) {
        isDestroying = true
        activeActionMode = null
        onSelectionDestroyed?.invoke()
    }
}

private class SelectionMenuPopupPositionProvider(
    private val selectionRectInView: android.graphics.Rect,
    private val density: Float
) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize
    ): androidx.compose.ui.unit.IntOffset {
        val marginPx = (12f * density).toInt()
        val topSafeBarPx = (62f * density).toInt()
        val bottomSafeBarPx = (56f * density).toInt()
        val gapAbovePx = (10f * density).toInt()
        val gapBelowHandlesPx = (30f * density).toInt()

        val selLeft = anchorBounds.left + selectionRectInView.left
        val selRight = anchorBounds.left + selectionRectInView.right
        val selTop = anchorBounds.top + selectionRectInView.top
        val selBottom = anchorBounds.top + selectionRectInView.bottom

        val centerX = (selLeft + selRight) / 2
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (centerX - popupContentSize.width / 2).coerceIn(marginPx, maxX)

        val candidateAboveY = selTop - popupContentSize.height - gapAbovePx
        val candidateBelowY = selBottom + gapBelowHandlesPx
        val maxY = (windowSize.height - popupContentSize.height - bottomSafeBarPx).coerceAtLeast(topSafeBarPx)

        val y = when {
            candidateAboveY >= topSafeBarPx -> candidateAboveY.coerceAtMost(maxY)
            candidateBelowY <= maxY -> candidateBelowY.coerceAtLeast(topSafeBarPx)
            else -> (selTop + gapAbovePx).coerceIn(topSafeBarPx, maxY)
        }
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

@Composable
private fun RichTextSelectionMenuPopup(
    menuState: SelectionMenuUiState,
    customItems: List<CustomTextMenuItem>,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCustomItemClick: (CustomTextMenuItem) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val positionProvider = remember(menuState.selectionBoundsInView, density) {
        SelectionMenuPopupPositionProvider(menuState.selectionBoundsInView, density)
    }

    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val baseSurface = colorScheme.surface
    val baseText = colorScheme.onSurface
    val primaryColor = colorScheme.primary
    val isDarkSurface = androidx.core.graphics.ColorUtils.calculateLuminance(baseSurface.toArgb()) < 0.5

    // Tonal elevated card color that strictly follows ReadBookScreen's active ReadingTheme (Day, Night, Sepia, E-Ink, BookPage, System)
    val popupContainerColor = remember(baseSurface, baseText, isDarkSurface) {
        Color(
            androidx.core.graphics.ColorUtils.blendARGB(
                baseSurface.toArgb(),
                baseText.toArgb(),
                if (isDarkSurface) 0.10f else 0.04f
            )
        )
    }
    val popupBorderColor = remember(primaryColor, baseText, isDarkSurface) {
        Color(
            androidx.core.graphics.ColorUtils.blendARGB(
                baseText.copy(alpha = if (isDarkSurface) 0.24f else 0.16f).toArgb(),
                primaryColor.copy(alpha = 0.35f).toArgb(),
                0.4f
            )
        )
    }
    val dividerColor = baseText.copy(alpha = if (isDarkSurface) 0.18f else 0.12f)

    // Order custom items so Ask AI, Bookmark, and Edit appear cleanly
    val orderedCustomItems = remember(customItems) {
        customItems.sortedBy { it.order }
    }

    androidx.compose.ui.window.Popup(
        popupPositionProvider = positionProvider,
        properties = androidx.compose.ui.window.PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = true
        )
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            color = popupContainerColor,
            contentColor = baseText,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, popupBorderColor)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                orderedCustomItems.forEach { item ->
                    val isAskAi = item.id == 99993 || item.title.contains("AI", ignoreCase = true)
                    val isBookmark = item.id == 99991 || item.title.contains("Bookmark", ignoreCase = true) || item.title.contains("Unbookmark", ignoreCase = true)
                    val isBookmarkedActive = item.title.startsWith("Unbookmark", ignoreCase = true) || item.iconRes == com.example.R.drawable.ic_bookmark
                    val isEdit = item.id == 99992 || item.title.contains("Edit", ignoreCase = true)

                    val shortLabel = when {
                        isAskAi -> "Ask AI"
                        isBookmark -> if (isBookmarkedActive) "Saved" else "Save"
                        isEdit -> "Edit"
                        else -> item.title.take(10)
                    }

                    val iconVector = when {
                        isAskAi -> Icons.Default.AutoAwesome
                        isBookmark -> if (isBookmarkedActive) Icons.Default.Bookmark else Icons.Default.BookmarkBorder
                        isEdit -> Icons.Default.Edit
                        else -> Icons.Default.Star
                    }

                    val iconTint = when {
                        isAskAi -> primaryColor
                        isBookmark && isBookmarkedActive -> Color(0xFFFFB300)
                        isBookmark -> primaryColor
                        else -> baseText
                    }

                    val labelColor = when {
                        isAskAi -> primaryColor
                        isBookmark && isBookmarkedActive -> Color(0xFFFFB300)
                        else -> baseText
                    }

                    val pillBg = if (isAskAi) {
                        primaryColor.copy(alpha = if (isDarkSurface) 0.18f else 0.12f)
                    } else if (isBookmark && isBookmarkedActive) {
                        Color(0xFFFFB300).copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    }

                    SelectionToolbarButton(
                        icon = iconVector,
                        label = shortLabel,
                        iconTint = iconTint,
                        labelColor = labelColor,
                        containerColor = pillBg,
                        isEmphasized = isAskAi,
                        onClick = { onCustomItemClick(item) }
                    )
                }

                if (orderedCustomItems.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(1.dp)
                            .height(28.dp)
                            .background(dividerColor)
                    )
                }

                SelectionToolbarButton(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    iconTint = baseText,
                    labelColor = baseText,
                    onClick = onCopy
                )

                SelectionToolbarButton(
                    icon = Icons.Default.SelectAll,
                    label = "All",
                    iconTint = baseText,
                    labelColor = baseText,
                    onClick = onSelectAll
                )

                SelectionToolbarButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    iconTint = baseText,
                    labelColor = baseText,
                    onClick = onShare
                )
            }
        }
    }
}

@Composable
private fun SelectionToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    labelColor: Color,
    containerColor: Color = Color.Transparent,
    isEmphasized: Boolean = false,
    onClick: () -> Unit
) {
    val itemShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .clip(itemShape)
            .background(containerColor, itemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
        androidx.compose.material3.Text(
            text = label,
            color = labelColor,
            fontSize = 10.5.sp,
            fontWeight = if (isEmphasized) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 1
        )
    }
}

class SingleDollarInlineProcessor : io.noties.markwon.inlineparser.InlineProcessor() {
    companion object {
        // Match display $$...$$ first, then inline $...$
        private val RE_DISPLAY = java.util.regex.Pattern.compile("^\\$\\$([\\s\\S]+?)\\$\\$")
        private val RE_INLINE = java.util.regex.Pattern.compile("^\\$([^\\$\\n]+?)\\$(?!\\$)")
    }

    override fun specialCharacter(): Char = '$'

    override fun parse(): org.commonmark.node.Node? {
        val displayMatched = match(RE_DISPLAY)
        if (displayMatched != null) {
            val inner = displayMatched.substring(2, displayMatched.length - 2).trim()
            if (inner.isEmpty()) return null
            val node = JLatexMathNode()
            node.latex(inner)
            return node
        }

        val inlineMatched = match(RE_INLINE) ?: return null
        val inner = inlineMatched.substring(1, inlineMatched.length - 1).trim()
        if (inner.isEmpty()) return null
        val node = JLatexMathNode()
        node.latex(inner)
        return node
    }
}

/**
 * Merges patterns where a parenthesized math group immediately precedes a superscript/subscript
 * dollar span, e.g. `(0.08\% \text{ of } 0.008\% \text{ of } 8)$^{\frac{1}{9}}$`
 * -> `$(0.08\% \text{ of } 0.008\% \text{ of } 8)^{\frac{1}{9}}$`.
 */
private fun mergeSplitBaseAndExponentSpans(raw: String): String {
    if (!raw.contains("$^") && !raw.contains("\$_")) return raw
    return raw.replace(
        Regex("""(\([^()\n]+\))\s*\$\s*([\^_](?:\{(?:[^{}]|\{[^{}]*\})*\}|[a-zA-Z0-9]+))\s*\$""")
    ) { m ->
        "$" + m.groupValues[1] + m.groupValues[2] + "$"
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

    val mergedRaw = mergeSplitBaseAndExponentSpans(raw)

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

    val out = StringBuilder(mergedRaw.length + 64)
    var lastIdx = 0

    for (m in spanPattern.findAll(mergedRaw)) {
        if (m.range.first > lastIdx) {
            val plainText = mergedRaw.substring(lastIdx, m.range.first)
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
                out.append("\n\n$$").append(sanitizeLatex(inner).replace(Regex("""\n\s*\n"""), "\n")).append("$$\n\n")
            }
            g2 != null -> {
                // Fenced code block (e.g. ```kotlin ... ```) -> preserve exactly as-is
                out.append(g2)
            }
            g3 != null -> {
                // Inline code span -> preserve as-is unless it explicitly wraps math like `$$...$$` or `$...$`
                if (g3.startsWith("`$$") && g3.endsWith("$$`")) {
                    val inner = g3.removePrefix("`$$").removeSuffix("$$`")
                    out.append("$$").append(sanitizeLatex(inner).replace(Regex("""\n\s*\n"""), "\n")).append("$$")
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
                out.append("$$").append(sanitizeLatex(inner).replace(Regex("""\n\s*\n"""), "\n")).append("$$")
            }
            g5 != null -> {
                // Display \[...\] -> convert to display math $$...$$ with newline boundaries
                val inner = g5.substring(2, g5.length - 2)
                out.append("\n\n$$").append(sanitizeLatex(inner).replace(Regex("""\n\s*\n"""), "\n")).append("$$\n\n")
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

    if (lastIdx < mergedRaw.length) {
        val plainText = mergedRaw.substring(lastIdx)
        out.append(wrapBareLatexInPlainText(plainText))
    }

    return out.toString()
}

private fun wrapBareLatexInPlainText(plainText: String): String {
    if (plainText.isEmpty()) return plainText

    if (!KNOWN_LATEX_COMMAND_REGEX.containsMatchIn(plainText)) return plainText

    val proseWords = setOf(
        "and", "or", "if", "then", "is", "are", "was", "were", "where", "when", "which", "what", "how",
        "the", "a", "an", "this", "that", "these", "those", "for", "with", "from", "to", "in", "on", "at",
        "by", "of", "equal", "equals", "yields", "gives", "so", "such", "solution", "curve",
        "option", "options", "question", "statement", "value", "values", "constant", "constants",
        "rewriting", "solving", "testing", "lies", "integrating", "differentiating", "find", "calculate",
        "select", "word", "given", "sentence", "antonym", "synonym", "meaning", "appropriate", "most"
    )

    val allowedMultiLetterMathWords = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
        "log", "ln", "lg", "exp", "lim", "sup", "inf", "max", "min", "det", "dim", "ker", "deg", "gcd", "lcm", "mod", "arg",
        "dx", "dy", "dz", "dt", "du", "dv", "dr", "ds", "rhs", "lhs"
    )

    val nestedBraceGroup = """\{(?:[^{}]|\{(?:[^{}]|\{[^{}]*\})*\})*\}"""
    val tokenPattern = Regex(
        """(<\/?[a-zA-Z][a-zA-Z0-9]*(?:\s+[^<>]*)?>)""" +
        """|(\\(?:$KNOWN_LATEX_COMMAND_NAMES)(?![a-zA-Z])(?:\s*$nestedBraceGroup|\s*\[[^\[\]]*\]|\s*_$nestedBraceGroup|\s*_\w+|\s*\^$nestedBraceGroup|\s*\^\w+)*|\\[%&#_{}])""" +
        """|([a-zA-Z]+)""" +
        """|([0-9]+(?:\.[0-9]+)?)""" +
        """|(_$nestedBraceGroup|_\w+|\^$nestedBraceGroup|\^\w+)""" +
        """|([\+\-\*\/=><,;\.!|\(\)\[\]~\:\?%])""" +
        """|(\$)""" +
        """|(\s+)""" +
        """|(\\[a-zA-Z]+|[^\s\\a-zA-Z0-9_\^\+\-\*\/=><,;\.!|\(\)\[\]~\:\?%\$]+)"""
    )

    val matches = tokenPattern.findAll(plainText).toList()
    if (matches.isEmpty()) return plainText

    val result = StringBuilder(plainText.length + 32)
    val matchStrings = matches.map { it.value }
    val matchTypes = matches.map { m ->
        when {
            m.groups[1] != null -> 9 // HTML tag
            m.groups[2] != null -> 1 // Known LaTeX command or escaped symbol
            m.groups[3] != null -> 2 // ASCII Word
            m.groups[4] != null -> 3 // Number
            m.groups[5] != null -> 4 // Sub/Super
            m.groups[6] != null -> 5 // Operator / Punctuation
            m.groups[7] != null -> 6 // Dollar $
            m.groups[8] != null -> 7 // Whitespace
            else -> 8                // Non-ASCII prose or unknown \word
        }
    }

    fun isAllowedMathWord(word: String): Boolean {
        val lower = word.lowercase()
        if (lower in proseWords) return false
        if (lower in allowedMultiLetterMathWords) return true
        return word.length <= 3
    }

    var i = 0
    val n = matches.size

    while (i < n) {
        val t = matchTypes[i]
        val s = matchStrings[i]

        if (t == 1 || (t == 6 && i + 1 < n && matchTypes[i + 1] == 1)) {
            val hasOpeningDollar = (t == 6)
            if (hasOpeningDollar) {
                i++
            }

            var leadStart = result.length
            val prevText = result.toString()

            val leadMatch = Regex("""(?:[a-zA-Z0-9\(\)]+\s*=\s*|\([0-9\.\s\\%\+\-\*\/]+)$""").find(prevText)
            if (leadMatch != null) {
                val leadStr = leadMatch.value
                val leadProse = leadStr.trim().split(Regex("""\s+""")).any { it.lowercase() in proseWords }
                if (!leadProse && !leadStr.contains('\n')) {
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

                if (nxtType == 7 && nxtStr.contains('\n')) {
                    break
                }

                if (nxtType == 8 || nxtType == 9) {
                    break
                }

                if (nxtType == 2 && !isAllowedMathWord(nxtStr)) {
                    break
                }

                if (nxtType == 6) {
                    i++
                    break
                }

                if (nxtType == 5 && nxtStr == ".") {
                    if (i + 1 == n ||
                        matchTypes[i + 1] == 8 ||
                        matchTypes[i + 1] == 9 ||
                        (matchTypes[i + 1] == 7 && matchStrings[i + 1].contains('\n')) ||
                        (matchTypes[i + 1] == 2 && !isAllowedMathWord(matchStrings[i + 1]))
                    ) {
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

    return result.toString()
}

fun formatRichTextLineBreaks(raw: String): String {
    if (raw.isBlank()) return raw

    // 1. Repair any corrupted $\n...$ or $...<strong>...$ spans and unescape non-LaTeX \n / \r\n / \t BEFORE math preprocessing
    val repaired = repairCorruptedMathAndEscapes(raw)

    // 2. Restore corrupted control-character escapes from JSON (\f -> \frac, \b -> \beta, etc.)
    val restored = restoreCorruptedLatexEscapes(repaired)

    // 3. Preprocess math spans
    val text = preprocessMathSpans(restored)

    // 4. Protect ALL mathematical expressions and code blocks
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

    // 4. Ensure Markdown block boundaries are preceded by an empty line
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
    private val spannableCache = android.util.LruCache<Triple<String, Int, Int>, SpannableString>(600)

    fun clearCache() {
        formattedTextCache.evictAll()
        spannableCache.evictAll()
    }

    fun getFormattedText(raw: String): String {
        if (raw.isBlank()) return raw
        val cached = formattedTextCache.get(raw)
        if (cached != null) return cached
        val formatted = formatRichTextLineBreaks(raw)
        formattedTextCache.put(raw, formatted)
        return formatted
    }

    /**
     * Caches the clean base SpannableString produced by Markwon and ALWAYS returns a fresh
     * SpannableString copy so caller-added spans (such as search BackgroundColorSpans or
     * image ClickableSpans) never mutate or pollute the cached template.
     */
    fun getSpannable(
        markwon: Markwon,
        processedText: String,
        markwonHash: Int,
        textColorArgb: Int
    ): Spannable {
        val key = Triple(processedText, markwonHash, textColorArgb)
        val cached = spannableCache.get(key)
        if (cached != null) {
            return SpannableString(cached)
        }

        val markdownNode = markwon.toMarkdown(processedText)
        val cleanBase = SpannableString(markdownNode)
        spannableCache.put(key, cleanBase)
        return SpannableString(cleanBase)
    }
}

private fun hasClickableSpanAt(textView: TextView, event: android.view.MotionEvent): Boolean {
    val text = textView.text as? android.text.Spanned ?: return false
    val layout = textView.layout ?: return false
    val x = (event.x.toInt() - textView.totalPaddingLeft + textView.scrollX)
        .coerceIn(0, layout.width.coerceAtLeast(0))
    val y = (event.y.toInt() - textView.totalPaddingTop + textView.scrollY)
        .coerceIn(0, layout.height.coerceAtLeast(0))
    val line = layout.getLineForVertical(y)
    val offset = layout.getOffsetForHorizontal(line, x.toFloat())
    val spans = text.getSpans(offset, offset, android.text.style.ClickableSpan::class.java)
    return spans != null && spans.isNotEmpty()
}

private class RichTextTouchBridge {
    var onClick: (() -> Unit)? = null
    var onClickWithCoords: ((Float, Float) -> Unit)? = null
    var isSelectable: Boolean = true
    var isHorizontallyScrollable: Boolean = false
    var hadSelectionOnDown: Boolean = false
}

/**
 * Self-contained TextView for RichText that owns its own [touchBridge] and [selectionCallback].
 * This guarantees that when Jetpack Compose recycles the AndroidView across LazyColumn items
 * via [onReset] + [update], touch callbacks are ALWAYS bound to the current visible item and
 * never invoke stale closures from previously recycled items.
 */
private class RichTextView(context: android.content.Context) : TextView(context) {
    val touchBridge = RichTextTouchBridge()
    var onSelectionMenuStateChanged: ((SelectionMenuUiState?) -> Unit)? = null
    val selectionCallback = CustomSelectionActionModeCallback().also { cb ->
        cb.textView = this
        cb.onSelectionStateUpdated = { notifySelectionState() }
        cb.onSelectionDestroyed = { onSelectionMenuStateChanged?.invoke(null) }
    }
    var parsedSpannableTag: Any? = null
    var boundSearchQuery: String = ""

    private val scrollListener = android.view.ViewTreeObserver.OnScrollChangedListener {
        if (hasSelection()) {
            notifySelectionState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
    }

    override fun onDetachedFromWindow() {
        try {
            viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        } catch (_: Exception) {}
        onSelectionMenuStateChanged?.invoke(null)
        super.onDetachedFromWindow()
    }

    fun getSelectedTextSafe(): String {
        val txt = text ?: return ""
        val selStart = selectionStart
        val selEnd = selectionEnd
        if (selStart in 0..txt.length && selEnd in 0..txt.length && selStart != selEnd) {
            val s = minOf(selStart, selEnd)
            val e = maxOf(selStart, selEnd)
            return txt.subSequence(s, e).toString()
        }
        return ""
    }

    fun computeSelectionBoundsInView(): android.graphics.Rect? {
        if (!hasSelection()) return null
        val tvLayout = layout ?: return null
        val txt = text ?: return null
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, txt.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, txt.length)
        if (start == end) return null

        val startLine = tvLayout.getLineForOffset(start)
        val endLine = tvLayout.getLineForOffset(end)
        val top = tvLayout.getLineTop(startLine) + totalPaddingTop - scrollY
        val bottom = tvLayout.getLineBottom(endLine) + totalPaddingTop - scrollY

        val left: Int
        val right: Int
        if (startLine == endLine) {
            val x1 = tvLayout.getPrimaryHorizontal(start).toInt()
            val x2 = tvLayout.getPrimaryHorizontal(end).toInt()
            left = minOf(x1, x2) + totalPaddingLeft - scrollX
            right = maxOf(x1, x2) + totalPaddingLeft - scrollX
        } else {
            left = totalPaddingLeft
            right = (width - totalPaddingRight).coerceAtLeast(left + 1)
        }
        return android.graphics.Rect(left, top, right.coerceAtLeast(left + 1), bottom.coerceAtLeast(top + 1))
    }

    fun notifySelectionState() {
        if (!touchBridge.isSelectable || !hasSelection()) {
            onSelectionMenuStateChanged?.invoke(null)
            return
        }
        val selected = getSelectedTextSafe()
        val bounds = computeSelectionBoundsInView()
        if (selected.isNotEmpty() && bounds != null) {
            onSelectionMenuStateChanged?.invoke(SelectionMenuUiState(selected, bounds))
        } else {
            onSelectionMenuStateChanged?.invoke(null)
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
            post { notifySelectionState() }
        } else {
            onSelectionMenuStateChanged?.invoke(null)
        }
    }

    private val tapDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                // 1. If the user tapped a ClickableSpan (e.g. zoomable inline image), let the span handle it
                if (hasClickableSpanAt(this@RichTextView, e)) {
                    return false
                }
                // 2. PDF/EPUB Reader behavior: if text is currently selected, a single tap clears
                // the selection without toggling full-screen bars
                if (touchBridge.isSelectable && (touchBridge.hadSelectionOnDown || hasSelection())) {
                    selectionCallback.finishSelection()
                    return true
                }
                // 3. Otherwise, fire the current composable's tap callbacks
                touchBridge.onClickWithCoords?.invoke(e.rawX, e.rawY)
                touchBridge.onClick?.invoke()
                return true
            }
        }
    )

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        // When RichText is non-selectable and non-horizontally-scrollable (e.g. clickable quiz options
        // in ReadBookScreen and QuizScreen, or collapsed cards in BookmarksScreen and SearchScreen),
        // do NOT consume touch events inside the AndroidView unless an inline ClickableSpan (image) was tapped.
        if (!touchBridge.isSelectable && !touchBridge.isHorizontallyScrollable) {
            if (hasClickableSpanAt(this, event)) {
                return super.dispatchTouchEvent(event)
            }
            return false
        }

        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            touchBridge.hadSelectionOnDown = hasSelection()
        }

        val hasTapCallbacks = touchBridge.onClick != null || touchBridge.onClickWithCoords != null
        if (hasTapCallbacks) {
            tapDetector.onTouchEvent(event)
        }

        val superHandled = super.dispatchTouchEvent(event)

        if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
            event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) {
            if (hasSelection()) {
                post { notifySelectionState() }
            }
        }

        return if (touchBridge.isHorizontallyScrollable) {
            superHandled || hasTapCallbacks
        } else {
            superHandled
        }
    }
}

private fun bindRichTextView(
    textView: RichTextView,
    textColorArgb: Int,
    selectionHighlightColorArgb: Int,
    fontSizeSp: Float,
    fontSizePx: Float,
    isSelectable: Boolean,
    isHorizontallyScrollable: Boolean,
    resolvedCustomItems: List<CustomTextMenuItem>,
    markwon: Markwon,
    spannableToDisplay: Spannable,
    searchQuery: String,
    onSelectionMenuStateChanged: (SelectionMenuUiState?) -> Unit,
    onClick: (() -> Unit)?,
    onClickWithCoords: ((Float, Float) -> Unit)?
) {
    textView.touchBridge.onClick = onClick
    textView.touchBridge.onClickWithCoords = onClickWithCoords
    textView.touchBridge.isSelectable = isSelectable
    textView.touchBridge.isHorizontallyScrollable = isHorizontallyScrollable
    textView.onSelectionMenuStateChanged = onSelectionMenuStateChanged

    textView.setTextColor(textColorArgb)
    textView.highlightColor = selectionHighlightColorArgb
    if (textView.textSize != fontSizePx) {
        textView.textSize = fontSizeSp
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        textView.setTextClassifier(android.view.textclassifier.TextClassifier.NO_OP)
    }

    textView.selectionCallback.textView = textView
    textView.selectionCallback.customItems = resolvedCustomItems
    textView.customSelectionActionModeCallback = textView.selectionCallback
    textView.customInsertionActionModeCallback = textView.selectionCallback

    if (textView.parsedSpannableTag !== spannableToDisplay || textView.boundSearchQuery != searchQuery) {
        markwon.setParsedMarkdown(textView, spannableToDisplay)
        textView.parsedSpannableTag = spannableToDisplay
        textView.boundSearchQuery = searchQuery
    }

    // Apply scrollability and selectability AFTER markwon.setParsedMarkdown so Markwon plugins
    // never leave a non-selectable view with an active MovementMethod or clickable state.
    if (isHorizontallyScrollable) {
        textView.setHorizontallyScrolling(true)
        if (textView.isTextSelectable) {
            textView.setTextIsSelectable(false)
        }
        textView.movementMethod = null
        textView.isClickable = false
        textView.isLongClickable = false
        textView.isFocusable = false
        textView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    } else {
        textView.setHorizontallyScrolling(false)
        if (isSelectable) {
            if (!textView.isTextSelectable) {
                textView.setTextIsSelectable(true)
            }
        } else {
            if (textView.isTextSelectable) {
                textView.setTextIsSelectable(false)
            }
            textView.movementMethod = null
            textView.isClickable = false
            textView.isLongClickable = false
            textView.isFocusable = false
            textView.isFocusableInTouchMode = false
        }
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
    val displayMetrics = context.resources.displayMetrics
    val fontSizePx = fontSize.value * displayMetrics.density
    val maxFormulaWidthPx = if (isHorizontallyScrollable) {
        Int.MAX_VALUE
    } else {
        (displayMetrics.widthPixels - (36f * displayMetrics.density)).toInt().coerceAtLeast(120)
    }

    val markwon = remember(fontSizePx, color, maxFormulaWidthPx) {
        MarkwonCache.get(context, fontSizePx, color, maxFormulaWidthPx)
    }

    val processedText = remember(text) {
        MathFormattingCache.getFormattedText(text)
    }

    val textColorArgb = if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK
    val themePrimaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val selectionHighlightColorArgb = remember(themePrimaryColor) {
        androidx.core.graphics.ColorUtils.setAlphaComponent(themePrimaryColor.toArgb(), 72)
    }

    var zoomableImageUrl by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var selectionMenuState by remember { androidx.compose.runtime.mutableStateOf<SelectionMenuUiState?>(null) }
    var richTextViewRef by remember { androidx.compose.runtime.mutableStateOf<RichTextView?>(null) }

    val spannableToDisplay = remember(processedText, markwon, searchQuery, highlightColor, textColorArgb) {
        val spannable = MathFormattingCache.getSpannable(markwon, processedText, markwon.hashCode(), textColorArgb)

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

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier,
            factory = { ctx ->
                RichTextView(ctx).apply {
                    includeFontPadding = true
                    richTextViewRef = this
                    bindRichTextView(
                        textView = this,
                        textColorArgb = textColorArgb,
                        selectionHighlightColorArgb = selectionHighlightColorArgb,
                        fontSizeSp = fontSize.value,
                        fontSizePx = fontSizePx,
                        isSelectable = isSelectable,
                        isHorizontallyScrollable = isHorizontallyScrollable,
                        resolvedCustomItems = resolvedCustomItems,
                        markwon = markwon,
                        spannableToDisplay = spannableToDisplay,
                        searchQuery = searchQuery,
                        onSelectionMenuStateChanged = { state -> selectionMenuState = state },
                        onClick = onClick,
                        onClickWithCoords = onClickWithCoords
                    )
                }
            },
            update = { textView ->
                if (textView is RichTextView) {
                    richTextViewRef = textView
                    bindRichTextView(
                        textView = textView,
                        textColorArgb = textColorArgb,
                        selectionHighlightColorArgb = selectionHighlightColorArgb,
                        fontSizeSp = fontSize.value,
                        fontSizePx = fontSizePx,
                        isSelectable = isSelectable,
                        isHorizontallyScrollable = isHorizontallyScrollable,
                        resolvedCustomItems = resolvedCustomItems,
                        markwon = markwon,
                        spannableToDisplay = spannableToDisplay,
                        searchQuery = searchQuery,
                        onSelectionMenuStateChanged = { state -> selectionMenuState = state },
                        onClick = onClick,
                        onClickWithCoords = onClickWithCoords
                    )
                }
            },
            onReset = { textView ->
                try {
                    selectionMenuState = null
                    if (textView is RichTextView) {
                        textView.onSelectionMenuStateChanged = null
                        textView.touchBridge.onClick = null
                        textView.touchBridge.onClickWithCoords = null
                        textView.touchBridge.hadSelectionOnDown = false
                        textView.parsedSpannableTag = null
                        textView.boundSearchQuery = ""
                        textView.selectionCallback.customItems = emptyList()
                    }
                    textView.customSelectionActionModeCallback = null
                    textView.customInsertionActionModeCallback = null
                    val spannable = textView.text as? android.text.Spannable
                    if (spannable != null) {
                        android.text.Selection.removeSelection(spannable)
                    }
                    textView.clearFocus()
                } catch (e: Exception) {}
            }
        )

        val currentMenuState = selectionMenuState
        if (isSelectable && currentMenuState != null) {
            RichTextSelectionMenuPopup(
                menuState = currentMenuState,
                customItems = resolvedCustomItems,
                onCopy = {
                    val selected = richTextViewRef?.getSelectedTextSafe().orEmpty().ifEmpty { currentMenuState.selectedText }
                    if (selected.isNotEmpty()) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Selected Text", selected))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    richTextViewRef?.selectionCallback?.finishSelection()
                    selectionMenuState = null
                },
                onSelectAll = {
                    val tv = richTextViewRef
                    val spannable = tv?.text as? android.text.Spannable
                    if (tv != null && spannable != null && spannable.isNotEmpty()) {
                        android.text.Selection.setSelection(spannable, 0, spannable.length)
                        tv.post { tv.notifySelectionState() }
                    }
                },
                onShare = {
                    val selected = richTextViewRef?.getSelectedTextSafe().orEmpty().ifEmpty { currentMenuState.selectedText }
                    richTextViewRef?.selectionCallback?.finishSelection()
                    selectionMenuState = null
                    if (selected.isNotEmpty()) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, selected)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share text"))
                    }
                },
                onCustomItemClick = { item ->
                    val selected = richTextViewRef?.getSelectedTextSafe().orEmpty().ifEmpty { currentMenuState.selectedText }
                    richTextViewRef?.selectionCallback?.finishSelection()
                    selectionMenuState = null
                    item.onClick(selected)
                }
            )
        }
    }

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
