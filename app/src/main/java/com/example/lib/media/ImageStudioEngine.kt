package com.example.lib.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withRotation
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ImageFilterType(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    WARMTH("Warmth"),
    GAMMA("Gamma"),
    HUE("Hue Shift"),
    SEPIA("Sepia"),
    GRAYSCALE("Grayscale"),
    INVERT("Invert"),
    BLUR("Box Blur"),
    DENOISE("Denoise"),
    SHARPEN("Sharpen"),
    AUTO_ENHANCE("Auto-Enhance"),
    COLORIZE_TINT("Colorize B&W"),
    PIXELATE("Pixelate"),
    POSTERIZE("Posterize"),
    VIGNETTE("Vignette"),
    THRESHOLD("B&W Threshold"),
    DITHER("Floyd-Steinberg Dither"),
    DUOTONE("Swiss Duotone"),
    EMBOSS("Emboss"),
    EDGE("Edge Outline")
}

data class ImageFilterStep(
    val type: ImageFilterType,
    val intensity: Float = 0.5f // 0f..1f
)

enum class ExportImageFormat(val label: String, val ext: String, val mime: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WEBP", "webp", "image/webp"),
    BMP("BMP", "bmp", "image/bmp")
}

enum class StitchLayout(val label: String) {
    VERTICAL("Vertical Strip"),
    HORIZONTAL("Horizontal Strip"),
    GRID_2X2("2×2 Grid")
}

data class ExifSummary(
    val tags: List<Pair<String, String>>,
    val hasGps: Boolean,
    val width: Int,
    val height: Int
)

object ImageStudioEngine {

