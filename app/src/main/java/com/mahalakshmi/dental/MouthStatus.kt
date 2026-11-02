@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ───────────── Mouth status: what is already in the mouth ─────────────
// Surfaces: M = front (mesial), O = biting (occlusal / incisal edge), D = back (distal),
//           B = cheek side (buccal), L = tongue / palate side.

data class MouthTooth(
    val tooth: Int,
    val decay: String = "",
    val filled: String = "",
    val crown: Boolean = false,
    val rct: Boolean = false,
    val implant: Boolean = false,
    val missing: Boolean = false,
    val broken: Boolean = false,
    val note: String = "",
    val updatedAt: Long = 0L
) {
    val isHealthy: Boolean
        get() = decay.isEmpty() && filled.isEmpty() && !crown && !rct && !implant && !missing && !broken && note.isBlank()
}

private val MouthBlue = Color(0xFF3B82C4)
private val MouthPurple = Color(0xFF8E5CC4)

// ───────────── Saving ─────────────
private var mouthReady: Any? = null

private fun ClinicDb.ensureMouth() {
    val sql = writableDatabase
    if (mouthReady === sql) return
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS tooth_status (patient_id INTEGER NOT NULL, tooth INTEGER NOT NULL, " +
            "decay TEXT DEFAULT '', filled TEXT DEFAULT '', crown INTEGER DEFAULT 0, rct INTEGER DEFAULT 0, " +
            "implant INTEGER DEFAULT 0, missing INTEGER DEFAULT 0, broken INTEGER DEFAULT 0, note TEXT DEFAULT '', " +
            "updated_at INTEGER DEFAULT 0, PRIMARY KEY (patient_id, tooth))"
    )
    mouthReady = sql
}

fun ClinicDb.mouthStatusOf(patientId: Long): List<MouthTooth> {
    ensureMouth()
    val list = mutableListOf<MouthTooth>()
    readableDatabase.rawQuery(
        "SELECT tooth, decay, filled, crown, rct, implant, missing, broken, note, updated_at FROM tooth_status WHERE patient_id = ?",
        arrayOf(patientId.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(
                MouthTooth(
                    tooth = c.getInt(0), decay = c.getString(1) ?: "", filled = c.getString(2) ?: "",
                    crown = c.getInt(3) == 1, rct = c.getInt(4) == 1, implant = c.getInt(5) == 1,
                    missing = c.getInt(6) == 1, broken = c.getInt(7) == 1, note = c.getString(8) ?: "",
                    updatedAt = c.getLong(9)
                )
            )
        }
    }
    return list
}

