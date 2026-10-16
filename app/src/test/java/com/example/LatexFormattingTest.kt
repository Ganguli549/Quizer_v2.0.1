package com.example

import com.example.data.DataParser
import com.example.ui.components.cleanExtractedMathText
import com.example.ui.components.formatRichTextLineBreaks
import com.example.ui.components.preprocessMathSpans
import com.example.ui.components.restoreCorruptedLatexEscapes
import com.example.ui.components.sanitizeLatex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.noties.jlatexmath.JLatexMathDrawable

@RunWith(RobolectricTestRunner::class)
class LatexFormattingTest {

    @org.junit.Before
    fun setUp() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val baseAssets = app.assets
        
        val wrapper = object : android.content.ContextWrapper(app) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getAssets(): android.content.res.AssetManager {
                return baseAssets
            }
        }
        
        ru.noties.jlatexmath.JLatexMathAndroid.init(wrapper)
    }

    /**
     * Requirement 5.A: Inline text and formulas
     * "If $y=y(x)$ is the solution and $y(0)=0$, find the value."
     * Expected: Inline equations remain in the sentence and are rendered using inline math ($...$).
     */
    @Test
    fun testCaseA_InlineTextAndFormulas() {
        val input = "If \$y=y(x)\$ is the solution and \$y(0)=0\$, find the value."
        val processed = formatRichTextLineBreaks(input)
        
        assertTrue("Inline math \$y=y(x)\$ should be preserved as inline math", processed.contains("\$y=y(x)\$"))
        assertTrue("Inline math \$y(0)=0\$ should be preserved as inline math", processed.contains("\$y(0)=0\$"))
        assertFalse("Inline math must not be converted to display \$\$ math", processed.contains("\$\$y=y(x)\$\$"))
        assertTrue("Surrounding sentence must remain intact", processed.startsWith("If \$y=y(x)\$ is the solution"))
    }

    /**
     * Requirement 5.B: Fractions, exponents, and roots
     * "$(1+e^{2x})\frac{dy}{dx}+2(1+y^2)e^x=0$"
     * Expected: All parts belong to one correctly rendered inline mathematical expression. No nested dollar delimiters.
     */
    @Test
    fun testCaseB_FractionsExponentsAndRoots() {
        val input = "\$(1+e^{2x})\\frac{dy}{dx}+2(1+y^2)e^x=0\$"
        val processed = formatRichTextLineBreaks(input)
        
        assertEquals("Should preserve entire expression in one pair of \$ delimiters without nesting", "\$(1+e^{2x})\\frac{dy}{dx}+2(1+y^2)e^x=0\$", processed)
        assertFalse("Must not have nested dollar delimiters", processed.contains("\$\$") || processed.contains("\$\$(1+e^{2x})\$\$"))
    }

    /**
     * Requirement 5.C: Inverse trigonometric functions
     * "$\tan^{-1}y+2\tan^{-1}e^x=\frac{\pi}{2}$"
     * Expected: Commands are parsed correctly and do not appear as literal command names.
     */
    @Test
    fun testCaseC_InverseTrigonometricFunctions() {
        // Also test with legacy over-escaped double backslashes
        val inputOverEscaped = "\$\\tan^{-1}y+2\\tan^{-1}e^x=\\frac{\\pi}{2}\$"
        val processed = formatRichTextLineBreaks(inputOverEscaped)
        
        assertEquals("\$\\tan^{-1}y+2\\tan^{-1}e^x=\\frac{\\pi}{2}\$", processed)
        assertFalse("Must not contain quadruple or double backslashes before command", processed.contains("\\\\tan"))
    }

    /**
     * Requirement 5.D: Inline delimiter normalization
     * "\(\frac{a}{b}\)"
     * Expected: Converted to supported inline math ($...$), not display math ($$...$$).
     */
    @Test
    fun testCaseD_InlineDelimiterNormalization() {
        val input = "Calculate \\(\\frac{a}{b}\\) for the given values."
        val processed = formatRichTextLineBreaks(input)
        
        assertTrue("Must convert \\( to \$ inline math", processed.contains("\$\\frac{a}{b}\$"))
        assertFalse("Must NOT convert \\( to \$\$ display math", processed.contains("\$\$\\frac{a}{b}\$\$"))
    }

    /**
     * Requirement 5.E: Display equation
     * "$$\frac{dy}{dx}=x^2$$"
     * Expected: Preserved as an independent display block.
     */
    @Test
    fun testCaseE_DisplayEquation() {
        val input = "\$\$\\frac{dy}{dx}=x^2\$\$"
        val processed = formatRichTextLineBreaks(input)
        
        assertEquals("Display equation must be preserved as display math", "\$\$\\frac{dy}{dx}=x^2\$\$", processed)
    }

    /**
     * Requirement 5.F: Escaped JSON content
     * Verify that valid JSON containing "\\frac" produces a Kotlin field containing one backslash before "frac".
     */
    @Test
    fun testCaseF_EscapedJsonContent() {
        // Valid JSON representation of \frac{1}{2} has "\\" in JSON source: "{\"question\":\"\\frac{1}{2}\"}"
        val rawJson = "{\"question\":\"\\frac{1}{2}\"}"
        val fixedJson = DataParser.autoFixJson(rawJson)
        val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(fixedJson)
        val questionString = (jsonElement as kotlinx.serialization.json.JsonObject)["question"]!!.toString()
        
        // In Kotlin memory, the string content must contain exactly one backslash before "frac"
        // (jsonElement.toString() adds JSON quotes, so the decoded content contains \frac)
        val decoded = kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(fixedJson)["question"]?.let {
            (it as kotlinx.serialization.json.JsonPrimitive).content
        }
        assertEquals("Decoded in-memory string must have exactly one backslash before frac", "\\frac{1}{2}", decoded)
    }

    /**
     * Requirement 5.G: Commands that resemble escape sequences
     * Verify that "\neq", "\nu", "\right", "\rightarrow", and "\text" are not modified by ordinary line-break normalization.
     */
    @Test
    fun testCaseG_CommandsResemblingEscapeSequences() {
        val input = "Check that \$x \\neq 0\$ and \$\\nu > 1\$ with \\left( \$a\$ \\right) and \$\\rightarrow\$ with \$\\text{abc}\$."
        val processed = formatRichTextLineBreaks(input)
        
        assertTrue("Must preserve \\neq", processed.contains("\\neq"))
        assertTrue("Must preserve \\nu", processed.contains("\\nu"))
        assertTrue("Must preserve \\right", processed.contains("\\right"))
        assertTrue("Must preserve \\rightarrow", processed.contains("\\rightarrow"))
        assertTrue("Must preserve \\text", processed.contains("\\text"))
    }

    /**
     * Requirement 5.H: Existing content
     * Verify bold text, lists, Markdown tables, images, HTML-supported formatting, and ordinary non-mathematical questions continue to work.
     */
    @Test
    fun testCaseH_ExistingContentPreservation() {
        val markdownText = """
            # Heading 1
            **Bold text** and *italic text*.
            - Item 1
            - Item 2
            
            | Header 1 | Header 2 |
            | --- | --- |
            | Cell 1 | Cell 2 |
            
            ![Alt image](https://example.com/image.png)
            <b>HTML bold</b> and <i>HTML italic</i>.
        """.trimIndent()
        
        val processed = formatRichTextLineBreaks(markdownText)
        assertTrue(processed.contains("**Bold text**"))
        assertTrue(processed.contains("- Item 1"))
        assertTrue(processed.contains("| Header 1 | Header 2 |"))
        assertTrue(processed.contains("![Alt image](https://example.com/image.png)"))
        assertTrue(processed.contains("<b>HTML bold</b>"))
    }

    /**
     * Requirement 5.I: Nested expressions
     * Verify existing math delimiters are never inserted again around commands already inside a formula.
     */
    @Test
    fun testCaseI_NoNestedDelimiters() {
        val input = "\$\\frac{1}{2} + \\alpha + \\sqrt{3} + \\sum_{i=1}^n i\$"
        val processed = formatRichTextLineBreaks(input)
        
        assertEquals("\$\\frac{1}{2} + \\alpha + \\sqrt{3} + \\sum_{i=1}^n i\$", processed)
        assertFalse("Must not have any nested dollar signs inside expression", processed.contains("\$\$"))
        assertFalse("Must not wrap internal commands with extra dollar signs", processed.contains("\$\\alpha\$") && processed.contains("\$\\frac"))
    }

    @Test
    fun testCorruptedEscapesRestoration() {
        val corrupted = "\u000crac{a}{b} + \u0008eta + \u0009imes + \u0009heta"
        val restored = restoreCorruptedLatexEscapes(corrupted)
        assertEquals("\\frac{a}{b} + \\beta + \\times + \\theta", restored)
    }

    @Test
    fun testSanitizeLatexRendersInJLatexMath() {
        val testCases = listOf(
            "\\frac{1}{2}",
            "x^2 + y^2 = z^2",
            "\\sqrt{x^2 + 1}",
            "\\alpha + \\beta + \\gamma + \\theta + \\pi",
            "\\int_{0}^{1} x \\, dx",
            "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}",
            "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1",
            "x \\in \\mathbb{R}",
            "\\mathbf{A} \\times \\mathbf{B}",
            "\\begin{aligned} x &= 1 \\\\ y &= 2 \\end{aligned}",
            "\\left( \\frac{a}{b} \\right)",
            "\\left( x + 1 \\right", // unmatched left
            "x + 1 \\right)", // unmatched right
            "\\dfrac{1}{2}",
            "\\cfrac{1}{2}",
            "30°",
            "x² + y³",
            "a × b ÷ c ± d ≤ e ≥ f ≠ g",
            "\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}"
        )

        for (case in testCases) {
            val sanitized = sanitizeLatex(case)
            try {
                val drawable = JLatexMathDrawable.builder(sanitized)
                    .textSize(40f)
                    .color(0xFF000000.toInt())
                    .build()
                assertNotNull("Should build drawable for: $case (sanitized: $sanitized)", drawable)
            } catch (e: Throwable) {
                throw AssertionError("Failed to parse: '$case' -> sanitized: '$sanitized'", e)
            }
        }
    }

    @Test
    fun testOverescapedLatexCommandsDoNotThrow() {
        // String containing over-escaped double backslashes before commands
        val overescaped = "\\\\tan{x} + \\\\frac{1}{2} + \\\\beta"
        val sanitized = sanitizeLatex(overescaped)
        assertEquals("\\tan{x} + \\frac{1}{2} + \\beta", sanitized)
    }

    @Test
    fun testToAndTopCommandsRemainDistinct() {
        val inputTo = "\$a \\to b\$"
        val processedTo = formatRichTextLineBreaks(inputTo)
        assertTrue("Must convert \\to to \\rightarrow", processedTo.contains("\\rightarrow"))

        val inputTop = "\$A^{\\top}\$"
        val processedTop = formatRichTextLineBreaks(inputTop)
        assertTrue("Must preserve \\top without corrupting to \\rightarrowp", processedTop.contains("\\top"))
        assertFalse("Must NOT corrupt \\top to \\rightarrowp", processedTop.contains("\\rightarrowp"))
    }

    @Test
    fun testPreprocessMathSpansIdempotency() {
        val input = "Calculate \$x = \\frac{a}{b}\$ and \\\n\n\$\$\\int_0^1 x dx = \\frac{1}{2}\$\$\n\n."
        val pass1 = preprocessMathSpans(input)
        val pass2 = preprocessMathSpans(pass1)
        assertEquals("Preprocessing an already normalized string must be idempotent", pass1, pass2)
    }

    @Test
    fun testCurrencyTextNotWrappedAsMath() {
        val input = "The price is \$100 million and \$50."
        val processed = preprocessMathSpans(input)
        assertFalse("Must not treat \$100 million as a math formula", processed.contains("\$100 million\$"))
    }

    @Test
    fun testBareLatexOptionsAssembledIntoSingleSpan() {
        val optionA = "\\int _{0}^{1} \\left(1 + \\sqrt{1 - y^2} \\right),dy"
        val optionBWithTrailingDollar = "\\int _{0}^{1} \\left( \\frac{y^2}{2} - \\sqrt{1 - y^2} + 1 \\right),dy\$"
        
        val processedA = formatRichTextLineBreaks(optionA)
        val processedB = formatRichTextLineBreaks(optionBWithTrailingDollar)

        assertEquals("\$\\int _{0}^{1} \\left(1 + \\sqrt{1 - y^2} \\right),dy\$", processedA)
        assertEquals("\$\\int _{0}^{1} \\left( \\frac{y^2}{2} - \\sqrt{1 - y^2} + 1 \\right),dy\$", processedB)

        // Verify JLatexMath renders the assembled formula without exceptions
        val sanitizedA = sanitizeLatex(processedA.removePrefix("\$").removeSuffix("\$"))
        val drawable = JLatexMathDrawable.builder(sanitizedA)
            .textSize(40f)
            .build()
        assertNotNull("JLatexMath should build drawable for option A", drawable)
    }

    @Test
    fun testAlignedEnvironmentColumnSpec() {
        val input = "\\begin{aligned} a &= b & c &= d \\\\ e &= f & g &= h \\end{aligned}"
        val sanitized = sanitizeLatex(input)
        assertTrue("Must preserve alignment column structure", sanitized.contains("\\begin{array}") && sanitized.contains("\\end{array}"))
    }

    @Test
    fun testCleanExtractedMathTextRemovesNullStrings() {
        assertEquals("", cleanExtractedMathText("null"))
        assertEquals("", cleanExtractedMathText("NULL"))
        assertEquals("", cleanExtractedMathText(" null "))
        assertEquals("", cleanExtractedMathText("undefined"))
        assertEquals("", cleanExtractedMathText("none"))
        assertEquals("", cleanExtractedMathText("   "))
        assertEquals("Valid Explanation", cleanExtractedMathText("Valid Explanation"))
    }
}
