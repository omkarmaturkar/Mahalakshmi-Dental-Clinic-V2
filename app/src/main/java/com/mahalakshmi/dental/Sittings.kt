@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.ContentValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────── Treatments done over several sittings ─────────────
data class Sitting(
    val id: Long = 0,
    val treatmentId: Long,
    val patientId: Long,
    val position: Int,
    val name: String,
    val fee: Double = 0.0,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    val paymentId: Long = 0L,
    val notes: String = ""
)

/** The usual sittings for common treatments. null = normally done in one visit. */
fun sittingTemplate(name: String): List<String>? {
    val n = name.lowercase()
    return when {
        "root canal" in n || "rct" in n -> listOf("Opening the tooth", "Cleaning the canals", "Filling the canals", "Crown")
        "implant" in n -> listOf("Implant placement", "Healing check", "Abutment", "Crown")
        "denture" in n -> listOf("Impressions", "Bite record", "Trial fitting", "Denture fitting")
        "crown" in n || "cap" in n || "bridge" in n -> listOf("Tooth preparation", "Crown fitting")
        else -> null
    }
}

// ───────────── Database ─────────────
private var sittingsReady: Any? = null

private fun ClinicDb.ensureSittings() {
    val sql = writableDatabase
    if (sittingsReady === sql) return
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS sittings (id INTEGER PRIMARY KEY AUTOINCREMENT, treatment_id INTEGER NOT NULL, " +
            "patient_id INTEGER NOT NULL, position INTEGER DEFAULT 0, name TEXT, fee REAL DEFAULT 0, done INTEGER DEFAULT 0, " +
            "done_at INTEGER DEFAULT 0, payment_id INTEGER DEFAULT 0, notes TEXT)"
    )
    sql.execSQL("CREATE INDEX IF NOT EXISTS idx_sitting_treatment ON sittings(treatment_id)")
    sittingsReady = sql
}

fun ClinicDb.sittingsOf(patientId: Long): List<Sitting> {
    ensureSittings()
    val list = mutableListOf<Sitting>()
    readableDatabase.rawQuery(
        "SELECT id, treatment_id, patient_id, position, name, fee, done, done_at, payment_id, notes FROM sittings " +
            "WHERE patient_id = ? AND treatment_id IN (SELECT id FROM treatments) ORDER BY treatment_id, position, id",
        arrayOf(patientId.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(
                Sitting(
                    id = c.getLong(0), treatmentId = c.getLong(1), patientId = c.getLong(2), position = c.getInt(3),
                    name = c.getString(4) ?: "", fee = c.getDouble(5), done = c.getInt(6) == 1, doneAt = c.getLong(7),
                    paymentId = c.getLong(8), notes = c.getString(9) ?: ""
                )
            )
        }
    }
    return list
}

fun ClinicDb.addSittings(t: Treatment, names: List<String>) {
    ensureSittings()
    val start = readableDatabase.rawQuery(
        "SELECT IFNULL(MAX(position), -1) FROM sittings WHERE treatment_id = ?", arrayOf(t.id.toString())
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 } + 1
    names.forEachIndexed { i, n ->
        val cv = ContentValues().apply {
            put("treatment_id", t.id)
            put("patient_id", t.patientId)
            put("position", start + i)
            put("name", n)
            put("fee", 0.0)
            put("done", 0)
            put("done_at", 0L)
            put("payment_id", 0L)
            put("notes", "")
        }
        writableDatabase.insert("sittings", null, cv)
    }
}

fun ClinicDb.saveSitting(s: Sitting) {
    ensureSittings()
    val cv = ContentValues().apply {
        put("treatment_id", s.treatmentId)
        put("patient_id", s.patientId)
        put("position", s.position)
        put("name", s.name)
        put("fee", s.fee)
        put("done", if (s.done) 1 else 0)
        put("done_at", s.doneAt)
        put("payment_id", s.paymentId)
        put("notes", s.notes)
    }
    if (s.id == 0L) {
        writableDatabase.insert("sittings", null, cv)
    } else {
        writableDatabase.update("sittings", cv, "id = ?", arrayOf(s.id.toString()))
    }
}

