package com.xs.expensetracker.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object ExportManager {

    private const val AUTHORITY = "com.xs.expensetracker.fileprovider"

    // ─── PDF constants ────────────────────────────────────────────────────────
    private const val PW = 595        // A4 width  @ 72 PPI
    private const val PH = 842        // A4 height @ 72 PPI
    private const val ML = 36f        // horizontal margin
    private const val CW = PW - ML * 2  // content width = 523

    // ─── Brand palette ────────────────────────────────────────────────────────
    private val C_PRIMARY    = Color.parseColor("#6C5CE7")
    private val C_PRIMARY_LT = Color.parseColor("#EDE9FE")
    private val C_PRIMARY_DK = Color.parseColor("#5B21B6")
    private val C_INCOME     = Color.parseColor("#00C9A7")
    private val C_INCOME_LT  = Color.parseColor("#D1FAF5")
    private val C_EXPENSE    = Color.parseColor("#FF6B6B")
    private val C_EXPENSE_LT = Color.parseColor("#FFE4E4")
    private val C_BG_ALT     = Color.parseColor("#F8F9FA")
    private val C_TEXT_DARK  = Color.parseColor("#1A1A2E")
    private val C_TEXT_MID   = Color.parseColor("#6B7280")
    private val C_BORDER     = Color.parseColor("#E5E7EB")
    private val C_GRAD_START = Color.parseColor("#7C3AED")
    private val C_GRAD_END   = Color.parseColor("#4F46E5")

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    fun exportToCsv(
        context: Context,
        tracker: Tracker,
        sources: List<TransactionSource>,
        receipts: List<TransactionReceipt>
    ): Uri {
        val dateFmt    = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val currency   = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val timestamp  = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val safeName   = tracker.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val file       = File(context.cacheDir, "${safeName}_$timestamp.csv")

        // FileWriter(File, Charset) is API 33+; minSdk is 24, so it would throw
        // NoSuchMethodError on anything below Android 13. bufferedWriter wraps
        // OutputStreamWriter and is available on every supported level.
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            // UTF-8 BOM so Excel opens correctly
            w.append("\uFEFF")

            // ── Report header ──────────────────────────────────────────
            w.appendLine("EXPENSE TRACKER REPORT")
            w.appendLine("Tracker,${esc(tracker.name)}")
            w.appendLine("Exported,${dateFmt.format(Date())}")
            w.appendLine()

            val totalIncome  = sources.filter { it.type == TransactionType.INCOME  }.sumOf { it.totalAmount }
            val totalExpense = sources.filter { it.type == TransactionType.EXPENSE }.sumOf { it.totalAmount }

            w.appendLine("SUMMARY")
            w.appendLine("Total Income,${currency.format(totalIncome)}")
            w.appendLine("Total Expenses,${currency.format(totalExpense)}")
            w.appendLine("Net Balance,${currency.format(tracker.grandTotal)}")
            w.appendLine()

            // ── Income sources ─────────────────────────────────────────
            w.appendLine("INCOME SOURCES")
            w.appendLine("Name,Total Amount")
            sources.filter { it.type == TransactionType.INCOME }.forEach { s ->
                w.appendLine("${esc(s.name)},${currency.format(s.totalAmount)}")
            }
            if (sources.none { it.type == TransactionType.INCOME }) w.appendLine("(none)")
            w.appendLine()

            // ── Expense sources ────────────────────────────────────────
            w.appendLine("EXPENSE SOURCES")
            w.appendLine("Name,Total Amount")
            sources.filter { it.type == TransactionType.EXPENSE }.forEach { s ->
                w.appendLine("${esc(s.name)},${currency.format(s.totalAmount)}")
            }
            if (sources.none { it.type == TransactionType.EXPENSE }) w.appendLine("(none)")
            w.appendLine()

            // ── Transaction history ────────────────────────────────────
            w.appendLine("TRANSACTION HISTORY")
            w.appendLine("Date,Name,Type,Source,Amount,Description")
            receipts.sortedByDescending { it.date }.forEach { r ->
                val src  = sources.find { it.id == r.sourceId }?.name ?: "Unknown"
                val date = dateFmt.format(Date(r.date))
                w.appendLine("${esc(date)},${esc(r.name)},${r.type.name},${esc(src)},${r.amount},${esc(r.description)}")
            }
            if (receipts.isEmpty()) w.appendLine("(no transactions)")
        }

        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    fun exportToPdf(
        context: Context,
        tracker: Tracker,
        sources: List<TransactionSource>,
        receipts: List<TransactionReceipt>
    ): Uri {
        val currency   = NumberFormat.getCurrencyInstance(Locale.getDefault())
        val dateFmt    = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFmt    = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
        val timestamp  = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val safeName   = tracker.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val file       = File(context.cacheDir, "${safeName}_$timestamp.pdf")

        val doc = PdfDocument()
        val ctx = PdfCtx(doc)
        ctx.newPage()

        // ── Page 1 header ─────────────────────────────────────────────
        drawHeader(ctx.canvas, tracker.name, dateFmt.format(Date()))
        ctx.y = 94f

        // ── Summary cards ─────────────────────────────────────────────
        val totalIncome  = sources.filter { it.type == TransactionType.INCOME  }.sumOf { it.totalAmount }
        val totalExpense = sources.filter { it.type == TransactionType.EXPENSE }.sumOf { it.totalAmount }

        ctx.ensureSpace(82f) { drawPageContinuationHeader(ctx.canvas, ctx.pageNum) }
        drawSummaryCards(ctx.canvas, ctx.y, currency, totalIncome, totalExpense, tracker.grandTotal)
        ctx.y += 82f

        // ── Income sources ────────────────────────────────────────────
        val incSources = sources.filter { it.type == TransactionType.INCOME }
        ctx.ensureSpace(54f) { drawPageContinuationHeader(ctx.canvas, ctx.pageNum) }
        ctx.y = drawSectionHeader(ctx.canvas, ctx.y, "INCOME SOURCES", C_INCOME)
        if (incSources.isEmpty()) {
            ctx.y = drawEmptyRow(ctx.canvas, ctx.y, "No income sources recorded")
        } else {
            ctx.y = drawSourcesTable(ctx, incSources, currency, C_INCOME, C_INCOME_LT)
        }

        // ── Expense sources ───────────────────────────────────────────
        val expSources = sources.filter { it.type == TransactionType.EXPENSE }
        ctx.ensureSpace(54f) { drawPageContinuationHeader(ctx.canvas, ctx.pageNum) }
        ctx.y = drawSectionHeader(ctx.canvas, ctx.y, "EXPENSE SOURCES", C_EXPENSE)
        if (expSources.isEmpty()) {
            ctx.y = drawEmptyRow(ctx.canvas, ctx.y, "No expense sources recorded")
        } else {
            ctx.y = drawSourcesTable(ctx, expSources, currency, C_EXPENSE, C_EXPENSE_LT)
        }

        // ── Transaction history ───────────────────────────────────────
        val sortedReceipts = receipts.sortedByDescending { it.date }
        ctx.ensureSpace(54f) { drawPageContinuationHeader(ctx.canvas, ctx.pageNum) }
        ctx.y = drawSectionHeader(ctx.canvas, ctx.y, "TRANSACTION HISTORY", C_PRIMARY)
        if (sortedReceipts.isEmpty()) {
            ctx.y = drawEmptyRow(ctx.canvas, ctx.y, "No transactions recorded")
        } else {
            ctx.y = drawReceiptsTable(ctx, sortedReceipts, sources, currency, dateFmt)
        }

        // ── Footer on final page ──────────────────────────────────────
        drawFooter(ctx.canvas, ctx.pageNum, timeFmt.format(Date()))
        ctx.doc.finishPage(ctx.page)

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    // =========================================================================
    // PDF – HEADER / FOOTER
    // =========================================================================

    private fun drawHeader(canvas: Canvas, trackerName: String, dateStr: String) {
        // Gradient bar
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, PW.toFloat(), 80f,
                intArrayOf(C_GRAD_START, C_PRIMARY, C_GRAD_END),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, PW.toFloat(), 80f, gradPaint)

        // Decorative translucent circles
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 18 }
        canvas.drawCircle(PW - 30f, -10f, 90f, circlePaint)
        canvas.drawCircle(PW - 70f, 100f, 55f, circlePaint)

        // Sub-label
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 190
            typeface = Typeface.DEFAULT; textSize = 8.5f; letterSpacing = 0.18f
        }
        canvas.drawText("EXPENSE TRACKER  •  FINANCIAL REPORT", ML, 21f, subPaint)

        // Tracker name
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD; textSize = 21f
        }
        canvas.drawText(trunc(trackerName, namePaint, CW - 130f), ML, 57f, namePaint)

        // Date (right)
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 210
            typeface = Typeface.DEFAULT; textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateStr, PW - ML, 57f, datePaint)

        // Bottom accent line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 70; strokeWidth = 1f; style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, 80f, PW.toFloat(), 80f, linePaint)
    }

    private fun drawPageContinuationHeader(canvas: Canvas, pageNum: Int) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_PRIMARY }
        canvas.drawRect(0f, 0f, PW.toFloat(), 42f, bgPaint)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = 12f
        }
        canvas.drawText("EXPENSE TRACKER REPORT  (continued)", ML, 28f, tp)

        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 200; typeface = Typeface.DEFAULT; textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNum", PW - ML, 28f, pp)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, timestamp: String) {
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_BORDER; strokeWidth = 0.8f; style = Paint.Style.STROKE
        }
        canvas.drawLine(ML, PH - 32f, PW - ML, PH - 32f, divPaint)

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_TEXT_MID; typeface = Typeface.DEFAULT; textSize = 7.5f
        }
        canvas.drawText("Generated  $timestamp", ML, PH - 16f, leftPaint)

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_TEXT_MID; typeface = Typeface.DEFAULT; textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNum", PW - ML, PH - 16f, rightPaint)
    }

    // =========================================================================
    // PDF – SUMMARY CARDS
    // =========================================================================

    private fun drawSummaryCards(
        canvas: Canvas, y: Float,
        currency: NumberFormat,
        income: Double, expense: Double, net: Double
    ) {
        val gap   = 10f
        val cardW = (CW - gap * 2f) / 3f
        val configs = listOf(
            Triple("TOTAL INCOME",   currency.format(income),  Pair(C_INCOME,  C_INCOME_LT)),
            Triple("TOTAL EXPENSES", currency.format(expense), Pair(C_EXPENSE, C_EXPENSE_LT)),
            Triple("NET BALANCE", buildString {
                if (net < 0) append("−")
                append(currency.format(abs(net)))
            }, if (net >= 0) Pair(C_INCOME, C_INCOME_LT) else Pair(C_EXPENSE, C_EXPENSE_LT))
        )

        configs.forEachIndexed { i, (label, value, colors) ->
            val (accent, ltBg) = colors
            val x = ML + i * (cardW + gap)
            val rect = RectF(x, y, x + cardW, y + 70f)

            // Card background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ltBg }
            canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

            // Left accent bar
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawRoundRect(RectF(x, y, x + 4f, y + 70f), 8f, 8f, accentPaint)
            canvas.drawRect(RectF(x + 1f, y, x + 4f, y + 70f), accentPaint)

            // Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; alpha = 80; style = Paint.Style.STROKE; strokeWidth = 1f
            }
            canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

            // Label
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_TEXT_MID; typeface = Typeface.DEFAULT; textSize = 7.5f; letterSpacing = 0.1f
            }
            canvas.drawText(label, x + 12f, y + 20f, labelPaint)

            // Value
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; typeface = Typeface.DEFAULT_BOLD; textSize = 17f
            }
            canvas.drawText(trunc(value, valuePaint, cardW - 18f), x + 12f, y + 52f, valuePaint)
        }
    }

    // =========================================================================
    // PDF – SECTION HEADER
    // =========================================================================

    private fun drawSectionHeader(canvas: Canvas, y: Float, title: String, color: Int): Float {
        val topY = y + 18f

        // Accent bar
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(ML, topY, ML + 4f, topY + 16f), 2f, 2f, ap)

        // Title
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = C_TEXT_DARK; typeface = Typeface.DEFAULT_BOLD
            textSize = 10.5f; letterSpacing = 0.06f
        }
        canvas.drawText(title, ML + 11f, topY + 12f, tp)

        return topY + 22f
    }

    private fun drawEmptyRow(canvas: Canvas, y: Float, message: String): Float {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_BG_ALT }
        canvas.drawRect(ML, y, ML + CW, y + 28f, bgPaint)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_TEXT_MID; typeface = Typeface.DEFAULT; textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(message, ML + CW / 2f, y + 19f, tp)

        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_BORDER; strokeWidth = 0.8f; style = Paint.Style.STROKE
        }
        canvas.drawLine(ML, y + 28f, ML + CW, y + 28f, bp)
        return y + 38f
    }

    // =========================================================================
    // PDF – SOURCES TABLE
    // =========================================================================

    private fun drawSourcesTable(
        ctx: PdfCtx,
        sources: List<TransactionSource>,
        currency: NumberFormat,
        accentColor: Int,
        accentLt: Int
    ): Float {
        val col1W = CW * 0.73f
        val col2W = CW * 0.27f

        var y = ctx.y + 4f

        // ── Table header row ──────────────────────────────────────────
        ctx.ensureSpace(24f) { drawPageContinuationHeader(ctx.canvas, ctx.pageNum); y = ctx.y + 4f }
        val hbg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentLt }
        ctx.canvas.drawRect(ML, y, ML + CW, y + 22f, hbg)
        val headerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor; alpha = 60; strokeWidth = 1f; style = Paint.Style.STROKE
        }
        ctx.canvas.drawRect(ML, y, ML + CW, y + 22f, headerBorder)

        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_PRIMARY_DK; typeface = Typeface.DEFAULT_BOLD; textSize = 8.5f; letterSpacing = 0.07f
        }
        ctx.canvas.drawText("SOURCE NAME", ML + 8f, y + 15f, hp)
        val hpR = Paint(hp).apply { textAlign = Paint.Align.RIGHT }
        ctx.canvas.drawText("TOTAL AMOUNT", ML + CW - 8f, y + 15f, hpR)
        y += 22f

        // ── Data rows ─────────────────────────────────────────────────
        sources.forEachIndexed { idx, source ->
            ctx.ensureSpace(22f) {
                drawPageContinuationHeader(ctx.canvas, ctx.pageNum)
                // Redraw column headers on new page
                val newY = ctx.y + 4f
                val hbg2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentLt }
                ctx.canvas.drawRect(ML, newY, ML + CW, newY + 22f, hbg2)
                ctx.canvas.drawRect(ML, newY, ML + CW, newY + 22f, headerBorder)
                ctx.canvas.drawText("SOURCE NAME", ML + 8f, newY + 15f, hp)
                ctx.canvas.drawText("TOTAL AMOUNT", ML + CW - 8f, newY + 15f, hpR)
                ctx.y = newY + 22f
                y = ctx.y
            }

            val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (idx % 2 == 0) Color.WHITE else C_BG_ALT
            }
            ctx.canvas.drawRect(ML, y, ML + CW, y + 22f, rowBg)

            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_TEXT_DARK; typeface = Typeface.DEFAULT; textSize = 10f
            }
            ctx.canvas.drawText(trunc(source.name, tp, col1W - 16f), ML + 8f, y + 15f, tp)

            val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor; typeface = Typeface.DEFAULT_BOLD; textSize = 10f
                textAlign = Paint.Align.RIGHT
            }
            ctx.canvas.drawText(currency.format(source.totalAmount), ML + CW - 8f, y + 15f, vp)
            y += 22f
        }

        // Bottom border
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_BORDER; strokeWidth = 0.8f; style = Paint.Style.STROKE
        }
        ctx.canvas.drawLine(ML, y, ML + CW, y, bp)
        ctx.y = y + 14f
        return ctx.y
    }

    // =========================================================================
    // PDF – RECEIPTS TABLE
    // =========================================================================

    private fun drawReceiptsTable(
        ctx: PdfCtx,
        receipts: List<TransactionReceipt>,
        sources: List<TransactionSource>,
        currency: NumberFormat,
        dateFmt: SimpleDateFormat
    ): Float {
        // Column x-positions (absolute from left margin)
        val xDate   = ML
        val xName   = ML + CW * 0.14f
        val xSrc    = ML + CW * 0.45f
        val xType   = ML + CW * 0.67f
        val xEnd    = ML + CW  // amount is right-aligned to here

        val wDate   = CW * 0.14f
        val wName   = CW * 0.31f
        val wSrc    = CW * 0.22f
        val wType   = CW * 0.10f

        fun drawTableHeader(canvas: Canvas, y: Float) {
            val hbg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_PRIMARY_LT }
            canvas.drawRect(ML, y, ML + CW, y + 22f, hbg)
            val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_PRIMARY; alpha = 55; strokeWidth = 1f; style = Paint.Style.STROKE
            }
            canvas.drawRect(ML, y, ML + CW, y + 22f, borderP)

            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_PRIMARY_DK; typeface = Typeface.DEFAULT_BOLD
                textSize = 8.5f; letterSpacing = 0.07f
            }
            canvas.drawText("DATE",        xDate  + 6f, y + 15f, hp)
            canvas.drawText("DESCRIPTION", xName  + 6f, y + 15f, hp)
            canvas.drawText("SOURCE",      xSrc   + 6f, y + 15f, hp)
            canvas.drawText("TYPE",        xType  + 6f, y + 15f, hp)
            val hpR = Paint(hp).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("AMOUNT", xEnd - 6f, y + 15f, hpR)
        }

        var y = ctx.y + 4f

        // First header
        ctx.ensureSpace(24f) { drawPageContinuationHeader(ctx.canvas, ctx.pageNum); y = ctx.y + 4f }
        drawTableHeader(ctx.canvas, y)
        y += 22f

        receipts.forEachIndexed { idx, r ->
            // Pagination: check if next row fits
            if (y > PH - 52f) {
                val bpLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = C_BORDER; strokeWidth = 0.8f; style = Paint.Style.STROKE
                }
                ctx.canvas.drawLine(ML, y, ML + CW, y, bpLine)
                drawFooter(ctx.canvas, ctx.pageNum, SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()).format(Date()))
                ctx.doc.finishPage(ctx.page)
                ctx.newPage()
                drawPageContinuationHeader(ctx.canvas, ctx.pageNum)
                ctx.y = 52f
                y = ctx.y
                drawTableHeader(ctx.canvas, y)
                y += 22f
            }

            // Row background (alternating)
            val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (idx % 2 == 0) Color.WHITE else C_BG_ALT
            }
            ctx.canvas.drawRect(ML, y, ML + CW, y + 22f, rowBg)

            val isIncome   = r.type == TransactionType.INCOME
            val amtColor   = if (isIncome) C_INCOME else C_EXPENSE
            val badgeBgClr = if (isIncome) C_INCOME_LT else C_EXPENSE_LT

            // Date
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_TEXT_MID; typeface = Typeface.DEFAULT; textSize = 8.5f
            }
            val dateStr = dateFmt.format(Date(r.date))
            ctx.canvas.drawText(trunc(dateStr, datePaint, wDate - 8f), xDate + 6f, y + 15f, datePaint)

            // Name
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_TEXT_DARK; typeface = Typeface.DEFAULT_BOLD; textSize = 9.5f
            }
            ctx.canvas.drawText(trunc(r.name, namePaint, wName - 10f), xName + 6f, y + 15f, namePaint)

            // Source
            val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C_TEXT_MID; typeface = Typeface.DEFAULT; textSize = 9f
            }
            val srcName = sources.find { it.id == r.sourceId }?.name ?: "—"
            ctx.canvas.drawText(trunc(srcName, srcPaint, wSrc - 10f), xSrc + 6f, y + 15f, srcPaint)

            // Type badge
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeBgClr }
            ctx.canvas.drawRoundRect(
                RectF(xType + 4f, y + 5f, xType + wType - 4f, y + 18f),
                4f, 4f, badgePaint
            )
            val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amtColor; typeface = Typeface.DEFAULT_BOLD
                textSize = 7.5f; textAlign = Paint.Align.CENTER
            }
            ctx.canvas.drawText(
                if (isIncome) "INC" else "EXP",
                xType + wType / 2f, y + 14f, typePaint
            )

            // Amount
            val amtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amtColor; typeface = Typeface.DEFAULT_BOLD
                textSize = 10f; textAlign = Paint.Align.RIGHT
            }
            val prefix = if (isIncome) "+" else "−"
            ctx.canvas.drawText("$prefix${currency.format(r.amount)}", xEnd - 6f, y + 15f, amtPaint)

            y += 22f
        }

        // Final bottom border
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_BORDER; strokeWidth = 0.8f; style = Paint.Style.STROKE
        }
        ctx.canvas.drawLine(ML, y, ML + CW, y, bp)
        ctx.y = y + 14f
        return ctx.y
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Truncate text to fit within maxWidth, appending "…" if truncated. */
    private fun trunc(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return if (t.isEmpty()) "…" else "$t…"
    }

    /** Escape a value for CSV (wrap in quotes, escape internal quotes). */
    private fun esc(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}

// =============================================================================
// PDF pagination context
// =============================================================================

private class PdfCtx(val doc: PdfDocument) {
    var pageNum = 0
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    var y = 0f

    fun newPage() {
        pageNum++
        val info = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
        page = doc.startPage(info)
        canvas = page.canvas
        // White page background
        canvas.drawColor(Color.WHITE)
        y = 36f
    }

    /**
     * If the next block (of size [needed]) won't fit on the current page,
     * finish the page, start a new one, call [onNewPage] for the continuation
     * header, and reset y.
     */
    fun ensureSpace(needed: Float, onNewPage: () -> Unit) {
        if (y + needed > 842 - 52f) {
            doc.finishPage(page)
            newPage()
            onNewPage()
            y = 56f
        }
    }
}