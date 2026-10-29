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
private val PdfGreenDark = 0xFF2F5A0E.toInt()
private val PdfWhite = 0xFFFFFFFF.toInt()
private val PdfBorder = 0xFFC9D3C1.toInt()
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

/** One line in the totals box (for example "Balance Due"). */
class SummaryLine(val label: String, val value: String, val strong: Boolean = false, val color: Int = PdfInk)

/**
 * Professional A4 / A5 pages: white letterhead with logo and doctors, a big faint logo in the middle,
 * bordered tables, a summary box, and the signature in the bottom-right corner of the last page.
 * A row drawn with fill = PdfSoft becomes a green header row with white text.
 */
class PdfBuilder(private val context: Context, private val clinic: ClinicInfo) {
    private val small = clinic.paper == "A5"
    private val pageW = if (small) 420 else 595
    private val pageH = if (small) 595 else 842
    private val k = if (small) 0.84f else 1f
    private val margin = if (small) 26f else 36f
    val left = margin
    val right = pageW - margin
    val width = right - left
    private val footerH = if (small) 34f else 40f
    private val bottom = pageH - footerH - 10f

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    private lateinit var canvas: Canvas
    private var y = 0f
    private var wantSignature = false

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

    private fun stroke(color: Int = PdfBorder, w: Float = 0.7f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = w
    }

    private fun gradient(x1: Float, y1: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, x1, y1, PdfDark, PdfGreen, Shader.TileMode.CLAMP)
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

    /** Small green heading, e.g. "PAYMENT DETAILS". */
    fun heading(text: String) {
        val l = layout(text.uppercase(), paint(8.5f, PdfGreenDark, 2).apply { letterSpacing = 0.14f }, width)
        ensure(l.height + 34f)
        drawLayout(l, left, y)
        y += l.height + 6f
    }

    fun notes(lines: List<String>) {
        lines.filter { it.isNotBlank() }.forEach { text("•  $it", 8.5f, PdfSub, after = 2f) }
    }

    private fun rowHeight(cells: List<PdfCell>, pad: Float): Float =
        (cells.maxOfOrNull { c -> layout(c.text, paint(c.size, c.color, c.weight), c.width * width - 12f, c.align).height } ?: 0) + pad * 2

    /** One table row. fill = PdfSoft makes a green header row; line = true draws cell borders. */
    fun row(cells: List<PdfCell>, fill: Int? = null, pad: Float = 6f, after: Float = 0f, line: Boolean = false) {
        val header = fill == PdfSoft
        val layouts = cells.map { c ->
            layout(c.text, paint(c.size, if (header) PdfWhite else c.color, if (header) 2 else c.weight), c.width * width - 12f, c.align)
        }
        val h = (layouts.maxOfOrNull { it.height } ?: 0) + pad * 2
        ensure(h)
        if (header) {
            canvas.drawRect(left, y, right, y + h, solid(PdfGreen))
        } else if (fill != null) {
            canvas.drawRect(left, y, right, y + h, solid(fill))
        }
        var x = left
        cells.forEachIndexed { i, c ->
            val w = c.width * width
            drawLayout(layouts[i], x + 6f, y + pad)
            if (header || line) canvas.drawRect(x, y, x + w, y + h, stroke())
            x += w
        }
        y += h + after
    }

    /** A fully bordered table. The header row repeats if the table continues on the next page. */
    fun table(headers: List<PdfCell>, rows: List<List<PdfCell>>) {
        row(headers, fill = PdfSoft, pad = 7f)
        rows.forEach { cells ->
            if (y + rowHeight(cells, 7f) > bottom) {
                newPage()
                row(headers, fill = PdfSoft, pad = 7f)
            }
            row(cells, pad = 7f, line = true)
        }
        y += 12f
    }

