@file:OptIn(ExperimentalMaterial3Api::class)

package com.mahalakshmi.dental

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

// ───────────── Light / dark mode ─────────────
/** Light, dark or the same as the phone. Saved on this device; every screen changes at once. */
object AppTheme {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val AUTO = "auto"

    var mode by mutableStateOf(LIGHT)
        private set
    var systemDark by mutableStateOf(false)

    val dark: Boolean get() = mode == DARK || (mode == AUTO && systemDark)

    private fun prefs(c: Context) = c.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(c: Context) {
        mode = prefs(c).getString("mode", LIGHT) ?: LIGHT
        systemDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun set(c: Context, newMode: String) {
        mode = newMode
        prefs(c).edit().putString("mode", newMode).apply()
    }
}

// ───────────── Brand colours: graphite, cloud white and sunshine (Heritage look) ─────────────
object Brand {
    private val d: Boolean get() = AppTheme.dark

    // The three notes
    /** Top note: the rich colour of hero cards, the start screen and dark mode.
     *  To try another one, change only this line: Midnight 0xFF0E1626, Espresso 0xFF1F1914, Forest 0xFF0E1C18. */
    val Rich = Color(0xFF1C1D20)
    /** Middle note: soft cloud white. */
    val Cloud = Color(0xFFF2F4F1)
    /** Base note: sunshine yellow. Only for things you act on. */
    val Sun = Color(0xFFFFC928)
    /** Text and icons on yellow. */
    val OnSun = Color(0xFF1C1D20)

    /** Selected chips and the drawer: graphite on light pages, cloud white on dark pages. */
    val Accent: Color get() = if (d) Cloud else Rich
    val OnAccent: Color get() = if (d) Rich else Cloud
    /** Typing cursor and the border of the field you are typing in. */
    val Focus: Color get() = if (d) Sun else Rich

    // Logo green: good news only (paid, done, growth)
    val Green = Color(0xFF86BC14)
    val GreenDeep = Color(0xFF5E9410)
    val GreenText: Color get() = if (d) Color(0xFFA3D447) else Color(0xFF3F6A06)
    val GreenSoft: Color get() = if (d) Color(0xFF25311A) else Color(0xFFEEF6DD)
    val GreenLine: Color get() = if (d) Color(0xFF34452A) else Color(0xFFD9EBB8)

    val Charcoal: Color get() = if (d) Cloud else Rich
    val Slate: Color get() = if (d) Color(0xFFB7BEBB) else Color(0xFF555B59)

    // Sunshine family (these names used to mean gold)
    val Gold = Sun
    val GoldDeep: Color get() = if (d) Sun else Color(0xFF8A6400)
    val GoldSoft: Color get() = if (d) Color(0xFF3A3218) else Color(0xFFFFF4CF)

    // Pages, cards and text
    val Bg: Color get() = if (d) Rich else Cloud
    val Surface: Color get() = if (d) Color(0xFF27282C) else Color(0xFFFCFDFB)
    val Ink: Color get() = if (d) Cloud else Rich
    val Sub: Color get() = if (d) Color(0xFFA4A8AB) else Color(0xFF6B6F72)
    val Faint: Color get() = if (d) Color(0xFF6F7377) else Color(0xFFA0A4A7)
    val Line: Color get() = if (d) Color(0xFF36383D) else Color(0xFFE3E6E1)
    val Soft: Color get() = if (d) Color(0xFF2E3034) else Color(0xFFE9ECE7)

    // Money owed and danger
    val Red = Color(0xFFD9483A)
    val RedSoft: Color get() = if (d) Color(0xFF3A2220) else Color(0xFFFBE8E5)
    val Glass = Color(0x29FFFFFF)

    /** Dark bars (bottom bar, messages): dark in both modes. */
    val Night: Color get() = if (d) Color(0xFF2A2B2F) else Rich

