package com.mahalakshmi.dental

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ───────────── Bigger on tablets (v37) ─────────────
/** On tablets everything is drawn larger, so the app fills a big screen the way it fills a phone. */
@Composable
fun ScaledForTablets(content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.smallestScreenWidthDp
    val scale = when {
        width >= 720 -> 1.3f
        width >= 600 -> 1.15f
        else -> 1f
    }
    if (scale == 1f) {
        content()
    } else {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale), content = content)
    }
}

// ───────────── Treatment names typed by hand ─────────────
private const val CUSTOM_TREATMENTS = "treatments:custom"

private fun ClinicDb.saveCustomTreatments(list: List<String>) {
    val cv = ContentValues().apply {
        put("key", CUSTOM_TREATMENTS)
        put("value", list.joinToString("\n"))
    }
    writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Names typed by hand, shown as chips after the usual ones. The first time, earlier typed names are collected. */
fun ClinicDb.customTreatments(): List<String> {
    val saved = readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(CUSTOM_TREATMENTS))
        .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    if (saved != null) return saved.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    val found = mutableListOf<String>()
    readableDatabase.rawQuery(
        "SELECT TRIM(name), COUNT(*) AS n FROM treatments WHERE TRIM(IFNULL(name, '')) <> '' " +
            "GROUP BY LOWER(TRIM(name)) ORDER BY n DESC LIMIT 40",
        null
    ).use { c -> while (c.moveToNext()) found.add(c.getString(0)) }
    val list = found.filter { n ->
        treatmentOptions.none { o -> o.equals(n, ignoreCase = true) || n.startsWith("$o (", ignoreCase = true) }
    }.distinctBy { it.lowercase() }.take(20)
    saveCustomTreatments(list)
    return list
}

/** Adds a name typed by hand to the list (once). */
fun ClinicDb.rememberTreatment(name: String) {
    val n = name.trim()
    if (n.isBlank() || treatmentOptions.any { it.equals(n, ignoreCase = true) }) return
    val list = customTreatments()
    if (list.any { it.equals(n, ignoreCase = true) }) return
    saveCustomTreatments(list + n)
}

/** Takes a name off the list. Treatments that already use it keep their name. */
fun ClinicDb.forgetTreatment(name: String) {
    saveCustomTreatments(customTreatments().filterNot { it.equals(name.trim(), ignoreCase = true) })
}

// ───────────── Small square button ─────────────
@Composable
fun SquareIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Soft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Brand.Ink) }
}

// ───────────── Patient ID and OPD ID ─────────────
private var idPrefix: String? = null

private fun initialsOf(name: String): String =
    name.split(Regex("\\s+")).mapNotNull { w -> w.firstOrNull { it.isLetter() } }
        .joinToString("") { it.uppercaseChar().toString() }.take(4).ifBlank { "PT" }

/** Letters in front of every patient ID (e.g. MDC). Made from the clinic name the first time, then kept. */
fun ClinicDb.loadPatientPrefix(): String {
    idPrefix?.let { return it }
    val saved = readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf("patient_id_prefix"))
        .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    val prefix = saved?.takeIf { it.isNotBlank() } ?: initialsOf(clinicInfo().name).also { made ->
        val cv = ContentValues().apply {
            put("key", "patient_id_prefix")
            put("value", made)
        }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
    idPrefix = prefix
    return prefix
}

/** Permanent patient ID, e.g. MDC-001. Built from the patient number, which is never reused. */
fun patientCode(id: Long): String =
    (idPrefix ?: initialsOf(DEFAULT_CLINIC_NAME)) + "-" + id.toString().padStart(3, '0')

/** OPD ID of one visit: patient ID + visit date (DDMMYY), e.g. MDC-001-181126 for 18 Nov 2026. */
fun opdId(patientId: Long, day: Long): String =
    patientCode(patientId) + "-" + LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("ddMMyy"))
