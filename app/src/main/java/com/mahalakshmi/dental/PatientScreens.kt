@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val medicalOptions = listOf(
    "Diabetes", "Blood Pressure", "Heart Problem", "Allergy",
    "Asthma", "Thyroid", "Pregnant", "Blood Thinners"
)

private val Purple = Color(0xFF7B4FD6)
private val PurpleSoft get() = if (AppTheme.dark) Color(0xFF2B2442) else Color(0xFFF1EBFF)
private val Blue = Color(0xFF2F6FED)
private val BlueSoft get() = if (AppTheme.dark) Color(0xFF1B2A45) else Color(0xFFE8F0FF)

fun statusColor(status: Int): Color = when (status) {
    STATUS_PLANNED -> Brand.Red
    STATUS_ONGOING -> Brand.Gold
    else -> Brand.Green
}

fun statusSoft(status: Int): Color = when (status) {
    STATUS_PLANNED -> Brand.RedSoft
    STATUS_ONGOING -> Brand.GoldSoft
    else -> Brand.GreenSoft
}

fun statusText(status: Int): Color = when (status) {
    STATUS_PLANNED -> Brand.Red
    STATUS_ONGOING -> Brand.GoldDeep
    else -> Brand.GreenText
}

// ───────────── The patient file ─────────────
@Composable
fun PatientFileScreen(db: ClinicDb, patientId: Long, startAdd: Boolean, go: (Screen) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val version = DataBus.version
    val patient = remember(version) { db.getPatient(patientId) }
    val treatments = remember(version) { db.treatmentsOf(patientId) }
    val payments = remember(version) { db.paymentsOf(patientId) }
    val notes = remember(version) { db.notesOf(patientId) }
    val photos = remember(version) { db.photosOf(patientId) }
    val prescriptions = remember(version) { db.prescriptionsOf(patientId) }
    val booking = remember(version) { db.nextBooking(patientId) }
    val doctors = remember(version) { db.doctorNames() }
    val clinicName = remember(version) { db.clinicInfo().name }
    val history = remember(treatments, payments, notes, prescriptions, photos) {
        buildHistory(treatments, payments, notes, prescriptions, photos)
    }
    val statusByTooth = remember(treatments) {
        val map = HashMap<String, Int>()
        treatments.forEach { t -> t.teethList.forEach { tooth -> map[tooth] = minOf(map[tooth] ?: STATUS_DONE, t.status) } }
        map
    }

    var addTreatment by remember { mutableStateOf(startAdd) }
    var treatmentEdit by remember { mutableStateOf<Treatment?>(null) }
    var presetTooth by remember { mutableStateOf<String?>(null) }
    var receive by remember { mutableStateOf(false) }
    var receiptFor by remember { mutableStateOf<Long?>(null) }
    var pdf by remember { mutableStateOf<String?>(null) }
    var rxShare by remember { mutableStateOf<Prescription?>(null) }
    var bookingOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var photoOptions by remember { mutableStateOf<CasePhoto?>(null) }
    var compare by remember { mutableStateOf(false) }
    var editPayment by remember { mutableStateOf<Payment?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var photoKind by remember { mutableStateOf("Photo") }
    var savingPhoto by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }

    fun addPhotos(sources: List<() -> InputStream?>) {
        scope.launch {
            savingPhoto = true
            var saved = 0
            for (open in sources) {
                val path = withContext(Dispatchers.IO) { importPhoto(context, open) }
                if (path != null) {
                    db.addPhoto(patientId, path, photoKind)
                    saved++
                }
            }
            savingPhoto = false
            cameraTempFile(context).delete()
            DataBus.changed()
            if (saved > 0) notify(if (saved == 1) "Photo saved ✓" else "$saved photos saved ✓")
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = cameraTempFile(context)
        if (ok && temp.length() > 0) addPhotos(listOf({ temp.inputStream() })) else temp.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) addPhotos(uris.map { uri -> { context.contentResolver.openInputStream(uri) } })
    }

    if (patient == null) return
    val account = accountOf(treatments, payments)
    val first = patient.name.trim().substringBefore(" ")

    fun openCamera() {
        try {
            val temp = cameraTempFile(context)
            temp.delete()
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temp))
        } catch (e: Exception) {
            notify("Could not open the camera")
        }
    }

    fun cycleStatus(t: Treatment) {
        val next = when (t.status) {
            STATUS_PLANNED -> STATUS_ONGOING
            STATUS_ONGOING -> STATUS_DONE
            else -> STATUS_ONGOING
        }
        db.saveTreatment(t.copy(status = next))
        DataBus.changed()
        notify("${t.name}: ${statusLabel(next)} ✓")
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 760.dp

        val header: @Composable () -> Unit = {
            PatientHeader(
                patient, account, booking,
                onCall = { callNumber(context, patient.phone) },
                onChat = { openWhatsApp(context, patient.phone, reminderMessage(patient, booking?.day)) },
                onPay = { receive = true },
                onBook = { bookingOpen = true }
            )
        }
        val actions: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row(Modifier.fillMaxWidth()) {
                    ActionTile("Treatment", Icons.Filled.Add, Brand.GreenDeep, Brand.GreenSoft, Modifier.weight(1f)) {
                        presetTooth = null
                        addTreatment = true
                    }
                    ActionTile("Payment", DocIcons.Receipt, Brand.GoldDeep, Brand.GoldSoft, Modifier.weight(1f)) { receive = true }
                    ActionTile("Rx", DocIcons.Pill, Purple, PurpleSoft, Modifier.weight(1f)) { go(Screen.RxForm(patientId, 0)) }
                    ActionTile("Bill", DocIcons.Pdf, Blue, BlueSoft, Modifier.weight(1f)) { pdf = "invoice" }
                }
            }
        }
        val treatmentsCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Teeth & treatments", Modifier.weight(1f))
                    SoftButton("Add", Icons.Filled.Add, height = 40.dp) {
                        presetTooth = null
                        addTreatment = true
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatusChart(statusByTooth) { tooth ->
                    val active = treatments.firstOrNull { tooth in it.teethList && it.status != STATUS_DONE }
                    if (active != null) {
                        treatmentEdit = active
                    } else {
                        presetTooth = tooth
                        addTreatment = true
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                    LegendDot(STATUS_PLANNED)
                    Spacer(Modifier.width(16.dp))
                    LegendDot(STATUS_ONGOING)
                    Spacer(Modifier.width(16.dp))
                    LegendDot(STATUS_DONE)
                }
                if (treatments.isEmpty()) {
                    Hint("No treatments yet. Tap a tooth above, or tap \"Add\".")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        treatments.forEach { t ->
                            key(t.id) {
                                TreatmentRow(t, doctors[t.doctorId].orEmpty(), onOpen = { treatmentEdit = t }) { cycleStatus(t) }
                            }
                        }
                    }
                    Text(
                        "Tap a treatment to change its fee or details. Tap its status to move it to the next step.",
                        fontSize = 11.sp, color = Brand.Faint, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (account.planned > 0) {
                    Spacer(Modifier.height(12.dp))
                    SoftButton("Send estimate (${rs(account.planned)})", DocIcons.Pdf, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                        pdf = "estimate"
                    }
                }
            }
        }
        val moneyCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                SectionHeader("Payments")
                Spacer(Modifier.height(14.dp))
                Row {
                    MoneyBox("Billed", rs(account.billed), Brand.Ink, Modifier.weight(1f))
                    MoneyBox("Paid", rs(account.paid), Brand.GreenDeep, Modifier.weight(1f))
                    MoneyBox(
                        if (account.balance >= 0) "Balance" else "Advance", rs(abs(account.balance)),
                        if (account.balance > 0) Brand.Red else Brand.GreenDeep, Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                val ratio = if (account.billed > 0) (account.paid / account.billed).toFloat().coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Brand.Soft)) {
                    Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(RoundedCornerShape(50)).background(BrandGradient))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton("Receive", Modifier.weight(1f), DocIcons.Receipt, height = 50.dp) { receive = true }
                    SoftButton("Full bill", DocIcons.Pdf, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Ink, height = 50.dp) {
                        pdf = "invoice"
                    }
                }
                if (account.planned > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Planned, not billed yet: ${rs(account.planned)}", fontSize = 12.sp, color = Brand.Sub)
                }
                if (payments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    payments.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Brand.Line)
                        PaymentRow(p, onEdit = { editPayment = p }, onReceipt = { receiptFor = p.id })
                    }
                    Text(
                        "Tap a payment to correct the amount, mode or date, or to delete it.",
                        fontSize = 11.sp, color = Brand.Faint, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        val rxCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Prescriptions", Modifier.weight(1f))
                    SoftButton("New", Icons.Filled.Add, height = 40.dp) { go(Screen.RxForm(patientId, 0)) }
                }
                Spacer(Modifier.height(6.dp))
                if (prescriptions.isEmpty()) {
                    Hint("No prescriptions yet.")
                } else {
                    prescriptions.forEachIndexed { i, rx ->
                        if (i > 0) HorizontalDivider(color = Brand.Line)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { go(Screen.RxForm(patientId, rx.id)) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(DocIcons.Pill, Purple, PurpleSoft, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(fmtDate(rx.date), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
                                val meds = if (rx.medicines.size == 1) "1 medicine" else "${rx.medicines.size} medicines"
                                Text(
                                    listOf(rx.diagnosis, meds).filter { it.isNotBlank() }.joinToString("  ·  "),
                                    fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            CircleIconButton(AppIcons.Chat, { rxShare = rx }, bg = Brand.Soft, tint = Brand.GreenDeep, size = 38.dp)
                        }
                    }
                }
            }
        }
        val photosCard: @Composable () -> Unit = {
            val canCompare = photos.any { it.kind == "Before" } && photos.any { it.kind == "After" }
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Photos & X-rays", Modifier.weight(1f))
                    if (canCompare) SoftButton("Before / After", null, height = 40.dp) { compare = true }
                }
                Spacer(Modifier.height(10.dp))
                Text("Save new photos as", fontSize = 12.sp, color = Brand.Sub)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photoKinds.forEach { k -> ChoiceChip(k, photoKind == k) { photoKind = k } }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Camera", AppIcons.Camera, Modifier.weight(1f)) { openCamera() }
                    SoftButton("Gallery", AppIcons.Gallery, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Ink) {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                if (photos.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Hint("No photos yet.")
                } else {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEachIndexed { i, ph ->
                            key(ph.id) {
                                Box(Modifier.size(96.dp)) {
                                    PhotoImage(
                                        ph.path,
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                                            .clickable { viewer = photos.map { it.path } to i }
                                    )
                                    Text(
                                        ph.kind, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                            .clip(RoundedCornerShape(50)).background(Color(0x99000000))
                                            .clickable { photoOptions = ph }.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Tap the label on a photo to change its type or delete it.",
                        fontSize = 11.sp, color = Brand.Faint, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        val historyCard: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("History", Modifier.weight(1f))
                    SoftButton("Add note", Icons.Filled.Edit, height = 40.dp) { noteOpen = true }
                }
                Spacer(Modifier.height(6.dp))
                if (history.isEmpty()) {
                    Hint("Everything done for this patient will appear here automatically.")
                } else {
                    (if (showAllHistory) history else history.take(25)).forEach { HistoryRow(it) }
                    if (history.size > 25) {
                        Text(
                            if (showAllHistory) "Show less" else "Show all ${history.size}",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenDeep,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAllHistory = !showAllHistory }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            AppTopBar(patient.name, "Patient file", onBack) {
                CircleIconButton(Icons.Filled.Edit, { go(Screen.PatientForm(patientId)) }, tint = Brand.GreenDeep)
            }
            if (wide) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(top = 6.dp, bottom = 28.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        header()
                        actions()
                        TreatmentProgress(db, patientId)
                        treatmentsCard()
                        PerioCard(db, patientId)
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(top = 6.dp, bottom = 28.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        moneyCard()
                        rxCard()
                        photosCard()
                        historyCard()
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    header()
                    actions()
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Treatments", "Payments", "Prescriptions", "Photos", "History").forEachIndexed { i, label ->
                            ChoiceChip(label, tab == i) { tab = i }
                        }
                    }
                    when (tab) {
                        0 -> { TreatmentProgress(db, patientId); treatmentsCard(); PerioCard(db, patientId) }
                        1 -> moneyCard()
                        2 -> rxCard()
                        3 -> photosCard()
                        else -> historyCard()
                    }
                }
            }
        }
    }

    // ───── Pop-ups ─────
    if (addTreatment || treatmentEdit != null) {
        TreatmentDialog(
            db = db,
            patientId = patientId,
            initial = treatmentEdit,
            presetTooth = presetTooth,
            onDismiss = {
                addTreatment = false
                treatmentEdit = null
                presetTooth = null
            },
            onSaved = { msg ->
                addTreatment = false
                treatmentEdit = null
                presetTooth = null
                DataBus.changed()
                notify(msg)
            }
        )
    }
    if (receive) {
        ReceivePaymentDialog(balance = account.balance, onDismiss = { receive = false }) { amount, mode ->
            val id = db.addPayment(patientId, amount, mode)
            receive = false
            DataBus.changed()
            notify("Payment of ${rs(amount)} received ✓")
            receiptFor = id
        }
    }
    editPayment?.let { p ->
        EditPaymentDialog(
            p,
            onDismiss = { editPayment = null },
            onDelete = {
                db.deletePayment(p.id)
                editPayment = null
                DataBus.changed()
                notify("Payment deleted")
            }
        ) { amount, mode, date ->
            db.updatePayment(p.id, amount, mode, date)
            editPayment = null
            DataBus.changed()
            notify("Payment updated to ${rs(abs(amount))} ✓")
        }
    }
    receiptFor?.let { id ->
        PdfActionsDialog(
            title = "Payment receipt",
            subtitle = "Send the receipt to ${patient.name}",
            phone = patient.phone,
            message = "Hello $first, here is your payment receipt from $clinicName. Thank you!",
            build = { ctx -> paymentReceiptPdf(ctx, db, id) },
            onDismiss = { receiptFor = null }
        )
    }
    pdf?.let { kind ->
        val estimate = kind == "estimate"
        PdfActionsDialog(
            title = if (estimate) "Treatment estimate" else "Full bill",
            subtitle = if (estimate) "Planned treatments with their cost" else "All treatments, payments and the balance",
            phone = patient.phone,
            message = if (estimate) "Hello $first, here is the estimate for your dental treatment from $clinicName."
            else "Hello $first, here is your bill from $clinicName. Thank you!",
            build = { ctx -> if (estimate) estimatePdf(ctx, db, patientId) else invoicePdf(ctx, db, patientId) },
            onDismiss = { pdf = null }
        )
    }
    rxShare?.let { rx ->
        PdfActionsDialog(
            title = "Prescription",
            subtitle = "From ${fmtDate(rx.date)}",
            phone = patient.phone,
            message = "Hello $first, here is your prescription from $clinicName. Get well soon!",
            build = { ctx -> prescriptionPdf(ctx, db, rx.id) },
            onDismiss = { rxShare = null }
        )
    }
    if (bookingOpen) {
        val reason = treatments.firstOrNull { it.status == STATUS_ONGOING }?.name
            ?: treatments.firstOrNull { it.status == STATUS_PLANNED }?.name ?: "Check-up"
        BookingDialog(
            initial = booking,
            defaultReason = reason,
            onDismiss = { bookingOpen = false },
            onCancelBooking = if (booking != null) ({
                db.cancelBookings(patientId)
                bookingOpen = false
                DataBus.changed()
                notify("Appointment cancelled")
            }) else null
        ) { day, time, why ->
            db.saveBooking(patientId, day, time, why)
            bookingOpen = false
            DataBus.changed()
            notify("Next visit booked ✓")
        }
    }
    if (noteOpen) {
        NoteDialog(onDismiss = { noteOpen = false }) { text ->
            db.addNote(patientId, text)
            noteOpen = false
            DataBus.changed()
            notify("Note saved ✓")
        }
    }
    viewer?.let { (list, index) -> PhotoViewer(list, index) { viewer = null } }
    photoOptions?.let { ph ->
        PhotoOptionsDialog(
            ph,
            onDismiss = { photoOptions = null },
            onKind = { k ->
                db.setPhotoKind(ph.id, k)
                photoOptions = null
                DataBus.changed()
            }
        ) {
            db.deletePhoto(ph)
            photoOptions = null
            DataBus.changed()
            notify("Photo deleted")
        }
    }
    if (compare) {
        val before = photos.firstOrNull { it.kind == "Before" }
        val after = photos.firstOrNull { it.kind == "After" }
        if (before != null && after != null) {
            BeforeAfterDialog(before, after, clinicName) { compare = false }
        }
    }
    if (savingPhoto) BusyDialog("Saving photo…")
}

// ───────────── Pieces of the patient file ─────────────
@Composable
private fun PatientHeader(
    p: Patient,
    account: Account,
    booking: Booking?,
    onCall: () -> Unit,
    onChat: () -> Unit,
    onPay: () -> Unit,
    onBook: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().background(CharcoalGradient).drawBehind {
                val c = Offset(size.width * 0.95f, 0f)
                val r = size.width * 0.55f
                drawCircle(
                    Brush.radialGradient(listOf(Brand.Green.copy(alpha = 0.5f), Color.Transparent), center = c, radius = r),
                    radius = r, center = c
                )
            }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(66.dp).clip(CircleShape).background(Brand.Surface), contentAlignment = Alignment.Center) {
                Avatar(p.name, 58.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val since = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(p.createdAt))
                val info = listOf(p.gender, if (p.age.isNotBlank()) "${p.age} yrs" else "", "Since $since")
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                Text(info, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                if (p.phone.isNotBlank()) Text(p.phone, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (p.phone.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Filled.Call, onCall, bg = Brand.Surface.copy(alpha = 0.16f), tint = Color.White, size = 42.dp)
                Spacer(Modifier.width(8.dp))
                CircleIconButton(AppIcons.Chat, onChat, bg = Brand.Surface.copy(alpha = 0.16f), tint = Color.White, size = 42.dp)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.alerts.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.RedSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(p.alerts.joinToString("  ·  "), color = Brand.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val due = account.balance
                val settled = abs(due) < 0.01
                InfoTile(
                    label = when {
                        settled -> "Account"
                        due > 0 -> "Balance due"
                        else -> "Advance"
                    },
                    value = if (settled) "All paid ✓" else rs(abs(due)),
                    color = if (due > 0.01) Brand.Red else Brand.GreenDeep,
                    bg = if (due > 0.01) Brand.RedSoft else Brand.GreenSoft,
                    hint = if (due > 0.01) "Tap to receive" else "Tap to add a payment",
                    modifier = Modifier.weight(1f),
                    onClick = onPay
                )
                InfoTile(
                    label = "Next visit",
                    value = booking?.let { b -> fmtDay(b.day) + if (b.time.isNotBlank()) " · ${b.time}" else "" } ?: "Not booked",
                    color = if (booking != null) Brand.GoldDeep else Brand.Sub,
                    bg = if (booking != null) Brand.GoldSoft else Brand.Soft,
                    hint = if (booking != null) "Tap to change" else "Tap to book",
                    modifier = Modifier.weight(1f),
                    onClick = onBook
                )
            }
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, color: Color, bg: Color, hint: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = Brand.Sub)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(hint, fontSize = 11.sp, color = Brand.Faint)
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, tint: Color, soft: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Brand.Ink, maxLines = 1)
    }
}

@Composable
private fun TreatmentRow(t: Treatment, doctor: String, onOpen: () -> Unit, onStatus: () -> Unit) {
    val teeth = t.teethList
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Soft).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(statusSoft(t.status)), contentAlignment = Alignment.Center) {
            if (teeth.isEmpty()) {
                Icon(AppIcons.Tooth, null, tint = statusText(t.status), modifier = Modifier.size(22.dp))
            } else {
                Text(
                    if (teeth.size == 1) teeth[0] else "${teeth[0]}+${teeth.size - 1}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = statusText(t.status)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(
                if (teeth.size > 1) "Teeth " + teeth.joinToString(", ") else "",
                doctor,
                fmtDate(t.createdAt)
            ).filter { it.isNotBlank() }.joinToString("  ·  ")
            Text(sub, fontSize = 12.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.notes.isNotBlank()) Text(t.notes, fontSize = 12.sp, color = Brand.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (t.fee > 0) rs(t.fee) else "–", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onStatus)) {
                Pill(statusLabel(t.status), Brand.Surface, statusText(t.status))
            }
        }
    }
}

