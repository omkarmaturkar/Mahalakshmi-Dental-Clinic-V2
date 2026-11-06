@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ───────────── Data ─────────────
data class Medicine(val name: String, val dose: String = "", val timing: String = "", val days: String = "", val note: String = "")

data class Prescription(
    val id: Long = 0,
    val patientId: Long,
    val visitId: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val diagnosis: String = "",
    val medicines: List<Medicine> = emptyList(),
    val advice: String = ""
)

data class RxTemplate(val id: Long, val name: String, val medicines: List<Medicine>, val advice: String)

// Quick choices – the doctor can always type anything else
val commonMedicines = listOf(
    "Cap. Amoxicillin 500 mg", "Tab. Amoxicillin + Clavulanic Acid 625 mg", "Tab. Metronidazole 400 mg",
    "Tab. Azithromycin 500 mg", "Cap. Clindamycin 300 mg", "Tab. Ibuprofen 400 mg", "Tab. Paracetamol 650 mg",
    "Tab. Aceclofenac 100 mg + Paracetamol 325 mg", "Tab. Ketorolac 10 mg", "Tab. Pantoprazole 40 mg",
    "Chlorhexidine 0.2% Mouthwash", "Benzydamine Mouthwash", "Triamcinolone Oral Paste",
    "Lignocaine 2% Gel", "Desensitizing Toothpaste"
)
val doseOptions = listOf("1-0-1", "1-1-1", "1-0-0", "0-0-1", "SOS")
val timingOptions = listOf("After food", "Before food")
val dayOptions = listOf("3 days", "5 days", "7 days")
val adviceOptions = listOf(
    "Do not eat or drink for 1 hour",
    "Avoid hot and spicy food today",
    "Do not spit or rinse for 24 hours",
    "Apply an ice pack on the cheek for the first few hours",
    "Eat soft food for 2–3 days",
    "Avoid chewing on the treated side",
    "Warm salt-water rinses from tomorrow",
    "Brush twice daily and floss once daily",
    "Avoid smoking and tobacco",
    "Come back if pain or swelling increases"
)

private fun medicinesToJson(list: List<Medicine>): String {
    val arr = JSONArray()
    list.forEach { m ->
        arr.put(
            JSONObject().put("name", m.name).put("dose", m.dose).put("timing", m.timing)
                .put("days", m.days).put("note", m.note)
        )
    }
    return arr.toString()
}

private fun medicinesFromJson(text: String?): List<Medicine> = try {
    val arr = JSONArray(text ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Medicine(o.optString("name"), o.optString("dose"), o.optString("timing"), o.optString("days"), o.optString("note"))
    }.filter { it.name.isNotBlank() }
} catch (e: Exception) {
    emptyList()
}

// ───────────── Database ─────────────
private const val RX_COLS = "id, patient_id, visit_id, date, diagnosis, medicines, advice"

private fun rxFrom(c: Cursor) = Prescription(
    id = c.getLong(0), patientId = c.getLong(1), visitId = c.getLong(2), date = c.getLong(3),
    diagnosis = c.getString(4) ?: "", medicines = medicinesFromJson(c.getString(5)), advice = c.getString(6) ?: ""
)

fun ClinicDb.getPrescriptionById(id: Long): Prescription? =
    readableDatabase.rawQuery("SELECT $RX_COLS FROM prescriptions WHERE id = ?", arrayOf(id.toString()))
        .use { c -> if (c.moveToFirst()) rxFrom(c) else null }

fun ClinicDb.prescriptionsOf(patientId: Long): List<Prescription> {
    val list = mutableListOf<Prescription>()
    readableDatabase.rawQuery(
        "SELECT $RX_COLS FROM prescriptions WHERE patient_id = ? ORDER BY date DESC, id DESC",
        arrayOf(patientId.toString())
    ).use { c -> while (c.moveToNext()) list.add(rxFrom(c)) }
    return list
}

