package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

@Composable
fun rememberMatteNoiseBrush(): Brush {
    return remember {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(42)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val noise = random.nextInt(256)
            // Very subtle noise, 4% opacity (10 out of 255)
            pixels[i] = Color.argb(10, noise, noise, noise)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        
        ShaderBrush(
            ImageShader(
                bitmap.asImageBitmap(),
                TileMode.Repeated,
                TileMode.Repeated
            )
        )
    }
}

fun Modifier.matteEffect(isMatteMode: Boolean, brush: Brush): Modifier = this.then(
    if (isMatteMode) {
        Modifier.drawWithContent {
            drawContent()
            // 1. Apply the noise texture
            drawRect(
                brush = brush,
                blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
            )
            // 2. Apply a very subtle, transparent grey wash to soften colors & contrast
            drawRect(
                color = androidx.compose.ui.graphics.Color(0x08888888), // 3% gray
                blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
            )
        }
    } else Modifier
)