    /** The lit edge of a glass panel: bright at the top, fading towards the bottom. */
    val GlassEdge: Brush
        get() = if (d) Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x0FFFFFFF)))
        else Brush.verticalGradient(listOf(Color.White, Color(0x241C1D20)))
}

/** Logo green: money received, progress bars and the avatar ring. */
val BrandGradient: Brush = Brush.linearGradient(listOf(Color(0xFF9BCB3C), Color(0xFF5E9410)))
/** The rich dark finish of hero cards. */
val CharcoalGradient: Brush = Brush.linearGradient(listOf(lerp(Brand.Rich, Color.White, 0.08f), Brand.Rich))

// ───────────── App-wide helpers ─────────────
/** Tells every screen that data changed, so lists and totals refresh. */
object DataBus {
    var version by mutableIntStateOf(0)
        private set

    fun changed() {
        version++
    }
}

/** Shows the small animated message at the top of the screen. */
val LocalNotify = staticCompositionLocalOf<(String) -> Unit> { {} }

// ───────────── Fonts & theme ─────────────
/** Jost: clean modern text for everything you read. */
val BodyFont = FontFamily(
    Font(R.font.jost_regular, FontWeight.Normal),
    Font(R.font.jost_medium, FontWeight.Medium),
    Font(R.font.jost_semibold, FontWeight.SemiBold),
    Font(R.font.jost_bold, FontWeight.Bold)
)

/** Cormorant Garamond: the elegant serif for names, money and titles. */
val DisplayFont = FontFamily(
    Font(R.font.cormorant_medium, FontWeight.Medium),
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold)
)

/** Old name kept so the other files keep working. It now means Jost. */
val Poppins = BodyFont

private fun TextStyle.body(): TextStyle = copy(fontFamily = BodyFont)
private fun TextStyle.display(): TextStyle = copy(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold)
private val baseType = Typography()
val AppTypography = Typography(
    displayLarge = baseType.displayLarge.display(),
    displayMedium = baseType.displayMedium.display(),
    displaySmall = baseType.displaySmall.display(),
    headlineLarge = baseType.headlineLarge.display(),
    headlineMedium = baseType.headlineMedium.display(),
    headlineSmall = baseType.headlineSmall.display(),
    titleLarge = baseType.titleLarge.body(),
    titleMedium = baseType.titleMedium.body(),
    titleSmall = baseType.titleSmall.body(),
    bodyLarge = baseType.bodyLarge.body(),
    bodyMedium = baseType.bodyMedium.body(),
    bodySmall = baseType.bodySmall.body(),
    labelLarge = baseType.labelLarge.body(),
    labelMedium = baseType.labelMedium.body(),
    labelSmall = baseType.labelSmall.body()
)

