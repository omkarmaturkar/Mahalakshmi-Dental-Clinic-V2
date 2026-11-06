@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────── Shared helpers ─────────────
fun filterPatients(all: List<Patient>, search: String, filter: Int, todayIds: Set<Long>): List<Patient> {
    val q = search.trim().lowercase()
    val todayStart = startOfToday()
    val list = all.filter { p ->
        val matches = q.isEmpty() || p.name.lowercase().contains(q) || p.phone.contains(q)
        val inFilter = when (filter) {
            1 -> p.remaining > 0.0
            2 -> p.lastVisit >= todayStart || p.id in todayIds
            else -> true
        }
        matches && inFilter
    }
    return if (filter == 1) list.sortedByDescending { it.remaining } else list
}

fun patientSubtitle(p: Patient): String =
    if (p.lastVisit > 0L) "${p.lastReason.substringBefore(",").ifBlank { "Visit" }} · ${relativeTime(p.lastVisit)}"
    else p.phone.ifBlank { "New patient" }

@Composable
fun PatientFilters(filter: Int, total: Int, dues: Int, onFilter: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("All  $total", filter == 0) { onFilter(0) }
        ChoiceChip("Dues  $dues", filter == 1, selectedBg = Brand.Red) { onFilter(1) }
        ChoiceChip("Today", filter == 2) { onFilter(2) }
    }
}

@Composable
fun DueBadge(p: Patient) {
    when {
        p.remaining > 0 -> Column(horizontalAlignment = Alignment.End) {
            Text(rs(p.remaining), color = Brand.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("due", color = Brand.Red.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        p.totalFees > 0 -> Pill("Paid", Brand.GreenSoft, Brand.GreenText)
        else -> {}
    }
}

@Composable
fun PatientCard(p: Patient, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 50.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (p.alerts.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(15.dp))
                    }
                }
                Text(patientSubtitle(p), fontSize = 13.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            DueBadge(p)
        }
    }
}
