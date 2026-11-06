@file:OptIn(ExperimentalMaterial3Api::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PayRow(val id: Long, val patientId: Long, val name: String, val amount: Double, val date: Long, val mode: String)

fun ClinicDb.paymentsBetween(from: Long, to: Long): List<PayRow> {
    val list = mutableListOf<PayRow>()
    readableDatabase.rawQuery(
        "SELECT y.id, y.patient_id, IFNULL(p.name, ''), y.amount, y.date, IFNULL(y.mode, '') FROM payments y " +
            "LEFT JOIN patients p ON p.id = y.patient_id WHERE y.date >= ? AND y.date < ? AND y.amount <> 0 ORDER BY y.date DESC",
        arrayOf(from.toString(), to.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add(PayRow(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getDouble(3), c.getLong(4), c.getString(5) ?: ""))
        }
    }
    return list
}

/** Fees of treatments started in the period, per doctor (visiting doctors included). */
fun ClinicDb.billedByDoctor(from: Long, to: Long): List<Pair<String, Double>> {
    val names = doctorNames()
    val list = mutableListOf<Pair<String, Double>>()
    readableDatabase.rawQuery(
        "SELECT doctor_id, SUM(fee) FROM treatments WHERE status > 0 AND created_at >= ? AND created_at < ? " +
            "GROUP BY doctor_id ORDER BY SUM(fee) DESC",
        arrayOf(from.toString(), to.toString())
    ).use { c ->
        while (c.moveToNext()) {
            list.add((names[c.getLong(0)]?.takeIf { it.isNotBlank() } ?: "Not assigned") to c.getDouble(1))
        }
    }
    return list
}

private val periodNames = listOf("Today", "This week", "This month", "Last month")
private const val CUSTOM = 4

private fun periodRange(period: Int): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now()
    fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    return when (period) {
        0 -> ms(now) to ms(now.plusDays(1))
        1 -> now.with(DayOfWeek.MONDAY).let { ms(it) to ms(it.plusWeeks(1)) }
        2 -> now.withDayOfMonth(1).let { ms(it) to ms(it.plusMonths(1)) }
        else -> now.withDayOfMonth(1).minusMonths(1).let { ms(it) to ms(it.plusMonths(1)) }
    }
}

