package com.mahalakshmi.dental

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// ───────────── Dental chart with real tooth shapes ─────────────
private val upperArch = listOf(18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28)
private val lowerArch = listOf(48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38)

private enum class ToothKind { MOLAR, PREMOLAR, CANINE, LATERAL, CENTRAL }

private fun kindOf(n: Int): ToothKind = when (n % 10) {
    1 -> ToothKind.CENTRAL
    2 -> ToothKind.LATERAL
    3 -> ToothKind.CANINE
    4, 5 -> ToothKind.PREMOLAR
    else -> ToothKind.MOLAR
}

/** Size along the arch, relative to a molar. */
private fun widthOf(k: ToothKind): Float = when (k) {
    ToothKind.MOLAR -> 1.0f
    ToothKind.PREMOLAR -> 0.74f
    ToothKind.CANINE -> 0.72f
    ToothKind.LATERAL -> 0.62f
    ToothKind.CENTRAL -> 0.74f
}

/** Size across the arch (cheek to tongue), relative to a molar. */
private fun depthOf(k: ToothKind): Float = when (k) {
    ToothKind.MOLAR -> 1.0f
    ToothKind.PREMOLAR -> 0.88f
    ToothKind.CANINE -> 0.84f
    ToothKind.LATERAL -> 0.6f
    ToothKind.CENTRAL -> 0.64f
}

/** Colours for one tooth: light and deep enamel, outline, grooves and the number. */
class ToothLook(val light: Color, val deep: Color, val outline: Color, val groove: Color, val label: Color)

private val Enamel = ToothLook(Color(0xFFFFFFFF), Color(0xFFEEE6D3), Color(0xFFC9BA98), Color(0xFFDACDAF), Color(0xFF8A8F8C))
private val Picked = ToothLook(Color(0xFFE8F5D2), Color(0xFF9ED35C), Color(0xFF4A780E), Color(0xFF7DB23A), Color(0xFF5A8F12))

private fun statusLook(status: Int): ToothLook = when (status) {
    STATUS_PLANNED -> ToothLook(Color(0xFFFFEFEF), Color(0xFFF5B2B5), Color(0xFFE5484D), Color(0xFFE79AA0), Color(0xFFE5484D))
    STATUS_ONGOING -> ToothLook(Color(0xFFFFF7E2), Color(0xFFF3D07A), Color(0xFFD99A00), Color(0xFFE2B85A), Color(0xFFD99A00))
    else -> ToothLook(Color(0xFFF2F9E6), Color(0xFFC4E299), Color(0xFF5A8F12), Color(0xFF9CC46A), Color(0xFF5A8F12))
}

/** Patient file: teeth coloured by treatment status. Tap a tooth to work on it. */
@Composable
fun StatusChart(statusByTooth: Map<String, Int>, onTooth: (String) -> Unit) {
    TeethChart(look = { n -> statusByTooth[n]?.let { statusLook(it) } }, onTooth = onTooth)
}

/** Choosing teeth for a treatment: tap a tooth to select or unselect it. */
@Composable
fun ToothChart(selected: List<String>, onToggle: (String) -> Unit) {
    TeethChart(look = { n -> if (n in selected) Picked else null }, onTooth = onToggle)
}

@Composable
private fun TeethChart(look: (String) -> ToothLook?, onTooth: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val width = minOf(maxWidth, 460.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TeethArch(upperArch, true, width, look, onTooth)
            Row(Modifier.width(width).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Right", fontSize = 11.sp, color = Brand.Faint)
                Spacer(Modifier.weight(1f))
                Text("Upper  ·  Lower", fontSize = 11.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("Left", fontSize = 11.sp, color = Brand.Faint)
            }
            TeethArch(lowerArch, false, width, look, onTooth)
        }
    }
}

private class PlacedTooth(
    val number: Int,
    val kind: ToothKind,
    val x: Float,
    val y: Float,
    val w: Float,
    val d: Float,
    val nx: Float,
    val ny: Float
)

private const val ARCH_MARGIN = 20f

