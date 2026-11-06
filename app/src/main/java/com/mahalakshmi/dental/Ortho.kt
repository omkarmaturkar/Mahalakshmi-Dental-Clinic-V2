@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

// ───────────── Braces (orthodontic) case sheet ─────────────
const val ORTHO_PLANNING = 0
const val ORTHO_ACTIVE = 1
const val ORTHO_RETENTION = 2
const val ORTHO_FINISHED = 3

data class OrthoCase(
    val id: Long = 0,
    val patientId: Long,
    val startDate: Long = System.currentTimeMillis(),
    val months: Int = 18,
    val status: Int = ORTHO_ACTIVE,
    val complaint: String = "",
    val bite: String = "",
    val profile: String = "",
    val overjet: String = "",
    val overbite: String = "",
    val crowding: String = "",
    val findings: String = "",
    val extraction: String = "",
    val appliance: String = "",
    val plan: String = "",
    val fee: Double = 0.0,
    val downPayment: Double = 0.0,
    val monthly: Double = 0.0,
    val paidBefore: Double = 0.0,
    val treatmentId: Long = 0
)

data class OrthoVisit(
    val id: Long = 0,
    val caseId: Long,
    val patientId: Long,
    val date: Long,
    val upper: String = "",
    val lower: String = "",
    val elastics: String = "",
    val notes: String = ""
)

data class OrthoInstalment(val label: String, val due: Long, val amount: Double, val paid: Double) {
    val isPaid: Boolean get() = paid >= amount - 0.5
}

private val ORTHO_BITES = listOf("Class I", "Class II div 1", "Class II div 2", "Class III")
private val ORTHO_PROFILES = listOf("Straight", "Convex", "Concave")
private val ORTHO_CROWDING = listOf("None", "Mild", "Moderate", "Severe")
private val ORTHO_FINDINGS = listOf("Spacing", "Crossbite", "Open bite", "Deep bite", "Midline shift", "Impacted canine")
private val ORTHO_EXTRACTIONS = listOf("Non-extraction", "4 premolars", "Upper premolars", "Lower premolars", "Other")
private val ORTHO_APPLIANCES = listOf("Metal braces", "Ceramic braces", "Self-ligating", "Aligners", "Removable")
private val ORTHO_WIRES = listOf(
    "0.012 NiTi", "0.014 NiTi", "0.016 NiTi", "0.018 NiTi", "0.016×0.022 NiTi", "0.017×0.025 NiTi",
    "0.019×0.025 NiTi", "0.016 SS", "0.018 SS", "0.019×0.025 SS", "0.017×0.025 TMA"
)
private val ORTHO_ELASTICS = listOf("None", "Class II", "Class III", "Box", "Cross", "Triangle")
private val ORTHO_STATUS = listOf("Planning", "Active", "Retention", "Finished")

fun orthoLike(name: String): Boolean {
    val n = name.lowercase()
    return "brace" in n || "ortho" in n || "aligner" in n
}

private fun orthoGuessAppliance(name: String): String {
    val n = name.lowercase()
    return when {
        "ceramic" in n -> "Ceramic braces"
        "self" in n -> "Self-ligating"
        "aligner" in n -> "Aligners"
        "removable" in n -> "Removable"
        else -> "Metal braces"
    }
}

// ───────────── Saving ─────────────
private var orthoReady: Any? = null

private fun ClinicDb.ensureOrtho() {
    val sql = writableDatabase
    if (orthoReady === sql) return
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS ortho_cases (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "start_date INTEGER DEFAULT 0, months INTEGER DEFAULT 18, status INTEGER DEFAULT 1, complaint TEXT, bite TEXT, " +
            "profile TEXT, overjet TEXT, overbite TEXT, crowding TEXT, findings TEXT, extraction TEXT, appliance TEXT, plan TEXT, " +
            "fee REAL DEFAULT 0, down_payment REAL DEFAULT 0, monthly REAL DEFAULT 0, paid_before REAL DEFAULT 0, " +
            "treatment_id INTEGER DEFAULT 0, created_at INTEGER DEFAULT 0)"
    )
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS ortho_visits (id INTEGER PRIMARY KEY AUTOINCREMENT, case_id INTEGER NOT NULL, " +
            "patient_id INTEGER NOT NULL, date INTEGER DEFAULT 0, upper_wire TEXT, lower_wire TEXT, elastics TEXT, notes TEXT)"
    )
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS ortho_payments (case_id INTEGER NOT NULL, payment_id INTEGER NOT NULL, " +
            "PRIMARY KEY (case_id, payment_id))"
    )
    orthoReady = sql
}