private fun dayStartMs(day: Long): Long =
    LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun utcMs(day: Long): Long = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun dayOfUtc(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

/** "12 Oct 2026" for one day, "1 Oct – 15 Oct 2026" for a range. */
private fun rangeLabel(from: Long, to: Long): String {
    val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val long = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    val a = LocalDate.ofEpochDay(from)
    val b = LocalDate.ofEpochDay(to)
    return when {
        from == to -> a.format(long)
        a.year == b.year -> "${a.format(short)} – ${b.format(long)}"
        else -> "${a.format(long)} – ${b.format(long)}"
    }
}

@Composable
fun FinanceScreen(db: ClinicDb, go: (Screen) -> Unit) {
    val version = DataBus.version
    var period by remember { mutableIntStateOf(2) }
    var custom by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val chosen = custom
    val today = todayEpochDay()
    val range = remember(period, chosen, today) {
        if (period == CUSTOM && chosen != null) dayStartMs(chosen.first) to dayStartMs(chosen.second + 1) else periodRange(period)
    }
    val periodLabel = if (period == CUSTOM && chosen != null) rangeLabel(chosen.first, chosen.second) else periodNames[period.coerceAtMost(3)]
    val payments = remember(version, range) { runCatching { db.paymentsBetween(range.first, range.second) }.getOrElse { emptyList() } }
    val byDoctor = remember(version, range) { runCatching { db.billedByDoctor(range.first, range.second) }.getOrElse { emptyList() } }
    val dues = remember(version) { runCatching { db.getPatients("") }.getOrElse { emptyList() }.filter { it.remaining > 0.01 } }
    val total = payments.sumOf { it.amount }
    val byMode = payments.groupBy { it.mode.ifBlank { "Other" } }
        .map { (mode, list) -> mode to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val doctorTotal = byDoctor.sumOf { it.second }
    val dueTotal = dues.sumOf { it.remaining }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 760.dp
        val side = if (wide) 24.dp else 20.dp

        val collectedCard: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(HomeColors.Teal).padding(22.dp)) {
                Text(
                    "COLLECTED · ${periodLabel.uppercase()}", color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(rs(total), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    if (payments.size == 1) "1 payment" else "${payments.size} payments",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                )
                if (byMode.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        byMode.forEach { (mode, amount) ->
                            Text(
                                "$mode ${rs(amount)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
        val duesCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), onClick = { go(Screen.Patients(filter = 1)) }, padding = 20.dp) {
                Text("Pending dues", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.GreenText)
                Spacer(Modifier.height(6.dp))
                Text(
                    rs(dueTotal), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (dueTotal > 0) HomeColors.DueRed else Brand.GreenDeep
                )
                Text(
                    if (dues.isEmpty()) "Nobody owes anything ✓" else "${dues.size} patients · tap to see who owes",
                    fontSize = 13.sp, color = Brand.Sub
                )
            }
        }
        val modeCard: @Composable () -> Unit = {
            FormCard("By payment mode") {
                if (byMode.isEmpty()) Text("No payments in this period", fontSize = 13.sp, color = Brand.Sub)
                byMode.forEach { (mode, amount) ->
                    ShareBar(mode, rs(amount), if (total > 0) (amount / total).toFloat() else 0f, HomeColors.Teal)
                }
            }
        }
        val doctorCard: @Composable () -> Unit = {
            FormCard("Treatments by doctor", "Fees of treatments started in this period") {
                if (byDoctor.isEmpty()) Text("No treatments in this period", fontSize = 13.sp, color = Brand.Sub)
                byDoctor.forEach { (name, amount) ->
                    ShareBar(name, rs(amount), if (doctorTotal > 0) (amount / doctorTotal).toFloat() else 0f, Brand.GreenDeep)
                }
            }
        }
        val listCard: @Composable () -> Unit = {
            FormCard("Payments", periodLabel) {
                if (payments.isEmpty()) Text("No payments in this period", fontSize = 13.sp, color = Brand.Sub)
                (if (showAll) payments else payments.take(15)).forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { if (p.patientId > 0) go(Screen.PatientDetail(p.patientId)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(p.name.ifBlank { "?" }, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name.ifBlank { "Deleted patient" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOf(fmtDateTime(p.date), p.mode).filter { it.isNotBlank() }.joinToString("  ·  "),
                                fontSize = 12.sp, color = Brand.Sub, maxLines = 1
                            )
                        }
                        Text(
                            if (p.amount >= 0) rs(p.amount) else "– " + rs(-p.amount),
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = if (p.amount >= 0) Brand.GreenDeep else Brand.Red
                        )
                    }
                }
                if (payments.size > 15) {
                    Text(
                        if (showAll) "Show less" else "Show all ${payments.size}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HomeColors.TealText,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAll = !showAll }.padding(6.dp)
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            SectionTopBar("Finance", "Collections, dues and doctors") {
                CircleIconButton(HomeIcons.Calendar, { picking = true }, tint = Brand.GreenDeep)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = side, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (chosen != null) {
                    ChoiceChip(rangeLabel(chosen.first, chosen.second), period == CUSTOM) {
                        if (period == CUSTOM) picking = true else period = CUSTOM
                    }
                }
                periodNames.forEachIndexed { i, label -> ChoiceChip(label, period == i) { period = i } }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = side, end = side, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            collectedCard()
                            modeCard()
                            duesCard()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            doctorCard()
                            listCard()
                        }
                    }
                } else {
                    collectedCard()
                    duesCard()
                    modeCard()
                    doctorCard()
                    listCard()
                }
            }
        }
    }

    if (picking) {
        RangePickDialog(chosen, onDismiss = { picking = false }) { from, to ->
            custom = from to to
            period = CUSTOM
            showAll = false
            picking = false
        }
    }
}

/** Tap one date for a single day, or two dates for a range. */
@Composable
private fun RangePickDialog(initial: Pair<Long, Long>?, onDismiss: () -> Unit, onPick: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.let { utcMs(it.first) },
        initialSelectedEndDateMillis = initial?.let { utcMs(it.second) }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis
                    if (start != null) {
                        val from = dayOfUtc(start)
                        val to = state.selectedEndDateMillis?.let { dayOfUtc(it) } ?: from
                        onPick(minOf(from, to), maxOf(from, to))
                    }
                }
            ) { Text("Show", color = Brand.GreenDeep, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Brand.Sub) } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ShareBar(label: String, value: String, fraction: Float, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Brand.Ink,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Brand.Soft)) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(50)).background(color)
            )
        }
    }
}