fun ClinicDb.savePrescription(rx: Prescription): Long {
    val cv = ContentValues().apply {
        put("patient_id", rx.patientId)
        put("visit_id", rx.visitId)
        put("date", rx.date)
        put("diagnosis", rx.diagnosis)
        put("medicines", medicinesToJson(rx.medicines))
        put("advice", rx.advice)
    }
    return if (rx.id == 0L) {
        writableDatabase.insert("prescriptions", null, cv)
    } else {
        writableDatabase.update("prescriptions", cv, "id = ?", arrayOf(rx.id.toString()))
        rx.id
    }
}

fun ClinicDb.deletePrescription(id: Long) {
    writableDatabase.delete("prescriptions", "id = ?", arrayOf(id.toString()))
}

// Used by older screens
fun ClinicDb.prescribedVisitIds(patientId: Long): Set<Long> {
    val set = HashSet<Long>()
    readableDatabase.rawQuery("SELECT visit_id FROM prescriptions WHERE patient_id = ?", arrayOf(patientId.toString()))
        .use { c -> while (c.moveToNext()) set.add(c.getLong(0)) }
    return set
}

fun ClinicDb.rxTemplates(): List<RxTemplate> {
    val list = mutableListOf<RxTemplate>()
    readableDatabase.rawQuery("SELECT id, name, medicines, advice FROM rx_templates ORDER BY name COLLATE NOCASE", null)
        .use { c ->
            while (c.moveToNext()) {
                list.add(RxTemplate(c.getLong(0), c.getString(1) ?: "", medicinesFromJson(c.getString(2)), c.getString(3) ?: ""))
            }
        }
    return list
}

fun ClinicDb.saveRxTemplate(name: String, medicines: List<Medicine>, advice: String) {
    val cv = ContentValues().apply {
        put("name", name)
        put("medicines", medicinesToJson(medicines))
        put("advice", advice)
    }
    val id = readableDatabase.rawQuery("SELECT id FROM rx_templates WHERE name = ? COLLATE NOCASE", arrayOf(name))
        .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    if (id == 0L) {
        writableDatabase.insert("rx_templates", null, cv)
    } else {
        writableDatabase.update("rx_templates", cv, "id = ?", arrayOf(id.toString()))
    }
}

fun ClinicDb.deleteRxTemplate(id: Long) {
    writableDatabase.delete("rx_templates", "id = ?", arrayOf(id.toString()))
}

/** Today's treatments, written as a diagnosis line, e.g. "Root Canal (tooth 36)". */
private fun ClinicDb.suggestedDiagnosis(patientId: Long): String {
    val today = startOfToday()
    return treatmentsOf(patientId)
        .filter { it.status == STATUS_ONGOING || (it.status == STATUS_DONE && it.doneAt >= today) }
        .joinToString(", ") { t -> if (t.teethList.isEmpty()) t.name else "${t.name} (tooth ${t.teethList.joinToString(", ")})" }
}

