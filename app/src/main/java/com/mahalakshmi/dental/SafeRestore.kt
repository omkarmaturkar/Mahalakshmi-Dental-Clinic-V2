package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

// ───────────── Safety copy before a restore ─────────────
/**
 * Before a restore or "Add patients", this phone's current data is kept here,
 * so "Undo last restore" can always bring it back.
 */
object RestoreSafety {
    private val lock = Any()
    private fun dir(c: Context) = File(c.filesDir, "before_restore")
    private fun prefs(c: Context) = c.getSharedPreferences("restore_safety", Context.MODE_PRIVATE)

    /** Keeps everything before a backup replaces it. Photos are moved, not copied, so no extra space is used. */
    fun keepForReplace(c: Context, db: ClinicDb) {
        synchronized(lock) { keep(c, db, movePhotos = true) }
    }

    /** Keeps the records before patients are added from another phone. Photos stay where they are. */
    fun keepForAdd(c: Context, db: ClinicDb) {
        synchronized(lock) { keep(c, db, movePhotos = false) }
    }

    private fun keep(c: Context, db: ClinicDb, movePhotos: Boolean) {
        val count = db.patientCount()
        if (count == 0) return
        val safe = dir(c)
        safe.deleteRecursively()
        safe.mkdirs()
        val sql = db.writableDatabase
        sql.beginTransaction()   // pauses other database work while copying
        try {
            c.getDatabasePath(ClinicDb.NAME).inputStream().use { input ->
                File(safe, "clinic.db").outputStream().use { input.copyTo(it) }
            }
        } finally {
            sql.endTransaction()
        }
        if (movePhotos) {
            val saved = File(safe, "photos").apply { mkdirs() }
            photoDir(c).listFiles()?.forEach { f ->
                val target = File(saved, f.name)
                if (!f.renameTo(target)) {
                    f.copyTo(target, overwrite = true)
                    f.delete()
                }
            }
        }
        prefs(c).edit().putInt("patients", count).putLong("time", System.currentTimeMillis()).apply()
    }

    fun available(c: Context): Boolean = File(dir(c), "clinic.db").exists()
    fun patients(c: Context): Int = prefs(c).getInt("patients", 0)
    fun time(c: Context): Long = prefs(c).getLong("time", 0L)

    /** Brings back the data this phone had before the last restore or "Add patients". */
    fun undo(c: Context, db: ClinicDb): Boolean = synchronized(lock) {
        val safe = dir(c)
        val safeDb = File(safe, "clinic.db")
        if (!safeDb.exists()) return@synchronized false
        db.close()
        val dbFile = c.getDatabasePath(ClinicDb.NAME)
        listOf("", "-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        if (!safeDb.renameTo(dbFile)) safeDb.copyTo(dbFile, overwrite = true)
        val savedPhotos = File(safe, "photos")
        if (savedPhotos.isDirectory) {
            val photos = photoDir(c)
            photos.listFiles()?.forEach { it.delete() }
            savedPhotos.listFiles()?.forEach { f ->
                val target = File(photos, f.name)
                if (!f.renameTo(target)) f.copyTo(target, overwrite = true)
            }
        }
        safe.deleteRecursively()
        prefs(c).edit().clear().apply()
        true
    }
}

// ───────────── Add patients from another phone ─────────────
/** What "Add patients" will do: shown before anything changes. */
class PatientAddPlan(val newPatients: Int, val alreadyHere: List<String>, val photos: Set<String>)

/**
 * Adds the patients of another phone's backup that are not on this phone yet, with all their records.
 * Every record gets a new number on this phone, and the links between records are kept.
 * Patients already on this phone (same name, and same mobile or no mobile) are left as they are.
 */
object PatientMerge {
    // Which column points to which table
    private val links = mapOf(
        "patient_id" to "patients", "visit_id" to "visits", "legacy_visit" to "visits",
        "treatment_id" to "treatments", "payment_id" to "payments", "record_id" to "treatment_records",
        "case_id" to "ortho_cases", "doctor_id" to "doctors"
    )

    // Patient records, in the order they are added (a record comes after the records it points to)
    private val order = listOf(
        "visits", "payments", "treatments", "treatment_records", "record_items", "record_payments",
        "prescriptions", "case_notes", "case_photos", "appointments", "tooth_status",
        "ortho_cases", "ortho_visits", "ortho_payments", "perio_exams", "sittings"
    )

    // Never copied: they are not patient records
    private val notRecords = setOf("patients", "doctors", "settings", "rx_templates", "android_metadata")

