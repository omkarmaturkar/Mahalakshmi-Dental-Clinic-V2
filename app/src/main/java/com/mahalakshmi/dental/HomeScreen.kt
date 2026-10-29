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
fun Greeting(modifier: Modifier = Modifier, big: Boolean = false) {
    val dateText = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${greeting()}, Doctor", fontSize = if (big) 16.sp else 14.sp, color = Brand.Sub)
            Spacer(Modifier.width(6.dp))
            Icon(AppIcons.Sparkle, null, tint = Brand.Gold, modifier = Modifier.size(14.dp))
        }
        Text(dateText, fontWeight = FontWeight.SemiBold, fontSize = if (big) 30.sp else 24.sp, color = Brand.Ink)
    }
}

@Composable
fun BackupChip(compact: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val version = DataBus.version
    val last = remember(version) { maxOf(DriveBackup.lastBackup(context), Backup.lastBackup(context)) }
    val on = remember(version) { DriveBackup.isConnected(context) || Backup.autoFolderName(context) != null }
    val ok = on && last > 0L && System.currentTimeMillis() - last < 3L * 24 * 60 * 60 * 1000
    if (compact) {
        Box {
            CircleIconButton(
                AppIcons.Cloud, onClick,
                bg = if (ok) Brand.GreenSoft else Brand.GoldSoft,
                tint = if (ok) Brand.GreenDeep else Brand.GoldDeep
            )
            if (!ok) Box(Modifier.align(Alignment.TopEnd).size(11.dp).clip(CircleShape).background(Brand.Gold))
        }
    } else {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (ok) Brand.GreenSoft else Brand.GoldSoft)
                .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.Cloud, null, tint = if (ok) Brand.GreenDeep else Brand.GoldDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (!on) "Backup is off" else if (last > 0L) "Backed up · ${agoText(last)}" else "Backup pending",
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (ok) Brand.GreenText else Brand.GoldDeep, maxLines = 1
            )
        }
    }
}

// ───────────── Cards ─────────────
@Composable
fun HeroCard(stats: Stats, modifier: Modifier = Modifier) {
    val today = animatedNumber(stats.todayCollection)
    val month = animatedNumber(stats.monthCollection)
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .shadow(20.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x4D000000))
            .clip(shape)
            .background(CharcoalGradient)
            .drawBehind {
                val glow = Offset(size.width * 0.92f, size.height * 0.05f)
                val r1 = size.minDimension * 0.95f
                drawCircle(
                    Brush.radialGradient(listOf(Brand.Green.copy(alpha = 0.55f), Color.Transparent), center = glow, radius = r1),
                    radius = r1, center = glow
                )
                val glow2 = Offset(size.width * 0.05f, size.height * 1.05f)
                val r2 = size.minDimension * 0.8f
                drawCircle(
                    Brush.radialGradient(listOf(Brand.Gold.copy(alpha = 0.22f), Color.Transparent), center = glow2, radius = r2),
                    radius = r2, center = glow2
                )
            }
            .padding(24.dp)
    ) {
        Icon(
            AppIcons.Tooth, null, tint = Color.White.copy(alpha = 0.07f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 36.dp, y = 40.dp).size(170.dp)
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Sparkle, null, tint = Brand.Gold, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "TODAY'S COLLECTION", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(rs(today), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 40.sp, maxLines = 1)
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip(if (stats.todayVisits == 1) "1 visit today" else "${stats.todayVisits} visits today")
                HeroChip("This month ${rs(month)}")
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Glass).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun StatCard(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accent: Color,
    soft: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    AppCard(modifier, onClick = onClick, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, soft, 40.dp)
            Spacer(Modifier.weight(1f))
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Faint)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 13.sp, color = Brand.Sub)
        Text(sub, fontSize = 12.sp, color = accent, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accent: Color,
    soft: Color,
    modifier: Modifier
) {
    AppCard(modifier, padding = 20.dp) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, soft, 54.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = Brand.Sub)
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 12.sp, color = accent, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmallEmpty(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Soft).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AppIcons.Tooth, null, tint = Brand.Faint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = Brand.Sub)
    }
}

@Composable
private fun DayPill(day: Long, today: Long) {
    val isToday = day == today
    Pill(
        fmtDay(day),
        if (isToday) Brand.GreenDeep else Brand.GoldSoft,
        if (isToday) Color.White else Brand.GoldDeep,
        Icons.Filled.DateRange
    )
}

