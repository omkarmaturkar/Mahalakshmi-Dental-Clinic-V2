package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ───────────── One-tap shortcuts ─────────────
/** Navigation for windows that are not given `go`, like the treatment record. */
val LocalGo = staticCompositionLocalOf<(Screen) -> Unit> { {} }

/** Opens the quick actions sheet (the "+" in the bottom bar). Null where it is not set up. */
val LocalQuickActions = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A visit to open as soon as that patient's file appears (after an OPD No. search). */
object RecordJump {
    var patientId: Long = 0L
    var day: Long = -1L
}

/** The round "+" in the middle of the bottom bar. */
@Composable
fun QuickPlusButton(onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 4.dp).size(46.dp).clip(CircleShape).background(BrandGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Add, "Quick actions", tint = Color.White, modifier = Modifier.size(26.dp)) }
}

/** "MDC-001-191126" → patient number 1 and the day 19 Nov 2026; null if it is not an OPD No. */
private fun parseOpd(text: String): Pair<Long, Long>? {
    val m = Regex("^\\s*[A-Za-z]+-(\\d+)-(\\d{6})\\s*$").find(text) ?: return null
    val id = m.groupValues[1].toLongOrNull() ?: return null
    val day = runCatching {
        LocalDate.parse(m.groupValues[2], DateTimeFormatter.ofPattern("ddMMyy")).toEpochDay()
    }.getOrNull() ?: return null
    return id to day
}

/** Patients with the newest treatment, payment or registration first. */
private fun ClinicDb.recentPatientIds(limit: Int): List<Long> {
    val ids = mutableListOf<Long>()
    runCatching {
        readableDatabase.rawQuery(
            "SELECT patient_id, MAX(t) AS last FROM (" +
                "SELECT patient_id, created_at AS t FROM treatments UNION ALL " +
                "SELECT patient_id, done_at FROM treatments WHERE done_at > 0 UNION ALL " +
                "SELECT patient_id, date FROM payments UNION ALL " +
                "SELECT id, created_at FROM patients" +
                ") GROUP BY patient_id ORDER BY last DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
    }
    return ids
}

/** A sheet that sits at the bottom of the screen; tapping outside closes it. */
@Composable
private fun BottomSheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Make the sheet window shrink above the keyboard, so nothing hides behind it
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Brand.Surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Brand.Line)
                )
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun QuickRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(title, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickActionTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brand.GreenSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand.GreenDeep, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = Brand.Ink, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
    }
}