private const val ORTHO_COLS = "id, patient_id, start_date, months, status, complaint, bite, profile, overjet, overbite, " +
    "crowding, findings, extraction, appliance, plan, fee, down_payment, monthly, paid_before, treatment_id"

fun ClinicDb.orthoCaseOf(patientId: Long): OrthoCase? {
    ensureOrtho()
    return readableDatabase.rawQuery(
        "SELECT $ORTHO_COLS FROM ortho_cases WHERE patient_id = ? ORDER BY id DESC LIMIT 1",
        arrayOf(patientId.toString())
    ).use { c ->
        if (!c.moveToFirst()) {
            null
        } else {
            OrthoCase(
                id = c.getLong(0), patientId = c.getLong(1), startDate = c.getLong(2), months = c.getInt(3),
                status = c.getInt(4), complaint = c.getString(5) ?: "", bite = c.getString(6) ?: "",
                profile = c.getString(7) ?: "", overjet = c.getString(8) ?: "", overbite = c.getString(9) ?: "",
                crowding = c.getString(10) ?: "", findings = c.getString(11) ?: "", extraction = c.getString(12) ?: "",
                appliance = c.getString(13) ?: "", plan = c.getString(14) ?: "", fee = c.getDouble(15),
                downPayment = c.getDouble(16), monthly = c.getDouble(17), paidBefore = c.getDouble(18),
                treatmentId = c.getLong(19)
            )
        }
    }
}

fun ClinicDb.saveOrthoCase(o: OrthoCase): Long {
    ensureOrtho()
    val cv = ContentValues().apply {
        put("patient_id", o.patientId)
        put("start_date", o.startDate)
        put("months", o.months)
        put("status", o.status)
        put("complaint", o.complaint)
        put("bite", o.bite)
        put("profile", o.profile)
        put("overjet", o.overjet)
        put("overbite", o.overbite)
        put("crowding", o.crowding)
        put("findings", o.findings)
        put("extraction", o.extraction)
        put("appliance", o.appliance)
        put("plan", o.plan)
        put("fee", o.fee)
        put("down_payment", o.downPayment)
        put("monthly", o.monthly)
        put("paid_before", o.paidBefore)
        put("treatment_id", o.treatmentId)
    }
    return if (o.id == 0L) {
        cv.put("created_at", System.currentTimeMillis())
        writableDatabase.insert("ortho_cases", null, cv)
    } else {
        writableDatabase.update("ortho_cases", cv, "id = ?", arrayOf(o.id.toString()))
        o.id
    }
}

fun ClinicDb.deleteOrthoCase(id: Long) {
    ensureOrtho()
    val args = arrayOf(id.toString())
    writableDatabase.delete("ortho_visits", "case_id = ?", args)
    writableDatabase.delete("ortho_payments", "case_id = ?", args)
    writableDatabase.delete("ortho_cases", "id = ?", args)
}

fun ClinicDb.orthoVisitsOf(caseId: Long): List<OrthoVisit> {
    ensureOrtho()
    val list = mutableListOf<OrthoVisit>()
    readableDatabase.rawQuery(
        "SELECT id, case_id, patient_id, date, upper_wire, lower_wire, elastics, notes FROM ortho_visits " +
            "WHERE case_id = ? ORDER BY date DESC, id DESC",
        arrayOf(caseId.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(
                OrthoVisit(
                    c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4) ?: "",
                    c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7) ?: ""
                )
            )
        }
    }
    return list
}

fun ClinicDb.saveOrthoVisit(v: OrthoVisit) {
    ensureOrtho()
    val cv = ContentValues().apply {
        put("case_id", v.caseId)
        put("patient_id", v.patientId)
        put("date", v.date)
        put("upper_wire", v.upper)
        put("lower_wire", v.lower)
        put("elastics", v.elastics)
        put("notes", v.notes)
    }
    if (v.id == 0L) {
        writableDatabase.insert("ortho_visits", null, cv)
    } else {
        writableDatabase.update("ortho_visits", cv, "id = ?", arrayOf(v.id.toString()))
    }
}

fun ClinicDb.deleteOrthoVisit(id: Long) {
    ensureOrtho()
    writableDatabase.delete("ortho_visits", "id = ?", arrayOf(id.toString()))
}

