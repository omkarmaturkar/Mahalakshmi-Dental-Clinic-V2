package com.mahalakshmi.dental

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

const val STATUS_PLANNED = 0
const val STATUS_ONGOING = 1
const val STATUS_DONE = 2

fun statusLabel(status: Int): String = when (status) {
    STATUS_PLANNED -> "To do"
    STATUS_ONGOING -> "In progress"
    else -> "Done"
}

val photoKinds = listOf("Photo", "Before", "After", "X-ray")

data class Treatment(
    val id: Long = 0,
    val patientId: Long,
    val name: String,
    val teeth: String = "",
    val fee: Double = 0.0,
    val status: Int = STATUS_ONGOING,
    val doctorId: Long = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long = 0L
) {
    val teethList: List<String> get() = teeth.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class Payment(val id: Long = 0, val patientId: Long, val amount: Double, val date: Long, val mode: String)

data class CaseNote(val id: Long = 0, val patientId: Long, val treatmentId: Long = 0, val date: Long, val text: String)

data class CasePhoto(val id: Long = 0, val patientId: Long, val treatmentId: Long = 0, val path: String, val kind: String, val date: Long)

data class Booking(val id: Long = 0, val patientId: Long, val day: Long, val time: String = "", val reason: String = "")

/** Money for one patient. "Billed" counts treatments started or done; planned ones are only an estimate. */
data class Account(val billed: Double, val paid: Double, val planned: Double) {
    val balance: Double get() = billed - paid
}

fun accountOf(treatments: List<Treatment>, payments: List<Payment>) = Account(
    billed = treatments.filter { it.status != STATUS_PLANNED }.sumOf { it.fee },
    paid = payments.sumOf { it.amount },
    planned = treatments.filter { it.status == STATUS_PLANNED }.sumOf { it.fee }
)

// ───────────── Treatments ─────────────
private const val TREAT_COLS = "id, patient_id, name, teeth, fee, status, doctor_id, notes, created_at, done_at"

private fun treatmentFrom(c: Cursor) = Treatment(
    id = c.getLong(0), patientId = c.getLong(1), name = c.getString(2) ?: "", teeth = c.getString(3) ?: "",
    fee = c.getDouble(4), status = c.getInt(5), doctorId = c.getLong(6), notes = c.getString(7) ?: "",
    createdAt = c.getLong(8), doneAt = c.getLong(9)
)

/** In progress first, then to do, then done (newest first). */
fun ClinicDb.treatmentsOf(patientId: Long): List<Treatment> {
    val list = mutableListOf<Treatment>()
    readableDatabase.rawQuery(
        "SELECT $TREAT_COLS FROM treatments WHERE patient_id = ? " +
            "ORDER BY CASE status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE 2 END, created_at DESC, id DESC",
        arrayOf(patientId.toString())
    ).use { c -> while (c.moveToNext()) list.add(treatmentFrom(c)) }
    return list
}

fun ClinicDb.saveTreatment(t: Treatment): Long {
    val doneAt = when {
        t.status != STATUS_DONE -> 0L
        t.doneAt > 0L -> t.doneAt
        else -> System.currentTimeMillis()
    }
    val cv = ContentValues().apply {
        put("patient_id", t.patientId)
        put("name", t.name)
        put("teeth", t.teeth)
        put("fee", t.fee)
        put("status", t.status)
        put("doctor_id", t.doctorId)
        put("notes", t.notes)
        put("created_at", t.createdAt)
        put("done_at", doneAt)
    }
    val id = if (t.id == 0L) {
        writableDatabase.insert("treatments", null, cv)
    } else {
        writableDatabase.update("treatments", cv, "id = ?", arrayOf(t.id.toString()))
        t.id
    }
    markArrived(t.patientId)
    return id
}

fun ClinicDb.deleteTreatment(id: Long) {
    val args = arrayOf(id.toString())
    writableDatabase.execSQL("UPDATE case_photos SET treatment_id = 0 WHERE treatment_id = ?", args)
    writableDatabase.execSQL("UPDATE case_notes SET treatment_id = 0 WHERE treatment_id = ?", args)
    writableDatabase.delete("treatments", "id = ?", args)
}

// ───────────── Payments ─────────────
fun ClinicDb.paymentsOf(patientId: Long): List<Payment> {
    val list = mutableListOf<Payment>()
    readableDatabase.rawQuery(
        "SELECT id, patient_id, amount, date, mode FROM payments WHERE patient_id = ? AND amount <> 0 ORDER BY date DESC, id DESC",
        arrayOf(patientId.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(Payment(c.getLong(0), c.getLong(1), c.getDouble(2), c.getLong(3), c.getString(4) ?: ""))
        }
    }
    return list
}

fun ClinicDb.getPaymentById(id: Long): Payment? =
    readableDatabase.rawQuery("SELECT id, patient_id, amount, date, mode FROM payments WHERE id = ?", arrayOf(id.toString()))
        .use { c ->
            if (c.moveToFirst()) Payment(c.getLong(0), c.getLong(1), c.getDouble(2), c.getLong(3), c.getString(4) ?: "") else null
        }

fun ClinicDb.addPayment(patientId: Long, amount: Double, mode: String): Long {
    val cv = ContentValues().apply {
        put("patient_id", patientId)
        put("visit_id", 0)
        put("amount", amount)
        put("date", System.currentTimeMillis())
        put("mode", mode)
    }
    val id = writableDatabase.insert("payments", null, cv)
    markArrived(patientId)
    return id
}

fun ClinicDb.deletePayment(id: Long) {
    writableDatabase.delete("payments", "id = ?", arrayOf(id.toString()))
}

// ───────────── Notes ─────────────
fun ClinicDb.notesOf(patientId: Long): List<CaseNote> {
    val list = mutableListOf<CaseNote>()
    readableDatabase.rawQuery(
        "SELECT id, patient_id, treatment_id, date, text FROM case_notes WHERE patient_id = ? ORDER BY date DESC",
        arrayOf(patientId.toString())
    ).use { c ->
        while (c.moveToNext()) list.add(CaseNote(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4) ?: ""))
    }
    return list
}

