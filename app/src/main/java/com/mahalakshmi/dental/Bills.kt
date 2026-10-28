package com.mahalakshmi.dental

import android.content.Context
import android.text.Layout
import java.io.File
import kotlin.math.abs

// ───────────── Amount in words (Indian lakh / crore system) ─────────────
private val onesWords = listOf(
    "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
    "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
)
private val tensWords = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

private fun twoDigits(n: Int): String =
    if (n < 20) onesWords[n] else tensWords[n / 10] + (if (n % 10 != 0) " " + onesWords[n % 10] else "")

private fun threeDigits(n: Int): String {
    val h = n / 100
    val r = n % 100
    return listOf(if (h > 0) onesWords[h] + " Hundred" else "", if (r > 0) twoDigits(r) else "")
        .filter { it.isNotBlank() }.joinToString(" ")
}

/** 12500 → "Rupees Twelve Thousand Five Hundred Only" */
fun amountInWords(amount: Double): String {
    var n = abs(amount).toLong()
    if (n == 0L) return "Rupees Zero Only"
    val crore = (n / 10_000_000).toInt().coerceAtMost(999)
    n %= 10_000_000
    val lakh = (n / 100_000).toInt()
    n %= 100_000
    val thousand = (n / 1000).toInt()
    n %= 1000
    val rest = n.toInt()
    val parts = mutableListOf<String>()
    if (crore > 0) parts.add(threeDigits(crore) + " Crore")
    if (lakh > 0) parts.add(twoDigits(lakh) + " Lakh")
    if (thousand > 0) parts.add(twoDigits(thousand) + " Thousand")
    if (rest > 0) parts.add(threeDigits(rest))
    return "Rupees " + parts.joinToString(" ") + " Only"
}

private fun treatmentText(t: Treatment, doctors: Map<Long, String>): String {
    val doctor = doctors[t.doctorId].orEmpty()
    return if (doctor.isBlank()) t.name else "${t.name}\n$doctor"
}

// ───────────── Full bill ─────────────
/** One bill for the whole account: every started or finished treatment, every payment, and the balance. */
fun invoicePdf(context: Context, db: ClinicDb, patientId: Long): File {
    val patient = db.getPatient(patientId) ?: error("Patient not found")
    val all = db.treatmentsOf(patientId)
    val billed = all.filter { it.status != STATUS_PLANNED }.sortedBy { it.createdAt }
    val payments = db.paymentsOf(patientId).sortedBy { it.date }
    val account = accountOf(all, payments)
    val doctors = db.doctorNames()
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("INVOICE", "Invoice No. ${docNo("INV-", patient.id)}\nDate: ${fmtDate(System.currentTimeMillis())}")
    pdf.patientBox(patient)
    pdf.row(
        listOf(
            PdfCell("#", 0.06f, 9.5f, PdfSub, 1),
            PdfCell("Treatment", 0.42f, 9.5f, PdfSub, 1),
            PdfCell("Tooth", 0.16f, 9.5f, PdfSub, 1),
            PdfCell("Date", 0.18f, 9.5f, PdfSub, 1),
            PdfCell("Amount", 0.18f, 9.5f, PdfSub, 1, end)
        ),
        fill = PdfSoft
    )
    if (billed.isEmpty()) pdf.text("No treatments yet.", 10f, PdfSub)
    billed.forEachIndexed { i, t ->
        pdf.row(
            listOf(
                PdfCell("${i + 1}", 0.06f, 10f, PdfSub),
                PdfCell(treatmentText(t, doctors), 0.42f, 10f, PdfInk, 1),
                PdfCell(t.teethList.joinToString(", ").ifBlank { "–" }, 0.16f, 10f),
                PdfCell(fmtDate(if (t.doneAt > 0L) t.doneAt else t.createdAt), 0.18f, 9.5f, PdfSub),
                PdfCell(rs(t.fee), 0.18f, 10f, align = end)
            ),
            pad = 7f, line = true
        )
    }
    pdf.space(10f)
    pdf.total("Total", rs(account.billed))
    pdf.total("Paid", rs(account.paid), PdfGreen)
    pdf.total(
        if (account.balance >= 0) "Balance due" else "Advance paid", rs(abs(account.balance)),
        if (account.balance > 0.01) PdfRed else PdfGreen, strong = true
    )
    if (account.billed > 0 && account.balance <= 0.01) pdf.stamp("PAID", PdfGreen)
    pdf.space(8f)
    pdf.text("Amount in words: ${amountInWords(account.billed)}", 9f, PdfSub, 1)
    if (payments.isNotEmpty()) {
        pdf.space(12f)
        pdf.text("Payments received", 11f, PdfGreen, 2)
        pdf.row(
            listOf(
                PdfCell("Date", 0.45f, 9.5f, PdfSub, 1),
                PdfCell("Mode", 0.3f, 9.5f, PdfSub, 1),
                PdfCell("Amount", 0.25f, 9.5f, PdfSub, 1, end)
            ),
            fill = PdfSoft
        )
        payments.forEach { p ->
            pdf.row(
                listOf(
                    PdfCell(fmtDate(p.date), 0.45f, 10f),
                    PdfCell(p.mode.ifBlank { "–" }, 0.3f, 10f, PdfSub),
                    PdfCell(if (p.amount < 0) "– " + rs(-p.amount) else rs(p.amount), 0.25f, 10f, align = end)
                ),
                pad = 5f, line = true
            )
        }
    }
    pdf.signature()
    return pdf.save(pdfFile(context, "Invoice", patient.name))
}