/** Paid towards the braces: amount paid before using the app + payments received from the case sheet. */
fun ClinicDb.orthoPaid(o: OrthoCase): Double {
    ensureOrtho()
    val linked = readableDatabase.rawQuery(
        "SELECT IFNULL(SUM(p.amount), 0) FROM ortho_payments op JOIN payments p ON p.id = op.payment_id WHERE op.case_id = ?",
        arrayOf(o.id.toString())
    ).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }
    return o.paidBefore + linked
}

fun ClinicDb.linkOrthoPayment(caseId: Long, paymentId: Long) {
    ensureOrtho()
    val cv = ContentValues().apply {
        put("case_id", caseId)
        put("payment_id", paymentId)
    }
    writableDatabase.insertWithOnConflict("ortho_payments", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
}

// ───────────── Dates, months and instalments ─────────────
private fun orthoLocal(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun orthoPlusMonths(start: Long, k: Int): Long =
    Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).plusMonths(k.toLong()).toInstant().toEpochMilli()

private fun orthoMonth(o: OrthoCase): Int =
    ChronoUnit.MONTHS.between(orthoLocal(o.startDate), orthoLocal(System.currentTimeMillis())).toInt().coerceAtLeast(0) + 1

private fun orthoShort(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))

private fun orthoStatusText(o: OrthoCase): String = when (o.status) {
    ORTHO_PLANNING -> "Planning"
    ORTHO_ACTIVE -> "Month ${orthoMonth(o)} of ${o.months}"
    ORTHO_RETENTION -> "Retention"
    else -> "Finished"
}

private fun orthoWires(v: OrthoVisit): String =
    listOf(
        if (v.upper.isNotBlank()) "U ${v.upper}" else "",
        if (v.lower.isNotBlank()) "L ${v.lower}" else ""
    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No wire change" }

/** Down payment, then monthly instalments until the fee is covered. Payments fill them in order. */
fun orthoSchedule(o: OrthoCase, paidTotal: Double): List<OrthoInstalment> {
    if (o.fee <= 0.0) return emptyList()
    val out = mutableListOf<OrthoInstalment>()
    var left = o.fee
    var pool = paidTotal
    fun add(label: String, due: Long, amount: Double) {
        val got = minOf(pool, amount).coerceAtLeast(0.0)
        pool -= got
        out.add(OrthoInstalment(label, due, amount, got))
        left -= amount
    }
    if (o.downPayment > 0.0) add("Down payment", o.startDate, minOf(o.downPayment, left))
    if (o.monthly > 0.0) {
        var k = 1
        while (left > 0.5 && k <= 120) {
            add("Month $k", orthoPlusMonths(o.startDate, k), minOf(o.monthly, left))
            k++
        }
    } else if (left > 0.5) {
        add("Balance", o.startDate, left)
    }
    return out
}

private fun orthoMoneyText(v: String) = v.filter { it.isDigit() || it == '.' }.take(9)

private fun orthoWhatsApp(context: Context, phone: String, text: String): Boolean {
    val ten = phone.filter { it.isDigit() }.takeLast(10)
    val intent = if (ten.length == 10) {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$ten?text=" + Uri.encode(text)))
    } else {
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Send reminder"
        )
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

// ───────────── Card in the patient file ─────────────
@Composable
fun OrthoCard(db: ClinicDb, patientId: Long) {
    val notify = LocalNotify.current
    val version = DataBus.version
    val case = remember(version) { runCatching { db.orthoCaseOf(patientId) }.getOrNull() }
    val treatments = remember(version) { db.treatmentsOf(patientId) }
    val visits = remember(version, case?.id) { case?.let { db.orthoVisitsOf(it.id) } ?: emptyList() }
    val paid = remember(version, case?.id) { case?.let { db.orthoPaid(it) } ?: 0.0 }
    var open by remember { mutableStateOf(false) }
    var withVisit by remember { mutableStateOf(false) }

    if (case == null) {
        val braces = treatments.firstOrNull { orthoLike(it.name) } ?: return
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.GoldSoft).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Braces case sheet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
                Text("Record the bite, plan, wires and instalments.", fontSize = 12.sp, color = Brand.GoldDeep)
            }
            Spacer(Modifier.width(8.dp))
            SoftButton("Start", null, height = 40.dp, bg = Brand.Surface, fg = Brand.GoldDeep) {
                val now = System.currentTimeMillis()
                db.saveOrthoCase(
                    OrthoCase(
                        patientId = patientId,
                        startDate = if (braces.createdAt in 1..now) braces.createdAt else now,
                        appliance = orthoGuessAppliance(braces.name),
                        fee = braces.fee,
                        treatmentId = braces.id
                    )
                )
                open = true
                DataBus.changed()
                notify("Braces case sheet started ✓")
            }
        }
        return
    }

    val next = orthoSchedule(case, paid).firstOrNull { !it.isPaid }
    AppCard(Modifier.fillMaxWidth(), onClick = { open = true }, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Braces case", fontFamily = DisplayFont, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Brand.Ink)
                val sub = listOf(case.appliance, case.bite, case.extraction).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    sub.ifBlank { "Tap to fill in the diagnosis and plan" },
                    fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            OrthoStatusPill(case)
        }
        if (case.status == ORTHO_ACTIVE) {
            Spacer(Modifier.height(12.dp))
            OrthoBar(orthoMonth(case) / case.months.toFloat())
        }
        Spacer(Modifier.height(10.dp))
        val last = visits.firstOrNull()
        Text(
            if (last == null) "No visits yet" else "Last visit ${orthoShort(last.date)} · ${orthoWires(last)}",
            fontSize = 13.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (case.fee > 0.0) {
            val overdue = next != null && epochDayOf(next.due) < todayEpochDay()
            Text(
                if (next == null) "Fees fully paid ✓"
                else "Next instalment ${rs(next.amount - next.paid)} · due ${fmtDate(next.due)}",
                fontSize = 13.sp,
                color = when {
                    next == null -> Brand.GreenText
                    overdue -> Brand.Red
                    else -> Brand.Sub
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Add visit", Modifier.weight(1f), Icons.Filled.Add, height = 46.dp) {
                withVisit = true
                open = true
            }
            SoftButton("Open case", null, Modifier.weight(1f), height = 46.dp) {
                withVisit = false
                open = true
            }
        }
    }

    if (open) {
        OrthoSheet(db, patientId, withVisit) {
            open = false
            withVisit = false
        }
    }
}