// ───────────── Prescription screen ─────────────
@Composable
fun PrescriptionScreen(db: ClinicDb, patientId: Long, rxId: Long, onBack: () -> Unit) {
    val notify = LocalNotify.current
    val patient = remember { db.getPatient(patientId) }
    val existing = remember { if (rxId != 0L) db.getPrescriptionById(rxId) else null }
    if (patient == null || (rxId != 0L && existing == null)) return
    val suggested = remember { if (existing == null) db.suggestedDiagnosis(patientId) else "" }
    val createdAt = remember { existing?.date ?: System.currentTimeMillis() }

    var currentId by remember { mutableLongStateOf(existing?.id ?: 0L) }
    var diagnosis by remember { mutableStateOf(existing?.diagnosis ?: suggested) }
    val medicines = remember { mutableStateListOf<Medicine>().apply { addAll(existing?.medicines ?: emptyList()) } }
    var advice by remember { mutableStateOf(existing?.advice ?: "") }
    var templates by remember { mutableStateOf(db.rxTemplates()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var removingTemplates by remember { mutableStateOf(false) }
    var askTemplateName by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    val canSave = medicines.isNotEmpty() || diagnosis.isNotBlank() || advice.isNotBlank()

    fun save() {
        currentId = db.savePrescription(
            Prescription(
                id = currentId, patientId = patientId, visitId = existing?.visitId ?: 0L, date = createdAt,
                diagnosis = diagnosis.trim(), medicines = medicines.toList(), advice = advice.trim()
            )
        )
        if (existing == null) db.markArrived(patientId)
        DataBus.changed()
    }

    fun applyTemplate(t: RxTemplate) {
        t.medicines.forEach { m -> if (medicines.none { it.name.equals(m.name, ignoreCase = true) }) medicines.add(m) }
        val lines = advice.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        t.advice.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in lines }.forEach { lines.add(it) }
        advice = lines.joinToString("\n")
        notify("${t.name} added ✓")
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val patientCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(patient.name, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            patient.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val info = listOf(
                            patient.gender, if (patient.age.isNotBlank()) "${patient.age} yrs" else "",
                            "Date ${fmtDate(createdAt)}"
                        ).filter { it.isNotBlank() }.joinToString("  ·  ")
                        Text(info, fontSize = 12.sp, color = Brand.Sub)
                    }
                }
                if (patient.alerts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.RedSoft).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Medical alerts: " + patient.alerts.joinToString(", "),
                            fontSize = 13.sp, color = Brand.Red, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        val medicinesCard: @Composable () -> Unit = {
            FormCard("Medicines", if (medicines.isEmpty()) "Add medicines or use a template" else "Tap a medicine to change it") {
                medicines.forEachIndexed { i, m ->
                    MedicineRow(i + 1, m, onEdit = { editing = i }, onRemove = { medicines.removeAt(i) })
                }
                GradientButton("Add medicine", Modifier.fillMaxWidth(), Icons.Filled.Add, height = 50.dp) { editing = -1 }
            }
        }
        val templatesCard: @Composable () -> Unit = {
            FormCard(
                "Templates",
                if (templates.isEmpty()) "Save medicines you prescribe often, then add them with one tap"
                else if (removingTemplates) "Tap a template to delete it" else "Tap to add its medicines and advice"
            ) {
                if (templates.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { t ->
                            TemplateChip(t.name, removingTemplates) {
                                if (removingTemplates) {
                                    db.deleteRxTemplate(t.id)
                                    templates = db.rxTemplates()
                                    if (templates.isEmpty()) removingTemplates = false
                                } else {
                                    applyTemplate(t)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Save as template", Icons.Filled.Add, Modifier.weight(1f), height = 46.dp) {
                        if (medicines.isEmpty()) notify("Add medicines first") else askTemplateName = true
                    }
                    if (templates.isNotEmpty()) {
                        SoftButton(
                            if (removingTemplates) "Done" else "Delete", null, height = 46.dp,
                            bg = Brand.Soft, fg = if (removingTemplates) Brand.Ink else Brand.Red
                        ) { removingTemplates = !removingTemplates }
                    }
                }
            }
        }
        val diagnosisCard: @Composable () -> Unit = {
            FormCard("Diagnosis / treatment") {
                AppField(diagnosis, { diagnosis = it }, "e.g. Root Canal (tooth 36)", lines = 2)
            }
        }
        val adviceCard: @Composable () -> Unit = {
            FormCard("Advice", "Tap to add or remove, or type your own") {
                val current = advice.lines().map { it.trim() }.filter { it.isNotEmpty() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    adviceOptions.forEach { a ->
                        ChoiceChip(a, a in current) {
                            advice = (if (a in current) current - a else current + a).joinToString("\n")
                        }
                    }
                }
                AppField(advice, { advice = it }, "Advice (one per line)", lines = 4)
            }
        }

        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar(if (existing == null) "New prescription" else "Prescription", patient.name, onBack)
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        patientCard()
                        medicinesCard()
                        templatesCard()
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        diagnosisCard()
                        adviceCard()
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    patientCard()
                    templatesCard()
                    medicinesCard()
                    diagnosisCard()
                    adviceCard()
                }
            }
            BottomActionBar {
                if (currentId != 0L) {
                    CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 56.dp)
                }
                if (twoColumn) Spacer(Modifier.weight(1f))
                SoftButton(
                    "Save", Icons.Filled.Check,
                    if (twoColumn) Modifier.width(150.dp) else Modifier.weight(1f), height = 56.dp
                ) {
                    if (canSave) {
                        save()
                        notify("Prescription saved ✓")
                        onBack()
                    } else {
                        notify("Add at least one medicine")
                    }
                }
                GradientButton(
                    "Save & send", if (twoColumn) Modifier.width(240.dp) else Modifier.weight(1.5f),
                    DocIcons.Pdf, enabled = canSave
                ) {
                    save()
                    share = true
                }
            }
        }
    }

    editing?.let { index ->
        MedicineDialog(medicines.getOrNull(index), onDismiss = { editing = null }) { m ->
            if (index in medicines.indices) medicines[index] = m else medicines.add(m)
            editing = null
        }
    }
    if (askTemplateName) {
        TemplateNameDialog(onDismiss = { askTemplateName = false }) { name ->
            db.saveRxTemplate(name, medicines.toList(), advice.trim())
            templates = db.rxTemplates()
            askTemplateName = false
            notify("Template saved ✓")
        }
    }
    if (askDelete) ConfirmDialog(
        "Delete prescription?",
        "Only this prescription is removed. Treatments and payments are not affected.",
        onConfirm = {
            askDelete = false
            db.deletePrescription(currentId)
            DataBus.changed()
            notify("Prescription deleted")
            onBack()
        },
        onDismiss = { askDelete = false }
    )
    if (share) {
        val first = patient.name.trim().substringBefore(" ")
        val idForPdf = currentId
        PdfActionsDialog(
            title = "Prescription ready",
            subtitle = "Saved ✓  Send it to ${patient.name} or print it",
            phone = patient.phone,
            message = "Hello $first, here is your prescription from ${db.clinicInfo().name}. Get well soon!",
            build = { ctx -> prescriptionPdf(ctx, db, idForPdf) },
            onDismiss = {
                share = false
                onBack()
            }
        )
    }
}

