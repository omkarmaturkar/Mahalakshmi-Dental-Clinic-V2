@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
private fun SheetLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
}

// ───────────── Coloured dental chart ─────────────
private val upperRow = listOf(18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28)
private val lowerRow = listOf(48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38)

/** Teeth coloured by their treatment status. Tap a tooth to work on it. */
@Composable
fun OldStatusChart(statusByTooth: Map<String, Int>, onTooth: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = minOf(maxWidth, 460.dp)
        val tooth = if (width < 320.dp) 24.dp else 28.dp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusArch(upperRow, true, width, tooth, statusByTooth, onTooth)
            Row(Modifier.width(width).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Right", fontSize = 11.sp, color = Brand.Faint)
                Spacer(Modifier.weight(1f))
                Text("Upper  ·  Lower", fontSize = 11.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("Left", fontSize = 11.sp, color = Brand.Faint)
            }
            StatusArch(lowerRow, false, width, tooth, statusByTooth, onTooth)
        }
    }
}

@Composable
private fun StatusArch(
    numbers: List<Int>,
    upper: Boolean,
    width: Dp,
    tooth: Dp,
    statusByTooth: Map<String, Int>,
    onTooth: (String) -> Unit
) {
    val rx = (width.value - tooth.value) / 2f
    val ry = rx * 0.85f
    val angles = remember(rx, numbers.size) { chartAngles(numbers.size, rx, ry) }
    val cx = width.value / 2f
    val cy = if (upper) ry + tooth.value / 2f else tooth.value / 2f
    Box(Modifier.width(width).height((ry + tooth.value).dp)) {
        numbers.forEachIndexed { i, n ->
            val a = angles[i]
            val x = cx + rx * cos(a) - tooth.value / 2f
            val y = (if (upper) cy - ry * sin(a) else cy + ry * sin(a)) - tooth.value / 2f
            val label = n.toString()
            val status = statusByTooth[label]
            Box(
                Modifier.offset(x = x.dp, y = y.dp).size(tooth).clip(CircleShape)
                    .background(if (status != null) statusSoft(status) else Brand.Surface)
                    .border(1.5.dp, if (status != null) statusColor(status) else Brand.Line, CircleShape)
                    .clickable { onTooth(label) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (status != null) statusText(status) else Brand.Sub
                )
            }
        }
    }
}

// Spreads the teeth evenly along an arch
private fun chartAngles(count: Int, rx: Float, ry: Float): List<Float> {
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

// ───────────── Add / edit treatment ─────────────
@Composable
fun TreatmentDialog(
    db: ClinicDb,
    patientId: Long,
    initial: Treatment?,
    presetTooth: String?,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val doctors = remember { db.doctors() }
    val mainId = remember(doctors) { (doctors.firstOrNull { it.main } ?: doctors.firstOrNull())?.id ?: 0L }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val teeth = remember { mutableStateListOf<String>().apply { addAll(initial?.teethList ?: listOfNotNull(presetTooth)) } }
    var fee by remember { mutableStateOf(numText(initial?.fee ?: 0.0)) }
    var autoFee by remember { mutableStateOf(initial == null) }
    var rememberFee by remember { mutableStateOf(initial == null) }
    var status by remember { mutableIntStateOf(initial?.status ?: STATUS_ONGOING) }
    var doctorId by remember { mutableLongStateOf(initial?.doctorId ?: mainId) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showChart by remember { mutableStateOf(teeth.isEmpty()) }
    var askDelete by remember { mutableStateOf(false) }

    fun save() {
        val feeValue = fee.toDoubleOrNull() ?: 0.0
        db.saveTreatment(
            Treatment(
                id = initial?.id ?: 0L, patientId = patientId, name = name.trim(),
                teeth = teeth.sortedBy { it.toIntOrNull() ?: 0 }.joinToString(","),
                fee = feeValue, status = status, doctorId = doctorId, notes = notes.trim(),
                createdAt = initial?.createdAt ?: System.currentTimeMillis(), doneAt = initial?.doneAt ?: 0L
            )
        )
        if (rememberFee) db.rememberPrice(name, feeValue)
        onSaved(if (initial == null) "Treatment added ✓" else "Treatment updated ✓")
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Brand.Surface)
                .verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial == null) "Add treatment" else "Edit treatment",
                fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink
            )
            SheetLabel("Treatment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (treatmentOptions + listOf(name).filter { it.isNotBlank() && it !in treatmentOptions }).forEach { option ->
                    ChoiceChip(option, name == option) {
                        name = if (name == option) "" else option
                        if (name.isNotBlank() && (autoFee || fee.isBlank())) {
                            db.priceFor(name)?.let {
                                fee = numText(it)
                                autoFee = true
                            }
                        }
                    }
                }
            }
            AppField(name, { name = it }, "Or type the treatment name", caps = KeyboardCapitalization.Words)

            SheetLabel(if (teeth.isEmpty()) "Teeth (optional)" else "Teeth")
            if (teeth.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    teeth.sortedBy { it.toIntOrNull() ?: 0 }.forEach { Pill("Tooth $it", Brand.GoldSoft, Brand.GoldDeep) }
                }
            }
            if (showChart) ToothChart(teeth) { n -> if (n in teeth) teeth.remove(n) else teeth.add(n) }
            SoftButton(
                when {
                    showChart -> "Hide dental chart"
                    teeth.isEmpty() -> "Choose teeth"
                    else -> "Change teeth"
                },
                null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink, height = 44.dp
            ) { showChart = !showChart }

            SheetLabel("Fee")
            AppField(
                fee, {
                    fee = it.filter { c -> c.isDigit() || c == '.' }
                    autoFee = false
                },
                "Fee", type = KeyboardType.Decimal, prefix = "₹ "
            )
            ToggleRow("Remember this fee", "Next time it fills in by itself", rememberFee) { rememberFee = it }

            SheetLabel("Status")
            Segmented(listOf("To do", "In progress", "Done"), statusLabel(status)) { label ->
                status = when (label) {
                    "To do" -> STATUS_PLANNED
                    "Done" -> STATUS_DONE
                    else -> STATUS_ONGOING
                }
            }
            Text(
                if (status == STATUS_PLANNED) "Planned only: it shows on the estimate, not on the bill yet."
                else "Counted in the patient's bill.",
                fontSize = 12.sp, color = Brand.Sub
            )

            if (doctors.isNotEmpty()) {
                SheetLabel("Doctor")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    doctors.forEach { d ->
                        ChoiceChip(if (d.visiting) "${d.name} · Visiting" else d.name, doctorId == d.id) {
                            doctorId = if (doctorId == d.id) 0L else d.id
                        }
                    }
                }
            }
            AppField(notes, { notes = it }, "Notes (optional)", lines = 2)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 52.dp)
                }
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank(), height = 52.dp) { save() }
            }
        }
    }

    if (askDelete && initial != null) {
        ConfirmDialog(
            "Remove ${initial.name}?",
            "This treatment will be removed from the patient file and from the bill.",
            confirmText = "Remove",
            onConfirm = {
                askDelete = false
                db.deleteTreatment(initial.id)
                onSaved("Treatment removed")
            },
            onDismiss = { askDelete = false }
        )
    }
}

