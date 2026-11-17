@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────── Classic fonts (v35: Home screen first) ─────────────
val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold)
)

/** Merriweather in two fixed weights (the variable file showed only its thinnest weight on the phone). */
val Merriweather = FontFamily(
    Font(R.font.merriweather_regular, FontWeight.Normal),
    Font(R.font.merriweather_bold, FontWeight.Bold)
)

private fun Typography.withFamily(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

private val ClassicTypography = Typography().withFamily(Lato)

/** The Home screen in the classic fonts. Other screens keep Poppins until the fonts are approved on the phone. */
@Composable
fun TodayScreen(db: ClinicDb, go: (Screen) -> Unit) {
    MaterialTheme(typography = ClassicTypography) { TodayScreenContent(db, go) }
}

// ───────────── Recent patients on the Home screen ─────────────
/** One patient on the Home list: their latest visit and what was done. */
private class SeenPatient(val patient: Patient, val time: Long, val what: String)

/** Days chosen with the calendar button (first and last day included). label is "" for picked dates. */
private class SeenRange(val from: Long, val to: Long, val label: String)

private fun seenDayStart(day: Long): Long =
    LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Patients seen between two times, newest first. Seen = a treatment, a sitting, a payment, a prescription,
 * a finished appointment or registering as a new patient. limit = 0 means everyone.
 */
private fun ClinicDb.patientsSeen(fromMs: Long, toMs: Long, fromDay: Long, toDay: Long, limit: Int): List<SeenPatient> {
    class Event(val patientId: Long, val time: Long, val what: String, val rank: Int)
    val events = mutableListOf<Event>()
    fun collect(sql: String, args: Array<String>, rank: Int, fixed: String?, dayColumn: Boolean = false) {
        runCatching {
            readableDatabase.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    val t = if (dayColumn) seenDayStart(c.getLong(1)) else c.getLong(1)
                    events.add(Event(c.getLong(0), t, fixed ?: c.getString(2).orEmpty(), rank))
                }
            }
        }
    }
    val ms = arrayOf(fromMs.toString(), toMs.toString())
    collect("SELECT patient_id, created_at, name FROM treatments WHERE created_at >= ? AND created_at < ?", ms, 3, null)
    collect("SELECT patient_id, done_at, name FROM treatments WHERE done_at >= ? AND done_at < ?", ms, 3, null)
    collect(
        "SELECT s.patient_id, s.done_at, t.name FROM sittings s JOIN treatments t ON t.id = s.treatment_id " +
            "WHERE s.done = 1 AND s.done_at >= ? AND s.done_at < ?", ms, 3, null
    )
    collect(
        "SELECT patient_id, day, reason FROM appointments WHERE status = 1 AND day >= ? AND day <= ?",
        arrayOf(fromDay.toString(), toDay.toString()), 2, null, dayColumn = true
    )
    collect("SELECT patient_id, date FROM payments WHERE amount <> 0 AND date >= ? AND date < ?", ms, 1, "Payment")
    collect("SELECT patient_id, date FROM prescriptions WHERE date >= ? AND date < ?", ms, 1, "Prescription")
    collect("SELECT id, created_at FROM patients WHERE created_at >= ? AND created_at < ?", ms, 0, "New patient")

    val zone = ZoneId.systemDefault()
    fun dayOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()
    val latest = HashMap<Long, Long>()
    events.forEach { e ->
        if (e.time > (latest[e.patientId] ?: Long.MIN_VALUE)) {
            latest[e.patientId] = e.time
        }
    }
    // On the patient's latest day, show the treatment rather than the payment
    val shown = HashMap<Long, Event>()
    events.forEach { e ->
        val last = latest[e.patientId] ?: return@forEach
        if (dayOf(e.time) == dayOf(last)) {
            val b = shown[e.patientId]
            if (b == null || e.rank > b.rank || (e.rank == b.rank && e.time > b.time)) {
                shown[e.patientId] = e
            }
        }
    }
    val people = getPatients("").associateBy { it.id }
    val list = latest.entries.sortedByDescending { it.value }.mapNotNull { (id, time) ->
        val p = people[id] ?: return@mapNotNull null
        SeenPatient(p, time, shown[id]?.what.orEmpty())
    }
    return if (limit > 0) list.take(limit) else list
}

private fun seenWhen(t: Long): String {
    val day = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        day == today -> "today"
        day == today.minusDays(1) -> "yesterday"
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

private fun presetRange(key: String): SeenRange {
    val today = LocalDate.now()
    val t = today.toEpochDay()
    return when (key) {
        "Today" -> SeenRange(t, t, "Today")
        "Yesterday" -> SeenRange(t - 1, t - 1, "Yesterday")
        "This week" -> SeenRange(today.with(DayOfWeek.MONDAY).toEpochDay(), t, "This week")
        "This month" -> SeenRange(today.withDayOfMonth(1).toEpochDay(), t, "This month")
        else -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            SeenRange(first.toEpochDay(), first.plusMonths(1).minusDays(1).toEpochDay(), "Last month")
        }
    }
}

