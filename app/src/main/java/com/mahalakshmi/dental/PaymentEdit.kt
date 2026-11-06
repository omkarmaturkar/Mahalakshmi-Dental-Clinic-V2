package com.mahalakshmi.dental

import android.content.ContentValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

/** Changes a payment that was already saved (amount, mode and date). */
fun ClinicDb.updatePayment(id: Long, amount: Double, mode: String, date: Long) {
    val cv = ContentValues().apply {
        put("amount", amount)
        put("mode", mode)
        put("date", date)
    }
    writableDatabase.update("payments", cv, "id = ?", arrayOf(id.toString()))
}

@Composable
fun EditPaymentDialog(
    payment: Payment,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Double, String, Long) -> Unit
) {
    var amount by remember { mutableStateOf(numText(abs(payment.amount))) }
    var mode by remember { mutableStateOf(payment.mode.ifBlank { "Cash" }) }
    var day by remember { mutableLongStateOf(epochDayOf(payment.date)) }
    var pickDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val value = amount.toDoubleOrNull() ?: 0.0

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brand.Surface).padding(26.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Text("₹", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Edit payment", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text("Saved earlier as ${rs(abs(payment.amount))} on ${fmtDate(payment.date)}", fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(18.dp))
            AppField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Correct amount",
                type = KeyboardType.Decimal, prefix = "₹ "
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card").forEach { m -> ChoiceChip(m, mode == m) { mode = m } }
            }
            Spacer(Modifier.height(14.dp))
            SoftButton("Date: ${fmtDayLong(day)}", Icons.Filled.DateRange, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                pickDate = true
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Filled.Delete, { askDelete = true }, bg = Brand.RedSoft, tint = Brand.Red, size = 52.dp)
                SoftButton("Cancel", null, Modifier.weight(1f), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
                GradientButton("Save", Modifier.weight(1f), Icons.Filled.Check, enabled = value > 0, height = 52.dp) {
                    val signed = if (payment.amount < 0) -value else value
                    onSave(signed, mode, withDay(payment.date, day))
                }
            }
        }
    }
    if (pickDate) DatePick(day, onDismiss = { pickDate = false }) { d -> day = d }
    if (askDelete) {
        ConfirmDialog(
            "Delete this payment?",
            "${rs(abs(payment.amount))} received on ${fmtDate(payment.date)} will be removed from the account.",
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false }
        )
    }
}