@Composable
private fun AppointmentRow(a: Appointment, today: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(a.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.reason.ifBlank { "Follow-up" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DayPill(a.day, today)
    }
}

@Composable
private fun AppointmentCard(a: Appointment, today: Long, onClick: () -> Unit) {
    AppCard(Modifier.width(230.dp), onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(a.name, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.reason.ifBlank { "Follow-up" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        DayPill(a.day, today)
    }
}

@Composable
private fun RecentRow(p: Patient, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(patientSubtitle(p), fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (p.remaining > 0) {
            Text(rs(p.remaining), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Red)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Faint)
        }
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

@Composable
private fun SidebarPatientRow(p: Patient, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Brand.GreenSoft else Color.Transparent, label = "patientRow")
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p.name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (selected) Brand.GreenText else Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(patientSubtitle(p), fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (p.remaining > 0) {
            Spacer(Modifier.width(6.dp))
            Text(rs(p.remaining), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Red)
        } else if (p.alerts.isNotEmpty()) {
            Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(16.dp))
        }
    }
}

// ───────────── Phone home screen ─────────────
@Composable
fun HomeCompact(db: ClinicDb, go: (Screen) -> Unit) {
    val version = DataBus.version
    val everyone = remember(version) { db.getPatients("") }
    val stats = remember(version) { db.stats() }
    val upcoming = remember(version) { db.upcoming(7) }
    val today = remember { todayEpochDay() }
    val todayIds = remember(upcoming) { upcoming.filter { it.day == today }.map { it.patientId }.toSet() }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val shown = remember(search, filter, everyone, todayIds) { filterPatients(everyone, search, filter, todayIds) }
    val dueTotal = remember(everyone) { everyone.sumOf { it.remaining.coerceAtLeast(0.0) } }
    val dueCount = remember(everyone) { everyone.count { it.remaining > 0.0 } }
    val dueAmount = animatedNumber(dueTotal)

    Box(Modifier.fillMaxSize().background(Brand.Bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "brand") {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { BrandRow(compact = true) }
                    CircleIconButton(DocIcons.Settings, { go(Screen.ClinicSettings) })
                    Spacer(Modifier.width(10.dp))
                    BackupChip(compact = true) { go(Screen.BackupPage) }
                }
            }
            item(key = "greeting") {
                Greeting(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp))
            }
            item(key = "hero") {
                HeroCard(stats, Modifier.padding(horizontal = 20.dp).fillMaxWidth().appear(0))
            }
            item(key = "stats") {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatCard(
                        "Pending dues", rs(dueAmount), if (dueCount == 1) "1 patient" else "$dueCount patients",
                        Icons.Filled.Warning, Brand.Red, Brand.RedSoft, Modifier.weight(1f).appear(1)
                    ) { filter = 1 }
                    StatCard(
                        "Total patients", "${stats.patientCount}",
                        if (upcoming.size == 1) "1 visit this week" else "${upcoming.size} visits this week",
                        Icons.Filled.Person, Brand.GreenDeep, Brand.GreenSoft, Modifier.weight(1f).appear(2), null
                    )
                }
            }
            if (upcoming.isNotEmpty()) item(key = "upcoming") {
                Column(Modifier.padding(top = 24.dp)) {
                    SectionHeader("Upcoming visits", Modifier.padding(horizontal = 20.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(upcoming, key = { "${it.patientId}-${it.day}" }) { a ->
                            AppointmentCard(a, today) { go(Screen.PatientDetail(a.patientId)) }
                        }
                    }
                }
            }
            item(key = "patients") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                    SectionHeader("Patients")
                    Spacer(Modifier.height(12.dp))
                    SearchBox(search) { search = it }
                    Spacer(Modifier.height(14.dp))
                    PatientFilters(filter, everyone.size, dueCount) { filter = it }
                }
            }
            if (shown.isEmpty()) item(key = "empty") {
                EmptyState(
                    if (everyone.isEmpty()) "Welcome, Doctor!" else "No patients found",
                    if (everyone.isEmpty()) "Tap \"New Patient\" to add your first patient." else "Try a different name, number or filter."
                )
            }
            items(shown, key = { it.id }) { p ->
                PatientCard(p, Modifier.animateItem().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    go(Screen.PatientDetail(p.id))
                }
            }
        }
        GradientFab(
            "New Patient", Icons.Filled.Add,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
        ) { go(Screen.PatientForm()) }
    }
}

