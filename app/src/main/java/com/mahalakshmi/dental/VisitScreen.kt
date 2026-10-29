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

// ───────────── New / edit visit ─────────────
@Composable
fun VisitFormScreen(db: ClinicDb, visitId: Long, patientId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val existing = remember { if (visitId != 0L) db.getVisit(visitId) else null }
    val patientName = remember { db.getPatient(patientId)?.name ?: "" }

    var date by remember { mutableLongStateOf(existing?.date ?: System.currentTimeMillis()) }
    val treatments = remember { mutableStateListOf<String>().apply { addAll(existing?.treatments ?: emptyList()) } }
    var otherTreatment by remember { mutableStateOf("") }
    val teeth = remember { mutableStateListOf<String>().apply { addAll(existing?.teethList ?: emptyList()) } }
    var showChart by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(existing?.problem ?: "") }
    var fees by remember { mutableStateOf(numText(existing?.fees ?: 0.0)) }
    var paid by remember { mutableStateOf(numText(existing?.paid ?: 0.0)) }
    var payMode by remember { mutableStateOf(existing?.payMode?.ifBlank { "Cash" } ?: "Cash") }
    var nextVisit by remember { mutableLongStateOf(existing?.nextVisit ?: 0L) }
    val photos = remember { mutableStateListOf<String>().apply { addAll(existing?.photos ?: emptyList()) } }
    val newPhotos = remember { mutableListOf<String>() }
    val removedPhotos = remember { mutableListOf<String>() }
    var saved by remember { mutableStateOf(false) }
    var savingPhoto by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var pickDateFor by remember { mutableStateOf<String?>(null) }

    // If you go back without saving, photos taken on this screen are removed
    DisposableEffect(Unit) { onDispose { if (!saved) newPhotos.forEach { File(it).delete() } } }

    fun addPhotos(sources: List<() -> InputStream?>) {
        scope.launch {
            savingPhoto = true
            for (open in sources) {
                val path = withContext(Dispatchers.IO) { importPhoto(context, open) }
                if (path != null) {
                    photos.add(path)
                    newPhotos.add(path)
                }
            }
            savingPhoto = false
            cameraTempFile(context).delete()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = cameraTempFile(context)
        if (ok && temp.length() > 0) addPhotos(listOf({ temp.inputStream() })) else temp.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) addPhotos(uris.map { uri -> { context.contentResolver.openInputStream(uri) } })
    }

    fun openCamera() {
        try {
            val temp = cameraTempFile(context)
            temp.delete()
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temp))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }

    fun save() {
        val allTreatments = (treatments + listOf(otherTreatment.trim())).filter { it.isNotBlank() }.distinct()
        db.saveVisit(
            Visit(
                id = visitId, patientId = patientId, date = date,
                reason = allTreatments.joinToString(", "), problem = problem.trim(),
                fees = fees.toDoubleOrNull() ?: 0.0, paid = paid.toDoubleOrNull() ?: 0.0,
                photos = photos.toList(),
                teeth = teeth.sortedBy { it.toIntOrNull() ?: 0 }.joinToString(","),
                nextVisit = nextVisit, payMode = payMode
            )
        )
        removedPhotos.forEach { File(it).delete() }
        saved = true
        DataBus.changed()
        notify(if (existing == null) "Visit saved ✓" else "Changes saved ✓")
        onBack()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val dateCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), onClick = { pickDateFor = "visit" }, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.DateRange, Brand.GreenDeep, Brand.GreenSoft, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Visit date", fontSize = 12.sp, color = Brand.Sub)
                        Text(fmtDayLong(epochDayOf(date)), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink)
                    }
                    Icon(Icons.Filled.Edit, null, tint = Brand.GreenDeep, modifier = Modifier.size(18.dp))
                }
            }
        }
        val treatmentCard: @Composable () -> Unit = {
            FormCard("Treatment", "Tap all that apply") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (treatmentOptions + treatments.filter { it !in treatmentOptions }).forEach { t ->
                        ChoiceChip(t, t in treatments) {
                            if (t in treatments) treatments.remove(t) else treatments.add(t)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppField(otherTreatment, { otherTreatment = it }, "Other treatment", Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    CircleIconButton(
                        Icons.Filled.Add,
                        {
                            val t = otherTreatment.trim()
                            if (t.isNotEmpty() && t !in treatments) treatments.add(t)
                            otherTreatment = ""
                        },
                        bg = Brand.GreenSoft, tint = Brand.GreenDeep, size = 54.dp
                    )
                }
            }
        }
        val teethCard: @Composable () -> Unit = {
            FormCard("Teeth", if (teeth.isEmpty()) "Optional – tap the teeth being treated" else "${teeth.size} selected") {
                if (teeth.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        teeth.sortedBy { it.toIntOrNull() ?: 0 }.forEach { Pill("Tooth $it", Brand.GreenSoft, Brand.GreenText) }
                    }
                }
                if (twoColumn) {
                    ToothChart(teeth) { n -> if (n in teeth) teeth.remove(n) else teeth.add(n) }
                } else {
                    SoftButton(
                        if (showChart) "Hide dental chart" else "Open dental chart",
                        if (showChart) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        Modifier.fillMaxWidth()
                    ) { showChart = !showChart }
                    AnimatedVisibility(visible = showChart, enter = expandVertically(), exit = shrinkVertically()) {
                        ToothChart(teeth) { n -> if (n in teeth) teeth.remove(n) else teeth.add(n) }
                    }
                }
            }
        }
        val problemCard: @Composable () -> Unit = {
            FormCard("Problem / findings") {
                AppField(problem, { problem = it }, "Describe the problem, diagnosis, notes", lines = 4)
            }
        }
        val photosCard: @Composable () -> Unit = {
            FormCard("Photos", if (photos.isEmpty()) "Add clear photos of the problem" else "${photos.size} photo(s)") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tile = (maxWidth - 21.dp) / 3
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AddTile("Camera", AppIcons.Camera, tile) { openCamera() }
                        AddTile("Gallery", AppIcons.Gallery, tile) {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        photos.forEachIndexed { i, path ->
                            key(path) {
                                Box(Modifier.size(tile)) {
                                    PhotoImage(path, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).clickable { viewerIndex = i })
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                                            .background(Color(0x99000000))
                                            .clickable {
                                                photos.remove(path)
                                                removedPhotos.add(path)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val paymentCard: @Composable () -> Unit = {
            FormCard("Payment") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppField(
                        fees, { fees = it.filter { c -> c.isDigit() || c == '.' } }, "Total fees",
                        Modifier.weight(1f), type = KeyboardType.Decimal, prefix = "₹ "
                    )
                    AppField(
                        paid, { paid = it.filter { c -> c.isDigit() || c == '.' } }, "Paid",
                        Modifier.weight(1f), type = KeyboardType.Decimal, prefix = "₹ "
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, payMode == m) { payMode = m } }
                }
                val f = fees.toDoubleOrNull() ?: 0.0
                val p = paid.toDoubleOrNull() ?: 0.0
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (f - p > 0) Brand.RedSoft else Brand.GreenSoft).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Remaining", fontSize = 12.sp, color = Brand.Sub)
                        Text(
                            rs(maxOf(0.0, f - p)), fontWeight = FontWeight.Bold, fontSize = 24.sp,
                            color = if (f - p > 0) Brand.Red else Brand.GreenText
                        )
                    }
                    if (f > 0 && p < f) {
                        SoftButton("Mark fully paid", Icons.Filled.Check, bg = Brand.Surface, height = 44.dp) { paid = fees }
                    } else if (f > 0) {
                        Pill("Fully paid", Brand.Surface, Brand.GreenText, Icons.Filled.Check)
                    }
                }
            }
        }
        val nextVisitCard: @Composable () -> Unit = {
            FormCard("Next visit", if (nextVisit > 0L) fmtDayLong(nextVisit) else "Not scheduled") {
                val today = todayEpochDay()
                val quick = listOf(
                    "None" to 0L, "Tomorrow" to today + 1, "3 days" to today + 3,
                    "1 week" to today + 7, "2 weeks" to today + 14, "1 month" to today + 30
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { (label, day) -> ChoiceChip(label, nextVisit == day) { nextVisit = day } }
                    val custom = nextVisit > 0L && quick.none { it.second == nextVisit }
                    ChoiceChip(if (custom) fmtDay(nextVisit) else "Pick date", custom) { pickDateFor = "next" }
                }
            }
        }

        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar(if (existing == null) "New Visit" else "Edit Visit", patientName, onBack)
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        dateCard()
                        treatmentCard()
                        teethCard()
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        problemCard()
                        photosCard()
                        paymentCard()
                        nextVisitCard()
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    dateCard()
                    treatmentCard()
                    teethCard()
                    problemCard()
                    photosCard()
                    paymentCard()
                    nextVisitCard()
                }
            }
            BottomActionBar {
                if (existing != null) {
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 56.dp)
                }
                val label = if (existing == null) "Save Visit" else "Save Changes"
                if (twoColumn) {
                    Spacer(Modifier.weight(1f))
                    GradientButton(label, Modifier.width(340.dp), Icons.Filled.Check) { save() }
                } else {
                    GradientButton(label, Modifier.weight(1f), Icons.Filled.Check) { save() }
                }
            }
        }
    }

    viewerIndex?.let { index -> PhotoViewer(photos.toList(), index) { viewerIndex = null } }
    if (savingPhoto) BusyDialog("Saving photo…")
    pickDateFor?.let { which ->
        val initial = if (which == "next") {
            if (nextVisit > 0L) nextVisit else todayEpochDay() + 7
        } else {
            epochDayOf(date)
        }
        DatePick(initial, onDismiss = { pickDateFor = null }) { day ->
            if (which == "next") {
                nextVisit = day
            } else {
                date = withDay(date, day)
            }
        }
    }
    if (askDelete && existing != null) ConfirmDialog(
        "Delete this visit?",
        "This visit, its photos and its payments will be permanently deleted.",
        onConfirm = {
            askDelete = false
            db.deleteVisit(existing)
            DataBus.changed()
            notify("Visit deleted")
            onBack()
        },
        onDismiss = { askDelete = false }
    )
}

