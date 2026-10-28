package com.mahalakshmi.dental

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

// ───────────── Colours & icons of the home page ─────────────
object HomeColors {
    val Teal = Color(0xFF135B56)
    val TealText = Color(0xFF14615B)
    val TealSoft = Color(0xFFE3F1EE)
    val Amber = Color(0xFFB26A00)
    val AmberSoft = Color(0xFFFDF0D9)
    val Dot = Color(0xFFF5A524)
    val Page = Color(0xFFF2F4F3)
    val DueRed = Color(0xFFC62828)
}

private fun outlineIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    )
    for (p in paths) builder.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
    return builder.build()
}

private const val CALENDAR_PATH =
    "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4 3.01,4.9 3.01,6L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4zM19,20H5V9h14V20z"

object HomeIcons {
    val Home: ImageVector by lazy {
        outlineIcon("home", "M12,5.69l5,4.5V18h-2v-6H9v6H7v-7.81l5,-4.5M12,3L2,12h3v8h6v-6h2v6h6v-8h3L12,3z")
    }
    val People: ImageVector by lazy {
        outlineIcon(
            "people",
            "M16.67,13.13C18.04,14.06 19,15.32 19,17v3h4v-3c0,-2.18 -3.57,-3.47 -6.33,-3.87zM15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4c-0.47,0 -0.91,0.1 -1.33,0.24C14.5,5.27 15,6.58 15,8s-0.5,2.73 -1.33,3.76C14.09,11.9 14.53,12 15,12zM9,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM9,6c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2zM9,13c-2.67,0 -8,1.34 -8,4v3h16v-3c0,-2.66 -5.33,-4 -8,-4zM15,18H3v-0.99C3.2,16.29 6.3,15 9,15s5.8,1.29 6,2V18z"
        )
    }
    val Calendar: ImageVector by lazy { outlineIcon("calendar", CALENDAR_PATH) }
    val CalendarAdd: ImageVector by lazy {
        outlineIcon("calendar_add", CALENDAR_PATH, "M11,11h2v2.5h2.5v2H13V18h-2v-2.5H8.5v-2H11z")
    }
    val Rupee: ImageVector by lazy {
        outlineIcon(
            "rupee",
            "M13.66,7C13.1,5.82 11.9,5 10.5,5L6,5V3h12v2l-3.26,0c0.48,0.58 0.84,1.26 1.05,2L18,7v2l-2.02,0c-0.25,2.8 -2.61,5 -5.48,5H9.77l6.73,7h-2.77L7,14v-2h3.5c1.76,0 3.22,-1.3 3.46,-3L6,9V7L13.66,7z"
        )
    }
    val PersonAdd: ImageVector by lazy {
        outlineIcon(
            "person_add",
            "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM15,6c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2zM15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4zM9,18c0.22,-0.72 3.31,-2 6,-2 2.7,0 5.8,1.29 6,2L9,18zM6,15v-3h3v-2L6,10L6,7L4,7v3L1,10v2h3v3z"
        )
    }
    val Clock: ImageVector by lazy {
        outlineIcon(
            "clock",
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
        )
    }
    val ClipboardAdd: ImageVector by lazy {
        outlineIcon(
            "clipboard_add",
            "M19,3h-4.18C14.4,1.84 13.3,1 12,1s-2.4,0.84 -2.82,2H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM12,2.75c0.41,0 0.75,0.34 0.75,0.75s-0.34,0.75 -0.75,0.75 -0.75,-0.34 -0.75,-0.75 0.34,-0.75 0.75,-0.75zM19,19H5V5h14V19z",
            "M11,8h2v3h3v2h-3v3h-2v-3H8v-2h3z"
        )
    }
    val Wallet: ImageVector by lazy {
        outlineIcon(
            "wallet",
            "M21,7.28V5c0,-1.1 -0.9,-2 -2,-2H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-2.28c0.59,-0.35 1,-0.98 1,-1.72V9c0,-0.74 -0.41,-1.37 -1,-1.72zM20,9v6h-7V9h7zM5,19V5h14v2h-6c-1.1,0 -2,0.9 -2,2v6c0,1.1 0.9,2 2,2h6v2H5z",
            "M16,13.5c0.83,0 1.5,-0.67 1.5,-1.5s-0.67,-1.5 -1.5,-1.5 -1.5,0.67 -1.5,1.5 0.67,1.5 1.5,1.5z"
        )
    }
    val Bell: ImageVector by lazy {
        outlineIcon(
            "bell",
            "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2zM16,17H8v-6c0,-2.48 1.51,-4.5 4,-4.5s4,2.02 4,4.5v6z"
        )
    }
}

