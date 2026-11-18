package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/** Adds a treatment to the list before it is used, with its usual fee. "Save & add another" keeps it open. */
@Composable
fun NewTreatmentDialog(db: ClinicDb, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val notify = LocalNotify.current
    var name by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun add(): Boolean {
        val n = name.trim()
        if (n.isBlank()) return false
        db.rememberTreatment(n)
        val f = fee.toDoubleOrNull() ?: 0.0
        if (f > 0.0) db.rememberPrice(n, f)
        onAdded(n)
        notify("$n added to the list ✓")
        return true
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(24.dp)
        ) {
            Text("New treatment", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text("It joins the list, ready to choose for any patient.", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(14.dp))
            AppField(
                name, { name = it }, "Treatment name",
                modifier = Modifier.fillMaxWidth().focusRequester(focus), caps = KeyboardCapitalization.Words
            )
            Spacer(Modifier.height(10.dp))
            AppField(
                fee, { fee = it.filter { c -> c.isDigit() || c == '.' } }, "Usual fee (optional)",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            Spacer(Modifier.height(18.dp))
            SoftButton("Save & add another", Icons.Filled.Add, Modifier.fillMaxWidth(), height = 48.dp) {
                if (add()) {
                    name = ""
                    fee = ""
                    runCatching { focus.requestFocus() }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton("Close", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = name.isNotBlank(), height = 52.dp) {
                    if (add()) onDismiss()
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
}
