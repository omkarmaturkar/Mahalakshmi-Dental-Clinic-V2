@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** New patient (patientId = 0) or edit all details of an existing patient. */
@Composable
fun PatientEditScreen(
    db: ClinicDb,
    patientId: Long,
    onBack: () -> Unit,
    onSaved: (Long, Boolean) -> Unit,
    onDeleted: () -> Unit
) {
    val notify = LocalNotify.current
    val existing = remember { if (patientId != 0L) db.getPatient(patientId) else null }
    val everyone = remember { db.getPatients("") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var age by remember { mutableStateOf(existing?.age ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val medical = remember { mutableStateListOf<String>().apply { addAll(existing?.alerts ?: emptyList()) } }
    var otherAlert by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(existing != null && existing.notes.isNotBlank()) }
    var askDelete by remember { mutableStateOf(false) }

    // Warn if this mobile number is already registered for another patient
    val duplicate = remember(phone) {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        if (digits.length < 10) null
        else everyone.firstOrNull { it.id != patientId && it.phone.filter { c -> c.isDigit() }.takeLast(10) == digits }
    }

    fun save() {
        val id = db.savePatient(
            Patient(
                id = patientId, name = name.trim(), phone = phone.trim(), age = age.trim(), gender = gender,
                medical = medical.joinToString(", "), notes = notes.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        DataBus.changed()
        notify(if (existing == null) "Patient added ✓" else "Changes saved ✓")
        onSaved(id, existing == null)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar(
                if (existing == null) "New patient" else "Edit patient",
                if (existing == null) "Only the name is required" else existing.name,
                onBack
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 24.dp else 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormCard("Patient") {
                        AppField(name, { name = it }, "Full name *", icon = Icons.Filled.Person, caps = KeyboardCapitalization.Words)
                        AppField(
                            phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Mobile number",
                            icon = Icons.Filled.Phone, type = KeyboardType.Phone
                        )
                        duplicate?.let { d ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.GoldSoft).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Warning, null, tint = Brand.GoldDeep, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "This number belongs to ${d.name}", fontSize = 13.sp, color = Brand.GoldDeep,
                                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                                )
                                SoftButton("Open", null, height = 36.dp, bg = Color.White, fg = Brand.GoldDeep) { onSaved(d.id, true) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppField(age, { age = it.filter { c -> c.isDigit() }.take(3) }, "Age", Modifier.width(110.dp), type = KeyboardType.Number)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.weight(1f)) {
                                Segmented(listOf("Male", "Female", "Other"), gender) { gender = if (gender == it) "" else it }
                            }
                        }
                    }
                    FormCard("Medical alerts", "Shown in red at the top of the patient file") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (medicalOptions + medical.filter { it !in medicalOptions }).forEach { m ->
                                ChoiceChip(m, m in medical, selectedBg = Brand.Red) {
                                    if (m in medical) medical.remove(m) else medical.add(m)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppField(otherAlert, { otherAlert = it }, "Other, e.g. Penicillin allergy", Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            CircleIconButton(
                                Icons.Filled.Add,
                                {
                                    val a = otherAlert.trim().replace(",", " ")
                                    if (a.isNotEmpty() && a !in medical) medical.add(a)
                                    otherAlert = ""
                                },
                                bg = Brand.RedSoft, tint = Brand.Red, size = 54.dp
                            )
                        }
                    }
                    if (more) {
                        FormCard("More details") {
                            AppField(notes, { notes = it }, "Address, referred by, other notes", lines = 3)
                        }
                    } else {
                        SoftButton("Add address or other notes", Icons.Filled.Add, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                            more = true
                        }
                    }
                    if (existing != null) {
                        Text(
                            "Delete this patient", color = Brand.Red, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { askDelete = true }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            BottomActionBar {
                val label = if (existing == null) "Save & open file" else "Save changes"
                if (wide) {
                    Spacer(Modifier.weight(1f))
                    GradientButton(label, Modifier.width(340.dp), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                } else {
                    GradientButton(label, Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank()) { save() }
                }
            }
        }
    }

    if (askDelete) {
        ConfirmDialog(
            "Delete ${existing?.name ?: "this patient"}?",
            "All treatments, payments, prescriptions and photos of this patient will be permanently deleted.",
            onConfirm = {
                askDelete = false
                db.deletePatient(patientId)
                DataBus.changed()
                notify("Patient deleted")
                onDeleted()
            },
            onDismiss = { askDelete = false }
        )
    }
}
