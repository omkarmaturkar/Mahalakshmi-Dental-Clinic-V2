package com.mahalakshmi.dental

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────── Colours used in PDFs ─────────────
val PdfGreen = 0xFF5A8F12.toInt()
val PdfInk = 0xFF1D2321.toInt()
val PdfSub = 0xFF69716D.toInt()
val PdfLine = 0xFFDCE3D6.toInt()
val PdfSoft = 0xFFF1F7E4.toInt()
val PdfRed = 0xFFD93A3F.toInt()
val PdfGold = 0xFFE09A00.toInt()
private val PdfDark = 0xFF1B3322.toInt()
private val PdfWhite = 0xFFFFFFFF.toInt()
private val PdfStripe = 0xFFF8FAF5.toInt()
private val PdfRedSoft = 0xFFFDEDED.toInt()

fun docNo(prefix: String, id: Long): String = prefix + id.toString().padStart(4, '0')

/** One cell in a table row. [width] is a share of the page width (0.5f = half). */
class PdfCell(
    val text: CharSequence,
    val width: Float,
    val size: Float = 10.5f,
    val color: Int = PdfInk,
    val weight: Int = 0,
    val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
)

/**
 * Draws A4 / A5 pages in the clinic's style: green header with logo, clinic name and doctors,
 * a big faint logo in the middle of every page, green table headers and a footer band.
 * A row drawn with fill = PdfSoft becomes a green header row with white text.
 */
class PdfBuilder(private val context: Context, private val clinic: ClinicInfo) {
    private val small = clinic.paper == "A5"
    private val pageW = if (small) 420 else 595
    private val pageH = if (small) 595 else 842
    private val k = if (small) 0.84f else 1f
    private val margin = if (small) 26f else 38f
    val left = margin
    val right = pageW - margin
    val width = right - left
    private val footerH = if (small) 40f else 48f
    private val bottom = pageH - footerH - 12f

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    private lateinit var canvas: Canvas
    private var y = 0f
    private var stripe = false
    private var totalsTop = -1f

    private val regular = font(R.font.poppins_regular, Typeface.DEFAULT)
    private val medium = font(R.font.poppins_medium, Typeface.DEFAULT)
    private val semibold = font(R.font.poppins_semibold, Typeface.DEFAULT_BOLD)
    private val bold = font(R.font.poppins_bold, Typeface.DEFAULT_BOLD)
    private val logo: Bitmap? = loadLogo()

    init {
        newPage()
    }

    private fun font(id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Throwable) { fallback }