// ───────────── Payment receipt ─────────────
/** Receipt for one payment, with the account balance after it. */
fun paymentReceiptPdf(context: Context, db: ClinicDb, paymentId: Long): File {
    val pay = db.getPaymentById(paymentId) ?: error("Payment not found")
    val patient = db.getPatient(pay.patientId) ?: error("Patient not found")
    val account = accountOf(db.treatmentsOf(pay.patientId), db.paymentsOf(pay.patientId))
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("PAYMENT RECEIPT", "Receipt No. ${docNo("R-", pay.id)}\nDate: ${fmtDate(pay.date)}")
    pdf.patientBox(patient)
    pdf.row(
        listOf(
            PdfCell("Amount received with thanks", 0.6f, 11f, PdfInk, 1),
            PdfCell(rs(pay.amount), 0.4f, 16f, PdfGreen, 2, end)
        ),
        fill = PdfSoft, pad = 12f, after = 8f
    )
    pdf.text("Received: ${amountInWords(pay.amount)}", 9f, PdfSub, 1, after = 10f)
    pdf.total("Payment mode", pay.mode.ifBlank { "–" })
    pdf.total("Total treatment amount", rs(account.billed))
    pdf.total("Total paid so far", rs(account.paid), PdfGreen)
    pdf.total(
        if (account.balance >= 0) "Balance due" else "Advance paid", rs(abs(account.balance)),
        if (account.balance > 0.01) PdfRed else PdfGreen, strong = true
    )
    pdf.stamp(if (account.balance <= 0.01) "PAID" else "RECEIVED", PdfGreen)
    pdf.signature()
    return pdf.save(pdfFile(context, "Receipt", patient.name))
}

// ───────────── Treatment estimate ─────────────
/** Quotation for the treatments that are planned but not started yet. */
fun estimatePdf(context: Context, db: ClinicDb, patientId: Long): File {
    val patient = db.getPatient(patientId) ?: error("Patient not found")
    val planned = db.treatmentsOf(patientId).filter { it.status == STATUS_PLANNED }.sortedBy { it.createdAt }
    val doctors = db.doctorNames()
    val total = planned.sumOf { it.fee }
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("TREATMENT ESTIMATE", "Estimate No. ${docNo("EST-", patient.id)}\nDate: ${fmtDate(System.currentTimeMillis())}")
    pdf.patientBox(patient)
    pdf.row(
        listOf(
            PdfCell("#", 0.06f, 9.5f, PdfSub, 1),
            PdfCell("Planned treatment", 0.52f, 9.5f, PdfSub, 1),
            PdfCell("Tooth", 0.2f, 9.5f, PdfSub, 1),
            PdfCell("Amount", 0.22f, 9.5f, PdfSub, 1, end)
        ),
        fill = PdfSoft
    )
    if (planned.isEmpty()) pdf.text("No planned treatments.", 10f, PdfSub)
    planned.forEachIndexed { i, t ->
        pdf.row(
            listOf(
                PdfCell("${i + 1}", 0.06f, 10f, PdfSub),
                PdfCell(treatmentText(t, doctors), 0.52f, 10f, PdfInk, 1),
                PdfCell(t.teethList.joinToString(", ").ifBlank { "–" }, 0.2f, 10f),
                PdfCell(rs(t.fee), 0.22f, 10f, align = end)
            ),
            pad = 7f, line = true
        )
    }
    pdf.space(10f)
    pdf.total("Estimated total", rs(total), strong = true)
    pdf.stamp("ESTIMATE", PdfGold)
    pdf.space(8f)
    pdf.text("Amount in words: ${amountInWords(total)}", 9f, PdfSub, 1)
    pdf.space(4f)
    pdf.text(
        "This is an estimate, valid for 30 days. The final amount may change if the treatment needed changes.",
        9f, PdfSub
    )
    pdf.signature()
    return pdf.save(pdfFile(context, "Estimate", patient.name))
}