fun ClinicDb.saveMouthTooth(patientId: Long, s: MouthTooth) {
    ensureMouth()
    if (s.isHealthy) {
        writableDatabase.delete("tooth_status", "patient_id = ? AND tooth = ?", arrayOf(patientId.toString(), s.tooth.toString()))
        return
    }
    val cv = ContentValues().apply {
        put("patient_id", patientId)
        put("tooth", s.tooth)
        put("decay", s.decay)
        put("filled", s.filled)
        put("crown", if (s.crown) 1 else 0)
        put("rct", if (s.rct) 1 else 0)
        put("implant", if (s.implant) 1 else 0)
        put("missing", if (s.missing) 1 else 0)
        put("broken", if (s.broken) 1 else 0)
        put("note", s.note)
        put("updated_at", s.updatedAt)
    }
    writableDatabase.insertWithOnConflict("tooth_status", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

// ───────────── Surfaces ─────────────
private enum class MouthSeg { TOP, RIGHT, BOTTOM, LEFT, CENTER }

private fun mouthUpper(n: Int) = n / 10 == 1 || n / 10 == 2
private fun mouthScreenLeft(n: Int) = n / 10 == 1 || n / 10 == 4
private fun mouthFront(n: Int) = n % 10 in 1..3

/** Where each surface sits on the drawing (patient's right is on the screen's left). */
private fun mouthSegOf(surface: Char, n: Int): MouthSeg = when (surface) {
    'O' -> MouthSeg.CENTER
    'B' -> if (mouthUpper(n)) MouthSeg.TOP else MouthSeg.BOTTOM
    'L' -> if (mouthUpper(n)) MouthSeg.BOTTOM else MouthSeg.TOP
    'M' -> if (mouthScreenLeft(n)) MouthSeg.RIGHT else MouthSeg.LEFT
    else -> if (mouthScreenLeft(n)) MouthSeg.LEFT else MouthSeg.RIGHT
}

private fun mouthSurfaceAt(seg: MouthSeg, n: Int): Char = "MODBL".first { mouthSegOf(it, n) == seg }

private fun mouthToggle(code: String, c: Char): String {
    val set = code.toMutableSet()
    if (!set.add(c)) set.remove(c)
    return "MODBL".filter { it in set }
}

/** Short code as dentists write it, e.g. MO or MOD (I instead of O for front teeth). */
fun mouthSurfaceCode(code: String, n: Int): String =
    "MODBL".filter { it in code }.map { if (it == 'O' && mouthFront(n)) 'I' else it }.joinToString("")

private fun mouthSurfaceWord(c: Char, n: Int): String = when (c) {
    'M' -> "front"
    'D' -> "back"
    'O' -> if (mouthFront(n)) "edge" else "biting"
    'B' -> "cheek"
    else -> if (mouthUpper(n)) "palate" else "tongue"
}

private fun mouthSurfaceWords(code: String, n: Int): String =
    "MODBL".filter { it in code }.map { mouthSurfaceWord(it, n) }.joinToString(" + ")

private fun mouthToothName(n: Int): String {
    val side = when (n / 10) {
        1 -> "Upper right"
        2 -> "Upper left"
        3 -> "Lower left"
        else -> "Lower right"
    }
    val kind = when (n % 10) {
        1 -> "central incisor"
        2 -> "lateral incisor"
        3 -> "canine"
        4 -> "first premolar"
        5 -> "second premolar"
        6 -> "first molar"
        7 -> "second molar"
        else -> "wisdom tooth"
    }
    return "$side · $kind"
}

/** The treatment to suggest for a tooth: name, and the name its price may be saved under. */
private fun mouthSuggestion(n: Int, s: MouthTooth?): Pair<String, String>? {
    if (s == null) return null
    return when {
        s.decay.isNotEmpty() -> "Filling (${mouthSurfaceCode(s.decay, n)})" to "Filling"
        s.broken && !s.crown -> "Crown" to "Crown"
        s.missing -> "Implant" to "Implant"
        else -> null
    }
}

// ───────────── Drawing ─────────────
private class MouthColors(
    val empty: Color,
    val line: Color,
    val decay: Color,
    val filled: Color,
    val crown: Color,
    val crownSoft: Color,
    val rct: Color,
    val gone: Color,
    val select: Color
)

@Composable
private fun mouthColors() = MouthColors(
    empty = Brand.Surface, line = Brand.Faint, decay = Brand.Red, filled = MouthBlue,
    crown = Brand.Gold, crownSoft = Brand.GoldSoft, rct = MouthPurple, gone = Brand.Sub, select = Brand.GreenDeep
)

private fun mouthSector(c: Offset, r1: Float, r2: Float, seg: MouthSeg): Path {
    val start = when (seg) {
        MouthSeg.TOP -> 225f
        MouthSeg.RIGHT -> 315f
        MouthSeg.BOTTOM -> 45f
        else -> 135f
    }
    return Path().apply {
        arcTo(Rect(c, r2), start, 90f, true)
        arcTo(Rect(c, r1), start + 90f, -90f, false)
        close()
    }
}

private fun DrawScope.drawMouthTooth(n: Int, s: MouthTooth?, col: MouthColors, c: Offset, r2: Float, thin: Float) {
    val r1 = r2 * 0.42f
    if (s != null && s.implant) {
        drawCircle(
            col.line, r2, c,
            style = Stroke(thin, pathEffect = PathEffect.dashPathEffect(floatArrayOf(thin * 3f, thin * 3f)))
        )
        val w = r2 * 0.42f
        val h = r2 * 1.15f
        drawRoundRect(col.gone, topLeft = Offset(c.x - w / 2f, c.y - h / 2f), size = Size(w, h), cornerRadius = CornerRadius(w * 0.25f))
        for (k in -1..1) {
            val y = c.y + k * h * 0.25f
            drawLine(col.empty, Offset(c.x - w / 2f, y), Offset(c.x + w / 2f, y), strokeWidth = thin)
        }
        return
    }
    if (s != null && s.missing) {
        drawCircle(col.line.copy(alpha = 0.5f), r2, c, style = Stroke(thin))
        val d = r2 * 0.75f
        drawLine(col.gone, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), strokeWidth = thin * 2f, cap = StrokeCap.Round)
        drawLine(col.gone, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), strokeWidth = thin * 2f, cap = StrokeCap.Round)
        return
    }
    MouthSeg.values().forEach { seg ->
        val surf = mouthSurfaceAt(seg, n)
        val fill = when {
            s == null -> col.empty
            surf in s.decay -> col.decay
            surf in s.filled -> col.filled
            s.crown -> col.crownSoft
            else -> col.empty
        }
        if (seg == MouthSeg.CENTER) drawCircle(fill, r1, c) else drawPath(mouthSector(c, r1, r2, seg), fill)
    }
    val crown = s?.crown == true
    drawCircle(if (crown) col.crown else col.line, r2, c, style = Stroke(if (crown) thin * 3f else thin))
    drawCircle(col.line, r1, c, style = Stroke(thin))
    listOf(45.0, 135.0, 225.0, 315.0).forEach { a ->
        val rad = Math.toRadians(a)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        drawLine(col.line, Offset(c.x + dx * r1, c.y + dy * r1), Offset(c.x + dx * r2, c.y + dy * r2), strokeWidth = thin)
    }
    if (s != null && s.broken) {
        val p = Path().apply {
            moveTo(c.x + r2 * 0.15f, c.y - r2 * 1.0f)
            lineTo(c.x - r2 * 0.1f, c.y - r2 * 0.45f)
            lineTo(c.x + r2 * 0.2f, c.y - r2 * 0.3f)
            lineTo(c.x - r2 * 0.05f, c.y + r2 * 0.25f)
        }
        drawPath(p, col.gone, style = Stroke(thin * 2f, cap = StrokeCap.Round))
    }
}