fun ClinicDb.deleteSitting(id: Long) {
    ensureSittings()
    writableDatabase.delete("sittings", "id = ?", arrayOf(id.toString()))
}

/** Keeps the treatment's status in step with its sittings: first done → In progress, all done → Done. */
fun ClinicDb.syncTreatmentWithSittings(treatmentId: Long, patientId: Long) {
    val t = treatmentsOf(patientId).firstOrNull { it.id == treatmentId } ?: return
    val list = sittingsOf(patientId).filter { it.treatmentId == treatmentId }
    if (list.isEmpty()) return
    val doneCount = list.count { it.done }
    val status = when {
        doneCount == list.size -> STATUS_DONE
        doneCount > 0 -> STATUS_ONGOING
        else -> t.status.coerceAtMost(STATUS_ONGOING)
    }
    if (status != t.status) {
        val doneAt = if (status == STATUS_DONE) (list.maxOfOrNull { it.doneAt } ?: 0L) else 0L
        saveTreatment(t.copy(status = status, doneAt = doneAt))
    }
}

/** Marks a sitting done and records the payment taken at it (if any). Returns the new payment id, or 0. */
fun ClinicDb.markSittingDone(s: Sitting, day: Long, amount: Double, mode: String): Long {
    var paymentId = s.paymentId
    var newPayment = 0L
    if (amount > 0.0) {
        newPayment = addPayment(s.patientId, amount, mode)
        if (day != todayEpochDay()) updatePayment(newPayment, amount, mode, withDay(System.currentTimeMillis(), day))
        paymentId = newPayment
    }
    saveSitting(s.copy(done = true, doneAt = withDay(System.currentTimeMillis(), day), paymentId = paymentId))
    syncTreatmentWithSittings(s.treatmentId, s.patientId)
    return newPayment
}

private fun shortDate(t: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))

private fun toothText(t: Treatment): String =
    if (t.teethList.isEmpty()) "" else "Tooth " + t.teethList.joinToString(", ")