// ───────────── Numbers for the home page ─────────────
fun ClinicDb.newPatientsSince(millis: Long): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM patients WHERE created_at >= ?", arrayOf(millis.toString()))
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

fun ClinicDb.paymentsCountSince(millis: Long): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM payments WHERE date >= ? AND amount > 0", arrayOf(millis.toString()))
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

fun recallMessage(p: Patient, clinicName: String): String {
    val first = p.name.trim().substringBefore(" ")
    return "Hello $first, it has been a while since your last dental check-up at $clinicName. " +
        "A check-up every 6 months keeps your teeth healthy and catches problems early. " +
        "Reply to this message to book a convenient time. 😊"
}

private class HomeData(
    val clinicName: String,
    val doctorName: String,
    val bookings: List<DayBooking>,
    val newToday: Int,
    val newWeek: Int,
    val collected: Double,
    val paymentsToday: Int,
    val dues: List<Patient>,
    val recall: List<Patient>,
    val backupOk: Boolean
)

private class HomeAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private class HomeAlert(
    val icon: ImageVector, val tint: Color, val soft: Color,
    val title: String, val sub: String, val onClick: () -> Unit
)

/** Loads everything for the home page. If anything goes wrong, the page still opens and the problem is recorded. */
private fun loadHome(db: ClinicDb, context: Context): HomeData = try {
    val everyone = db.getPatients("")
    val today = todayEpochDay()
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val booked = db.bookingsBetween(today, today + 60).map { it.patientId }.toSet()
    val lastBackup = maxOf(DriveBackup.lastBackup(context), Backup.lastBackup(context))
    HomeData(
        clinicName = db.clinicInfo().name,
        doctorName = db.mainDoctor()?.name?.takeIf { it.isNotBlank() } ?: "Doctor",
        bookings = db.bookingsBetween(today, today),
        newToday = db.newPatientsSince(startOfToday()),
        newWeek = db.newPatientsSince(weekStart),
        collected = db.stats().todayCollection,
        paymentsToday = db.paymentsCountSince(startOfToday()),
        dues = everyone.filter { it.remaining > 0.01 }.sortedByDescending { it.remaining },
        recall = everyone.filter {
            it.lastVisit in (now - 3 * 365 * dayMs) until (now - 180 * dayMs) && it.id !in booked && it.phone.isNotBlank()
        }.sortedByDescending { it.lastVisit },
        backupOk = lastBackup > 0L && now - lastBackup < 3 * dayMs
    )
} catch (e: Throwable) {
    CrashGuard.save(context, e)
    HomeData(DEFAULT_CLINIC_NAME, "Doctor", emptyList(), 0, 0, 0.0, 0, emptyList(), emptyList(), true)
}

