package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One row in "Today's patients": an appointment for today, or a visit already done today. */
private data class TodayItem(val patient: Patient, val scheduled: Boolean, val reason: String)

@Composable
fun TodayScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val version = DataBus.version
    val everyone = remember(version) { db.getPatients("") }
    val stats = remember(version) { db.stats() }
    val upcoming = remember(version) { db.upcoming(7) }
    val todayDay = todayEpochDay()
    val todayStart = startOfToday()

    val todayList = remember(everyone, upcoming) {
        val byId = everyone.associateBy { it.id }
        val waiting = upcoming.filter { it.day == todayDay }
            .mapNotNull { a -> byId[a.patientId]?.let { TodayItem(it, true, a.reason) } }
        val seen = everyone.filter { it.lastVisit >= todayStart }
            .sortedByDescending { it.lastVisit }
            .filter { p -> waiting.none { it.patient.id == p.id } }
            .map { TodayItem(it, false, it.lastReason) }
        waiting + seen
    }
    val later = remember(upcoming) { upcoming.filter { it.day > todayDay }.take(6) }
    val recent = remember(everyone) {
        everyone.filter { it.lastVisit in 1L until todayStart }.sortedByDescending { it.lastVisit }.take(6)
    }
    val dueTotal = remember(everyone) { everyone.sumOf { it.remaining.coerceAtLeast(0.0) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp
        val side = if (twoColumn) 24.dp else 20.dp

        val summary: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Collected today", rs(stats.todayCollection), Brand.GreenDeep, Modifier.weight(1f))
                SummaryTile("Visits today", "${stats.todayVisits}", Brand.Ink, Modifier.weight(1f))
                SummaryTile(
                    "Pending dues", rs(dueTotal), if (dueTotal > 0) Brand.Red else Brand.GreenDeep, Modifier.weight(1f)
                ) { go(Screen.Patients(filter = 1)) }
            }
        }
        val quick: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientButton("New Patient", Modifier.weight(1f), Icons.Filled.Add) { go(Screen.PatientForm()) }
                SoftButton("Find patient", Icons.Filled.Search, Modifier.weight(1f), height = 56.dp) {
                    go(Screen.Patients(focusSearch = true))
                }
            }
        }
        val todayCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                SectionHeader("Today's patients")
                Spacer(Modifier.height(8.dp))
                if (todayList.isEmpty()) {
                    Text(
                        "No patients yet today. Today's appointments and visits will appear here.",
                        fontSize = 14.sp, color = Brand.Sub, modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    todayList.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = Brand.Line)
                        val p = item.patient
                        TodayRow(
                            item, showContact = twoColumn,
                            onOpen = { go(Screen.PatientDetail(p.id)) },
                            onStart = { go(Screen.VisitForm(p.id)) },
                            onCall = { callNumber(context, p.phone) },
                            onChat = { openWhatsApp(context, p.phone, reminderMessage(p, if (item.scheduled) todayDay else null)) }
                        )
                    }
                }
            }
        }
        val laterCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                SectionHeader("Coming up this week")
                Spacer(Modifier.height(8.dp))
                if (later.isEmpty()) {
                    Text(
                        "No appointments in the next 7 days.",
                        fontSize = 14.sp, color = Brand.Sub, modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    later.forEach { a ->
                        SimpleRow(a.name, a.reason.ifBlank { "Follow-up" }, fmtDay(a.day), Brand.GoldDeep) {
                            go(Screen.PatientDetail(a.patientId))
                        }
                    }
                }
            }
        }
        val recentCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                SectionHeader("Recent patients")
                Spacer(Modifier.height(8.dp))
                if (recent.isEmpty()) {
                    Text(
                        "Patients you see will appear here.",
                        fontSize = 14.sp, color = Brand.Sub, modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    recent.forEach { p ->
                        SimpleRow(p.name, patientSubtitle(p), if (p.remaining > 0) rs(p.remaining) else null, Brand.Red) {
                            go(Screen.PatientDetail(p.id))
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            SectionTopBar("Today", fmtDayLong(todayDay)) {
                CircleIconButton(Icons.Filled.Search, { go(Screen.Patients(focusSearch = true)) })
                Spacer(Modifier.width(10.dp))
                BackupChip(compact = true) { go(Screen.BackupPage) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = side, end = side, top = 4.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                summary()
                quick()
                if (twoColumn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1.2f)) { todayCard() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            laterCard()
                            recentCard()
                        }
                    }
                } else {
                    todayCard()
                    laterCard()
                    recentCard()
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick = onClick, padding = 16.dp) {
        Text(label, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TodayRow(
    item: TodayItem,
    showContact: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onCall: () -> Unit,
    onChat: () -> Unit
) {
    val p = item.patient
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p.name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (item.scheduled) "Appointment · ${item.reason.ifBlank { "Follow-up" }}"
            else "Visited · ${item.reason.substringBefore(",").ifBlank { "Visit" }}"
            Text(
                sub, fontSize = 12.sp, color = if (item.scheduled) Brand.GoldDeep else Brand.Sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (showContact && p.phone.isNotBlank()) {
            CircleIconButton(Icons.Filled.Call, onCall, bg = Brand.Soft, tint = Brand.GreenDeep, size = 38.dp)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(AppIcons.Chat, onChat, bg = Brand.Soft, tint = Brand.GreenDeep, size = 38.dp)
            Spacer(Modifier.width(10.dp))
        }
        if (item.scheduled) {
            SoftButton("Start visit", null, height = 40.dp, onClick = onStart)
        } else if (p.remaining > 0) {
            Text(rs(p.remaining), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Red)
        } else {
            Icon(Icons.Filled.CheckCircle, null, tint = Brand.Green, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SimpleRow(name: String, sub: String, trailing: String?, trailingColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = trailingColor, maxLines = 1)
        }
    }
}