// ───────────── In the patient file ─────────────
/** Treatment progress: one card per treatment that is done over several sittings. */
@Composable
fun TreatmentProgress(db: ClinicDb, patientId: Long) {
    val notify = LocalNotify.current
    val version = DataBus.version
    val patient = remember(version) { db.getPatient(patientId) }
    val treatments = remember(version) { db.treatmentsOf(patientId) }
    val sittings = remember(version) { runCatching { db.sittingsOf(patientId) }.getOrElse { emptyList() } }
    val payments = remember(version) { db.paymentsOf(patientId).associateBy { it.id } }
    val booking = remember(version) { db.nextBooking(patientId) }
    val doctors = remember(version) { db.doctorNames() }
    val clinicName = remember(version) { db.clinicInfo().name }
    var open by remember { mutableStateOf<Long?>(null) }
    var finishing by remember { mutableStateOf<Pair<Treatment, Sitting>?>(null) }
    var editing by remember { mutableStateOf<Pair<Treatment, Sitting>?>(null) }
    var bookReason by remember { mutableStateOf<String?>(null) }
    var receiptId by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }

    if (patient == null) return
    val byTreatment = sittings.groupBy { it.treatmentId }
    fun allDone(t: Treatment) = byTreatment[t.id].orEmpty().all { it.done }
    val tracked = treatments.filter { byTreatment.containsKey(it.id) }
        .sortedWith(compareBy<Treatment>({ if (allDone(it)) 1 else 0 }, { -it.createdAt }))
    val suggestions = treatments.filter { it.status != STATUS_DONE && !byTreatment.containsKey(it.id) && sittingTemplate(it.name) != null }
    val others = treatments.filter { it.status != STATUS_DONE && !byTreatment.containsKey(it.id) && sittingTemplate(it.name) == null }
    val openId = open ?: tracked.firstOrNull { !allDone(it) }?.id

    if (tracked.isNotEmpty() || suggestions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tracked.forEach { t ->
                val list = byTreatment[t.id].orEmpty()
                if (t.id == openId) {
                    SittingsCard(
                        t, list, doctors[t.doctorId].orEmpty(), payments, booking,
                        onDone = { s -> finishing = t to s },
                        onEdit = { s -> editing = t to s },
                        onBook = { s -> bookReason = "${t.name}: ${s.name}" },
                        onAdd = {
                            editing = t to Sitting(
                                treatmentId = t.id, patientId = patientId,
                                position = (list.maxOfOrNull { it.position } ?: -1) + 1, name = ""
                            )
                        }
                    )
                } else {
                    SittingsSummary(t, list) { open = t.id }
                }
            }
            suggestions.forEach { t ->
                val steps = sittingTemplate(t.name).orEmpty()
                SuggestionRow(t, steps.size) {
                    db.addSittings(t, steps)
                    open = t.id
                    DataBus.changed()
                    notify("${t.name}: ${steps.size} sittings added ✓")
                }
            }
            if (others.isNotEmpty()) {
                Text(
                    "+ Track another treatment in sittings", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = Brand.GreenText,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picking = true }.padding(6.dp)
                )
            }
        }
    }

    // ───── Pop-ups ─────
    finishing?.let { (t, s) ->
        val after = byTreatment[t.id].orEmpty().sortedBy { it.position }.firstOrNull { !it.done && it.id != s.id }
        SittingDoneDialog(
            t, s, hasNext = after != null, alreadyPaid = payments[s.paymentId]?.amount ?: 0.0,
            onDismiss = { finishing = null }
        ) { day, amount, mode, bookNext ->
            val paymentId = db.markSittingDone(s, day, amount, mode)
            finishing = null
            DataBus.changed()
            notify("${s.name} done ✓")
            if (bookNext && after != null) bookReason = "${t.name}: ${after.name}"
            if (paymentId > 0L) receiptId = paymentId
        }
    }
    editing?.let { (t, s) ->
        SittingEditDialog(
            initial = s,
            onDismiss = { editing = null },
            onSave = { updated ->
                db.saveSitting(updated)
                editing = null
                DataBus.changed()
                notify(if (s.id == 0L) "Sitting added ✓" else "Sitting updated ✓")
            },
            onUndo = if (s.done) ({
                db.saveSitting(s.copy(done = false, doneAt = 0L))
                db.syncTreatmentWithSittings(t.id, patientId)
                editing = null
                DataBus.changed()
                notify("Marked as not done")
            }) else null,
            onDelete = if (s.id != 0L) ({
                db.deleteSitting(s.id)
                db.syncTreatmentWithSittings(t.id, patientId)
                editing = null
                DataBus.changed()
                notify("Sitting removed")
            }) else null
        )
    }
    receiptId?.let { id ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Send the receipt to ${patient.name}",
            phone = patient.phone,
            message = "Hello ${patient.name.trim().substringBefore(" ")}, here is your payment receipt from $clinicName. Thank you!",
            build = { ctx -> paymentReceiptPdf(ctx, db, id) },
            onDismiss = { receiptId = null }
        )
    }
    if (receiptId == null) {
        bookReason?.let { reason ->
            BookingDialog(initial = booking, defaultReason = reason, onDismiss = { bookReason = null }, onCancelBooking = null) { day, time, why ->
                db.saveBooking(patientId, day, time, why)
                bookReason = null
                DataBus.changed()
                notify("Next sitting booked ✓")
            }
        }
    }
    if (picking) {
        Dialog(onDismissRequest = { picking = false }) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brand.Surface).padding(22.dp)
            ) {
                Text("Track in sittings", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
                Text("Choose the treatment. You can rename the sittings afterwards.", fontSize = 13.sp, color = Brand.Sub)
                Spacer(Modifier.height(10.dp))
                others.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            db.addSittings(t, listOf("Sitting 1", "Sitting 2"))
                            picking = false
                            open = t.id
                            DataBus.changed()
                            notify("${t.name}: sittings added ✓")
                        }.padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Brand.Ink)
                            val tooth = toothText(t)
                            if (tooth.isNotBlank()) Text(tooth, fontSize = 12.sp, color = Brand.Sub)
                        }
                        Icon(Icons.Filled.Add, null, tint = Brand.GreenDeep)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SoftButton("Close", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub) { picking = false }
            }
        }
    }
}

