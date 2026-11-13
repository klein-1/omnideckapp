package com.example.lib.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class PdfPageSizePreset(val label: String, val wPts: Int, val hPts: Int) {
    ORIGINAL("Original", 0, 0),
    A4("A4 Portrait", 1190, 1684),
    LETTER("US Letter", 1224, 1584)
}

data class PdfDeepAudit(
    val pageCount: Int,
    val byteSize: Long,
    val wordCount: Int,
    val links: List<String>,
    val bookmarks: List<String>,
    val isEncryptedFlag: Boolean,
    val checklist: List<Pair<String, Boolean>>
)

object PdfLabAdvanced {

    fun isPageMostlyBlank(bmp: Bitmap): Boolean {
        val tiny = Bitmap.createScaledBitmap(bmp, 32, 32, true)
        val pix = IntArray(32 * 32)
        tiny.getPixels(pix, 0, 32, 0, 0, 32, 32)
        var nonWhite = 0
        pix.forEach { c ->
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            if (r < 242 || g < 242 || b < 242) nonWhite++
        }
        return nonWhite < 6
    }

    fun splitEveryNToZip(
        context: Context,
        sourceUri: Uri,
        everyN: Int,
        out: OutputStream
    ) {
        val total = PdfLabEngine.getPdfPageCount(context, sourceUri)
        if (total <= 0) return
        val chunk = everyN.coerceIn(1, total)
        ZipOutputStream(out).use { zos ->
            var start = 0
            var part = 1
            while (start < total) {
                val end = min(total, start + chunk)
                val plan = (start until end).map { PdfPageTransform(0, it, 0, false) }
                val baos = ByteArrayOutputStream()
                PdfLabEngine.buildTransformedPdf(context, listOf(sourceUri), plan, renderWidth = 1100, jpegQuality = 85, out = baos)
                zos.putNextEntry(ZipEntry("split_part_${"%02d".format(part)}_p${start + 1}-${end}.pdf"))
                zos.write(baos.toByteArray())
                zos.closeEntry()
                start = end
                part++
            }
        }
    }

    fun compressToTargetKb(
        context: Context,
        sourceUris: List<Uri>,
        plan: List<PdfPageTransform>,
        targetKb: Int
    ): Triple<ByteArray, Int, Int> {
        val targetBytes = targetKb.coerceAtLeast(30) * 1024
        val attempts = listOf(
            1100 to 82, 1000 to 70, 900 to 58, 780 to 48, 660 to 38, 540 to 30
        )
        var lastBytes = ByteArray(0)
        var usedDpi = 72
        var usedQ = 30
        for ((w, q) in attempts) {
            val baos = ByteArrayOutputStream()
            PdfLabEngine.buildTransformedPdf(context, sourceUris, plan, renderWidth = w, jpegQuality = q, out = baos)
            lastBytes = baos.toByteArray()
            usedDpi = w / 8
            usedQ = q
            if (lastBytes.size <= targetBytes) break
        }
        return Triple(lastBytes, usedDpi, usedQ)
    }

