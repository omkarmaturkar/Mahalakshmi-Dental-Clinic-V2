package com.mahalakshmi.dental

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

// ───────────── App lock ─────────────
/** PIN (and fingerprint) lock. The PIN is never stored, only a salted fingerprint of it. */
object AppLock {
    var locked by mutableStateOf(false)
        private set
    var chooseNewPin by mutableStateOf(false)
    var busy = false
    private var leftAt = 0L

    private fun prefs(c: Context) = c.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    fun isOn(c: Context): Boolean = prefs(c).getString("pin", null) != null
    fun fingerprintOn(c: Context): Boolean = prefs(c).getBoolean("finger", true)
    fun lockAfterSeconds(c: Context): Int = prefs(c).getInt("after", 60)

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun hash(pin: String, salt: String) = hex(MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()))

    fun setPin(c: Context, pin: String) {
        val salt = hex(ByteArray(16).also { SecureRandom().nextBytes(it) })
        prefs(c).edit().putString("salt", salt).putString("pin", hash(pin, salt)).apply()
    }

    fun check(c: Context, pin: String): Boolean {
        val p = prefs(c)
        return p.getString("pin", null) == hash(pin, p.getString("salt", "") ?: "")
    }

    fun turnOff(c: Context) {
        prefs(c).edit().remove("pin").remove("salt").apply()
        locked = false
    }

    fun setFingerprint(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("finger", on).apply()
    }

    fun setLockAfter(c: Context, seconds: Int) {
        prefs(c).edit().putInt("after", seconds).apply()
    }

    /** The app has just opened. */
    fun start(c: Context) {
        locked = isOn(c)
    }

    fun leftApp() {
        leftAt = System.currentTimeMillis()
    }

    fun cameBack(c: Context) {
        if (busy || leftAt == 0L || !isOn(c)) return
        if (System.currentTimeMillis() - leftAt >= lockAfterSeconds(c) * 1000L) locked = true
    }

    fun unlock() {
        locked = false
    }
}

fun Context.fragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

fun canUseFingerprint(c: Context): Boolean = try {
    BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
} catch (e: Exception) {
    false
}