@Composable
private fun AddTile(label: String, icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier.size(size).clip(RoundedCornerShape(18.dp)).background(Brand.GreenSoft)
            .drawBehind {
                drawRoundRect(
                    color = Brand.Green.copy(alpha = 0.6f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                )
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Brand.GreenDeep, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Brand.GreenText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

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

// ───────────── Dental chart ─────────────
private val upperTeeth = listOf(18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28)
private val lowerTeeth = listOf(48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38)

@Composable
fun OldToothChart(selected: List<String>, onToggle: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val width = maxWidth
        val tooth = if (width < 320.dp) 24.dp else 28.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ToothArch(upperTeeth, upper = true, width = width, tooth = tooth, selected = selected, onToggle = onToggle)
            Row(Modifier.width(width).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Right", fontSize = 11.sp, color = Brand.Faint)
                Spacer(Modifier.weight(1f))
                Text("Upper  ·  Lower", fontSize = 11.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("Left", fontSize = 11.sp, color = Brand.Faint)
            }
            ToothArch(lowerTeeth, upper = false, width = width, tooth = tooth, selected = selected, onToggle = onToggle)
        }
    }
}

@Composable
private fun ToothArch(
    numbers: List<Int>,
    upper: Boolean,
    width: Dp,
    tooth: Dp,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val rx = (width.value - tooth.value) / 2f
    val ry = rx * 0.92f
    val angles = remember(rx, numbers.size) { archAngles(numbers.size, rx, ry) }
    val cx = width.value / 2f
    val cy = if (upper) ry + tooth.value / 2f else tooth.value / 2f
    Box(Modifier.width(width).height((ry + tooth.value).dp)) {
        numbers.forEachIndexed { i, n ->
            val a = angles[i]
            val x = cx + rx * cos(a) - tooth.value / 2f
            val y = (if (upper) cy - ry * sin(a) else cy + ry * sin(a)) - tooth.value / 2f
            val label = n.toString()
            val isSelected = label in selected
            Box(
                Modifier.offset(x = x.dp, y = y.dp).size(tooth)
                    .clip(CircleShape)
                    .background(if (isSelected) BrandGradient else SolidColor(Brand.Surface))
                    .border(1.5.dp, if (isSelected) Brand.GreenDeep else Brand.Line, CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(label)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else Brand.Sub)
            }
        }
    }
}

// Spreads the teeth evenly along an arch shape
private fun archAngles(count: Int, rx: Float, ry: Float): List<Float> {
    val steps = 400
    val lengths = FloatArray(steps + 1)
    var px = -rx
    var py = 0f
    for (k in 1..steps) {
        val t = (PI * (1.0 - k.toDouble() / steps)).toFloat()
        val x = rx * cos(t)
        val y = ry * sin(t)
        lengths[k] = lengths[k - 1] + hypot(x - px, y - py)
        px = x
        py = y
    }
    val total = lengths[steps]
    return (0 until count).map { i ->
        val target = total * i / (count - 1)
        var k = 0
        while (k < steps && lengths[k] < target) k++
        (PI * (1.0 - k.toDouble() / steps)).toFloat()
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
