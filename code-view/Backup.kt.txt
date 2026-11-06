package com.mahalakshmi.dental

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Backup {
    private val lock = Any()
    private fun prefs(c: Context) = c.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun markDone(c: Context) = prefs(c).edit().putLong("last", System.currentTimeMillis()).apply()

    fun lastBackup(c: Context): Long = prefs(c).getLong("last", 0L)
    fun zipName() = "MahalakshmiClinic_${day()}.zip"

    fun autoFolderName(c: Context): String? = prefs(c).getString("folder", null)?.let {
        Uri.parse(it).lastPathSegment?.substringAfter(':')?.ifBlank { "Selected folder" } ?: "Selected folder"
    }

    fun setAutoFolder(c: Context, uri: Uri) {
        c.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs(c).edit().putString("folder", uri.toString()).putLong("lastAuto", 0L).apply()
    }

    // ---------- Automatic backup to a folder ----------
    fun autoBackup(c: Context, db: ClinicDb, force: Boolean = false): Boolean =
        synchronized(lock) { runAuto(c, db, force) }

    private fun runAuto(c: Context, db: ClinicDb, force: Boolean): Boolean {
        val p = prefs(c)
        val folder = p.getString("folder", null) ?: return false
        if (!force && System.currentTimeMillis() - p.getLong("lastAuto", 0L) < 30 * 60 * 1000L) return false
        return try {
            if (db.patientCount() == 0) return false   // never overwrite a good backup with an empty app
            val r = c.contentResolver
            val tree = Uri.parse(folder)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val root = children(r, tree, rootId)

            // 1) Today's copy of the database (the last 30 days are kept as history)
            val dbName = "clinic_${day()}.db"
            val dbDoc = root[dbName]?.let { doc(tree, it.id) }
                ?: create(r, tree, rootId, "application/octet-stream", dbName)
            r.openOutputStream(dbDoc, "wt")!!.use { copyDb(c, db, it) }
            root.keys.filter { it.startsWith("clinic_") && it.endsWith(".db") && it != dbName }
                .sortedDescending().drop(29)
                .forEach { DocumentsContract.deleteDocument(r, doc(tree, root.getValue(it).id)) }

            // 2) Photos: only new ones are copied
            val photosId = root["photos"]?.id
                ?: DocumentsContract.getDocumentId(create(r, tree, rootId, Document.MIME_TYPE_DIR, "photos"))
            val saved = children(r, tree, photosId)
            photoDir(c).listFiles()?.forEach { f ->
                val old = saved[f.name]
                if (old == null || old.size != f.length()) {
                    val target = old?.let { doc(tree, it.id) } ?: create(r, tree, photosId, "image/jpeg", f.name)
                    r.openOutputStream(target, "wt")!!.use { out -> f.inputStream().use { it.copyTo(out) } }
                }
            }
            p.edit().putLong("lastAuto", System.currentTimeMillis()).apply()
            markDone(c)
            true
        } catch (e: Exception) { false }
    }

    // ---------- One-file backup (.zip) for Google Drive / pen drive ----------
    fun exportZip(c: Context, db: ClinicDb, uri: Uri): Boolean = synchronized(lock) {
        try {
            c.contentResolver.openOutputStream(uri)!!.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    zip.setLevel(1)
                    zip.putNextEntry(ZipEntry("clinic.db")); copyDb(c, db, zip); zip.closeEntry()
                    photoDir(c).listFiles()?.forEach { f ->
                        zip.putNextEntry(ZipEntry("photos/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            markDone(c)
            true
        } catch (e: Exception) { false }
    }

    // ---------- Restore ----------
    fun restoreZip(c: Context, db: ClinicDb, uri: Uri): Boolean = synchronized(lock) {
        try {
            val temp = freshTemp(c)
            c.contentResolver.openInputStream(uri)!!.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val n = entry.name
                        if (!entry.isDirectory && !n.contains("..") && (n == "clinic.db" || n.startsWith("photos/"))) {
                            val t = File(temp, n)
                            t.parentFile?.mkdirs()
                            t.outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            install(c, db, temp)
        } catch (e: Exception) { false }
    }

    fun restoreFromFolder(c: Context, db: ClinicDb, tree: Uri): Boolean = synchronized(lock) {
        try {
            val r = c.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val root = children(r, tree, rootId)
            val latest = root.keys.filter { it.startsWith("clinic_") && it.endsWith(".db") }.maxOrNull()
            if (latest == null) false else {
                val temp = freshTemp(c)
                r.openInputStream(doc(tree, root.getValue(latest).id))!!.use { input ->
                    File(temp, "clinic.db").outputStream().use { input.copyTo(it) }
                }
                root["photos"]?.let { ph ->
                    val dir = File(temp, "photos").apply { mkdirs() }
                    children(r, tree, ph.id).forEach { (name, d) ->
                        r.openInputStream(doc(tree, d.id))!!.use { input ->
                            File(dir, name).outputStream().use { input.copyTo(it) }
                        }
                    }
                }
                val ok = install(c, db, temp)
                if (ok) try { setAutoFolder(c, tree) } catch (e: Exception) {}
                ok
            }
        } catch (e: Exception) { false }
    }

    // ---------- Helpers ----------
    private fun copyDb(c: Context, db: ClinicDb, out: OutputStream) {
        val sql = db.writableDatabase
        sql.beginTransaction()   // pauses other database work while copying
        try { c.getDatabasePath(ClinicDb.NAME).inputStream().use { it.copyTo(out) } }
        finally { sql.endTransaction() }
    }

    private fun install(c: Context, db: ClinicDb, temp: File): Boolean {
        val newDb = File(temp, "clinic.db")
        val header = ByteArray(16)
        val valid = newDb.exists() && newDb.inputStream().use { it.read(header) == 16 } &&
            String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
        if (!valid) { temp.deleteRecursively(); return false }
        db.close()
        val dbFile = c.getDatabasePath(ClinicDb.NAME)
        listOf("", "-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        dbFile.parentFile?.mkdirs()
        if (!newDb.renameTo(dbFile)) newDb.copyTo(dbFile, overwrite = true)
        val dir = photoDir(c)
        dir.listFiles()?.forEach { it.delete() }
        File(temp, "photos").listFiles()?.forEach {
            val target = File(dir, it.name)
            if (!it.renameTo(target)) it.copyTo(target, overwrite = true)
        }
        temp.deleteRecursively()
        return true
    }

    private fun freshTemp(c: Context) = File(c.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }

    private class Doc(val id: String, val size: Long)

    private fun children(r: ContentResolver, tree: Uri, parentId: String): Map<String, Doc> {
        val result = HashMap<String, Doc>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        r.query(
            uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
            null, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1)
                if (name != null) result[name] = Doc(cur.getString(0), if (cur.isNull(2)) -1L else cur.getLong(2))
            }
        }
        return result
    }

    private fun doc(tree: Uri, id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun create(r: ContentResolver, tree: Uri, parentId: String, mime: String, name: String): Uri =
        DocumentsContract.createDocument(r, doc(tree, parentId), mime, name) ?: throw IOException("Cannot create $name")
}