fun phoneHasScreenLock(c: Context): Boolean =
    (c.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

/** Fingerprint check. withPhoneLock = the phone's own PIN or pattern is also accepted (used by "Forgot PIN"). */
fun askBiometric(activity: FragmentActivity, title: String, withPhoneLock: Boolean, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLock.busy = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                AppLock.busy = false
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder().setTitle(title)
    if (withPhoneLock) {
        info.setSubtitle("Use your fingerprint or your phone's screen lock")
        info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    } else {
        info.setSubtitle("Touch the fingerprint sensor")
        info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        info.setNegativeButtonText("Use PIN")
    }
    AppLock.busy = true
    try {
        prompt.authenticate(info.build())
    } catch (e: Exception) {
        AppLock.busy = false
    }
}

/** Covers the app with the lock screen while it is locked. */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        content()
        if (AppLock.locked) LockScreen()
    }
    if (AppLock.chooseNewPin) {
        PinDialog("Choose a new PIN", confirmTwice = true, verify = null, onDismiss = { AppLock.chooseNewPin = false }) { pin ->
            AppLock.setPin(context, pin)
            AppLock.chooseNewPin = false
            Toast.makeText(context, "New PIN saved ✓", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun LockScreen() {
    val context = LocalContext.current
    val activity = remember(context) { context.fragmentActivity() }
    val finger = remember { AppLock.fingerprintOn(context) && canUseFingerprint(context) }
    var pin by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tries by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val useFinger: () -> Unit = {
        if (activity != null) askBiometric(activity, "Unlock the clinic app", false) { AppLock.unlock() }
    }

    BackHandler { activity?.moveTaskToBack(true) }
    LaunchedEffect(Unit) {
        if (finger) {
            delay(350)
            useFinger()
        }
    }
    LaunchedEffect(tries) {
        if (tries > 0) for (x in listOf(16f, -16f, 11f, -11f, 5f, -5f, 0f)) shake.animateTo(x, tween(40))
    }

    fun press(d: String) {
        if (System.currentTimeMillis() < waitUntil) {
            note = "Too many wrong tries. Please wait 30 seconds."
            return
        }
        if (pin.length >= 4) return
        pin += d
        if (pin.length < 4) return
        if (AppLock.check(context, pin)) {
            pin = ""
            note = ""
            AppLock.unlock()
        } else {
            pin = ""
            tries++
            if (tries % 5 == 0) {
                waitUntil = System.currentTimeMillis() + 30_000
                note = "Too many wrong tries. Please wait 30 seconds."
            } else {
                note = "Wrong PIN. Please try again."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Brand.Bg)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LogoTile(64.dp)
        Spacer(Modifier.height(18.dp))
        Text("Enter your PIN", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            note.ifBlank { "The clinic app is locked" }, fontSize = 13.sp,
            color = if (note.isBlank()) Brand.Sub else Brand.Red, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        PinDots(pin.length, Modifier.offset(x = shake.value.dp))
        Spacer(Modifier.height(30.dp))
        PinPad(onDigit = { press(it) }, onDelete = { pin = pin.dropLast(1) }, onFinger = if (finger) useFinger else null)
        Spacer(Modifier.height(18.dp))
        Text(
            "Forgot PIN?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand.GreenText,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                when {
                    activity == null -> {}
                    !phoneHasScreenLock(context) ->
                        note = "To reset the PIN, first set a screen lock on your phone (Settings → Security)."
                    else -> askBiometric(activity, "Reset app lock", true) {
                        AppLock.unlock()
                        AppLock.chooseNewPin = true
                    }
                }
            }.padding(8.dp)
        )
    }
}

@Composable
private fun PinDialog(
    title: String,
    confirmTwice: Boolean,
    verify: ((String) -> Boolean)?,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    fun press(d: String) {
        if (pin.length >= 4) return
        pin += d
        if (pin.length < 4) return
        val entered = pin
        pin = ""
        when {
            verify != null -> {
                if (verify(entered)) onDone(entered) else {
                    note = "Wrong PIN. Please try again."
                }
            }
            !confirmTwice -> onDone(entered)
            first == null -> {
                first = entered
                note = ""
            }
            first == entered -> onDone(entered)
            else -> {
                first = null
                note = "The two PINs didn't match. Please start again."
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (confirmTwice && first != null) "Enter the same PIN again" else title,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                note.ifBlank { if (confirmTwice) "4 digits that you will remember" else "Enter your current PIN" },
                fontSize = 13.sp, color = if (note.isBlank()) Brand.Sub else Brand.Red, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            PinDots(pin.length)
            Spacer(Modifier.height(24.dp))
            PinPad(onDigit = { press(it) }, onDelete = { pin = pin.dropLast(1) }, onFinger = null)
            Spacer(Modifier.height(16.dp))
            SoftButton("Cancel", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
        }
    }
}

@Composable
private fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) { i ->
            Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < filled) Brand.GreenDeep else Brand.Line))
        }
    }
}

@Composable
private fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit, onFinger: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { d -> PadKey(d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onFinger != null) {
                PadButton(onFinger) { FingerprintGlyph(Brand.GreenDeep, 30.dp) }
            } else {
                Spacer(Modifier.size(70.dp))
            }
            PadKey("0") { onDigit("0") }
            PadButton(onDelete) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Delete", tint = Brand.Sub, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PadKey(text: String, onClick: () -> Unit) {
    PadButton(onClick, filled = true) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Brand.Ink)
    }
}

@Composable
private fun PadButton(onClick: () -> Unit, filled: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(70.dp).clip(CircleShape)
            .then(if (filled) Modifier.background(Brand.Surface).border(1.dp, Brand.Line, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun FingerprintGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.075f, cap = StrokeCap.Round)
        val c = Offset(this.size.width / 2f, this.size.height * 0.55f)
        listOf(0.46f, 0.35f, 0.24f, 0.13f).forEachIndexed { i, r ->
            val rad = s * r
            drawArc(
                color, startAngle = 195f + i * 6f, sweepAngle = 150f - i * 12f, useCenter = false,
                topLeft = Offset(c.x - rad, c.y - rad), size = Size(rad * 2, rad * 2), style = stroke
            )
        }
        drawLine(color, Offset(c.x, c.y - s * 0.02f), Offset(c.x, c.y + s * 0.3f), strokeWidth = s * 0.075f, cap = StrokeCap.Round)
    }
}

/** "App lock" row in the menu. */
@Composable
fun AppLockRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(AppLock.isOn(context)) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .clickable { open = true }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, null, tint = Brand.Sub, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text("App lock", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Brand.Ink, modifier = Modifier.weight(1f))
        Pill(if (on) "On" else "Off", if (on) Brand.GreenSoft else Brand.Soft, if (on) Brand.GreenText else Brand.Sub)
    }
    if (open) {
        AppLockDialog {
            open = false
            on = AppLock.isOn(context)
        }
    }
}