    private fun norm(s: String?): String = s.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
    private fun digits(s: String?): String = s.orEmpty().filter { it.isDigit() }.takeLast(10)

    private fun columns(sql: SQLiteDatabase, table: String): List<String> {
        val list = mutableListOf<String>()
        sql.rawQuery("PRAGMA table_info($table)", null).use { c -> while (c.moveToNext()) list.add(c.getString(1)) }
        return list
    }

    private fun tables(sql: SQLiteDatabase): Set<String> {
        val set = HashSet<String>()
        sql.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set
    }

    private fun openCopy(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    /** Stops if the other backup has records this version of the app does not know how to add. */
    private fun checkKnown(src: SQLiteDatabase) {
        val all = tables(src)
        if ("patients" !in all) throw IllegalStateException("This is not a valid clinic backup")
        for (t in all) {
            if (t in notRecords || t.startsWith("sqlite_")) continue
            val cols = columns(src, t)
            val known = t in order && cols.none { it.endsWith("_id") && it !in links }
            if (!known && ("patient_id" in cols || t in order)) {
                throw IllegalStateException("Update both phones to the same version of the app first")
            }
        }
    }

    /** The backup's patient numbers that are new here, and the names of those already on this phone. */
    private fun sortPatients(src: SQLiteDatabase, db: ClinicDb): Pair<List<Long>, List<String>> {
        val here = mutableListOf<Pair<String, String>>()
        db.readableDatabase.rawQuery("SELECT name, phone FROM patients", null).use { c ->
            while (c.moveToNext()) here.add(norm(c.getString(0)) to digits(c.getString(1)))
        }
        val fresh = mutableListOf<Long>()
        val already = mutableListOf<String>()
        src.rawQuery("SELECT id, name, phone FROM patients ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val name = norm(c.getString(1))
                val phone = digits(c.getString(2))
                val same = here.any { (n, p) -> n == name && (p == phone || p.isEmpty() || phone.isEmpty()) }
                if (same) already.add(c.getString(1) ?: "") else fresh.add(c.getLong(0))
            }
        }
        return fresh to already
    }

    /** Looks at the other backup and works out what would be added. Nothing is changed. */
    fun plan(dbFile: File, db: ClinicDb): PatientAddPlan {
        val src = openCopy(dbFile)
        try {
            checkKnown(src)
            val (fresh, already) = sortPatients(src, db)
            val photos = HashSet<String>()
            if (fresh.isNotEmpty()) {
                val ids = fresh.joinToString(",")
                val have = tables(src)
                if ("case_photos" in have) {
                    src.rawQuery("SELECT file FROM case_photos WHERE patient_id IN ($ids)", null).use { c ->
                        while (c.moveToNext()) {
                            val f = c.getString(0)
                            if (!f.isNullOrBlank()) photos.add(File(f).name)
                        }
                    }
                }
                if ("visits" in have && "photos" in columns(src, "visits")) {
                    src.rawQuery("SELECT photos FROM visits WHERE patient_id IN ($ids)", null).use { c ->
                        while (c.moveToNext()) {
                            (c.getString(0) ?: "").split("\n").map { File(it.trim()).name }
                                .filter { it.isNotBlank() }.forEach { photos.add(it) }
                        }
                    }
                }
            }
            return PatientAddPlan(fresh.size, already, photos)
        } finally {
            src.close()
        }
    }

