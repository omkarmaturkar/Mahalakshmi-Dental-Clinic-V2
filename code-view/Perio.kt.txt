@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

// ───────────── Gum (perio) chart ─────────────
// Six spots per tooth: 0-2 on the cheek side, 3-5 on the palate/tongue side.
// Inside each group: 0 = back (distal), 1 = middle, 2 = front (mesial).

data class PerioTooth(
    val pd: List<Int> = List(6) { -1 },
    val bop: List<Boolean> = List(6) { false },
    val recCheek: Int = -1,
    val recTongue: Int = -1,
    val mobility: Int = 0,
    val furcation: Int = 0,
    val missing: Boolean = false
) {
    val hasData: Boolean
        get() = pd.any { it >= 0 } || bop.any { it } || recCheek >= 0 || recTongue >= 0 || mobility > 0 || furcation > 0
}

data class PerioExam(
    val id: Long = 0,
    val patientId: Long,
    val date: Long,
    val teeth: Map<Int, PerioTooth> = emptyMap()
) {
    fun tooth(n: Int): PerioTooth = teeth[n] ?: PerioTooth()
    fun with(n: Int, t: PerioTooth): PerioExam = copy(teeth = teeth + (n to t))
    val hasData: Boolean get() = teeth.values.any { it.hasData }
}

data class PerioSite(val tooth: Int, val index: Int)

data class PerioStats(val sites: Int, val deep: Int, val watch: Int, val bleeding: Int) {
    val bleedingPercent: Int get() = if (sites == 0) 0 else Math.round(bleeding * 100f / sites)
}

val PERIO_UPPER = listOf(18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28)
val PERIO_LOWER = listOf(48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38)

private val PERIO_COL = 50.dp
private val PERIO_GAP = 4.dp
private val PERIO_MID = 14.dp

fun PerioExam.stats(): PerioStats {
    var sites = 0
    var deep = 0
    var watch = 0
    var bleed = 0
    teeth.values.filter { !it.missing }.forEach { t ->
        for (i in 0 until 6) {
            val d = t.pd[i]
            if (d >= 0 || t.bop[i]) sites++
            if (d >= 5) deep++ else if (d == 4) watch++
            if (t.bop[i]) bleed++
        }
    }
    return PerioStats(sites, deep, watch, bleed)
}

/** Left-to-right order of a tooth's three spots as seen on the chart. */
private fun perioOrder(tooth: Int, tongue: Boolean): List<Int> {
    val q = tooth / 10
    val base = if (tongue) 3 else 0
    return if (q == 1 || q == 4) listOf(base, base + 1, base + 2) else listOf(base + 2, base + 1, base)
}

/** The order a dentist probes: cheek side left→right, then palate/tongue side right→left; upper, then lower. */
private fun perioSequence(exam: PerioExam): List<PerioSite> {
    val out = mutableListOf<PerioSite>()
    for (arch in listOf(PERIO_UPPER, PERIO_LOWER)) {
        arch.filter { !exam.tooth(it).missing }.forEach { t ->
            perioOrder(t, false).forEach { out.add(PerioSite(t, it)) }
        }
        arch.reversed().filter { !exam.tooth(it).missing }.forEach { t ->
            perioOrder(t, true).reversed().forEach { out.add(PerioSite(t, it)) }
        }
    }
    return out
}

private fun perioSpotName(s: PerioSite): String {
    val side = if (s.index < 3) "cheek" else if (s.tooth / 10 <= 2) "palate" else "tongue"
    val spot = listOf("back", "middle", "front")[s.index % 3]
    return "tooth ${s.tooth} · $side · $spot"
}

/** +1 if the deepest pocket is 2 mm or more worse than last time, -1 if 2 mm or more better. */
private fun perioChange(now: PerioTooth, before: PerioTooth?): Int {
    if (before == null || now.missing || before.missing) return 0
    val a = now.pd.maxOrNull() ?: -1
    val b = before.pd.maxOrNull() ?: -1
    if (a < 0 || b < 0) return 0
    return when {
        a - b >= 2 -> 1
        b - a >= 2 -> -1
        else -> 0
    }
}

