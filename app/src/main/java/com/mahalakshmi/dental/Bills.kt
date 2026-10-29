package com.mahalakshmi.dental

import android.content.Context
import android.text.Layout
import java.io.File
import java.text.NumberFormat
import java.util.Locale
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

private val inr2 = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

/** Money with two decimals, e.g. ₹6,000.00 */
fun rupees2(x: Double): String = "₹" + inr2.format(x)

private fun treatmentText(t: Treatment, doctors: Map<Long, String>): String {
    val doctor = doctors[t.doctorId].orEmpty()
    return if (doctor.isBlank()) t.name else "${t.name}\n$doctor"
}

private fun patientRows(p: Patient) = listOf(
    "Name" to p.name,
    "Age / Sex" to listOf(if (p.age.isNotBlank()) "${p.age} yrs" else "", p.gender).filter { it.isNotBlank() }.joinToString(" / "),
    "Mobile" to p.phone,
    "Patient ID" to docNo("P-", p.id)
)

private fun consultantsOf(list: List<Treatment>, doctors: Map<Long, String>, db: ClinicDb): String =
    list.mapNotNull { doctors[it.doctorId]?.takeIf { n -> n.isNotBlank() } }.distinct().joinToString(", ")
        .ifBlank { db.mainDoctor()?.name.orEmpty() }

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
    val status = when {
        account.balance > 0.01 -> "Balance due"
        account.balance < -0.01 -> "Advance paid"
        else -> "Paid in full"
    }
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("INVOICE", "")
    pdf.partyBox(
        "PATIENT DETAILS", patientRows(patient),
        "INVOICE DETAILS", listOf(
            "Invoice No." to docNo("INV-", patient.id),
            "Date" to fmtDate(System.currentTimeMillis()),
            "Consultant" to consultantsOf(billed, doctors, db),
            "Status" to status
        )
    )
    val rows = if (billed.isEmpty()) {
        listOf(
            listOf(
                PdfCell("–", 0.07f, 9.5f, PdfSub), PdfCell("No treatments yet", 0.43f, 9.5f, PdfSub),
                PdfCell("–", 0.13f, 9.5f), PdfCell("–", 0.17f, 9f), PdfCell(rupees2(0.0), 0.2f, 9.5f, align = end)
            )
        )
    } else {
        billed.mapIndexed { i, t ->
            listOf(
                PdfCell("${i + 1}", 0.07f, 9.5f, PdfSub),
                PdfCell(treatmentText(t, doctors), 0.43f, 9.5f, PdfInk, 1),
                PdfCell(t.teethList.joinToString(", ").ifBlank { "–" }, 0.13f, 9.5f),
                PdfCell(fmtDate(if (t.doneAt > 0L) t.doneAt else t.createdAt), 0.17f, 9f, PdfSub),
                PdfCell(rupees2(t.fee), 0.2f, 9.5f, align = end)
            )
        }
    }
    pdf.table(
        listOf(
            PdfCell("Sr.", 0.07f, 9f, PdfSub, 1),
            PdfCell("Treatment / Procedure", 0.43f, 9f, PdfSub, 1),
            PdfCell("Tooth", 0.13f, 9f, PdfSub, 1),
            PdfCell("Date", 0.17f, 9f, PdfSub, 1),
            PdfCell("Amount", 0.2f, 9f, PdfSub, 1, end)
        ),
        rows
    )
    val modes = payments.map { it.mode }.filter { it.isNotBlank() }.distinct().joinToString(", ")
    pdf.summary(
        "AMOUNT IN WORDS", amountInWords(account.billed),
        if (modes.isNotBlank()) "Payment mode: $modes" else "",
        listOf(
            SummaryLine("Total Amount", rupees2(account.billed)),
            SummaryLine("Amount Paid", rupees2(account.paid), color = PdfGreen),
            SummaryLine(
                if (account.balance >= 0) "Balance Due" else "Advance Paid", rupees2(abs(account.balance)),
                strong = true, color = if (account.balance > 0.01) PdfRed else PdfGreen
            )
        )
    )
    if (payments.isNotEmpty()) {
        pdf.heading("Payment details")
        pdf.table(
            listOf(
                PdfCell("Date", 0.3f, 9f, PdfSub, 1),
                PdfCell("Receipt No.", 0.25f, 9f, PdfSub, 1),
                PdfCell("Mode", 0.2f, 9f, PdfSub, 1),
                PdfCell("Amount", 0.25f, 9f, PdfSub, 1, end)
            ),
            payments.map { p ->
                listOf(
                    PdfCell(fmtDate(p.date), 0.3f, 9.5f),
                    PdfCell(docNo("R-", p.id), 0.25f, 9.5f),
                    PdfCell(p.mode.ifBlank { "–" }, 0.2f, 9.5f),
                    PdfCell(if (p.amount < 0) "– " + rupees2(-p.amount) else rupees2(p.amount), 0.25f, 9.5f, align = end)
                )
            }
        )
    }
    pdf.notes(listOf("This is a computer-generated invoice.", "Please keep it for your records."))
    pdf.signature()
    return pdf.save(pdfFile(context, "Invoice", patient.name))
}