@Composable
private fun TeethArch(numbers: List<Int>, upper: Boolean, width: Dp, look: (String) -> ToothLook?, onTooth: (String) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTooth)
    val numberColor = Brand.Sub
    val regular = remember { runCatching { ResourcesCompat.getFont(context, R.font.poppins_medium) }.getOrNull() ?: Typeface.DEFAULT }
    val strong = remember { runCatching { ResourcesCompat.getFont(context, R.font.poppins_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD }
    val w = width.value
    val rx = w / 2f - ARCH_MARGIN
    val ry = rx * 0.86f
    val teeth = remember(w, upper) { placeTeeth(numbers, upper, w, rx, ry) }
    Canvas(
        Modifier.width(width).height((ry + ARCH_MARGIN * 2f).dp)
            .pointerInput(teeth) {
                detectTapGestures { pos ->
                    val k = density
                    val hit = teeth.minByOrNull { hypot(it.x * k - pos.x, it.y * k - pos.y) }
                    if (hit != null && hypot(hit.x * k - pos.x, hit.y * k - pos.y) <= (max(hit.w, hit.d) * 0.7f + 10f) * k) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tap(hit.number.toString())
                    }
                }
            }
    ) {
        val k = density
        teeth.forEach { t -> drawTooth(t, look(t.number.toString()), k) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 10.sp.toPx()
        }
        teeth.forEach { t ->
            val l = look(t.number.toString())
            val off = (t.d / 2f + 9f) * k
            paint.typeface = if (l != null) strong else regular
            paint.color = (l?.label ?: numberColor).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                t.number.toString(), t.x * k - t.nx * off, t.y * k - t.ny * off + paint.textSize * 0.36f, paint
            )
        }
    }
}

/** Spaces the teeth along the arch by their real width, and works out which way each one faces. */
private fun placeTeeth(numbers: List<Int>, upper: Boolean, w: Float, rx: Float, ry: Float): List<PlacedTooth> {
    val steps = 720
    val angle = FloatArray(steps + 1)
    val length = FloatArray(steps + 1)
    var px = -rx
    var py = 0f
    for (s in 0..steps) {
        val a = (PI * (1.0 - s.toDouble() / steps)).toFloat()
        val x = rx * cos(a)
        val y = ry * sin(a)
        angle[s] = a
        if (s > 0) length[s] = length[s - 1] + hypot(x - px, y - py)
        px = x
        py = y
    }
    val kinds = numbers.map { kindOf(it) }
    val unit = length[steps] / kinds.sumOf { widthOf(it).toDouble() }.toFloat()
    val cx = w / 2f
    val cy = if (upper) ARCH_MARGIN + ry else ARCH_MARGIN
    var used = 0f
    return numbers.mapIndexed { i, n ->
        val kind = kinds[i]
        val target = (used + widthOf(kind) / 2f) * unit
        used += widthOf(kind)
        var s = 0
        while (s < steps && length[s] < target) s++
        val a = angle[s]
        val x = cx + rx * cos(a)
        val y = if (upper) cy - ry * sin(a) else cy + ry * sin(a)
        var nx = cos(a) / rx
        var ny = (if (upper) -sin(a) else sin(a)) / ry
        val len = hypot(nx, ny)
        nx /= len
        ny /= len
        PlacedTooth(n, kind, x, y, widthOf(kind) * unit * 0.9f, depthOf(kind) * unit * 0.92f, nx, ny)
    }
}

private fun DrawScope.drawTooth(t: PlacedTooth, look: ToothLook?, k: Float) {
    val l = look ?: Enamel
    val w = t.w * k
    val d = t.d * k
    val path = toothPath(t.kind, w, d)
    val turn = (atan2(-t.nx, t.ny) * 180.0 / PI).toFloat()
    // soft shadow
    withTransform({
        translate(t.x * k, t.y * k + 1.5f * k)
        rotate(turn, Offset.Zero)
    }) { drawPath(path, Color(0x24000000)) }
    // the tooth
    withTransform({
        translate(t.x * k, t.y * k)
        rotate(turn, Offset.Zero)
    }) {
        drawPath(
            path,
            Brush.radialGradient(listOf(l.light, l.deep), center = Offset(-w * 0.15f, -d * 0.1f), radius = max(w, d) * 0.8f)
        )
        drawPath(path, l.outline, style = Stroke(width = (if (look != null) 1.7f else 1.1f) * k))
        drawGrooves(t.kind, w, d, l.groove, k)
    }
}

