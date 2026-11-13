package com.example.lib.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Inflater

data class PdfPageTransform(
    val sourceDocIndex: Int,
    val sourcePageIndex: Int,
    val rotationDeg: Int = 0,
    val deleted: Boolean = false
)

data class PdfAnnotationOverlay(
    val pageIndex: Int,
    val redactions: List<RectF> = emptyList(), // normalized 0..1 coordinates
    val watermarkText: String = "",
    val includePageNumbers: Boolean = false,
    val signatureBitmap: Bitmap? = null,
    val signatureNormRect: RectF? = null
)

data class PdfInspectResult(
    val pageCount: Int,
    val byteSize: Long,
    val producer: String,
    val title: String,
    val author: String,
    val extractedText: String
)

object PdfLabEngine {

    fun renderPdfPages(
        context: Context,
        uri: Uri,
        maxThumbWidth: Int = 900,
        limitPages: Int = 40
    ): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = minOf(renderer.pageCount, limitPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val scale = maxThumbWidth.toFloat() / page.width.coerceAtLeast(1)
                            val w = (page.width * scale).toInt().coerceIn(64, 2400)
                            val h = (page.height * scale).toInt().coerceIn(64, 3400)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result += bmp
                        }
                    }
                }
            }
        }
        return result
    }

    fun getPdfPageCount(context: Context, uri: Uri): Int =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            } ?: 0
        }.getOrDefault(0)

    fun bitmapsToPdf(bitmaps: List<Bitmap>, out: OutputStream, jpegQuality: Int = 90) {
        val doc = PdfDocument()
        try {
            bitmaps.forEachIndexed { idx, raw ->
                val bmp = if (jpegQuality < 95) {
                    val compressed = ImageStudioEngine.compressToBytes(raw, ExportImageFormat.JPEG, jpegQuality)
                    android.graphics.BitmapFactory.decodeByteArray(compressed, 0, compressed.size) ?: raw
                } else raw
                val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, idx + 1).create()
                val page = doc.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    fun buildTransformedPdf(
        context: Context,
        sourceUris: List<Uri>,
        plan: List<PdfPageTransform>,
        renderWidth: Int = 1240,
        jpegQuality: Int = 88,
        overlays: List<PdfAnnotationOverlay> = emptyList(),
        out: OutputStream
    ) {
        val doc = PdfDocument()
        val pfds = mutableListOf<ParcelFileDescriptor>()
        val renderers = mutableListOf<PdfRenderer>()
        try {
            sourceUris.forEach { uri ->
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("Cannot open PDF")
                pfds += pfd
                renderers += PdfRenderer(pfd)
            }

            val active = plan.filter { !it.deleted }
            active.forEachIndexed { outIdx, step ->
                val renderer = renderers.getOrNull(step.sourceDocIndex) ?: return@forEachIndexed
                if (step.sourcePageIndex !in 0 until renderer.pageCount) return@forEachIndexed
                renderer.openPage(step.sourcePageIndex).use { page ->
                    val scale = renderWidth.toFloat() / page.width.coerceAtLeast(1)
                    val w = (page.width * scale).toInt().coerceIn(200, 2200)
                    val h = (page.height * scale).toInt().coerceIn(200, 3000)
                    var bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    if (step.rotationDeg % 360 != 0) {
                        val m = Matrix().apply { postRotate(step.rotationDeg.toFloat()) }
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }

                    // Apply optional compression
                    if (jpegQuality < 95) {
                        val bytes = ImageStudioEngine.compressToBytes(bmp, ExportImageFormat.JPEG, jpegQuality)
                        bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bmp
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, outIdx + 1).create()
                    val outPage = doc.startPage(pageInfo)
                    val canvas = outPage.canvas
                    canvas.drawBitmap(bmp, 0f, 0f, null)

                    // Apply redactions, signature, watermarks, page numbers
                    val overlay = overlays.firstOrNull { it.pageIndex == outIdx } ?: overlays.firstOrNull { it.pageIndex == -1 }
                    if (overlay != null) {
                        val redPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                        overlay.redactions.forEach { r ->
                            canvas.drawRect(
                                r.left * bmp.width,
                                r.top * bmp.height,
                                r.right * bmp.width,
                                r.bottom * bmp.height,
                                redPaint
                            )
                        }
                        if (overlay.signatureBitmap != null && overlay.signatureNormRect != null) {
                            val sr = overlay.signatureNormRect
                            val dst = RectF(
                                sr.left * bmp.width,
                                sr.top * bmp.height,
                                sr.right * bmp.width,
                                sr.bottom * bmp.height
                            )
                            canvas.drawBitmap(overlay.signatureBitmap, null, dst, null)
                        }
                        if (overlay.watermarkText.isNotBlank()) {
                            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.argb(55, 20, 20, 19)
                                textSize = bmp.width * 0.06f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.save()
                            canvas.rotate(-32f, bmp.width / 2f, bmp.height / 2f)
                            canvas.drawText(overlay.watermarkText, bmp.width / 2f, bmp.height / 2f, wmPaint)
                            canvas.restore()
                        }
                        if (overlay.includePageNumbers) {
                            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.DKGRAY
                                textSize = bmp.width * 0.022f
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.drawText("${outIdx + 1} / ${active.size}", bmp.width / 2f, bmp.height - 28f, numPaint)
                        }
                    }
                    doc.finishPage(outPage)
                }
            }
            doc.writeTo(out)
        } finally {
            doc.close()
            renderers.forEach { runCatching { it.close() } }
            pfds.forEach { runCatching { it.close() } }
        }
    }

    fun inspectAndExtractText(context: Context, uri: Uri): PdfInspectResult {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val count = getPdfPageCount(context, uri)
        val text = String(raw, Charsets.ISO_8859_1)

        fun extractInfoField(key: String): String {
            val m = Regex("/$key\\s*\\(([^)]{1,120})\\)").find(text)
            return m?.groupValues?.get(1)?.trim().orEmpty()
        }

        val extracted = extractTextFromPdfBytes(raw)
        return PdfInspectResult(
            pageCount = count,
            byteSize = raw.size.toLong(),
            producer = extractInfoField("Producer"),
            title = extractInfoField("Title"),
            author = extractInfoField("Author"),
            extractedText = extracted.ifBlank { "No plain/Flate text streams found (document may be scanned image pages)." }
        )
    }

    /**
     * Pure-Kotlin PDF stream decompressor + text operator parser (BT ... ET / Tj / TJ).
     */
    fun extractTextFromPdfBytes(rawBytes: ByteArray): String {
        val latin = String(rawBytes, Charsets.ISO_8859_1)
        val out = StringBuilder()

        fun parseTextOperators(content: String) {
            val tjMatches = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)\\s*Tj").findAll(content)
            tjMatches.forEach { m ->
                out.append(unescapePdfString(m.groupValues[1])).append(' ')
            }
            val tjArrMatches = Regex("\\[(.*?)\\]\\s*TJ", RegexOption.DOT_MATCHES_ALL).findAll(content)
            tjArrMatches.forEach { m ->
                Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)").findAll(m.groupValues[1]).forEach { inner ->
                    out.append(unescapePdfString(inner.groupValues[1]))
                }
                out.append(' ')
            }
            if (tjMatches.any() || tjArrMatches.any()) out.append('\n')
        }

        // First pass: uncompressed streams
        parseTextOperators(latin)

        // Second pass: FlateDecode streams
        var cursor = 0
        while (cursor < latin.length) {
            val streamIdx = latin.indexOf("stream", cursor)
            if (streamIdx < 0) break
            var dataStart = streamIdx + 6
            if (dataStart < latin.length && latin[dataStart] == '\r') dataStart++
            if (dataStart < latin.length && latin[dataStart] == '\n') dataStart++
            val endIdx = latin.indexOf("endstream", dataStart)
            if (endIdx < 0) break
            val chunk = rawBytes.copyOfRange(dataStart, endIdx)
            runCatching {
                val inflater = Inflater()
                inflater.setInput(chunk)
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buf)
                    if (n == 0) break
                    baos.write(buf, 0, n)
                }
                inflater.end()
                val decoded = String(baos.toByteArray(), Charsets.ISO_8859_1)
                if ("Tj" in decoded || "TJ" in decoded) parseTextOperators(decoded)
            }
            cursor = endIdx + 9
        }
        return out.toString().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun unescapePdfString(s: String): String = s
        .replace("\\(", "(")
        .replace("\\)", ")")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "")
        .replace("\\t", " ")
}
