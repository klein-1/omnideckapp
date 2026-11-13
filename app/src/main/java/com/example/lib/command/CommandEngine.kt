package com.example.lib.command

import com.example.design.RegionFormat
import com.example.lib.calc.EvalResult
import com.example.lib.calc.ExpressionEvaluator
import com.example.lib.calc.FinanceEngine
import com.example.lib.crypto.HashAlgorithm
import com.example.lib.crypto.HashEngine
import com.example.lib.format.NumberFormatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToLong

data class InlineAnswer(
    val badge: String,
    val headline: String,
    val detail: String,
    val copyValue: String,
    val targetToolId: String? = null
)

object CommandEngine {
    fun evaluateInline(rawQuery: String, region: RegionFormat = RegionFormat.EN_IN): InlineAnswer? {
        val q = rawQuery.trim()
        if (q.isEmpty()) return null
        val lower = q.lowercase()

        // 1. UUID
        if (lower == "uuid" || lower == "guid") {
            val id = UUID.randomUUID().toString()
            return InlineAnswer("UUID v4", id, "Cryptographically random RFC 4122 UUID", id, "dev-pass")
        }

        // 2. Hash commands
        val hashMatch = Regex("^(md5|sha1|sha256|sha512|crc32)\\s+(.+)$", RegexOption.IGNORE_CASE).find(q)
        if (hashMatch != null) {
            val algName = hashMatch.groupValues[1].lowercase()
            val payload = hashMatch.groupValues[2]
            val alg = when (algName) {
                "md5" -> HashAlgorithm.MD5
                "sha1" -> HashAlgorithm.SHA1
                "sha512" -> HashAlgorithm.SHA512
                "crc32" -> HashAlgorithm.CRC32
                else -> HashAlgorithm.SHA256
            }
            val digest = HashEngine.digestText(payload, alg)
            return InlineAnswer(
                badge = "HASH · ${alg.label}",
                headline = digest,
                detail = "${alg.bits}-bit digest of \"$payload\"",
                copyValue = digest,
                targetToolId = "dev-hash"
            )
        }

        // 3. Percentage: "18% of 2400"
        val pctOf = Regex("^([0-9.]+)\\s*%\\s*of\\s*([0-9.,]+)$", RegexOption.IGNORE_CASE).find(q)
        if (pctOf != null) {
            val pct = pctOf.groupValues[1].toDoubleOrNull()
            val base = pctOf.groupValues[2].replace(",", "").toDoubleOrNull()
            if (pct != null && base != null) {
                val res = (pct / 100.0) * base
                val formatted = formatCleanDouble(res)
                return InlineAnswer(
                    badge = "PERCENT",
                    headline = formatted,
                    detail = "$pct% of ${NumberFormatters.formatNumber(base.roundToLong(), region)} = $formatted",
                    copyValue = formatted,
                    targetToolId = "calc-pct"
                )
            }
        }

        // 4. EMI: "emi 5L 8.5% 5y"
        val emiMatch = Regex("^emi\\s+([0-9.]+)\\s*([lLkK]|cr)?\\s+([0-9.]+)%?\\s+([0-9.]+)\\s*([yYmM])?$", RegexOption.IGNORE_CASE).find(q)
        if (emiMatch != null) {
            val rawPrincipal = emiMatch.groupValues[1].toDoubleOrNull() ?: return null
            val multUnit = emiMatch.groupValues[2].lowercase()
            val principal = when (multUnit) {
                "l" -> rawPrincipal * 100_000.0
                "cr" -> rawPrincipal * 10_000_000.0
                "k" -> rawPrincipal * 1_000.0
                else -> rawPrincipal
            }
            val annualRate = emiMatch.groupValues[3].toDoubleOrNull() ?: return null
            val rawTenure = emiMatch.groupValues[4].toDoubleOrNull() ?: return null
            val tenureUnit = emiMatch.groupValues[5].lowercase()
            val months = if (tenureUnit == "m") rawTenure.toInt() else (rawTenure * 12).toInt()
            if (months > 0) {
                val b = FinanceEngine.calculateEmi(principal, annualRate, months)
                val emiStr = NumberFormatters.formatCurrency(b.monthlyEmi, region)
                return InlineAnswer(
                    badge = "FINANCE · EMI",
                    headline = "$emiStr / mo",
                    detail = "Interest ${NumberFormatters.formatCurrency(b.totalInterest, region)} · Total ${NumberFormatters.formatCurrency(b.totalPayable, region)} ($months mos)",
                    copyValue = b.monthlyEmi.roundToLong().toString(),
                    targetToolId = "fin-emi"
                )
            }
        }

        // 5. Unit conversions: "5 km in miles"
        val unitMatch = Regex("^(-?[0-9.]+)\\s*([a-zA-Z]+)\\s+(?:in|to)\\s+([a-zA-Z]+)$").find(q)
        if (unitMatch != null) {
            val amount = unitMatch.groupValues[1].toDoubleOrNull()
            val fromU = unitMatch.groupValues[2].lowercase()
            val toU = unitMatch.groupValues[3].lowercase()
            if (amount != null) {
                val converted = convertUnits(amount, fromU, toU)
                if (converted != null) {
                    val outStr = "${formatCleanDouble(converted.first)} ${converted.second}"
                    return InlineAnswer(
                        badge = "UNITS",
                        headline = outStr,
                        detail = "$amount $fromU = $outStr (${converted.third})",
                        copyValue = formatCleanDouble(converted.first),
                        targetToolId = "com-units"
                    )
                }
            }
        }

        // 6. World Time: "time in tokyo"
        val timeMatch = Regex("^time\\s+in\\s+(.+)$", RegexOption.IGNORE_CASE).find(q)
        if (timeMatch != null) {
            val city = timeMatch.groupValues[1].trim().lowercase()
            val tzId = when {
                "tokyo" in city || "japan" in city || city == "jst" -> "Asia/Tokyo"
                "london" in city || "uk" in city || city == "gmt" || city == "bst" -> "Europe/London"
                "new york" in city || "nyc" in city || city == "est" -> "America/New_York"
                "san francisco" in city || city == "la" || city == "pst" -> "America/Los_Angeles"
                "mumbai" in city || "delhi" in city || "india" in city || city == "ist" -> "Asia/Kolkata"
                "berlin" in city || "paris" in city || city == "cet" -> "Europe/Berlin"
                "dubai" in city -> "Asia/Dubai"
                "singapore" in city -> "Asia/Singapore"
                "sydney" in city -> "Australia/Sydney"
                else -> "UTC"
            }
            val sdf = SimpleDateFormat("HH:mm:ss · EEE, dd MMM yyyy (z)", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(tzId)
            }
            val nowStr = sdf.format(Date())
            return InlineAnswer(
                badge = "TIME · $tzId",
                headline = nowStr.substringBefore(" ·"),
                detail = nowStr,
                copyValue = nowStr,
                targetToolId = "time-world"
            )
        }

        // 7. Hex Color (#RRGGBB)
        val hexColor = Regex("^#([0-9a-fA-F]{6})$").find(q)
        if (hexColor != null) {
            val hex = hexColor.groupValues[1].uppercase()
            val r = hex.substring(0, 2).toInt(16)
            val g = hex.substring(2, 4).toInt(16)
            val b = hex.substring(4, 6).toInt(16)
            return InlineAnswer(
                badge = "COLOR · #$hex",
                headline = "rgb($r, $g, $b)",
                detail = "Open Color & Contrast Studio",
                copyValue = "#$hex",
                targetToolId = "dev-color"
            )
        }

        // 8. Direct math expression solver (e.g., "12 * (4 + 5)" or "sqrt(144)")
        if (q.any { it in "+-*/^()" } && q.any { it.isDigit() }) {
            val eval = ExpressionEvaluator.evaluate(q, degreesMode = true)
            if (eval is EvalResult.Success) {
                return InlineAnswer(
                    badge = "CALCULATOR",
                    headline = eval.formatted,
                    detail = "$q = ${eval.formatted}",
                    copyValue = eval.formatted,
                    targetToolId = "calc-sci"
                )
            }
        }

        // 9. Pasted URL detection -> route to URL Safety Checker
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return InlineAnswer(
                badge = "URL DETECTED",
                headline = q,
                detail = "Inspect redirects, TLS & trackers in URL Safety — or fetch via Downloader",
                copyValue = q,
                targetToolId = "url-safety"
            )
        }

