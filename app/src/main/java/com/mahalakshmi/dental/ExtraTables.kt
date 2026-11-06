package com.mahalakshmi.dental

import android.database.sqlite.SQLiteDatabase

// All newer tables. "IF NOT EXISTS" makes this safe to run every time the app opens.
fun createExtraTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS prescriptions (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "visit_id INTEGER NOT NULL DEFAULT 0, date INTEGER, diagnosis TEXT, medicines TEXT, advice TEXT)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS rx_templates (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
            "medicines TEXT, advice TEXT)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS treatments (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "name TEXT, teeth TEXT, fee REAL DEFAULT 0, status INTEGER DEFAULT 0, doctor_id INTEGER DEFAULT 0, " +
            "notes TEXT, created_at INTEGER, done_at INTEGER DEFAULT 0, legacy_visit INTEGER DEFAULT 0)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS case_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "treatment_id INTEGER DEFAULT 0, date INTEGER, text TEXT)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS case_photos (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "treatment_id INTEGER DEFAULT 0, file TEXT, kind TEXT, date INTEGER)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS appointments (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "day INTEGER, time TEXT, reason TEXT, status INTEGER DEFAULT 0, created_at INTEGER)"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_treat_patient ON treatments(patient_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_pay_patient ON payments(patient_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_rx_patient ON prescriptions(patient_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_note_patient ON case_notes(patient_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_photo_patient ON case_photos(patient_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_appt_day ON appointments(day)")
}

// Called when an old-style visit is deleted
fun ClinicDb.deleteVisitExtras(visitId: Long) {
    writableDatabase.delete("prescriptions", "visit_id = ?", arrayOf(visitId.toString()))
}

// Called when a patient is deleted: removes their treatments, notes, photos, appointments and prescriptions,
// and their sittings, gum charts, mouth status and braces records
fun ClinicDb.deletePatientExtras(patientId: Long) {
    val args = arrayOf(patientId.toString())
    val sql = writableDatabase
    sql.rawQuery("SELECT file FROM case_photos WHERE patient_id = ?", args).use { c ->
        while (c.moveToNext()) {
            val name = c.getString(0)
            if (!name.isNullOrBlank()) photoFile(name).delete()
        }
    }
    deleteFeatureData(patientId)
    listOf("prescriptions", "treatments", "case_notes", "case_photos", "appointments").forEach { table ->
        sql.delete(table, "patient_id = ?", args)
    }
}

// ───────────── Tables of the clinical features ─────────────
// Sittings, gum charts, mouth status and braces records create their own tables the first time they are used.

private val FEATURE_TABLES = listOf("sittings", "perio_exams", "tooth_status", "ortho_visits", "ortho_cases")

private fun tableHasColumn(sql: SQLiteDatabase, table: String, column: String): Boolean =
    sql.rawQuery("PRAGMA table_info($table)", null).use { c ->
        var found = false
        while (c.moveToNext()) {
            if (c.getString(1) == column) found = true
        }
        found
    }

/** Removes one patient's sittings, gum charts, mouth status and braces records. */
fun ClinicDb.deleteFeatureData(patientId: Long) {
    val sql = writableDatabase
    val args = arrayOf(patientId.toString())
    if (tableHasColumn(sql, "ortho_payments", "case_id") && tableHasColumn(sql, "ortho_cases", "patient_id")) {
        sql.delete("ortho_payments", "case_id IN (SELECT id FROM ortho_cases WHERE patient_id = ?)", args)
    }
    FEATURE_TABLES.forEach { table ->
        if (tableHasColumn(sql, table, "patient_id")) sql.delete(table, "patient_id = ?", args)
    }
}

/**
 * Removes feature records whose patient, treatment or braces case no longer exists
 * (left behind by older versions of the app). Safe to run at any time.
 */
fun ClinicDb.removeLeftoverData() {
    val sql = writableDatabase
    sql.beginTransaction()
    try {
        FEATURE_TABLES.forEach { table ->
            if (tableHasColumn(sql, table, "patient_id")) {
                sql.delete(table, "patient_id NOT IN (SELECT id FROM patients)", null)
            }
        }
        if (tableHasColumn(sql, "sittings", "treatment_id")) {
            sql.delete("sittings", "treatment_id NOT IN (SELECT id FROM treatments)", null)
        }
        if (tableHasColumn(sql, "ortho_cases", "id")) {
            if (tableHasColumn(sql, "ortho_visits", "case_id")) {
                sql.delete("ortho_visits", "case_id NOT IN (SELECT id FROM ortho_cases)", null)
            }
            if (tableHasColumn(sql, "ortho_payments", "case_id")) {
                sql.delete("ortho_payments", "case_id NOT IN (SELECT id FROM ortho_cases)", null)
            }
        }
        sql.setTransactionSuccessful()
    } finally {
        sql.endTransaction()
    }
}
