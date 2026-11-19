package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.text.Layout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

// ───────────── Treatment records (v34) ─────────────
// One record per visit, like a job card: that visit's teeth chart, treatments, sittings, notes and money.
// Tables (made the first time they are needed):
//   treatment_records  one row per record: patient, day, notes
//   record_items       which treatment is in which record
//   record_payments    payments received inside a record

private class TxRecord(
    val id: Long,
    val day: Long,
    val notes: String,
    val treatments: List<Treatment>,
    val billed: Double,
    val planned: Double,
    val paid: Double,
    val nextSitting: String,
    val opd: String = ""
) {
    val left: Double get() = (billed - paid).coerceAtLeast(0.0)
    val done: Boolean get() = treatments.isNotEmpty() && treatments.all { it.status == STATUS_DONE }
    val total: Double get() = billed + planned
}

// ───────────── Database ─────────────
private var txReady: Any? = null

private fun ClinicDb.ensureTxRecords() {
    val sql = writableDatabase
    if (txReady === sql) return
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS treatment_records (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "patient_id INTEGER NOT NULL, day INTEGER NOT NULL, notes TEXT, created_at INTEGER)"
    )
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS record_items (treatment_id INTEGER PRIMARY KEY, " +
            "record_id INTEGER NOT NULL, patient_id INTEGER NOT NULL)"
    )
    sql.execSQL(
        "CREATE TABLE IF NOT EXISTS record_payments (payment_id INTEGER PRIMARY KEY, " +
            "record_id INTEGER NOT NULL, patient_id INTEGER NOT NULL)"
    )
    sql.execSQL("CREATE INDEX IF NOT EXISTS idx_records_patient ON treatment_records(patient_id)")
    sql.execSQL("CREATE INDEX IF NOT EXISTS idx_record_items_patient ON record_items(patient_id)")
    sql.execSQL("CREATE INDEX IF NOT EXISTS idx_record_payments_patient ON record_payments(patient_id)")
    txReady = sql
}

/** A record with no notes, no treatments and no payments in it. */
private const val TX_EMPTY = "IFNULL(notes, '') = '' " +
    "AND id NOT IN (SELECT ri.record_id FROM record_items ri JOIN treatments t ON t.id = ri.treatment_id) " +
    "AND id NOT IN (SELECT rp.record_id FROM record_payments rp JOIN payments p ON p.id = rp.payment_id)"