    fun buildImposedOrStyledPdf(
        context: Context,
        sourceUris: List<Uri>,
        plan: List<PdfPageTransform>,
        renderWidth: Int,
        jpegQuality: Int,
        nUp: Int, // 1, 2, or 4
        pageSize: PdfPageSizePreset,
        marginPct: Int,
        headerText: String,
        footerText: String,
        batesPrefix: String,
        batesStart: Int,
        stampLabel: String,
        watermarkText: String,
        includePageNumbers: Boolean,
        skipBlankPages: Boolean,
        whitewashBg: Boolean,
        redactions: List<RectF>,
        highlightBoxes: List<RectF>,
        signatureBmp: Bitmap?,
        out: OutputStream
    ) {
        val doc = PdfDocument()
        val pfds = mutableListOf<ParcelFileDescriptor>()
        val renderers = mutableListOf<PdfRenderer>()
        try {
            sourceUris.forEach { uri ->
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open PDF")
                pfds += pfd
                renderers += PdfRenderer(pfd)
            }
            val active = plan.filter { !it.deleted }
            val renderedPages = mutableListOf<Bitmap>()
            active.forEach { step ->
                val r = renderers.getOrNull(step.sourceDocIndex) ?: return@forEach
                if (step.sourcePageIndex !in 0 until r.pageCount) return@forEach
                r.openPage(step.sourcePageIndex).use { page ->
                    val scale = renderWidth.toFloat() / page.width.coerceAtLeast(1)
                    val w = (page.width * scale).toInt().coerceIn(200, 2000)
                    val h = (page.height * scale).toInt().coerceIn(200, 2800)
                    var bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    if (skipBlankPages && isPageMostlyBlank(bmp)) return@use
                    if (step.rotationDeg % 360 != 0) {
                        val m = Matrix().apply { postRotate(step.rotationDeg.toFloat()) }
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    if (whitewashBg) {
                        bmp = ImageStudioEngine.applyFilterChain(bmp, listOf(ImageFilterStep(ImageFilterType.CONTRAST, 0.72f)))
                    }
                    if (jpegQuality < 95) {
                        val bytes = ImageStudioEngine.compressToBytes(bmp, ExportImageFormat.JPEG, jpegQuality)
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bmp
                    }
                    renderedPages += bmp
                }
            }

            if (renderedPages.isEmpty()) {
                val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val p = doc.startPage(info); p.canvas.drawColor(Color.WHITE); doc.finishPage(p)
                doc.writeTo(out); return
            }

            val groupSize = nUp.coerceIn(1, 4)
            val groups = renderedPages.chunked(groupSize)
            groups.forEachIndexed { outIdx, group ->
                val first = group.first()
                val baseW = if (pageSize == PdfPageSizePreset.ORIGINAL) first.width else pageSize.wPts
                val baseH = if (pageSize == PdfPageSizePreset.ORIGINAL) first.height else pageSize.hPts
                val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, outIdx + 1).create()
                val outPage = doc.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val marginX = baseW * (marginPct.coerceIn(0, 25) / 100f)
                val marginY = baseH * (marginPct.coerceIn(0, 25) / 100f)
                val inner = RectF(marginX, marginY, baseW - marginX, baseH - marginY)

                when (groupSize) {
                    1 -> canvas.drawBitmap(group[0], null, inner, null)
                    2 -> {
                        val slotH = inner.height() / 2f
                        group.forEachIndexed { i, b ->
                            val dst = RectF(inner.left, inner.top + i * slotH, inner.right, inner.top + (i + 1) * slotH - 8f)
                            canvas.drawBitmap(b, null, dst, null)
                        }
                    }
                    else -> {
                        val slotW = inner.width() / 2f
                        val slotH = inner.height() / 2f
                        group.forEachIndexed { i, b ->
                            val col = i % 2; val row = i / 2
                            val dst = RectF(
                                inner.left + col * slotW + 4f,
                                inner.top + row * slotH + 4f,
                                inner.left + (col + 1) * slotW - 4f,
                                inner.top + (row + 1) * slotH - 4f
                            )
                            canvas.drawBitmap(b, null, dst, null)
                        }
                    }
                }

                // Highlights & Redactions
                val hlPaint = Paint().apply { color = Color.argb(95, 255, 230, 0); style = Paint.Style.FILL }
                highlightBoxes.forEach { r ->
                    canvas.drawRect(r.left * baseW, r.top * baseH, r.right * baseW, r.bottom * baseH, hlPaint)
                }
                val redPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                redactions.forEach { r ->
                    canvas.drawRect(r.left * baseW, r.top * baseH, r.right * baseW, r.bottom * baseH, redPaint)
                }

                // Stamp badge
                if (stampLabel.isNotBlank()) {
                    val stampColor = if (stampLabel == "APPROVED") 0xFF059669.toInt() else 0xFFE11D48.toInt()
                    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stampColor; style = Paint.Style.STROKE; strokeWidth = 5f
                    }
                    val stPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stampColor; textSize = baseW * 0.036f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }
                    val box = RectF(baseW * 0.62f, baseH * 0.05f, baseW * 0.94f, baseH * 0.11f)
                    canvas.drawRect(box, sp)
                    canvas.drawText(stampLabel, box.left + 14f, box.centerY() + baseW * 0.012f, stPaint)
                }

                // Watermark
                if (watermarkText.isNotBlank()) {
                    val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(55, 20, 20, 19)
                        textSize = baseW * 0.055f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.save()
                    canvas.rotate(-30f, baseW / 2f, baseH / 2f)
                    canvas.drawText(watermarkText, baseW / 2f, baseH / 2f, wmPaint)
                    canvas.restore()
                }

                // Signature
                if (signatureBmp != null) {
                    val dst = RectF(baseW * 0.62f, baseH * 0.80f, baseW * 0.92f, baseH * 0.91f)
                    canvas.drawBitmap(signatureBmp, null, dst, null)
                }

                // Header / Footer / Bates / Page numbers
                val hfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = baseW * 0.02f }
                if (headerText.isNotBlank()) canvas.drawText(headerText, 36f, 34f, hfPaint)
                val bottomParts = buildList {
                    if (footerText.isNotBlank()) add(footerText)
                    if (batesPrefix.isNotBlank()) add("$batesPrefix${"%04d".format(batesStart + outIdx)}")
                    if (includePageNumbers) add("Page ${outIdx + 1}/${groups.size}")
                }
                if (bottomParts.isNotEmpty()) {
                    canvas.drawText(bottomParts.joinToString("   ·   "), 36f, baseH - 22f, hfPaint)
                }
                doc.finishPage(outPage)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
            renderers.forEach { runCatching { it.close() } }
            pfds.forEach { runCatching { it.close() } }
        }
    }

    fun textToPdf(title: String, bodyText: String, out: OutputStream) {
        val doc = PdfDocument()
        val pageW = 1190; val pageH = 1684 // A4 crisp pts
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 38f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF222222.toInt(); textSize = 24f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val maxLineW = pageW - 120f
        val wrapped = mutableListOf<String>()
        bodyText.lines().forEach { raw ->
            if (raw.isBlank()) {
                wrapped += ""
            } else {
                var cur = ""
                raw.split(" ").forEach { w ->
                    val candidate = if (cur.isEmpty()) w else "$cur $w"
                    if (bodyPaint.measureText(candidate) > maxLineW) {
                        wrapped += cur; cur = w
                    } else cur = candidate
                }
                if (cur.isNotEmpty()) wrapped += cur
            }
        }

        val linesPerPage = 44
        val chunks = if (wrapped.isEmpty()) listOf(emptyList()) else wrapped.chunked(linesPerPage)
        chunks.forEachIndexed { idx, lines ->
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
            val page = doc.startPage(info)
            val c = page.canvas
            c.drawColor(Color.WHITE)
            var y = 84f
            if (idx == 0 && title.isNotBlank()) {
                c.drawText(title, 60f, y, titlePaint)
                y += 58f
            }
            lines.forEach { line ->
                c.drawText(line, 60f, y, bodyPaint)
                y += 33f
            }
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
    }

    fun exportPagesToPngZip(bitmaps: List<Bitmap>, out: OutputStream) {
        ZipOutputStream(out).use { zos ->
            bitmaps.forEachIndexed { idx, bmp ->
                zos.putNextEntry(ZipEntry("page_${"%03d".format(idx + 1)}.png"))
                zos.write(ImageStudioEngine.compressToBytes(bmp, ExportImageFormat.PNG, 100))
                zos.closeEntry()
            }
        }
    }

    fun deepAuditAndExtract(context: Context, uri: Uri, inspect: PdfInspectResult): PdfDeepAudit {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val latin = String(raw, Charsets.ISO_8859_1)
        val links = Regex("https?://[^\\s\\)\\]>\"']+").findAll(latin + "\n" + inspect.extractedText)
            .map { it.value }.distinct().take(40).toList()
        val bookmarks = Regex("/Title\\s*\\(([^)]{1,80})\\)").findAll(latin)
            .map { it.groupValues[1].trim() }.distinct().take(40).toList()
        val isEncrypted = "/Encrypt" in latin
        val words = inspect.extractedText.split(Regex("\\s+")).count { it.isNotBlank() }
        val checklist = listOf(
            "Unencrypted / Accessible" to !isEncrypted,
            "Contains Extractable Text" to (words > 5 && !inspect.extractedText.startsWith("No plain")),
            "Has Title Metadata" to inspect.title.isNotBlank(),
            "Has Page Tree (>=1 page)" to (inspect.pageCount >= 1),
            "Clean Author Tag" to inspect.author.isNotBlank()
        )
        return PdfDeepAudit(
            pageCount = inspect.pageCount,
            byteSize = raw.size.toLong(),
            wordCount = words,
            links = links,
            bookmarks = bookmarks,
            isEncryptedFlag = isEncrypted,
            checklist = checklist
        )
    }

    fun diffPdfTextStreams(textA: String, textB: String): String {
        val aLines = textA.lines()
        val bLines = textB.lines()
        val maxN = max(aLines.size, bLines.size)
        val sb = StringBuilder()
        var diffs = 0
        for (i in 0 until maxN) {
            val la = aLines.getOrNull(i)
            val lb = bLines.getOrNull(i)
            if (la != lb) {
                diffs++
                if (la != null) sb.appendLine("- [${i + 1}] $la")
                if (lb != null) sb.appendLine("+ [${i + 1}] $lb")
            }
        }
        return if (diffs == 0) "Text streams match identically (${aLines.size} lines)." else "Found $diffs differing line(s):\n$sb"
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled(); return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                runCatching {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure {
                    callback?.onWriteFailed(it.message)
                }
            }
        }
        printManager.print(jobName, adapter, null)
    }
}
