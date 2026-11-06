package com.mahalakshmi.dental

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
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackupScreen(db: ClinicDb, onBack: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    var folder by remember { mutableStateOf(Backup.autoFolderName(context)) }
    var localLast by remember { mutableLongStateOf(Backup.lastBackup(context)) }
    var driveOn by remember { mutableStateOf(DriveBackup.isConnected(context)) }
    var account by remember { mutableStateOf(DriveBackup.account(context)) }
    var driveLast by remember { mutableLongStateOf(DriveBackup.lastBackup(context)) }
    var autoDaily by remember { mutableStateOf(DriveBackup.autoEnabled(context)) }
    var mobileData by remember { mutableStateOf(DriveBackup.useMobileData(context)) }

    fun refresh() {
        folder = Backup.autoFolderName(context)
        localLast = Backup.lastBackup(context)
        driveOn = DriveBackup.isConnected(context)
        account = DriveBackup.account(context)
        driveLast = DriveBackup.lastBackup(context)
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

    fun driveRestore() {
        withDriveToken { token ->
            work("Restoring from Google Drive…", { progress -> DriveBackup.restore(context, db, token, progress) }) { r ->
                when {
                    r.getOrNull() == true -> {
                        notify("Data restored from Google Drive ✓")
                        onRestored()
                    }
                    r.isFailure -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    else -> {
                        notify("No backup found on this Google account")
                    }
                }
            }
        }
    }

    fun connectDrive() {
        withDriveToken { token ->
            work("Connecting to Google Drive…", { _ ->
                DriveBackup.markConnected(context, token)
                if (db.patientCount() == 0) DriveBackup.latestBackupDate(token) else null
            }) { r ->
                val found = r.getOrNull()
                when {
                    r.isFailure -> {
                        notify(friendlyError(r.exceptionOrNull()))
                    }
                    found != null -> {
                        confirm = "We found a backup from ${prettyDate(found)} on your Google Drive. Restore it now?" to { driveRestore() }
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
            confirm = "Everything in the app will be replaced with the data from this backup folder." to {
                work("Restoring…", { _ -> Backup.restoreFromFolder(context, db, uri) }) { r ->
                    if (r.getOrNull() == true) {
                        notify("Data restored ✓")
                        onRestored()
                    } else {
                        notify("This is not a valid clinic backup")
                    }
                }
            }
        }
    }
    val restoreZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            confirm = "Everything in the app will be replaced with the data from this backup file." to {
                work("Restoring…", { _ -> Backup.restoreZip(context, db, uri) }) { r ->
                    if (r.getOrNull() == true) {
                        notify("Data restored ✓")
                        onRestored()
                    } else {
                        notify("This is not a valid clinic backup")
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val twoColumn = maxWidth >= 760.dp

        val driveSection: @Composable () -> Unit = {
            AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().background(CharcoalGradient).drawBehind {
                        val c = Offset(size.width, 0f)
                        val r = size.width * 0.7f
                        drawCircle(
                            Brush.radialGradient(listOf(Brand.Sun.copy(alpha = 0.16f), Color.Transparent), center = c, radius = r),
                            radius = r, center = c
                        )
                    }.padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Surface),
                        contentAlignment = Alignment.Center
                    ) { Icon(AppIcons.Cloud, null, tint = Brand.Ink, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Google Drive backup", color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
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
                        InfoLine(
                            "Last backup",
                            if (driveLast > 0L) "${fmtDateTime(driveLast)}  (${agoText(driveLast)})" else "Not yet"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientButton("Back up now", Modifier.weight(1f), AppIcons.Cloud) { driveBackupNow() }
                            SoftButton("Restore", Icons.Filled.Refresh, Modifier.weight(1f), height = 56.dp) {
                                confirm = "Everything in the app will be replaced with your latest Google Drive backup." to { driveRestore() }
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
            FormCard("Restore from a file", "Use on a new tablet or after reinstalling. This replaces all current data.") {
                SoftButton("Restore from backup folder", AppIcons.Cloud, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    restoreFolder.launch(null)
                }
                SoftButton("Restore from .zip file", Icons.Filled.Refresh, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                    restoreZip.launch(arrayOf("*/*"))
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
                    ) { driveSection() }
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
    busy?.let { BusyDialog(it) }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label:  ", fontSize = 14.sp, color = Brand.Sub)
        Text(value, fontSize = 14.sp, color = Brand.Ink, fontWeight = FontWeight.Medium)
    }
}

private fun prettyDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
} catch (e: Exception) {
    iso
}

private fun friendlyError(e: Throwable?): String {
    val msg = e?.message ?: ""
    return when {
        msg.startsWith("There are no patients") -> msg
        msg.contains("401") || msg.contains("403") -> "Google Drive refused access. Tap Disconnect, then connect again."
        else -> "Something went wrong. Please check the internet and try again."
    }
}
