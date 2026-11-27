package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "1 patient", "25 patients" */
private fun patientsText(n: Int): String = if (n == 1) "1 patient" else "$n patients"

/** The message shown before a backup replaces everything on this phone. */
private fun replaceText(here: Int, what: String): String =
    "This phone has ${patientsText(here)}. Everything will be replaced with $what." +
        (if (here > 0) "\n\nA copy of this phone's data is kept, so you can undo this." else "")

/** A request to add patients: where they come from, what will happen, and what runs when confirmed. */
private class AddAsk(val from: String, val plan: PatientAddPlan, val action: () -> Unit)

@Composable
fun BackupScreen(db: ClinicDb, onBack: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var choices by remember { mutableStateOf<List<DriveBackupItem>?>(null) }
    var picked by remember { mutableStateOf<DriveBackupItem?>(null) }
    var replaceItem by remember { mutableStateOf<DriveBackupItem?>(null) }
    var addAsk by remember { mutableStateOf<AddAsk?>(null) }
    var askUndo by remember { mutableStateOf(false) }
    val deviceLabel = remember { DriveBackup.deviceName(context) }

    var folder by remember { mutableStateOf(Backup.autoFolderName(context)) }
    var localLast by remember { mutableLongStateOf(Backup.lastBackup(context)) }
    var driveOn by remember { mutableStateOf(DriveBackup.isConnected(context)) }
    var account by remember { mutableStateOf(DriveBackup.account(context)) }
    var driveLast by remember { mutableLongStateOf(DriveBackup.lastBackup(context)) }
    var autoDaily by remember { mutableStateOf(DriveBackup.autoEnabled(context)) }
    var mobileData by remember { mutableStateOf(DriveBackup.useMobileData(context)) }
    var undoReady by remember { mutableStateOf(RestoreSafety.available(context)) }

    fun refresh() {
        folder = Backup.autoFolderName(context)
        localLast = Backup.lastBackup(context)
        driveOn = DriveBackup.isConnected(context)
        account = DriveBackup.account(context)
        driveLast = DriveBackup.lastBackup(context)
        undoReady = RestoreSafety.available(context)
        DataBus.changed()
    }

    // Runs slow work in the background and shows a progress window
    fun <T> work(label: String, task: ((String) -> Unit) -> T, done: (Result<T>) -> Unit) {
        busy = label
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { task { msg -> scope.launch { busy = msg } } }
            }
            busy = null
            refresh()
            done(result)
        }
    }

    // After a restore or an undo, the app restarts so every screen shows the right data
    fun afterRestore(message: String) {
        notify(message)
        onRestored()
        restartAfterRestore(context)
    }

    // ───── Google sign-in ─────
    var pendingAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pendingAction
        pendingAction = null
        val token = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken
        } catch (e: Exception) {
            null
        }
        if (token != null && action != null) action(token) else notify("Google sign-in was cancelled")
    }

    fun withDriveToken(action: (String) -> Unit) {
        Identity.getAuthorizationClient(context).authorize(DriveBackup.request())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    pendingAction = action
                    authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val token = result.accessToken
                    if (token != null) action(token) else notify("Could not connect to Google Drive")
                }
            }
            .addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                notify(
                    if (code == 10) "Google Drive setup is not finished yet (check the SHA-1 step)"
                    else "Could not connect to Google Drive – check the internet"
                )
            }
    }

    fun driveBackupNow() {
        withDriveToken { token ->
            work("Backing up to Google Drive…", { progress -> DriveBackup.backup(context, db, token, progress) }) { r ->
                notify(r.getOrNull() ?: friendlyError(r.exceptionOrNull()))
            }
        }
    }

    fun driveRestore(item: DriveBackupItem) {
        withDriveToken { token ->
            work("Restoring from Google Drive…", { progress -> DriveBackup.restoreItem(context, db, token, item, progress) }) { r ->
                when {
                    r.getOrNull() == true -> {
                        afterRestore("Data restored ✓  The app will restart now")
                    }
                    r.isFailure -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    else -> {
                        notify("This backup could not be opened")
                    }
                }
            }
        }
    }

    // Adds the new patients from another phone's Drive backup, after showing what will happen
    fun driveAdd(item: DriveBackupItem) {
        withDriveToken { token ->
            work("Checking the backup…", { progress -> DriveBackup.planAdd(context, db, token, item, progress) }) { r ->
                val plan = r.getOrNull()
                when {
                    plan == null -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    plan.newPatients == 0 -> {
                        notify("All patients in this backup are already on this phone")
                    }
                    else -> {
                        addAsk = AddAsk(item.device, plan) {
                            withDriveToken { token2 ->
                                work("Adding patients…", { progress -> DriveBackup.finishAdd(context, db, token2, item, plan, progress) }) { r2 ->
                                    val added = r2.getOrNull()
                                    notify(if (added != null) "${patientsText(added)} added ✓" else friendlyError(r2.exceptionOrNull()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun undoNow() {
        work("Bringing back the earlier data…", { _ -> RestoreSafety.undo(context, db) }) { r ->
            if (r.getOrNull() == true) afterRestore("Done ✓  The app will restart now") else notify("There is nothing to undo")
        }
    }

    // Shows every backup on Google Drive, with the phone or tablet it came from
    fun chooseDriveBackup() {
        withDriveToken { token ->
            work("Looking for backups…", { _ -> DriveBackup.listBackups(context, token) }) { r ->
                val list = r.getOrNull().orEmpty()
                when {
                    r.isFailure -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    list.isEmpty() -> {
                        notify("No backup found on this Google account")
                    }
                    else -> {
                        choices = list
                    }
                }
            }
        }
    }

    fun connectDrive() {
        withDriveToken { token ->
            work("Connecting to Google Drive…", { _ ->
                DriveBackup.markConnected(context, token)
                if (db.patientCount() == 0) DriveBackup.listBackups(context, token) else emptyList()
            }) { r ->
                val found = r.getOrNull().orEmpty()
                when {
                    r.isFailure -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    found.isNotEmpty() -> {
                        choices = found
                    }
                    db.patientCount() > 0 -> {
                        notify("Google Drive connected ✓")
                        driveBackupNow()
                    }
                    else -> {
                        notify("Google Drive connected ✓")
                    }
                }
            }
        }
    }

    // ───── Backup on this tablet ─────
    fun localBackupNow() {
        work("Backing up…", { _ -> Backup.autoBackup(context, db, force = true) }) { r ->
            notify(
                when {
                    isTestApp(context) -> "Backups are off in the TEST app"
                    r.getOrNull() == true -> "Backup saved ✓"
                    db.patientCount() == 0 -> "Nothing to back up yet"
                    else -> "Backup failed – please choose the folder again"
                }
            )
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                Backup.setAutoFolder(context, uri)
                folder = Backup.autoFolderName(context)
                localBackupNow()
            } catch (e: Exception) {
                notify("Please choose a different folder")
            }
        }
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            work("Creating backup file…", { _ -> Backup.exportZip(context, db, uri) }) { r ->
                notify(if (r.getOrNull() == true) "Backup file saved ✓" else "Could not save the backup file")
            }
        }
    }
    val restoreFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            confirm = replaceText(db.patientCount(), "the data from this backup folder") to {
                work("Restoring…", { _ -> Backup.restoreFromFolder(context, db, uri) }) { r ->
                    if (r.getOrNull() == true) {
                        afterRestore("Data restored ✓  The app will restart now")
                    } else {
                        notify("This is not a valid clinic backup")
                    }
                }
            }
        }
    }
    val restoreZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            confirm = replaceText(db.patientCount(), "the data from this backup file") to {
                work("Restoring…", { _ -> Backup.restoreZip(context, db, uri) }) { r ->
                    if (r.getOrNull() == true) {
                        afterRestore("Data restored ✓  The app will restart now")
                    } else {
                        notify("This is not a valid clinic backup")
                    }
                }
            }
        }
    }
    val addZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            work("Checking the backup file…", { _ -> Backup.planAddZip(context, db, uri) }) { r ->
                val plan = r.getOrNull()
                when {
                    plan == null -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    plan.newPatients == 0 -> {
                        notify("All patients in this file are already on this phone")
                    }
                    else -> {
                        addAsk = AddAsk("this file", plan) {
                            work("Adding patients…", { _ -> Backup.finishAddZip(context, db, plan) }) { r2 ->
                                val added = r2.getOrNull()
                                notify(if (added != null) "${patientsText(added)} added ✓" else friendlyError(r2.exceptionOrNull()))
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val undoSection: @Composable () -> Unit = {
            if (undoReady) {
                val count = RestoreSafety.patients(context)
                val time = RestoreSafety.time(context)
                FormCard(
                    "Undo last restore",
                    "Brings back the ${patientsText(count)} this phone had" + (if (time > 0L) " on ${fmtDateTime(time)}" else "")
                ) {
                    SoftButton("Undo last restore", Icons.Filled.Refresh, Modifier.fillMaxWidth(), bg = Brand.GoldSoft, fg = Brand.GoldDeep) {
                        askUndo = true
                    }
                }
            }
        }
        val driveSection: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().background(CharcoalGradient).drawBehind {
                        val c = Offset(size.width, 0f)
                        val r = size.width * 0.7f
                        drawCircle(
                            Brush.radialGradient(listOf(Brand.Green.copy(alpha = 0.5f), Color.Transparent), center = c, radius = r),
                            radius = r, center = c
                        )
                    }.padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Surface),
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Cloud, null, tint = Brand.GreenDeep, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Google Drive backup", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            if (driveOn) account.ifBlank { "Connected" } else "Safe in your Google account, like WhatsApp",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                        )
                    }
                    if (driveOn) Icon(Icons.Filled.CheckCircle, null, tint = Brand.Green, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!driveOn) {
                        Text(
                            "Connect once and your patients, payments and photos are copied to your Google Drive every time you close the app. " +
                                "If you change your tablet, everything comes back with one tap.",
                            fontSize = 14.sp, color = Brand.Sub
                        )
                        GradientButton("Connect Google Drive", Modifier.fillMaxWidth(), AppIcons.Cloud) { connectDrive() }
                    } else {
                        InfoLine("This device", deviceLabel)
                        InfoLine(
                            "Last backup",
                            if (driveLast > 0L) "${fmtDateTime(driveLast)}  (${agoText(driveLast)})" else "Not yet"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientButton("Back up now", Modifier.weight(1f), AppIcons.Cloud) { driveBackupNow() }
                            SoftButton("Restore", Icons.Filled.Refresh, Modifier.weight(1f), height = 56.dp) {
                                chooseDriveBackup()
                            }
                        }
                        HorizontalDivider(color = Brand.Line)
                        ToggleRow("Automatic backup", "Every time you close the app (waits for internet if needed)", autoDaily) {
                            autoDaily = it
                            DriveBackup.setAuto(context, it)
                        }
                        ToggleRow("Use mobile data", "Otherwise the backup waits for Wi-Fi", mobileData) {
                            mobileData = it
                            DriveBackup.setMobile(context, it)
                        }
                        Text(
                            "Disconnect Google Drive", color = Brand.Red, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                DriveBackup.disconnect(context)
                                refresh()
                                notify("Google Drive disconnected")
                            }.padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
        val localSection: @Composable () -> Unit = {
            FormCard("Backup on this tablet", "A copy is saved to a folder every time you close the app") {
                InfoLine("Folder", folder ?: "Not chosen yet")
                InfoLine("Last backup", if (localLast > 0L) "${fmtDateTime(localLast)}  (${agoText(localLast)})" else "Not yet")
                if (folder == null) {
                    GradientButton("Choose backup folder", Modifier.fillMaxWidth(), AppIcons.Cloud) { pickFolder.launch(null) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SoftButton("Back up now", AppIcons.Cloud, Modifier.weight(1f)) { localBackupNow() }
                        SoftButton("Change folder", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Ink) {
                            pickFolder.launch(null)
                        }
                    }
                }
                SoftButton("Save backup file (.zip)", Icons.Filled.Share, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    exportZip.launch(Backup.zipName())
                }
            }
        }
        val restoreSection: @Composable () -> Unit = {
            FormCard("Restore from a file", "Replace all data from a backup, or add the patients from another phone's .zip file") {
                SoftButton("Restore from backup folder", AppIcons.Cloud, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    restoreFolder.launch(null)
                }
                SoftButton("Restore from .zip file", Icons.Filled.Refresh, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    restoreZip.launch(arrayOf("*/*"))
                }
                SoftButton("Add patients from a .zip file", Icons.Filled.Add, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    addZip.launch(arrayOf("*/*"))
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            AppTopBar("Backup & Restore", "Keep your clinic data safe", onBack)
            if (twoColumn) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                            .navigationBarsPadding().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        undoSection()
                        driveSection()
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                            .navigationBarsPadding().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        localSection()
                        restoreSection()
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    undoSection()
                    driveSection()
                    localSection()
                    restoreSection()
                }
            }
        }
    }

    confirm?.let { (text, action) ->
        ConfirmDialog(
            "Restore backup?", text, confirmText = "Restore", danger = false,
            onConfirm = {
                confirm = null
                action()
            },
            onDismiss = { confirm = null }
        )
    }
    choices?.let { list ->
        BackupChoiceDialog(
            list,
            onPick = { item ->
                choices = null
                picked = item
            },
            onDismiss = { choices = null }
        )
    }
    picked?.let { item ->
        BackupActionDialog(
            item,
            onAdd = {
                picked = null
                driveAdd(item)
            },
            onReplace = {
                picked = null
                replaceItem = item
            },
            onDismiss = { picked = null }
        )
    }
    replaceItem?.let { item ->
        val here = db.patientCount()
        val whenText = if (item.time > 0L) fmtDateTime(item.time) else "an earlier date"
        val theirs = if (item.patients >= 0) patientsText(item.patients) else "the patients"
        val fewer = item.patients in 0 until here
        val text = buildString {
            append("This phone has ${patientsText(here)}. They will be replaced by $theirs from ${item.device} ($whenText).")
            if (fewer) append("\n\nThis backup has fewer patients. ${patientsText(here - item.patients)} would be removed from this phone.")
            if (here > 0) append("\n\nA copy of this phone's data is kept, so you can undo this.")
        }
        ConfirmDialog(
            "Replace this phone's data?", text, confirmText = "Replace", danger = fewer,
            onConfirm = {
                replaceItem = null
                driveRestore(item)
            },
            onDismiss = { replaceItem = null }
        )
    }
    addAsk?.let { ask ->
        val plan = ask.plan
        val names = plan.alreadyHere.take(5).joinToString(", ") +
            (if (plan.alreadyHere.size > 5) " and ${plan.alreadyHere.size - 5} more" else "")
        val text = buildString {
            append("${patientsText(plan.newPatients)} from ${ask.from} will be added to this phone, with their treatments, payments, prescriptions and photos.")
            if (plan.alreadyHere.isNotEmpty()) append("\n\nAlready on this phone, left as they are: $names.")
            append("\n\nAdded patients get new Patient IDs on this phone. A copy of this phone's data is kept, so you can undo this.")
        }
        ConfirmDialog(
            "Add ${patientsText(plan.newPatients)}?", text, confirmText = "Add patients", danger = false,
            onConfirm = {
                addAsk = null
                ask.action()
            },
            onDismiss = { addAsk = null }
        )
    }
    if (askUndo) {
        ConfirmDialog(
            "Undo last restore?",
            "This phone goes back to the ${patientsText(RestoreSafety.patients(context))} it had before. " +
                "Anything restored or added after that is removed from this phone.",
            confirmText = "Undo", danger = false,
            onConfirm = {
                askUndo = false
                undoNow()
            },
            onDismiss = { askUndo = false }
        )
    }
    busy?.let { BusyDialog(it) }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label:  ", fontSize = 14.sp, color = Brand.Sub)
        Text(value, fontSize = 14.sp, color = Brand.Ink, fontWeight = FontWeight.Medium)
    }
}

private fun friendlyError(e: Throwable?): String {
    val msg = e?.message ?: ""
    return when {
        msg.startsWith("There are no patients") -> msg
        msg.startsWith("Update both phones") -> msg
        msg.startsWith("This is not a valid") -> msg
        msg.startsWith("Open the backup file") -> msg
        msg.contains("401") || msg.contains("403") -> "Google Drive refused access. Tap Disconnect, then connect again."
        else -> "Something went wrong. Please check the internet and try again."
    }
}

// All backups on Google Drive, newest first, so the right one can be picked
@Composable
private fun BackupChoiceDialog(items: List<DriveBackupItem>, onPick: (DriveBackupItem) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Surface).padding(20.dp)
        ) {
            Text("Choose a backup", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Each phone and tablet keeps its own backups. Tap one to add its patients or to restore it.",
                fontSize = 14.sp, color = Brand.Sub
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEach { item ->
                    val count = if (item.patients < 0) "" else "  ·  " + patientsText(item.patients)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Soft)
                            .clickable { onPick(item) }.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.device, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.thisDevice) {
                                Text("This device", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Brand.GreenDeep)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            (if (item.time > 0L) fmtDateTime(item.time) else "Date not known") + count,
                            fontSize = 14.sp, color = Brand.Sub
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Cancel", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.Ink,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// After picking a backup: add its patients to this phone, or replace everything
@Composable
private fun BackupActionDialog(item: DriveBackupItem, onAdd: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Surface).padding(22.dp)
        ) {
            Text(item.device, fontFamily = ClinicTitleFont, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
            Text(
                (if (item.time > 0L) fmtDateTime(item.time) else "Date not known") +
                    (if (item.patients >= 0) "  ·  " + patientsText(item.patients) else ""),
                fontSize = 14.sp, color = Brand.Sub
            )
            Spacer(Modifier.height(18.dp))
            if (!item.thisDevice) {
                GradientButton("Add its patients", Modifier.fillMaxWidth(), Icons.Filled.Add, height = 52.dp, onClick = onAdd)
                Text(
                    "New patients are added to this phone. Patients already here stay as they are.",
                    fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )
            }
            SoftButton("Replace everything", Icons.Filled.Refresh, Modifier.fillMaxWidth(), bg = Brand.RedSoft, fg = Brand.Red, onClick = onReplace)
            Text(
                "All data on this phone is replaced by this backup.",
                fontSize = 13.sp, color = Brand.Sub, modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Cancel", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.Ink,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// Starts the app fresh after a restore, so every screen shows the restored data
private fun restartAfterRestore(context: Context) {
    val app = context.applicationContext
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            val component = app.packageManager.getLaunchIntentForPackage(app.packageName)?.component
            if (component != null) {
                app.startActivity(Intent.makeRestartActivityTask(component))
                Runtime.getRuntime().exit(0)
            }
        } catch (e: Exception) {
            // If the restart does not work, the restored data is still shown
        }
    }, 1500L)
}