private fun ClinicDb.txRecordOnDay(patientId: Long, day: Long): Long? =
    readableDatabase.rawQuery(
        "SELECT id FROM treatment_records WHERE patient_id = ? AND day = ? ORDER BY id LIMIT 1",
        arrayOf(patientId.toString(), day.toString())
    ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

private fun ClinicDb.newTxRecord(patientId: Long, day: Long): Long {
    val cv = ContentValues().apply {
        put("patient_id", patientId)
        put("day", day)
        put("notes", "")
        put("created_at", System.currentTimeMillis())
    }
    return writableDatabase.insert("treatment_records", null, cv)
}

private fun ClinicDb.linkTxTreatment(treatmentId: Long, recordId: Long, patientId: Long) {
    val cv = ContentValues().apply {
        put("treatment_id", treatmentId)
        put("record_id", recordId)
        put("patient_id", patientId)
    }
    writableDatabase.insertWithOnConflict("record_items", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

private fun ClinicDb.linkTxPayment(paymentId: Long, recordId: Long, patientId: Long) {
    ensureTxRecords()
    val cv = ContentValues().apply {
        put("payment_id", paymentId)
        put("record_id", recordId)
        put("patient_id", patientId)
    }
    writableDatabase.insertWithOnConflict("record_payments", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Treatments that are not in a record yet: new ones, and ones added before records existed. */
private fun ClinicDb.looseTreatments(patientId: Long): List<Treatment> {
    val inRecord = HashSet<Long>()
    readableDatabase.rawQuery(
        "SELECT ri.treatment_id FROM record_items ri JOIN treatment_records r ON r.id = ri.record_id " +
            "WHERE ri.patient_id = ?",
        arrayOf(patientId.toString())
    ).use { c -> while (c.moveToNext()) inRecord.add(c.getLong(0)) }
    return treatmentsOf(patientId).filter { it.id !in inRecord }
}

/** Puts each loose treatment into the record of the day it was added, making that record if needed. */
private fun ClinicDb.syncTxRecords(patientId: Long) {
    ensureTxRecords()
    val loose = looseTreatments(patientId).sortedWith(compareBy<Treatment>({ it.createdAt }, { it.id }))
    if (loose.isEmpty()) return
    val sql = writableDatabase
    sql.beginTransaction()
    try {
        loose.forEach { t ->
            val stamp = if (t.createdAt > 0L) t.createdAt else t.doneAt
            val day = if (stamp > 0L) epochDayOf(stamp).toLong() else todayEpochDay()
            val recordId = txRecordOnDay(patientId, day) ?: newTxRecord(patientId, day)
            linkTxTreatment(t.id, recordId, patientId)
        }
        sql.setTransactionSuccessful()
    } finally {
        sql.endTransaction()
    }
}

/** The record for this patient on that day, made if there is none yet. */
private fun ClinicDb.txRecordForDay(patientId: Long, day: Long): Long {
    ensureTxRecords()
    return txRecordOnDay(patientId, day) ?: newTxRecord(patientId, day)
}

/** Moves a record to another day (its treatments and payments go with it). */
private fun ClinicDb.moveTxRecord(recordId: Long, day: Long) {
    ensureTxRecords()
    val cv = ContentValues().apply { put("day", day) }
    writableDatabase.update("treatment_records", cv, "id = ?", arrayOf(recordId.toString()))
}

/** Puts the treatments added just now into this record. */
private fun ClinicDb.claimLooseTreatments(patientId: Long, recordId: Long) {
    ensureTxRecords()
    looseTreatments(patientId).forEach { linkTxTreatment(it.id, recordId, patientId) }
}

private fun ClinicDb.saveTxNotes(recordId: Long, notes: String) {
    ensureTxRecords()
    val cv = ContentValues().apply { put("notes", notes) }
    writableDatabase.update("treatment_records", cv, "id = ?", arrayOf(recordId.toString()))
}

/** A record opened by mistake and left empty is removed when it is closed. */
private fun ClinicDb.dropTxRecordIfEmpty(recordId: Long) {
    ensureTxRecords()
    writableDatabase.delete("treatment_records", "id = ? AND $TX_EMPTY", arrayOf(recordId.toString()))
}

/** Deletes a record with its treatments and their sittings. Payments stay in the patient's account. */
private fun ClinicDb.deleteTxRecord(patientId: Long, recordId: Long) {
    ensureTxRecords()
    val args = arrayOf(recordId.toString())
    val ids = mutableListOf<Long>()
    readableDatabase.rawQuery("SELECT treatment_id FROM record_items WHERE record_id = ?", args)
        .use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
    val sittings = runCatching { sittingsOf(patientId) }.getOrElse { emptyList() }
    val sql = writableDatabase
    sql.beginTransaction()
    try {
        sittings.filter { it.treatmentId in ids }.forEach { deleteSitting(it.id) }
        ids.forEach { deleteTreatment(it) }
        sql.delete("record_items", "record_id = ?", args)
        sql.delete("record_payments", "record_id = ?", args)
        sql.delete("treatment_records", "id = ?", args)
        sql.setTransactionSuccessful()
    } finally {
        sql.endTransaction()
    }
}

/** All records of a patient, newest first, with what was billed and paid for each. */
private fun ClinicDb.txRecordsOf(patientId: Long): List<TxRecord> {
    syncTxRecords(patientId)
    val args = arrayOf(patientId.toString())
    val rows = mutableListOf<Triple<Long, Long, String>>()
    readableDatabase.rawQuery("SELECT id, day, notes FROM treatment_records WHERE patient_id = ? ORDER BY day, id", args)
        .use { c -> while (c.moveToNext()) rows.add(Triple(c.getLong(0), c.getLong(1), c.getString(2) ?: "")) }
    val recordOfTreatment = HashMap<Long, Long>()
    readableDatabase.rawQuery("SELECT treatment_id, record_id FROM record_items WHERE patient_id = ?", args)
        .use { c -> while (c.moveToNext()) recordOfTreatment[c.getLong(0)] = c.getLong(1) }
    val recordOfPayment = HashMap<Long, Long>()
    readableDatabase.rawQuery("SELECT payment_id, record_id FROM record_payments WHERE patient_id = ?", args)
        .use { c -> while (c.moveToNext()) recordOfPayment[c.getLong(0)] = c.getLong(1) }

    val treatments = treatmentsOf(patientId)
    val payments = paymentsOf(patientId)
    val sittings = runCatching { sittingsOf(patientId) }.getOrElse { emptyList() }
    // Money taken at a sitting belongs to the record of that sitting's treatment
    sittings.forEach { s ->
        val record = recordOfTreatment[s.treatmentId]
        if (s.paymentId > 0L && record != null && s.paymentId !in recordOfPayment) {
            recordOfPayment[s.paymentId] = record
        }
    }

    val ids = rows.map { it.first }.toSet()
    val byRecord = treatments.groupBy { recordOfTreatment[it.id] ?: 0L }
    val billed = HashMap<Long, Double>()
    rows.forEach { (id, _, _) ->
        billed[id] = byRecord[id].orEmpty().filter { it.status != STATUS_PLANNED }.sumOf { it.fee }
    }

    // Payments received inside a record count for that record (up to its total).
    // All other money pays the oldest records first, so the records add up to the patient's balance.
    var loose = 0.0
    val own = HashMap<Long, Double>()
    payments.forEach { p ->
        val record = recordOfPayment[p.id]
        if (record != null && record in ids) {
            own[record] = (own[record] ?: 0.0) + p.amount
        } else {
            loose += p.amount
        }
    }
    val paid = HashMap<Long, Double>()
    rows.forEach { (id, _, _) ->
        val mine = own[id] ?: 0.0
        val kept = mine.coerceAtMost(billed[id] ?: 0.0).coerceAtLeast(0.0)
        paid[id] = kept
        loose += mine - kept
    }
    rows.forEach { (id, _, _) ->
        val need = (billed[id] ?: 0.0) - (paid[id] ?: 0.0)
        if (loose > 0.0 && need > 0.0) {
            val take = minOf(need, loose)
            paid[id] = (paid[id] ?: 0.0) + take
            loose -= take
        }
    }
    if (loose < 0.0) {
        // Refunds bigger than the loose payments come off the newest records
        rows.asReversed().forEach { (id, _, _) ->
            if (loose < 0.0) {
                val back = minOf(paid[id] ?: 0.0, -loose)
                paid[id] = (paid[id] ?: 0.0) - back
                loose += back
            }
        }
    }

    val sittingsOfTreatment = sittings.groupBy { it.treatmentId }
    return rows.map { (id, day, notes) ->
        val list = byRecord[id].orEmpty().sortedWith(compareBy<Treatment>({ it.createdAt }, { it.id }))
        val next = list.filter { it.status != STATUS_DONE }.firstNotNullOfOrNull { t ->
            sittingsOfTreatment[t.id]?.sortedBy { it.position }?.firstOrNull { !it.done }?.name
        }.orEmpty()
        TxRecord(
            id = id, day = day, notes = notes, treatments = list,
            billed = billed[id] ?: 0.0,
            planned = list.filter { it.status == STATUS_PLANNED }.sumOf { it.fee },
            paid = paid[id] ?: 0.0,
            nextSitting = next,
            opd = opdId(patientId, day)
        )
    }.asReversed()
}

// ───────────── Text helpers ─────────────
private fun txDate(day: Long): String = fmtDate(withDay(System.currentTimeMillis(), day))

private fun txTeeth(t: Treatment): String = when (t.teethList.size) {
    0 -> ""
    1 -> "Tooth " + t.teethList[0]
    else -> "Teeth " + t.teethList.joinToString(", ")
}

private fun txSummary(list: List<Treatment>): String = when (list.size) {
    0 -> "No treatments yet"
    1 -> listOf(list[0].name, txTeeth(list[0]).lowercase()).filter { it.isNotBlank() }.joinToString(" · ")
    else -> list.joinToString(", ") { t ->
        if (t.teethList.isEmpty()) t.name else t.name + " (" + t.teethList.joinToString(", ") + ")"
    }
}

// ───────────── In the patient file ─────────────
/** The patient's treatment records, newest first, with the "New treatment record" button. */
@Composable
fun TreatmentRecordsCard(db: ClinicDb, patientId: Long, openNew: Boolean, onOpened: () -> Unit) {
    val version = DataBus.version
    val loaded = remember(version) { runCatching { db.txRecordsOf(patientId) } }
    val records = loaded.getOrNull().orEmpty()
    var openId by remember { mutableStateOf<Long?>(null) }
    var addFirst by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }

    // Opened from an OPD No. search: open the record of that visit once the list is loaded
    LaunchedEffect(records) {
        if (RecordJump.patientId == patientId && RecordJump.day >= 0) {
            records.firstOrNull { it.day == RecordJump.day }?.let { r ->
                addFirst = false
                openId = r.id
            }
            RecordJump.patientId = 0L
            RecordJump.day = -1L
        }
    }

    fun startNew(day: Long) {
        val id = db.txRecordForDay(patientId, day)
        addFirst = records.firstOrNull { it.id == id }?.treatments.isNullOrEmpty()
        openId = id
        DataBus.changed()
    }

    LaunchedEffect(openNew) {
        if (openNew) {
            startNew(todayEpochDay())
            onOpened()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("New treatment record", Icons.Filled.Add, Modifier.weight(1f), height = 52.dp) { startNew(todayEpochDay()) }
            SquareIconButton(Icons.Filled.DateRange, "Record for another day") { pickDay = true }
        }
        val error = loaded.exceptionOrNull()
        if (error != null) {
            AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text("Could not load the treatment records: ${error.message}", fontSize = 13.sp, color = Brand.Red)
            }
        } else if (records.isEmpty()) {
            AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text(
                    "No treatment records yet. Tap \"New treatment record\" when the patient comes in.",
                    fontSize = 13.sp, color = Brand.Sub
                )
            }
        } else {
            (if (showAll) records else records.take(8)).forEach { r ->
                key(r.id) {
                    TxRow(r) {
                        addFirst = false
                        openId = r.id
                    }
                }
            }
            if (records.size > 8) {
                Text(
                    if (showAll) "Show less" else "Show all ${records.size} records",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAll = !showAll }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
        }
    }

    if (pickDay) {
        DatePick(todayEpochDay(), onDismiss = { pickDay = false }) { day ->
            pickDay = false
            startNew(day)
        }
    }
    openId?.let { id ->
        TxDialog(db, patientId, id, addFirst) {
            db.dropTxRecordIfEmpty(id)
            openId = null
            addFirst = false
            DataBus.changed()
        }
    }
}

@Composable
private fun TxRow(r: TxRecord, onOpen: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                txDate(r.day), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink,
                modifier = Modifier.weight(1f)
            )
            TxChip(r)
        }
        Text("OPD ${r.opd}", fontSize = 12.sp, color = Brand.Slate)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                txSummary(r.treatments), fontSize = 13.sp, color = Brand.Sub, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (r.total > 0.0) {
                Spacer(Modifier.width(8.dp))
                Text(rs(r.total), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
            }
        }
        if (r.nextSitting.isNotBlank() || r.left > 0.01) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.nextSitting.isNotBlank()) "Next: ${r.nextSitting}" else "",
                    fontSize = 12.sp, color = Brand.GoldDeep, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (r.left > 0.01) {
                    Text("${rs(r.left)} due", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Brand.Red)
                }
            }
        }
    }
}

