@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ───────────── Allergies ─────────────
// Saved with the patient's medical alerts as "Allergy: Penicillin", so they show in red everywhere.
// Older alerts typed as "Penicillin allergy" are understood too.

val allergyOptions = listOf(
    "Penicillin", "Sulfa drugs", "Painkillers (NSAIDs)", "Aspirin", "Metronidazole", "Local anaesthetic", "Latex"
)

private val allergyPatterns = listOf(
    Regex("^allergy\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE),
    Regex("^allergic to\\s+(.+)$", RegexOption.IGNORE_CASE),
    Regex("^(.+?)\\s+allergy$", RegexOption.IGNORE_CASE)
)
private val notAllergyNames = setOf("drug", "drugs", "medicine", "medicines", "food", "no", "no known", "any")

/** The allergy named in a medical alert ("Penicillin" in "Allergy: Penicillin"), or null if it is not an allergy. */
fun allergyName(alert: String): String? {
    val t = alert.trim()
    for (p in allergyPatterns) {
        val name = p.find(t)?.groupValues?.get(1)?.trim()
        if (!name.isNullOrEmpty() && name.lowercase() !in notAllergyNames) return name
    }
    return null
}

/** All allergies among a patient's medical alerts. */
fun allergiesIn(alerts: List<String>): List<String> = alerts.mapNotNull { allergyName(it) }.distinct()

// Which medicines belong to each allergy. Doctor sir can check this list in the TEST app.
private class AllergyGroup(val names: List<String>, val medicines: List<String>)

private val allergyGroups = listOf(
    AllergyGroup(
        listOf("penicillin", "amoxicillin", "amoxycillin", "augmentin"),
        listOf("cillin", "augmentin", "clavulan", "clavam", "amoxyclav", "amoxiclav")
    ),
    AllergyGroup(
        listOf("sulfa", "sulpha"),
        listOf("sulfa", "sulpha", "cotrimoxazole", "co-trimoxazole", "septran", "bactrim")
    ),
    AllergyGroup(
        listOf("nsaid", "painkiller", "ibuprofen", "diclofenac"),
        listOf(
            "ibuprofen", "diclofenac", "aceclofenac", "ketorolac", "naproxen", "nimesulide", "mefenamic", "aspirin",
            "etoricoxib", "piroxicam", "indomethacin", "ketoprofen", "flurbiprofen", "combiflam", "brufen", "voveran",
            "zerodol", "meftal", "dolonex", "ketorol", "ecosprin", "disprin"
        )
    ),
    AllergyGroup(listOf("aspirin"), listOf("aspirin", "ecosprin", "disprin", "acetylsalicylic")),
    AllergyGroup(
        listOf("metronidazole", "metrogyl", "flagyl"),
        listOf("metronidazole", "metrogyl", "flagyl", "tinidazole", "ornidazole")
    ),
    AllergyGroup(
        listOf("anaesthetic", "anesthetic", "lignocaine", "lidocaine", "xylocaine"),
        listOf("lignocaine", "lidocaine", "xylocaine", "benzocaine", "articaine", "bupivacaine", "prilocaine")
    )
)

/** The allergy that a medicine may clash with, or null when it looks safe. */
fun allergyClash(allergies: List<String>, medicine: String): String? {
    val med = medicine.lowercase()
    for (a in allergies) {
        val name = a.lowercase()
        val words = allergyGroups.filter { g -> g.names.any { name.contains(it) } }.flatMap { it.medicines }.toMutableList()
        if (name.length >= 4) words.add(name)
        if (words.any { med.contains(it) }) return a
    }
    return null
}

// ───────────── Patient form ─────────────
/** Allergy chips on the patient form. Tapping one adds or removes "Allergy: …" in the medical alerts. */
@Composable
fun AllergiesCard(medical: MutableList<String>) {
    var other by remember { mutableStateOf("") }
    val chosen = medical.mapNotNull { allergyName(it) }
    FormCard("Allergies", "Shown in red on every prescription, with a warning before a matching medicine") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (allergyOptions + chosen.filter { c -> allergyOptions.none { it.equals(c, ignoreCase = true) } }).forEach { a ->
                val on = chosen.any { it.equals(a, ignoreCase = true) }
                ChoiceChip(a, on, selectedBg = Brand.Red) {
                    if (on) {
                        medical.removeAll(medical.filter { allergyName(it)?.equals(a, ignoreCase = true) == true })
                    } else {
                        medical.add("Allergy: $a")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppField(other, { other = it }, "Other allergy, e.g. Iodine", Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            CircleIconButton(
                Icons.Filled.Add,
                {
                    val a = other.trim().replace(",", " ")
                    if (a.isNotEmpty() && chosen.none { it.equals(a, ignoreCase = true) }) medical.add("Allergy: $a")
                    other = ""
                },
                bg = Brand.RedSoft, tint = Brand.Red, size = 54.dp
            )
        }
    }
}

// ───────────── Prescription ─────────────
/** The red strip on the prescription: allergies first, then the other medical alerts. */
@Composable
fun RowScope.AlertLines(alerts: List<String>) {
    val allergies = allergiesIn(alerts)
    val others = alerts.filter { allergyName(it) == null }
    Column(Modifier.weight(1f)) {
        if (allergies.isNotEmpty()) {
            Text("Allergic to: " + allergies.joinToString(", "), fontSize = 14.sp, color = Brand.Red, fontWeight = FontWeight.Bold)
        }
        if (others.isNotEmpty()) {
            Text("Medical alerts: " + others.joinToString(", "), fontSize = 13.sp, color = Brand.Red, fontWeight = FontWeight.Medium)
        }
    }
}

/** "What we found" with BP and sugar boxes on top. */
@Composable
fun FindingsVitalsCard(
    text: String,
    bp: String,
    sugar: String,
    onBp: (String) -> Unit,
    onSugar: (String) -> Unit,
    onChange: (String) -> Unit
) {
    FormCard("What we found", "On examination (optional)") {
        Row {
            AppField(bp, { v -> onBp(v.filter { it.isDigit() || it == '/' }.take(7)) }, "BP, e.g. 120/80", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            AppField(
                sugar, { v -> onSugar(v.filter { it.isDigit() }.take(3)) }, "Sugar (mg/dL)", Modifier.weight(1f),
                type = KeyboardType.Number
            )
        }
        AppField(text, onChange, "Findings, e.g. deep cavity in 46, tender on tapping", lines = 2)
    }
}

/** Shown before adding a medicine that may clash with the patient's allergy. */
@Composable
fun AllergyWarningDialog(
    patientName: String,
    medicine: String,
    allergy: String,
    onKeepOut: () -> Unit,
    onAddAnyway: () -> Unit
) {
    val first = patientName.trim().substringBefore(" ").ifBlank { "This patient" }
    Dialog(onDismissRequest = onKeepOut) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Brand.RedSoft),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Warning, null, tint = Brand.Red) }
            Spacer(Modifier.height(16.dp))
            Text("Allergy alert", fontFamily = ClinicTitleFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Spacer(Modifier.height(6.dp))
            Text("$first is allergic to $allergy. $medicine may cause a reaction.", fontSize = 15.sp, color = Brand.Ink)
            Spacer(Modifier.height(24.dp))
            GradientButton("Don't add", Modifier.fillMaxWidth(), height = 52.dp, onClick = onKeepOut)
            Spacer(Modifier.height(6.dp))
            Text(
                "Add anyway", color = Brand.Red, fontWeight = FontWeight.Medium, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAddAnyway)
                    .padding(vertical = 12.dp)
            )
        }
    }
}