    /** Adds the new patients with all their records, and copies their photos. Returns how many patients were added. */
    fun apply(c: Context, db: ClinicDb, dbFile: File, photosDir: File?, wanted: Set<String>): Int {
        val src = openCopy(dbFile)
        try {
            checkKnown(src)
            val fresh = sortPatients(src, db).first.toHashSet()
            if (fresh.isEmpty()) return 0
            val sql = db.writableDatabase
            val have = tables(src)
            val maps = HashMap<String, HashMap<Long, Long>>()
            fun numbers(table: String): HashMap<Long, Long> = maps.getOrPut(table) { HashMap() }

            sql.beginTransaction()
            try {
                // Doctors are matched by name; a doctor missing here is added (not as main doctor)
                if ("doctors" in have && "doctors" in tables(sql)) {
                    val local = HashMap<String, Long>()
                    sql.rawQuery("SELECT id, name FROM doctors", null).use { cur ->
                        while (cur.moveToNext()) local[norm(cur.getString(1))] = cur.getLong(0)
                    }
                    val hereCols = columns(sql, "doctors")
                    val common = columns(src, "doctors").filter { it in hereCols && it != "id" }
                    src.rawQuery("SELECT * FROM doctors", null).use { cur ->
                        while (cur.moveToNext()) {
                            val oldId = cur.getLong(cur.getColumnIndexOrThrow("id"))
                            val name = norm(cur.getString(cur.getColumnIndexOrThrow("name")))
                            val found = local[name]
                            if (found != null) {
                                numbers("doctors")[oldId] = found
                            } else {
                                val cv = ContentValues()
                                common.forEach { col -> put(cv, cur, col) }
                                if ("main" in common) cv.put("main", 0)
                                val newId = sql.insert("doctors", null, cv)
                                if (newId > 0) {
                                    numbers("doctors")[oldId] = newId
                                    local[name] = newId
                                }
                            }
                        }
                    }
                }

                // The new patients
                val pHere = columns(sql, "patients")
                val pCommon = columns(src, "patients").filter { it in pHere && it != "id" }
                src.rawQuery("SELECT * FROM patients ORDER BY id", null).use { cur ->
                    while (cur.moveToNext()) {
                        val oldId = cur.getLong(cur.getColumnIndexOrThrow("id"))
                        if (oldId !in fresh) continue
                        val cv = ContentValues()
                        pCommon.forEach { col -> put(cv, cur, col) }
                        val newId = sql.insert("patients", null, cv)
                        if (newId <= 0) throw IllegalStateException("A patient could not be added")
                        numbers("patients")[oldId] = newId
                    }
                }

                // All their records
                for (table in order) {
                    if (table !in have) continue
                    if (table !in tables(sql)) {
                        val create = src.rawQuery(
                            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)
                        ).use { cur -> if (cur.moveToFirst()) cur.getString(0) else null } ?: continue
                        sql.execSQL(create)
                    }
                    val srcCols = columns(src, table)
                    val hereCols = columns(sql, table)
                    val common = srcCols.filter { it in hereCols && it != "id" }
                    src.rawQuery("SELECT * FROM $table", null).use { cur ->
                        while (cur.moveToNext()) {
                            // Only the records of the new patients (braces payments go with their braces case)
                            if ("patient_id" in srcCols) {
                                if (cur.getLong(cur.getColumnIndexOrThrow("patient_id")) !in fresh) continue
                            } else if (table == "ortho_payments") {
                                if (numbers("ortho_cases")[cur.getLong(cur.getColumnIndexOrThrow("case_id"))] == null) continue
                            } else {
                                continue
                            }
                            val cv = ContentValues()
                            var keep = true
                            for (col in common) {
                                val target = links[col]
                                if (target == null) {
                                    put(cv, cur, col)
                                    continue
                                }
                                val i = cur.getColumnIndexOrThrow(col)
                                val old = if (cur.isNull(i)) 0L else cur.getLong(i)
                                val now = if (old == 0L) 0L else numbers(target)[old]
                                if (now != null) {
                                    cv.put(col, now)
                                } else if (mustLink(table, col)) {
                                    keep = false
                                    break
                                } else {
                                    cv.put(col, 0L)
                                }
                            }
                            if (!keep) continue
                            val newId = sql.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                            if ("id" in srcCols && newId > 0) numbers(table)[cur.getLong(cur.getColumnIndexOrThrow("id"))] = newId
                        }
                    }
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }

            // Their photos (a photo already on this phone is left as it is)
            if (photosDir != null) {
                val dir = photoDir(c)
                photosDir.listFiles()?.forEach { f ->
                    if (f.name in wanted) {
                        val target = File(dir, f.name)
                        if (!target.exists() && !f.renameTo(target)) f.copyTo(target, overwrite = false)
                    }
                }
            }
            return fresh.size
        } finally {
            src.close()
        }
    }

    // A link that belongs to the record itself must point to an added record, or the row is left out
    private fun mustLink(table: String, col: String): Boolean =
        col == "patient_id" || col == "record_id" || col == "case_id" ||
            (table == "record_items" && col == "treatment_id") ||
            (table == "record_payments" && col == "payment_id") ||
            (table == "ortho_payments" && col == "payment_id")

    private fun put(cv: ContentValues, cur: Cursor, col: String) {
        val i = cur.getColumnIndexOrThrow(col)
        when (cur.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> cv.putNull(col)
            Cursor.FIELD_TYPE_INTEGER -> cv.put(col, cur.getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> cv.put(col, cur.getDouble(i))
            Cursor.FIELD_TYPE_BLOB -> cv.put(col, cur.getBlob(i))
            else -> cv.put(col, cur.getString(i))
        }
    }
}