// ───────────── Saving ─────────────
private fun perioJson(e: PerioExam): String {
    val o = JSONObject()
    e.teeth.forEach { (n, t) ->
        if (t == PerioTooth()) return@forEach
        o.put(n.toString(), JSONObject().apply {
            put("pd", JSONArray(t.pd))
            put("bop", JSONArray(t.bop.map { if (it) 1 else 0 }))
            put("rc", t.recCheek)
            put("rt", t.recTongue)
            put("mob", t.mobility)
            put("fur", t.furcation)
            put("mis", if (t.missing) 1 else 0)
        })
    }
    return o.toString()
}

private fun parsePerio(json: String?): Map<Int, PerioTooth> {
    if (json.isNullOrBlank()) return emptyMap()
    return runCatching {
        val o = JSONObject(json)
        val map = mutableMapOf<Int, PerioTooth>()
        o.keys().forEach { k ->
            val t = o.getJSONObject(k)
            val pd = t.optJSONArray("pd")
            val bop = t.optJSONArray("bop")
            map[k.toInt()] = PerioTooth(
                pd = List(6) { i -> pd?.optInt(i, -1) ?: -1 },
                bop = List(6) { i -> (bop?.optInt(i, 0) ?: 0) == 1 },
                recCheek = t.optInt("rc", -1),
                recTongue = t.optInt("rt", -1),
                mobility = t.optInt("mob", 0),
                furcation = t.optInt("fur", 0),
                missing = t.optInt("mis", 0) == 1
            )
        }
        map.toMap()
    }.getOrElse { emptyMap() }
}

private var perioReady: Any? = null

private fun ClinicDb.ensurePerio() {
    val sql = writableDatabase
    if (perioReady === sql) return
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS perio_exams (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "date INTEGER DEFAULT 0, data TEXT, notes TEXT)"
    )
    sql.execSQL("CREATE INDEX IF NOT EXISTS idx_perio_patient ON perio_exams(patient_id)")
    perioReady = sql
}

fun ClinicDb.perioExamsOf(patientId: Long): List<PerioExam> {
    ensurePerio()
    val list = mutableListOf<PerioExam>()
    readableDatabase.rawQuery(
        "SELECT id, patient_id, date, data FROM perio_exams WHERE patient_id = ? ORDER BY date DESC, id DESC",
        arrayOf(patientId.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(PerioExam(c.getLong(0), c.getLong(1), c.getLong(2), parsePerio(c.getString(3))))
        }
    }
    return list
}

fun ClinicDb.savePerioExam(e: PerioExam): Long {
    ensurePerio()
    val cv = ContentValues().apply {
        put("patient_id", e.patientId)
        put("date", e.date)
        put("data", perioJson(e))
    }
    return if (e.id == 0L) {
        writableDatabase.insert("perio_exams", null, cv)
    } else {
        writableDatabase.update("perio_exams", cv, "id = ?", arrayOf(e.id.toString()))
        e.id
    }
}

fun ClinicDb.deletePerioExam(id: Long) {
    ensurePerio()
    writableDatabase.delete("perio_exams", "id = ?", arrayOf(id.toString()))
}

/** A fresh chart: teeth missing last time, or already extracted, start as missing. */
fun ClinicDb.newPerioExam(patientId: Long, last: PerioExam?): PerioExam {
    val missing = mutableSetOf<Int>()
    last?.teeth?.forEach { (n, t) -> if (t.missing) missing.add(n) }
    runCatching { mouthStatusOf(patientId) }.getOrDefault(emptyList()).filter { it.missing }.forEach { missing.add(it.tooth) }
    treatmentsOf(patientId)
        .filter { it.status == STATUS_DONE && "extract" in it.name.lowercase() }
        .forEach { t -> t.teethList.forEach { s -> s.toString().toIntOrNull()?.let { missing.add(it) } } }
    return PerioExam(
        patientId = patientId,
        date = System.currentTimeMillis(),
        teeth = missing.associateWith { PerioTooth(missing = true) }
    )
}

