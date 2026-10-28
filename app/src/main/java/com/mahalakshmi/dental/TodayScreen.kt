package com.mahalakshmi.dental

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row in "Today's patients": an appointment for today, or a visit already done today. */
private data class TodayItem(val patient: Patient, val scheduled: Boolean, val reason: String)

private class QuickAction(val label: String, val icon: ImageVector, val tint: Color, val soft: Color, val onClick: () -> Unit)

private val Blue = Color(0xFF2F6FED)
private val BlueSoft = Color(0xFFE8F0FF)
private val Purple = Color(0xFF7B4FD6)
private val PurpleSoft = Color(0xFFF1EBFF)
private val HeroBrush = Brush.linearGradient(listOf(Color(0xFF15241B), Color(0xFF243F24), Color(0xFF4A7A17)))

// ───────────── Numbers for the home screen ─────────────
/** Money received on each of the last [days] days, oldest first. */
fun ClinicDb.dailyCollection(days: Int): List<Pair<Long, Double>> {
    val today = todayEpochDay()
    val first = today - days + 1
    val from = LocalDate.ofEpochDay(first).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val totals = HashMap<Long, Double>()
    readableDatabase.rawQuery("SELECT date, amount FROM payments WHERE date >= ?", arrayOf(from.toString())).use { c ->
        while (c.moveToNext()) {
            val day = epochDayOf(c.getLong(0))
            totals[day] = (totals[day] ?: 0.0) + c.getDouble(1)
        }
    }
    return (first..today).map { it to (totals[it] ?: 0.0) }
}

fun ClinicDb.newPatientsSince(millis: Long): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM patients WHERE created_at >= ?", arrayOf(millis.toString()))
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

fun recallMessage(p: Patient, clinicName: String): String {
    val first = p.name.trim().substringBefore(" ")
    return "Hello $first, it has been a while since your last dental check-up at $clinicName. " +
        "A check-up every 6 months keeps your teeth healthy and catches problems early. " +
        "Reply to this message to book a convenient time. 😊"
}