    /** Document title in a light band, e.g. INVOICE. With [info], the info is shown on the right. */
    fun title(title: String, info: String) {
        val tp = paint(15f, PdfGreen, 3).apply { letterSpacing = 0.22f }
        if (info.isBlank()) {
            val tl = layout(title, tp, width, Layout.Alignment.ALIGN_CENTER)
            val h = tl.height + 14f
            ensure(h + 12f)
            canvas.drawRect(left, y, right, y + h, solid(PdfSoft))
            canvas.drawRect(left, y, right, y + 0.9f, solid(PdfGreen))
            canvas.drawRect(left, y + h - 0.9f, right, y + h, solid(PdfGreen))
            drawLayout(tl, left, y + 7f)
            y += h + 12f
        } else {
            val tl = layout(title, tp, width * 0.55f)
            val il = layout(info, paint(9f, PdfInk, 1), width * 0.45f - 12f, Layout.Alignment.ALIGN_OPPOSITE)
            val h = maxOf(tl.height, il.height) + 14f
            ensure(h + 12f)
            canvas.drawRect(left, y, right, y + h, solid(PdfSoft))
            canvas.drawRect(left, y, right, y + 0.9f, solid(PdfGreen))
            canvas.drawRect(left, y + h - 0.9f, right, y + h, solid(PdfGreen))
            drawLayout(tl, left + 12f, y + (h - tl.height) / 2f)
            drawLayout(il, left + width * 0.55f, y + (h - il.height) / 2f)
            y += h + 12f
        }
    }

    /** Two bordered boxes side by side, e.g. PATIENT DETAILS and INVOICE DETAILS. */
    fun partyBox(
        leftTitle: String,
        leftRows: List<Pair<String, String>>,
        rightTitle: String,
        rightRows: List<Pair<String, String>>
    ) {
        val half = width / 2f
        val labelW = half * 0.38f
        val valueW = half - labelW - 18f
        val cap = paint(7.5f, PdfGreenDark, 2).apply { letterSpacing = 0.14f }
        val tl = layout(leftTitle, cap, half - 16f)
        val tr = layout(rightTitle, cap, half - 16f)
        val bandH = maxOf(tl.height, tr.height) + 10f
        fun rowsOf(rows: List<Pair<String, String>>) = rows.mapIndexed { i, (label, value) ->
            layout(label, paint(8.8f, PdfSub), labelW - 6f) to
                layout(value.ifBlank { "–" }, paint(9.5f, PdfInk, if (i == 0) 2 else 1), valueW)
        }
        val l = rowsOf(leftRows)
        val r = rowsOf(rightRows)
        fun heightOf(list: List<Pair<StaticLayout, StaticLayout>>): Float =
            list.sumOf { (a, b) -> maxOf(a.height, b.height) + 4 }.toFloat()
        val h = bandH + maxOf(heightOf(l), heightOf(r)) + 12f
        ensure(h + 12f)
        canvas.drawRect(left, y, right, y + bandH, solid(PdfSoft))
        canvas.drawRect(left, y, right, y + h, stroke())
        canvas.drawLine(left, y + bandH, right, y + bandH, stroke())
        canvas.drawLine(left + half, y, left + half, y + h, stroke())
        drawLayout(tl, left + 10f, y + 5f)
        drawLayout(tr, left + half + 10f, y + 5f)
        fun drawRows(list: List<Pair<StaticLayout, StaticLayout>>, x: Float) {
            var ty = y + bandH + 7f
            list.forEach { (a, b) ->
                drawLayout(a, x + 10f, ty)
                drawLayout(b, x + 10f + labelW, ty)
                ty += maxOf(a.height, b.height) + 4
            }
        }
        drawRows(l, left)
        drawRows(r, left + half)
        y += h + 12f
    }

