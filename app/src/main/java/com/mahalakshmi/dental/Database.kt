package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

fun photoDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun startOfMonth(): Long =
    LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

data class Patient(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val age: String = "",
    val gender: String = "",
    val medical: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val totalFees: Double = 0.0,
    val totalPaid: Double = 0.0,
    val lastVisit: Long = 0L,
    val lastReason: String = "",
    val visitCount: Int = 0
) {
    val remaining: Double get() = totalFees - totalPaid
    val alerts: List<String> get() = medical.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

// Old-style visit (kept so older records and backups still open)
data class Visit(
    val id: Long = 0,
    val patientId: Long,
    val date: Long = System.currentTimeMillis(),
    val reason: String = "",
    val problem: String = "",
    val fees: Double = 0.0,
    val paid: Double = 0.0,
    val photos: List<String> = emptyList(),
    val teeth: String = "",
    val nextVisit: Long = 0L,
    val payMode: String = ""
) {
    val remaining: Double get() = fees - paid
    val treatments: List<String> get() = reason.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val teethList: List<String> get() = teeth.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class Appointment(val patientId: Long, val name: String, val day: Long, val reason: String)

data class Stats(
    val todayCollection: Double,
    val monthCollection: Double,
    val todayVisits: Int,
    val patientCount: Int
)

class ClinicDb(private val context: Context) : SQLiteOpenHelper(context, NAME, null, 4) {
    companion object { const val NAME = "clinic.db" }

    // Keeps all data in one file, which makes backups simple and safe
    init { setWriteAheadLoggingEnabled(false) }

    private val photoFolder by lazy { photoDir(context) }
    private val visitCols = "id, patient_id, date, reason, problem, fees, paid, photos, teeth, next_visit, pay_mode"

    /** A photo saved inside the app, found by its file name. */
    fun photoFile(name: String): File = File(photoFolder, File(name).name)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE patients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT, " +
                "age TEXT, gender TEXT, created_at INTEGER, medical TEXT, notes TEXT)"
        )
        db.execSQL(
            "CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
                "date INTEGER, reason TEXT, problem TEXT, fees REAL, paid REAL, photos TEXT, teeth TEXT, " +
                "next_visit INTEGER DEFAULT 0, pay_mode TEXT)"
        )
        createPayments(db)
        createExtraTables(db)
    }

    private fun createPayments(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER, " +
                "visit_id INTEGER, amount REAL, date INTEGER, mode TEXT)"
        )
    }

    // Upgrades data from older versions of the app
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE patients ADD COLUMN medical TEXT")
            db.execSQL("ALTER TABLE patients ADD COLUMN notes TEXT")
            db.execSQL("ALTER TABLE visits ADD COLUMN teeth TEXT")
            db.execSQL("ALTER TABLE visits ADD COLUMN next_visit INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE visits ADD COLUMN pay_mode TEXT")
            createPayments(db)
            db.execSQL(
                "INSERT INTO payments (patient_id, visit_id, amount, date, mode) " +
                    "SELECT patient_id, id, paid, date, '' FROM visits WHERE paid > 0"
            )
        }
        createExtraTables(db)
        if (oldVersion < 4) moveVisitsIntoPatientFiles(db)
    }

    // Version 4: every old visit becomes a treatment in the patient file. Nothing is deleted.
    private fun moveVisitsIntoPatientFiles(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO treatments (patient_id, name, teeth, fee, status, doctor_id, notes, created_at, done_at, legacy_visit) " +
                "SELECT patient_id, CASE WHEN TRIM(IFNULL(reason, '')) = '' THEN 'Dental treatment' ELSE reason END, " +
                "IFNULL(teeth, ''), IFNULL(fees, 0), 2, 0, IFNULL(problem, ''), date, date, id FROM visits " +
                "WHERE TRIM(IFNULL(reason, '')) <> '' OR IFNULL(fees, 0) > 0"
        )
        db.execSQL(
            "INSERT INTO case_notes (patient_id, treatment_id, date, text) " +
                "SELECT patient_id, 0, date, problem FROM visits " +
                "WHERE TRIM(IFNULL(reason, '')) = '' AND IFNULL(fees, 0) = 0 AND TRIM(IFNULL(problem, '')) <> ''"
        )
        db.execSQL(
            "INSERT INTO appointments (patient_id, day, time, reason, status, created_at) " +
                "SELECT patient_id, next_visit, '', IFNULL(reason, ''), 0, date FROM visits v " +
                "WHERE next_visit >= ? AND date = (SELECT MAX(date) FROM visits WHERE patient_id = v.patient_id)",
            arrayOf<Any>(todayEpochDay() - 1)
        )
        val photoRows = mutableListOf<ContentValues>()
        db.rawQuery(
            "SELECT v.patient_id, v.photos, v.date, IFNULL(t.id, 0) FROM visits v " +
                "LEFT JOIN treatments t ON t.legacy_visit = v.id WHERE IFNULL(v.photos, '') <> ''",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val names = (c.getString(1) ?: "").split("\n").map { File(it).name }.filter { it.isNotBlank() }
                for (name in names) {
                    val cv = ContentValues()
                    cv.put("patient_id", c.getLong(0))
                    cv.put("treatment_id", c.getLong(3))
                    cv.put("file", name)
                    cv.put("kind", "photo")
                    cv.put("date", c.getLong(2))
                    photoRows.add(cv)
                }
            }
        }
        photoRows.forEach { db.insert("case_photos", null, it) }
    }

    // ---------- Patients ----------
    private fun queryPatients(where: String, args: Array<String>): List<Patient> {
        val sql = """
            SELECT p.id, p.name, p.phone, p.age, p.gender, p.medical, p.notes, p.created_at,
                   IFNULL((SELECT SUM(t.fee) FROM treatments t WHERE t.patient_id = p.id AND t.status > 0), 0),
                   IFNULL((SELECT SUM(y.amount) FROM payments y WHERE y.patient_id = p.id), 0),
                   MAX(
                       IFNULL((SELECT MAX(MAX(IFNULL(t.created_at, 0), IFNULL(t.done_at, 0))) FROM treatments t WHERE t.patient_id = p.id), 0),
                       IFNULL((SELECT MAX(y.date) FROM payments y WHERE y.patient_id = p.id), 0),
                       IFNULL((SELECT MAX(n.date) FROM case_notes n WHERE n.patient_id = p.id), 0),
                       IFNULL((SELECT MAX(r.date) FROM prescriptions r WHERE r.patient_id = p.id), 0)
                   ) AS last_seen,
                   (SELECT COUNT(*) FROM treatments t WHERE t.patient_id = p.id),
                   (SELECT t.name FROM treatments t WHERE t.patient_id = p.id ORDER BY t.created_at DESC, t.id DESC LIMIT 1)
            FROM patients p
            WHERE $where
            ORDER BY CASE WHEN last_seen > 0 THEN last_seen ELSE p.created_at END DESC
        """
        val list = mutableListOf<Patient>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                list.add(
                    Patient(
                        id = c.getLong(0), name = c.getString(1) ?: "", phone = c.getString(2) ?: "",
                        age = c.getString(3) ?: "", gender = c.getString(4) ?: "",
                        medical = c.getString(5) ?: "", notes = c.getString(6) ?: "",
                        createdAt = c.getLong(7), totalFees = c.getDouble(8), totalPaid = c.getDouble(9),
                        lastVisit = c.getLong(10), visitCount = c.getInt(11), lastReason = c.getString(12) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getPatients(search: String): List<Patient> {
        val q = "%${search.trim()}%"
        return queryPatients("p.name LIKE ? OR p.phone LIKE ?", arrayOf(q, q))
    }

    fun getPatient(id: Long): Patient? = queryPatients("p.id = ?", arrayOf(id.toString())).firstOrNull()

    fun savePatient(p: Patient): Long {
        val cv = ContentValues().apply {
            put("name", p.name); put("phone", p.phone); put("age", p.age); put("gender", p.gender)
            put("medical", p.medical); put("notes", p.notes)
        }
        return if (p.id == 0L) {
            cv.put("created_at", p.createdAt)
            writableDatabase.insert("patients", null, cv)
        } else {
            writableDatabase.update("patients", cv, "id = ?", arrayOf(p.id.toString()))
            p.id
        }
    }

    fun deletePatient(id: Long) {
        getVisits(id).forEach { deleteVisit(it) }
        deletePatientExtras(id)
        writableDatabase.delete("payments", "patient_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("patients", "id = ?", arrayOf(id.toString()))
    }

    // ---------- Old-style visits (kept for older records) ----------
    private fun toVisit(c: Cursor) = Visit(
        id = c.getLong(0), patientId = c.getLong(1), date = c.getLong(2),
        reason = c.getString(3) ?: "", problem = c.getString(4) ?: "",
        fees = c.getDouble(5), paid = c.getDouble(6),
        photos = (c.getString(7) ?: "").split("\n").filter { it.isNotBlank() }
            .map { File(photoFolder, File(it).name).absolutePath },
        teeth = c.getString(8) ?: "",
        nextVisit = c.getLong(9),
        payMode = c.getString(10) ?: ""
    )

    fun getVisits(patientId: Long): List<Visit> {
        val list = mutableListOf<Visit>()
        readableDatabase.rawQuery(
            "SELECT $visitCols FROM visits WHERE patient_id = ? ORDER BY date DESC",
            arrayOf(patientId.toString())
        ).use { c -> while (c.moveToNext()) list.add(toVisit(c)) }
        return list
    }

    fun getVisit(id: Long): Visit? =
        readableDatabase.rawQuery("SELECT $visitCols FROM visits WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) toVisit(c) else null }

    fun saveVisit(v: Visit): Long {
        val cv = ContentValues().apply {
            put("patient_id", v.patientId); put("date", v.date)
            put("reason", v.reason); put("problem", v.problem)
            put("fees", v.fees); put("paid", v.paid)
            put("photos", v.photos.joinToString("\n") { File(it).name })
            put("teeth", v.teeth); put("next_visit", v.nextVisit); put("pay_mode", v.payMode)
        }
        return if (v.id == 0L) {
            val id = writableDatabase.insert("visits", null, cv)
            if (v.paid > 0) logPayment(v.patientId, id, v.paid, v.payMode, v.date)
            id
        } else {
            val oldPaid = getVisit(v.id)?.paid ?: 0.0
            writableDatabase.update("visits", cv, "id = ?", arrayOf(v.id.toString()))
            val diff = v.paid - oldPaid
            if (diff != 0.0) logPayment(v.patientId, v.id, diff, v.payMode, System.currentTimeMillis())
            v.id
        }
    }

    fun deleteVisit(v: Visit) {
        v.photos.forEach { File(it).delete() }
        deleteVisitExtras(v.id)
        writableDatabase.delete("payments", "visit_id = ?", arrayOf(v.id.toString()))
        writableDatabase.delete("visits", "id = ?", arrayOf(v.id.toString()))
    }

    private fun logPayment(patientId: Long, visitId: Long, amount: Double, mode: String, date: Long) {
        val cv = ContentValues().apply {
            put("patient_id", patientId); put("visit_id", visitId); put("amount", amount)
            put("date", date); put("mode", mode)
        }
        writableDatabase.insert("payments", null, cv)
    }

    fun recordPayment(patientId: Long, amount: Double, mode: String, visitId: Long = 0L) {
        var left = amount
        val targets = getVisits(patientId)
            .filter { it.remaining > 0.0 && (visitId == 0L || it.id == visitId) }
            .sortedBy { it.date }
        for (v in targets) {
            if (left <= 0.0) break
            val pay = minOf(left, v.remaining)
            val cv = ContentValues().apply { put("paid", v.paid + pay); put("pay_mode", mode) }
            writableDatabase.update("visits", cv, "id = ?", arrayOf(v.id.toString()))
            logPayment(patientId, v.id, pay, mode, System.currentTimeMillis())
            left -= pay
        }
    }

    // ---------- Dashboard ----------
    private fun number(sql: String, vararg args: String): Double =
        readableDatabase.rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

    fun patientCount(): Int = number("SELECT COUNT(*) FROM patients").toInt()

    fun stats(): Stats {
        val day = startOfToday().toString()
        val month = startOfMonth().toString()
        return Stats(
            todayCollection = number("SELECT IFNULL(SUM(amount), 0) FROM payments WHERE date >= ?", day),
            monthCollection = number("SELECT IFNULL(SUM(amount), 0) FROM payments WHERE date >= ?", month),
            todayVisits = number(
                "SELECT COUNT(DISTINCT pid) FROM (" +
                    "SELECT patient_id AS pid FROM treatments WHERE created_at >= ? OR done_at >= ? " +
                    "UNION ALL SELECT patient_id FROM payments WHERE date >= ? " +
                    "UNION ALL SELECT patient_id FROM case_notes WHERE date >= ? " +
                    "UNION ALL SELECT patient_id FROM prescriptions WHERE date >= ?)",
                day, day, day, day, day
            ).toInt(),
            patientCount = patientCount()
        )
    }

    /** Booked appointments from today up to [days] days ahead (one per patient). */
    fun upcoming(days: Int): List<Appointment> {
        val today = todayEpochDay()
        val list = mutableListOf<Appointment>()
        readableDatabase.rawQuery(
            "SELECT p.id, p.name, a.day, a.reason FROM appointments a JOIN patients p ON p.id = a.patient_id " +
                "WHERE a.status = 0 AND a.day >= ? AND a.day <= ? ORDER BY a.day, a.time, p.name",
            arrayOf(today.toString(), (today + days).toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(Appointment(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getString(3) ?: ""))
            }
        }
        return list.distinctBy { it.patientId }
    }
}