/** The "+" sheet: search on top, recent patients, then the five usual actions. */
@Composable
private fun QuickSheet(
    db: ClinicDb,
    onDismiss: () -> Unit,
    onOpenPatient: (Long) -> Unit,
    onOpd: (Long, Long) -> Unit,
    onAction: (String) -> Unit
) {
    val everyone = remember { db.getPatients("") }
    val recent = remember(everyone) { db.recentPatientIds(6).mapNotNull { id -> everyone.firstOrNull { it.id == id } } }
    var query by remember { mutableStateOf("") }
    val opd = parseOpd(query)
    val opdPatient = opd?.let { (id, _) -> everyone.firstOrNull { it.id == id } }
    val found = remember(query, everyone) {
        if (query.isBlank()) emptyList() else filterPatients(everyone, query, 0, emptySet()).take(5)
    }

    BottomSheetFrame(onDismiss) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Soft)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Brand.Faint)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Name, mobile, MDC ID or OPD No.", color = Brand.Faint, fontSize = 15.sp, maxLines = 1)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Poppins, fontSize = 15.sp, color = Brand.Ink),
                    cursorBrush = SolidColor(Brand.GreenDeep), modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (opd != null && opdPatient != null) {
            QuickRow(opdPatient.name, "Open visit " + opdId(opdPatient.id, opd.second)) { onOpd(opdPatient.id, opd.second) }
        }
        found.forEach { p ->
            QuickRow(p.name, listOf(patientCode(p.id), p.phone).filter { it.isNotBlank() }.joinToString("  ·  ")) {
                onOpenPatient(p.id)
            }
        }
        if (query.isNotBlank() && found.isEmpty() && opdPatient == null) {
            Text("No patient found", fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(top = 10.dp))
        }
        if (query.isBlank() && recent.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Recent", fontSize = 12.sp, color = Brand.Sub)
                recent.forEach { p ->
                    Text(
                        p.name, fontSize = 13.sp, color = Brand.Ink, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Soft)
                            .clickable { onOpenPatient(p.id) }.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        if (query.isBlank()) {
        Spacer(Modifier.height(16.dp))
        val actions = listOf(
            Triple("new", "New patient", HomeIcons.PersonAdd),
            Triple("treatment", "Treatment record", HomeIcons.ClipboardAdd),
            Triple("payment", "Receive payment", HomeIcons.Wallet),
            Triple("book", "Book appointment", HomeIcons.CalendarAdd),
            Triple("rx", "Prescription", DocIcons.Pill)
        )
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                row.forEach { (key, label, icon) -> QuickActionTile(label, icon, Modifier.weight(1f)) { onAction(key) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        }
    }
}

/** The "+" sheet and the windows it opens. Shown over any screen. */
@Composable
fun QuickActionsHost(db: ClinicDb, go: (Screen) -> Unit, onClose: () -> Unit) {
    val notify = LocalNotify.current
    var sheet by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf<String?>(null) }
    var payFor by remember { mutableStateOf<Patient?>(null) }
    var bookFor by remember { mutableStateOf<Patient?>(null) }
    var receiptFor by remember { mutableStateOf<Pair<Patient, Long>?>(null) }

    if (sheet) {
        QuickSheet(
            db,
            onDismiss = onClose,
            onOpenPatient = { id ->
                onClose()
                go(Screen.PatientDetail(id))
            },
            onOpd = { id, day ->
                RecordJump.patientId = id
                RecordJump.day = day
                onClose()
                go(Screen.PatientDetail(id))
            },
            onAction = { key ->
                if (key == "new") {
                    onClose()
                    go(Screen.PatientForm())
                } else {
                    sheet = false
                    picker = key
                }
            }
        )
    }
    picker?.let { key ->
        val title = when (key) {
            "treatment" -> "New treatment record"
            "payment" -> "Receive payment"
            "book" -> "Book appointment"
            else -> "Prescription"
        }
        PatientPickerDialog(db, title, onDismiss = onClose) { p ->
            picker = null
            when (key) {
                "treatment" -> {
                    onClose()
                    go(Screen.VisitForm(p.id))
                }
                "rx" -> {
                    onClose()
                    go(Screen.RxForm(p.id, 0))
                }
                "payment" -> {
                    payFor = p
                }
                else -> {
                    bookFor = p
                }
            }
        }
    }
    payFor?.let { p ->
        ReceivePaymentDialog(balance = p.remaining, onDismiss = onClose) { amount, mode ->
            val id = db.addPayment(p.id, amount, mode)
            DataBus.changed()
            notify("Payment of ${rs(amount)} received ✓")
            payFor = null
            receiptFor = p to id
        }
    }
    bookFor?.let { p ->
        val existing = remember(p.id) { db.nextBooking(p.id) }
        BookingDialog(initial = existing, defaultReason = "Check-up", onDismiss = onClose, onCancelBooking = null) { day, time, why ->
            db.saveBooking(p.id, day, time, why)
            DataBus.changed()
            notify("Appointment booked for ${p.name} ✓")
            onClose()
        }
    }
    receiptFor?.let { (p, id) ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Send the receipt to ${p.name}",
            phone = p.phone,
            message = "Hello ${p.name.trim().substringBefore(" ")}, here is your payment receipt from ${db.clinicInfo().name}. Thank you!",
            build = { ctx -> paymentReceiptPdf(ctx, db, id) },
            onDismiss = onClose
        )
    }
}

// ───────────── Finish visit ─────────────
/** After a treatment is marked Done in today's record: the usual next steps, one tap each. */
@Composable
fun FinishVisitSheet(
    patientName: String,
    due: Double,
    onDismiss: () -> Unit,
    onPay: () -> Unit,
    onRx: () -> Unit,
    onBook: () -> Unit,
    onBill: () -> Unit
) {
    BottomSheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Treatment done. What's next?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Brand.Ink)
        }
        Text(
            patientName + (if (due > 0.01) "  ·  ${rs(due)} due" else ""),
            fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(start = 40.dp, top = 2.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinishTile("Receive payment", HomeIcons.Wallet, Modifier.weight(1f), onPay)
            FinishTile("Prescription", DocIcons.Pill, Modifier.weight(1f), onRx)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinishTile("Book next visit", HomeIcons.CalendarAdd, Modifier.weight(1f), onBook)
            FinishTile("Bill on WhatsApp", AppIcons.Chat, Modifier.weight(1f), onBill)
        }
        Spacer(Modifier.height(12.dp))
        SoftButton("Done", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
    }
}

@Composable
private fun FinishTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Brand.GreenSoft).clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Brand.GreenDeep, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.GreenText,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ───────────── Repeat last prescription ─────────────
/** In a new prescription: copies the medicines and advice of the patient's last one. */
@Composable
fun RepeatLastButton(dateText: String, medicineCount: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.GreenSoft)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Refresh, null, tint = Brand.GreenDeep, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Repeat last prescription", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Brand.GreenText)
            val meds = if (medicineCount == 1) "1 medicine" else "$medicineCount medicines"
            Text("$dateText  ·  $meds and advice", fontSize = 12.sp, color = Brand.Sub)
        }
    }
}