// ───────────── Tablet dashboard ─────────────
@Composable
fun DashboardWide(db: ClinicDb, onBackup: () -> Unit, onPatient: (Long) -> Unit, onNewPatient: () -> Unit) {
    val version = DataBus.version
    val everyone = remember(version) { db.getPatients("") }
    val stats = remember(version) { db.stats() }
    val upcoming = remember(version) { db.upcoming(7) }
    val today = remember { todayEpochDay() }
    val dueTotal = remember(everyone) { everyone.sumOf { it.remaining.coerceAtLeast(0.0) } }
    val dueCount = remember(everyone) { everyone.count { it.remaining > 0.0 } }
    val recent = remember(everyone) { everyone.filter { it.lastVisit > 0L }.sortedByDescending { it.lastVisit }.take(6) }
    val dueAmount = animatedNumber(dueTotal)
    val dueSub = if (dueCount == 1) "1 patient" else "$dueCount patients"
    val weekSub = if (upcoming.size == 1) "1 visit this week" else "${upcoming.size} visits this week"

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxWidth >= 760.dp
        val upcomingCard: @Composable (Modifier) -> Unit = { m ->
            AppCard(m, padding = 22.dp) {
                SectionHeader("Upcoming visits")
                Spacer(Modifier.height(12.dp))
                if (upcoming.isEmpty()) {
                    SmallEmpty("No visits scheduled this week")
                } else {
                    upcoming.take(6).forEach { a -> AppointmentRow(a, today) { onPatient(a.patientId) } }
                }
            }
        }
        val recentCard: @Composable (Modifier) -> Unit = { m ->
            AppCard(m, padding = 22.dp) {
                SectionHeader("Recent patients")
                Spacer(Modifier.height(12.dp))
                if (recent.isEmpty()) {
                    SmallEmpty("Your patients will appear here")
                    Spacer(Modifier.height(14.dp))
                    GradientButton("Add first patient", Modifier.fillMaxWidth(), Icons.Filled.Add, onClick = onNewPatient)
                } else {
                    recent.forEach { p -> RecentRow(p) { onPatient(p.id) } }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = if (roomy) 32.dp else 22.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Greeting(Modifier.weight(1f), big = roomy)
                BackupChip(compact = !roomy, onClick = onBackup)
            }
            Spacer(Modifier.height(26.dp))
            if (roomy) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroCard(stats, Modifier.weight(1.35f).height(220.dp).appear(0))
                    Column(Modifier.weight(1f).height(220.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MiniStat(
                            "Pending dues", rs(dueAmount), dueSub, Icons.Filled.Warning,
                            Brand.Red, Brand.RedSoft, Modifier.weight(1f).fillMaxWidth().appear(1)
                        )
                        MiniStat(
                            "Total patients", "${stats.patientCount}", weekSub, Icons.Filled.Person,
                            Brand.GreenDeep, Brand.GreenSoft, Modifier.weight(1f).fillMaxWidth().appear(2)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    upcomingCard(Modifier.weight(1f).appear(3))
                    recentCard(Modifier.weight(1f).appear(4))
                }
            } else {
                HeroCard(stats, Modifier.fillMaxWidth().appear(0))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatCard(
                        "Pending dues", rs(dueAmount), dueSub, Icons.Filled.Warning,
                        Brand.Red, Brand.RedSoft, Modifier.weight(1f).appear(1), null
                    )
                    StatCard(
                        "Total patients", "${stats.patientCount}", weekSub, Icons.Filled.Person,
                        Brand.GreenDeep, Brand.GreenSoft, Modifier.weight(1f).appear(2), null
                    )
                }
                Spacer(Modifier.height(20.dp))
                upcomingCard(Modifier.fillMaxWidth().appear(3))
                Spacer(Modifier.height(16.dp))
                recentCard(Modifier.fillMaxWidth().appear(4))
            }
        }
    }
}

// ───────────── Tablet sidebar ─────────────
@Composable
fun Sidebar(
    db: ClinicDb,
    width: Dp,
    dashboardActive: Boolean,
    backupActive: Boolean,
    settingsActive: Boolean,
    selectedId: Long?,
    onDashboard: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onNewPatient: () -> Unit,
    onPatient: (Long) -> Unit
) {
    val context = LocalContext.current
    val version = DataBus.version
    val everyone = remember(version) { db.getPatients("") }
    val todayIds = remember(version) { db.upcoming(0).map { it.patientId }.toSet() }
    val backupWarn = remember(version) { !DriveBackup.isConnected(context) && Backup.autoFolderName(context) == null }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val shown = remember(search, filter, everyone, todayIds) { filterPatients(everyone, search, filter, todayIds) }
    val dueCount = remember(everyone) { everyone.count { it.remaining > 0.0 } }

    Column(Modifier.width(width).fillMaxHeight().background(Brand.Surface).statusBarsPadding()) {
        Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)) { BrandRow() }
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NavItem("Dashboard", Icons.Filled.Home, dashboardActive, onClick = onDashboard)
            NavItem("Backup", AppIcons.Cloud, backupActive, dot = if (backupWarn) Brand.Gold else null, onClick = onBackup)
            NavItem("Clinic details", DocIcons.Settings, settingsActive, onClick = onSettings)
        }
        HorizontalDivider(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), color = Brand.Line)
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Patients", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.GreenText, modifier = Modifier.weight(1f))
            Text("${everyone.size}", fontSize = 13.sp, color = Brand.Sub)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = 18.dp)) { SearchBox(search) { search = it } }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = 18.dp)) { PatientFilters(filter, everyone.size, dueCount) { filter = it } }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            if (shown.isEmpty()) item(key = "empty") {
                Text(
                    if (everyone.isEmpty()) "No patients yet – add your first patient below." else "No patient found.",
                    fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(16.dp)
                )
            }
            items(shown, key = { it.id }) { p ->
                SidebarPatientRow(p, p.id == selectedId, Modifier.animateItem()) { onPatient(p.id) }
            }
        }
        Box(Modifier.navigationBarsPadding().padding(18.dp)) {
            GradientButton("New Patient", Modifier.fillMaxWidth(), Icons.Filled.Add, onClick = onNewPatient)
        }
    }
}