@Composable
private fun TxChip(r: TxRecord) {
    when {
        r.day > todayEpochDay() -> Pill("Upcoming", Brand.Soft, Brand.Slate)
        r.done -> Pill("Done", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
        else -> Pill("Open", Brand.GoldSoft, Brand.GoldDeep)
    }
}

/** One record, full screen: its teeth chart, treatments, sittings, notes and money. */
@Composable
private fun TxDialog(db: ClinicDb, patientId: Long, recordId: Long, addFirst: Boolean, onClose: () -> Unit) {
    val notify = LocalNotify.current
    val version = DataBus.version
    val patient = remember(version) { db.getPatient(patientId) }
    val record = remember(version) {
        runCatching { db.txRecordsOf(patientId) }.getOrNull()?.firstOrNull { it.id == recordId }
    }
    val doctors = remember(version) { db.doctorNames() }
    val clinicName = remember(version) { db.clinicInfo().name }
    val balance = remember(version) { accountOf(db.treatmentsOf(patientId), db.paymentsOf(patientId)).balance }
    val statusByTooth = HashMap<String, Int>()
    record?.treatments?.forEach { t ->
        t.teethList.forEach { tooth -> statusByTooth[tooth] = minOf(statusByTooth[tooth] ?: STATUS_DONE, t.status) }
    }

    var adding by remember { mutableStateOf(addFirst) }
    var presetTooth by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Treatment?>(null) }
    var askEdit by remember { mutableStateOf<Treatment?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var receive by remember { mutableStateOf(false) }
    var receiptFor by remember { mutableStateOf<Long?>(null) }
    var pdf by remember { mutableStateOf<String?>(null) }
    var moveDay by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var booking by remember { mutableStateOf(false) }
    val go = LocalGo.current

    fun cycleStatus(t: Treatment) {
        val next = when (t.status) {
            STATUS_PLANNED -> STATUS_ONGOING
            STATUS_ONGOING -> STATUS_DONE
            else -> STATUS_ONGOING
        }
        db.saveTreatment(t.copy(status = next))
        DataBus.changed()
        notify("${t.name}: ${statusLabel(next)} ✓")
        if (next == STATUS_DONE && record?.day == todayEpochDay()) {
            finish = true
        }
    }

    // Tap on a tooth: open the unfinished treatment on it, or add a new treatment for that tooth
    fun openTooth(tooth: String) {
        val onTooth = record?.treatments.orEmpty().filter { tooth in it.teethList }
        val active = onTooth.firstOrNull { it.status != STATUS_DONE }
        if (active != null) {
            editing = active
        } else if (onTooth.isNotEmpty() && record?.done == true) {
            askEdit = onTooth.first()
        } else {
            presetTooth = tooth
            adding = true
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Brand.Bg)) {
            AppTopBar(record?.let { txDate(it.day) } ?: "Treatment record", listOfNotNull(patient?.name, record?.let { "OPD " + it.opd }).joinToString(" · "), onClose) {
                if (record != null) {
                    CircleIconButton(Icons.Filled.DateRange, { moveDay = true }, size = 40.dp)
                    Spacer(Modifier.width(8.dp))
                    TxChip(record)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (record == null) {
                        Text("This record is not there any more.", fontSize = 14.sp, color = Brand.Sub)
                    } else {
                        AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader("Teeth & treatments", Modifier.weight(1f))
                                SoftButton("Add", Icons.Filled.Add, height = 40.dp) {
                                    presetTooth = null
                                    adding = true
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            StatusChart(statusByTooth) { tooth -> openTooth(tooth) }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TxLegendDot(STATUS_PLANNED)
                                Spacer(Modifier.width(16.dp))
                                TxLegendDot(STATUS_ONGOING)
                                Spacer(Modifier.width(16.dp))
                                TxLegendDot(STATUS_DONE)
                            }
                            if (record.treatments.isEmpty()) {
                                Text(
                                    "No treatments yet. Tap a tooth above, or tap \"Add\".", fontSize = 13.sp,
                                    color = Brand.Sub, modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                record.treatments.forEachIndexed { i, t ->
                                    if (i > 0) HorizontalDivider(color = Brand.Line)
                                    key(t.id) {
                                        TxTreatmentRow(
                                            t, doctors[t.doctorId].orEmpty(), locked = record.done,
                                            onOpen = {
                                                if (record.done) {
                                                    askEdit = t
                                                } else {
                                                    editing = t
                                                }
                                            },
                                            onStatus = { cycleStatus(t) }
                                        )
                                    }
                                }
                                Text(
                                    if (record.done) "This visit is finished. Changing a treatment asks you first."
                                    else "Tap a treatment to change it. Tap its status to move it to the next step.",
                                    fontSize = 12.sp, color = Brand.Sub, modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        TreatmentProgress(db, patientId, onlyIds = record.treatments.map { it.id }.toSet())
                        AppCard(Modifier.fillMaxWidth(), onClick = { notesOpen = true }, padding = 18.dp) {
                            Text("Notes", fontSize = 12.sp, color = Brand.Sub)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                record.notes.ifBlank { "Tap to add notes for this visit" }, fontSize = 14.sp,
                                color = if (record.notes.isBlank()) Brand.Sub else Brand.Ink
                            )
                        }
                        AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                            Row {
                                TxMoney("Total", rs(record.billed), Brand.Ink, Modifier.weight(1f))
                                TxMoney("Paid", rs(record.paid), Brand.GreenText, Modifier.weight(1f))
                                TxMoney(
                                    "Left", rs(record.left), if (record.left > 0.01) Brand.Red else Brand.GreenText,
                                    Modifier.weight(1f)
                                )
                            }
                            if (record.planned > 0.0) {
                                Spacer(Modifier.height(8.dp))
                                Text("To do, not billed yet: ${rs(record.planned)}", fontSize = 12.sp, color = Brand.Sub)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GradientButton("Receive payment", Modifier.weight(1.3f), height = 50.dp) { receive = true }
                                SoftButton(
                                    "Bill", DocIcons.Pdf, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Ink, height = 50.dp
                                ) { pdf = "bill" }
                            }
                            if (record.planned > 0.0) {
                                Spacer(Modifier.height(10.dp))
                                SoftButton(
                                    "Send estimate (${rs(record.planned)})", DocIcons.Pdf, Modifier.fillMaxWidth(),
                                    bg = Brand.Soft, fg = Brand.Ink
                                ) { pdf = "estimate" }
                            }
                        }
                        if (record.day == todayEpochDay() && record.treatments.isNotEmpty()) {
                            GradientButton("Finish visit", Modifier.fillMaxWidth(), Icons.Filled.Check, height = 52.dp) { finish = true }
                        }
                        SoftButton(
                            "Delete this record", Icons.Filled.Delete, Modifier.fillMaxWidth(),
                            bg = Brand.RedSoft, fg = Brand.Red
                        ) { askDelete = true }
                    }
                }
            }
        }
    }

    // ───── Pop-ups ─────
    if (adding || editing != null) {
        TreatmentDialog(
            db = db,
            patientId = patientId,
            initial = editing,
            presetTooth = presetTooth,
            defaultStatus = if ((record?.day ?: 0L) > todayEpochDay()) STATUS_PLANNED else STATUS_ONGOING,
            onDismiss = {
                adding = false
                editing = null
                presetTooth = null
            },
            onSaved = { msg ->
                db.claimLooseTreatments(patientId, recordId)
                val doneBefore = record?.treatments?.count { it.status == STATUS_DONE } ?: 0
                val now = runCatching { db.txRecordsOf(patientId).firstOrNull { it.id == recordId } }.getOrNull()
                if (now != null && now.day == todayEpochDay() && now.treatments.count { it.status == STATUS_DONE } > doneBefore) {
                    finish = true
                }
                adding = false
                editing = null
                presetTooth = null
                DataBus.changed()
                notify(msg)
            }
        )
    }
    askEdit?.let { t ->
        ConfirmDialog(
            "Change a finished visit?",
            "Every treatment in this record is done. Change it only to correct a mistake.",
            confirmText = "Change",
            onConfirm = {
                askEdit = null
                editing = t
            },
            onDismiss = { askEdit = null }
        )
    }
    if (finish && record != null) {
        FinishVisitSheet(
            patientName = patient?.name.orEmpty(),
            due = if (record.left > 0.01) record.left else balance,
            onDismiss = { finish = false },
            onPay = {
                finish = false
                receive = true
            },
            onRx = {
                finish = false
                go(Screen.RxForm(patientId, 0))
                onClose()
            },
            onBook = {
                finish = false
                booking = true
            },
            onBill = {
                finish = false
                pdf = "bill"
            }
        )
    }
    if (booking) {
        val existing = remember { db.nextBooking(patientId) }
        BookingDialog(initial = existing, defaultReason = "Check-up", onDismiss = { booking = false }, onCancelBooking = null) { day, time, why ->
            db.saveBooking(patientId, day, time, why)
            booking = false
            DataBus.changed()
            notify("Next visit booked ✓")
        }
    }
    if (moveDay && record != null) {
        DatePick(record.day, onDismiss = { moveDay = false }) { day ->
            moveDay = false
            db.moveTxRecord(recordId, day)
            DataBus.changed()
            notify("Record moved to ${txDate(day)} ✓")
        }
    }
    if (askDelete && record != null) {
        val date = txDate(record.day)
        val what = when (record.treatments.size) {
            0 -> "The visit on $date"
            1 -> "The visit on $date and its treatment"
            else -> "The visit on $date and its ${record.treatments.size} treatments"
        }
        ConfirmDialog(
            "Delete this record?",
            "$what will be removed from the patient file and the bill. Payments stay in the patient's account.",
            confirmText = "Delete",
            onConfirm = {
                askDelete = false
                db.deleteTxRecord(patientId, recordId)
                DataBus.changed()
                notify("Record deleted")
                onClose()
            },
            onDismiss = { askDelete = false }
        )
    }
    if (notesOpen && record != null) {
        TxNotesDialog(record.notes, onDismiss = { notesOpen = false }) { text ->
            db.saveTxNotes(recordId, text)
            notesOpen = false
            DataBus.changed()
            notify("Notes saved ✓")
        }
    }
    if (receive && record != null) {
        ReceivePaymentDialog(
            balance = if (record.left > 0.01) record.left else balance,
            onDismiss = { receive = false }
        ) { amount, mode ->
            val id = db.addPayment(patientId, amount, mode)
            db.linkTxPayment(id, recordId, patientId)
            receive = false
            DataBus.changed()
            notify("Payment of ${rs(amount)} received ✓")
            receiptFor = id
        }
    }
    val p = patient ?: return
    val first = p.name.trim().substringBefore(" ")
    receiptFor?.let { id ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Send the receipt to ${p.name}",
            phone = p.phone,
            message = "Hello $first, here is your payment receipt from $clinicName. Thank you!",
            build = { ctx -> paymentReceiptPdf(ctx, db, id) },
            onDismiss = { receiptFor = null }
        )
    }
    pdf?.let { kind ->
        val estimate = kind == "estimate"
        PdfActionsDialog(
            title = if (estimate) "Treatment estimate" else "Bill for this visit",
            subtitle = record?.let { txDate(it.day) }.orEmpty(),
            phone = p.phone,
            message = if (estimate) "Hello $first, here is the estimate for your dental treatment from $clinicName."
            else "Hello $first, here is your bill from $clinicName. Thank you!",
            build = { ctx ->
                if (estimate) txEstimatePdf(ctx, db, patientId, recordId) else txBillPdf(ctx, db, patientId, recordId)
            },
            onDismiss = { pdf = null }
        )
    }
}