    fun decodeBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        return runCatching {
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
            val maxSide = max(optsBounds.outWidth, optsBounds.outHeight)
            var sample = 1
            while (maxSide / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            applyExifOrientation(context, uri, raw)
        }.getOrNull()
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    fun serializeFilterChain(steps: List<ImageFilterStep>): String {
        val arr = JSONArray()
        steps.forEach { step ->
            arr.put(JSONObject().apply {
                put("type", step.type.name)
                put("intensity", step.intensity.toDouble())
            })
        }
        return JSONObject().apply {
            put("suite", "OmniDeck-ImageStudio")
            put("filters", arr)
        }.toString()
    }

    fun deserializeFilterChain(jsonStr: String): List<ImageFilterStep> {
        return runCatching {
            val obj = JSONObject(jsonStr)
            val arr = obj.optJSONArray("filters") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val type = ImageFilterType.valueOf(item.getString("type"))
                    val intensity = item.optDouble("intensity", 0.5).toFloat().coerceIn(0f, 1f)
                    add(ImageFilterStep(type, intensity))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun applyFilterChain(source: Bitmap, steps: List<ImageFilterStep>): Bitmap {
        var current = source.copy(Bitmap.Config.ARGB_8888, true)
        steps.forEach { step ->
            current = applySingleFilter(current, step)
        }
        return current
    }

    fun applySingleFilter(src: Bitmap, step: ImageFilterStep): Bitmap {
        val v = step.intensity.coerceIn(0f, 1f)
        return when (step.type) {
            ImageFilterType.BRIGHTNESS -> {
                val shift = (v - 0.5f) * 200f
                applyColorMatrix(src, ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, shift,
                    0f, 1f, 0f, 0f, shift,
                    0f, 0f, 1f, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            ImageFilterType.CONTRAST -> {
                val c = 0.4f + v * 1.6f
                val t = (-0.5f * c + 0.5f) * 255f
                applyColorMatrix(src, ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            ImageFilterType.SATURATION -> {
                val cm = ColorMatrix().apply { setSaturation(v * 2.2f) }
                applyColorMatrix(src, cm)
            }
            ImageFilterType.WARMTH -> {
                val warm = (v - 0.5f) * 60f
                applyColorMatrix(src, ColorMatrix(floatArrayOf(
                    1f + warm / 255f, 0f, 0f, 0f, warm * 0.5f,
                    0f, 1f + warm / 600f, 0f, 0f, 0f,
                    0f, 0f, 1f - warm / 255f, 0f, -warm * 0.5f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            ImageFilterType.GAMMA -> ImageStudioAdvanced.applyGamma(src, gamma = 0.4f + v * 1.6f)
            ImageFilterType.HUE -> {
                val angle = (v - 0.5f) * 2f * PI.toFloat()
                val cosA = cos(angle)
                val sinA = sin(angle)
                val lumR = 0.213f; val lumG = 0.715f; val lumB = 0.072f
                val mat = floatArrayOf(
                    lumR + cosA * (1 - lumR) + sinA * (-lumR), lumG + cosA * (-lumG) + sinA * (-lumG), lumB + cosA * (-lumB) + sinA * (1 - lumB), 0f, 0f,
                    lumR + cosA * (-lumR) + sinA * (0.143f), lumG + cosA * (1 - lumG) + sinA * (0.140f), lumB + cosA * (-lumB) + sinA * (-0.283f), 0f, 0f,
                    lumR + cosA * (-lumR) + sinA * (-(1 - lumR)), lumG + cosA * (-lumG) + sinA * (lumG), lumB + cosA * (1 - lumB) + sinA * (lumB), 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                applyColorMatrix(src, ColorMatrix(mat))
            }
            ImageFilterType.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                applyColorMatrix(src, cm)
            }
            ImageFilterType.SEPIA -> {
                val sep = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                applyColorMatrix(src, sep)
            }
            ImageFilterType.INVERT -> {
                applyColorMatrix(src, ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            ImageFilterType.BLUR -> boxBlur(src, radius = (1 + (v * 10).roundToInt()).coerceIn(1, 12))
            ImageFilterType.DENOISE -> ImageStudioAdvanced.bilateralDenoise(src, strength = v)
            ImageFilterType.SHARPEN -> convolve3x3(src, floatArrayOf(0f, -v, 0f, -v, 1f + 4f * v, -v, 0f, -v, 0f))
            ImageFilterType.AUTO_ENHANCE -> ImageStudioAdvanced.autoEnhanceContrast(src, strength = v)
            ImageFilterType.COLORIZE_TINT -> ImageStudioAdvanced.colorizeWarmPortrait(src, intensity = v)
            ImageFilterType.EMBOSS -> convolve3x3(src, floatArrayOf(-2f * v, -v, 0f, -v, 1f, v, 0f, v, 2f * v))
            ImageFilterType.EDGE -> convolve3x3(src, floatArrayOf(-1f, -1f, -1f, -1f, 8f, -1f, -1f, -1f, -1f))
            ImageFilterType.PIXELATE -> pixelate(src, blockSize = (4 + (v * 36).roundToInt()).coerceIn(2, 48))
            ImageFilterType.POSTERIZE -> posterize(src, levels = (2 + ((1f - v) * 8).roundToInt()).coerceIn(2, 10))
            ImageFilterType.THRESHOLD -> threshold(src, cutoff = (40 + (v * 175).roundToInt()).coerceIn(20, 235))
            ImageFilterType.DITHER -> floydSteinbergDither(src)
            ImageFilterType.DUOTONE -> duotone(src, darkColor = 0xFF141413.toInt(), lightColor = 0xFFFF4F00.toInt())
            ImageFilterType.VIGNETTE -> applyVignette(src, strength = v)
        }
    }

    private fun applyColorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = createBitmap(src.width, src.height)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val outPix = IntArray(w * h)
        val div = radius * 2 + 1

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0
                for (k in -radius..radius) {
                    val sx = (x + k).coerceIn(0, w - 1)
                    val c = pix[rowOff + sx]
                    a += (c ushr 24) and 0xFF
                    r += (c ushr 16) and 0xFF
                    g += (c ushr 8) and 0xFF
                    b += c and 0xFF
                }
                tmp[rowOff + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0; var g = 0; var b = 0; var a = 0
                for (k in -radius..radius) {
                    val sy = (y + k).coerceIn(0, h - 1)
                    val c = tmp[sy * w + x]
                    a += (c ushr 24) and 0xFF
                    r += (c ushr 16) and 0xFF
                    g += (c ushr 8) and 0xFF
                    b += c and 0xFF
                }
                outPix[y * w + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
        return Bitmap.createBitmap(outPix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun convolve3x3(src: Bitmap, kernel: FloatArray): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val outPix = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0f; var g = 0f; var b = 0f
                val alpha = pix[y * w + x] and 0xFF000000.toInt()
                var ki = 0
                for (ky in -1..1) {
                    val sy = (y + ky).coerceIn(0, h - 1)
                    for (kx in -1..1) {
                        val sx = (x + kx).coerceIn(0, w - 1)
                        val c = pix[sy * w + sx]
                        val kv = kernel[ki++]
                        r += ((c ushr 16) and 0xFF) * kv
                        g += ((c ushr 8) and 0xFF) * kv
                        b += (c and 0xFF) * kv
                    }
                }
                val rr = r.roundToInt().coerceIn(0, 255)
                val gg = g.roundToInt().coerceIn(0, 255)
                val bb = b.roundToInt().coerceIn(0, 255)
                outPix[y * w + x] = alpha or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return Bitmap.createBitmap(outPix, w, h, Bitmap.Config.ARGB_8888)
    }

    fun pixelate(src: Bitmap, blockSize: Int): Bitmap {
        val w = max(1, src.width / blockSize)
        val h = max(1, src.height / blockSize)
        val small = src.scale(w, h, false)
        return small.scale(src.width, src.height, false)
    }

    private fun posterize(src: Bitmap, levels: Int): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val step = 255f / (levels - 1).coerceAtLeast(1)
        for (i in pix.indices) {
            val c = pix[i]
            val a = c and 0xFF000000.toInt()
            val r = (((c ushr 16) and 0xFF) / step).roundToInt() * step
            val g = (((c ushr 8) and 0xFF) / step).roundToInt() * step
            val b = ((c and 0xFF) / step).roundToInt() * step
            pix[i] = a or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun threshold(src: Bitmap, cutoff: Int): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        for (i in pix.indices) {
            val c = pix[i]
            val lum = (((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            pix[i] = if (lum >= cutoff) Color.WHITE else Color.BLACK
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun floydSteinbergDither(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val lum = FloatArray(w * h) { i ->
            val c = pix[i]
            (((c ushr 16) and 0xFF) * 0.299f + ((c ushr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f)
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val old = lum[idx]
                val nw = if (old < 128f) 0f else 255f
                val err = old - nw
                pix[idx] = if (nw > 0f) Color.WHITE else Color.BLACK
                if (x + 1 < w) lum[idx + 1] += err * 7f / 16f
                if (y + 1 < h) {
                    if (x > 0) lum[(y + 1) * w + x - 1] += err * 3f / 16f
                    lum[(y + 1) * w + x] += err * 5f / 16f
                    if (x + 1 < w) lum[(y + 1) * w + x + 1] += err * 1f / 16f
                }
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun duotone(src: Bitmap, darkColor: Int, lightColor: Int): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val dr = (darkColor ushr 16) and 0xFF; val dg = (darkColor ushr 8) and 0xFF; val db = darkColor and 0xFF
        val lr = (lightColor ushr 16) and 0xFF; val lg = (lightColor ushr 8) and 0xFF; val lb = lightColor and 0xFF
        for (i in pix.indices) {
            val c = pix[i]
            val t = (((c ushr 16) and 0xFF) * 0.299f + ((c ushr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
            val r = (dr + (lr - dr) * t).roundToInt().coerceIn(0, 255)
            val g = (dg + (lg - dg) * t).roundToInt().coerceIn(0, 255)
            val b = (db + (lb - db) * t).roundToInt().coerceIn(0, 255)
            pix[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyVignette(src: Bitmap, strength: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val cx = out.width / 2f; val cy = out.height / 2f
        val rad = max(cx, cy) * 1.15f
        val alpha = (strength * 220).roundToInt().coerceIn(0, 235)
        val shader = RadialGradient(
            cx, cy, rad,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), Paint().apply { this.shader = shader })
        return out
    }

    fun transformGeometry(
        src: Bitmap,
        rotateDeg: Float = 0f,
        flipH: Boolean = false,
        flipV: Boolean = false,
        aspectRatio: Float? = null, // w/h e.g. 1f, 16/9f
        padWithBlur: Boolean = false
    ): Bitmap {
        val m = Matrix().apply {
            if (flipH || flipV) postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
            if (rotateDeg != 0f) postRotate(rotateDeg)
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (aspectRatio == null) return rotated

        val targetW: Int; val targetH: Int
        if (padWithBlur) {
            if (rotated.width.toFloat() / rotated.height < aspectRatio) {
                targetH = rotated.height
                targetW = (targetH * aspectRatio).roundToInt()
            } else {
                targetW = rotated.width
                targetH = (targetW / aspectRatio).roundToInt()
            }
            val bg = boxBlur(rotated.scale(targetW / 4.coerceAtLeast(1), targetH / 4.coerceAtLeast(1)), 6)
            val scaledBg = bg.scale(targetW, targetH)
            val canvas = Canvas(scaledBg)
            canvas.drawBitmap(rotated, (targetW - rotated.width) / 2f, (targetH - rotated.height) / 2f, null)
            return scaledBg
        } else {
            val curRatio = rotated.width.toFloat() / rotated.height
            val (cw, ch) = if (curRatio > aspectRatio) {
                (rotated.height * aspectRatio).roundToInt() to rotated.height
            } else {
                rotated.width to (rotated.width / aspectRatio).roundToInt()
            }
            val x = ((rotated.width - cw) / 2).coerceAtLeast(0)
            val y = ((rotated.height - ch) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(rotated, x, y, cw.coerceAtMost(rotated.width - x), ch.coerceAtMost(rotated.height - y))
        }
    }

    fun removeBackgroundByCornerChroma(src: Bitmap, tolerance: Int = 48): Bitmap {
        val w = src.width; val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        // Average the 4 corners as key background color
        val corners = listOf(pix[0], pix[w - 1], pix[(h - 1) * w], pix[h * w - 1])
        val bgR = corners.map { (it ushr 16) and 0xFF }.average().toFloat()
        val bgG = corners.map { (it ushr 8) and 0xFF }.average().toFloat()
        val bgB = corners.map { it and 0xFF }.average().toFloat()
        val maxDist = tolerance * 2.2f

        for (i in pix.indices) {
            val c = pix[i]
            val dr = ((c ushr 16) and 0xFF) - bgR
            val dg = ((c ushr 8) and 0xFF) - bgG
            val db = (c and 0xFF) - bgB
            val dist = sqrt(dr * dr + dg * dg + db * db)
            if (dist < maxDist) {
                val feather = ((dist / maxDist).coerceIn(0f, 1f))
                val newAlpha = if (feather < 0.7f) 0 else ((feather - 0.7f) / 0.3f * 255).roundToInt()
                pix[i] = (newAlpha shl 24) or (c and 0x00FFFFFF)
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    fun addWatermark(src: Bitmap, text: String, tiled: Boolean, opacity: Float = 0.45f, color: Int = Color.WHITE): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = (opacity * 255).roundToInt().coerceIn(10, 255)
            this.textSize = max(24f, min(out.width, out.height) * 0.06f)
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        if (tiled) {
            canvas.withRotation(-28f, out.width / 2f, out.height / 2f) {
                val stepX = max(220f, paint.measureText(text) + 80f)
                val stepY = paint.textSize * 3.2f
                var y = -out.height.toFloat()
                while (y < out.height * 2f) {
                    var x = -out.width.toFloat()
                    while (x < out.width * 2f) {
                        drawText(text, x, y, paint)
                        x += stepX
                    }
                    y += stepY
                }
            }
        } else {
            val tw = paint.measureText(text)
            canvas.drawText(text, out.width - tw - 28f, out.height - 28f, paint)
        }
        return out
    }

    fun makeCircleBorderAvatar(src: Bitmap, borderWidthPx: Int = 24, borderColor: Int = 0xFFFF4F00.toInt(), sizePx: Int = 720): Bitmap {
        val minSide = min(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - minSide) / 2, (src.height - minSide) / 2, minSide, minSide)
        val scaled = cropped.scale(sizePx, sizePx)
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius - borderWidthPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        paint.xfermode = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidthPx.toFloat()
        paint.color = borderColor
        canvas.drawCircle(radius, radius, radius - borderWidthPx / 2f, paint)
        return out
    }

    fun stitchCollage(bitmaps: List<Bitmap>, layout: StitchLayout, gapPx: Int = 16, bgColor: Int = Color.WHITE): Bitmap? {
        if (bitmaps.isEmpty()) return null
        val normWidth = 720
        val scaled = bitmaps.map { b ->
            val h = max(1, (b.height.toFloat() * normWidth / b.width).roundToInt())
            b.scale(normWidth, h)
        }
        return when (layout) {
            StitchLayout.VERTICAL -> {
                val totalH = scaled.sumOf { it.height } + gapPx * (scaled.size + 1)
                val out = createBitmap(normWidth + gapPx * 2, totalH)
                val canvas = Canvas(out).apply { drawColor(bgColor) }
                var y = gapPx.toFloat()
                scaled.forEach { b ->
                    canvas.drawBitmap(b, gapPx.toFloat(), y, null)
                    y += b.height + gapPx
                }
                out
            }
            StitchLayout.HORIZONTAL -> {
                val targetH = 640
                val hScaled = bitmaps.map { b ->
                    val w = max(1, (b.width.toFloat() * targetH / b.height).roundToInt())
                    b.scale(w, targetH)
                }
                val totalW = hScaled.sumOf { it.width } + gapPx * (hScaled.size + 1)
                val out = createBitmap(totalW, targetH + gapPx * 2)
                val canvas = Canvas(out).apply { drawColor(bgColor) }
                var x = gapPx.toFloat()
                hScaled.forEach { b ->
                    canvas.drawBitmap(b, x, gapPx.toFloat(), null)
                    x += b.width + gapPx
                }
                out
            }
            StitchLayout.GRID_2X2 -> {
                val cell = 540
                val cols = 2
                val rows = (bitmaps.size + 1) / 2
                val outW = cell * cols + gapPx * (cols + 1)
                val outH = cell * rows + gapPx * (rows + 1)
                val out = createBitmap(outW, outH)
                val canvas = Canvas(out).apply { drawColor(bgColor) }
                bitmaps.forEachIndexed { idx, b ->
                    val r = idx / cols
                    val c = idx % cols
                    val square = b.scale(cell, cell)
                    canvas.drawBitmap(square, (gapPx + c * (cell + gapPx)).toFloat(), (gapPx + r * (cell + gapPx)).toFloat(), null)
                }
                out
            }
        }
    }

    fun renderTextCard(title: String, body: String, bgHex: Int, fgHex: Int, accentHex: Int, width: Int = 1080, height: Int = 1080): Bitmap {
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        canvas.drawColor(bgHex)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentHex }
        canvas.drawRect(72f, 72f, 128f, 128f, accentPaint)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgHex
            textSize = 68f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(title.take(32), 72f, 220f, titlePaint)
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgHex
            textSize = 42f
        }
        var y = 320f
        body.lines().forEach { rawLine ->
            val words = rawLine.split(" ")
            var line = ""
            words.forEach { w ->
                val test = if (line.isEmpty()) w else "$line $w"
                if (bodyPaint.measureText(test) > width - 144f) {
                    canvas.drawText(line, 72f, y, bodyPaint)
                    y += 58f
                    line = w
                } else line = test
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, 72f, y, bodyPaint)
                y += 58f
            }
            if (y > height - 100f) return@forEach
        }
        return out
    }

    fun compressToBytes(bmp: Bitmap, format: ExportImageFormat, quality: Int): ByteArray {
        if (format == ExportImageFormat.BMP) return ImageStudioAdvanced.encodeBmp24(bmp)
        val out = ByteArrayOutputStream()
        val cf = when (format) {
            ExportImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            ExportImageFormat.BMP -> Bitmap.CompressFormat.PNG
        }
        bmp.compress(cf, quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }

    fun readExifSummary(context: Context, uri: Uri): ExifSummary {
        val tags = mutableListOf<Pair<String, String>>()
        var hasGps = false
        var width = 0
        var height = 0
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                val interesting = listOf(
                    "Camera Make" to ExifInterface.TAG_MAKE,
                    "Camera Model" to ExifInterface.TAG_MODEL,
                    "Date Taken" to ExifInterface.TAG_DATETIME_ORIGINAL,
                    "Exposure" to ExifInterface.TAG_EXPOSURE_TIME,
                    "Aperture" to ExifInterface.TAG_F_NUMBER,
                    "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    "Focal Length" to ExifInterface.TAG_FOCAL_LENGTH,
                    "Orientation" to ExifInterface.TAG_ORIENTATION,
                    "Software" to ExifInterface.TAG_SOFTWARE
                )
                interesting.forEach { (label, tag) ->
                    exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { tags += label to it }
                }
                val latLon = exif.latLong
                if (latLon != null) {
                    hasGps = true
                    tags += "GPS Coordinates" to "${"%.5f".format(latLon[0])}, ${"%.5f".format(latLon[1])}"
                }
            }
        }
        return ExifSummary(tags, hasGps, width, height)
    }

    // Pure-Kotlin GIF89a Animated GIF Encoder (6x6x6 Web-safe 216-color cube + LZW)
    fun encodeAnimatedGif(frames: List<Bitmap>, delayMs: Int, out: OutputStream, maxSize: Int = 360) {
        if (frames.isEmpty()) return
        val first = frames.first()
        val scale = min(1f, maxSize.toFloat() / max(first.width, first.height))
        val w = max(16, (first.width * scale).roundToInt())
        val h = max(16, (first.height * scale).roundToInt())

        // Write GIF89a header
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeLe16(out, w)
        writeLe16(out, h)
        // Global Color Table flag (1), color res (7), sort (0), size (7 -> 256 colors) => 0xF7
        out.write(0xF7)
        out.write(0) // bg index
        out.write(0) // aspect ratio

        // Build 256-entry Global Color Table (216 6x6x6 RGB cube + 40 grayscale ramp)
        for (r in 0..5) for (g in 0..5) for (b in 0..5) {
            out.write(r * 51); out.write(g * 51); out.write(b * 51)
        }
        for (i in 0 until 40) {
            val v = (i * 255) / 39
            out.write(v); out.write(v); out.write(v)
        }

        // Netscape 2.0 infinite loop extension
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); writeLe16(out, 0); out.write(0)

        val delayCs = max(2, delayMs / 10)
        val pix = IntArray(w * h)
        val indexed = ByteArray(w * h)

        frames.forEach { frame ->
            val scaled = frame.scale(w, h)
            scaled.getPixels(pix, 0, w, 0, 0, w, h)
            for (i in pix.indices) {
                val c = pix[i]
                val r = (((c ushr 16) and 0xFF) * 5 + 127) / 255
                val g = (((c ushr 8) and 0xFF) * 5 + 127) / 255
                val b = ((c and 0xFF) * 5 + 127) / 255
                indexed[i] = (r * 36 + g * 6 + b).toByte()
            }
            // Graphic Control Extension
            out.write(0x21); out.write(0xF9); out.write(4)
            out.write(0x04) // disposal = do not dispose
            writeLe16(out, delayCs)
            out.write(0); out.write(0)

            // Image Descriptor
            out.write(0x2C)
            writeLe16(out, 0); writeLe16(out, 0); writeLe16(out, w); writeLe16(out, h)
            out.write(0) // no local color table

            writeGifLzw(out, indexed, 8)
        }
        out.write(0x3B) // Trailer
        out.flush()
    }

    private fun writeLe16(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeGifLzw(out: OutputStream, pixels: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clearCode = 1 shl minCodeSize
        val eofCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = eofCode + 1
        val dict = HashMap<Int, Int>(4096)

        val blockBuf = ByteArray(256)
        var blockLen = 0
        var bitAcc = 0
        var bitCount = 0

        fun emitByte(b: Int) {
            blockBuf[blockLen++] = b.toByte()
            if (blockLen == 255) {
                out.write(255)
                out.write(blockBuf, 0, 255)
                blockLen = 0
            }
        }

        fun emitCode(code: Int) {
            bitAcc = bitAcc or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                emitByte(bitAcc and 0xFF)
                bitAcc = bitAcc ushr 8
                bitCount -= 8
            }
        }

        emitCode(clearCode)
        if (pixels.isNotEmpty()) {
            var prefix = pixels[0].toInt() and 0xFF
            for (i in 1 until pixels.size) {
                val k = pixels[i].toInt() and 0xFF
                val key = (prefix shl 12) or k
                val found = dict[key]
                if (found != null) {
                    prefix = found
                } else {
                    emitCode(prefix)
                    if (nextCode < 4096) {
                        dict[key] = nextCode++
                        if (nextCode > (1 shl codeSize) && codeSize < 12) {
                            codeSize++
                        }
                    } else {
                        emitCode(clearCode)
                        dict.clear()
                        codeSize = minCodeSize + 1
                        nextCode = eofCode + 1
                    }
                    prefix = k
                }
            }
            emitCode(prefix)
        }
        emitCode(eofCode)
        if (bitCount > 0) emitByte(bitAcc and 0xFF)
        if (blockLen > 0) {
            out.write(blockLen)
            out.write(blockBuf, 0, blockLen)
        }
        out.write(0)
    }
}