private fun clockText(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

// ───────────── Today screen ─────────────
@Composable
fun TodayScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val version = DataBus.version
    val everyone = remember(version) { db.getPatients("") }
    val stats = remember(version) { db.stats() }
    val upcoming = remember(version) { db.upcoming(7) }
    val week = remember(version) { db.dailyCollection(7) }
    val clinicName = remember(version) { db.clinicInfo().name }
    val doctor = remember(version) { db.mainDoctor() }
    val newToday = remember(version) { db.newPatientsSince(startOfToday()) }
    val newThisMonth = remember(version) { db.newPatientsSince(startOfMonth()) }
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
    val later = remember(upcoming) { upcoming.filter { it.day > todayDay } }
    val dues = remember(everyone) { everyone.filter { it.remaining > 0.0 }.sortedByDescending { it.remaining } }
    val recall = remember(everyone, upcoming) {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val booked = upcoming.map { it.patientId }.toSet()
        everyone.filter {
            it.lastVisit in (now - 3 * 365 * dayMs) until (now - 180 * dayMs) && it.id !in booked && it.phone.isNotBlank()
        }.sortedByDescending { it.lastVisit }
    }
    val actions = listOf(
        QuickAction("New patient", Icons.Filled.Add, Brand.GreenDeep, Brand.GreenSoft) { go(Screen.PatientForm()) },
        QuickAction("Find patient", Icons.Filled.Search, Blue, BlueSoft) { go(Screen.Patients(focusSearch = true)) },
        QuickAction("Collect dues", DocIcons.Receipt, Brand.GoldDeep, Brand.GoldSoft) { go(Screen.Patients(filter = 1)) },
        QuickAction("Prescription", DocIcons.Pill, Purple, PurpleSoft) { go(Screen.Patients(focusSearch = true)) }
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 760.dp
        val side = if (wide) 24.dp else 20.dp

        val hero: @Composable (Modifier) -> Unit = { m ->
            HeroCard(
                doctorName = doctor?.name?.takeIf { it.isNotBlank() } ?: "Doctor",
                todayAmount = stats.todayCollection,
                monthAmount = stats.monthCollection,
                visits = stats.todayVisits,
                booked = todayList.count { it.scheduled },
                newPatients = newToday,
                week = week,
                compact = !wide,
                modifier = m
            )
        }
        val numbers: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DuesCard(dues, Modifier.weight(1f)) { go(Screen.Patients(filter = 1)) }
                PatientsCard(everyone.size, newThisMonth, Modifier.weight(1f)) { go(Screen.Patients()) }
            }
        }
        val todayCard: @Composable () -> Unit = {
            TodayCard(
                items = todayList,
                wide = wide,
                onOpen = { p -> go(Screen.PatientDetail(p.id)) },
                onStart = { p -> go(Screen.VisitForm(p.id)) },
                onCall = { p -> callNumber(context, p.phone) },
                onChat = { item ->
                    openWhatsApp(context, item.patient.phone, reminderMessage(item.patient, if (item.scheduled) todayDay else null))
                },
                onNew = { go(Screen.PatientForm()) }
            )
        }
        val extras: @Composable () -> Unit = {
            if (later.isNotEmpty()) ComingUpCard(later) { id -> go(Screen.PatientDetail(id)) }
            if (recall.isNotEmpty()) {
                RecallCard(recall, onOpen = { p -> go(Screen.PatientDetail(p.id)) }) { p ->
                    openWhatsApp(context, p.phone, recallMessage(p, clinicName))
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
                    .padding(start = side, end = side, top = 4.dp, bottom = 28.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        hero(Modifier.weight(1.45f).appear(0))
                        Column(Modifier.weight(1f).appear(1), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            QuickActions(actions, perRow = 2)
                            numbers()
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1.45f).appear(2)) { todayCard() }
                        Column(Modifier.weight(1f).appear(3), verticalArrangement = Arrangement.spacedBy(16.dp)) { extras() }
                    }
                } else {
                    hero(Modifier.fillMaxWidth().appear(0))
                    Box(Modifier.appear(1)) { QuickActions(actions, perRow = 4) }
                    Box(Modifier.appear(2)) { numbers() }
                    Box(Modifier.appear(3)) { todayCard() }
                    extras()
                }
            }
        }
    }
}

// ───────────── Hero banner ─────────────
@Composable
private fun HeroCard(
    doctorName: String,
    todayAmount: Double,
    monthAmount: Double,
    visits: Int,
    booked: Int,
    newPatients: Int,
    week: List<Pair<Long, Double>>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    val today = animatedNumber(todayAmount)
    Box(
        modifier
            .shadow(22.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x55000000))
            .clip(shape)
            .background(HeroBrush)
            .drawBehind {
                val r = size.minDimension
                val corner = Offset(size.width * 0.95f, 0f)
                drawCircle(
                    Brush.radialGradient(listOf(Brand.Green.copy(alpha = 0.45f), Color.Transparent), center = corner, radius = r),
                    radius = r, center = corner
                )
                val low = Offset(0f, size.height)
                drawCircle(
                    Brush.radialGradient(listOf(Brand.Gold.copy(alpha = 0.2f), Color.Transparent), center = low, radius = r * 0.8f),
                    radius = r * 0.8f, center = low
                )
                val ring = Offset(size.width * 0.9f, size.height * 0.1f)
                drawCircle(Color.White.copy(alpha = 0.07f), radius = r * 0.4f, center = ring, style = Stroke(width = 1.5.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = r * 0.62f, center = ring, style = Stroke(width = 1.dp.toPx()))
            }
            .padding(24.dp)
    ) {
        Icon(
            AppIcons.Tooth, null, tint = Color.White.copy(alpha = 0.06f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 46.dp).size(210.dp)
                .graphicsLayer { rotationZ = -14f }
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${greeting()},", color = Color.White.copy(alpha = 0.78f), fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Icon(AppIcons.Sparkle, null, tint = Brand.Gold, modifier = Modifier.size(14.dp))
            }
            Text(
                doctorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 27.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            if (compact) {
                CollectionBlock(today, monthAmount)
                Spacer(Modifier.height(18.dp))
                WeekChart(week, Modifier.fillMaxWidth(), spread = true)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { CollectionBlock(today, monthAmount) }
                    Spacer(Modifier.width(16.dp))
                    WeekChart(week, spread = false)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(AppIcons.Tooth, if (visits == 1) "1 visit" else "$visits visits")
                GlassChip(Icons.Filled.DateRange, if (booked == 1) "1 appointment" else "$booked appointments")
                GlassChip(AppIcons.Sparkle, if (newPatients == 1) "1 new patient" else "$newPatients new patients")
            }
        }
    }
}

