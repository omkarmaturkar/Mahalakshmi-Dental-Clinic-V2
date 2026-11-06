@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val DEFAULT_CLINIC_NAME = "Mahalakshmi Dental Clinic"
const val DEFAULT_FOOTER = "Thank you for visiting. Wishing you a healthy smile!"

private const val DOCTORS_TABLE =
    "CREATE TABLE IF NOT EXISTS doctors (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
        "qualification TEXT, specialty TEXT, reg_no TEXT, phone TEXT, on_bills INTEGER DEFAULT 1, " +
        "main INTEGER DEFAULT 0, visiting INTEGER DEFAULT 0, share_percent REAL DEFAULT 0, active INTEGER DEFAULT 1)"

val specialtyOptions = listOf(
    "General Dentist", "Endodontist", "Orthodontist", "Periodontist",
    "Oral Surgeon", "Prosthodontist", "Pedodontist", "Implantologist"
)

/** Clinic details used on bills and prescriptions. The doctor fields are filled from the doctors list. */
data class ClinicInfo(
    val name: String = DEFAULT_CLINIC_NAME,
    val doctor: String = "",
    val qualification: String = "",
    val regNo: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val timings: String = "",
    val footer: String = DEFAULT_FOOTER,
    val paper: String = "A4"
)

data class Doctor(
    val id: Long = 0,
    val name: String,
    val qualification: String = "",
    val specialty: String = "",
    val regNo: String = "",
    val phone: String = "",
    val onBills: Boolean = true,
    val main: Boolean = false,
    val visiting: Boolean = false
) {
    /** For example "Dr. Priya Patil, BDS" */
    val title: String get() = listOf(name, qualification).filter { it.isNotBlank() }.joinToString(", ")
}

// ───────────── Saved settings ─────────────
private fun ClinicDb.settingsMap(): Map<String, String> {
    val map = HashMap<String, String>()
    readableDatabase.rawQuery("SELECT key, value FROM settings", null).use { c ->
        while (c.moveToNext()) map[c.getString(0)] = c.getString(1) ?: ""
    }
    return map
}