/** Tooth outline seen from above, centred on 0,0. The cheek side points down (+y) before rotating. */
private fun toothPath(kind: ToothKind, w: Float, d: Float): Path {
    val hw = w / 2f
    val hd = d / 2f
    return Path().apply {
        when (kind) {
            ToothKind.MOLAR -> {
                moveTo(0f, -hd)
                cubicTo(hw * 0.55f, -hd * 1.04f, hw * 1.02f, -hd * 0.62f, hw * 0.98f, -hd * 0.05f)
                cubicTo(hw * 1.02f, hd * 0.55f, hw * 0.6f, hd * 1.02f, 0f, hd * 0.98f)
                cubicTo(-hw * 0.6f, hd * 1.02f, -hw * 1.02f, hd * 0.55f, -hw * 0.98f, -hd * 0.05f)
                cubicTo(-hw * 1.02f, -hd * 0.62f, -hw * 0.55f, -hd * 1.04f, 0f, -hd)
                close()
            }
            ToothKind.PREMOLAR -> addOval(Rect(-hw, -hd, hw, hd))
            ToothKind.CANINE -> {
                moveTo(0f, hd)
                cubicTo(hw * 0.55f, hd * 0.85f, hw, hd * 0.2f, hw * 0.92f, -hd * 0.2f)
                cubicTo(hw * 0.8f, -hd * 0.8f, hw * 0.35f, -hd, 0f, -hd)
                cubicTo(-hw * 0.35f, -hd, -hw * 0.8f, -hd * 0.8f, -hw * 0.92f, -hd * 0.2f)
                cubicTo(-hw, hd * 0.2f, -hw * 0.55f, hd * 0.85f, 0f, hd)
                close()
            }
            else -> {
                moveTo(-hw, hd * 0.35f)
                cubicTo(-hw * 0.95f, hd * 1.05f, hw * 0.95f, hd * 1.05f, hw, hd * 0.35f)
                cubicTo(hw * 1.02f, -hd * 0.25f, hw * 0.45f, -hd, 0f, -hd)
                cubicTo(-hw * 0.45f, -hd, -hw * 1.02f, -hd * 0.25f, -hw, hd * 0.35f)
                close()
            }
        }
    }
}

/** The fine grooves that make each tooth type recognisable. */
private fun DrawScope.drawGrooves(kind: ToothKind, w: Float, d: Float, color: Color, k: Float) {
    val hw = w / 2f
    val hd = d / 2f
    val line = Stroke(width = 1.1f * k, cap = StrokeCap.Round)
    when (kind) {
        ToothKind.MOLAR -> {
            val g = Path().apply {
                moveTo(-hw * 0.55f, -hd * 0.05f)
                cubicTo(-hw * 0.2f, hd * 0.12f, hw * 0.2f, -hd * 0.12f, hw * 0.55f, hd * 0.05f)
                moveTo(-hw * 0.12f, -hd * 0.55f)
                lineTo(-hw * 0.05f, hd * 0.02f)
                moveTo(hw * 0.1f, -hd * 0.02f)
                lineTo(hw * 0.14f, hd * 0.55f)
            }
            drawPath(g, color, style = line)
        }
        ToothKind.PREMOLAR -> {
            val g = Path().apply {
                moveTo(-hw * 0.5f, 0f)
                cubicTo(-hw * 0.2f, hd * 0.12f, hw * 0.2f, hd * 0.12f, hw * 0.5f, 0f)
            }
            drawPath(g, color, style = line)
        }
        ToothKind.CANINE -> drawLine(color, Offset(0f, -hd * 0.35f), Offset(0f, hd * 0.6f), strokeWidth = 1.1f * k, cap = StrokeCap.Round)
        else -> drawLine(color, Offset(-hw * 0.62f, hd * 0.42f), Offset(hw * 0.62f, hd * 0.42f), strokeWidth = 1.1f * k, cap = StrokeCap.Round)
    }
}