@Composable
private fun AppLockDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val hasFinger = remember { canUseFingerprint(context) }
    val screenLock = remember { phoneHasScreenLock(context) }
    var on by remember { mutableStateOf(AppLock.isOn(context)) }
    var finger by remember { mutableStateOf(AppLock.fingerprintOn(context)) }
    var after by remember { mutableIntStateOf(AppLock.lockAfterSeconds(context)) }
    var choosing by remember { mutableStateOf(false) }
    var turningOff by remember { mutableStateOf(false) }
    val afterLabels = listOf("Right away", "1 min", "5 min")
    val afterValues = listOf(0, 60, 300)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brand.GreenSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, null, tint = Brand.GreenDeep, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("App lock", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                    Text(
                        if (on) "On · PIN needed to open the app" else "Off",
                        fontSize = 13.sp, color = if (on) Brand.GreenText else Brand.Sub
                    )
                }
            }
            Text(
                "Only someone with the PIN or your fingerprint can see patients, bills and accounts.",
                fontSize = 14.sp, color = Brand.Sub
            )
            if (!on) {
                GradientButton("Set a 4-digit PIN", Modifier.fillMaxWidth(), Icons.Filled.Lock) { choosing = true }
            } else {
                if (hasFinger) {
                    ToggleRow("Unlock with fingerprint", "Faster than typing the PIN", finger) {
                        finger = it
                        AppLock.setFingerprint(context, it)
                    }
                }
                Text("Lock again after leaving the app", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
                Segmented(afterLabels, afterLabels[afterValues.indexOf(after).coerceAtLeast(0)]) { label ->
                    after = afterValues[afterLabels.indexOf(label)]
                    AppLock.setLockAfter(context, after)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftButton("Change PIN", null, Modifier.weight(1f)) { choosing = true }
                    SoftButton("Turn off", null, Modifier.weight(1f), bg = Brand.RedSoft, fg = Brand.Red) { turningOff = true }
                }
            }
            if (!screenLock) {
                Text(
                    "Tip: also set a screen lock on this phone. If you ever forget the PIN, the phone's screen lock is how you get back in.",
                    fontSize = 12.sp, color = Brand.GoldDeep
                )
            }
            SoftButton("Close", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onClose)
        }
    }
    if (choosing) {
        PinDialog(
            if (on) "Choose a new PIN" else "Choose a 4-digit PIN", confirmTwice = true, verify = null,
            onDismiss = { choosing = false }
        ) { pin ->
            AppLock.setPin(context, pin)
            on = true
            choosing = false
            notify("App lock is on ✓")
        }
    }
    if (turningOff) {
        PinDialog(
            "Enter your PIN to turn off app lock", confirmTwice = false, verify = { AppLock.check(context, it) },
            onDismiss = { turningOff = false }
        ) {
            AppLock.turnOff(context)
            on = false
            turningOff = false
            notify("App lock turned off")
        }
    }
}

// ───────────── Backup every time the app is closed ─────────────
object AutoBackup {
    private var savedVersion = 0

    /** Called when the app goes to the background. Backs up at once if anything changed. */
    fun onAppClosed(c: Context, db: ClinicDb) {
        val version = DataBus.version
        if (version == savedVersion) return
        savedVersion = version
        val app = c.applicationContext
        // 1) The folder on this phone or tablet: straight away
        Thread {
            try {
                Backup.autoBackup(app, db, force = true)
            } catch (e: Throwable) {
                // A failed background backup should never close the app
            }
        }.start()
        // 2) Google Drive: as soon as there is internet, even if the app is swiped away
        if (DriveBackup.isConnected(app) && DriveBackup.autoEnabled(app)) {
            val network = if (DriveBackup.useMobileData(app)) NetworkType.CONNECTED else NetworkType.UNMETERED
            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork("drive-backup", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!DriveBackup.isConnected(c) || !DriveBackup.autoEnabled(c)) return Result.success()
        val db = ClinicDb(c)
        return try {
            if (db.patientCount() == 0) return Result.success()
            val token = DriveBackup.silentToken(c) ?: return Result.retry()
            DriveBackup.backup(c, db, token) {}
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } finally {
            db.close()
        }
    }
}
