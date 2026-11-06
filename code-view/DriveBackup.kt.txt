package com.mahalakshmi.dental

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriveFile(val id: String, val name: String, val size: Long)

/** Google Drive backup – works like WhatsApp: one folder in your Google Drive, updated automatically. */
object DriveBackup {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val FOLDER_NAME = "Mahalakshmi Dental Clinic Backup"
    private val lock = Any()

    private fun prefs(c: Context) = c.getSharedPreferences("drive_backup", Context.MODE_PRIVATE)

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun isConnected(c: Context): Boolean = prefs(c).getBoolean("connected", false)
    fun account(c: Context): String = prefs(c).getString("account", "") ?: ""
    fun lastBackup(c: Context): Long = prefs(c).getLong("last", 0L)
    fun autoEnabled(c: Context): Boolean = prefs(c).getBoolean("auto", true)
    fun useMobileData(c: Context): Boolean = prefs(c).getBoolean("mobile", false)

    fun setAuto(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("auto", on).apply()
    }

    fun setMobile(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("mobile", on).apply()
    }

    fun disconnect(c: Context) {
        prefs(c).edit().putBoolean("connected", false).putString("account", "").apply()
    }

    /** Called once the user allows access. Remembers the Google account name. */
    fun markConnected(c: Context, token: String) {
        val account = try {
            val json = JSONObject(get(token, "$API/about?fields=" + enc("user(displayName,emailAddress)")))
            json.optJSONObject("user")?.optString("emailAddress").orEmpty()
        } catch (e: Exception) {
            ""
        }
        prefs(c).edit().putBoolean("connected", true).putString("account", account).apply()
    }

    /** Gets access without showing any screen (works after the first connection). */
    fun silentToken(c: Context): String? = try {
        val result = Tasks.await(Identity.getAuthorizationClient(c).authorize(request()))
        if (result.hasResolution()) null else result.accessToken
    } catch (e: Exception) {
        null
    }

    /** Daily automatic backup – called when the app goes to the background. */
    fun autoBackup(c: Context, db: ClinicDb) {
        try {
            if (!isConnected(c) || !autoEnabled(c)) return
            if (System.currentTimeMillis() - lastBackup(c) < 20L * 60 * 60 * 1000) return
            if (!networkOk(c)) return
            val token = silentToken(c) ?: return
            backup(c, db, token) {}
        } catch (e: Exception) {
            // It will simply try again next time
        }
    }