@Composable
private fun MedicineRow(number: Int, m: Medicine, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Soft).clickable(onClick = onEdit).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Brand.Surface), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
            val info = listOf(m.dose, m.timing, m.days).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (info.isNotBlank()) Text(info, fontSize = 13.sp, color = Brand.GoldDeep, fontWeight = FontWeight.Medium)
            if (m.note.isNotBlank()) Text(m.note, fontSize = 12.sp, color = Brand.Sub)
        }
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, null, tint = Brand.Sub, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TemplateChip(text: String, removing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (removing) Brand.RedSoft else Brand.Soft)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (removing) Icons.Filled.Close else DocIcons.Pill, null,
            tint = if (removing) Brand.Red else Brand.GoldDeep, modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (removing) Brand.Red else Brand.Ink, maxLines = 1)
    }
}

@Composable
private fun DialogLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
}

@Composable
private fun MedicineDialog(initial: Medicine?, onDismiss: () -> Unit, onSave: (Medicine) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "1-0-1") }
    var timing by remember { mutableStateOf(initial?.timing ?: "After food") }
    var days by remember { mutableStateOf(initial?.days ?: "5 days") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val query = name.trim()
    val suggestions = remember(query) {
        if (query.isEmpty()) commonMedicines.take(8)
        else commonMedicines.filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }.take(6)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Brand.Surface)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial == null) "Add medicine" else "Edit medicine",
                fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink
            )
            AppField(name, { name = it }, "Medicine name *", icon = DocIcons.Pill, caps = KeyboardCapitalization.Words)
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s ->
                        Text(
                            s, fontSize = 13.sp, color = Brand.Ink, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Soft)
                                .clickable { name = s }.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            DialogLabel("Dosage (morning - afternoon - night)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                doseOptions.forEach { d -> ChoiceChip(d, dose == d) { dose = d } }
            }
            AppField(dose, { dose = it }, "Dosage")
            DialogLabel("When to take")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timingOptions.forEach { t -> ChoiceChip(t, timing == t) { timing = if (timing == t) "" else t } }
            }
            DialogLabel("For how long")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dayOptions.forEach { d -> ChoiceChip(d, days == d) { days = d } }
            }
            AppField(days, { days = it }, "Duration")
            AppField(note, { note = it }, "Note (optional), e.g. Rinse after meals")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank(), height = 52.dp) {
                    onSave(Medicine(name.trim(), dose.trim(), timing.trim(), days.trim(), note.trim()))
                }
            }
        }
    }
}