@Composable
private fun CollectionBlock(amount: Double, month: Double) {
    Column {
        Text(
            "TODAY'S COLLECTION", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(rs(amount), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 1)
        Text("This month ${rs(month)}", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun WeekChart(week: List<Pair<Long, Double>>, modifier: Modifier = Modifier, spread: Boolean) {
    val max = week.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val today = todayEpochDay()
    Row(
        modifier.height(82.dp),
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        week.forEach { (day, amount) ->
            val isToday = day == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val barHeight = (6f + 50f * (amount / max).toFloat() * grow.value).dp
                Box(
                    Modifier.width(12.dp).height(barHeight).clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) Brand.Gold else Color.White.copy(alpha = if (amount > 0) 0.6f else 0.22f))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                    fontSize = 10.sp,
                    color = if (isToday) Color.White else Color.White.copy(alpha = 0.6f),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun GlassChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ───────────── Quick actions & numbers ─────────────
@Composable
private fun QuickActions(actions: List<QuickAction>, perRow: Int) {
    AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        actions.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { a ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = a.onClick)
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(a.soft),
                            contentAlignment = Alignment.Center
                        ) { Icon(a.icon, null, tint = a.tint, modifier = Modifier.size(26.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            a.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Brand.Ink,
                            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuesCard(dues: List<Patient>, modifier: Modifier, onClick: () -> Unit) {
    val total = dues.sumOf { it.remaining }
    val shown = animatedNumber(total)
    AppCard(modifier, onClick = onClick, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Warning, Brand.Red, Brand.RedSoft, 38.dp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Faint)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            rs(shown), fontSize = 21.sp, fontWeight = FontWeight.Bold,
            color = if (total > 0) Brand.Red else Brand.GreenDeep, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text("Pending dues", fontSize = 13.sp, color = Brand.Sub)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dues.isEmpty()) {
                Text("All clear ✓", fontSize = 12.sp, color = Brand.GreenDeep, fontWeight = FontWeight.Medium)
            } else {
                AvatarStack(dues.take(3).map { it.name })
                Spacer(Modifier.width(8.dp))
                Text(if (dues.size == 1) "1 patient" else "${dues.size} patients", fontSize = 12.sp, color = Brand.Sub, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PatientsCard(total: Int, newThisMonth: Int, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Person, Blue, BlueSoft, 38.dp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Faint)
        }
        Spacer(Modifier.height(12.dp))
        Text("$total", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Brand.Ink, maxLines = 1)
        Text("Total patients", fontSize = 13.sp, color = Brand.Sub)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(34.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                if (newThisMonth > 0) "+$newThisMonth new this month" else "No new patients this month",
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (newThisMonth > 0) Brand.GreenDeep else Brand.Sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AvatarStack(names: List<String>) {
    Box(Modifier.width((34 + 22 * (names.size - 1)).dp).height(34.dp)) {
        names.forEachIndexed { i, n ->
            Box(
                Modifier.offset(x = (22 * i).dp).size(34.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Avatar(n, 30.dp) }
        }
    }
}

// ───────────── Today's patients ─────────────
@Composable
private fun TodayCard(
    items: List<TodayItem>,
    wide: Boolean,
    onOpen: (Patient) -> Unit,
    onStart: (Patient) -> Unit,
    onCall: (Patient) -> Unit,
    onChat: (TodayItem) -> Unit,
    onNew: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Today's patients", Modifier.weight(1f))
            if (items.isNotEmpty()) Pill("${items.size}", Brand.GreenSoft, Brand.GreenText)
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(Brand.GreenSoft), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Tooth, null, tint = Brand.Green, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("A fresh new day!", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Brand.Ink)
                Text(
                    "Today's appointments and visits will appear here.",
                    fontSize = 13.sp, color = Brand.Sub, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                SoftButton("Add a patient", Icons.Filled.Add, onClick = onNew)
            }
        } else {
            items.forEachIndexed { i, item ->
                TimelineRow(
                    item, isLast = i == items.lastIndex, wide = wide,
                    onOpen = { onOpen(item.patient) }, onStart = { onStart(item.patient) },
                    onCall = { onCall(item.patient) }, onChat = { onChat(item) }
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    item: TodayItem,
    isLast: Boolean,
    wide: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onCall: () -> Unit,
    onChat: () -> Unit
) {
    val p = item.patient
    val dot = if (item.scheduled) Brand.Gold else Brand.Green
    val what = if (item.scheduled) item.reason.ifBlank { "Follow-up" } else item.reason.substringBefore(",").ifBlank { "Visit" }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (wide) {
            Text(
                if (item.scheduled) "Waiting" else clockText(p.lastVisit),
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (item.scheduled) Brand.GoldDeep else Brand.Sub,
                modifier = Modifier.width(62.dp).padding(top = 22.dp)
            )
            Box(
                Modifier.width(18.dp).fillMaxHeight().drawBehind {
                    val x = size.width / 2
                    val cy = 30.dp.toPx()
                    if (!isLast) drawLine(Brand.GreenLine, Offset(x, cy), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                    drawCircle(dot.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = Offset(x, cy))
                    drawCircle(dot, radius = 4.5.dp.toPx(), center = Offset(x, cy))
                }
            )
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(p.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    wide -> what
                    item.scheduled -> "Waiting · $what"
                    else -> "${clockText(p.lastVisit)} · $what"
                }
                Text(
                    sub, fontSize = 12.sp, color = if (item.scheduled) Brand.GoldDeep else Brand.Sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (wide && p.phone.isNotBlank()) {
                CircleIconButton(Icons.Filled.Call, onCall, bg = Brand.Soft, tint = Brand.GreenDeep, size = 38.dp)
                Spacer(Modifier.width(8.dp))
                CircleIconButton(AppIcons.Chat, onChat, bg = Brand.Soft, tint = Brand.GreenDeep, size = 38.dp)
                Spacer(Modifier.width(10.dp))
            }
            if (item.scheduled) {
                SoftButton("Start", null, height = 38.dp, onClick = onStart)
            } else if (p.remaining > 0) {
                Text(rs(p.remaining), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Red)
            } else {
                Icon(Icons.Filled.CheckCircle, null, tint = Brand.Green, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ───────────── Coming up & recall ─────────────
@Composable
private fun ComingUpCard(items: List<Appointment>, onOpen: (Long) -> Unit) {
    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        SectionHeader("Coming up this week")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { a ->
                val date = LocalDate.ofEpochDay(a.day)
                Column(
                    Modifier.width(136.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Soft)
                        .clickable { onOpen(a.patientId) }.padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(),
                                fontSize = 10.sp, color = Brand.GoldDeep, fontWeight = FontWeight.SemiBold
                            )
                            Text("${date.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                        }
                        Spacer(Modifier.weight(1f))
                        Avatar(a.name, 34.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.reason.ifBlank { "Follow-up" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RecallCard(list: List<Patient>, onOpen: (Patient) -> Unit, onRemind: (Patient) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Due for check-up", Modifier.weight(1f))
            Pill("${list.size}", Brand.GoldSoft, Brand.GoldDeep)
        }
        Text("Last visit more than 6 months ago", fontSize = 12.sp, color = Brand.Sub)
        Spacer(Modifier.height(8.dp))
        (if (showAll) list else list.take(4)).forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(p) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(p.name, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Last visit: ${relativeTime(p.lastVisit)}", fontSize = 12.sp, color = Brand.Sub, maxLines = 1)
                }
                SoftButton("Remind", AppIcons.Chat, height = 38.dp) { onRemind(p) }
            }
        }
        if (list.size > 4) {
            Text(
                if (showAll) "Show less" else "Show all ${list.size}",
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAll = !showAll }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            )
        }
    }
}
