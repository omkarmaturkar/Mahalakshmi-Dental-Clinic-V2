package com.mahalakshmi.dental

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/** Saves the details of any crash, so they can be shown and sent the next time the app opens. */
object CrashGuard {
    private var installed = false

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    /** Call first thing in onCreate. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save(app, error)
            } catch (e: Throwable) {
                // Nothing more we can do
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun save(context: Context, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val text = "Mahalakshmi Dental Clinic – problem report\n" +
            "App version: $version\n" +
            "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n" + trace
        file(context).writeText(text.take(15000))
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}

/** Shown instead of the app when the last session closed because of an error. */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Brand.Bg).statusBarsPadding().navigationBarsPadding().padding(20.dp)
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.RedSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Warning, null, tint = Brand.Red, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("The app closed unexpectedly", fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = Brand.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your patient data is safe. Please send this report to the developer so the problem can be fixed.",
            fontSize = 14.sp, color = Brand.Sub
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Brand.Ink,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientButton("Send report", Modifier.fillMaxWidth(), Icons.Filled.Share) { shareReport(context, report) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Copy", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Ink) { copyReport(context, report) }
            SoftButton("Open the app", Icons.Filled.Refresh, Modifier.weight(1f)) { onContinue() }
        }
    }
}

private fun shareReport(context: Context, report: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(send, "Send report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open sharing", Toast.LENGTH_SHORT).show()
    }
}

private fun copyReport(context: Context, report: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Problem report", report))
        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not copy", Toast.LENGTH_SHORT).show()
    }
}