@Composable
fun ClinicTheme(content: @Composable () -> Unit) {
    val scheme = if (AppTheme.dark) {
        darkColorScheme(
            primary = Brand.Sun, onPrimary = Brand.OnSun,
            primaryContainer = Brand.GoldSoft, onPrimaryContainer = Brand.Sun,
            secondary = Brand.Cloud, onSecondary = Brand.Rich,
            secondaryContainer = Brand.Soft, onSecondaryContainer = Brand.Ink,
            tertiary = Brand.Green, onTertiary = Brand.Rich,
            background = Brand.Bg, onBackground = Brand.Ink,
            surface = Brand.Surface, onSurface = Brand.Ink,
            surfaceVariant = Brand.Soft, onSurfaceVariant = Brand.Sub,
            surfaceContainerLowest = Brand.Surface, surfaceContainerLow = Brand.Surface,
            surfaceContainer = Brand.Surface, surfaceContainerHigh = Brand.Surface,
            surfaceContainerHighest = Brand.Soft,
            outline = Color(0xFF45474D), outlineVariant = Brand.Line, error = Brand.Red
        )
    } else {
        lightColorScheme(
            primary = Brand.Rich, onPrimary = Brand.Cloud,
            primaryContainer = Brand.GoldSoft, onPrimaryContainer = Brand.Rich,
            secondary = Brand.Rich, onSecondary = Brand.Cloud,
            secondaryContainer = Brand.Soft, onSecondaryContainer = Brand.Ink,
            tertiary = Brand.GreenDeep, onTertiary = Color.White,
            background = Brand.Bg, onBackground = Brand.Ink,
            surface = Brand.Surface, onSurface = Brand.Ink,
            surfaceVariant = Brand.Soft, onSurfaceVariant = Brand.Sub,
            surfaceContainerLowest = Brand.Surface, surfaceContainerLow = Brand.Surface,
            surfaceContainer = Brand.Surface, surfaceContainerHigh = Brand.Surface,
            surfaceContainerHighest = Brand.Soft,
            outline = Color(0xFFD3D7D1), outlineVariant = Brand.Line, error = Brand.Red
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

/** dark = true gives dark icons (for light pages); false gives light icons (for dark pages). */
@Composable
fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = dark
        controller.isAppearanceLightNavigationBars = dark
    }
}

// ───────────── Icons ─────────────
private fun vectorIcon(name: String, viewport: Float, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = viewport, viewportHeight = viewport
    )
    for (p in paths) builder.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
    return builder.build()
}

object AppIcons {
    val Tooth: ImageVector by lazy {
        vectorIcon(
            "tooth", 32f,
            "M9,4C5.5,4 3.5,7 4.3,11C5,14.5 6.5,16 7,20.5C7.6,25 8.5,28 10.3,28C12,28 12.3,25 13,22.5C13.6,20.5 14.6,19.5 16,19.5C17.4,19.5 18.4,20.5 19,22.5C19.7,25 20,28 21.7,28C23.5,28 24.4,25 25,20.5C25.5,16 27,14.5 27.7,11C28.5,7 26.5,4 23,4C20,4 18.5,5.5 16,5.5C13.5,5.5 12,4 9,4Z"
        )
    }
    val Camera: ImageVector by lazy {
        vectorIcon(
            "camera", 24f,
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0",
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zm3,15c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
        )
    }
    val Gallery: ImageVector by lazy {
        vectorIcon(
            "gallery", 24f,
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
        )
    }
    val Cloud: ImageVector by lazy {
        vectorIcon(
            "cloud", 24f,
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z"
        )
    }
    val Chat: ImageVector by lazy {
        vectorIcon("chat", 24f, "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z")
    }
    val Sparkle: ImageVector by lazy {
        vectorIcon("sparkle", 24f, "M12,0C12.6,6.6 17.4,11.4 24,12C17.4,12.6 12.6,17.4 12,24C11.4,17.4 6.6,12.6 0,12C6.6,11.4 11.4,6.6 12,0Z")
    }
}

object ThemeIcons {
    val Moon: ImageVector by lazy {
        vectorIcon(
            "moon", 24f,
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3L12,3z"
        )
    }
    val Sun: ImageVector by lazy {
        vectorIcon(
            "sun", 24f,
            "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5S14.76,7 12,7L12,7zM2,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1S1.45,13 2,13zM20,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1S19.45,13 20,13zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1S11,1.45 11,2zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1C11.45,19 11,19.45 11,20zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0c0.39,-0.39 0.39,-1.03 0,-1.41L18.36,16.95zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L19.42,5.99zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L7.05,18.36z"
        )
    }
}

// ───────────── Formatting ─────────────
private val inr = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 2 }
fun rs(x: Double): String = "₹" + inr.format(x)
fun numText(x: Double): String = if (x == 0.0) "" else if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()
fun fmtDate(t: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t))
fun fmtTime(t: Long): String = SimpleDateFormat("EEEE · hh:mm a", Locale.getDefault()).format(Date(t))
fun fmtDateTime(t: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(t))
fun epochDayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
fun fmtDayLong(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))

