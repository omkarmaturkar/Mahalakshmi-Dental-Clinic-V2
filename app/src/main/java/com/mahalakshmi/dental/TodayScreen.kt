@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.mahalakshmi.dental

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────── Colours & icons of the home page ─────────────
object HomeColors {
    val Teal get() = Brand.GreenDeep
    val TealText get() = Brand.GreenText
    val TealSoft get() = Brand.GreenSoft
    val Amber get() = Brand.GoldDeep
    val AmberSoft get() = Brand.GoldSoft
    val Dot get() = Brand.Sun
    val Page get() = Brand.Bg
    val DueRed get() = Brand.Red
}

/** Text colours on the dark next-patient card. */
private val OnRich = Color(0xFFF2F4F1)
private val RichSub = Color(0xFFA4A8AB)

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
    val queue: List<DayBooking>,
    val folks: Map<Long, Patient>,
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

private fun minutesNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/** "in 25 min", "now", "Tomorrow"… for the patient card. */
private fun whenLabel(b: DayBooking): String {
    if (b.day != todayEpochDay()) return fmtDay(b.day)
    if (b.time.isBlank()) return "today"
    val at = timeMinutes(b.time)
    if (at >= 24 * 60) return "today"
    val diff = at - minutesNow()
    return when {
        diff > 59 -> "in ${diff / 60} hr" + if (diff % 60 > 0) " ${diff % 60} min" else ""
        diff > 0 -> "in $diff min"
        diff >= -15 -> "now"
        else -> "waiting"
    }
}

/** "Dr. Tushar Maturkar" → "Dr. Tushar" */
private fun shortDoctor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "Doctor"
    return if (parts.size > 1 && parts[0].trimEnd('.').equals("dr", ignoreCase = true)) "Dr. ${parts[1]}" else parts[0]
}