// ───────────── Card in the patient file ─────────────
@Composable
fun PerioCard(db: ClinicDb, patientId: Long) {
    val version = DataBus.version
    val exams = remember(version) { runCatching { db.perioExamsOf(patientId) }.getOrElse { emptyList() } }
    var showing by remember { mutableStateOf<PerioExam?>(null) }
    val latest = exams.firstOrNull()

    AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gum health", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.Ink)
                Text(
                    if (latest == null) "No gum check-up yet" else "Last check-up ${fmtDate(latest.date)}",
                    fontSize = 12.sp, color = Brand.Sub
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftButton(if (latest == null) "Start chart" else "New check-up", Icons.Filled.Add, height = 40.dp) {
                showing = db.newPerioExam(patientId, latest)
            }
        }
        if (latest != null) {
            Spacer(Modifier.height(12.dp))
            PerioChips(latest.stats(), exams.getOrNull(1)?.stats())
            Spacer(Modifier.height(6.dp))
            exams.take(5).forEach { e ->
                val st = e.stats()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showing = e }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(fmtDate(e.date), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Brand.Ink)
                        Text("${st.deep} deep pockets · ${st.bleedingPercent}% bleeding", fontSize = 12.sp, color = Brand.Sub)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Faint)
                }
            }
        }
    }

    showing?.let { e ->
        val previous = exams.firstOrNull { it.id != e.id && (it.date < e.date || (it.date == e.date && it.id < e.id)) }
        PerioScreen(db, e, previous, onClose = { showing = null })
    }
}

@Composable
private fun PerioChips(now: PerioStats, before: PerioStats?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(
            "Deep pockets ${now.deep}",
            if (now.deep > 0) Brand.RedSoft else Brand.GreenSoft,
            if (now.deep > 0) Brand.Red else Brand.GreenText
        )
        val bleedHigh = now.bleedingPercent >= 10
        Pill(
            "Bleeding ${now.bleedingPercent}%",
            if (bleedHigh) Brand.GoldSoft else Brand.GreenSoft,
            if (bleedHigh) Brand.GoldDeep else Brand.GreenText
        )
        if (before != null) {
            val better = now.deep <= before.deep
            Pill(
                "Was ${before.deep} → now ${now.deep}",
                if (better) Brand.GreenSoft else Brand.RedSoft,
                if (better) Brand.GreenText else Brand.Red
            )
        }
    }
}