@Composable
private fun SittingsCard(
    t: Treatment,
    list: List<Sitting>,
    doctor: String,
    payments: Map<Long, Payment>,
    booking: Booking?,
    onDone: (Sitting) -> Unit,
    onEdit: (Sitting) -> Unit,
    onBook: (Sitting) -> Unit,
    onAdd: () -> Unit
) {
    val doneCount = list.count { it.done }
    val next = list.firstOrNull { !it.done }
    val nextNumber = if (next != null) list.indexOf(next) + 1 else list.size
    val paid = list.sumOf { s -> payments[s.paymentId]?.amount ?: 0.0 }
    AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(toothText(t), doctor).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (next == null) {
                Pill("All done", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
            } else {
                Pill("Sitting $nextNumber of ${list.size}", Brand.GoldSoft, Brand.GoldDeep)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Brand.Soft)) {
            Box(
                Modifier.fillMaxWidth(if (list.isEmpty()) 0f else doneCount / list.size.toFloat()).fillMaxHeight()
                    .clip(RoundedCornerShape(50)).background(Brand.GreenDeep)
            )
        }
        Spacer(Modifier.height(14.dp))
        list.forEachIndexed { i, s ->
            SittingRow(
                number = i + 1, s = s, isNext = s.id == next?.id, isLast = i == list.lastIndex,
                paidAmount = payments[s.paymentId]?.amount,
                booking = if (s.id == next?.id) booking else null
            ) { onEdit(s) }
        }
        Text(
            "+ Add sitting", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenText,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onAdd).padding(6.dp)
        )
        if (t.fee > 0) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Brand.Line)
            Spacer(Modifier.height(10.dp))
            Row {
                MoneyCell("Fee", rs(t.fee), Brand.Ink, Modifier.weight(1f))
                MoneyCell("Paid", rs(paid), Brand.GreenText, Modifier.weight(1f))
                val left = (t.fee - paid).coerceAtLeast(0.0)
                MoneyCell("Left", rs(left), if (left > 0.01) Brand.Red else Brand.GreenText, Modifier.weight(1f))
            }
        }
        if (next != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Mark sitting $nextNumber done", Modifier.weight(1.4f), height = 48.dp) { onDone(next) }
                SoftButton(if (booking != null) "Change date" else "Book next", null, Modifier.weight(1f), height = 48.dp) { onBook(next) }
            }
        }
    }
}

@Composable
private fun SittingRow(
    number: Int,
    s: Sitting,
    isNext: Boolean,
    isLast: Boolean,
    paidAmount: Double?,
    booking: Booking?,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Column(Modifier.width(26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                s.done -> Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Brand.GreenDeep),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                isNext -> Box(
                    Modifier.size(24.dp).clip(CircleShape).border(2.dp, Brand.Gold, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Brand.GoldDeep) }
                else -> Box(
                    Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, Brand.Faint, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$number", fontSize = 12.sp, color = Brand.Faint) }
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(if (s.done) Brand.GreenLine else Brand.Line))
        }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f).padding(bottom = if (isLast) 2.dp else 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = if (s.done || isNext) Brand.Ink else Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = when {
                    s.done -> shortDate(s.doneAt) + if (s.notes.isNotBlank()) " · ${s.notes}" else ""
                    isNext && booking != null -> "Next · ${fmtDay(booking.day)}" + if (booking.time.isNotBlank()) ", ${booking.time}" else ""
                    isNext -> "Not booked yet"
                    else -> s.notes
                }
                if (sub.isNotBlank()) {
                    Text(
                        sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = when {
                            isNext -> Brand.GoldDeep
                            s.done -> Brand.Sub
                            else -> Brand.Faint
                        }
                    )
                }
            }
            val isPaid = paidAmount != null && paidAmount > 0.0
            val amountText = when {
                isPaid -> rs(paidAmount ?: 0.0) + "\npaid"
                s.fee > 0.0 -> rs(s.fee)
                else -> ""
            }
            if (amountText.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    amountText, fontSize = 12.sp, textAlign = TextAlign.End, lineHeight = 15.sp,
                    color = if (isPaid) Brand.GreenText else Brand.Sub
                )
            }
        }
    }
}