@Composable
private fun TemplateNameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Text("Save as template", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
            Text("Use a short name, e.g. After extraction", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(16.dp))
            AppField(name, { name = it }, "Template name")
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank(), height = 52.dp) {
                    onSave(name.trim())
                }
            }
        }
    }
}

// ───────────── Prescription PDF ─────────────
fun prescriptionPdf(context: Context, db: ClinicDb, rxId: Long): File {
    val rx = db.getPrescriptionById(rxId) ?: error("Prescription not found")
    val patient = db.getPatient(rx.patientId) ?: error("Patient not found")
    val next = db.nextBooking(rx.patientId)
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("PRESCRIPTION", "Date: ${fmtDate(rx.date)}")
    pdf.patientBox(patient)
    if (patient.alerts.isNotEmpty()) {
        pdf.text("Medical alerts: " + patient.alerts.joinToString(", "), 10f, PdfRed, 1, after = 8f)
    }
    if (rx.diagnosis.isNotBlank()) {
        pdf.text("Diagnosis / Treatment", 9.5f, PdfSub, 1, after = 1f)
        pdf.text(rx.diagnosis, 11f, after = 10f)
    }
    if (rx.medicines.isNotEmpty()) {
        pdf.text("Rx", 20f, PdfGreen, 2, after = 2f)
        pdf.row(
            listOf(
                PdfCell("#", 0.06f, 9f, PdfSub, 1),
                PdfCell("Medicine", 0.44f, 9f, PdfSub, 1),
                PdfCell("Dosage", 0.16f, 9f, PdfSub, 1),
                PdfCell("When", 0.17f, 9f, PdfSub, 1),
                PdfCell("Duration", 0.17f, 9f, PdfSub, 1)
            ),
            fill = PdfSoft
        )
        rx.medicines.forEachIndexed { i, m ->
            val name = SpannableStringBuilder(m.name)
            if (m.note.isNotBlank()) {
                val start = name.length
                name.append("\n").append(m.note)
                name.setSpan(RelativeSizeSpan(0.85f), start, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                name.setSpan(ForegroundColorSpan(PdfSub), start, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            pdf.row(
                listOf(
                    PdfCell("${i + 1}", 0.06f, 10.5f, PdfSub),
                    PdfCell(name, 0.44f, 10.5f, PdfInk, 1),
                    PdfCell(m.dose.ifBlank { "–" }, 0.16f, 10.5f),
                    PdfCell(m.timing.ifBlank { "–" }, 0.17f, 10f),
                    PdfCell(m.days.ifBlank { "–" }, 0.17f, 10f)
                ),
                pad = 7f, line = true
            )
        }
        if (rx.medicines.any { Regex("\\d-\\d-\\d").matches(it.dose.trim()) }) {
            pdf.space(4f)
            pdf.text("1-0-1 means: morning – afternoon – night", 8.5f, PdfSub, after = 8f)
        }
    }
    val adviceLines = rx.advice.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (adviceLines.isNotEmpty()) {
        pdf.space(8f)
        pdf.text("Advice", 11f, PdfGreen, 2, after = 3f)
        adviceLines.forEach { pdf.text("•  $it", 10.5f, after = 2f) }
    }
    if (next != null) {
        val whenText = fmtDayLong(next.day) + (if (next.time.isNotBlank()) ", ${next.time}" else "")
        pdf.space(10f)
        pdf.text("Next visit: $whenText", 11f, PdfInk, 2)
    }
    pdf.signature()
    return pdf.save(pdfFile(context, "Prescription", patient.name))
}