// ───────────── Receive payment ─────────────
@Composable
fun ReceivePaymentDialog(balance: Double, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf(if (balance > 0) numText(balance) else "") }
    var mode by remember { mutableStateOf("Cash") }
    val value = amount.toDoubleOrNull() ?: 0.0
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Text("₹", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Receive payment", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Brand.Ink)
            Text(
                if (balance > 0) "Balance due ${rs(balance)}" else "No balance due – this will be kept as an advance",
                fontSize = 13.sp, color = Brand.Sub
            )
            Spacer(Modifier.height(18.dp))
            AppField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount received",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            if (balance > 0) {
                Spacer(Modifier.height(10.dp))
                ChoiceChip("Full balance ${rs(balance)}", amount == numText(balance)) { amount = numText(balance) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, mode == m) { mode = m } }
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = value > 0, height = 52.dp) {
                    onSave(value, mode)
                }
            }
        }
    }
}

// ───────────── Book the next visit ─────────────
@Composable
fun BookingDialog(
    initial: Booking?,
    defaultReason: String,
    onDismiss: () -> Unit,
    onCancelBooking: (() -> Unit)?,
    onSave: (Long, String, String) -> Unit
) {
    val today = todayEpochDay()
    var day by remember { mutableLongStateOf(initial?.day ?: (today + 7)) }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var reason by remember { mutableStateOf(initial?.reason?.ifBlank { defaultReason } ?: defaultReason) }
    var pickDate by remember { mutableStateOf(false) }
    val quick = listOf("Tomorrow" to today + 1, "3 days" to today + 3, "1 week" to today + 7, "2 weeks" to today + 14, "1 month" to today + 30)
    val times = listOf("10:00 AM", "11:00 AM", "12:00 PM", "5:00 PM", "6:00 PM", "7:00 PM")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Brand.Surface)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial == null) "Book next visit" else "Change next visit",
                fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink
            )
            Text(fmtDayLong(day), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Brand.GoldDeep)
            SheetLabel("When")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { (label, d) -> ChoiceChip(label, day == d) { day = d } }
                val custom = quick.none { it.second == day }
                ChoiceChip(if (custom) fmtDay(day) else "Pick date", custom) { pickDate = true }
            }
            SheetLabel("Time (optional)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEach { t -> ChoiceChip(t, time == t) { time = if (time == t) "" else t } }
            }
            AppField(time, { time = it }, "Or type a time, e.g. 4:30 PM")
            AppField(reason, { reason = it }, "Reason, e.g. RCT sitting 2")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onCancelBooking != null) {
                    CircleIconButton(Icons.Filled.Delete, onCancelBooking, bg = Brand.RedSoft, tint = Brand.Red, size = 52.dp)
                }
                SoftButton("Close", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Book", Modifier.weight(1f), Icons.Filled.DateRange, height = 52.dp) {
                    onSave(day, time.trim(), reason.trim())
                }
            }
        }
    }
    if (pickDate) DatePick(day, onDismiss = { pickDate = false }) { d -> day = d }
}

