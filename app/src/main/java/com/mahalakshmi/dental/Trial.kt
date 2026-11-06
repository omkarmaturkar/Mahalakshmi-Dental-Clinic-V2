package com.mahalakshmi.dental

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil

/** A trial version stops working after TRIAL_UNTIL (set by the Build APK workflow). Full versions have 0. */
object Trial {
    private const val DAY = 24L * 60 * 60 * 1000

    val isTrial: Boolean get() = TRIAL_UNTIL > 0L

    /** Changes whenever the date is checked again, so the screens look again. */
    var checks by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("trial", Context.MODE_PRIVATE)

    /** The latest time the app has ever seen: turning the phone's date back does not help. */
    fun now(c: Context): Long {
        val p = prefs(c)
        val seen = p.getLong("seen", 0L)
        val clock = System.currentTimeMillis()
        if (clock > seen) p.edit().putLong("seen", clock).apply()
        return maxOf(clock, seen)
    }

    fun expired(c: Context): Boolean = isTrial && now(c) >= TRIAL_UNTIL

    fun daysLeft(c: Context): Int =
        if (!isTrial) Int.MAX_VALUE else ceil((TRIAL_UNTIL - now(c)).coerceAtLeast(0L) / DAY.toDouble()).toInt()

    fun recheck() {
        checks++
    }

    /** When online, a trial version also reads the real date from the internet. */
    fun checkInternetTime(c: Context) {
        if (!isTrial) return
        val app = c.applicationContext
        Thread {
            try {
                val conn = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val time = conn.date
                conn.disconnect()
                if (time > 0L) {
                    val p = prefs(app)
                    if (time > p.getLong("seen", 0L)) p.edit().putLong("seen", time).apply()
                    Handler(Looper.getMainLooper()).post { checks++ }
                }
            } catch (e: Exception) {
                // Offline: the phone's own clock is used
            }
        }.start()
    }
}

/** Shown instead of the app when the trial is over. The clinic's data stays safe and can still be backed up. */
@Composable
fun TrialEndedScreen(onBackup: () -> Unit) {
    val context = LocalContext.current
    val contact = TRIAL_CONTACT.ifBlank { "the developer" }
    val phone = TRIAL_CONTACT.filter { it.isDigit() }.takeLast(10)
    Column(
        Modifier.fillMaxSize().background(Brand.Bg).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LogoTile(84.dp)
        Spacer(Modifier.height(26.dp))
        Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.GoldSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Brand.GoldDeep, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Trial period has ended", fontFamily = DisplayFont, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            color = Brand.Ink, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This trial version ended on ${fmtDate(TRIAL_UNTIL)}. To keep using the app, please contact " +
                "$contact to activate the full version. All your patients and records are safe.",
            fontSize = 14.sp, color = Brand.Sub, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp)
        )
        Spacer(Modifier.height(26.dp))
        if (phone.length == 10) {
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientButton("Call", Modifier.weight(1f), Icons.Filled.Call) { callNumber(context, phone) }
                SoftButton("WhatsApp", AppIcons.Chat, Modifier.weight(1f), height = 56.dp) {
                    openWhatsApp(context, phone, "Hello, the trial of the clinic app has ended. I would like to activate the full version.")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        SoftButton(
            "Back up my data", AppIcons.Cloud, Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            bg = Brand.Soft, fg = Brand.Ink, onClick = onBackup
        )
    }
}

/** Slim reminder shown during the last days of a trial. */
@Composable
fun TrialStrip(daysLeft: Int, padNav: Boolean) {
    val text = (if (daysLeft <= 1) "Trial version · last day" else "Trial version · $daysLeft days left") +
        if (TRIAL_CONTACT.isNotBlank()) "  ·  $TRIAL_CONTACT" else ""
    Row(
        Modifier.fillMaxWidth().background(Brand.GoldSoft)
            .then(if (padNav) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, null, tint = Brand.GoldDeep, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Brand.GoldDeep, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
