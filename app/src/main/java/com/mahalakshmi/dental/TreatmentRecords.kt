package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.text.Layout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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

// ───────────── Treatment records (v31) ─────────────
// One record per visit, like a job card: that visit's treatments, sittings, notes and money.
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
    val nextSitting: String
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

/** Today's record for this patient, made if there is none yet. */
private fun ClinicDb.todayTxRecord(patientId: Long): Long {
    ensureTxRecords()
    val today = todayEpochDay()
    return txRecordOnDay(patientId, today) ?: newTxRecord(patientId, today)
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
            nextSitting = next
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

    fun startNew() {
        val id = db.todayTxRecord(patientId)
        addFirst = records.firstOrNull { it.id == id }?.treatments.isNullOrEmpty()
        openId = id
        DataBus.changed()
    }

    LaunchedEffect(openNew) {
        if (openNew) {
            startNew()
            onOpened()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftButton("New treatment record", Icons.Filled.Add, Modifier.fillMaxWidth(), height = 52.dp) { startNew() }
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
    if (r.done) Pill("Done", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
    else Pill("Open", Brand.GoldSoft, Brand.GoldDeep)
}

/** One record, full screen: its treatments, sittings, notes and money. */
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

    var adding by remember { mutableStateOf(addFirst) }
    var editing by remember { mutableStateOf<Treatment?>(null) }
    var askEdit by remember { mutableStateOf<Treatment?>(null) }
    var notesOpen by remember { mutableStateOf(false) }
    var receive by remember { mutableStateOf(false) }
    var receiptFor by remember { mutableStateOf<Long?>(null) }
    var pdf by remember { mutableStateOf<String?>(null) }

    fun cycleStatus(t: Treatment) {
        val next = when (t.status) {
            STATUS_PLANNED -> STATUS_ONGOING
            STATUS_ONGOING -> STATUS_DONE
            else -> STATUS_ONGOING
        }
        db.saveTreatment(t.copy(status = next))
        DataBus.changed()
        notify("${t.name}: ${statusLabel(next)} ✓")
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Brand.Bg)) {
            AppTopBar(record?.let { txDate(it.day) } ?: "Treatment record", patient?.name.orEmpty(), onClose) {
                if (record != null) TxChip(record)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end =
