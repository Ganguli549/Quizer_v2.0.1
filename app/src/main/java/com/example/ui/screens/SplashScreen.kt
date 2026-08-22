package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun SplashScreen(appIcon: String = "ocean") {
    var startAnimation by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    
    val alpha = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "alpha"
    )
    
    val scale = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "scale"
    )

    // Smoothly animate to a high percentage to simulate loading progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        progress = 0.95f 
    }

    val (iconRes, iconColor, surfaceColor) = when (appIcon) {
        "ocean" -> Triple(R.drawable.ocean, Color(0xFF0277BD), Color(0xFFFBFBFB))
        "forest" -> Triple(R.drawable.forest, Color(0xFF2E7D32), Color(0xFFEDFDF1))
        "sunset" -> Triple(R.drawable.sunset, Color(0xFFD84315), Color(0xFFFEECED))
        "lavender" -> Triple(R.drawable.lavender, Color(0xFF6A1B9A), Color(0xFFF5EDFD))
        "dark" -> Triple(R.drawable.dark, Color(0xFF212121), Color(0xFF0B1026))
        "gold" -> Triple(R.drawable.gold, Color(0xFFFF8F00), Color(0xFFFEFCF8))
        "titanium" -> Triple(R.drawable.titanium, Color(0xFF607D8B), Color(0xFFF5F7FB))
        else -> Triple(R.drawable.ocean, Color(0xFF0277BD), Color(0xFFFBFBFB))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scale.value)
        ) {
            // Spacer removed, icon removed
            
            Text(
                text = "Quizer",
                style = MaterialTheme.typography.displayMedium,
                color = iconColor,
                fontWeight = FontWeight.Black
            )
            
        }

        // Place progress at the very bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .alpha(alpha.value)
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .width(220.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = iconColor,
                trackColor = iconColor.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Loading your library...",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