// ───────────── The chart screen ─────────────
@Composable
private fun PerioScreen(db: ClinicDb, initial: PerioExam, previous: PerioExam?, onClose: () -> Unit) {
    val notify = LocalNotify.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val patient = remember { db.getPatient(initial.patientId) }
    val clinicName = remember { db.clinicInfo().name }
    var exam by remember { mutableStateOf(initial) }
    var saved by remember { mutableStateOf(initial) }
    var cursor by remember {
        mutableStateOf(perioSequence(initial).firstOrNull { initial.tooth(it.tooth).pd[it.index] < 0 })
    }
    var arch by remember { mutableIntStateOf(if ((cursor?.tooth ?: 18) / 10 >= 3) 1 else 0) }
    var bleedMode by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Int?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val chartScroll = rememberScrollState()
    val sequence = perioSequence(exam)
    val stats = exam.stats()

    fun persist() {
        if (exam == saved) return
        if (exam.id == 0L && !exam.hasData) return
        val id = db.savePerioExam(exam)
        val now = exam.copy(id = id)
        exam = now
        saved = now
        DataBus.changed()
    }

    fun close() {
        persist()
        onClose()
    }

    fun setTooth(n: Int, t: PerioTooth) {
        exam = exam.with(n, t)
    }

    fun enter(d: Int) {
        val s = cursor ?: return
        val t = exam.tooth(s.tooth)
        setTooth(s.tooth, t.copy(pd = t.pd.toMutableList().also { it[s.index] = d }))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        cursor = sequence.getOrNull(sequence.indexOf(s) + 1)
    }

    fun back() {
        val c = cursor
        cursor = if (c == null) sequence.lastOrNull() else sequence.getOrNull(sequence.indexOf(c) - 1) ?: c
    }

    fun next() {
        val c = cursor ?: return
        cursor = sequence.getOrNull(sequence.indexOf(c) + 1)
    }

    fun clear() {
        val s = cursor ?: return
        val t = exam.tooth(s.tooth)
        setTooth(
            s.tooth,
            t.copy(
                pd = t.pd.toMutableList().also { it[s.index] = -1 },
                bop = t.bop.toMutableList().also { it[s.index] = false }
            )
        )
    }

    fun tapSite(s: PerioSite) {
        val t = exam.tooth(s.tooth)
        if (t.missing) return
        if (bleedMode) {
            setTooth(s.tooth, t.copy(bop = t.bop.toMutableList().also { it[s.index] = !it[s.index] }))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            cursor = s
        }
    }

    fun showArch(a: Int) {
        arch = a
        val lower = a == 1
        val c = cursor
        if (c == null || (c.tooth / 10 >= 3) != lower) {
            val inArch = sequence.filter { (it.tooth / 10 >= 3) == lower }
            cursor = inArch.firstOrNull { exam.tooth(it.tooth).pd[it.index] < 0 } ?: inArch.firstOrNull()
        }
    }

    fun share() {
        persist()
        val first = patient?.name?.trim()?.substringBefore(" ").orEmpty()
        val text = buildString {
            append("Hello $first, here is your gum check-up summary from $clinicName (${fmtDate(exam.date)}):\n\n")
            append("• Deep gum pockets (5 mm or more): ${stats.deep}\n")
            append("• Spots that bled: ${stats.bleedingPercent}%\n")
            if (previous != null) {
                val p = previous.stats()
                append("• Last check-up (${fmtDate(previous.date)}): ${p.deep} deep pockets, ${p.bleedingPercent}% bleeding\n")
            }
            append("\nHealthy gums measure 1–3 mm and do not bleed.")
        }
        val phone = patient?.phone.orEmpty().filter { it.isDigit() }.takeLast(10)
        val intent = if (phone.length == 10) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$phone?text=" + Uri.encode(text)))
        } else {
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Send summary"
            )
        }
        runCatching { context.startActivity(intent) }.onFailure { notify("Could not open WhatsApp") }
    }

    // Save a moment after each change
    LaunchedEffect(exam) {
        if (exam != saved) {
            delay(700)
            persist()
        }
    }
    // Follow the cursor to the right arch
    LaunchedEffect(cursor) {
        cursor?.let { c -> arch = if (c.tooth / 10 >= 3) 1 else 0 }
    }

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
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { pickDate = true }) {
                    Text("Gum chart", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Brand.Ink)
                    Text(
                        "${patient?.name.orEmpty()} · ${fmtDate(exam.date)}",
                        fontSize = 12.sp, color = Brand.Sub, maxLines = 1
                    )
                }
                CircleIconButton(Icons.Filled.Share, { share() }, bg = Brand.Surface, tint = Brand.GreenDeep, size = 44.dp)
                if (exam.id != 0L) {
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 44.dp)
                }
            }
            Column(
                Modifier.widthIn(max = 1100.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PerioChips(stats, previous?.stats())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Upper teeth", arch == 0) { showArch(0) }
                    ChoiceChip("Lower teeth", arch == 1) { showArch(1) }
                }
                AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val viewport = constraints.maxWidth.toFloat()
                        val teeth = if (arch == 0) PERIO_UPPER else PERIO_LOWER
                        LaunchedEffect(cursor, arch) {
                            val c = cursor ?: return@LaunchedEffect
                            val i = teeth.indexOf(c.tooth)
                            if (i < 0) return@LaunchedEffect
                            val col = with(density) { (PERIO_COL + PERIO_GAP).toPx() }
                            val mid = with(density) { PERIO_MID.toPx() }
                            val x = i * col + (if (i >= 8) mid else 0f)
                            val target = (x - viewport / 2f + col / 2f).toInt().coerceIn(0, chartScroll.maxValue)
                            chartScroll.animateScrollTo(target)
                        }
                        PerioChart(
                            exam, previous, teeth, arch == 1, cursor, chartScroll,
                            onSite = { tapSite(it) }, onTooth = { detail = it }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PerioLegend()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Enter depths", !bleedMode) { bleedMode = false }
                    ChoiceChip("Mark bleeding", bleedMode) { bleedMode = true }
                }
                if (bleedMode) {
                    AppCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text("Tap each spot that bled", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink)
                        Text(
                            "A red dot appears under the number. Tap again to remove it. " +
                                "So far: ${stats.bleeding} spots (${stats.bleedingPercent}%).",
                            fontSize = 13.sp, color = Brand.Sub
                        )
                    }
                } else {
                    val c = cursor
                    PerioKeypad(
                        label = if (c != null) "Now entering: ${perioSpotName(c)}" else "All spots entered ✓  Tap any spot to change it.",
                        onDigit = { enter(it) },
                        onBack = { back() },
                        onClear = { clear() },
                        onNext = { next() }
                    )
                }
            }
        }
    }

    detail?.let { n ->
        PerioToothDialog(n, exam.tooth(n), onDismiss = { detail = null }) { t ->
            setTooth(n, t)
            detail = null
            val c = cursor
            if (t.missing && c != null && c.tooth == n) {
                val updated = exam
                cursor = perioSequence(updated).firstOrNull { updated.tooth(it.tooth).pd[it.index] < 0 }
            }
        }
    }
    if (pickDate) {
        val day = Instant.ofEpochMilli(exam.date).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        DatePick(day, onDismiss = { pickDate = false }) { d ->
            exam = exam.copy(date = withDay(exam.date, d))
            pickDate = false
        }
    }
    if (askDelete) {
        ConfirmDialog(
            "Delete this gum chart?",
            "The gum chart from ${fmtDate(exam.date)} will be removed.",
            confirmText = "Delete",
            onConfirm = {
                askDelete = false
                db.deletePerioExam(exam.id)
                DataBus.changed()
                notify("Gum chart deleted")
                onClose()
            },
            onDismiss = { askDelete = false }
        )
    }
}