// ───────────── Card in the patient file ─────────────
@Composable
fun MouthStatusCard(db: ClinicDb, patientId: Long) {
    val version = DataBus.version
    val list = remember(version) { runCatching { db.mouthStatusOf(patientId) }.getOrElse { emptyList() } }
    var open by remember { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth(), onClick = { open = true }, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mouth status", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.Ink)
                Text(
                    if (list.isEmpty()) "Record decay, fillings, crowns and missing teeth"
                    else "Updated ${fmtDate(list.maxOf { it.updatedAt })}",
                    fontSize = 12.sp, color = Brand.Sub
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftButton(if (list.isEmpty()) "Start" else "Open", null, height = 40.dp) { open = true }
        }
        if (list.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MouthChips(list)
        }
    }

    if (open) MouthStatusScreen(db, patientId) { open = false }
}

@Composable
private fun MouthChips(list: List<MouthTooth>) {
    val decay = list.count { it.decay.isNotEmpty() }
    val filled = list.count { it.filled.isNotEmpty() }
    val crown = list.count { it.crown }
    val rct = list.count { it.rct }
    val implant = list.count { it.implant }
    val missing = list.count { it.missing }
    val broken = list.count { it.broken }
    if (decay + filled + crown + rct + implant + missing + broken == 0) {
        Text("No problems marked", fontSize = 12.sp, color = Brand.Sub)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (decay > 0) Pill("Decay $decay", Brand.RedSoft, Brand.Red)
        if (filled > 0) Pill("Filled $filled", MouthBlue.copy(alpha = 0.14f), MouthBlue)
        if (crown > 0) Pill("Crown $crown", Brand.GoldSoft, Brand.GoldDeep)
        if (rct > 0) Pill("Root canal $rct", MouthPurple.copy(alpha = 0.14f), MouthPurple)
        if (implant > 0) Pill("Implant $implant", Brand.Soft, Brand.Sub)
        if (missing > 0) Pill("Missing $missing", Brand.Soft, Brand.Sub)
        if (broken > 0) Pill("Broken $broken", Brand.Soft, Brand.Ink)
    }
}

