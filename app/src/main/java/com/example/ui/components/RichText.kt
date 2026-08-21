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

class TextDrawable(private val text: String, private val textSize: Float, private val textColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@TextDrawable.textColor
        this.textSize = this@TextDrawable.textSize
    }

    override fun draw(canvas: Canvas) {
        canvas.drawText(text, 0f, bounds.height().toFloat() - paint.descent(), paint)
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
        return paint.measureText(text).toInt()
    }

    override fun getIntrinsicHeight(): Int {
        return (paint.descent() - paint.ascent()).toInt()
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
                .usePlugin(
                    JLatexMathPlugin.create(
                        fontSizePx,
                        object : JLatexMathPlugin.BuilderConfigure {
                            override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                                builder.inlinesEnabled(true)
                                builder.blocksLegacy(true)
                                builder.blocksEnabled(true)
                                builder.theme().textColor(textColorArgb)
                                builder.errorHandler { latex, error ->
                                    android.util.Log.e("JLatexMath", "Error parsing: $latex", error)
                                    TextDrawable(latex ?: "MathError", fontSizePx, textColorArgb).apply {
                                        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
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

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.LocalContentColor.current,
    fontSize: TextUnit = 16.sp,
    searchQuery: String = "",
    highlightColor: Color = Color.Yellow,
    isSelectable: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val fontSizePx = fontSize.value * context.resources.displayMetrics.density
    val markwon = remember(fontSizePx, color) {
        MarkwonCache.get(context, fontSizePx, color)
    }

        val processedText = text
        .replace(Regex("(?s)```[a-zA-Z]*\\s*\\$\\$([^`]*?)\\$\\$\\s*```")) { "$$${it.groupValues[1]}$$" }
        .replace(Regex("(?s)```[a-zA-Z]*\\s*\\$([^`]*?)\\$\\s*```")) { "$$${it.groupValues[1]}$$" }
        .replace(Regex("(?s)`\\$\\$([^`]*?)\\$\\$`")) { "$$${it.groupValues[1]}$$" }
        .replace(Regex("(?s)`\\$([^`]*?)\\$`")) { "$$${it.groupValues[1]}$$" }
        .replace("\\(", "$$")
        .replace("\\)", "$$")
        .replace("\\[", "$$")
        .replace("\\]", "$$")
        .replace(Regex("(?<!\\$)\\$([^$]+?)\\$(?!\\$)")) { "$$${it.groupValues[1]}$$" }

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
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(if (color != Color.Unspecified) color.toArgb() else android.graphics.Color.BLACK)
                textSize = fontSize.value
                setTextIsSelectable(isSelectable)
                markwon.setParsedMarkdown(this, spannableToDisplay)
                if (onClick != null) {
                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                            onClick()
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
            if (textView.tag != spannableToDisplay) {
                markwon.setParsedMarkdown(textView, spannableToDisplay)
                textView.tag = spannableToDisplay
            }
            if (onClick != null) {
                val gestureDetector = android.view.GestureDetector(textView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                        onClick()
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
