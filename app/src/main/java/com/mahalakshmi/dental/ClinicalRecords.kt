package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ───────────── Clinical records in the patient file ─────────────
// Mouth status, gum health, braces and patient education as short one-line rows.
// Mouth status and education open straight away; gum health and braces open their card under the list.

@Composable
fun ClinicalRecordsCard(db: ClinicDb, patientId: Long) {
    val version = DataBus.version
    val mouth = remember(version) { runCatching { db.mouthStatusOf(patientId) }.getOrElse { emptyList() } }
    val exams = remember(version) { runCatching { db.perioExamsOf(patientId) }.getOrElse { emptyList() } }
    val braces = remember(version) { runCatching { db.orthoCaseOf(patientId) }.getOrNull() }
    val bracesTreatment = remember(version) { db.treatmentsOf(patientId).any { orthoLike(it.name) } }
    var expanded by remember { mutableStateOf<String?>(null) }
    var mouthOpen by remember { mutableStateOf(false) }
    var explainOpen by remember { mutableStateOf(false) }

    val mouthSummary = run {
        val parts = listOf(
            mouth.count { it.decay.isNotEmpty() } to "decay",
            mouth.count { it.filled.isNotEmpty() } to "filled",
            mouth.count { it.crown } to "crown",
            mouth.count { it.rct } to "root canal",
            mouth.count { it.implant } to "implant",
            mouth.count { it.missing } to "missing",
            mouth.count { it.broken } to "broken"
        ).filter { it.first > 0 }.map { "${it.first} ${it.second}" }
        when {
            mouth.isEmpty() -> "Not recorded yet"
            parts.isEmpty() -> "No problems marked"
            else -> parts.take(3).joinToString(" · ")
        }
    }
    val gumSummary = exams.firstOrNull()?.let { e ->
        val deep = e.stats().deep
        "${fmtDate(e.date)} · " + if (deep == 1) "1 deep pocket" else "$deep deep pockets"
    } ?: "No check-up yet"
    val bracesSummary = braces?.let { o ->
        when (o.status) {
            ORTHO_PLANNING -> "Planning"
            ORTHO_ACTIVE -> {
                val start = Instant.ofEpochMilli(o.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
                val month = ChronoUnit.MONTHS.between(start, LocalDate.now()).toInt().coerceAtLeast(0) + 1
                "Month $month of ${o.months}"
            }
            ORTHO_RETENTION -> "Retention"
            else -> "Finished"
        }
    } ?: "Start the case sheet"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppCard(Modifier.fillMaxWidth(), padding = 6.dp) {
            RecordRow(AppIcons.Tooth, "Mouth status", mouthSummary, null) { mouthOpen = true }
            HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 10.dp))
            RecordRow(Icons.Filled.Favorite, "Gum health", gumSummary, expanded == "gum") {
                expanded = if (expanded == "gum") null else "gum"
            }
            if (braces != null || bracesTreatment) {
                HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 10.dp))
                RecordRow(Icons.Filled.Build, "Braces case", bracesSummary, expanded == "braces") {
                    expanded = if (expanded == "braces") null else "braces"
                }
            }
            HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 10.dp))
            RecordRow(Icons.Filled.Info, "Explain to patient", "English, हिंदी, मराठी", null) { explainOpen = true }
        }
        when (expanded) {
            "gum" -> PerioCard(db, patientId)
            "braces" -> OrthoCard(db, patientId)
        }
    }
    if (mouthOpen) MouthStatusScreen(db, patientId) { mouthOpen = false }
    if (explainOpen) EducationScreen(db, patientId) { explainOpen = false }
}

/** One line: icon, name, short summary and an arrow. open = null opens a full screen; true / false shows or hides a card below. */
@Composable
private fun RecordRow(icon: ImageVector, title: String, summary: String, open: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.Soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand.Slate, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            when (open) {
                null -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                true -> Icons.Filled.KeyboardArrowUp
                false -> Icons.Filled.KeyboardArrowDown
            },
            null, tint = Brand.Faint
        )
    }
}
