package com.example.lib.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class FilterPresetPack(
    val name: String,
    val steps: List<ImageFilterStep>
)

object ImageStudioAdvanced {

    val BUILTIN_PRESETS: List<FilterPresetPack> = listOf(
        FilterPresetPack(
            "Swiss Editorial",
            listOf(
                ImageFilterStep(ImageFilterType.CONTRAST, 0.72f),
                ImageFilterStep(ImageFilterType.SATURATION, 0.35f),
                ImageFilterStep(ImageFilterType.SHARPEN, 0.30f)
            )
        ),
        FilterPresetPack(
            "Kodak Warm",
            listOf(
                ImageFilterStep(ImageFilterType.WARMTH, 0.78f),
                ImageFilterStep(ImageFilterType.CONTRAST, 0.62f),
                ImageFilterStep(ImageFilterType.VIGNETTE, 0.45f)
            )
        ),
        FilterPresetPack(
            "High Contrast Mono",
            listOf(
                ImageFilterStep(ImageFilterType.GRAYSCALE, 1f),
                ImageFilterStep(ImageFilterType.CONTRAST, 0.85f),
                ImageFilterStep(ImageFilterType.VIGNETTE, 0.35f)
            )
        ),
        FilterPresetPack(
            "Auto-Enhance",
            listOf(
                ImageFilterStep(ImageFilterType.AUTO_ENHANCE, 0.85f),
                ImageFilterStep(ImageFilterType.SHARPEN, 0.25f)
            )
        ),
        FilterPresetPack(
            "Retro Dither",
            listOf(
                ImageFilterStep(ImageFilterType.CONTRAST, 0.65f),
                ImageFilterStep(ImageFilterType.DITHER, 1f)
            )
        )
    )