/** Moves content up so it overlaps whatever is above it (used for the cards over the green header). */
private fun Modifier.pullUp(by: Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = by.roundToPx()
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

// ───────────── Home page ─────────────
@Composable
fun TodayScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val openMenu = LocalMenu.current
    val version = DataBus.version
    val data = remember(version) { loadHome(db, context) }
    var picker by remember { mutableStateOf<String?>(null) }
    var bookFor by remember { mutableStateOf<Patient?>(null) }
    var payFor by remember { mutableStateOf<Patient?>(null) }
    var receiptFor by remember { mutableStateOf<Pair<Patient, Long>?>(null) }
    var alertsOpen by remember { mutableStateOf(false) }
    var showAllRecall by remember { mutableStateOf(false) }

    val dueTotal = data.dues.sumOf { it.remaining }
    val done = data.bookings.count { it.status == 1 }
    val waiting = data.bookings.count { it.status == 0 }
    val words = data.clinicName.trim().split(" ").filter { it.isNotBlank() }
    val alerts = buildList<HomeAlert> {
        if (waiting > 0) {
            add(HomeAlert(HomeIcons.Calendar, HomeColors.Teal, HomeColors.TealSoft,
                if (waiting == 1) "1 appointment waiting today" else "$waiting appointments waiting today",
                "Open today's schedule") { go(Screen.Schedule) })
        }
        if (!data.backupOk) {
            add(HomeAlert(AppIcons.Cloud, HomeColors.Amber, HomeColors.AmberSoft,
                "Backup needs attention", "No backup in the last 3 days") { go(Screen.BackupPage) })
        }
        if (data.dues.isNotEmpty()) {
            add(HomeAlert(HomeIcons.Clock, HomeColors.Amber, HomeColors.AmberSoft,
                "${rs(dueTotal)} pending dues", if (data.dues.size == 1) "1 patient" else "${data.dues.size} patients") {
                go(Screen.Patients(filter = 1))
            })
        }
        if (data.recall.isNotEmpty()) {
            add(HomeAlert(HomeIcons.People, HomeColors.Teal, HomeColors.TealSoft,
                "${data.recall.size} due for a check-up", "Send them a reminder from the home page") {})
        }
    }
    val actions = listOf(
        HomeAction("New patient", HomeIcons.PersonAdd) { go(Screen.PatientForm()) },
        HomeAction("Book appointment", HomeIcons.CalendarAdd) { picker = "book" },
        HomeAction("Add treatment", HomeIcons.ClipboardAdd) { picker = "treatment" },
        HomeAction("Record payment", HomeIcons.Wallet) { picker = "payment" }
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(HomeColors.Page)) {
        val wide = maxWidth >= 760.dp
        val side = if (wide) 28.dp else 20.dp

        val quickSection: @Composable (Int) -> Unit = { perRow ->
            Text("Quick actions", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
            Spacer(Modifier.height(14.dp))
            actions.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    row.forEach { a -> QuickTile(a.label, a.icon, Modifier.weight(1f), a.onClick) }
                }
            }
        }
        val appointmentsSection: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today's appointments", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (data.bookings.isEmpty()) "Schedule" else "See all ${data.bookings.size}",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.TealText,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { go(Screen.Schedule) }.padding(6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            if (data.bookings.isEmpty()) {
                WhiteCard(padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(HomeColors.TealSoft),
                            contentAlignment = Alignment.Center
                        ) { Icon(HomeIcons.Calendar, null, tint = HomeColors.Teal, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("No appointments today", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink)
                            Text("Book a patient's next visit in a few taps", fontSize = 13.sp, color = Brand.Sub)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SoftButton(
                        "Book appointment", HomeIcons.CalendarAdd, Modifier.fillMaxWidth(),
                        bg = HomeColors.TealSoft, fg = HomeColors.TealText
                    ) { picker = "book" }
                }
            } else {
                WhiteCard(padding = 0.dp) {
                    data.bookings.take(8).forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 16.dp))
                        HomeAppointmentRow(
                            b, wide,
                            onOpen = { go(Screen.PatientDetail(b.patientId)) },
                            onStart = { go(Screen.VisitForm(b.patientId)) },
                            onRemind = { openWhatsApp(context, b.phone, appointmentReminder(b, data.clinicName)) }
                        )
                    }
                }
            }
        }
        val recallSection: @Composable () -> Unit = {
            if (data.recall.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Due for check-up", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink, modifier = Modifier.weight(1f))
                    Pill("${data.recall.size}", HomeColors.AmberSoft, HomeColors.Amber)
                }
                Text("Last visit more than 6 months ago", fontSize = 13.sp, color = Brand.Sub)
                Spacer(Modifier.height(10.dp))
                WhiteCard(padding = 0.dp) {
                    val shown = if (showAllRecall) data.recall else data.recall.take(4)
                    shown.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { go(Screen.PatientDetail(p.id)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(p.name, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Last visit: ${relativeTime(p.lastVisit)}", fontSize = 12.sp, color = Brand.Sub, maxLines = 1)
                            }
                            SoftButton("Remind", AppIcons.Chat, height = 38.dp, bg = HomeColors.TealSoft, fg = HomeColors.TealText) {
                                openWhatsApp(context, p.phone, recallMessage(p, data.clinicName))
                            }
                        }
                    }
                    if (data.recall.size > 4) {
                        Text(
                            if (showAllRecall) "Show less" else "Show all ${data.recall.size}",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.TealText,
                            modifier = Modifier.fillMaxWidth().clickable { showAllRecall = !showAllRecall }.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ───── Teal header ─────
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 44.dp)).background(HomeColors.Teal)) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = side, end = side, top = 14.dp, bottom = 64.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (openMenu != null) {
                            HeaderButton(Icons.Filled.Menu, onClick = openMenu)
                            Spacer(Modifier.width(14.dp))
                        }
                        LogoBadge()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                words.firstOrNull() ?: data.clinicName, color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            val rest = words.drop(1).joinToString(" ")
                            if (rest.isNotBlank()) {
                                Text(rest, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HeaderButton(HomeIcons.Bell, dot = alerts.isNotEmpty()) { alertsOpen = true }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("${greeting()},", color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
                    Text(
                        data.doctorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(fmtDayLong(todayEpochDay()), color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp)
                    Spacer(Modifier.height(22.dp))
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                            .clickable { go(Screen.Patients(focusSearch = true)) }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Brand.Sub, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Search patient by name or mobile no.", fontSize = 15.sp, color = Brand.Faint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ───── Four cards, overlapping the header ─────
            val appointmentsTile: @Composable (Modifier) -> Unit = { m ->
                StatTile(
                    HomeIcons.Calendar, HomeColors.Teal, HomeColors.TealSoft, "${data.bookings.size}", Brand.Ink,
                    "Appointments today", if (data.bookings.isEmpty()) "None booked yet" else "$done done · $waiting to go", m
                ) { go(Screen.Schedule) }
            }
            val newTile: @Composable (Modifier) -> Unit = { m ->
                StatTile(
                    HomeIcons.PersonAdd, HomeColors.Teal, HomeColors.TealSoft, "${data.newToday}", Brand.Ink,
                    "New patients", "${data.newWeek} this week", m
                ) { go(Screen.Patients()) }
            }
            val collectedTile: @Composable (Modifier) -> Unit = { m ->
                StatTile(
                    HomeIcons.Rupee, HomeColors.Teal, HomeColors.TealSoft, rs(data.collected), Brand.Ink,
                    "Collected today", if (data.paymentsToday == 1) "1 payment" else "${data.paymentsToday} payments", m
                ) { go(Screen.Finance) }
            }
            val duesTile: @Composable (Modifier) -> Unit = { m ->
                StatTile(
                    HomeIcons.Clock, HomeColors.Amber, HomeColors.AmberSoft, rs(dueTotal),
                    if (dueTotal > 0) HomeColors.DueRed else Brand.GreenDeep,
                    "Pending dues", if (data.dues.size == 1) "1 patient" else "${data.dues.size} patients", m
                ) { go(Screen.Patients(filter = 1)) }
            }
            Column(Modifier.padding(horizontal = side).pullUp(44.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        appointmentsTile(Modifier.weight(1f))
                        newTile(Modifier.weight(1f))
                        collectedTile(Modifier.weight(1f))
                        duesTile(Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        appointmentsTile(Modifier.weight(1f))
                        newTile(Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        collectedTile(Modifier.weight(1f))
                        duesTile(Modifier.weight(1f))
                    }
                }
            }

            // ───── Quick actions, appointments, check-ups ─────
            Column(Modifier.padding(horizontal = side).padding(top = 24.dp, bottom = 28.dp)) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1.4f)) {
                            appointmentsSection()
                            recallSection()
                        }
                        Column(Modifier.weight(1f)) { quickSection(2) }
                    }
                } else {
                    quickSection(4)
                    Spacer(Modifier.height(16.dp))
                    appointmentsSection()
                    recallSection()
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    // ───── Pop-ups ─────
    if (alertsOpen) AlertsDialog(alerts) { alertsOpen = false }
    picker?.let { purpose ->
        PatientPickerDialog(
            db,
            when (purpose) {
                "book" -> "Book appointment"
                "treatment" -> "Add treatment"
                else -> "Record payment"
            },
            onDismiss = { picker = null }
        ) { p ->
            picker = null
            when (purpose) {
                "book" -> bookFor = p
                "treatment" -> go(Screen.VisitForm(p.id))
                else -> payFor = p
            }
        }
    }
    bookFor?.let { p ->
        val existing = remember(p.id) { db.nextBooking(p.id) }
        BookingDialog(initial = existing, defaultReason = "Check-up", onDismiss = { bookFor = null }, onCancelBooking = null) { day, time, why ->
            db.saveBooking(p.id, day, time, why)
            bookFor = null
            DataBus.changed()
            notify("Appointment booked for ${p.name} ✓")
        }
    }
    payFor?.let { p ->
        ReceivePaymentDialog(balance = p.remaining, onDismiss = { payFor = null }) { amount, mode ->
            val id = db.addPayment(p.id, amount, mode)
            payFor = null
            DataBus.changed()
            notify("Payment of ${rs(amount)} received ✓")
            receiptFor = p to id
        }
    }
    receiptFor?.let { (p, id) ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Send the receipt to ${p.name}",
            phone = p.phone,
            message = "Hello ${p.name.trim().substringBefore(" ")}, here is your payment receipt from ${data.clinicName}. Thank you!",
            build = { ctx -> paymentReceiptPdf(ctx, db, id) },
            onDismiss = { receiptFor = null }
        )
    }
}

// ───────────── Pieces of the home page ─────────────
@Composable
private fun HeaderButton(icon: ImageVector, dot: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(48.dp)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(10.dp).clip(CircleShape).background(HomeColors.Dot))
    }
}

