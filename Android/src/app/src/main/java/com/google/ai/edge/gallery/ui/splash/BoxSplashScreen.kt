package com.google.ai.edge.gallery.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/**
 * Box splash screen with animated pastel colored squares that form the app icon.
 * Creates a playful opening animation matching the new launcher icon design.
 */
@Composable
fun BoxSplashAnimation(
    onAnimationComplete: () -> Unit
) {
    // Define pastel colors matching the launcher icon
    val colors = listOf(
        Color(0xFFB3E5FC), // Light blue
        Color(0xFFFF6B9D), // Pink  
        Color(0xFFFFE082), // Yellow
        Color(0xFF4CAF50), // Green
        Color(0xFF0D7377), // Teal
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF9800), // Orange
        Color(0xFFEF5350), // Red
        Color(0xFF1565C0), // Dark blue
    )
    
    // Animation state
    var animationState by remember { mutableStateOf(0) }
    
    // Handle animation progression
    LaunchedEffect(Unit) {
        delay(800) // Squares forming
        animationState = 1
        delay(1200) // Squares bouncing
        animationState = 2
        delay(500) // Squares settling
        animationState = 3
        delay(300) // Icon revealed
        onAnimationComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Animated squares
        colors.forEachIndexed { index, color ->
            val targetOffset = when (index) {
                0 -> Offset(-60f, -40f)
                1 -> Offset(-20f, -60f)
                2 -> Offset(20f, -40f)
                3 -> Offset(60f, -20f)
                4 -> Offset(40f, 20f)
                5 -> Offset(20f, 60f)
                6 -> Offset(-20f, 40f)
                7 -> Offset(-60f, 20f)
                8 -> Offset(-40f, 60f)
                else -> Offset(0f, 0f)
            }
            
            val animatedOffset by animateOffsetAsState(
                targetValue = if (animationState >= 1) targetOffset else Offset(0f, 0f),
                animationSpec = tween(800, easing = EaseOutBack),
                label = "square_offset_$index"
            )
            
            val bounce by animateFloatAsState(
                targetValue = when {
                    animationState == 2 -> 1f
                    animationState >= 3 -> 0f
                    else -> 0f
                },
                animationSpec = repeatable(
                    iterations = 3,
                    animation = tween(400, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "square_bounce_$index"
            )
            
            val alpha by animateFloatAsState(
                targetValue = when {
                    animationState == 0 -> 0f
                    animationState >= 1 && animationState < 4 -> 1f
                    else -> 0f
                },
                animationSpec = tween(300),
                label = "square_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffset.x.toInt(), animatedOffset.y.toInt()) }
                    .size(24.dp)
                    .background(color)
                    .alpha(alpha)
                    .graphicsLayer {
                        translationY = bounce * -30f
                    }
                    .clip(CircleShape)
            )
        }
    }
}