fun fmtDay(day: Long): String {
    val today = todayEpochDay()
    return when (day) {
        today -> "Today"
        today + 1 -> "Tomorrow"
        else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
}

fun relativeTime(t: Long): String {
    if (t <= 0L) return ""
    val days = todayEpochDay() - epochDayOf(t)
    return when {
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        days < 30L -> "${days / 7} wk ago"
        days < 365L -> "${days / 30} mo ago"
        else -> "${days / 365} yr ago"
    }
}

fun agoText(t: Long): String {
    if (t <= 0L) return "Never"
    val mins = (System.currentTimeMillis() - t) / 60000
    return when {
        mins < 1L -> "just now"
        mins < 60L -> "$mins min ago"
        mins < 24L * 60 -> "${mins / 60} hr ago"
        else -> "${mins / (24 * 60)} days ago"
    }
}

fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

// ───────────── Animations ─────────────
@Composable
fun animatedNumber(target: Double): Double {
    val anim = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        done = false
        anim.animateTo(target.toFloat(), tween(1000, easing = FastOutSlowInEasing))
        done = true
    }
    return if (done) target else anim.value.roundToLong().toDouble()
}

/** Gently fades and slides content in, one after another. */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 40f
    }
}

// ───────────── Light / dark controls ─────────────
/** Round button on the home page: moon switches to dark mode, sun switches back to light. */
@Composable
fun ThemeToggle(size: Dp = 46.dp) {
    val context = LocalContext.current
    val dark = AppTheme.dark
    val turn by animateFloatAsState(if (dark) 180f else 0f, tween(450), label = "themeTurn")
    Box(
        Modifier.size(size)
            .shadow(4.dp, CircleShape, ambientColor = Color(0x1A000000), spotColor = Color(0x22000000))
            .clip(CircleShape)
            .background(Brand.Surface)
            .glassEdge(CircleShape)
            .clickable { AppTheme.set(context, if (dark) AppTheme.LIGHT else AppTheme.DARK) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (dark) ThemeIcons.Sun else ThemeIcons.Moon,
            if (dark) "Switch to light mode" else "Switch to dark mode",
            tint = if (dark) Brand.Gold else Brand.Charcoal,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn }
        )
    }
}

/** Light / Dark / Auto, shown in the menu. Auto follows the phone's own setting. */
@Composable
fun AppearanceRow() {
    val context = LocalContext.current
    val labels = listOf("Light", "Dark", "Auto")
    val modes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.AUTO)
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (AppTheme.dark) ThemeIcons.Moon else ThemeIcons.Sun, null, tint = Brand.Sub, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text("Appearance", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.Ink)
        }
        Segmented(labels, labels[modes.indexOf(AppTheme.mode).coerceAtLeast(0)]) { label ->
            AppTheme.set(context, modes[labels.indexOf(label)])
        }
        Text(
            "Auto follows your phone's setting", fontSize = 11.sp, color = Brand.Faint,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
        )
    }
}

// ───────────── Glass helpers ─────────────
/** The lit edge of a glass panel: bright at the top, fading towards the bottom. */
fun Modifier.glassEdge(shape: Shape, width: Dp = 1.dp): Modifier = this.border(width, Brand.GlassEdge, shape)

/** The rich dark panel for the most important thing on a page, like the next patient. */
@Composable
fun RichCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .then(
                if (AppTheme.dark) Modifier
                else Modifier.shadow(14.dp, shape, ambientColor = Color(0x331C1D20), spotColor = Color(0x4D1C1D20))
            )
            .clip(shape)
            .background(if (AppTheme.dark) lerp(Brand.Rich, Color.White, 0.07f) else Brand.Rich)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x0AFFFFFF))), shape)
            .padding(padding),
        content = content
    )
}