/** Loads everything for the home page. If anything goes wrong, the page still opens and the problem is recorded. */
private fun loadHome(db: ClinicDb, context: Context): HomeData = try {
    val everyone = db.getPatients("")
    val today = todayEpochDay()
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val ahead = db.bookingsBetween(today, today + 60)
    val booked = ahead.map { it.patientId }.toSet()
    val pending = ahead.filter { it.status == 0 }
    val nowMin = minutesNow()
    // The swipeable card: everyone still to come on the next clinic day (today, if anyone is left today)
    val first = pending.indexOfFirst { it.day > today || it.time.isBlank() || timeMinutes(it.time) >= nowMin - 60 }
    val from = if (first >= 0) pending.drop(first) else pending
    val queue = from.filter { it.day == from.firstOrNull()?.day }
    val queueIds = queue.map { it.patientId }.toSet()
    val lastBackup = maxOf(DriveBackup.lastBackup(context), Backup.lastBackup(context))
    HomeData(
        clinicName = db.clinicInfo().name,
        doctorName = db.mainDoctor()?.name?.takeIf { it.isNotBlank() } ?: "Doctor",
        bookings = ahead.filter { it.day == today },
        queue = queue,
        folks = everyone.filter { it.id in queueIds }.associateBy { it.id },
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
    HomeData(DEFAULT_CLINIC_NAME, "Doctor", emptyList(), emptyList(), emptyMap(), 0, 0, 0.0, 0, emptyList(), emptyList(), true)
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

    StatusBarIcons(dark = !AppTheme.dark)

    val dueTotal = data.dues.sumOf { it.remaining }
    val done = data.bookings.count { it.status == 1 }
    val waiting = data.bookings.count { it.status == 0 }
    val words = data.clinicName.trim().split(" ").filter { it.isNotBlank() }
    val dateLine = remember(version) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    val alerts = buildList<HomeAlert> {
        if (waiting > 0) {
            add(HomeAlert(HomeIcons.Calendar, Brand.Ink, Brand.Soft,
                if (waiting == 1) "1 appointment waiting today" else "$waiting appointments waiting today",
                "Open today's schedule") { go(Screen.Schedule) })
        }
        if (!data.backupOk) {
            add(HomeAlert(AppIcons.Cloud, Brand.GoldDeep, Brand.GoldSoft,
                "Backup needs attention", "No backup in the last 3 days") { go(Screen.BackupPage) })
        }
        if (data.dues.isNotEmpty()) {
            add(HomeAlert(HomeIcons.Clock, Brand.Red, Brand.RedSoft,
                "${rs(dueTotal)} pending dues", if (data.dues.size == 1) "1 patient" else "${data.dues.size} patients") {
                go(Screen.Patients(filter = 1))
            })
        }
        if (data.recall.isNotEmpty()) {
            add(HomeAlert(HomeIcons.People, Brand.Ink, Brand.Soft,
                "${data.recall.size} due for a check-up", "Send them a reminder from the home page") {})
        }
    }
    val actions = listOf(
        HomeAction("New patient", HomeIcons.PersonAdd) { go(Screen.PatientForm()) },
        HomeAction("Book", HomeIcons.CalendarAdd) { picker = "book" },
        HomeAction("Treatment", HomeIcons.ClipboardAdd) { picker = "treatment" },
        HomeAction("Payment", HomeIcons.Wallet) { picker = "payment" }
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 760.dp
        val side = if (wide) 28.dp else 20.dp

        val spotlight: @Composable () -> Unit = {
            SpotlightCard(
                queue = data.queue,
                folks = data.folks,
                onCall = { b -> callNumber(context, b.phone) },
                onRemind = { b -> openWhatsApp(context, b.phone, appointmentReminder(b, data.clinicName)) },
                onPrimary = { b ->
                    go(if (b.day == todayEpochDay()) Screen.VisitForm(b.patientId) else Screen.PatientDetail(b.patientId))
                },
                onOpen = { b -> go(Screen.PatientDetail(b.patientId)) },
                onBook = { picker = "book" }
            )
        }
        val stats: @Composable () -> Unit = {
            HomeStats(
                done = done, total = data.bookings.size, collected = data.collected, payments = data.paymentsToday,
                dueTotal = dueTotal, dueCount = data.dues.size, newToday = data.newToday, newWeek = data.newWeek, go = go
            )
        }
        val quick: @Composable () -> Unit = { HomeQuickRow(actions) }
        val today: @Composable () -> Unit = {
            if (data.bookings.isNotEmpty()) HomeTodayList(data.bookings, go)
        }
        val recallSection: @Composable () -> Unit = {
            if (data.recall.isNotEmpty()) {
                Column {
                    HomeSectionTitle(
                        "Recalls due",
                        if (data.recall.size > 4) (if (showAllRecall) "Show less" else "See all ${data.recall.size}") else null
                    ) { showAllRecall = !showAllRecall }
                    Text("Last visit more than 6 months ago", fontSize = 13.sp, color = Brand.Sub)
                    Spacer(Modifier.height(10.dp))
                    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
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
                                    Text(
                                        p.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Brand.Ink,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text("Last visit: ${relativeTime(p.lastVisit)}", fontSize = 12.sp, color = Brand.Sub, maxLines = 1)
                                }
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(Brand.Soft)
                                        .clickable { openWhatsApp(context, p.phone, recallMessage(p, data.clinicName)) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(AppIcons.Chat, "Send reminder", tint = Brand.Ink, modifier = Modifier.size(19.dp)) }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ───── Top bar ─────
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = side, end = side, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (openMenu != null) {
                    CircleIconButton(Icons.Filled.Menu, openMenu, size = 42.dp)
                    Spacer(Modifier.width(10.dp))
                }
                LogoTile(40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        words.firstOrNull() ?: data.clinicName, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp, lineHeight = 24.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val rest = words.drop(1).joinToString(" ")
                    if (rest.isNotBlank()) {
                        Text(
                            rest, fontSize = 11.sp, color = Brand.Sub, letterSpacing = 1.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                CircleIconButton(Icons.Filled.Search, { go(Screen.Patients(focusSearch = true)) }, size = 42.dp)
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleIconButton(HomeIcons.Bell, { alertsOpen = true }, size = 42.dp)
                    if (alerts.isNotEmpty()) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(10.dp)
                                .clip(CircleShape).background(Brand.Sun).border(1.5.dp, Brand.Surface, CircleShape)
                        )
                    }
                }
            }

            // ───── Greeting and the light / dark button ─────
            Row(
                Modifier.fillMaxWidth().padding(start = side, end = side, top = 26.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(dateLine, fontSize = 12.sp, color = Brand.Sub, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${greeting()},", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold,
                        fontSize = 34.sp, lineHeight = 36.sp, color = Brand.Ink
                    )
                    Text(
                        shortDoctor(data.doctorName), fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold,
                        fontSize = 34.sp, lineHeight = 36.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                ThemeToggle(44.dp)
            }

            // ───── Content ─────
            if (wide) {
                Row(
                    Modifier.padding(start = side, end = side, top = 22.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        spotlight()
                        today()
                        recallSection()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        stats()
                        quick()
                    }
                }
            } else {
                Column(
                    Modifier.padding(start = side, end = side, top = 22.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    spotlight()
                    stats()
                    quick()
                    today()
                    recallSection()
                }
            }
            Spacer(Modifier.navigationBarsPadding())
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
/** Money in the serif font with a small ₹ sign. */
@Composable
private fun HomeMoney(amount: Double, size: TextUnit, color: Color, symbol: Color) {
    val digits = remember(amount) { rs(amount).removePrefix("₹") }
    Text(
        buildAnnotatedString {
            withStyle(
                SpanStyle(fontFamily = BodyFont, fontSize = size * 0.5f, color = symbol, baselineShift = BaselineShift(0.45f))
            ) { append("₹") }
            append(digits)
        },
        fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = size, color = color, maxLines = 1
    )
}

/** The rich dark patient card. Swipe it to see the next patient booked for the same day. */
@Composable
private fun SpotlightCard(
    queue: List<DayBooking>,
    folks: Map<Long, Patient>,
    onCall: (DayBooking) -> Unit,
    onRemind: (DayBooking) -> Unit,
    onPrimary: (DayBooking) -> Unit,
    onOpen: (DayBooking) -> Unit,
    onBook: () -> Unit
) {
    RichCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.fillMaxWidth()) {
            Icon(
                AppIcons.Sparkle, null, tint = Brand.Sun.copy(alpha = 0.07f),
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 46.dp, y = 46.dp).size(180.dp)
            )
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                if (queue.isEmpty()) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        SpotLabel("No patients booked", null)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Your schedule is open", color = OnRich, fontFamily = DisplayFont,
                            fontWeight = FontWeight.SemiBold, fontSize = 27.sp
                        )
                        Text("Book a patient's next visit in a few taps", color = RichSub, fontSize = 13.sp)
                        Spacer(Modifier.height(18.dp))
                        GradientButton("Book appointment", Modifier.fillMaxWidth(), HomeIcons.CalendarAdd, height = 50.dp, onClick = onBook)
                    }
                } else {
                    val pager = rememberPagerState(pageCount = { queue.size })
                    HorizontalPager(state = pager, pageSpacing = 8.dp, key = { queue[it].id }) { i ->
                        val b = queue[i]
                        val patient = folks[b.patientId]
                        val balance = patient?.remaining ?: 0.0
                        val medical = patient?.alerts.orEmpty()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            SpotLabel(
                                (if (i == 0) "Next patient · " else "Coming up · ") + whenLabel(b),
                                b.time.ifBlank { null }
                            )
                            Spacer(Modifier.height(14.dp))
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(b) }) {
                                Text(
                                    b.name, color = OnRich, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold,
                                    fontSize = 29.sp, lineHeight = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    b.reason.ifBlank { "Check-up" }, color = RichSub, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (medical.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                DarkChip(medical.take(2).joinToString(" · "), Color(0xFFFF9E8F), Color(0x33D9483A), Icons.Filled.Warning)
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (balance > 0.01) "Due" else "Account", color = RichSub, fontSize = 11.sp, letterSpacing = 1.sp)
                                    if (balance > 0.01) {
                                        HomeMoney(balance, 26.sp, OnRich, Brand.Sun)
                                    } else {
                                        Text("No dues ✓", color = Color(0xFFA3D447), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (b.phone.isNotBlank()) {
                                    GlassIcon(Icons.Filled.Call, "Call") { onCall(b) }
                                    Spacer(Modifier.width(8.dp))
                                    GlassIcon(AppIcons.Chat, "Send reminder") { onRemind(b) }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            GradientButton(
                                if (b.day == todayEpochDay()) "Start visit" else "Open file",
                                Modifier.fillMaxWidth(), height = 50.dp
                            ) { onPrimary(b) }
                        }
                    }
                    if (queue.size > 1) {
                        Spacer(Modifier.height(14.dp))
                        PageDots(queue.size, pager.currentPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (count > 8) {
            Text(
                "${current + 1} of $count  ·  swipe for more", color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
        } else {
            repeat(count) { i ->
                val active = i == current
                val w by animateDpAsState(if (active) 20.dp else 7.dp, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).height(7.dp).width(w).clip(RoundedCornerShape(50))
                        .background(if (active) Brand.Sun else Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
private fun SpotLabel(text: String, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Sparkle, null, tint = Brand.Sun, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text, color = Brand.Sun, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = OnRich, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DarkChip(text: String, fg: Color, bg: Color, icon: ImageVector?) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GlassIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = OnRich, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun HomeStats(
    done: Int,
    total: Int,
    collected: Double,
    payments: Int,
    dueTotal: Double,
    dueCount: Int,
    newToday: Int,
    newWeek: Int,
    go: (Screen) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(Modifier.fillMaxWidth(), padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { go(Screen.Schedule) }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$done", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Brand.Ink)
                        if (total > 0) {
                            Text(" of $total", fontSize = 14.sp, color = Brand.Sub, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                    Text("Patients seen", fontSize = 11.sp, color = Brand.Sub, letterSpacing = 1.sp)
                }
                Box(Modifier.width(1.dp).height(44.dp).background(Brand.Line))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { go(Screen.Finance) }) {
                    HomeMoney(collected, 30.sp, Brand.Ink, Brand.Sub)
                    Text(
                        if (payments == 1) "Collected · 1 payment" else "Collected · $payments payments",
                        fontSize = 11.sp, color = Brand.Sub, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeSmallStat(
                "Pending dues", dueTotal, null, if (dueTotal > 0.01) Brand.Red else Brand.GreenText,
                if (dueCount == 1) "1 patient" else "$dueCount patients", Modifier.weight(1f)
            ) { go(Screen.Patients(filter = 1)) }
            HomeSmallStat(
                "New patients", null, "$newToday", Brand.Ink, "$newWeek this week", Modifier.weight(1f)
            ) { go(Screen.Patients()) }
        }
    }
}

@Composable
private fun HomeSmallStat(
    label: String,
    money: Double?,
    value: String?,
    color: Color,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    AppCard(modifier, onClick = onClick, padding = 16.dp) {
        Text(label, fontSize = 11.sp, color = Brand.Sub, letterSpacing = 1.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        if (money != null) {
            HomeMoney(money, 24.sp, color, color)
        } else {
            Text(value ?: "", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = color, maxLines = 1)
        }
        Text(sub, fontSize = 12.sp, color = Brand.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeQuickRow(actions: List<HomeAction>) {
    AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(Modifier.fillMaxWidth()) {
            actions.forEach { a -> QuickTile(a.label, a.icon, Modifier.weight(1f), a.onClick) }
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Brand.Soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand.Ink, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 12.sp, color = Brand.Ink, textAlign = TextAlign.Center, lineHeight = 15.sp, maxLines = 2)
    }
}

/** Today's appointments: done ones get a green tick. */
@Composable
private fun HomeTodayList(bookings: List<DayBooking>, go: (Screen) -> Unit) {
    val shown = bookings.take(5)
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
                color = Brand.Ink, modifier = Modifier.weight(1f)
            )
            Text(
                "Schedule", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.GoldDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { go(Screen.Schedule) }.padding(8.dp)
            )
        }
        shown.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 18.dp))
            val isDone = b.status == 1
            Row(
                Modifier.fillMaxWidth().clickable { go(Screen.PatientDetail(b.patientId)) }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(b.time.ifBlank { "Any time" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, modifier = Modifier.width(70.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        b.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (isDone) Brand.Sub else Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(b.reason.ifBlank { "Check-up" }, fontSize = 11.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (b.status) {
                    1 -> Icon(Icons.Filled.Check, "Done", tint = Brand.GreenText, modifier = Modifier.size(18.dp))
                    2 -> Text("Cancelled", fontSize = 11.sp, color = Brand.Faint)
                    else -> Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Sun))
                }
            }
        }
        if (bookings.size > shown.size) {
            Text(
                "+ ${bookings.size - shown.size} more today", fontSize = 12.sp, color = Brand.Sub,
                modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 12.dp)
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
            color = Brand.Ink, modifier = Modifier.weight(1f)
        )
        if (action != null) {
            Text(
                action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.GoldDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(6.dp)
            )
        }
    }
}

@Composable
private fun AlertsDialog(items: List<HomeAlert>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(22.dp)
        ) {
            Text("Notifications", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
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
                    Box(Modifier.size(40.dp).clip(CircleShape).background(a.soft), contentAlignment = Alignment.Center) {
                        Icon(a.icon, null, tint = a.tint, modifier = Modifier.size(21.dp))
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
                .clip(RoundedCornerShape(28.dp)).background(Brand.Surface).padding(20.dp)
        ) {
            Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, color = Brand.Ink)
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
                        textStyle = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, color = Brand.Ink),
                        cursorBrush = SolidColor(Brand.Focus),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Brand.Surface).clickable { query = "" },
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
                            Text(p.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