@Composable
private fun LegendDot(status: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(status)))
        Spacer(Modifier.width(5.dp))
        Text(statusLabel(status), fontSize = 12.sp, color = Brand.Sub)
    }
}

@Composable
private fun MoneyBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Brand.Sub)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PaymentRow(p: Payment, onEdit: () -> Unit, onReceipt: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (p.amount >= 0) rs(p.amount) else "– " + rs(-p.amount),
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (p.amount >= 0) Brand.GreenDeep else Brand.Red
            )
            Text(
                listOf(fmtDateTime(p.date), p.mode).filter { it.isNotBlank() }.joinToString("  ·  "),
                fontSize = 12.sp, color = Brand.Sub
            )
        }
        CircleIconButton(DocIcons.Receipt, onReceipt, bg = Brand.Soft, tint = Brand.GreenDeep, size = 38.dp)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(Icons.Filled.Edit, onEdit, bg = Brand.Soft, tint = Brand.Sub, size = 38.dp)
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val (icon, tint, bg) = when (item.kind) {
        H_TREATMENT -> Triple(AppIcons.Tooth, Brand.GoldDeep, Brand.GoldSoft)
        H_DONE -> Triple(AppIcons.Tooth, Brand.GreenDeep, Brand.GreenSoft)
        H_PAYMENT -> Triple(DocIcons.Receipt, Brand.GreenDeep, Brand.GreenSoft)
        H_RX -> Triple(DocIcons.Pill, Purple, PurpleSoft)
        H_PHOTO -> Triple(AppIcons.Camera, Blue, BlueSoft)
        else -> Triple(Icons.Filled.Edit, Brand.Sub, Brand.Soft)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Ink)
            if (item.detail.isNotBlank()) Text(item.detail, fontSize = 12.sp, color = Brand.Sub)
        }
        Spacer(Modifier.width(8.dp))
        Text(fmtDate(item.date), fontSize = 11.sp, color = Brand.Faint)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(vertical = 8.dp))
}

// ───────────── Call & WhatsApp ─────────────
fun callNumber(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open the dialer", Toast.LENGTH_SHORT).show()
    }
}

fun openWhatsApp(context: Context, phone: String, message: String) {
    val digits = phone.filter { it.isDigit() }
    val number = when {
        digits.length == 10 -> "91$digits"
        digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
        else -> digits
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(message))))
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp is not available", Toast.LENGTH_SHORT).show()
    }
}

fun reminderMessage(p: Patient, nextVisit: Long?): String {
    val first = p.name.trim().substringBefore(" ")
    return when {
        p.remaining > 0 ->
            "Hello $first, this is a gentle reminder from Mahalakshmi Dental Clinic. " +
                "An amount of ${rs(p.remaining)} is pending for your dental treatment. Thank you! 🙏"
        nextVisit != null ->
            "Hello $first, this is a reminder from Mahalakshmi Dental Clinic for your next dental visit on " +
                "${fmtDayLong(nextVisit)}. See you soon! 😊"
        else -> "Hello $first, greetings from Mahalakshmi Dental Clinic! 😊"
    }
}