fun ClinicDb.addNote(patientId: Long, text: String, treatmentId: Long = 0L): Long {
    val cv = ContentValues().apply {
        put("patient_id", patientId)
        put("treatment_id", treatmentId)
        put("date", System.currentTimeMillis())
        put("text", text)
    }
    val id = writableDatabase.insert("case_notes", null, cv)
    markArrived(patientId)
    return id
}

fun ClinicDb.deleteNote(id: Long) {
    writableDatabase.delete("case_notes", "id = ?", arrayOf(id.toString()))
}

// ───────────── Photos ─────────────
fun ClinicDb.photosOf(patientId: Long): List<CasePhoto> {
    val list = mutableListOf<CasePhoto>()
    readableDatabase.rawQuery(
        "SELECT id, patient_id, treatment_id, file, kind, date FROM case_photos WHERE patient_id = ? ORDER BY date DESC, id DESC",
        arrayOf(patientId.toString())
    ).use { c ->
        while (c.moveToNext()) {
            val kind = (c.getString(4) ?: "Photo").replaceFirstChar { it.uppercase() }
            list.add(CasePhoto(c.getLong(0), c.getLong(1), c.getLong(2), photoFile(c.getString(3) ?: "").absolutePath, kind, c.getLong(5)))
        }
    }
    return list
}

fun ClinicDb.addPhoto(patientId: Long, path: String, kind: String, treatmentId: Long = 0L): Long {
    val cv = ContentValues().apply {
        put("patient_id", patientId)
        put("treatment_id", treatmentId)
        put("file", File(path).name)
        put("kind", kind)
        put("date", System.currentTimeMillis())
    }
    return writableDatabase.insert("case_photos", null, cv)
}

fun ClinicDb.setPhotoKind(id: Long, kind: String) {
    val cv = ContentValues().apply { put("kind", kind) }
    writableDatabase.update("case_photos", cv, "id = ?", arrayOf(id.toString()))
}

fun ClinicDb.deletePhoto(photo: CasePhoto) {
    File(photo.path).delete()
    writableDatabase.delete("case_photos", "id = ?", arrayOf(photo.id.toString()))
}

// ───────────── Appointments ─────────────
fun ClinicDb.nextBooking(patientId: Long): Booking? =
    readableDatabase.rawQuery(
        "SELECT id, patient_id, day, time, reason FROM appointments WHERE patient_id = ? AND status = 0 AND day >= ? " +
            "ORDER BY day, time LIMIT 1",
        arrayOf(patientId.toString(), todayEpochDay().toString())
    ).use { c ->
        if (c.moveToFirst()) Booking(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3) ?: "", c.getString(4) ?: "") else null
    }