    /** Patient details in one bordered row (used on prescriptions). */
    fun patientBox(p: Patient) {
        val fields = listOf(
            "PATIENT NAME" to p.name,
            "AGE / SEX" to listOf(if (p.age.isNotBlank()) "${p.age} yrs" else "", p.gender).filter { it.isNotBlank() }.joinToString(" / "),
            "MOBILE" to p.phone,
            "PATIENT ID" to docNo("P-", p.id)
        )
        val widths = listOf(0.38f, 0.2f, 0.22f, 0.2f)
        val cap = paint(7.2f, PdfGreenDark, 2).apply { letterSpacing = 0.12f }
        val cells = fields.mapIndexed { i, (label, value) ->
            layout(label, cap, widths[i] * width - 16f) to
                layout(value.ifBlank { "–" }, paint(if (i == 0) 10.5f else 9.5f, PdfInk, if (i == 0) 2 else 1), widths[i] * width - 16f)
        }
        val h = (cells.maxOfOrNull { (a, b) -> a.height + 3 + b.height } ?: 0) + 16f
        ensure(h + 12f)
        canvas.drawRect(left, y, right, y + h, solid(PdfSoft))
        var x = left
        cells.forEachIndexed { i, (a, b) ->
            val w = widths[i] * width
            drawLayout(a, x + 8f, y + 8f)
            drawLayout(b, x + 8f, y + 8f + a.height + 3)
            canvas.drawRect(x, y, x + w, y + h, stroke())
            x += w
        }
        y += h + 12f
    }

    /** Amount in words on the left, totals on the right, directly under the table. */
    fun summary(wordsTitle: String, words: String, note: String, totals: List<SummaryLine>) {
        val leftW = width * 0.55f
        val rightW = width - leftW
        val x0 = left + leftW
        val cap = paint(7.5f, PdfGreenDark, 2).apply { letterSpacing = 0.14f }
        val wt = layout(wordsTitle, cap, leftW - 20f)
        val wl = layout(words, paint(9.8f, PdfInk, 1), leftW - 20f)
        val nl = if (note.isNotBlank()) layout(note, paint(8.5f, PdfSub), leftW - 20f) else null
        val leftH = 10f + wt.height + 4 + wl.height + (if (nl != null) nl.height + 8 else 0) + 10f
        val rows = totals.map { t ->
            Triple(
                layout(t.label, paint(if (t.strong) 10f else 9.2f, if (t.strong) PdfInk else PdfSub, if (t.strong) 2 else 0), rightW * 0.55f - 12f),
                layout(t.value, paint(if (t.strong) 11.5f else 9.8f, t.color, if (t.strong) 3 else 1), rightW * 0.45f - 12f, Layout.Alignment.ALIGN_OPPOSITE),
                t
            )
        }
        val rowHs = rows.map { (a, b, _) -> maxOf(a.height, b.height) + 14f }.toMutableList()
        val h = maxOf(leftH, rowHs.sum())
        if (rowHs.isNotEmpty()) rowHs[rowHs.lastIndex] += h - rowHs.sum()
        ensure(h + 12f)
        var ry = y
        rows.forEachIndexed { i, (_, _, t) ->
            if (t.strong) canvas.drawRect(x0, ry, right, ry + rowHs[i], solid(if (t.color == PdfRed) PdfRedSoft else PdfSoft))
            ry += rowHs[i]
        }
        canvas.drawRect(left, y, right, y + h, stroke())
        canvas.drawLine(x0, y, x0, y + h, stroke())
        var ly = y + 10f
        drawLayout(wt, left + 10f, ly)
        ly += wt.height + 4
        drawLayout(wl, left + 10f, ly)
        ly += wl.height + 8
        if (nl != null) drawLayout(nl, left + 10f, ly)
        ry = y
        rows.forEachIndexed { i, (a, b, _) ->
            if (i > 0) canvas.drawLine(x0, ry, right, ry, stroke())
            val rh = rowHs[i]
            drawLayout(a, x0 + 10f, ry + (rh - a.height) / 2f)
            drawLayout(b, x0 + rightW * 0.55f, ry + (rh - b.height) / 2f)
            ry += rh
        }
        y += h + 12f
    }

    /** Older style single total line (kept for compatibility). */
    fun total(label: String, value: String, color: Int = PdfInk, strong: Boolean = false) {
        summary("", "", "", listOf(SummaryLine(label, value, strong, color)))
    }