@Composable
private fun TxTreatmentRow(t: Treatment, doctor: String, locked: Boolean, onOpen: () -> Unit, onStatus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(txTeeth(t), doctor).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.notes.isNotBlank()) Text(t.notes, fontSize = 12.sp, color = Brand.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (t.fee > 0) rs(t.fee) else "–", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !locked, onClick = onStatus)) {
                Pill(statusLabel(t.status), statusSoft(t.status), statusText(t.status))
            }
        }
    }
}

@Composable
private fun TxLegendDot(status: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(status)))
        Spacer(Modifier.width(5.dp))
        Text(statusLabel(status), fontSize = 12.sp, color = Brand.Sub)
    }
}

@Composable
private fun TxMoney(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Brand.Sub)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TxNotesDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Text("Notes for this visit", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text("For example: crown fitted, bite checked", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(14.dp))
            AppField(text, { text = it }, "Notes", lines = 4)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, height = 52.dp) { onSave(text.trim()) }
            }
        }
    }
}

// ───────────── Bill and estimate for one record ─────────────
private fun txPatientRows(p: Patient) = listOf(
    "Name" to p.name,
    "Age / Sex" to listOf(if (p.age.isNotBlank()) "${p.age} yrs" else "", p.gender).filter { it.isNotBlank() }.joinToString(" / "),
    "Mobile" to p.phone,
    "Patient ID" to patientCode(p.id)
)

