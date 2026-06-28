package com.subrosa.app.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.subrosa.app.R
import com.subrosa.app.domain.model.Fact
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The aggregated, render-ready content of a case report (built from the local stores). */
data class CaseReport(
    val clientName: String,
    val contact: List<Pair<String, String>>,
    val matter: String,
    val caseTitle: String,
    val facts: List<Fact>,
    val missing: List<String>,
    val prompts: List<String>,
    val documents: List<String>,
    val consultations: List<String>,
    val generatedAtMs: Long,
)

/**
 * Renders a [CaseReport] to a themed, lawyerly PDF — entirely on-device (native [PdfDocument] +
 * Canvas, no library, no network). US-Letter, "Confidential Dossier" styling: wax-seal letterhead,
 * a privilege banner, numbered sections, verbatim source quotes, and an on-device footer.
 */
class CaseReportPdf(private val context: Context) {

    private fun font(id: Int, fallback: Typeface) =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

    private val fraunces = font(R.font.fraunces_black, Typeface.SERIF)
    private val frauncesSemi = font(R.font.fraunces_semibold, fraunces)
    private val news = font(R.font.newsreader_regular, Typeface.SERIF)
    private val newsItalic = font(R.font.newsreader_italic, news)
    private val mono = font(R.font.jetbrains_mono_regular, Typeface.MONOSPACE)
    private val monoMed = font(R.font.jetbrains_mono_medium, mono)