/** Books the next appointment (replaces any earlier booking for this patient). */
fun ClinicDb.saveBooking(patientId: Long, day: Long, time: String, reason: String) {
    cancelBookings(patientId)
    val cv = ContentValues().apply {
        put("patient_id", patientId)
        put("day", day)
        put("time", time)
        put("reason", reason)
        put("status", 0)
        put("created_at", System.currentTimeMillis())
    }
    writableDatabase.insert("appointments", null, cv)
}

fun ClinicDb.cancelBookings(patientId: Long) {
    writableDatabase.execSQL(
        "UPDATE appointments SET status = 2 WHERE patient_id = ? AND status = 0",
        arrayOf(patientId.toString())
    )
}

/** The patient came in: today's (and missed) appointments are marked as done. */
fun ClinicDb.markArrived(patientId: Long) {
    writableDatabase.execSQL(
        "UPDATE appointments SET status = 1 WHERE patient_id = ? AND status = 0 AND day <= ?",
        arrayOf(patientId.toString(), todayEpochDay().toString())
    )
}

// ───────────── Remembered fees & doctor names ─────────────
fun ClinicDb.priceFor(name: String): Double? =
    readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf("price:" + name.trim().lowercase()))
        .use { c -> if (c.moveToFirst()) c.getString(0)?.toDoubleOrNull() else null }

fun ClinicDb.rememberPrice(name: String, fee: Double) {
    if (name.isBlank() || fee <= 0.0) return
    val cv = ContentValues().apply {
        put("key", "price:" + name.trim().lowercase())
        put("value", fee.toString())
    }
    writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Names of all doctors, including removed ones (so old treatments still show who did them). */
fun ClinicDb.doctorNames(): Map<Long, String> {
    doctors()
    val map = HashMap<Long, String>()
    readableDatabase.rawQuery("SELECT id, name FROM doctors", null).use { c ->
        while (c.moveToNext()) map[c.getLong(0)] = c.getString(1) ?: ""
    }
    return map
}

// ───────────── Automatic history ─────────────
const val H_TREATMENT = 0
const val H_DONE = 1
const val H_PAYMENT = 2
const val H_NOTE = 3
const val H_RX = 4
const val H_PHOTO = 5

data class HistoryItem(val date: Long, val kind: Int, val title: String, val detail: String)

fun buildHistory(
    treatments: List<Treatment>,
    payments: List<Payment>,
    notes: List<CaseNote>,
    prescriptions: List<Prescription>,
    photos: List<CasePhoto>
): List<HistoryItem> {
    val items = mutableListOf<HistoryItem>()
    treatments.forEach { t ->
        val teeth = if (t.teethList.isEmpty()) "" else "Tooth " + t.teethList.joinToString(", ")
        val detail = listOf(teeth, if (t.fee > 0) rs(t.fee) else "").filter { it.isNotBlank() }.joinToString("  ·  ")
        val addedAndDone = t.status == STATUS_DONE && t.doneAt > 0L && t.doneAt == t.createdAt
        if (!addedAndDone) {
            val verb = if (t.status == STATUS_PLANNED) "Planned" else "Started"
            items.add(HistoryItem(t.createdAt, H_TREATMENT, "$verb ${t.name}", detail))
        }
        if (t.status == STATUS_DONE && t.doneAt > 0L) items.add(HistoryItem(t.doneAt, H_DONE, "Completed ${t.name}", detail))
    }
    payments.forEach { p ->
        val title = if (p.amount >= 0) "Payment received ${rs(p.amount)}" else "Refund ${rs(-p.amount)}"
        items.add(HistoryItem(p.date, H_PAYMENT, title, p.mode))
    }
    notes.forEach { n -> items.add(HistoryItem(n.date, H_NOTE, "Note", n.text)) }
    prescriptions.forEach { r ->
        val meds = if (r.medicines.size == 1) "1 medicine" else "${r.medicines.size} medicines"
        items.add(HistoryItem(r.date, H_RX, "Prescription", listOf(r.diagnosis, meds).filter { it.isNotBlank() }.joinToString("  ·  ")))
    }
    photos.groupBy { epochDayOf(it.date) }.forEach { (_, list) ->
        val title = if (list.size == 1) "1 photo added" else "${list.size} photos added"
        items.add(HistoryItem(list.maxOf { it.date }, H_PHOTO, title, list.map { it.kind }.distinct().joinToString(", ")))
    }
    return items.sortedByDescending { it.date }
}