private fun txTreatmentText(t: Treatment, doctors: Map<Long, String>): String {
    val doctor = doctors[t.doctorId].orEmpty()
    return if (doctor.isBlank()) t.name else "${t.name}\n$doctor"
}

private fun txConsultants(list: List<Treatment>, doctors: Map<Long, String>, db: ClinicDb): String =
    list.mapNotNull { doctors[it.doctorId]?.takeIf { n -> n.isNotBlank() } }.distinct().joinToString(", ")
        .ifBlank { db.mainDoctor()?.name.orEmpty() }

/** Bill for one treatment record (one visit). */
private fun txBillPdf(context: Context, db: ClinicDb, patientId: Long, recordId: Long): File {
    val patient = db.getPatient(patientId) ?: error("Patient not found")
    val record = db.txRecordsOf(patientId).firstOrNull { it.id == recordId } ?: error("Record not found")
    val billed = record.treatments.filter { it.status != STATUS_PLANNED }
    val doctors = db.doctorNames()
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val status = when {
        record.billed <= 0.0 -> "Nothing billed yet"
        record.left > 0.01 -> "Balance due"
        else -> "Paid in full"
    }
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("INVOICE", "")
    pdf.partyBox(
        "PATIENT DETAILS", txPatientRows(patient),
        "INVOICE DETAILS", listOf(
            "Invoice No." to docNo("INV-", patient.id) + "-" + record.id,
            "Visit date" to txDate(record.day),
            "Consultant" to txConsultants(billed, doctors, db),
            "OPD No." to record.opd
        )
    )
    val rows = if (billed.isEmpty()) {
        listOf(
            listOf(
                PdfCell("–", 0.07f, 9.5f, PdfSub), PdfCell("No billed treatments yet", 0.43f, 9.5f, PdfSub),
                PdfCell("–", 0.13f, 9.5f), PdfCell("–", 0.17f, 9f), PdfCell(rupees2(0.0), 0.2f, 9.5f, align = end)
            )
        )
    } else {
        billed.mapIndexed { i, t ->
            listOf(
                PdfCell("${i + 1}", 0.07f, 9.5f, PdfSub),
                PdfCell(txTreatmentText(t, doctors), 0.43f, 9.5f, PdfInk, 1),
                PdfCell(t.teethList.joinToString(", ").ifBlank { "–" }, 0.13f, 9.5f),
                PdfCell(fmtDate(if (t.doneAt > 0L) t.doneAt else t.createdAt), 0.17f, 9f, PdfSub),
                PdfCell(rupees2(t.fee), 0.2f, 9.5f, align = end)
            )
        }
    }
    pdf.table(
        listOf(
            PdfCell("Sr.", 0.07f, 9f, PdfSub, 1),
            PdfCell("Treatment / Procedure", 0.43f, 9f, PdfSub, 1),
            PdfCell("Tooth", 0.13f, 9f, PdfSub, 1),
            PdfCell("Date", 0.17f, 9f, PdfSub, 1),
            PdfCell("Amount", 0.2f, 9f, PdfSub, 1, end)
        ),
        rows
    )
    pdf.summary(
        "AMOUNT IN WORDS", amountInWords(record.billed), "",
        listOf(
            SummaryLine("Total Amount", rupees2(record.billed)),
            SummaryLine("Amount Paid", rupees2(record.paid), color = PdfGreen),
            SummaryLine(
                "Balance Due", rupees2(record.left),
                strong = true, color = if (record.left > 0.01) PdfRed else PdfGreen
            )
        )
    )
    pdf.notes(listOf("Bill for the visit on ${txDate(record.day)}.", "This is a computer-generated invoice."))
    pdf.signature()
    return pdf.save(pdfFile(context, "Bill", patient.name))
}