    private fun loadLogo(): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.clinic_logo, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 700) sample *= 2
        BitmapFactory.decodeResource(context.resources, R.drawable.clinic_logo, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }

    /** weight: 0 regular, 1 medium, 2 semibold, 3 bold */
    fun paint(size: Float, color: Int = PdfInk, weight: Int = 0) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * k
        this.color = color
        typeface = when (weight) {
            3 -> bold
            2 -> semibold
            1 -> medium
            else -> regular
        }
    }

    private fun layout(text: CharSequence, paint: TextPaint, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w.toInt().coerceAtLeast(10))
            .setAlignment(align)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.1f)
            .build()

    private fun drawLayout(l: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        l.draw(canvas)
        canvas.restore()
    }

    private fun solid(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    fun ensure(h: Float) {
        if (y + h > bottom) newPage()
    }

    fun space(h: Float) {
        y += h
    }

    fun text(
        text: CharSequence, size: Float = 10.5f, color: Int = PdfInk, weight: Int = 0,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, after: Float = 4f
    ) {
        if (text.isBlank()) return
        val l = layout(text, paint(size, color, weight), width, align)
        ensure(l.height.toFloat())
        drawLayout(l, left, y)
        y += l.height + after
    }

    fun row(cells: List<PdfCell>, fill: Int? = null, pad: Float = 6f, after: Float = 0f, line: Boolean = false) {
        val header = fill == PdfSoft
        val layouts = cells.map { c ->
            val color = if (header) PdfWhite else c.color
            val weight = if (header) 2 else c.weight
            layout(c.text, paint(c.size, color, weight), c.width * width - 12f, c.align)
        }
        val h = (layouts.maxOfOrNull { it.height } ?: 0) + pad * 2
        ensure(h)
        when {
            header -> {
                canvas.drawRoundRect(RectF(left, y, right, y + h), 8f, 8f, solid(PdfGreen))
                stripe = false
            }
            fill != null -> canvas.drawRect(left, y, right, y + h, solid(fill))
            line -> {
                if (stripe) canvas.drawRect(left, y, right, y + h, solid(PdfStripe))
                stripe = !stripe
            }
        }
        var x = left
        cells.forEachIndexed { i, c ->
            drawLayout(layouts[i], x + 6f, y + pad)
            x += c.width * width
        }
        y += h
        if (line) canvas.drawRect(left, y - 0.6f, right, y, solid(PdfLine))
        y += after
        totalsTop = -1f
    }

    fun title(title: String, info: String) {
        val tl = layout(title, paint(17f, PdfGreen, 3).apply { letterSpacing = 0.1f }, width * 0.6f)
        val il = layout(info, paint(9.5f, PdfInk, 1), width * 0.4f, Layout.Alignment.ALIGN_OPPOSITE)
        val h = maxOf(tl.height + 9f, il.height.toFloat())
        ensure(h + 14f)
        drawLayout(tl, left, y)
        canvas.drawRoundRect(RectF(left, y + tl.height + 3f, left + 44f, y + tl.height + 6.5f), 2f, 2f, solid(PdfGold))
        drawLayout(il, left + width * 0.6f, y)
        y += h + 14f
        totalsTop = -1f
    }

    fun patientBox(p: Patient) {
        val details = listOf(
            listOf(p.gender, if (p.age.isNotBlank()) "${p.age} yrs" else "").filter { it.isNotBlank() }.joinToString(" / "),
            p.phone
        ).filter { it.isNotBlank() }.joinToString("   ·   ")
        val textW = width * 0.62f
        val idW = width * 0.3f
        val cap = paint(7.5f, PdfGreen, 2).apply { letterSpacing = 0.16f }
        val label = layout("PATIENT", cap, textW)
        val name = layout(p.name, paint(13.5f, PdfInk, 2), textW)
        val det = if (details.isNotBlank()) layout(details, paint(9.5f, PdfSub), textW) else null
        val idLabel = layout("PATIENT ID", cap, idW, Layout.Alignment.ALIGN_OPPOSITE)
        val id = layout(docNo("P-", p.id), paint(12f, PdfInk, 2), idW, Layout.Alignment.ALIGN_OPPOSITE)
        val pad = 12f
        val leftH = label.height + 2 + name.height + (if (det != null) det.height + 2 else 0)
        val rightH = idLabel.height + 2 + id.height
        val h = maxOf(leftH, rightH) + pad * 2
        ensure(h + 14f)
        canvas.drawRoundRect(RectF(left, y, right, y + h), 12f, 12f, solid(PdfSoft))
        canvas.drawRoundRect(RectF(left, y, left + 5f, y + h), 3f, 3f, solid(PdfGreen))
        var ty = y + pad
        drawLayout(label, left + 18f, ty)
        ty += label.height + 2
        drawLayout(name, left + 18f, ty)
        ty += name.height + 2
        if (det != null) drawLayout(det, left + 18f, ty)
        val idX = right - 14f - idW
        drawLayout(idLabel, idX, y + pad)
        drawLayout(id, idX, y + pad + idLabel.height + 2)
        y += h + 14f
    }

    /** A line in the totals block on the right. The strong line (balance) is highlighted. */
    fun total(label: String, value: String, color: Int = PdfInk, strong: Boolean = false) {
        val boxW = width * 0.5f
        val x0 = right - boxW
        val ll = layout(
            label, paint(if (strong) 11f else 9.5f, if (strong) PdfInk else PdfSub, if (strong) 2 else 0), boxW * 0.55f - 12f
        )
        val vl = layout(
            value, paint(if (strong) 14f else 10.5f, color, if (strong) 3 else 1), boxW * 0.45f - 12f,
            Layout.Alignment.ALIGN_OPPOSITE
        )
        val pad = if (strong) 10f else 4f
        val inner = maxOf(ll.height, vl.height).toFloat()
        val h = inner + pad * 2
        ensure(h + 4f)
        if (totalsTop < 0f) totalsTop = y
        if (strong) {
            y += 4f
            val bg = when (color) {
                PdfRed -> PdfRedSoft
                PdfGreen -> PdfSoft
                else -> PdfStripe
            }
            canvas.drawRoundRect(RectF(x0, y, right, y + h), 10f, 10f, solid(bg))
            canvas.drawRoundRect(RectF(x0, y, x0 + 4f, y + h), 2f, 2f, solid(color))
        }
        drawLayout(ll, x0 + 12f, y + pad + (inner - ll.height) / 2f)
        drawLayout(vl, x0 + boxW * 0.55f, y + pad + (inner - vl.height) / 2f)
        y += h
    }

    /** A tilted stamp such as "PAID", drawn to the left of the totals. */
    fun stamp(text: String, color: Int) {
        val top = if (totalsTop >= 0f) totalsTop else y - 50f
        val cx = left + width * 0.22f
        val cy = (top + y) / 2f
        val p = paint(24f, color, 3).apply {
            letterSpacing = 0.2f
            alpha = 215
        }
        val tw = p.measureText(text)
        val bw = tw + 34f
        val bh = p.textSize + 20f
        val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            this.color = color
            alpha = 200
        }
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            this.color = color
            alpha = 170
        }
        canvas.save()
        canvas.rotate(-12f, cx, cy)
        canvas.drawRoundRect(rect, 9f, 9f, outer)
        canvas.drawRoundRect(RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.bottom - 4f), 6f, 6f, inner)
        val fm = p.fontMetrics
        canvas.drawText(text, cx - tw / 2f, cy - (fm.ascent + fm.descent) / 2f, p)
        canvas.restore()
    }

    /** Signature block of the main doctor (the first doctor shown on bills). */
    fun signature() {
        val doctors = clinic.doctor.split("  ·  ").map { it.trim() }.filter { it.isNotBlank() }
        val first = doctors.firstOrNull().orEmpty()
        val name = first.substringBefore(",").trim()
        val reg = when {
            clinic.regNo.isBlank() -> ""
            ":" !in clinic.regNo -> if (doctors.size <= 1) clinic.regNo else ""
            else -> clinic.regNo.split("  ·  ").firstOrNull { it.startsWith("$name:") }?.substringAfter(":")?.trim().orEmpty()
        }
        val lines = listOf(first, if (reg.isNotBlank()) "Reg. No. $reg" else "").filter { it.isNotBlank() }
        val w = width * 0.4f
        val nameL = layout(lines.joinToString("\n").ifBlank { "Doctor" }, paint(9.5f, PdfInk, 2), w, Layout.Alignment.ALIGN_CENTER)
        val labelL = layout("Signature", paint(8f, PdfSub), w, Layout.Alignment.ALIGN_CENTER)
        ensure(52f + nameL.height + labelL.height)
        y += 40f
        canvas.drawRect(right - w, y, right, y + 0.9f, solid(PdfSub))
        y += 4f
        drawLayout(labelL, right - w, y)
        y += labelL.height + 2f
        drawLayout(nameL, right - w, y)
        y += nameL.height + 6f
    }

    private fun watermark() {
        val bmp = logo ?: return
        val boxW = pageW * 0.6f
        val boxH = pageH * 0.42f
        val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        var w = boxW
        var h = w / ratio
        if (h > boxH) {
            h = boxH
            w = h * ratio
        }
        val cx = pageW / 2f
        val cy = pageH / 2f + 30f
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = 20 }
        canvas.drawBitmap(bmp, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), p)
    }

    private fun letterhead() {
        val pad = if (small) 16f else 22f
        val logoSize = if (small) 50f else 62f
        val hasLogo = logo != null
        val textX = if (hasLogo) margin + logoSize + 14f else margin
        val sideW = width * 0.33f
        val mainW = right - sideW - 14f - textX
        val nameL = layout(clinic.name.uppercase(), paint(17f, PdfWhite, 3).apply { letterSpacing = 0.04f }, mainW)
        val doctorL = if (clinic.doctor.isNotBlank()) layout(clinic.doctor, paint(9.5f, 0xFFE3F1CC.toInt(), 1), mainW) else null
        val regL = if (clinic.regNo.isNotBlank()) layout("Reg. No. ${clinic.regNo}", paint(8f, 0xCCFFFFFF.toInt()), mainW) else null
        val contacts = listOf(
            if (clinic.phone.isNotBlank()) "Ph: ${clinic.phone}" else "",
            clinic.email, clinic.address, clinic.timings
        ).filter { it.isNotBlank() }
        val contactL = if (contacts.isNotEmpty()) {
            layout(contacts.joinToString("\n"), paint(8.3f, 0xEEFFFFFF.toInt()), sideW, Layout.Alignment.ALIGN_OPPOSITE)
        } else null
        val mainH = nameL.height + (if (doctorL != null) doctorL.height + 4 else 0) + (if (regL != null) regL.height + 2 else 0)
        val bandH = maxOf(if (hasLogo) logoSize else 0f, mainH.toFloat(), (contactL?.height ?: 0).toFloat()) + pad * 2

        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, pageW.toFloat(), bandH, PdfDark, PdfGreen, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, pageW.toFloat(), bandH, band)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = 0x26FFFFFF
        }
        canvas.drawCircle(pageW - 40f, 0f, bandH * 0.85f, ring)
        canvas.drawCircle(pageW - 40f, 0f, bandH * 1.3f, ring)
        canvas.drawRect(0f, bandH, pageW.toFloat(), bandH + 3f, solid(PdfGold))

        if (hasLogo) {
            val tileTop = (bandH - logoSize) / 2f
            canvas.drawRoundRect(RectF(margin, tileTop, margin + logoSize, tileTop + logoSize), 14f, 14f, solid(PdfWhite))
            logo?.let { bmp ->
                val inset = 5f
                val box = logoSize - inset * 2
                val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                val w = if (ratio >= 1f) box else box * ratio
                val h = if (ratio >= 1f) box / ratio else box
                val lx = margin + inset + (box - w) / 2f
                val ly = tileTop + inset + (box - h) / 2f
                canvas.drawBitmap(bmp, null, RectF(lx, ly, lx + w, ly + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
        }
        var ty = (bandH - mainH) / 2f
        drawLayout(nameL, textX, ty)
        ty += nameL.height
        if (doctorL != null) {
            ty += 4f
            drawLayout(doctorL, textX, ty)
            ty += doctorL.height
        }
        if (regL != null) {
            ty += 2f
            drawLayout(regL, textX, ty)
        }
        if (contactL != null) drawLayout(contactL, right - sideW, (bandH - contactL.height) / 2f)
        y = bandH + 3f + (if (small) 16f else 22f)
    }

    private fun footer() {
        val top = pageH - footerH
        canvas.drawRect(0f, top, pageW.toFloat(), pageH.toFloat(), solid(PdfSoft))
        val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, pageW.toFloat(), 0f, PdfDark, PdfGreen, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, pageH - 6f, pageW.toFloat(), pageH.toFloat(), strip)
        val area = footerH - 6f
        val pnW = 60f
        if (clinic.footer.isNotBlank()) {
            val l = layout(clinic.footer, paint(8.5f, PdfGreen, 1), width - pnW - 10f)
            drawLayout(l, left, top + (area - l.height) / 2f)
        }
        val pn = layout("Page $pageNo", paint(8.5f, PdfSub), pnW, Layout.Alignment.ALIGN_OPPOSITE)
        drawLayout(pn, right - pnW, top + (area - pn.height) / 2f)
    }

    private fun newPage() {
        page?.let {
            footer()
            doc.finishPage(it)
        }
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        page = p
        canvas = p.canvas
        stripe = false
        totalsTop = -1f
        watermark()
        letterhead()
    }

    fun save(file: File): File {
        page?.let {
            footer()
            doc.finishPage(it)
        }
        page = null
        file.parentFile?.mkdirs()
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }
}