@Composable
private fun PerioChart(
    exam: PerioExam,
    previous: PerioExam?,
    teeth: List<Int>,
    lower: Boolean,
    cursor: PerioSite?,
    scroll: ScrollState,
    onSite: (PerioSite) -> Unit,
    onTooth: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Cheek side", fontSize = 11.sp, color = Brand.Sub, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        Row(Modifier.horizontalScroll(scroll)) {
            teeth.forEachIndexed { i, n ->
                if (i == 8) {
                    Box(Modifier.width(PERIO_MID).height(118.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(1.5.dp).fillMaxHeight().background(Brand.Line))
                    }
                }
                PerioToothColumn(n, exam.tooth(n), previous?.tooth(n), cursor, onSite, onTooth)
                if (i != 7 && i != teeth.lastIndex) Spacer(Modifier.width(PERIO_GAP))
            }
        }
        Text(
            if (lower) "Tongue side" else "Palate side",
            fontSize = 11.sp, color = Brand.Sub, modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )
    }
}

@Composable
private fun PerioToothColumn(
    n: Int,
    t: PerioTooth,
    before: PerioTooth?,
    cursor: PerioSite?,
    onSite: (PerioSite) -> Unit,
    onTooth: (Int) -> Unit
) {
    Column(Modifier.width(PERIO_COL), horizontalAlignment = Alignment.CenterHorizontally) {
        PerioTriple(n, t, tongue = false, cursor = cursor, onSite = onSite)
        Spacer(Modifier.height(5.dp))
        val hot = cursor?.tooth == n
        Box(
            Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(9.dp))
                .background(if (hot) Brand.GreenSoft else Brand.Soft).clickable { onTooth(n) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$n", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = when {
                    t.missing -> Brand.Faint
                    hot -> Brand.GreenText
                    else -> Brand.Ink
                }
            )
        }
        val marks = buildList {
            if (t.missing) {
                add("missing")
            } else {
                if (t.mobility > 0) add("M${t.mobility}")
                if (t.furcation > 0) add("F${t.furcation}")
            }
        }
        val change = perioChange(t, before)
        Row(Modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (marks.isNotEmpty()) Text(marks.joinToString(" "), fontSize = 9.sp, color = Brand.Sub, maxLines = 1)
            if (change != 0) {
                Text(
                    if (change > 0) " ▲" else " ▼", fontSize = 9.sp,
                    color = if (change > 0) Brand.Red else Brand.GreenText
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        PerioTriple(n, t, tongue = true, cursor = cursor, onSite = onSite)
    }
}

@Composable
private fun PerioTriple(n: Int, t: PerioTooth, tongue: Boolean, cursor: PerioSite?, onSite: (PerioSite) -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        perioOrder(n, tongue).forEach { idx ->
            val d = t.pd[idx]
            val bleed = t.bop[idx] && !t.missing
            val selected = cursor == PerioSite(n, idx)
            val bg = when {
                t.missing -> Color.Transparent
                d >= 5 -> Brand.RedSoft
                d == 4 -> Brand.GoldSoft
                else -> Color.Transparent
            }
            val fg = when {
                t.missing || d < 0 -> Brand.Faint
                d >= 5 -> Brand.Red
                d == 4 -> Brand.GoldDeep
                else -> Brand.Ink
            }
            val shape = RoundedCornerShape(6.dp)
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(bg)
                    .then(if (selected) Modifier.border(2.dp, Brand.GreenDeep, shape) else Modifier)
                    .clickable(enabled = !t.missing) { onSite(PerioSite(n, idx)) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    when {
                        t.missing -> "–"
                        d < 0 -> "·"
                        else -> "$d"
                    },
                    fontSize = 13.sp,
                    fontWeight = if (d >= 4) FontWeight.SemiBold else FontWeight.Normal,
                    color = fg
                )
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (bleed) Brand.Red else Color.Transparent))
            }
        }
    }
}