// ───────────── The chart screen ─────────────
@Composable
private fun MouthStatusScreen(db: ClinicDb, patientId: Long, onClose: () -> Unit) {
    val notify = LocalNotify.current
    val version = DataBus.version
    val patient = remember { db.getPatient(patientId) }
    val treatments = remember(version) { db.treatmentsOf(patientId) }
    var map by remember { mutableStateOf(db.mouthStatusOf(patientId).associateBy { it.tooth }) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var markFilled by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    fun update(n: Int, s: MouthTooth) {
        val stamped = s.copy(tooth = n, updatedAt = System.currentTimeMillis())
        db.saveMouthTooth(patientId, stamped)
        map = if (stamped.isHealthy) map - n else map + (n to stamped)
        DataBus.changed()
    }

    fun flushNote() {
        val n = selected ?: return
        val cur = map[n] ?: MouthTooth(n)
        if (note.trim() != cur.note) update(n, cur.copy(note = note.trim()))
    }

    fun select(n: Int) {
        flushNote()
        selected = if (selected == n) null else n
        note = map[n]?.note.orEmpty()
        markFilled = false
    }

    fun close() {
        flushNote()
        onClose()
    }

    fun tapSurface(c: Char) {
        val n = selected ?: return
        val s = map[n] ?: MouthTooth(n)
        if (s.missing || s.implant) return
        update(n, if (markFilled) s.copy(filled = mouthToggle(s.filled, c)) else s.copy(decay = mouthToggle(s.decay, c)))
    }

    fun toggle(kind: String) {
        val n = selected ?: return
        val s = map[n] ?: MouthTooth(n)
        val next = when (kind) {
            "crown" -> s.copy(crown = !s.crown, missing = false, implant = false)
            "rct" -> s.copy(rct = !s.rct, missing = false, implant = false)
            "broken" -> s.copy(broken = !s.broken, missing = false, implant = false)
            "implant" -> if (s.implant) s.copy(implant = false) else MouthTooth(n, implant = true, note = s.note)
            "missing" -> if (s.missing) s.copy(missing = false) else MouthTooth(n, missing = true, note = s.note)
            else -> s
        }
        update(n, next)
    }

    fun addTreatment() {
        val n = selected ?: return
        val (name, key) = mouthSuggestion(n, map[n]) ?: return
        val fee = db.priceFor(name) ?: db.priceFor(key) ?: 0.0
        db.saveTreatment(Treatment(patientId = patientId, name = name, teeth = "$n", fee = fee, status = STATUS_PLANNED))
        DataBus.changed()
        notify("$name · tooth $n added to treatments ✓")
    }

    // Save the note a moment after typing stops
    LaunchedEffect(note) {
        val n = selected ?: return@LaunchedEffect
        delay(700)
        val cur = map[n] ?: MouthTooth(n)
        if (note.trim() != cur.note) update(n, cur.copy(note = note.trim()))
    }

    val updated = map.values.maxOfOrNull { it.updatedAt }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, { close() }, bg = Brand.Surface, tint = Brand.Ink, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mouth status", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Brand.Ink)
                    Text(
                        patient?.name.orEmpty() + if (updated != null) " · updated ${fmtDate(updated)}" else "",
                        fontSize = 12.sp, color = Brand.Sub, maxLines = 1
                    )
                }
            }
            Column(
                Modifier.widthIn(max = 1100.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (map.isNotEmpty()) MouthChips(map.values.toList())
                AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    MouthChart(map, selected) { select(it) }
                    Spacer(Modifier.height(8.dp))
                    MouthLegend()
                }
                val n = selected
                if (n == null) {
                    AppCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text("Tap a tooth to record what you see", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink)
                        Text(
                            "Mark decay and fillings surface by surface, or mark crowns, root canals, implants and missing teeth.",
                            fontSize = 13.sp, color = Brand.Sub
                        )
                    }
                } else {
                    val s = map[n]
                    val suggestion = mouthSuggestion(n, s)
                    val already = suggestion != null && treatments.any {
                        it.status != STATUS_DONE && it.name.equals(suggestion.first, ignoreCase = true) && "$n" in it.teethList
                    }
                    MouthToothPanel(
                        n = n, s = s, markFilled = markFilled, onMarkFilled = { markFilled = it },
                        onSurface = { tapSurface(it) }, onToggle = { toggle(it) },
                        note = note, onNote = { note = it },
                        suggestion = suggestion?.first, already = already,
                        onAdd = { addTreatment() },
                        onClear = {
                            note = ""
                            update(n, MouthTooth(n))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MouthChart(map: Map<Int, MouthTooth>, selected: Int?, onTooth: (Int) -> Unit) {
    val col = mouthColors()
    Column(Modifier.fillMaxWidth()) {
        Text("Upper teeth", fontSize = 11.sp, color = Brand.Sub, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            MouthRow(PERIO_UPPER, map, selected, col, onTooth)
            Text("Lower teeth", fontSize = 11.sp, color = Brand.Sub, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
            MouthRow(PERIO_LOWER, map, selected, col, onTooth)
        }
    }
}

@Composable
private fun MouthRow(teeth: List<Int>, map: Map<Int, MouthTooth>, selected: Int?, col: MouthColors, onTooth: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        teeth.forEachIndexed { i, n ->
            if (i == 8) Box(Modifier.padding(horizontal = 4.dp).width(1.5.dp).height(52.dp).background(Brand.Line))
            MouthCell(n, map[n], selected == n, col) { onTooth(n) }
        }
    }
}

@Composable
private fun MouthCell(n: Int, s: MouthTooth?, selected: Boolean, col: MouthColors, onClick: () -> Unit) {
    Column(
        Modifier.width(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(36.dp, 42.dp)) {
            val thin = 1.dp.toPx()
            val r2 = 13.dp.toPx()
            val c = Offset(size.width / 2f, 17.dp.toPx())
            if (selected) drawCircle(col.select, r2 + 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            drawMouthTooth(n, s, col, c, r2, thin)
            if (s != null && s.rct && !s.missing && !s.implant) {
                drawRoundRect(
                    col.rct,
                    topLeft = Offset(c.x - 1.5.dp.toPx(), c.y + r2 + 3.dp.toPx()),
                    size = Size(3.dp.toPx(), 6.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
        Text(
            "$n", fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Brand.GreenText else Brand.Sub
        )
    }
}

@Composable
private fun MouthLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MouthLegendDot(Brand.Red, "Decay")
        MouthLegendDot(MouthBlue, "Filled")
        MouthLegendDot(Brand.Gold, "Crown")
        MouthLegendDot(MouthPurple, "Root canal")
        Text("✕ Missing · ▯ Implant · ⚡ Broken", fontSize = 11.sp, color = Brand.Sub)
    }
}

@Composable
private fun MouthLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Brand.Sub)
    }
}

@Composable
private fun MouthToothPanel(
    n: Int,
    s: MouthTooth?,
    markFilled: Boolean,
    onMarkFilled: (Boolean) -> Unit,
    onSurface: (Char) -> Unit,
    onToggle: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    suggestion: String?,
    already: Boolean,
    onAdd: () -> Unit,
    onClear: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            MouthSurfacePicker(n, s, onSurface)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Tooth $n", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.Ink)
                Text(mouthToothName(n), fontSize = 12.sp, color = Brand.Sub)
                Spacer(Modifier.height(10.dp))
                Text("Tap surfaces to mark", fontSize = 11.sp, color = Brand.Sub)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Decay", !markFilled) { onMarkFilled(false) }
                    ChoiceChip("Filling", markFilled) { onMarkFilled(true) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val parts = buildList {
            if (s != null && s.decay.isNotEmpty()) add("Decay on ${mouthSurfaceWords(s.decay, n)} (${mouthSurfaceCode(s.decay, n)})")
            if (s != null && s.filled.isNotEmpty()) add("Filled on ${mouthSurfaceWords(s.filled, n)} (${mouthSurfaceCode(s.filled, n)})")
            if (s?.crown == true) add("Crown")
            if (s?.rct == true) add("Root canal done")
            if (s?.implant == true) add("Implant")
            if (s?.missing == true) add("Missing")
            if (s?.broken == true) add("Broken")
        }
        Text(
            if (parts.isEmpty()) "Healthy. Tap a surface to mark ${if (markFilled) "a filling" else "decay"}."
            else parts.joinToString(" · "),
            fontSize = 13.sp, color = if (parts.isEmpty()) Brand.Sub else Brand.Ink
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Crown", s?.crown == true) { onToggle("crown") }
            ChoiceChip("Root canal done", s?.rct == true) { onToggle("rct") }
            ChoiceChip("Implant", s?.implant == true) { onToggle("implant") }
            ChoiceChip("Missing", s?.missing == true) { onToggle("missing") }
            ChoiceChip("Broken", s?.broken == true) { onToggle("broken") }
        }
        Spacer(Modifier.height(12.dp))
        AppField(note, onNote, "Note (optional)", lines = 2)
        if (suggestion != null) {
            Spacer(Modifier.height(12.dp))
            if (already) {
                Pill("$suggestion is already in treatments", Brand.GreenSoft, Brand.GreenText)
            } else {
                GradientButton("Add treatment: $suggestion", Modifier.fillMaxWidth(), Icons.Filled.Add, height = 50.dp) { onAdd() }
            }
        }
        if (s != null) {
            Text(
                "Clear this tooth", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.Red,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClear).padding(6.dp)
            )
        }
    }
}

@Composable
private fun MouthSurfacePicker(n: Int, s: MouthTooth?, onSurface: (Char) -> Unit) {
    val col = mouthColors()
    val disabled = s != null && (s.missing || s.implant)
    Box(
        Modifier.size(140.dp).pointerInput(n, disabled) {
            if (disabled) return@pointerInput
            detectTapGestures { o ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r2 = minOf(size.width, size.height) / 2f * 0.94f
                val r1 = r2 * 0.42f
                val dx = o.x - cx
                val dy = o.y - cy
                val dist = sqrt(dx * dx + dy * dy)
                val seg = when {
                    dist <= r1 -> MouthSeg.CENTER
                    dist > r2 * 1.05f -> null
                    else -> {
                        val a = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0
                        when {
                            a >= 315.0 || a < 45.0 -> MouthSeg.RIGHT
                            a < 135.0 -> MouthSeg.BOTTOM
                            a < 225.0 -> MouthSeg.LEFT
                            else -> MouthSeg.TOP
                        }
                    }
                }
                seg?.let { onSurface(mouthSurfaceAt(it, n)) }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawMouthTooth(n, s, col, center, size.minDimension / 2f * 0.94f, 1.5.dp.toPx())
        }
        if (!disabled) {
            MouthSeg.values().forEach { seg ->
                val surf = mouthSurfaceAt(seg, n)
                val strong = s != null && (surf in s.decay || surf in s.filled)
                val (align, pad) = when (seg) {
                    MouthSeg.TOP -> Alignment.TopCenter to Modifier.padding(top = 20.dp)
                    MouthSeg.BOTTOM -> Alignment.BottomCenter to Modifier.padding(bottom = 20.dp)
                    MouthSeg.LEFT -> Alignment.CenterStart to Modifier.padding(start = 12.dp)
                    MouthSeg.RIGHT -> Alignment.CenterEnd to Modifier.padding(end = 12.dp)
                    MouthSeg.CENTER -> Alignment.Center to Modifier
                }
                Text(
                    mouthSurfaceWord(surf, n).replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    color = if (strong) Color.White else Brand.Sub,
                    modifier = Modifier.align(align).then(pad)
                )
            }
        }
    }
}