    private fun networkOk(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return useMobileData(c) || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    // ───── Backup ─────
    /** Uploads today's copy of the records and any new photos. Returns a short message. */
    fun backup(c: Context, db: ClinicDb, token: String, progress: (String) -> Unit): String =
        synchronized(lock) { runBackup(c, db, token, progress) }

    private fun runBackup(c: Context, db: ClinicDb, token: String, progress: (String) -> Unit): String {
        if (db.patientCount() == 0) throw IOException("There are no patients to back up yet")
        progress("Preparing backup…")
        val folder = findFolder(token, FOLDER_NAME, null) ?: createFolder(token, FOLDER_NAME, null)
        val photosFolder = findFolder(token, "photos", folder) ?: createFolder(token, "photos", folder)

        progress("Uploading patient records…")
        val snapshot = File(c.cacheDir, "drive_snapshot.db")
        copyDatabase(c, db, snapshot)
        val todayName = "clinic_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".db"
        val newId = uploadFile(token, todayName, folder, "application/octet-stream", snapshot)
        snapshot.delete()
        val dbFiles = listFiles(token, "'$folder' in parents and trashed=false and mimeType!='$FOLDER_MIME'")
            .filter { it.name.startsWith("clinic_") && it.name.endsWith(".db") }
        dbFiles.filter { it.name == todayName && it.id != newId }.forEach { delete(token, it.id) }
        dbFiles.filter { it.name != todayName }.sortedByDescending { it.name }.drop(14).forEach { delete(token, it.id) }

        val uploaded = listFiles(token, "'$photosFolder' in parents and trashed=false").map { it.name }.toHashSet()
        val pending = photoDir(c).listFiles()?.filter { it.name !in uploaded } ?: emptyList()
        pending.forEachIndexed { i, f ->
            progress("Uploading photos ${i + 1} of ${pending.size}…")
            uploadFile(token, f.name, photosFolder, "image/jpeg", f)
        }
        prefs(c).edit().putLong("last", System.currentTimeMillis()).apply()
        return if (pending.isEmpty()) "Backup complete ✓" else "Backup complete ✓  (${pending.size} new photos)"
    }

    /** Date of the newest backup on Drive (like "2026-10-23"), or null if there is none. */
    fun latestBackupDate(token: String): String? {
        val folder = findFolder(token, FOLDER_NAME, null) ?: return null
        return listFiles(token, "'$folder' in parents and trashed=false and mimeType!='$FOLDER_MIME'")
            .map { it.name }
            .filter { it.startsWith("clinic_") && it.endsWith(".db") }
            .maxOrNull()
            ?.removePrefix("clinic_")?.removeSuffix(".db")
    }

    // ───── Restore ─────
    fun restore(c: Context, db: ClinicDb, token: String, progress: (String) -> Unit): Boolean =
        synchronized(lock) { runRestore(c, db, token, progress) }

    private fun runRestore(c: Context, db: ClinicDb, token: String, progress: (String) -> Unit): Boolean {
        progress("Looking for your backup…")
        val folder = findFolder(token, FOLDER_NAME, null) ?: return false
        val latest = listFiles(token, "'$folder' in parents and trashed=false and mimeType!='$FOLDER_MIME'")
            .filter { it.name.startsWith("clinic_") && it.name.endsWith(".db") }
            .maxByOrNull { it.name } ?: return false
        val temp = File(c.cacheDir, "drive_restore").apply {
            deleteRecursively()
            mkdirs()
        }
        progress("Downloading patient records…")
        download(token, latest.id, File(temp, "clinic.db"))
        val photosFolder = findFolder(token, "photos", folder)
        if (photosFolder != null) {
            val photos = listFiles(token, "'$photosFolder' in parents and trashed=false")
            val dir = File(temp, "photos").apply { mkdirs() }
            photos.forEachIndexed { i, f ->
                progress("Downloading photos ${i + 1} of ${photos.size}…")
                download(token, f.id, File(dir, f.name))
            }
        }
        progress("Restoring…")
        return install(c, db, temp)
    }

    private fun copyDatabase(c: Context, db: ClinicDb, target: File) {
        val sql = db.writableDatabase
        sql.beginTransaction()   // pauses other database work while copying
        try {
            c.getDatabasePath(ClinicDb.NAME).inputStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        } finally {
            sql.endTransaction()
        }
    }

    private fun install(c: Context, db: ClinicDb, temp: File): Boolean {
        val newDb = File(temp, "clinic.db")
        val header = ByteArray(16)
        val valid = newDb.exists() && newDb.inputStream().use { it.read(header) == 16 } &&
            String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
        if (!valid) {
            temp.deleteRecursively()
            return false
        }
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

    // ───── Talking to Google Drive ─────
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun open(token: String, method: String, url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("Google Drive error $code")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun get(token: String, url: String): String = readResponse(open(token, "GET", url))

    private fun delete(token: String, id: String) {
        try {
            readResponse(open(token, "DELETE", "$API/files/$id"))
        } catch (e: Exception) {
            // Not important if an old copy can't be removed
        }
    }

    private fun findFolder(token: String, name: String, parent: String?): String? {
        var q = "mimeType='$FOLDER_MIME' and name='${name.replace("'", "\\'")}' and trashed=false"
        if (parent != null) q += " and '$parent' in parents"
        return listFiles(token, q).firstOrNull()?.id
    }

    private fun createFolder(token: String, name: String, parent: String?): String {
        val meta = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
        if (parent != null) meta.put("parents", JSONArray().put(parent))
        val body = meta.toString().toByteArray(Charsets.UTF_8)
        val conn = open(token, "POST", "$API/files?fields=id")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setFixedLengthStreamingMode(body.size)
        conn.outputStream.use { it.write(body) }
        return JSONObject(readResponse(conn)).getString("id")
    }

    private fun listFiles(token: String, query: String): List<DriveFile> {
        val result = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            var url = "$API/files?q=" + enc(query) + "&fields=" + enc("nextPageToken,files(id,name,size)") +
                "&pageSize=1000&spaces=drive"
            val current = pageToken
            if (current != null) url += "&pageToken=" + enc(current)
            val json = JSONObject(get(token, url))
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                result.add(DriveFile(f.getString("id"), f.optString("name"), f.optString("size", "0").toLongOrNull() ?: 0L))
            }
            pageToken = json.optString("nextPageToken", "").ifBlank { null }
        } while (pageToken != null)
        return result
    }

    private fun uploadFile(token: String, name: String, parent: String, mime: String, file: File): String {
        val boundary = "clinic" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(parent)).toString()
        val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: $mime\r\n\r\n").toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val conn = open(token, "POST", "$UPLOAD?uploadType=multipart&fields=id")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.setFixedLengthStreamingMode(head.size + file.length() + tail.size)
        conn.outputStream.use { out ->
            out.write(head)
            file.inputStream().use { it.copyTo(out) }
            out.write(tail)
        }
        return JSONObject(readResponse(conn)).getString("id")
    }

    private fun download(token: String, id: String, target: File) {
        val conn = open(token, "GET", "$API/files/$id?alt=media")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Google Drive error $code")
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }
}