        return null
    }

    private fun convertUnits(value: Double, from: String, to: String): Triple<Double, String, String>? {
        if ((from == "c" || from == "celsius") && (to == "f" || to == "fahrenheit")) {
            return Triple(value * 9.0 / 5.0 + 32.0, "°F", "Thermodynamic scale")
        }
        if ((from == "f" || from == "fahrenheit") && (to == "c" || to == "celsius")) {
            return Triple((value - 32.0) * 5.0 / 9.0, "°C", "Thermodynamic scale")
        }
        val lengthToMeters = mapOf(
            "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
            "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
            "cm" to 0.01, "mm" to 0.001,
            "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
            "ft" to 0.3048, "feet" to 0.3048, "foot" to 0.3048,
            "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254,
            "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144
        )
        if (from in lengthToMeters && to in lengthToMeters) {
            val meters = value * lengthToMeters.getValue(from)
            return Triple(meters / lengthToMeters.getValue(to), to, "Length")
        }
        val massToKg = mapOf(
            "kg" to 1.0, "kilogram" to 1.0, "kilograms" to 1.0,
            "g" to 0.001, "gram" to 0.001, "grams" to 0.001,
            "lb" to 0.45359237, "lbs" to 0.45359237, "pound" to 0.45359237, "pounds" to 0.45359237,
            "oz" to 0.0283495231
        )
        if (from in massToKg && to in massToKg) {
            val kg = value * massToKg.getValue(from)
            return Triple(kg / massToKg.getValue(to), to, "Mass")
        }
        val areaToSqm = mapOf(
            "sqm" to 1.0, "sqft" to 0.09290304, "sqyd" to 0.83612736, "gaj" to 0.83612736,
            "acre" to 4046.8564224, "acres" to 4046.8564224,
            "hectare" to 10000.0, "ha" to 10000.0,
            "bigha" to 2529.285, "kanal" to 505.857, "marla" to 25.29285, "guntha" to 101.1714
        )
        if (from in areaToSqm && to in areaToSqm) {
            val sqm = value * areaToSqm.getValue(from)
            return Triple(sqm / areaToSqm.getValue(to), to, "Area")
        }
        return null
    }

    private fun formatCleanDouble(v: Double): String {
        val rounded = (v * 10000.0).roundToLong() / 10000.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