@Composable
private fun LogoBadge() {
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(Color.White).border(2.5.dp, Brand.Gold, CircleShape).padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.clinic_logo), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

@Composable
private fun WhiteCard(padding: Dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().shadow(8.dp, shape, ambientColor = Color(0x12000000), spotColor = Color(0x1C000000))
            .clip(shape).background(Color.White).padding(padding),
        content = content
    )
}

@Composable
private fun StatTile(
    icon: ImageVector,
    tint: Color,
    soft: Color,
    value: String,
    valueColor: Color,
    label: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.shadow(10.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x22000000))
            .clip(shape).background(Color.White).clickable(onClick = onClick).padding(18.dp)
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 13.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(66.dp).shadow(4.dp, shape, ambientColor = Color(0x10000000), spotColor = Color(0x18000000))
                .clip(shape).background(Color.White).border(1.dp, Brand.Line, shape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = HomeColors.Teal, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 13.sp, color = Brand.Ink, textAlign = TextAlign.Center, lineHeight = 17.sp, maxLines = 2)
    }
}

@Composable
private fun HomeAppointmentRow(b: DayBooking, wide: Boolean, onOpen: () -> Unit, onStart: () -> Unit, onRemind: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(b.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(b.time.ifBlank { "Any time" }, b.reason).filter { it.isNotBlank() }.joinToString("  ·  ")
            Text(sub, fontSize = 12.sp, color = HomeColors.TealText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.status == 1) {
            Pill("Done", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
        } else {
            if (wide && b.phone.isNotBlank()) {
                CircleIconButton(AppIcons.Chat, onRemind, bg = HomeColors.TealSoft, tint = HomeColors.Teal, size = 38.dp)
                Spacer(Modifier.width(8.dp))
            }
            SoftButton("Start", null, height = 38.dp, bg = HomeColors.TealSoft, fg = HomeColors.TealText, onClick = onStart)
        }
    }
}

@Composable
private fun AlertsDialog(items: List<HomeAlert>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color.White).padding(22.dp)
        ) {
            Text("Notifications", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Spacer(Modifier.height(10.dp))
            if (items.isEmpty()) {
                Text("All caught up ✓", fontSize = 14.sp, color = Brand.GreenText, modifier = Modifier.padding(vertical = 8.dp))
            }
            items.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        onDismiss()
                        a.onClick()
                    }.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(a.soft), contentAlignment = Alignment.Center) {
                        Icon(a.icon, null, tint = a.tint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
                        Text(a.sub, fontSize = 12.sp, color = Brand.Sub)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SoftButton("Close", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
        }
    }
}

/** Quick search to choose a patient, used by Book appointment, Add treatment and Record payment. */
@Composable
fun PatientPickerDialog(db: ClinicDb, title: String, onDismiss: () -> Unit, onPick: (Patient) -> Unit) {
    val everyone = remember { db.getPatients("") }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shown = remember(query, everyone) { filterPatients(everyone, query, 0, emptySet()).take(60) }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 620.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color.White).padding(20.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Brand.Ink)
            Text("Choose the patient", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Soft).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = Brand.Faint)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Name or mobile number", color = Brand.Faint, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Poppins, fontSize = 15.sp, color = Brand.Ink),
                        cursorBrush = SolidColor(HomeColors.Teal),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color.White).clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Close, null, tint = Brand.Sub, modifier = Modifier.size(16.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Text("No patients found", fontSize = 14.sp, color = Brand.Sub, modifier = Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(shown, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(p) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(p.name, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.phone.ifBlank { "No mobile number" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1)
                        }
                        if (p.remaining > 0.01) Text(rs(p.remaining), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Red)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SoftButton("Cancel", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
        }
    }
}