/** Estimate for the planned (To do) treatments of one record. */
private fun txEstimatePdf(context: Context, db: ClinicDb, patientId: Long, recordId: Long): File {
    val patient = db.getPatient(patientId) ?: error("Patient not found")
    val record = db.txRecordsOf(patientId).firstOrNull { it.id == recordId } ?: error("Record not found")
    val planned = record.treatments.filter { it.status == STATUS_PLANNED }
    val doctors = db.doctorNames()
    val total = planned.sumOf { it.fee }
    val now = System.currentTimeMillis()
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("TREATMENT ESTIMATE", "")
    pdf.partyBox(
        "PATIENT DETAILS", txPatientRows(patient),
        "ESTIMATE DETAILS", listOf(
            "Estimate No." to docNo("EST-", patient.id) + "-" + record.id,
            "Date" to fmtDate(now),
            "Valid till" to fmtDate(now + 30L * 24 * 60 * 60 * 1000),
            "Consultant" to txConsultants(planned, doctors, db)
        )
    )
    val rows = if (planned.isEmpty()) {
        listOf(
            listOf(
                PdfCell("–", 0.07f, 9.5f, PdfSub), PdfCell("No planned treatments", 0.53f, 9.5f, PdfSub),
                PdfCell("–", 0.18f, 9.5f), PdfCell(rupees2(0.0), 0.22f, 9.5f, align = end)
            )
        )
    } else {
        planned.mapIndexed { i, t ->
            listOf(
                PdfCell("${i + 1}", 0.07f, 9.5f, PdfSub),
                PdfCell(txTreatmentText(t, doctors), 0.53f, 9.5f, PdfInk, 1),
                PdfCell(t.teethList.joinToString(", ").ifBlank { "–" }, 0.18f, 9.5f),
                PdfCell(rupees2(t.fee), 0.22f, 9.5f, align = end)
            )
        }
    }
    pdf.table(
        listOf(
            PdfCell("Sr.", 0.07f, 9f, PdfSub, 1),
            PdfCell("Planned Treatment", 0.53f, 9f, PdfSub, 1),
            PdfCell("Tooth", 0.18f, 9f, PdfSub, 1),
            PdfCell("Amount", 0.22f, 9f, PdfSub, 1, end)
        ),
        rows
    )
    pdf.summary(
        "ESTIMATED AMOUNT IN WORDS", amountInWords(total), "",
        listOf(SummaryLine("Estimated Total", rupees2(total), strong = true, color = PdfGreen))
    )
    pdf.notes(
        listOf(
            "This is an estimate. The final amount may change if the treatment needed changes.",
            "Valid for 30 days from the date above."
        )
    )
    pdf.signature()
    return pdf.save(pdfFile(context, "Estimate", patient.name))
}