    fun render(report: CaseReport, outFile: File): File {
        val doc = PdfDocument()
        Flow(doc).run {
            start(report)
            emit(report)
            finish()
        }
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /** Issues the section sequence — kept separate from the page mechanics in [Flow]. */
    private fun Flow.emit(r: CaseReport) {
        section("I", "Client & contact")
        if (r.contact.isEmpty()) muted("No contact details on file.")
        else r.contact.forEach { (k, v) -> keyValue(k, v) }

        section("II", "Statement of facts")
        if (r.facts.isEmpty()) muted("No facts were recorded for this matter.")
        else r.facts.forEach { fact(it) }

        section("III", "Outstanding intake items")
        if (r.missing.isEmpty()) muted("All standard intake items for this matter were covered.")
        else {
            para("${r.missing.size} standard intake item(s) were not covered in consultation:", bodyDim)
            r.missing.forEach { bullet(it, MISSING) }
        }

        section("IV", "Recommended follow-up")
        if (r.prompts.isEmpty()) muted("No follow-up questions suggested.")
        else r.prompts.forEachIndexed { i, q -> numbered(i + 1, q) }

        section("V", "Documents on file")
        if (r.documents.isEmpty()) muted("No documents attached to this matter.")
        else r.documents.forEach { bullet(it, INK_SOFT) }

        section("VI", "Consultation log")
        if (r.consultations.isEmpty()) muted("No consultations recorded.")
        else r.consultations.forEach { monoLine(it) }

        gap(14f)
        para(DISCLAIMER, disclaimerPaint)
    }

    // ── page + layout engine ────────────────────────────────────────────────
    private inner class Flow(val doc: PdfDocument) {
        private val pageW = 612
        private val pageH = 792
        private val margin = 54f
        private val contentW = pageW - 2 * margin
        private val bottomLimit = pageH - margin - 26f

        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f
        private var pageNum = 0
        private lateinit var report: CaseReport

        fun start(r: CaseReport) {
            report = r
            openPage()
        }

        fun finish() {
            footer()
            doc.finishPage(page)
        }

        private fun openPage() {
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            canvas = page.canvas
            canvas.drawColor(PAPER)
            y = margin
            if (pageNum == 1) letterhead() else runningHeader()
        }

        private fun newPage() {
            footer()
            doc.finishPage(page)
            openPage()
        }

        private fun ensure(h: Float) {
            if (y + h > bottomLimit) newPage()
        }

        fun gap(h: Float) {
            y += h
        }

        private fun draw(layout: StaticLayout, x: Float) {
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun build(text: CharSequence, tp: TextPaint, width: Float): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, tp, width.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.05f)
                .setIncludePad(false)
                .build()

        fun para(text: String, tp: TextPaint, indent: Float = 0f, gapAfter: Float = 7f) {
            val l = build(text, tp, contentW - indent)
            ensure(l.height + gapAfter)
            draw(l, margin + indent)
            y += l.height + gapAfter
        }

        fun muted(text: String) = para(text, mutedItalic, gapAfter = 7f)

        fun keyValue(key: String, value: String) {
            val kp = build(key.uppercase(Locale.US), labelMono, 120f)
            val vp = build(value, body, contentW - 132f)
            ensure(maxOf(kp.height, vp.height) + 6f)
            draw(kp, margin)
            draw(vp, margin + 132f)
            y += maxOf(kp.height, vp.height) + 6f
        }

        fun fact(f: Fact) {
            para("•  ${f.statement}", body, gapAfter = 3f)
            val quote = "“${f.sourceQuote}”"
            val l = build(quote, quotePaint, contentW - 30f)
            ensure(l.height + 8f)
            canvas.drawRect(margin + 14f, y, margin + 15.5f, y + l.height, brassFill)
            draw(l, margin + 26f)
            y += l.height + 3f
            if (!f.verified) para("       ⚠ quote not matched verbatim to the transcript", flagPaint, gapAfter = 8f)
            else y += 7f
        }

        fun bullet(text: String, color: Int) {
            val bp = TextPaint(body).apply { this.color = color }
            para("▢  $text", bp, gapAfter = 5f)
        }

        fun numbered(n: Int, text: String) = para("$n.  $text", body, gapAfter = 5f)

        fun monoLine(text: String) = para(text, labelMono, gapAfter = 5f)

        fun section(numeral: String, title: String) {
            ensure(46f)
            y += 12f
            val baseline = y + 12f
            canvas.drawText("§ $numeral", margin, baseline, sectionNumeral)
            canvas.drawText(title, margin + 52f, baseline, sectionTitle)
            y += 20f
            canvas.drawLine(margin, y, margin + contentW, y, hairline)
            y += 11f
        }

        private fun letterhead() {
            drawSeal(canvas, margin + 26f, y + 26f, 26f)
            canvas.drawText("SUB ROSA", margin + 66f, y + 22f, wordmark)
            canvas.drawText("CONFIDENTIAL CASE REPORT", margin + 68f, y + 40f, eyebrow)
            canvas.drawText(
                fmtDate(report.generatedAtMs),
                pageW - margin,
                y + 22f,
                dateRight,
            )
            y += 64f
            // privilege banner
            val bannerH = 26f
            canvas.drawRect(margin, y, margin + contentW, y + bannerH, oxbloodFill)
            canvas.drawText(
                "PRIVILEGED & CONFIDENTIAL  ·  ATTORNEY WORK PRODUCT",
                pageW / 2f,
                y + 17f,
                bannerText,
            )
            y += bannerH + 16f
            // caption
            canvas.drawText("RE:  ${report.caseTitle}", margin, y + 6f, captionTitle)
            y += 22f
            para("${report.matter}  ·  Client: ${report.clientName}", captionMono, gapAfter = 2f)
            para("Prepared on-device by Sub Rosa · no data left this phone", captionMono, gapAfter = 0f)
            y += 6f
            canvas.drawLine(margin, y, margin + contentW, y, ruleThick)
            y += 16f
        }

        private fun runningHeader() {
            canvas.drawText(
                "SUB ROSA  ·  ${report.clientName}  ·  ${report.matter}",
                margin,
                y + 8f,
                eyebrow,
            )
            y += 16f
            canvas.drawLine(margin, y, margin + contentW, y, hairline)
            y += 14f
        }

        private fun footer() {
            val fy = pageH - margin + 10f
            canvas.drawLine(margin, fy - 12f, margin + contentW, fy - 12f, hairline)
            canvas.drawText("GENERATED ON-DEVICE · SUB ROSA", margin, fy, footerMono)
            canvas.drawText("Page $pageNum", pageW - margin, fy, footerRight)
        }
    }

    private fun drawSeal(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = OXBLOOD; c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.07f; p.color = OXBLOOD_DEEP; c.drawCircle(cx, cy, r, p)
        p.strokeWidth = r * 0.03f; p.color = Color.argb((255 * 0.85f).toInt(), 0xC2, 0xA4, 0x5F)
        c.drawCircle(cx, cy, r * 0.80f, p)
        p.style = Paint.Style.FILL
        val pL = r * 0.56f; val pW = r * 0.36f
        p.color = Color.argb((255 * 0.92f).toInt(), 0x9B, 0x4A, 0x56)
        for (i in 0 until 5) {
            c.save(); c.rotate(i * 72f, cx, cy)
            c.drawOval(cx - pW / 2f, cy - pL, cx + pW / 2f, cy, p); c.restore()
        }
        p.color = Color.argb((255 * 0.80f).toInt(), 0xC2, 0xA4, 0x5F)
        val iw = pW * 0.60f; val il = pL * 0.64f
        for (i in 0 until 5) {
            c.save(); c.rotate(36f + i * 72f, cx, cy)
            c.drawOval(cx - iw / 2f, cy - il, cx + iw / 2f, cy, p); c.restore()
        }
        p.color = OXBLOOD_DEEP; c.drawCircle(cx, cy, r * 0.11f, p)
    }

    private fun fmtDate(ms: Long) = SimpleDateFormat("d MMMM yyyy", Locale.US).format(Date(ms))

    // ── paints ──────────────────────────────────────────────────────────────
    private fun tp(tf: Typeface, size: Float, c: Int, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; color = c; textAlign = align }

    private val wordmark = tp(fraunces, 21f, INK)
    private val eyebrow = tp(mono, 8.5f, BRASS).apply { letterSpacing = 0.18f }
    private val dateRight = tp(mono, 9f, INK_SOFT, Paint.Align.RIGHT)
    private val bannerText = tp(monoMed, 9f, PAPER, Paint.Align.CENTER).apply { letterSpacing = 0.10f }
    private val captionTitle = tp(frauncesSemi, 15f, INK)
    private val captionMono = tp(mono, 9f, INK_SOFT)
    private val sectionNumeral = tp(monoMed, 11f, OXBLOOD)
    private val sectionTitle = tp(frauncesSemi, 13f, INK)
    private val body = tp(news, 11f, INK)
    private val bodyDim = tp(news, 10.5f, INK_SOFT)
    private val quotePaint = tp(newsItalic, 10.5f, OXBLOOD)
    private val labelMono = tp(mono, 9f, INK_SOFT)
    private val mutedItalic = tp(newsItalic, 10.5f, INK_SOFT)
    private val flagPaint = tp(mono, 8.5f, MISSING)
    private val disclaimerPaint = tp(newsItalic, 8.5f, INK_FAINT)
    private val footerMono = tp(mono, 7.5f, INK_FAINT).apply { letterSpacing = 0.08f }
    private val footerRight = tp(mono, 7.5f, INK_FAINT, Paint.Align.RIGHT)

    private val oxbloodFill = Paint().apply { color = OXBLOOD; isAntiAlias = true }
    private val brassFill = Paint().apply { color = Color.argb(220, 0xC2, 0xA4, 0x5F); isAntiAlias = true }
    private val hairline = Paint().apply { color = HAIRLINE; strokeWidth = 0.7f; isAntiAlias = true }
    private val ruleThick = Paint().apply { color = OXBLOOD; strokeWidth = 1.6f; isAntiAlias = true }

    private companion object {
        val PAPER = Color.parseColor("#FBF6EC")
        val INK = Color.parseColor("#221B16")
        val INK_SOFT = Color.parseColor("#6A5E4F")
        val INK_FAINT = Color.parseColor("#9C8E79")
        val OXBLOOD = Color.parseColor("#6E1F2A")
        val OXBLOOD_DEEP = Color.parseColor("#52141D")
        val BRASS = Color.parseColor("#94762F")
        val HAIRLINE = Color.parseColor("#CBBCA1")
        val MISSING = Color.parseColor("#8C2A2A")

        const val DISCLAIMER =
            "These intake notes were generated on this device from the consultation transcript to " +
                "assist the attorney's review. They are a working summary, not legal advice or a legal " +
                "conclusion, and should be verified against the source record."
    }
}