// ───────────── Building blocks ─────────────
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "cardPress")
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (AppTheme.dark) Modifier
                else Modifier.shadow(5.dp, shape, ambientColor = Color(0x0F1C1D20), spotColor = Color(0x1A1C1D20))
            )
            .clip(shape)
            .background(Brand.Surface)
            .glassEdge(shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = LocalIndication.current, onClick = onClick
                ) else Modifier
            )
            .padding(padding),
        content = content
    )
}

@Composable
fun LogoTile(size: Dp = 52.dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size)
            .shadow(8.dp, shape, ambientColor = Color(0x1A000000), spotColor = Color(0x29000000))
            .clip(shape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.clinic_logo),
            contentDescription = "Mahalakshmi Dental Clinic",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }
        )
    }
}

@Composable
fun BrandRow(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoTile(if (compact) 46.dp else 54.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Mahalakshmi", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp,
                color = Brand.Ink, lineHeight = 25.sp
            )
            Text(
                "Dental clinic", fontWeight = FontWeight.Medium, fontSize = 11.sp,
                color = Brand.GoldDeep, letterSpacing = 1.5.sp, lineHeight = 14.sp
            )
        }
    }
}

private val avatarColors = listOf(
    Color(0xFFFFF1C7) to Color(0xFF7A5A00),
    Color(0xFFE8F2D6) to Color(0xFF3F6A06),
    Color(0xFFE6E8EC) to Color(0xFF2A2C31),
    Color(0xFFF3E6DA) to Color(0xFF7A4A24),
    Color(0xFFE3ECF2) to Color(0xFF2E4A5E)
)

@Composable
fun Avatar(name: String, size: Dp = 48.dp, ring: Boolean = false) {
    val colors = avatarColors[(name.trim().lowercase().hashCode() and 0x7fffffff) % avatarColors.size]
    val dark = AppTheme.dark
    val bg = if (dark) colors.second.copy(alpha = 0.35f) else colors.first
    val fg = if (dark) colors.first else colors.second
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
    Box(
        Modifier.size(size)
            .then(if (ring) Modifier.border(2.dp, Brand.Sun, CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = fg, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.34f).sp)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Sparkle, null, tint = Brand.Gold, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = Brand.Ink)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, bg: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(bg),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** The main button: sunshine yellow with graphite text. (Old name kept so the other files keep working.) */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "buttonPress")
    val off = if (AppTheme.dark) Color(0xFF3A3C41) else Color(0xFFDADDD7)
    val fg = if (enabled) Brand.OnSun else Brand.Sub
    Row(
        modifier.height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (enabled) Modifier.shadow(
                    10.dp, shape,
                    ambientColor = Brand.Sun.copy(alpha = 0.35f), spotColor = Brand.Sun.copy(alpha = 0.55f)
                ) else Modifier
            )
            .clip(shape)
            .background(if (enabled) Brand.Sun else off)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    bg: Color = Brand.Soft,
    fg: Color = Brand.Ink,
    height: Dp = 52.dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "softPress")
    Row(
        modifier.height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50)).background(bg)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    bg: Color = Brand.Surface,
    tint: Color = Brand.Ink,
    size: Dp = 46.dp
) {
    val plain = bg == Brand.Surface
    Box(
        Modifier.size(size)
            .then(
                if (plain) Modifier.shadow(
                    4.dp, CircleShape, ambientColor = Color(0x141C1D20), spotColor = Color(0x1F1C1D20)
                ) else Modifier
            )
            .clip(CircleShape).background(bg)
            .then(if (plain) Modifier.glassEdge(CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Floating main button: sunshine yellow with graphite text. */
@Composable
fun GradientFab(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "fabPress")
    Row(
        modifier.height(60.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(14.dp, shape, ambientColor = Brand.Sun.copy(alpha = 0.4f), spotColor = Brand.Sun.copy(alpha = 0.6f))
            .clip(shape).background(Brand.Sun)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Brand.OnSun)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Brand.OnSun, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedBg: Color = Brand.Accent,
    onClick: () -> Unit
) {
    val selectedFg = if (selectedBg == Brand.Accent) Brand.OnAccent else Color.White
    val bg by animateColorAsState(if (selected) selectedBg else Brand.Surface, label = "chipBg")
    val fg by animateColorAsState(if (selected) selectedFg else Brand.Ink, label = "chipFg")
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(bg)
            .border(1.dp, if (selected) selectedBg else Brand.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = selectedFg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Soft).padding(4.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) Brand.Surface else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).height(46.dp)
                    .then(if (isSelected) Modifier.shadow(2.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option, fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Brand.Ink else Brand.Sub
                )
            }
        }
    }
}

@Composable
fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    type: KeyboardType = KeyboardType.Text,
    lines: Int = 1,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = if (icon != null) { { Icon(icon, null, tint = Brand.Sub) } } else null,
        prefix = if (prefix != null) { { Text(prefix, color = Brand.Ink) } } else null,
        singleLine = lines == 1,
        minLines = lines,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = type),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Brand.Ink, unfocusedTextColor = Brand.Ink,
            focusedBorderColor = Brand.Focus, unfocusedBorderColor = Brand.Line,
            focusedLabelColor = Brand.Focus, cursorColor = Brand.Focus,
            focusedContainerColor = Brand.Surface,
            unfocusedContainerColor = Brand.Surface
        ),
        modifier = modifier
    )
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .clip(shape).background(Brand.Soft).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Brand.Faint)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search name or mobile", color = Brand.Faint, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, color = Brand.Ink),
                cursorBrush = SolidColor(Brand.Focus),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Brand.Surface).clickable { onChange("") },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, null, tint = Brand.Sub, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun AppTopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Brand.Bg).statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Brand.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 13.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(16.dp).background(Brand.Surface)
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun FormCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier, padding = 22.dp) {
        Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = Brand.Ink)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Brand.Sub)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(Brand.GoldSoft), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Tooth, null, tint = Brand.Charcoal, modifier = Modifier.size(52.dp))
            Icon(
                AppIcons.Sparkle, null, tint = Brand.Gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(16.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
            color = Brand.Ink, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(message, fontSize = 14.sp, color = Brand.Sub, textAlign = TextAlign.Center)
    }
}