// ───────────── Payment receipt ─────────────
/** Receipt for one payment, with the account position after it. */
fun paymentReceiptPdf(context: Context, db: ClinicDb, paymentId: Long): File {
    val pay = db.getPaymentById(paymentId) ?: error("Payment not found")
    val patient = db.getPatient(pay.patientId) ?: error("Patient not found")
    val account = accountOf(db.treatmentsOf(pay.patientId), db.paymentsOf(pay.patientId))
    val clinic = db.clinicInfo()
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val pdf = PdfBuilder(context, clinic)
    pdf.title("PAYMENT RECEIPT", "")
    pdf.partyBox(
        "PATIENT DETAILS", patientRows(patient),
        "RECEIPT DETAILS", listOf(
            "Receipt No." to docNo("R-", pay.id),
            "Date" to fmtDate(pay.date),
            "Payment mode" to pay.mode,
            "Received by" to (db.mainDoctor()?.name ?: clinic.name)
        )
    )
    pdf.table(
        listOf(
            PdfCell("Sr.", 0.08f, 9f, PdfSub, 1),
            PdfCell("Description", 0.67f, 9f, PdfSub, 1),
            PdfCell("Amount", 0.25f, 9f, PdfSub, 1, end)
        ),
        listOf(
            listOf(
                PdfCell("1", 0.08f, 9.5f, PdfSub),
                PdfCell("Payment received towards dental treatment", 0.67f, 9.5f, PdfInk, 1),
                PdfCell(rupees2(abs(pay.amount)), 0.25f, 9.5f, PdfInk, 2, end)
            )
        )
    )
    pdf.summary(
        "AMOUNT RECEIVED IN WORDS", amountInWords(pay.amount), "",
        listOf(
            SummaryLine("Total treatment amount", rupees2(account.billed)),
            SummaryLine("Total paid till date", rupees2(account.paid), color = PdfGreen),
            SummaryLine(
                if (account.balance >= 0) "Balance Due" else "Advance Paid", rupees2(abs(account.balance)),
                strong = true, color = if (account.balance > 0.01) PdfRed else PdfGreen
            )
        )
    )
    pdf.notes(listOf("This is a computer-generated receipt."))
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
    val now = System.currentTimeMillis()
    val end = Layout.Alignment.ALIGN_OPPOSITE
    val pdf = PdfBuilder(context, db.clinicInfo())
    pdf.title("TREATMENT ESTIMATE", "")
    pdf.partyBox(
        "PATIENT DETAILS", patientRows(patient),
        "ESTIMATE DETAILS", listOf(
            "Estimate No." to docNo("EST-", patient.id),
            "Date" to fmtDate(now),
            "Valid till" to fmtDate(now + 30L * 24 * 60 * 60 * 1000),
            "Consultant" to consultantsOf(planned, doctors, db)
        )
    )
    val rows = if (planned.isEmpty()) {
        listOf(
            listOf(
                PdfCell("–", 0.07f, 9.5f, PdfSub), PdfCell("No planned treatments", 0.53f, 9.5f, PdfSub),
                PdfCell("–", 0.18f, 9.5f), PdfCell(rupees2(0.0), 0.22f, 9.5f, align = end)
            )
        )
    } else {
        planned.mapIndexed { i, t ->
            listOf(
                PdfCell("${i + 1}", 0.07f, 9.5f, PdfSub),
                PdfCell(treatmentText(t, doctors), 0.53f, 9.5f, PdfInk, 1),
                PdfCell(t.teethList.joinToString(", ").ifBlank { "–" }, 0.18f, 9.5f),
                PdfCell(rupees2(t.fee), 0.22f, 9.5f, align = end)
            )
        }
    }
    pdf.table(
        listOf(
            PdfCell("Sr.", 0.07f, 9f, PdfSub, 1),
            PdfCell("Planned Treatment", 0.53f, 9f, PdfSub, 1),
            PdfCell("Tooth", 0.18f, 9f, PdfSub, 1),
            PdfCell("Amount", 0.22f, 9f, PdfSub, 1, end)
        ),
        rows
    )
    pdf.summary(
        "ESTIMATED AMOUNT IN WORDS", amountInWords(total), "",
        listOf(SummaryLine("Estimated Total", rupees2(total), strong = true, color = PdfGreen))
    )
    pdf.notes(
        listOf(
            "This is an estimate. The final amount may change if the treatment needed changes.",
            "Valid for 30 days from the date above."
        )
    )
    pdf.signature()
    return pdf.save(pdfFile(context, "Estimate", patient.name))
}