/** A new PDF file in the app's cache. PDFs older than a day are cleaned up. */
fun pdfFile(context: Context, kind: String, who: String): File {
    val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
    val now = System.currentTimeMillis()
    dir.listFiles()?.forEach { if (now - it.lastModified() > 24L * 60 * 60 * 1000) it.delete() }
    val safe = who.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Patient" }.take(30)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
    return File(dir, "${kind}_${safe}_$stamp.pdf")
}

/** Sample page for "Preview letterhead" in Clinic & doctors. */
fun letterheadSamplePdf(context: Context, clinic: ClinicInfo): File {
    val pdf = PdfBuilder(context, clinic)
    val end = Layout.Alignment.ALIGN_OPPOSITE
    pdf.title("SAMPLE INVOICE", "Invoice No. INV-0001\nDate: ${fmtDate(System.currentTimeMillis())}")
    pdf.patientBox(Patient(id = 1, name = "Sample Patient", phone = "9876543210", age = "35", gender = "Female"))
    pdf.row(
        listOf(
            PdfCell("#", 0.08f, 9.5f, PdfSub, 1),
            PdfCell("Treatment", 0.5f, 9.5f, PdfSub, 1),
            PdfCell("Tooth", 0.17f, 9.5f, PdfSub, 1),
            PdfCell("Amount", 0.25f, 9.5f, PdfSub, 1, end)
        ),
        fill = PdfSoft
    )
    pdf.row(
        listOf(
            PdfCell("1", 0.08f, 10f, PdfSub),
            PdfCell("Root Canal", 0.5f, 10f, PdfInk, 1),
            PdfCell("36", 0.17f, 10f),
            PdfCell(rs(6000.0), 0.25f, 10f, align = end)
        ),
        pad = 7f, line = true
    )
    pdf.row(
        listOf(
            PdfCell("2", 0.08f, 10f, PdfSub),
            PdfCell("Cleaning", 0.5f, 10f, PdfInk, 1),
            PdfCell("–", 0.17f, 10f),
            PdfCell(rs(1200.0), 0.25f, 10f, align = end)
        ),
        pad = 7f, line = true
    )
    pdf.space(8f)
    pdf.total("Total", rs(7200.0))
    pdf.total("Paid", rs(7200.0), PdfGreen)
    pdf.total("Balance due", rs(0.0), PdfGreen, strong = true)
    pdf.stamp("PAID", PdfGreen)
    pdf.signature()
    return pdf.save(pdfFile(context, "Letterhead", "Sample"))
}