@Composable
private fun MoneyCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Brand.Sub)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun SittingsSummary(t: Treatment, list: List<Sitting>, onOpen: () -> Unit) {
    val done = list.count { it.done }
    val next = list.firstOrNull { !it.done }
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val tooth = if (t.teethList.isEmpty()) "" else " · tooth " + t.teethList.joinToString(", ")
                Text(t.name + tooth, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (next != null) "Next: ${next.name}" else "All sittings done ✓", fontSize = 12.sp, color = Brand.Sub)
            }
            Spacer(Modifier.width(8.dp))
            Pill(
                "$done of ${list.size}",
                if (next == null) Brand.GreenSoft else Brand.Soft,
                if (next == null) Brand.GreenText else Brand.Sub
            )
        }
    }
}

@Composable
private fun SuggestionRow(t: Treatment, count: Int, onTrack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.GoldSoft).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val tooth = if (t.teethList.isEmpty()) "" else " · tooth " + t.teethList.joinToString(", ")
            Text(t.name + tooth, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Usually done in $count sittings. Track each visit?", fontSize = 12.sp, color = Brand.GoldDeep)
        }
        Spacer(Modifier.width(8.dp))
        SoftButton("Track", null, height = 40.dp, bg = Brand.Surface, fg = Brand.GoldDeep, onClick = onTrack)
    }
}

@Composable
private fun SittingDoneDialog(
    t: Treatment,
    s: Sitting,
    hasNext: Boolean,
    alreadyPaid: Double,
    onDismiss: () -> Unit,
    onSave: (day: Long, amount: Double, mode: String, bookNext: Boolean) -> Unit
) {
    val today = todayEpochDay()
    var day by remember { mutableLongStateOf(today) }
    // Money already taken at this sitting (it was marked done before): don't suggest the fee again
    var amount by remember { mutableStateOf(if (alreadyPaid > 0.0) "" else numText(s.fee)) }
    var mode by remember { mutableStateOf("Cash") }
    var bookNext by remember { mutableStateOf(hasNext) }
    var pickDate by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White)
            }
            Text("Sitting done", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text("${t.name}: ${s.name}", fontSize = 14.sp, color = Brand.Sub)
            Text("When", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Today", day == today) { day = today }
                ChoiceChip("Yesterday", day == today - 1) { day = today - 1 }
                val custom = day != today && day != today - 1
                ChoiceChip(if (custom) fmtDay(day) else "Pick date", custom) { pickDate = true }
            }
            Text("Payment taken at this sitting (optional)", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
            if (alreadyPaid > 0.0) {
                Pill("${rs(alreadyPaid)} already received at this sitting", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
            }
            AppField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, mode == m) { mode = m } }
            }
            if (hasNext) {
                ToggleRow("Book the next sitting", "Choose the date right after saving", bookNext) { bookNext = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, height = 52.dp) {
                    onSave(day, amount.toDoubleOrNull() ?: 0.0, mode, bookNext)
                }
            }
        }
    }
    if (pickDate) DatePick(day, onDismiss = { pickDate = false }) { d -> day = d }
}

@Composable
private fun SittingEditDialog(
    initial: Sitting,
    onDismiss: () -> Unit,
    onSave: (Sitting) -> Unit,
    onUndo: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial.name) }
    var fee by remember { mutableStateOf(numText(initial.fee)) }
    var notes by remember { mutableStateOf(initial.notes) }
    var askDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial.id == 0L) "Add sitting" else "Edit sitting",
                fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink
            )
            if (initial.done) Pill("Done on ${fmtDate(initial.doneAt)}", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
            AppField(name, { name = it }, "What happens at this sitting *", caps = KeyboardCapitalization.Sentences)
            AppField(
                fee, { fee = it.filter { c -> c.isDigit() || c == '.' } }, "Fee for this sitting (optional)",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            AppField(notes, { notes = it }, "Notes (optional)", lines = 2)
            if (onUndo != null) {
                SoftButton("Mark as not done", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink, onClick = onUndo)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 52.dp)
                }
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank(), height = 52.dp) {
                    onSave(initial.copy(name = name.trim(), fee = fee.toDoubleOrNull() ?: 0.0, notes = notes.trim()))
                }
            }
        }
    }
    if (askDelete && onDelete != null) {
        ConfirmDialog(
            "Remove this sitting?",
            "\"${initial.name}\" will be removed from the treatment steps. Payments are not changed.",
            confirmText = "Remove",
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false }
        )
    }
}
