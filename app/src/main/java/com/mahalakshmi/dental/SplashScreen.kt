package com.mahalakshmi.dental

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    StatusBarIcons(dark = true)
    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val glint = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textShift = remember { Animatable(40f) }
    val spacing = remember { Animatable(12f) }
    val tagAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(450)) }
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 160f)) }
        delay(250)
        launch { shine.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        launch { textAlpha.animateTo(1f, tween(450)) }
        launch { textShift.animateTo(0f, tween(550, easing = FastOutSlowInEasing)) }
        launch { spacing.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(300)
        launch {
            glint.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            glint.animateTo(0f, tween(400))
        }
        delay(150)
        launch { tagAlpha.animateTo(1f, tween(350)) }
        delay(400)
        onFinished()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).drawBehind {
            drawCircle(
                Brush.radialGradient(
                    listOf(Brand.Green.copy(alpha = 0.10f), Color.Transparent),
                    center = center, radius = size.minDimension * 0.75f
                ),
                radius = size.minDimension * 0.75f, center = center
            )
        },
        contentAlignment = Alignment.Center
    ) {
        val big = maxWidth >= 600.dp
        val logoSize = if (big) 250.dp else 180.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(logoSize).graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    alpha = logoAlpha.value
                }
            ) {
                Image(
                    painter = painterResource(R.drawable.clinic_logo),
                    contentDescription = "Mahalakshmi Dental Clinic",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        drawContent()
                        val p = shine.value
                        if (p > 0f && p < 1f) {
                            val x = -size.width * 0.6f + p * size.width * 2.1f
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                                    start = Offset(x, 0f), end = Offset(x + size.width * 0.45f, size.height)
                                )
                            )
                        }
                    }
                )
                Canvas(Modifier.fillMaxSize()) {
                    val g = glint.value
                    if (g > 0f) {
                        val c = Offset(size.width * 0.782f, size.height * 0.209f)
                        val glow = size.width * 0.17f * g
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Brand.Gold.copy(alpha = 0.6f * g), Color.Transparent),
                                center = c, radius = glow
                            ),
                            radius = glow, center = c
                        )
                        rotate(degrees = 45f * g, pivot = c) {
                            drawPath(sparklePath(c, size.width * 0.075f * g), Brand.Gold.copy(alpha = g))
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (big) 34.dp else 26.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textShift.value
                }
            ) {
                Text(
                    "Mahalakshmi", fontWeight = FontWeight.Bold, fontSize = if (big) 42.sp else 32.sp,
                    color = Color(0xFF2A302E), letterSpacing = spacing.value.sp
                )
                Text(
                    "DENTAL CLINIC", fontWeight = FontWeight.SemiBold, fontSize = if (big) 16.sp else 13.sp,
                    color = Brand.GreenDeep, letterSpacing = (4f + spacing.value * 0.6f).sp
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Caring for every smile", fontSize = 14.sp, color = Brand.Faint,
                modifier = Modifier.graphicsLayer { alpha = tagAlpha.value }
            )
        }
    }
}

private fun sparklePath(c: Offset, r: Float): Path = Path().apply {
    val k = r * 0.14f
    moveTo(c.x, c.y - r)
    cubicTo(c.x + k, c.y - k, c.x + k, c.y - k, c.x + r, c.y)
    cubicTo(c.x + k, c.y + k, c.x + k, c.y + k, c.x, c.y + r)
    cubicTo(c.x - k, c.y + k, c.x - k, c.y + k, c.x - r, c.y)
    cubicTo(c.x - k, c.y - k, c.x - k, c.y - k, c.x, c.y - r)
    close()
}
