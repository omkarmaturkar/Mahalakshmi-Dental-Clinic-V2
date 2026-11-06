package com.mahalakshmi.dental

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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

/** The start screen: the clinic's logo on a white tile over the rich graphite, with a warm glow. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    StatusBarIcons(dark = false)
    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    val glint = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textShift = remember { Animatable(40f) }
    val spacing = remember { Animatable(12f) }
    val tagAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(550)) }
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 160f)) }
        delay(600)
        launch { shine.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { textAlpha.animateTo(1f, tween(650)) }
        launch { textShift.animateTo(0f, tween(750, easing = FastOutSlowInEasing)) }
        launch { spacing.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        delay(500)
        launch {
            glint.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            glint.animateTo(0f, tween(600))
        }
        delay(250)
        launch { tagAlpha.animateTo(1f, tween(600)) }
        delay(1150)
        onFinished()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brand.Rich).drawBehind {
            val r = size.minDimension * 0.85f
            drawCircle(
                Brush.radialGradient(listOf(Brand.Sun.copy(alpha = 0.12f), Color.Transparent), center = center, radius = r),
                radius = r, center = center
            )
        },
        contentAlignment = Alignment.Center
    ) {
        val big = maxWidth >= 600.dp
        val tile = if (big) 220.dp else 164.dp
        val tileShape = RoundedCornerShape(tile * 0.28f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(tile)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
                    .shadow(24.dp, tileShape, ambientColor = Color(0x66000000), spotColor = Color(0x99000000))
                    .clip(tileShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(tile * 0.8f)) {
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
                                    listOf(Brand.Sun.copy(alpha = 0.6f * g), Color.Transparent),
                                    center = c, radius = glow
                                ),
                                radius = glow, center = c
                            )
                            rotate(degrees = 45f * g, pivot = c) {
                                drawPath(sparklePath(c, size.width * 0.075f * g), Brand.Sun.copy(alpha = g))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (big) 36.dp else 30.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textShift.value
                }
            ) {
                Text(
                    "Mahalakshmi", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold,
                    fontSize = if (big) 50.sp else 42.sp, color = Brand.Cloud, letterSpacing = spacing.value.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dental clinic · Gondia", fontWeight = FontWeight.Medium, fontSize = if (big) 15.sp else 12.sp,
                    color = Brand.Sun, letterSpacing = (2f + spacing.value * 0.4f).sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.graphicsLayer { alpha = tagAlpha.value }
                    .width(40.dp).height(1.dp).background(Brand.Sun.copy(alpha = 0.6f))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Caring for every smile", fontSize = 14.sp, color = Color(0xFFA4A8AB),
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
