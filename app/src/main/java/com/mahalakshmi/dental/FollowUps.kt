package com.mahalakshmi.dental

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────── Follow-ups (patient tracker) ─────────────
// Patients who need a nudge, one row each, most urgent first.
private const val FU_MISSED = 0
private const val FU_UNFINISHED = 1
private const val FU_NOT_STARTED = 2
private const val FU_DUES = 3
private const val FU_CHECKUP = 4
private val fuLabels = listOf("Missed", "Unfinished", "Not started", "Dues", "Check-up")
private const val DAY_MS = 24L * 60 * 60 * 1000

private class FollowUp(val patient: Patient, val kind: Int, val detail: String, val message: String)

private fun shortDay(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun ClinicDb.snoozedUntil(): Map<Long, Long> {
    val map = HashMap<Long, Long>()
    runCatching {
        readableDatabase.rawQuery("SELECT key, value FROM settings WHERE key LIKE 'followup:%'", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0).removePrefix("followup:").toLongOrNull() ?: continue
                map[id] = c.getString(1)?.toLongOrNull() ?: 0L
            }
        }
    }
    return map
}

/** Hides a patient from Follow-ups for 7 days after they were contacted. */
private fun ClinicDb.markContacted(patientId: Long) {
    val cv = ContentValues().apply {
        put("key", "followup:$patientId")
        put("value", (System.currentTimeMillis() + 7 * DAY_MS).toString())
    }
    writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Everyone who needs a follow-up: missed visit, unfinished or not started treatment, dues, check-up. */
private fun ClinicDb.followUps(): List<FollowUp> {
    val now = System.currentTimeMillis()
    val today = todayEpochDay()
    val clinic = clinicInfo().name
    val snoozed = snoozedUntil()
    val people = getPatients("")

    val booked = HashSet<Long>()
    readableDatabase.rawQuery(
        "SELECT DISTINCT patient_id FROM appointments WHERE status = 0 AND day >= ?", arrayOf(today.toString())
    ).use { c -> while (c.moveToNext()) booked.add(c.getLong(0)) }

    val missed = HashMap<Long, Long>()
    readableDatabase.rawQuery(
        "SELECT patient_id, MAX(day) FROM appointments WHERE status = 0 AND day < ? AND day >= ? GROUP BY patient_id",
        arrayOf(today.toString(), (today - 60).toString())
    ).use { c -> while (c.moveToNext()) missed[c.getLong(0)] = c.getLong(1) }

    val planned = HashMap<Long, Pair<String, Long>>()
    val ongoing = HashMap<Long, String>()
    readableDatabase.rawQuery(
        "SELECT patient_id, name, status, created_at FROM treatments WHERE status IN (0, 1) ORDER BY created_at", null
    ).use { c ->
        while (c.moveToNext()) {
            val pid = c.getLong(0)
            val name = c.getString(1).orEmpty()
            if (c.getInt(2) == STATUS_PLANNED) {
                if (pid !in planned) {
                    planned[pid] = name to c.getLong(3)
                }
            } else {
                ongoing[pid] = name
            }
        }
    }

    val list = mutableListOf<FollowUp>()
    people.forEach { p ->
        if ((snoozed[p.id] ?: 0L) > now) return@forEach
        val first = p.name.trim().substringBefore(" ")
        val hasBooking = p.id in booked
        val missedDay = missed[p.id]
        val plan = planned[p.id]
        val going = ongoing[p.id]
        val quiet = p.lastVisit < now - 3 * DAY_MS
        val item = when {
            missedDay != null && !hasBooking && epochDayOf(p.lastVisit) < missedDay ->
                FollowUp(
                    p, FU_MISSED, "Appointment on ${shortDay(missedDay)}",
                    "Hello $first, we missed you at $clinic on ${shortDay(missedDay)}. Would you like to book a new time? Please reply here."
                )
            going != null && !hasBooking && quiet ->
                FollowUp(
                    p, FU_UNFINISHED, "$going · next sitting not booked",
                    "Hello $first, this is $clinic. Your $going is not complete yet. Please book your next sitting so we can finish it. Reply here to choose a time."
                )
            plan != null && !hasBooking && plan.second < now - 7 * DAY_MS ->
                FollowUp(
                    p, FU_NOT_STARTED, "${plan.first} planned ${shortDay(epochDayOf(plan.second))}",
                    "Hello $first, this is $clinic. Your ${plan.first} treatment planned on ${shortDay(epochDayOf(plan.second))} is still pending. Shall we book a convenient time? Please reply here."
                )
            p.remaining > 0.01 && !hasBooking && quiet ->
                FollowUp(
                    p, FU_DUES, "${rs(p.remaining)} pending",
                    "Hello $first, this is a gentle reminder from $clinic. An amount of ${rs(p.remaining)} is pending for your dental treatment. Thank you!"
                )
            !hasBooking && p.phone.isNotBlank() && p.lastVisit in (now - 3 * 365 * DAY_MS) until (now - 180 * DAY_MS) ->
                FollowUp(p, FU_CHECKUP, "Last visit ${shortDay(epochDayOf(p.lastVisit))}", recallMessage(p, clinic))
            else -> null
        }
        if (item != null) list.add(item)
    }
    return list.sortedWith(compareBy<FollowUp>({ it.kind }, { it.patient.name.lowercase() }))
}

/** The Follow-ups screen. */
@Composable
fun FollowUpsScreen(db: ClinicDb, go: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val version = DataBus.version
    var refresh by remember { mutableIntStateOf(0) }
    val loaded by produceState<List<FollowUp>?>(null, version, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { db.followUps() }.getOrElse { emptyList() } }
    }
    var filter by remember { mutableIntStateOf(-1) }
    val all = loaded.orEmpty()
    val shown = if (filter < 0) all else all.filter { it.kind == filter }

    fun send(f: FollowUp) {
        db.markContacted(f.patient.id)
        openWhatsApp(context, f.patient.phone, f.message)
        refresh++
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        AppTopBar(
            "Follow-ups",
            when {
                loaded == null -> "Checking…"
                all.isEmpty() -> "All caught up"
                all.size == 1 -> "1 patient needs a nudge"
                else -> "${all.size} patients need a nudge"
            },
            onBack
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                if (all.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChoiceChip("All ${all.size}", filter < 0) { filter = -1 }
                        fuLabels.forEachIndexed { kind, label ->
                            val n = all.count { it.kind == kind }
                            if (n > 0) ChoiceChip("$label $n", filter == kind) { filter = kind }
                        }
                    }
                }
                if (loaded != null && shown.isEmpty()) {
                    EmptyState("All caught up", "No patient needs a follow-up right now.")
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(shown, key = { it.patient.id }) { f ->
                            FollowUpRow(
                                f,
                                onOpen = { go(Screen.PatientDetail(f.patient.id)) },
                                onWhatsApp = { send(f) },
                                onCall = { callNumber(context, f.patient.phone) },
                                onDone = {
                                    db.markContacted(f.patient.id)
                                    refresh++
                                    notify("${f.patient.name}: hidden for 7 days ✓")
                                }
                            )
                        }
                    }
                    val withPhone = shown.filter { it.patient.phone.isNotBlank() }
                    if (withPhone.isNotEmpty()) {
                        val next = withPhone.first()
                        GradientButton(
                            "Next reminder: ${next.patient.name.trim().substringBefore(" ")} (${withPhone.size} left)",
                            Modifier.fillMaxWidth().padding(bottom = 12.dp), AppIcons.Chat, height = 52.dp
                        ) { send(next) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUpRow(f: FollowUp, onOpen: () -> Unit, onWhatsApp: () -> Unit, onCall: () -> Unit, onDone: () -> Unit) {
    val (tagBg, tagFg) = when (f.kind) {
        FU_MISSED, FU_DUES -> Brand.RedSoft to Brand.Red
        FU_CHECKUP -> Brand.Soft to Brand.Slate
        else -> Brand.GoldSoft to Brand.GoldDeep
    }
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(f.patient.name, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.patient.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fuLabels[f.kind], fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tagFg,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(tagBg).padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(f.detail, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (f.patient.phone.isNotBlank()) {
                CircleIconButton(AppIcons.Chat, onWhatsApp, bg = Brand.GreenSoft, tint = Brand.GreenDeep, size = 38.dp)
                Spacer(Modifier.width(6.dp))
                CircleIconButton(Icons.Filled.Call, onCall, bg = Brand.Soft, tint = Brand.Ink, size = 38.dp)
                Spacer(Modifier.width(6.dp))
            }
            CircleIconButton(Icons.Filled.Check, onDone, bg = Brand.Soft, tint = Brand.Sub, size = 38.dp)
        }
    }
}

/** On Home: "12 patients need a follow-up". Hidden when nobody does. */
@Composable
fun FollowUpsEntry(db: ClinicDb, onOpen: () -> Unit) {
    val version = DataBus.version
    val count by produceState(0, version) {
        value = withContext(Dispatchers.IO) { runCatching { db.followUps().size }.getOrElse { 0 } }
    }
    if (count == 0) return
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.GoldSoft).clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(HomeIcons.People, null, tint = Brand.GoldDeep, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 patient needs a follow-up" else "$count patients need a follow-up",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.GoldDeep
                )
                Text("Tap to open the list", fontSize = 12.sp, color = Brand.Sub)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.GoldDeep)
        }
        Spacer(Modifier.height(14.dp))
    }
}