@Composable
private fun OrthoStatusPill(o: OrthoCase) {
    val (bg, fg) = when (o.status) {
        ORTHO_ACTIVE -> Brand.GoldSoft to Brand.GoldDeep
        ORTHO_FINISHED -> Brand.GreenSoft to Brand.GreenText
        else -> Brand.Soft to Brand.Sub
    }
    Pill(orthoStatusText(o), bg, fg)
}

@Composable
private fun OrthoBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Brand.Soft)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(50)).background(Brand.GreenDeep)
        )
    }
}

// ───────────── The case sheet ─────────────
@Composable
private fun OrthoSheet(db: ClinicDb, patientId: Long, startWithVisit: Boolean, onClose: () -> Unit) {
    val notify = LocalNotify.current
    val context = LocalContext.current
    val version = DataBus.version
    val patient = remember { db.getPatient(patientId) }
    val clinicName = remember { db.clinicInfo().name }
    var case by remember { mutableStateOf(db.orthoCaseOf(patientId)) }
    var saved by remember { mutableStateOf(case) }
    val current = case ?: return
    val visits = remember(version) { db.orthoVisitsOf(current.id) }
    val linkedPaid = remember(version) { db.orthoPaid(current.copy(paidBefore = 0.0)) }
    val treatments = remember(version) { db.treatmentsOf(patientId) }
    val booking = remember(version) { db.nextBooking(patientId) }
    val photos = remember(version) {
        db.photosOf(patientId).filter { it.date >= current.startDate - 30L * 86_400_000L }.sortedBy { it.date }
    }
    val paid = linkedPaid + current.paidBefore
    val schedule = orthoSchedule(current, paid)
    val next = schedule.firstOrNull { !it.isPaid }
    val linked = treatments.firstOrNull { it.id == current.treatmentId }

    var feeText by remember { mutableStateOf(numText(current.fee)) }
    var downText by remember { mutableStateOf(numText(current.downPayment)) }
    var monthlyText by remember { mutableStateOf(numText(current.monthly)) }
    var beforeText by remember { mutableStateOf(numText(current.paidBefore)) }
    var visitEdit by remember { mutableStateOf<OrthoVisit?>(null) }
    var payOpen by remember { mutableStateOf(false) }
    var receiptId by remember { mutableStateOf<Long?>(null) }
    var bookReason by remember { mutableStateOf<String?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    fun edit(f: (OrthoCase) -> OrthoCase) {
        case?.let { case = f(it) }
    }

    fun persist() {
        val c = case ?: return
        if (c == saved) return
        db.saveOrthoCase(c)
        val t = treatments.firstOrNull { it.id == c.treatmentId }
        if (t != null && c.fee > 0.0 && kotlin.math.abs(t.fee - c.fee) > 0.01) db.saveTreatment(t.copy(fee = c.fee))
        saved = c
        DataBus.changed()
    }

    fun close() {
        persist()
        onClose()
    }

    fun newVisit(): OrthoVisit {
        val last = visits.firstOrNull()
        return OrthoVisit(
            caseId = current.id, patientId = patientId, date = System.currentTimeMillis(),
            upper = last?.upper.orEmpty(), lower = last?.lower.orEmpty()
        )
    }

    fun remind() {
        val n = next ?: return
        val first = patient?.name?.trim()?.substringBefore(" ").orEmpty()
        val text = buildString {
            append("Hello $first, a gentle reminder from $clinicName: your braces instalment of ${rs(n.amount - n.paid)} ")
            append(if (epochDayOf(n.due) < todayEpochDay()) "was due on ${fmtDate(n.due)}." else "is due on ${fmtDate(n.due)}.")
            booking?.let { b ->
                append(" Your next visit is on ${fmtDay(b.day)}")
                append(if (b.time.isNotBlank()) " at ${b.time}." else ".")
            }
            append(" Thank you!")
        }
        if (!orthoWhatsApp(context, patient?.phone.orEmpty(), text)) notify("Could not open WhatsApp")
    }

    fun addToBill() {
        persist()
        val c = case ?: return
        val id = db.saveTreatment(
            Treatment(patientId = patientId, name = c.appliance.ifBlank { "Braces" }, fee = c.fee, status = STATUS_ONGOING)
        )
        case = c.copy(treatmentId = id)
        DataBus.changed()
        notify("Braces fee added to the bill ✓")
    }

    LaunchedEffect(Unit) {
        if (startWithVisit) visitEdit = newVisit()
    }
    LaunchedEffect(case) {
        if (case != saved) {
            delay(700)
            persist()
        }
    }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, { close() }, bg = Brand.Surface, tint = Brand.Ink, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Braces case", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Brand.Ink)
                    Text(
                        "${patient?.name.orEmpty()} · started ${fmtDate(current.startDate)}",
                        fontSize = 12.sp, color = Brand.Sub, maxLines = 1
                    )
                }
            }
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Progress
                AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(current.appliance.ifBlank { "Braces" }, fontFamily = DisplayFont, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Brand.Ink)
                            Text(
                                "Started ${fmtDate(current.startDate)} · tap to change", fontSize = 12.sp, color = Brand.Sub,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { pickStart = true }
                            )
                        }
                        OrthoStatusPill(current)
                    }
                    if (current.status == ORTHO_ACTIVE) {
                        Spacer(Modifier.height(12.dp))
                        OrthoBar(orthoMonth(current) / current.months.toFloat())
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ORTHO_STATUS.forEachIndexed { i, s -> ChoiceChip(s, current.status == i) { edit { it.copy(status = i) } } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Planned length", fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.weight(1f))
                        OrthoStep("−") { edit { it.copy(months = (it.months - 1).coerceAtLeast(1)) } }
                        Text(
                            "${current.months} months", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        OrthoStep("+") { edit { it.copy(months = (it.months + 1).coerceAtMost(60)) } }
                    }
                }

                // ── Diagnosis
                AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    OrthoTitle("Diagnosis")
                    AppField(
                        current.complaint, { v -> edit { it.copy(complaint = v) } }, "Patient's main concern",
                        caps = KeyboardCapitalization.Sentences
                    )
                    Spacer(Modifier.height(12.dp))
                    OrthoChoice("Bite (molar relation)", ORTHO_BITES, current.bite) { v -> edit { it.copy(bite = v) } }
                    OrthoChoice("Face profile", ORTHO_PROFILES, current.profile) { v -> edit { it.copy(profile = v) } }
                    OrthoChoice("Crowding", ORTHO_CROWDING, current.crowding) { v -> edit { it.copy(crowding = v) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppField(
                            current.overjet, { v -> edit { it.copy(overjet = orthoMoneyText(v).take(4)) } }, "Overjet (mm)",
                            modifier = Modifier.weight(1f), type = KeyboardType.Decimal
                        )
                        AppField(
                            current.overbite, { v -> edit { it.copy(overbite = orthoMoneyText(v).take(4)) } }, "Overbite (mm)",
                            modifier = Modifier.weight(1f), type = KeyboardType.Decimal
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OrthoMulti("Other findings", ORTHO_FINDINGS, current.findings) { v -> edit { it.copy(findings = v) } }
                }

                // ── Plan
                AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    OrthoTitle("Plan")
                    OrthoChoice("Extractions", ORTHO_EXTRACTIONS, current.extraction) { v -> edit { it.copy(extraction = v) } }
                    OrthoChoice("Appliance", ORTHO_APPLIANCES, current.appliance) { v -> edit { it.copy(appliance = v) } }
                    AppField(
                        current.plan, { v -> edit { it.copy(plan = v) } }, "Plan notes (anchorage, sequence, retainers…)",
                        lines = 3, caps = KeyboardCapitalization.Sentences
                    )
                }

                // ── Visits & wires
                AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Visits & wires", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink, modifier = Modifier.weight(1f))
                        SoftButton("Add visit", Icons.Filled.Add, height = 40.dp) { visitEdit = newVisit() }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (visits.isEmpty()) {
                        Text("No visits yet. Add one after each adjustment.", fontSize = 13.sp, color = Brand.Sub)
                    }
                    visits.forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider(color = Brand.Line)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { visitEdit = v }
                                .padding(vertical = 10.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                orthoShort(v.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink,
                                modifier = Modifier.width(60.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(orthoWires(v), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Brand.Ink)
                                val extra = listOf(
                                    if (v.elastics.isNotBlank()) "Elastics: ${v.elastics}" else "",
                                    v.notes
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                if (extra.isNotBlank()) Text(extra, fontSize = 12.sp, color = Brand.Sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (booking != null) {
                        Text(
                            "Next visit · ${fmtDay(booking.day)}" + if (booking.time.isNotBlank()) ", ${booking.time}" else "",
                            fontSize = 13.sp, color = Brand.GoldDeep
                        )
                    } else {
                        Text(
                            "+ Book the next visit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenText,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { bookReason = "Braces adjustment" }.padding(4.dp)
                        )
                    }
                }

                // ── Fee & instalments
                AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    OrthoTitle("Fee & instalments")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppField(
                            feeText, { v ->
                                feeText = orthoMoneyText(v)
                                edit { it.copy(fee = feeText.toDoubleOrNull() ?: 0.0) }
                            }, "Total fee", modifier = Modifier.weight(1f), type = KeyboardType.Decimal, prefix = "₹ "
                        )
                        AppField(
                            downText, { v ->
                                downText = orthoMoneyText(v)
                                edit { it.copy(downPayment = downText.toDoubleOrNull() ?: 0.0) }
                            }, "Down payment", modifier = Modifier.weight(1f), type = KeyboardType.Decimal, prefix = "₹ "
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppField(
                            monthlyText, { v ->
                                monthlyText = orthoMoneyText(v)
                                edit { it.copy(monthly = monthlyText.toDoubleOrNull() ?: 0.0) }
                            }, "Monthly", modifier = Modifier.weight(1f), type = KeyboardType.Decimal, prefix = "₹ "
                        )
                        AppField(
                            beforeText, { v ->
                                beforeText = orthoMoneyText(v)
                                edit { it.copy(paidBefore = beforeText.toDoubleOrNull() ?: 0.0) }
                            }, "Paid earlier", modifier = Modifier.weight(1f), type = KeyboardType.Decimal, prefix = "₹ "
                        )
                    }
                    if (current.fee > 0.0) {
                        Spacer(Modifier.height(14.dp))
                        Row {
                            OrthoMoney("Fee", rs(current.fee), Brand.Ink, Modifier.weight(1f))
                            OrthoMoney("Paid", rs(paid), Brand.GreenText, Modifier.weight(1f))
                            val left = (current.fee - paid).coerceAtLeast(0.0)
                            OrthoMoney("Left", rs(left), if (left > 0.5) Brand.Red else Brand.GreenText, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        val paidCount = schedule.count { it.isPaid }
                        if (paidCount > 0) {
                            Text(
                                if (paidCount == 1) "1 instalment paid ✓" else "$paidCount instalments paid ✓",
                                fontSize = 12.sp, color = Brand.GreenText
                            )
                        }
                        val unpaid = schedule.filter { !it.isPaid }
                        unpaid.take(6).forEach { OrthoInstalmentRow(it) }
                        if (unpaid.size > 6) Text("+ ${unpaid.size - 6} more", fontSize = 12.sp, color = Brand.Sub)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton("Receive payment", Modifier.weight(1.3f), height = 48.dp) {
                                persist()
                                payOpen = true
                            }
                            SoftButton("Remind", null, Modifier.weight(1f), height = 48.dp) { remind() }
                        }
                        if (linked == null) {
                            Text(
                                "+ Add the braces fee to the bill", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = Brand.GreenText,
                                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable { addToBill() }.padding(4.dp)
                            )
                        }
                    }
                }

                // ── Progress photos
                AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    OrthoTitle("Progress photos")
                    if (photos.isEmpty()) {
                        Text(
                            "Photos you add in the Photos tab after the start date appear here.",
                            fontSize = 13.sp, color = Brand.Sub
                        )
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            photos.forEach { p ->
                                Column(Modifier.width(104.dp)) {
                                    OrthoThumb(p.path, Modifier.size(104.dp))
                                    Text("${p.kind} · ${orthoShort(p.date)}", fontSize = 11.sp, color = Brand.Sub, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Text(
                    "Delete this case sheet", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.Red,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { askDelete = true }.padding(6.dp)
                )
            }
        }
    }

    // ───── Pop-ups ─────
    visitEdit?.let { v ->
        OrthoVisitDialog(
            initial = v,
            onDismiss = { visitEdit = null },
            onSave = { updated, bookNext ->
                db.saveOrthoVisit(updated)
                if (v.id == 0L) db.markArrived(patientId)
                if (current.status == ORTHO_PLANNING) edit { it.copy(status = ORTHO_ACTIVE) }
                visitEdit = null
                DataBus.changed()
                notify("Visit saved ✓")
                if (bookNext) bookReason = "Braces adjustment"
            },
            onDelete = if (v.id != 0L) ({
                db.deleteOrthoVisit(v.id)
                visitEdit = null
                DataBus.changed()
                notify("Visit removed")
            }) else null
        )
    }
    if (payOpen) {
        OrthoPayDialog(next?.let { it.amount - it.paid } ?: 0.0, onDismiss = { payOpen = false }) { amount, mode ->
            val id = db.addPayment(patientId, amount, mode)
            db.linkOrthoPayment(current.id, id)
            payOpen = false
            DataBus.changed()
            notify("Payment of ${rs(amount)} saved ✓")
            receiptId = id
        }
    }
    receiptId?.let { id ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Send the receipt to ${patient?.name.orEmpty()}",
            phone = patient?.phone.orEmpty(),
            message = "Hello ${patient?.name?.trim()?.substringBefore(" ").orEmpty()}, here is your payment receipt from $clinicName. Thank you!",
            build = { ctx -> paymentReceiptPdf(ctx, db, id) },
            onDismiss = { receiptId = null }
        )
    }
    if (receiptId == null && visitEdit == null) {
        bookReason?.let { reason ->
            BookingDialog(initial = booking, defaultReason = reason, onDismiss = { bookReason = null }, onCancelBooking = null) { day, time, why ->
                db.saveBooking(patientId, day, time, why)
                bookReason = null
                DataBus.changed()
                notify("Next visit booked ✓")
            }
        }
    }
    if (pickStart) {
        DatePick(epochDayOf(current.startDate), onDismiss = { pickStart = false }) { d ->
            edit { it.copy(startDate = withDay(it.startDate, d)) }
            pickStart = false
        }
    }
    if (askDelete) {
        ConfirmDialog(
            "Delete this case sheet?",
            "The diagnosis, plan, visits and instalment plan will be removed. Payments and treatments stay in the patient's account.",
            confirmText = "Delete",
            onConfirm = {
                askDelete = false
                db.deleteOrthoCase(current.id)
                DataBus.changed()
                notify("Case sheet deleted")
                onClose()
            },
            onDismiss = { askDelete = false }
        )
    }
}

@Composable
private fun OrthoTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun OrthoStep(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Brand.Soft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Brand.Ink) }
}

@Composable
private fun OrthoMoney(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Brand.Sub)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun OrthoChoice(title: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Text(title, fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> ChoiceChip(o, selected == o) { onPick(if (selected == o) "" else o) } }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OrthoMulti(title: String, options: List<String>, csv: String, onChange: (String) -> Unit) {
    val chosen = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    Text(title, fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            ChoiceChip(o, o in chosen) {
                val nextSet = if (o in chosen) chosen - o else chosen + o
                onChange(options.filter { it in nextSet }.joinToString(", "))
            }
        }
    }
}

@Composable
private fun OrthoInstalmentRow(i: OrthoInstalment) {
    val day = epochDayOf(i.due)
    val today = todayEpochDay()
    val (label, color) = when {
        i.isPaid -> "Paid ✓" to Brand.GreenText
        i.paid > 0.0 -> "Part paid" to Brand.GoldDeep
        day < today -> "Overdue" to Brand.Red
        day == today -> "Due today" to Brand.Red
        else -> "Upcoming" to Brand.Faint
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(i.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.Ink)
            Text(fmtDate(i.due), fontSize = 11.sp, color = Brand.Sub)
        }
        Text(rs(i.amount - i.paid), fontSize = 13.sp, color = Brand.Ink)
        Text(label, fontSize = 12.sp, color = color, textAlign = TextAlign.End, modifier = Modifier.width(78.dp))
    }
}

@Composable
private fun OrthoThumb(path: String, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brand.Soft)) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun OrthoVisitDialog(
    initial: OrthoVisit,
    onDismiss: () -> Unit,
    onSave: (OrthoVisit, Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isNew = initial.id == 0L
    val today = todayEpochDay()
    var day by remember { mutableLongStateOf(epochDayOf(initial.date)) }
    var upper by remember { mutableStateOf(initial.upper) }
    var lower by remember { mutableStateOf(initial.lower) }
    var elastics by remember { mutableStateOf(initial.elastics.ifBlank { "None" }) }
    var notes by remember { mutableStateOf(initial.notes) }
    var bookNext by remember { mutableStateOf(isNew) }
    var pickDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (isNew) "Braces visit" else "Edit visit", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
            Text("When", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Today", day == today) { day = today }
                ChoiceChip("Yesterday", day == today - 1) { day = today - 1 }
                val custom = day != today && day != today - 1
                ChoiceChip(if (custom) fmtDay(day) else "Pick date", custom) { pickDate = true }
            }
            OrthoWirePicker("Upper wire", upper) { upper = it }
            OrthoWirePicker("Lower wire", lower) { lower = it }
            Text("Elastics", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ORTHO_ELASTICS.forEach { e -> ChoiceChip(e, elastics == e) { elastics = e } }
            }
            AppField(notes, { notes = it }, "Notes (re-bonded bracket, power chain…)", lines = 2, caps = KeyboardCapitalization.Sentences)
            if (isNew) ToggleRow("Book the next visit", "Choose the date right after saving", bookNext) { bookNext = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 52.dp)
                }
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, height = 52.dp) {
                    onSave(
                        initial.copy(
                            date = withDay(initial.date, day),
                            upper = upper,
                            lower = lower,
                            elastics = if (elastics == "None") "" else elastics,
                            notes = notes.trim()
                        ),
                        isNew && bookNext
                    )
                }
            }
        }
    }
    if (pickDate) {
        DatePick(day, onDismiss = { pickDate = false }) { d ->
            day = d
            pickDate = false
        }
    }
    if (askDelete && onDelete != null) {
        ConfirmDialog(
            "Remove this visit?",
            "The wires and notes for this visit will be removed.",
            confirmText = "Remove",
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false }
        )
    }
}

@Composable
private fun OrthoWirePicker(title: String, value: String, onPick: (String) -> Unit) {
    Text(title, fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ORTHO_WIRES.forEach { w -> ChoiceChip(w, value == w) { onPick(if (value == w) "" else w) } }
    }
}

@Composable
private fun OrthoPayDialog(suggested: Double, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf(numText(suggested)) }
    var mode by remember { mutableStateOf("Cash") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Receive payment", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
            Text("For the braces treatment", fontSize = 13.sp, color = Brand.Sub)
            AppField(amount, { amount = orthoMoneyText(it) }, "Amount", type = KeyboardType.Decimal, prefix = "₹ ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, mode == m) { mode = m } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton(
                    "Save", Modifier.weight(1f), Icons.Filled.Check,
                    enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0, height = 52.dp
                ) { onSave(amount.toDoubleOrNull() ?: 0.0, mode) }
            }
        }
    }
}
