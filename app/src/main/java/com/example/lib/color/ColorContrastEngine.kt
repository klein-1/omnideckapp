package com.example.lib.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class RgbColor(val r: Int, val g: Int, val b: Int) {
    val hex: String get() = "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

data class ContrastEvaluation(
    val ratio: Double,
    val passAaNormal: Boolean,
    val passAaaNormal: Boolean,
    val passAaLarge: Boolean
)

object ColorContrastEngine {
    fun parseHex(hex: String): RgbColor? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6 || !clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return null
        }
        return RgbColor(
            r = clean.substring(0, 2).toInt(16),
            g = clean.substring(2, 4).toInt(16),
            b = clean.substring(4, 6).toInt(16)
        )
    }

    fun rgbToHsl(c: RgbColor): Triple<Int, Int, Int> {
        val rf = c.r / 255.0
        val gf = c.g / 255.0
        val bf = c.b / 255.0
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val l = (maxC + minC) / 2.0
        var h = 0.0
        var s = 0.0
        if (maxC != minC) {
            val d = maxC - minC
            s = if (l > 0.5) d / (2.0 - maxC - minC) else d / (maxC + minC)
            h = when (maxC) {
                rf -> (gf - bf) / d + (if (gf < bf) 6.0 else 0.0)
                gf -> (bf - rf) / d + 2.0
                else -> (rf - gf) / d + 4.0
            }
            h /= 6.0
        }
        return Triple((h * 360).roundToInt(), (s * 100).roundToInt(), (l * 100).roundToInt())
    }

    fun relativeLuminance(c: RgbColor): Double {
        fun lin(channel: Int): Double {
            val v = channel / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b)
    }

    fun evaluateContrast(fg: RgbColor, bg: RgbColor): ContrastEvaluation {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val brighter = max(l1, l2)
        val darker = min(l1, l2)
        val ratio = (brighter + 0.05) / (darker + 0.05)
        return ContrastEvaluation(
            ratio = ratio,
            passAaNormal = ratio >= 4.5,
            passAaaNormal = ratio >= 7.0,
            passAaLarge = ratio >= 3.0
        )
    }

    fun generateShades(base: RgbColor): List<RgbColor> {
        val factors = listOf(0.85, 0.45, 0.0, -0.35, -0.65)
        return factors.map { f ->
            if (f >= 0) {
                RgbColor(
                    (base.r + (255 - base.r) * f).roundToInt(),
                    (base.g + (255 - base.g) * f).roundToInt(),
                    (base.b + (255 - base.b) * f).roundToInt()
                )
            } else {
                val keep = 1.0 + f
                RgbColor(
                    (base.r * keep).roundToInt(),
                    (base.g * keep).roundToInt(),
                    (base.b * keep).roundToInt()
                )
            }
        }
    }
}