// ───────────── Share, open, print ─────────────
private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun pdfUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun whatsAppNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.length == 10 -> "91$digits"
        digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
        else -> digits
    }
}

/** Opens WhatsApp (or WhatsApp Business) with the PDF attached, straight to the patient's chat when possible. */
fun sharePdfOnWhatsApp(context: Context, file: File, phone: String, message: String) {
    val uri = try {
        pdfUri(context, file)
    } catch (e: Exception) {
        toast(context, "Could not share the PDF")
        return
    }
    val number = whatsAppNumber(phone)
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            setPackage(pkg)
            putExtra(Intent.EXTRA_STREAM, uri)
            if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
            if (number.length >= 10) putExtra("jid", "$number@s.whatsapp.net")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(send)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next WhatsApp app
        }
    }
    toast(context, "WhatsApp is not installed")
    sharePdf(context, file, message)
}

fun sharePdf(context: Context, file: File, message: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri(context, file))
            if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share PDF"))
    } catch (e: Exception) {
        toast(context, "Could not share the PDF")
    }
}

fun openPdf(context: Context, file: File) {
    try {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri(context, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No PDF viewer found – choose an app")
        sharePdf(context, file, "")
    } catch (e: Exception) {
        toast(context, "Could not open the PDF")
    }
}

fun printPdf(context: Context, file: File) {
    try {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(file.nameWithoutExtension, PdfPrintAdapter(file), null)
    } catch (e: Exception) {
        toast(context, "Printing is not available on this device")
    }
}

private class PdfPrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.WriteResultCallback
    ) {
        try {
            file.inputStream().use { input ->
                FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

// ───────────── "PDF ready" window ─────────────
@Composable
fun PdfActionsDialog(
    title: String,
    subtitle: String,
    phone: String,
    message: String,
    build: (Context) -> File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val made = withContext(Dispatchers.IO) { runCatching { build(context) }.getOrNull() }
        if (made != null) file = made else failed = true
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color.White).padding(26.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                Icon(DocIcons.Pdf, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Brand.Ink)
            Text(subtitle, fontSize = 13.sp, color = Brand.Sub)
            Spacer(Modifier.height(20.dp))
            val ready = file
            if (failed) {
                Text("Could not create the PDF. Please try again.", fontSize = 14.sp, color = Brand.Red)
            } else if (ready == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Brand.GreenDeep, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Preparing PDF…", fontSize = 15.sp, color = Brand.Ink)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton("Send on WhatsApp", Modifier.fillMaxWidth(), AppIcons.Chat) {
                        sharePdfOnWhatsApp(context, ready, phone, message)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SoftButton("Print", DocIcons.Print, Modifier.weight(1f)) { printPdf(context, ready) }
                        SoftButton("Open", DocIcons.Pdf, Modifier.weight(1f)) { openPdf(context, ready) }
                    }
                    SoftButton("Share or save", Icons.Filled.Share, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Ink) {
                        sharePdf(context, ready, message)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SoftButton("Close", null, Modifier.fillMaxWidth(), bg = Brand.Soft, fg = Brand.Sub, onClick = onDismiss)
        }
    }
}

// ───────────── Icons ─────────────
private fun docIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

object DocIcons {
    val Pdf: ImageVector by lazy {
        docIcon(
            "pdf",
            "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM11.5,9.5c0,0.83 -0.67,1.5 -1.5,1.5H9v2H7.5V7H10c0.83,0 1.5,0.67 1.5,1.5v1zM16.5,11.5c0,0.83 -0.67,1.5 -1.5,1.5h-2.5V7H15c0.83,0 1.5,0.67 1.5,1.5v3zM20.5,8.5H19v1h1.5V11H19v2h-1.5V7h3v1.5zM9,9.5h1v-1H9v1zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM14,11.5h1v-3h-1v3z"
        )
    }
    val Print: ImageVector by lazy {
        docIcon(
            "print",
            "M19,8H5c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19H8v-5h8v5zM19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3H6v4h12V3z"
        )
    }
    val Receipt: ImageVector by lazy {
        docIcon(
            "receipt",
            "M18,17H6v-2h12v2zM18,13H6v-2h12v2zM18,9H6V7h12v2zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22V2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2 7.5,3.5 6,2 4.5,3.5 3,2v20z"
        )
    }
    val Pill: ImageVector by lazy {
        docIcon(
            "pill",
            "M6,3h12v2H6V3zM17,6H7C5.9,6 5,6.9 5,8v11c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V8C19,6.9 18.1,6 17,6zM15,15.5h-2v2h-2v-2H9v-2h2v-2h2v2h2V15.5z"
        )
    }
    val Settings: ImageVector get() = Icons.Filled.Settings
}