private fun ClinicDb.putSetting(key: String, value: String) {
    val cv = ContentValues().apply {
        put("key", key)
        put("value", value)
    }
    writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Makes sure the doctors list exists, and moves the old single doctor into it once. */
private fun ClinicDb.ensureDoctors() {
    val sql = writableDatabase
    sql.execSQL(DOCTORS_TABLE)
    val map = settingsMap()
    if (map["doctors_moved"] != "1") {
        val oldName = map["doctor"].orEmpty()
        if (oldName.isNotBlank()) {
            val cv = ContentValues().apply {
                put("name", oldName)
                put("qualification", map["qualification"].orEmpty())
                put("reg_no", map["reg_no"].orEmpty())
                put("on_bills", 1)
                put("main", 1)
            }
            sql.insert("doctors", null, cv)
        }
        putSetting("doctors_moved", "1")
    }
}

fun ClinicDb.doctors(): List<Doctor> {
    ensureDoctors()
    val list = mutableListOf<Doctor>()
    readableDatabase.rawQuery(
        "SELECT id, name, qualification, specialty, reg_no, phone, on_bills, main, visiting FROM doctors " +
            "WHERE active = 1 ORDER BY main DESC, id",
        null
    ).use { c ->
        while (c.moveToNext()) {
            list.add(
                Doctor(
                    id = c.getLong(0), name = c.getString(1) ?: "", qualification = c.getString(2) ?: "",
                    specialty = c.getString(3) ?: "", regNo = c.getString(4) ?: "", phone = c.getString(5) ?: "",
                    onBills = c.getInt(6) == 1, main = c.getInt(7) == 1, visiting = c.getInt(8) == 1
                )
            )
        }
    }
    return list
}

fun ClinicDb.mainDoctor(): Doctor? = doctors().let { list -> list.firstOrNull { it.main } ?: list.firstOrNull() }

fun ClinicDb.saveDoctor(d: Doctor): Long {
    ensureDoctors()
    val sql = writableDatabase
    if (d.main) sql.execSQL("UPDATE doctors SET main = 0")
    val cv = ContentValues().apply {
        put("name", d.name)
        put("qualification", d.qualification)
        put("specialty", d.specialty)
        put("reg_no", d.regNo)
        put("phone", d.phone)
        put("on_bills", if (d.onBills) 1 else 0)
        put("main", if (d.main) 1 else 0)
        put("visiting", if (d.visiting) 1 else 0)
    }
    return if (d.id == 0L) {
        sql.insert("doctors", null, cv)
    } else {
        sql.update("doctors", cv, "id = ?", arrayOf(d.id.toString()))
        d.id
    }
}

/** Hides the doctor instead of erasing them, so older records that mention them still make sense. */
fun ClinicDb.deleteDoctor(id: Long) {
    ensureDoctors()
    val cv = ContentValues().apply {
        put("active", 0)
        put("main", 0)
    }
    writableDatabase.update("doctors", cv, "id = ?", arrayOf(id.toString()))
}

fun ClinicDb.clinicInfo(): ClinicInfo {
    val onBills = doctors().filter { it.onBills }
    val map = settingsMap()
    val withReg = onBills.filter { it.regNo.isNotBlank() }
    return ClinicInfo(
        name = map["clinic_name"]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLINIC_NAME,
        doctor = onBills.joinToString("  ·  ") { it.title },
        qualification = "",
        regNo = if (withReg.size == 1) withReg[0].regNo else withReg.joinToString("  ·  ") { "${it.name}: ${it.regNo}" },
        phone = map["phone"] ?: "",
        email = map["email"] ?: "",
        address = map["address"] ?: "",
        timings = map["timings"] ?: "",
        footer = map["footer"] ?: DEFAULT_FOOTER,
        paper = if (map["paper"] == "A5") "A5" else "A4"
    )
}

fun ClinicDb.saveClinicInfo(info: ClinicInfo) {
    val values = listOf(
        "clinic_name" to info.name, "phone" to info.phone, "email" to info.email, "address" to info.address,
        "timings" to info.timings, "footer" to info.footer, "paper" to info.paper
    )
    val sql = writableDatabase
    sql.beginTransaction()
    try {
        for ((key, value) in values) putSetting(key, value)
        sql.setTransactionSuccessful()
    } finally {
        sql.endTransaction()
    }
}

// ───────────── Clinic & doctors screen ─────────────
@Composable
fun ClinicSettingsScreen(db: ClinicDb, onBack: () -> Unit) {
    val notify = LocalNotify.current
    val saved = remember { db.clinicInfo() }
    var name by remember { mutableStateOf(saved.name) }
    var phone by remember { mutableStateOf(saved.phone) }
    var email by remember { mutableStateOf(saved.email) }
    var address by remember { mutableStateOf(saved.address) }
    var timings by remember { mutableStateOf(saved.timings) }
    var footer by remember { mutableStateOf(saved.footer) }
    var paper by remember { mutableStateOf(saved.paper) }
    var doctors by remember { mutableStateOf(db.doctors()) }
    var editing by remember { mutableStateOf<Doctor?>(null) }
    var preview by remember { mutableStateOf<ClinicInfo?>(null) }

    fun current() = db.clinicInfo().copy(
        name = name.trim().ifBlank { DEFAULT_CLINIC_NAME }, phone = phone.trim(), email = email.trim(),
        address = address.trim(), timings = timings.trim(), footer = footer.trim(), paper = paper
    )

    fun save() {
        db.saveClinicInfo(current())
        DataBus.changed()
        notify("Clinic details saved ✓")
        onBack()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val clinicCard: @Composable () -> Unit = {
            FormCard("Clinic", "Changes are used on every new bill and prescription") {
                AppField(name, { name = it }, "Clinic name *", icon = AppIcons.Tooth, caps = KeyboardCapitalization.Words)
                AppField(phone, { phone = it }, "Clinic phone", icon = Icons.Filled.Phone, type = KeyboardType.Phone)
                AppField(
                    email, { email = it }, "Email (optional)", icon = Icons.Filled.Email,
                    type = KeyboardType.Email, caps = KeyboardCapitalization.None
                )
                AppField(address, { address = it }, "Clinic address", icon = Icons.Filled.Place, lines = 2)
                AppField(timings, { timings = it }, "Timings, e.g. Mon–Sat 10 AM–2 PM, 5–9 PM", icon = Icons.Filled.DateRange)
            }
        }
        val doctorsCard: @Composable () -> Unit = {
            FormCard(
                "Doctors",
                if (doctors.isEmpty()) "Add the doctors who work at the clinic" else "Tap a doctor to change or remove"
            ) {
                doctors.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(color = Brand.Line)
                    DoctorRow(d) { editing = d }
                }
                SoftButton("Add doctor", Icons.Filled.Add, Modifier.fillMaxWidth()) {
                    editing = Doctor(name = "", main = doctors.isEmpty())
                }
            }
        }
        val printCard: @Composable () -> Unit = {
            FormCard("Printing", "Paper size and the message at the bottom of each page") {
                Segmented(listOf("A4", "A5"), paper) { paper = it }
                AppField(footer, { footer = it }, "Bottom message", lines = 2)
                SoftButton("Preview letterhead", DocIcons.Pdf, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    preview = current()
                }
            }
        }

        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar("Clinic & doctors", "Used on bills, prescriptions and messages", onBack)
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        doctorsCard()
                        printCard()
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) { clinicCard() }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    doctorsCard()
                    clinicCard()
                    printCard()
                }
            }
            BottomActionBar {
                if (twoColumn) {
                    Spacer(Modifier.weight(1f))
                    GradientButton("Save clinic details", Modifier.width(340.dp), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                } else {
                    GradientButton("Save clinic details", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                }
            }
        }
    }

    editing?.let { d ->
        DoctorDialog(
            initial = d,
            onDismiss = { editing = null },
            onDelete = if (d.id != 0L) ({
                db.deleteDoctor(d.id)
                doctors = db.doctors()
                editing = null
                DataBus.changed()
                notify("Doctor removed")
            }) else null
        ) { updated ->
            db.saveDoctor(updated)
            doctors = db.doctors()
            editing = null
            DataBus.changed()
            notify("Doctor saved ✓")
        }
    }
    preview?.let { info ->
        PdfActionsDialog(
            title = "Letterhead preview",
            subtitle = "A sample page with these details",
            phone = "",
            message = info.name,
            build = { ctx -> letterheadSamplePdf(ctx, info) },
            onDismiss = { preview = null }
        )
    }
}