    /** Pure-Kotlin 24-bit uncompressed BMP encoder (testable on JVM too). */
    fun encodeBmp24FromPixels(width: Int, height: Int, argbPixels: IntArray): ByteArray {
        val rowBytes = width * 3
        val rowPad = (4 - (rowBytes % 4)) % 4
        val pixelDataSize = (rowBytes + rowPad) * height
        val fileSize = 54 + pixelDataSize
        val out = ByteArrayOutputStream(fileSize)

        fun writeLe16(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }
        fun writeLe32(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 24) and 0xFF)
        }

        // BITMAPFILEHEADER (14 bytes)
        out.write('B'.code); out.write('M'.code)
        writeLe32(fileSize)
        writeLe32(0)
        writeLe32(54)

        // BITMAPINFOHEADER (40 bytes)
        writeLe32(40)
        writeLe32(width)
        writeLe32(height)
        writeLe16(1) // planes
        writeLe16(24) // bpp
        writeLe32(0) // BI_RGB
        writeLe32(pixelDataSize)
        writeLe32(2835) // 72 DPI X
        writeLe32(2835) // 72 DPI Y
        writeLe32(0)
        writeLe32(0)

        // Bottom-up BGR rows
        for (y in height - 1 downTo 0) {
            val rowStart = y * width
            for (x in 0 until width) {
                val c = argbPixels[rowStart + x]
                out.write(c and 0xFF) // B
                out.write((c ushr 8) and 0xFF) // G
                out.write((c ushr 16) and 0xFF) // R
            }
            repeat(rowPad) { out.write(0) }
        }
        return out.toByteArray()
    }

    fun encodeBmp24(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        return encodeBmp24FromPixels(w, h, pix)
    }

    /** Perceptual 64-bit difference hash (9x8 grayscale luminance gradients). */
    fun computeDHashFromLums(lums9x8: IntArray): Long {
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            val row = y * 9
            for (x in 0 until 8) {
                if (lums9x8[row + x] < lums9x8[row + x + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun computeDHash(bmp: Bitmap): Long {
        val tiny = bmp.scale(9, 8)
        val pix = IntArray(72)
        tiny.getPixels(pix, 0, 9, 0, 0, 9, 8)
        val lums = IntArray(72) { i ->
            val c = pix[i]
            (((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        return computeDHashFromLums(lums)
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun applyGamma(src: Bitmap, gamma: Float): Bitmap {
        val g = gamma.coerceIn(0.2f, 3.0f)
        val lut = IntArray(256) { i ->
            ((i / 255.0).pow(1.0 / g) * 255.0).roundToInt().coerceIn(0, 255)
        }
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        for (i in pix.indices) {
            val c = pix[i]
            val a = c and 0xFF000000.toInt()
            val r = lut[(c ushr 16) and 0xFF]
            val gg = lut[(c ushr 8) and 0xFF]
            val b = lut[c and 0xFF]
            pix[i] = a or (r shl 16) or (gg shl 8) or b
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    fun autoEnhanceContrast(src: Bitmap, strength: Float): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        var minLum = 255; var maxLum = 0
        for (i in pix.indices step 2) {
            val c = pix[i]
            val lum = (((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }
        val span = (maxLum - minLum).coerceAtLeast(16)
        val blend = strength.coerceIn(0f, 1f)
        for (i in pix.indices) {
            val c = pix[i]
            val a = c and 0xFF000000.toInt()
            val r0 = (c ushr 16) and 0xFF
            val g0 = (c ushr 8) and 0xFF
            val b0 = c and 0xFF
            val r1 = ((r0 - minLum) * 255 / span).coerceIn(0, 255)
            val g1 = ((g0 - minLum) * 255 / span).coerceIn(0, 255)
            val b1 = ((b0 - minLum) * 255 / span).coerceIn(0, 255)
            val r = (r0 + (r1 - r0) * blend).roundToInt().coerceIn(0, 255)
            val g = (g0 + (g1 - g0) * blend).roundToInt().coerceIn(0, 255)
            val b = (b0 + (b1 - b0) * blend).roundToInt().coerceIn(0, 255)
            pix[i] = a or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Edge-preserving bilateral filter (fast 5x5 range + spatial approximation). */
    fun bilateralDenoise(src: Bitmap, strength: Float): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val sigmaColor = 20f + strength * 65f
        val invTwoSigmaSq = 1f / (2f * sigmaColor * sigmaColor)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val center = pix[y * w + x]
                val a = center and 0xFF000000.toInt()
                val cr = (center ushr 16) and 0xFF
                val cg = (center ushr 8) and 0xFF
                val cb = center and 0xFF
                var sumW = 0f; var sumR = 0f; var sumG = 0f; var sumB = 0f
                for (ky in -2..2) {
                    val sy = (y + ky).coerceIn(0, h - 1)
                    for (kx in -2..2) {
                        val sx = (x + kx).coerceIn(0, w - 1)
                        val c = pix[sy * w + sx]
                        val r = (c ushr 16) and 0xFF
                        val g = (c ushr 8) and 0xFF
                        val b = c and 0xFF
                        val dr = (r - cr).toFloat()
                        val dg = (g - cg).toFloat()
                        val db = (b - cb).toFloat()
                        val distSq = dr * dr + dg * dg + db * db
                        val weight = kotlin.math.exp(-distSq * invTwoSigmaSq)
                        sumW += weight
                        sumR += r * weight
                        sumG += g * weight
                        sumB += b * weight
                    }
                }
                val nr = (sumR / sumW).roundToInt().coerceIn(0, 255)
                val ng = (sumG / sumW).roundToInt().coerceIn(0, 255)
                val nb = (sumB / sumW).roundToInt().coerceIn(0, 255)
                out[y * w + x] = a or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Multi-band luminance-mapped warm portrait & foliage colorizer for B&W photos. */
    fun colorizeWarmPortrait(src: Bitmap, intensity: Float): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val t = intensity.coerceIn(0f, 1f)
        for (i in pix.indices) {
            val c = pix[i]
            val a = c and 0xFF000000.toInt()
            val r0 = (c ushr 16) and 0xFF
            val g0 = (c ushr 8) and 0xFF
            val b0 = c and 0xFF
            val lum = (r0 * 0.299f + g0 * 0.587f + b0 * 0.114f) / 255f
            // Map shadows->cool slate, midtones->warm peach/amber, highlights->cream white
            val cr = when {
                lum < 0.35f -> lum * 255f * 0.88f
                lum < 0.78f -> (lum * 255f * 1.14f).coerceAtMost(255f)
                else -> (lum * 255f * 1.03f).coerceAtMost(255f)
            }
            val cg = when {
                lum < 0.35f -> lum * 255f * 0.94f
                lum < 0.78f -> (lum * 255f * 0.98f).coerceAtMost(255f)
                else -> (lum * 255f * 1.00f).coerceAtMost(255f)
            }
            val cb = when {
                lum < 0.35f -> (lum * 255f * 1.16f).coerceAtMost(255f)
                lum < 0.78f -> lum * 255f * 0.82f
                else -> lum * 255f * 0.93f
            }
            val nr = (r0 + (cr - r0) * t).roundToInt().coerceIn(0, 255)
            val ng = (g0 + (cg - g0) * t).roundToInt().coerceIn(0, 255)
            val nb = (b0 + (cb - b0) * t).roundToInt().coerceIn(0, 255)
            pix[i] = a or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    fun upscale2xSharpened(src: Bitmap): Bitmap {
        val tw = (src.width * 2).coerceAtMost(3840)
        val th = (src.height * 2).coerceAtMost(3840)
        val scaled = src.scale(tw, th)
        return ImageStudioEngine.applySingleFilter(scaled, ImageFilterStep(ImageFilterType.SHARPEN, 0.32f))
    }

    fun roundCornersBorderExpand(
        src: Bitmap,
        cornerPct: Int = 0,
        borderPx: Int = 0,
        borderColor: Int = Color.BLACK,
        expandPct: Int = 0,
        expandColor: Int = Color.WHITE,
        circleCrop: Boolean = false
    ): Bitmap {
        val working = if (circleCrop) {
            val side = min(src.width, src.height)
            Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        } else src

        val pad = if (expandPct > 0) (min(working.width, working.height) * expandPct / 200f).roundToInt() else 0
        val outW = working.width + pad * 2 + borderPx * 2
        val outH = working.height + pad * 2 + borderPx * 2
        val out = createBitmap(outW, outH)
        val canvas = Canvas(out)
        if (pad > 0) canvas.drawColor(expandColor)

        val rect = RectF(
            (pad + borderPx).toFloat(),
            (pad + borderPx).toFloat(),
            (outW - pad - borderPx).toFloat(),
            (outH - pad - borderPx).toFloat()
        )
        val radius = if (circleCrop) min(rect.width(), rect.height()) / 2f else (min(rect.width(), rect.height()) * (cornerPct / 200f))

        if (radius > 0f) {
            val clipped = createBitmap(rect.width().toInt(), rect.height().toInt())
            val cc = Canvas(clipped)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            cc.drawRoundRect(RectF(0f, 0f, rect.width(), rect.height()), radius, radius, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            cc.drawBitmap(working, 0f, 0f, p)
            canvas.drawBitmap(clipped, rect.left, rect.top, null)
        } else {
            canvas.drawBitmap(working, rect.left, rect.top, null)
        }

        if (borderPx > 0) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderPx.toFloat()
                color = borderColor
            }
            val half = borderPx / 2f
            val bRect = RectF(rect.left - half, rect.top - half, rect.right + half, rect.bottom + half)
            if (radius > 0f) canvas.drawRoundRect(bRect, radius + half, radius + half, bp)
            else canvas.drawRect(bRect, bp)
        }
        return out
    }

    fun buildPassportSheet(src: Bitmap, cols: Int = 2, rows: Int = 3): Bitmap {
        // Standard 35x45mm aspect (~7:9) tile repeated cleanly on a 1200x1800 4x6 printable canvas
        val cellW = 420; val cellH = 540
        val gap = 48
        val sheetW = cols * cellW + (cols + 1) * gap
        val sheetH = rows * cellH + (rows + 1) * gap
        val cropTile = ImageStudioEngine.transformGeometry(src, aspectRatio = cellW.toFloat() / cellH.toFloat())
        val scaledTile = cropTile.scale(cellW, cellH)
        val out = createBitmap(sheetW, sheetH)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val cutPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
        for (r in 0 until rows) {
            for (col in 0 until cols) {
                val x = (gap + col * (cellW + gap)).toFloat()
                val y = (gap + r * (cellH + gap)).toFloat()
                c.drawBitmap(scaledTile, x, y, null)
                c.drawRect(x, y, x + cellW, y + cellH, cutPaint)
            }
        }
        return out
    }

    fun splitIntoGrid(src: Bitmap, cols: Int, rows: Int): List<Bitmap> {
        val cw = (src.width / cols).coerceAtLeast(1)
        val ch = (src.height / rows).coerceAtLeast(1)
        return buildList {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    add(Bitmap.createBitmap(src, c * cw, r * ch, cw, ch))
                }
            }
        }
    }

    fun buildSpriteSheet(frames: List<Bitmap>, cols: Int = 4, cellPx: Int = 256): Bitmap? {
        if (frames.isEmpty()) return null
        val cCount = cols.coerceIn(1, frames.size)
        val rows = (frames.size + cCount - 1) / cCount
        val out = createBitmap(cCount * cellPx, rows * cellPx)
        val canvas = Canvas(out)
        frames.forEachIndexed { idx, b ->
            val r = idx / cCount
            val col = idx % cCount
            val tile = b.scale(cellPx, cellPx)
            canvas.drawBitmap(tile, (col * cellPx).toFloat(), (r * cellPx).toFloat(), null)
        }
        return out
    }

    fun compressToTargetKb(bmp: Bitmap, format: ExportImageFormat, targetKb: Int): Pair<ByteArray, Int> {
        val targetBytes = targetKb.coerceAtLeast(8) * 1024
        if (format == ExportImageFormat.PNG || format == ExportImageFormat.BMP) {
            var working = bmp
            repeat(5) {
                val bytes = ImageStudioEngine.compressToBytes(working, format, 100)
                if (bytes.size <= targetBytes || min(working.width, working.height) < 120) return bytes to 100
                working = working.scale((working.width * 0.78f).toInt(), (working.height * 0.78f).toInt())
            }
            return ImageStudioEngine.compressToBytes(working, format, 100) to 100
        }
        var low = 10; var high = 98
        var bestBytes = ImageStudioEngine.compressToBytes(bmp, format, 10)
        var bestQual = 10
        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = ImageStudioEngine.compressToBytes(bmp, format, mid)
            if (bytes.size <= targetBytes) {
                bestBytes = bytes; bestQual = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (bestBytes.size > targetBytes && min(bmp.width, bmp.height) > 200) {
            val down = bmp.scale((bmp.width * 0.7f).toInt(), (bmp.height * 0.7f).toInt())
            val fallback = ImageStudioEngine.compressToBytes(down, format, 70)
            if (fallback.size < bestBytes.size) return fallback to 70
        }
        return bestBytes to bestQual
    }

    fun batchConvertToZip(
        items: List<Bitmap>,
        format: ExportImageFormat,
        quality: Int,
        prefix: String,
        out: OutputStream
    ) {
        val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "omnideck" }
        ZipOutputStream(out).use { zos ->
            items.forEachIndexed { idx, bmp ->
                val bytes = ImageStudioEngine.compressToBytes(bmp, format, quality)
                val name = "${cleanPrefix}_${"%03d".format(idx + 1)}.${format.ext}"
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    fun buildFaviconZip(src: Bitmap, out: OutputStream) {
        val sizes = listOf(16, 32, 48, 64, 128, 180, 192, 512)
        ZipOutputStream(out).use { zos ->
            sizes.forEach { sz ->
                val resized = src.scale(sz, sz)
                val bytes = ImageStudioEngine.compressToBytes(resized, ExportImageFormat.PNG, 100)
                val fileName = if (sz == 180) "apple-touch-icon.png" else "icon-${sz}x${sz}.png"
                zos.putNextEntry(ZipEntry(fileName))
                zos.write(bytes)
                zos.closeEntry()
            }
            val html = """
                <link rel="icon" type="image/png" sizes="32x32" href="icon-32x32.png">
                <link rel="icon" type="image/png" sizes="192x192" href="icon-192x192.png">
                <link rel="apple-touch-icon" sizes="180x180" href="apple-touch-icon.png">
            """.trimIndent().toByteArray()
            zos.putNextEntry(ZipEntry("favicon_tags.html"))
            zos.write(html)
            zos.closeEntry()
        }
    }

    fun buildSrcsetZip(src: Bitmap, baseName: String, format: ExportImageFormat, quality: Int, out: OutputStream) {
        val widths = listOf(320, 640, 1024, 1600)
        val clean = baseName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "image" }
        ZipOutputStream(out).use { zos ->
            val srcSetEntries = mutableListOf<String>()
            widths.forEach { w ->
                val targetW = w.coerceAtMost(src.width.coerceAtLeast(320))
                val targetH = max(1, (src.height.toFloat() * targetW / src.width).roundToInt())
                val scaled = src.scale(targetW, targetH)
                val fileName = "${clean}-${w}w.${format.ext}"
                zos.putNextEntry(ZipEntry(fileName))
                zos.write(ImageStudioEngine.compressToBytes(scaled, format, quality))
                zos.closeEntry()
                srcSetEntries += "$fileName ${w}w"
            }
            val snippet = """<img src="${clean}-640w.${format.ext}" srcset="${srcSetEntries.joinToString(", ")}" sizes="(max-width: 768px) 100vw, 1024px" loading="lazy" alt="$clean" />"""
            zos.putNextEntry(ZipEntry("responsive_img.html"))
            zos.write(snippet.toByteArray())
            zos.closeEntry()
        }
    }

    fun extractPalette(src: Bitmap, maxColors: Int = 6): List<Int> {
        val small = src.scale(48, 48)
        val pix = IntArray(48 * 48)
        small.getPixels(pix, 0, 48, 0, 0, 48, 48)
        val buckets = HashMap<Int, Int>()
        pix.forEach { c ->
            if (((c ushr 24) and 0xFF) > 128) {
                val r = ((c ushr 16) and 0xFF) and 0xE0
                val g = ((c ushr 8) and 0xFF) and 0xE0
                val b = (c and 0xFF) and 0xE0
                val key = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                buckets[key] = (buckets[key] ?: 0) + 1
            }
        }
        return buckets.entries.sortedByDescending { it.value }.take(maxColors).map { it.key }
    }

    fun renderMemeOverlay(src: Bitmap, topText: String, bottomText: String, color: Int = Color.WHITE): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val sizePx = max(32f, min(out.width, out.height) * 0.085f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = sizePx
            this.textAlign = Paint.Align.CENTER
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val strokePaint = Paint(fillPaint).apply {
            this.color = Color.BLACK
            this.style = Paint.Style.STROKE
            this.strokeWidth = max(4f, sizePx * 0.14f)
        }
        if (topText.isNotBlank()) {
            val y = sizePx + 24f
            canvas.drawText(topText.uppercase(), out.width / 2f, y, strokePaint)
            canvas.drawText(topText.uppercase(), out.width / 2f, y, fillPaint)
        }
        if (bottomText.isNotBlank()) {
            val y = out.height - 28f
            canvas.drawText(bottomText.uppercase(), out.width / 2f, y, strokePaint)
            canvas.drawText(bottomText.uppercase(), out.width / 2f, y, fillPaint)
        }
        return out
    }

    fun replaceBackground(src: Bitmap, tolerance: Int, replaceColor: Int?, useBlurBg: Boolean): Bitmap {
        val cutout = ImageStudioEngine.removeBackgroundByCornerChroma(src, tolerance)
        if (replaceColor == null && !useBlurBg) return cutout
        val out = createBitmap(src.width, src.height)
        val c = Canvas(out)
        if (useBlurBg) {
            val blurred = ImageStudioEngine.boxBlur(src, 12)
            c.drawBitmap(blurred, 0f, 0f, null)
        } else if (replaceColor != null) {
            c.drawColor(replaceColor)
        }
        c.drawBitmap(cutout, 0f, 0f, null)
        return out
    }

    fun extractVideoFrames(context: Context, videoUri: Uri, count: Int = 8): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        val mmr = MediaMetadataRetriever()
        runCatching {
            mmr.setDataSource(context, videoUri)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 4000L
            val step = (durMs * 1000L / count.coerceAtLeast(1)).coerceAtLeast(100_000L)
            for (i in 0 until count) {
                val bmp = mmr.getFrameAtTime(i * step, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) frames += bmp.scale(480, max(1, (bmp.height * 480f / bmp.width).roundToInt()))
            }
        }
        runCatching { mmr.release() }
        return frames
    }
}