/** "14 Oct", "1–31 Oct" or "28 Sep – 3 Oct" */
private fun rangeText(r: SeenRange): String {
    val a = LocalDate.ofEpochDay(r.from)
    val b = LocalDate.ofEpochDay(r.to)
    val full = DateTimeFormatter.ofPattern(if (b.year == LocalDate.now().year) "d MMM" else "d MMM yyyy", Locale.getDefault())
    return when {
        r.from == r.to -> a.format(full)
        a.year == b.year && a.month == b.month -> "${a.dayOfMonth}–${b.format(full)}"
        else -> a.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + " – " + b.format(full)
    }
}

private fun utcDay(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

/** Recent patients on the Home screen. The calendar button swaps in the patients seen on chosen days until × is tapped. */
@Composable
fun RecentPatientsCard(db: ClinicDb, go: (Screen) -> Unit) {
    val version = DataBus.version
    var range by remember { mutableStateOf<SeenRange?>(null) }
    var askDates by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    val rows by produceState<List<SeenPatient>?>(null, version, range) {
        val r = range
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (r == null) db.patientsSeen(0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE, 5)
                else db.patientsSeen(seenDayStart(r.from), seenDayStart(r.to + 1), r.from, r.to, 0)
            }.getOrElse { emptyList() }
        }
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val r = range
                Text(
                    if (r == null) "Recent patients" else r.label.ifBlank { rangeText(r) },
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Merriweather, color = Brand.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (r != null) {
                    val count = rows?.size ?: 0
                    val people = if (count == 1) "1 patient" else "$count patients"
                    Text(if (r.label.isBlank()) people else "${rangeText(r)} · $people", fontSize = 13.sp, color = Brand.Slate)
                }
            }
            CircleIconButton(
                Icons.Filled.DateRange, { askDates = true },
                bg = if (range != null) Brand.GreenSoft else Brand.Surface,
                tint = if (range != null) Brand.GreenDeep else Brand.Ink, size = 38.dp
            )
            Spacer(Modifier.width(8.dp))
            if (range == null) {
                Text(
                    "See all", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Brand.GreenText,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { go(Screen.Patients()) }.padding(6.dp)
                )
            } else {
                CircleIconButton(Icons.Filled.Close, { range = null }, size = 38.dp)
            }
        }
        AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            val list = rows
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(64.dp))
            } else if (list.isEmpty()) {
                Text(
                    if (range == null) "Patients you see will show here." else "No patients seen on these days.",
                    fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(18.dp)
                )
            } else {
                key(range) {
                    // Five rows fit; more scroll inside the card so the Home screen stays the same length
                    Column(Modifier.fillMaxWidth().heightIn(max = 290.dp).verticalScroll(rememberScrollState())) {
                        list.forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider(color = Brand.Line, modifier = Modifier.padding(horizontal = 14.dp))
                            key(s.patient.id) { SeenRow(s) { go(Screen.PatientDetail(s.patient.id)) } }
                        }
                    }
                }
            }
        }
    }

    if (askDates) {
        SeenDatesDialog(
            current = range,
            onDismiss = { askDates = false },
            onPick = { r ->
                range = r
                askDates = false
            },
            onCalendar = {
                askDates = false
                calendar = true
            }
        )
    }
    if (calendar) {
        SeenCalendarDialog(onDismiss = { calendar = false }) { r ->
            range = r
            calendar = false
        }
    }
}

@Composable
private fun SeenRow(s: SeenPatient, onClick: () -> Unit) {
    val p = s.patient
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p.name, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Merriweather, color = Brand.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${s.what.ifBlank { "Visit" }} · ${seenWhen(s.time)}", fontSize = 13.sp, color = Brand.Slate,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (p.remaining > 0.01) {
            Pill("${rs(p.remaining)} due", Brand.RedSoft, Brand.Red)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Faint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SeenDatesDialog(current: SeenRange?, onDismiss: () -> Unit, onPick: (SeenRange) -> Unit, onCalendar: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(24.dp)
        ) {
            Text("Show patients seen", fontWeight = FontWeight.Bold, fontFamily = Merriweather, fontSize = 19.sp, color = Brand.Ink)
            Text("Treatment, sitting, payment or visit on those days", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today", "Yesterday", "This week", "This month", "Last month").forEach { key ->
                    ChoiceChip(key, current?.label == key) { onPick(presetRange(key)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            SoftButton(
                "Pick dates on the calendar", Icons.Filled.DateRange, Modifier.fillMaxWidth(),
                bg = Brand.Soft, fg = Brand.Ink, onClick = onCalendar
            )
            Text(
                "One date for a single day, two dates for a range", fontSize = 12.sp, color = Brand.Sub,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            Spacer(Modifier.height(12.dp))
            SoftButton("Close", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
        }
    }
}

@Composable
private fun SeenCalendarDialog(onDismiss: () -> Unit, onPick: (SeenRange) -> Unit) {
    val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    state.selectedStartDateMillis?.let { start ->
                        val end = state.selectedEndDateMillis ?: start
                        onPick(SeenRange(utcDay(start), utcDay(end), ""))
                    }
                }
            ) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text("Pick one date, or two for a range", modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            showModeToggle = false
        )
    }
}
