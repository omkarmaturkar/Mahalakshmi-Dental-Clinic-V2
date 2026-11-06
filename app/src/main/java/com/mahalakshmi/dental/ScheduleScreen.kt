package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DayBooking(
    val id: Long,
    val patientId: Long,
    val name: String,
    val phone: String,
    val day: Long,
    val time: String,
    val reason: String,
    val status: Int
)

/** Minutes after midnight for times like "10:30 AM"; unknown times go last. */
fun timeMinutes(time: String): Int {
    val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*([AaPp])?").find(time.trim()) ?: return 24 * 60
    var h = m.groupValues[1].toIntOrNull() ?: return 24 * 60
    val min = m.groupValues[2].toIntOrNull() ?: 0
    val ap = m.groupValues[3].lowercase()
    if (ap == "p" && h < 12) h += 12
    if (ap == "a" && h == 12) h = 0
    return h * 60 + min
}

/** Booked (and done) appointments between two days, in time order. Cancelled ones are left out. */
fun ClinicDb.bookingsBetween(fromDay: Long, toDay: Long): List<DayBooking> {
    val list = mutableListOf<DayBooking>()
    readableDatabase.rawQuery(
        "SELECT a.id, a.patient_id, p.name, p.phone, a.day, a.time, a.reason, a.status FROM appointments a " +
            "JOIN patients p ON p.id = a.patient_id WHERE a.status <> 2 AND a.day >= ? AND a.day <= ?",
        arrayOf(fromDay.toString(), toDay.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(
                DayBooking(
                    c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "",
                    c.getLong(4), c.getString(5) ?: "", c.getString(6) ?: "", c.getInt(7)
                )
            )
        }
    }
    return list.sortedWith(compareBy<DayBooking>({ it.day }, { timeMinutes(it.time) }, { it.name }))
}

fun ClinicDb.cancelBookingById(id: Long) {
    writableDatabase.execSQL("UPDATE appointments SET status = 2 WHERE id = ?", arrayOf(id.toString()))
}

fun appointmentReminder(b: DayBooking, clinicName: String): String {
    val first = b.name.trim().substringBefore(" ")
    val today = todayEpochDay()
    val day = when (b.day) {
        today -> "today"
        today + 1 -> "tomorrow (${fmtDayLong(b.day)})"
        else -> "on ${fmtDayLong(b.day)}"
    }
    val at = if (b.time.isNotBlank()) " at ${b.time}" else ""
    return "Hello $first, this is a reminder from $clinicName for your dental appointment $day$at. See you soon! 😊"
}

private fun dayTitle(day: Long): String {
    val today = todayEpochDay()
    return when (day) {
        today -> "Today"
        today + 1 -> "Tomorrow"
        else -> fmtDayLong(day)
    }
}

@Composable
fun ScheduleScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val version = DataBus.version
    val today = todayEpochDay()
    val bookings = remember(version) { runCatching { db.bookingsBetween(today, today + 30) }.getOrElse { emptyList() } }
    val clinicName = remember(version) { db.clinicInfo().name }
    val byDay = remember(bookings) { bookings.groupBy { it.day } }
    val tomorrow = bookings.count { it.day == today + 1 && it.status == 0 }
    var picker by remember { mutableStateOf(false) }
    var bookFor by remember { mutableStateOf<Patient?>(null) }
    var cancel by remember { mutableStateOf<DayBooking?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 700.dp
        val side = if (wide) 24.dp else 20.dp
        Column(Modifier.fillMaxSize()) {
            SectionTopBar("Schedule", if (bookings.isEmpty()) "No appointments booked" else "${bookings.size} in the next 30 days") {
                if (wide) GradientButton("Book", Modifier.width(150.dp), HomeIcons.CalendarAdd, height = 48.dp) { picker = true }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = side, end = side, top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (tomorrow > 0) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.GoldSoft).padding(16.dp)
                        ) {
                            Text(
                                if (tomorrow == 1) "1 patient tomorrow" else "$tomorrow patients tomorrow",
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink
                            )
                            Text("Tap 💬 next to each one to send a WhatsApp reminder", fontSize = 12.sp, color = Brand.Sub)
                        }
                    }
                    if (bookings.isEmpty()) EmptyState("No appointments yet", "Tap \"Book\" to book a patient's visit.")
                    byDay.forEach { (day, list) ->
                        Text(
                            dayTitle(day) + "  ·  ${list.size}", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                            color = Brand.Ink, modifier = Modifier.padding(top = 6.dp)
                        )
                        AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                            list.forEachIndexed { i, b ->
                                if (i > 0) HorizontalDivider(color = Brand.Line)
                                ScheduleRow(
                                    b,
                                    onOpen = { go(Screen.PatientDetail(b.patientId)) },
                                    onRemind = { openWhatsApp(context, b.phone, appointmentReminder(b, clinicName)) },
                                    onCancel = { cancel = b }
                                )
                            }
                        }
                    }
                }
                if (!wide) {
                    GradientFab("Book", HomeIcons.CalendarAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { picker = true }
                }
            }
        }
    }

    if (picker) {
        PatientPickerDialog(db, "Book appointment", onDismiss = { picker = false }) { p ->
            picker = false
            bookFor = p
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
    cancel?.let { b ->
        ConfirmDialog(
            "Cancel this appointment?",
            "${b.name} · ${dayTitle(b.day)}" + if (b.time.isNotBlank()) " · ${b.time}" else "",
            confirmText = "Cancel it",
            onConfirm = {
                db.cancelBookingById(b.id)
                cancel = null
                DataBus.changed()
                notify("Appointment cancelled")
            },
            onDismiss = { cancel = null }
        )
    }
}

@Composable
private fun ScheduleRow(b: DayBooking, onOpen: () -> Unit, onRemind: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            b.time.ifBlank { "Any time" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Brand.Ink, modifier = Modifier.width(66.dp)
        )
        Avatar(b.name, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(b.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b.reason.ifBlank { "Check-up" }, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.status == 1) {
            Pill("Done", Brand.GreenSoft, Brand.GreenText, Icons.Filled.Check)
        } else {
            if (b.phone.isNotBlank()) {
                CircleIconButton(AppIcons.Chat, onRemind, bg = Brand.Soft, tint = Brand.Ink, size = 38.dp)
                Spacer(Modifier.width(6.dp))
            }
            CircleIconButton(Icons.Filled.Close, onCancel, bg = Brand.Soft, tint = Brand.Faint, size = 38.dp)
        }
    }
}