@Composable
private fun DoctorRow(d: Doctor, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(d.name.replace(Regex("^(?i)dr\\.?\\s+"), "").ifBlank { d.name }, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(d.specialty, if (d.regNo.isNotBlank()) "Reg. ${d.regNo}" else "")
                .filter { it.isNotBlank() }.joinToString("  ·  ")
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (d.main) Pill("Main", Brand.GoldSoft, Brand.GoldDeep)
                if (d.visiting) Pill("Visiting", Brand.Soft, Brand.Ink)
                Pill(
                    if (d.onBills) "On bills" else "Not on bills",
                    if (d.onBills) Brand.GreenSoft else Brand.Soft,
                    if (d.onBills) Brand.GreenText else Brand.Sub
                )
            }
        }
    }
}

@Composable
private fun DoctorDialog(initial: Doctor, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Doctor) -> Unit) {
    var name by remember { mutableStateOf(initial.name.ifBlank { "Dr. " }) }
    var qualification by remember { mutableStateOf(initial.qualification) }
    var specialty by remember { mutableStateOf(initial.specialty) }
    var regNo by remember { mutableStateOf(initial.regNo) }
    var phone by remember { mutableStateOf(initial.phone) }
    var onBills by remember { mutableStateOf(initial.onBills) }
    var main by remember { mutableStateOf(initial.main) }
    var visiting by remember { mutableStateOf(initial.visiting) }
    var askDelete by remember { mutableStateOf(false) }
    val valid = name.replace(Regex("^(?i)dr\\.?\\s*"), "").isNotBlank()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Brand.Surface)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial.id == 0L) "Add doctor" else "Edit doctor",
                fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink
            )
            AppField(name, { name = it }, "Doctor name *", icon = Icons.Filled.Person, caps = KeyboardCapitalization.Words)
            AppField(qualification, { qualification = it }, "Qualification, e.g. BDS, MDS", caps = KeyboardCapitalization.Characters)
            Text("Speciality", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                specialtyOptions.forEach { s ->
                    ChoiceChip(s, specialty == s) { specialty = if (specialty == s) "" else s }
                }
            }
            AppField(specialty, { specialty = it }, "Or type the speciality", caps = KeyboardCapitalization.Words)
            AppField(regNo, { regNo = it }, "Registration number", caps = KeyboardCapitalization.Characters)
            AppField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Mobile (optional)", icon = Icons.Filled.Phone, type = KeyboardType.Phone)
            HorizontalDivider(color = Brand.Line)
            ToggleRow("Show on bills & prescriptions", "Name, qualification and registration number are printed", onBills) { onBills = it }
            ToggleRow("Main doctor", "Shown in the greeting and used as the signature", main) { main = it }
            ToggleRow("Visiting doctor", "A consultant who comes on certain days", visiting) { visiting = it }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 52.dp)
                }
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = valid, height = 52.dp) {
                    onSave(
                        initial.copy(
                            name = name.trim(), qualification = qualification.trim(), specialty = specialty.trim(),
                            regNo = regNo.trim(), phone = phone.trim(), onBills = onBills, main = main, visiting = visiting
                        )
                    )
                }
            }
        }
    }

    if (askDelete && onDelete != null) {
        ConfirmDialog(
            "Remove ${name.trim()}?",
            "They won't appear on new bills or prescriptions. Bills already sent are not changed.",
            confirmText = "Remove",
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false }
        )
    }
}