    /** Stamps are switched off: documents stay clean. */
    @Suppress("UNUSED_PARAMETER")
    fun stamp(text: String, color: Int) {
        // Intentionally empty
    }

    /** The signature is drawn in the bottom-right corner of the last page. */
    fun signature() {
        wantSignature = true
    }

    private fun drawSignature() {
        val doctors = clinic.doctor.split("  ·  ").map { it.trim() }.filter { it.isNotBlank() }
        val first = doctors.firstOrNull().orEmpty()
        val name = first.substringBefore(",").trim()
        val reg = when {
            clinic.regNo.isBlank() -> ""
            ":" !in clinic.regNo -> if (doctors.size <= 1) clinic.regNo else ""
            else -> clinic.regNo.split("  ·  ").firstOrNull { it.startsWith("$name:") }?.substringAfter(":")?.trim().orEmpty()
        }
        val w = width * 0.42f
        val center = Layout.Alignment.ALIGN_CENTER
        val forL = layout("For ${clinic.name}", paint(8.5f, PdfSub, 1), w, center)
        val nameL = layout(
            listOf(first, if (reg.isNotBlank()) "Reg. No. $reg" else "").filter { it.isNotBlank() }.joinToString("\n").ifBlank { "Doctor" },
            paint(9.5f, PdfInk, 2), w, center
        )
        val authL = layout("Authorised Signatory", paint(8f, PdfSub), w, center)
        val gap = if (small) 26f else 34f
        val sigH = forL.height + gap + 6f + nameL.height + 2f + authL.height
        if (y + 10f > bottom - sigH) newPage()
        val x = right - w
        var sy = bottom - sigH
        drawLayout(forL, x, sy)
        sy += forL.height + gap
        canvas.drawRect(x + 16f, sy, right - 16f, sy + 0.9f, solid(PdfSub))
        sy += 6f
        drawLayout(nameL, x, sy)
        sy += nameL.height + 2f
        drawLayout(authL, x, sy)
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
        canvas.drawRect(0f, 0f, pageW.toFloat(), 6f, gradient(pageW.toFloat(), 0f))
        val top = if (small) 18f else 24f
        val logoSize = if (small) 48f else 60f
        val hasLogo = logo != null
        val textX = if (hasLogo) margin + logoSize + 14f else margin
        val sideW = width * 0.34f
        val mainW = right - sideW - 14f - textX
        val nameL = layout(clinic.name.uppercase(), paint(17f, PdfGreenDark, 3).apply { letterSpacing = 0.03f }, mainW)
        val doctorL = if (clinic.doctor.isNotBlank()) layout(clinic.doctor, paint(9.5f, PdfInk, 1), mainW) else null
        val regL = if (clinic.regNo.isNotBlank()) layout("Reg. No. ${clinic.regNo}", paint(8f, PdfSub), mainW) else null
        val contacts = listOf(
            if (clinic.phone.isNotBlank()) "Ph: ${clinic.phone}" else "",
            if (clinic.email.isNotBlank()) "Email: ${clinic.email}" else "",
            clinic.address, clinic.timings
        ).filter { it.isNotBlank() }
        val contactL = if (contacts.isNotEmpty()) {
            layout(contacts.joinToString("\n"), paint(8.3f, PdfSub), sideW, Layout.Alignment.ALIGN_OPPOSITE)
        } else null
        val mainH = nameL.height + (if (doctorL != null) doctorL.height + 3 else 0) + (if (regL != null) regL.height + 2 else 0)
        val headH = maxOf(if (hasLogo) logoSize else 0f, mainH.toFloat(), (contactL?.height ?: 0).toFloat())

        logo?.let { bmp ->
            val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
            val w = if (ratio >= 1f) logoSize else logoSize * ratio
            val h = if (ratio >= 1f) logoSize / ratio else logoSize
            val ly = top + (headH - h) / 2f
            canvas.drawBitmap(bmp, null, RectF(margin, ly, margin + w, ly + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        var ty = top + (headH - mainH) / 2f
        drawLayout(nameL, textX, ty)
        ty += nameL.height
        if (doctorL != null) {
            ty += 3f
            drawLayout(doctorL, textX, ty)
            ty += doctorL.height
        }
        if (regL != null) {
            ty += 2f
            drawLayout(regL, textX, ty)
        }
        if (contactL != null) drawLayout(contactL, right - sideW, top + (headH - contactL.height) / 2f)

        y = top + headH + 10f
        canvas.drawRect(left, y, right, y + 1.6f, solid(PdfGreen))
        y += 3.2f
        canvas.drawRect(left, y, right, y + 0.6f, solid(PdfGold))
        y += 0.6f + (if (small) 12f else 16f)
    }

    private fun footer() {
        val top = pageH - footerH
        canvas.drawRect(left, top, right, top + 0.8f, solid(PdfGreen))
        val area = footerH - 8f
        val pnW = 60f
        if (clinic.footer.isNotBlank()) {
            val l = layout(clinic.footer, paint(8.3f, PdfGreenDark, 1), width - pnW - 10f)
            drawLayout(l, left, top + 2f + (area - l.height) / 2f)
        }
        val pn = layout("Page $pageNo", paint(8.3f, PdfSub), pnW, Layout.Alignment.ALIGN_OPPOSITE)
        drawLayout(pn, right - pnW, top + 2f + (area - pn.height) / 2f)
        canvas.drawRect(0f, pageH - 5f, pageW.toFloat(), pageH.toFloat(), gradient(pageW.toFloat(), 0f))
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
        watermark()
        letterhead()
    }

    fun save(file: File): File {
        if (wantSignature) drawSignature()
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
    val today = fmtDate(System.currentTimeMillis())
    pdf.title("INVOICE", "")
    pdf.partyBox(
        "PATIENT DETAILS",
        listOf("Name" to "Sample Patient", "Age / Sex" to "35 yrs / Female", "Mobile" to "9876543210", "Patient ID" to "P-0001"),
        "INVOICE DETAILS",
        listOf("Invoice No." to "INV-0001", "Date" to today, "Consultant" to clinic.doctor.substringBefore(",").ifBlank { "Doctor" }, "Status" to "Paid in full")
    )
    pdf.table(
        listOf(
            PdfCell("Sr.", 0.07f, 9f, PdfSub, 1),
            PdfCell("Treatment / Procedure", 0.43f, 9f, PdfSub, 1),
            PdfCell("Tooth", 0.13f, 9f, PdfSub, 1),
            PdfCell("Date", 0.17f, 9f, PdfSub, 1),
            PdfCell("Amount", 0.2f, 9f, PdfSub, 1, end)
        ),
        listOf(
            listOf(
                PdfCell("1", 0.07f, 9.5f, PdfSub), PdfCell("Root Canal", 0.43f, 9.5f, PdfInk, 1),
                PdfCell("36", 0.13f, 9.5f), PdfCell(today, 0.17f, 9f, PdfSub), PdfCell(rupees2(6000.0), 0.2f, 9.5f, align = end)
            ),
            listOf(
                PdfCell("2", 0.07f, 9.5f, PdfSub), PdfCell("Cleaning", 0.43f, 9.5f, PdfInk, 1),
                PdfCell("–", 0.13f, 9.5f), PdfCell(today, 0.17f, 9f, PdfSub), PdfCell(rupees2(1200.0), 0.2f, 9.5f, align = end)
            )
        )
    )
    pdf.summary(
        "AMOUNT IN WORDS", amountInWords(7200.0), "Payment mode: Cash, UPI",
        listOf(
            SummaryLine("Total Amount", rupees2(7200.0)),
            SummaryLine("Amount Paid", rupees2(7200.0), color = PdfGreen),
            SummaryLine("Balance Due", rupees2(0.0), strong = true, color = PdfGreen)
        )
    )
    pdf.notes(listOf("This is a computer-generated invoice.", "Please keep it for your records."))
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
