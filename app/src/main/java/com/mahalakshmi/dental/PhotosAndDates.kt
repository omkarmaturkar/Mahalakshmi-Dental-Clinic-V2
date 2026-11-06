@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

val treatmentOptions = listOf(
    "Checkup", "Cleaning", "Filling", "Root Canal", "Extraction",
    "Crown / Cap", "Braces", "Implant", "Denture", "X-Ray"
)

fun withDay(millis: Long, day: Long): Long {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return LocalDate.ofEpochDay(day).atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Composable
fun DatePick(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(initialDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                onDismiss()
            }) { Text("OK", color = Brand.GreenDeep, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Brand.Sub) } }
    ) {
        DatePicker(state = state)
    }
}

// ───────────── Photos ─────────────
const val MAX_PHOTO = 3000

fun cameraTempFile(context: Context): File =
    File(File(context.cacheDir, "camera").apply { mkdirs() }, "camera.jpg")

fun newPhotoFile(context: Context): File =
    File(photoDir(context), "IMG_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")

/** Saves a sharp, upright JPEG copy of the photo inside the app. */
fun importPhoto(context: Context, open: () -> InputStream?): String? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO) sample *= 2
        var bmp = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = try {
            open()?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        val scale = MAX_PHOTO.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0f) m.postRotate(rotation)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val file = newPhotoFile(context)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return file.absolutePath
    } catch (e: Throwable) {
        return null
    }
}

fun loadBitmap(path: String, maxSize: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: Throwable) {
    null
}

@Composable
fun PhotoImage(path: String, modifier: Modifier, maxSize: Int = 400, crop: Boolean = true) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { loadBitmap(path, maxSize)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) {
        Image(b, contentDescription = null, modifier = modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
    } else {
        Box(modifier.background(Brand.Soft))
    }
}

fun sharePhoto(context: Context, path: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share photo"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share this photo", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PhotoViewer(photos: List<String>, start: Int, onClose: () -> Unit) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(start.coerceIn(0, photos.size - 1)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            key(index) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                ) {
                    PhotoImage(
                        photos[index],
                        Modifier.fillMaxSize().graphicsLayer(
                            scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
                        ),
                        maxSize = 3000, crop = false
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.Filled.Close, onClose, bg = Color(0x33FFFFFF), tint = Color.White)
                Spacer(Modifier.weight(1f))
                Text("${index + 1} / ${photos.size}", color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Filled.Share, { sharePhoto(context, photos[index]) }, bg = Color(0x33FFFFFF), tint = Color.White)
            }
            if (photos.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircleIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        { index = (index - 1 + photos.size) % photos.size },
                        bg = Color(0x33FFFFFF), tint = Color.White, size = 54.dp
                    )
                    CircleIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        { index = (index + 1) % photos.size },
                        bg = Color(0x33FFFFFF), tint = Color.White, size = 54.dp
                    )
                }
            }
        }
    }
}