@Composable
private fun PerioLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PerioLegendItem(Brand.Red, CircleShape, "Bleeding")
        PerioLegendItem(Brand.Red, RoundedCornerShape(3.dp), "5+ mm")
        PerioLegendItem(Brand.Gold, RoundedCornerShape(3.dp), "4 mm")
        Text("M loose · F root bone loss · ▲ worse · ▼ better", fontSize = 11.sp, color = Brand.Sub)
    }
}

@Composable
private fun PerioLegendItem(color: Color, shape: Shape, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(shape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Brand.Sub)
    }
}

@Composable
private fun PerioKeypad(
    label: String,
    onDigit: (Int) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text(label, fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(bottom = 10.dp))
        listOf(1..6, 7..12).forEach { range ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { d -> PerioKey("$d", Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerioKey("◀ Back", Modifier.weight(1f), small = true, onClick = onBack)
            PerioKey("Clear", Modifier.weight(1f), small = true, onClick = onClear)
            PerioKey("Next ▶", Modifier.weight(1f), bg = Brand.GreenDeep, fg = Color.White, small = true, onClick = onNext)
        }
    }
}

@Composable
private fun PerioKey(
    text: String,
    modifier: Modifier,
    bg: Color = Brand.Soft,
    fg: Color = Brand.Ink,
    small: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = if (small) 14.sp else 18.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

@Composable
private fun PerioToothDialog(n: Int, t: PerioTooth, onDismiss: () -> Unit, onSave: (PerioTooth) -> Unit) {
    var missing by remember { mutableStateOf(t.missing) }
    var mobility by remember { mutableIntStateOf(t.mobility) }
    var furcation by remember { mutableIntStateOf(t.furcation) }
    var recCheek by remember { mutableStateOf(if (t.recCheek >= 0) t.recCheek.toString() else "") }
    var recTongue by remember { mutableStateOf(if (t.recTongue >= 0) t.recTongue.toString() else "") }
    val molar = n % 10 >= 6
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tooth $n", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            ToggleRow("Tooth is missing", "It will be skipped while entering", missing) { missing = it }
            if (!missing) {
                Text("Looseness (mobility)", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { m -> ChoiceChip(if (m == 0) "None" else "$m", mobility == m) { mobility = m } }
                }
                if (molar) {
                    Text("Bone loss between the roots (furcation)", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("None", "I", "II", "III").forEachIndexed { i, s ->
                            ChoiceChip(s, furcation == i) { furcation = i }
                        }
                    }
                }
                Text("Gum shrinkage (recession) in mm", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(
                        recCheek, { recCheek = it.filter(Char::isDigit).take(2) }, "Cheek side",
                        modifier = Modifier.weight(1f), type = KeyboardType.Number
                    )
                    AppField(
                        recTongue, { recTongue = it.filter(Char::isDigit).take(2) },
                        if (n / 10 <= 2) "Palate side" else "Tongue side",
                        modifier = Modifier.weight(1f), type = KeyboardType.Number
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, height = 52.dp) {
                    onSave(
                        t.copy(
                            missing = missing,
                            mobility = if (missing) 0 else mobility,
                            furcation = if (missing || !molar) 0 else furcation,
                            recCheek = recCheek.toIntOrNull() ?: -1,
                            recTongue = recTongue.toIntOrNull() ?: -1
                        )
                    )
                }
            }
        }
    }
}
