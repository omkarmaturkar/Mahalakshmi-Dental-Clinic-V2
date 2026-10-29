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

    /** Stamps (PAID, RECEIVED, ESTIMATE) are switched off: documents stay clean. */
    @Suppress("UNUSED_PARAMETER")
    fun stamp(text: String, color: Int) {
        // Intentionally empty
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
        canvas.drawRect(0f, 0f, pageW.toFloat(),