@Composable
fun NavItem(label: String, icon: ImageVector, active: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Brand.GoldSoft else Color.Transparent, label = "navItem")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) Brand.Ink else Brand.Sub, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label, fontSize = 15.sp, modifier = Modifier.weight(1f),
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = Brand.Ink
        )
        if (dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Brand.Ink)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Brand.Sub)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Brand.OnSun, checkedTrackColor = Brand.Sun,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (AppTheme.dark) Color(0xFF45474D) else Color(0xFFD5D8D3),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun NotifyBanner(text: String) {
    val success = text.contains("✓")
    Row(
        Modifier.shadow(18.dp, RoundedCornerShape(50), ambientColor = Color(0x33000000), spotColor = Color(0x55000000))
            .clip(RoundedCornerShape(50)).background(Brand.Night)
            .padding(start = 10.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (success) Brand.Green else Brand.Sun),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (success) Icons.Filled.Check else AppIcons.Sparkle, null, tint = Brand.OnSun, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (danger) Brand.RedSoft else Brand.GoldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (danger) Icons.Filled.Delete else Icons.Filled.Refresh, null,
                    tint = if (danger) Brand.Red else Brand.Ink
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
            Spacer(Modifier.height(6.dp))
            Text(message, fontSize = 14.sp, color = Brand.Sub)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                if (danger) {
                    SoftButton(confirmText, null, Modifier.weight(1f), bg = Brand.Red, fg = Color.White, onClick = onConfirm)
                } else {
                    GradientButton(confirmText, Modifier.weight(1f), height = 52.dp, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
fun BusyDialog(text: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Brand.Surface).padding(horizontal = 26.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = Brand.Focus, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, fontSize = 15.sp, color = Brand.Ink)
        }
    }
}