// ───────────── Note ─────────────
@Composable
fun NoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Text("Add note", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
            Text("For example: RCT sitting 2 – canals cleaned, temporary filling placed", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(14.dp))
            AppField(text, { text = it }, "Note", lines = 4)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = text.isNotBlank(), height = 52.dp) {
                    onSave(text.trim())
                }
            }
        }
    }
}

// ───────────── Photo type / delete ─────────────
@Composable
fun PhotoOptionsDialog(photo: CasePhoto, onDismiss: () -> Unit, onKind: (String) -> Unit, onDelete: () -> Unit) {
    var askDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(24.dp)
        ) {
            PhotoImage(photo.path, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(14.dp))
            Text("This photo is a…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photoKinds.forEach { k -> ChoiceChip(k, photo.kind == k) { onKind(k) } }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Delete", Icons.Filled.Delete, Modifier.weight(1f), bg = Brand.RedSoft, fg = Brand.Red) { askDelete = true }
                SoftButton("Close", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
            }
        }
    }
    if (askDelete) {
        ConfirmDialog(
            "Delete this photo?",
            "It will be removed from the patient file.",
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false }
        )
    }
}

// ───────────── Before & after ─────────────
@Composable
fun BeforeAfterDialog(before: CasePhoto, after: CasePhoto, clinicName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF111614)).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Filled.Close, onDismiss, bg = Color(0x33FFFFFF), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("Before & After", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                CircleIconButton(
                    Icons.Filled.Share,
                    {
                        scope.launch {
                            busy = true
                            val file = withContext(Dispatchers.IO) {
                                runCatching { beforeAfterImage(context, before.path, after.path, clinicName) }.getOrNull()
                            }
                            busy = false
                            if (file != null) shareImage(context, file)
                            else Toast.makeText(context, "Could not prepare the picture", Toast.LENGTH_SHORT).show()
                        }
                    },
                    bg = Color(0x33FFFFFF), tint = Color.White
                )
            }
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ComparePane("BEFORE", before, Brand.Red, Modifier.weight(1f))
                        ComparePane("AFTER", after, Brand.GreenDeep, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ComparePane("BEFORE", before, Brand.Red, Modifier.weight(1f))
                        ComparePane("AFTER", after, Brand.GreenDeep, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (busy) BusyDialog("Preparing picture…")
}

@Composable
private fun ComparePane(label: String, photo: CasePhoto, color: Color, modifier: Modifier) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
        PhotoImage(photo.path, Modifier.fillMaxSize(), maxSize = 1600, crop = false)
        Text(
            label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50))
                .background(color).padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Text(
            fmtDate(photo.date), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
    }
}

/** Puts the before and after photos side by side in one picture, with the clinic name. */
fun beforeAfterImage(context: Context, beforePath: String, afterPath: String, clinicName: String): File {
    val a = loadBitmap(beforePath, 1400) ?: error("Photo not found")
    val b = loadBitmap(afterPath, 1400) ?: error("Photo not found")
    val h = 1000
    val wa = (a.width * h.toFloat() / a.height).toInt()
    val wb = (b.width * h.toFloat() / b.height).toInt()
    val gap = 24
    val pad = 40
    val top = 110
    val bottom = 90
    val out = Bitmap.createBitmap(pad * 2 + wa + gap + wb, top + h + bottom, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(a, null, RectF(pad.toFloat(), top.toFloat(), (pad + wa).toFloat(), (top + h).toFloat()), photoPaint)
    canvas.drawBitmap(
        b, null,
        RectF((pad + wa + gap).toFloat(), top.toFloat(), (pad + wa + gap + wb).toFloat(), (top + h).toFloat()), photoPaint
    )
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A302E.toInt()
        textSize = 54f
        isFakeBoldText = true
    }
    canvas.drawText("BEFORE", pad.toFloat(), 80f, title)
    canvas.drawText("AFTER", (pad + wa + gap).toFloat(), 80f, title)
    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A8F12.toInt()
        textSize = 40f
        isFakeBoldText = true
    }
    canvas.drawText(clinicName, pad.toFloat(), (top + h + 62).toFloat(), brand)
    val file = File(File(context.cacheDir, "pdf").apply { mkdirs() }, "BeforeAfter_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return file
}

fun shareImage(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share before & after"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share the picture", Toast.LENGTH_SHORT).show()
    }
}
