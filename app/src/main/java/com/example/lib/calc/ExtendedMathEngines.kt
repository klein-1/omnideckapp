package com.example.lib.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class SolarTimesResult(
    val sunriseLocalMin: Double?,
    val sunsetLocalMin: Double?,
    val solarNoonLocalMin: Double,
    val daylightMinutes: Double
)

data class CalendarConversions(
    val julianDay: Double,
    val hijriDate: String,
    val jalaliDate: String,
    val lunarAgeDays: Double,
    val lunarIlluminationPct: Double,
    val lunarPhaseName: String
)

data class MatrixResult(
    val determinant: Double,
    val transpose: List<List<Double>>,
    val inverse: List<List<Double>>?
)

data class RegressionStats(
    val count: Int,
    val mean: Double,
    val median: Double,
    val stdDev: Double,
    val min: Double,
    val max: Double,
    val slope: Double? = null,
    val intercept: Double? = null,
    val r: Double? = null
)

data class DebtAccount(
    val name: String,
    val balance: Double,
    val aprPercent: Double,
    val minPayment: Double
)

data class DebtStrategySummary(
    val strategyName: String,
    val monthsToPayoff: Int,
    val totalInterestPaid: Double,
    val totalPaid: Double
)

data class ReadabilityResult(
    val words: Int,
    val sentences: Int,
    val syllables: Int,
    val fleschReadingEase: Double,
    val gunningFogIndex: Double,
    val topBigrams: List<Pair<String, Int>>,
    val topTrigrams: List<Pair<String, Int>>,
    val invisibleCharsFound: List<String>
)

object ExtendedMathEngines {

    // -------- ASTRONOMICAL SOLAR & CALENDARS --------

    fun computeNoaaSolarTimes(
        year: Int,
        month: Int,
        day: Int,
        latDeg: Double,
        lonDeg: Double,
        tzOffsetHours: Double
    ): SolarTimesResult {
        val jd = toJulianDay(year, month, day).toDouble() - 0.5
        val n = jd - 2451545.0
        val g = ((357.528 + 0.9856003 * n) % 360.0 + 360.0) % 360.0
        val l = ((280.460 + 0.9856474 * n) % 360.0 + 360.0) % 360.0
        val gRad = Math.toRadians(g)
        val lambda = l + 1.915 * sin(gRad) + 0.020 * sin(2 * gRad)
        val epsilon = 23.439 - 0.0000004 * n
        val lamRad = Math.toRadians(lambda)
        val epsRad = Math.toRadians(epsilon)
        val declRad = asin(sin(epsRad) * sin(lamRad))
        var raDeg = Math.toDegrees(atan(cos(epsRad) * tan(lamRad)))
        if (cos(lamRad) < 0) raDeg += 180.0
        raDeg = (raDeg % 360.0 + 360.0) % 360.0

        val eqOfTimeMin = 4.0 * ((l - raDeg + 540.0) % 360.0 - 180.0)
        val solarNoonMin = 720.0 - 4.0 * lonDeg - eqOfTimeMin + tzOffsetHours * 60.0

        val latRad = Math.toRadians(latDeg.coerceIn(-89.9, 89.9))
        val cosHa = (cos(Math.toRadians(90.833)) / (cos(latRad) * cos(declRad))) - tan(latRad) * tan(declRad)
        return if (cosHa < -1.0 || cosHa > 1.0) {
            val polarDay = cosHa < -1.0
            SolarTimesResult(null, null, normalizeMinutes(solarNoonMin), if (polarDay) 1440.0 else 0.0)
        } else {
            val haDeg = Math.toDegrees(acos(cosHa))
            val deltaMin = 4.0 * haDeg
            SolarTimesResult(
                sunriseLocalMin = normalizeMinutes(solarNoonMin - deltaMin),
                sunsetLocalMin = normalizeMinutes(solarNoonMin + deltaMin),
                solarNoonLocalMin = normalizeMinutes(solarNoonMin),
                daylightMinutes = deltaMin * 2.0
            )
        }
    }

    private fun normalizeMinutes(m: Double): Double = ((m % 1440.0) + 1440.0) % 1440.0

    fun formatMinutesHhMm(minutes: Double?): String {
        if (minutes == null) return "Polar"
        val total = minutes.roundToInt().coerceIn(0, 1439)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    fun toJulianDay(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day.toLong() + (153L * m + 2L) / 5L + 365L * y + y / 4L - y / 100L + y / 400L - 32045L
    }

    fun convertCalendar(year: Int, month: Int, day: Int): CalendarConversions {
        val jdn = toJulianDay(year, month, day)

        // Hijri Kuwaiti/tabular algorithm
        val l = jdn - 1948440L + 10632L
        val n = (l - 1L) / 10631L
        val l2 = l - 10631L * n + 354L
        val j = ((10985L - l2) / 5316L) * ((50L * l2) / 17719L) + (l2 / 5670L) * ((43L * l2) / 15238L)
        val l3 = l2 - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val hMonth = (24L * l3) / 709L
        val hDay = l3 - (709L * hMonth) / 24L
        val hYear = 30L * n + j - 30L
        val hijriMonths = listOf("Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")
        val hMonthName = hijriMonths.getOrElse((hMonth - 1).toInt()) { "M$hMonth" }

        // Jalali (Solar Hijri) exact conversion
        var gy = year - 1600
        var gm = month - 1
        var gd = day - 1
        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (i in 0 until gm) gDayNo += gDaysInMonth[i]
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jmIdx = 0
        while (jmIdx < 11 && jDayNo >= jDaysInMonth[jmIdx]) {
            jDayNo -= jDaysInMonth[jmIdx]
            jmIdx++
        }
        val jm = jmIdx + 1
        val jdOut = jDayNo + 1

        // Lunar phase (synodic month 29.53058867 days from known new moon JDN 2451550.1)
        val synodic = 29.53058867
        val diffDays = (jdn.toDouble() - 2451550.1)
        val age = ((diffDays % synodic) + synodic) % synodic
        val illum = ((1.0 - cos(2.0 * PI * age / synodic)) / 2.0) * 100.0
        val phaseName = when {
            age < 1.84 -> "New Moon"
            age < 5.53 -> "Waxing Crescent"
            age < 9.22 -> "First Quarter"
            age < 12.91 -> "Waxing Gibbous"
            age < 16.61 -> "Full Moon"
            age < 20.30 -> "Waning Gibbous"
            age < 23.99 -> "Last Quarter"
            age < 27.68 -> "Waning Crescent"
            else -> "New Moon"
        }

        return CalendarConversions(
            julianDay = jdn.toDouble(),
            hijriDate = "$hDay $hMonthName $hYear AH",
            jalaliDate = "%04d-%02d-%02d SH".format(jy, jm, jdOut),
            lunarAgeDays = age,
            lunarIlluminationPct = illum,
            lunarPhaseName = phaseName
        )
    }

    fun countBusinessDays(
        startEpochDay: Long,
        endEpochDay: Long,
        holidayEpochDays: Set<Long> = emptySet()
    ): Int {
        val from = minOf(startEpochDay, endEpochDay)
        val to = maxOf(startEpochDay, endEpochDay)
        var count = 0
        var d = from
        while (d <= to) {
            // java.time.DayOfWeek: epochDay 0 (1970-01-01) was Thursday (4). (d + 3) % 7 -> 0=Mon..4=Fri, 5=Sat, 6=Sun
            val dow = (((d + 3L) % 7L + 7L) % 7L).toInt()
            if (dow < 5 && d !in holidayEpochDays) count++
            d++
        }
        return count
    }

    // -------- PROGRAMMER BASE / BITWISE / IEEE-754 --------

    fun parseProgrammerInput(raw: String, radix: Int): Long? {
        val cleaned = raw.trim().replace("_", "").replace(" ", "")
        if (cleaned.isEmpty()) return 0L
        return cleaned.toLongOrNull(radix)
    }

    fun formatBitwiseBases(value: Long): Map<String, String> = mapOf(
        "DEC (64s)" to value.toString(),
        "DEC (32s)" to value.toInt().toString(),
        "HEX" to value.toInt().toUInt().toString(16).uppercase(),
        "OCT" to value.toInt().toUInt().toString(8),
        "BIN (32b)" to value.toInt().toUInt().toString(2).padStart(32, '0').chunked(4).joinToString(" ")
    )

    fun ieee754FloatBreakdown(value: Float): Triple<String, String, String> {
        val bits = java.lang.Float.floatToRawIntBits(value)
        val bin = bits.toUInt().toString(2).padStart(32, '0')
        val sign = bin.substring(0, 1)
        val exp = bin.substring(1, 9)
        val mant = bin.substring(9, 32)
        return Triple(sign, exp, mant)
    }

    // -------- MATRIX & ALGEBRA --------

    fun solve2x2Matrix(m: List<List<Double>>): MatrixResult {
        val a = m[0][0]; val b = m[0][1]
        val c = m[1][0]; val d = m[1][1]
        val det = a * d - b * c
        val trans = listOf(listOf(a, c), listOf(b, d))
        val inv = if (abs(det) > 1e-12) {
            listOf(listOf(d / det, -b / det), listOf(-c / det, a / det))
        } else null
        return MatrixResult(det, trans, inv)
    }

    fun solve3x3Matrix(m: List<List<Double>>): MatrixResult {
        val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
        val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
        val g = m[2][0]; val h = m[2][1]; val i = m[2][2]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val trans = listOf(listOf(a, d, g), listOf(b, e, h), listOf(c, f, i))
        val inv = if (abs(det) > 1e-12) {
            listOf(
                listOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
                listOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
                listOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det)
            )
        } else null
        return MatrixResult(det, trans, inv)
    }

    fun solveQuadratic(a: Double, b: Double, c: Double): String {
        if (abs(a) < 1e-12) {
            return if (abs(b) < 1e-12) "Degenerate equation (0 = ${"%.4f".format(c)})"
            else "Linear root x = ${"%.6f".format(-c / b)}"
        }
        val disc = b * b - 4.0 * a * c
        return when {
            disc > 1e-12 -> {
                val r1 = (-b + sqrt(disc)) / (2.0 * a)
                val r2 = (-b - sqrt(disc)) / (2.0 * a)
                "2 Real Roots: x1 = ${"%.6f".format(r1)}, x2 = ${"%.6f".format(r2)} (Δ=${"%.3f".format(disc)})"
            }
            abs(disc) <= 1e-12 -> {
                val r = -b / (2.0 * a)
                "Double Root: x = ${"%.6f".format(r)}"
            }
            else -> {
                val real = -b / (2.0 * a)
                val imag = sqrt(-disc) / (2.0 * a)
                "Complex Roots: ${"%.4f".format(real)} ± ${"%.4f".format(abs(imag))}i"
            }
        }
    }

    fun computeStatsAndRegression(xs: List<Double>, ys: List<Double>? = null): RegressionStats? {
        if (xs.isEmpty()) return null
        val sorted = xs.sorted()
        val mean = xs.average()
        val median = if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0 else sorted[sorted.size / 2]
        val variance = xs.sumOf { (it - mean) * (it - mean) } / xs.size
        val std = sqrt(variance)
        if (ys == null || ys.size != xs.size || xs.size < 2) {
            return RegressionStats(xs.size, mean, median, std, sorted.first(), sorted.last())
        }
        val mx = mean
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val syy = ys.sumOf { (it - my) * (it - my) }
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val slope = if (sxx > 1e-12) sxy / sxx else 0.0
        val intercept = my - slope * mx
        val r = if (sxx > 1e-12 && syy > 1e-12) sxy / sqrt(sxx * syy) else 0.0
        return RegressionStats(xs.size, mean, median, std, sorted.first(), sorted.last(), slope, intercept, r)
    }

    // -------- DEBT SNOWBALL VS AVALANCHE & FIRE --------

    fun simulateDebtPayoff(
        accounts: List<DebtAccount>,
        extraMonthlyPayment: Double,
        avalanche: Boolean
    ): DebtStrategySummary {
        val name = if (avalanche) "Avalanche (Highest APR First)" else "Snowball (Smallest Balance First)"
        if (accounts.isEmpty() || accounts.all { it.balance <= 0.0 }) {
            return DebtStrategySummary(name, 0, 0.0, 0.0)
        }
        val balances = accounts.map { it.balance.coerceAtLeast(0.0) }.toMutableList()
        var months = 0
        var totalInterest = 0.0
        var totalPaid = 0.0

        while (balances.any { it > 0.01 } && months < 600) {
            months++
            var freedPool = extraMonthlyPayment.coerceAtLeast(0.0)
            // Accrue monthly interest and pay minimums
            for (i in accounts.indices) {
                if (balances[i] <= 0.01) {
                    freedPool += accounts[i].minPayment
                    continue
                }
                val monthlyRate = (accounts[i].aprPercent / 100.0) / 12.0
                val interest = balances[i] * monthlyRate
                totalInterest += interest
                balances[i] += interest
                val minPay = minOf(balances[i], accounts[i].minPayment.coerceAtLeast(1.0))
                balances[i] -= minPay
                totalPaid += minPay
                if (balances[i] < 0.01) balances[i] = 0.0
            }
            // Apply snowball/avalanche surplus to priority target(s)
            val order = if (avalanche) {
                accounts.indices.sortedByDescending { accounts[it].aprPercent }
            } else {
                accounts.indices.sortedBy { balances[it] }
            }
            for (idx in order) {
                if (freedPool <= 0.01) break
                if (balances[idx] > 0.01) {
                    val pay = minOf(balances[idx], freedPool)
                    balances[idx] -= pay
                    totalPaid += pay
                    freedPool -= pay
                    if (balances[idx] < 0.01) balances[idx] = 0.0
                }
            }
        }
        return DebtStrategySummary(name, months, totalInterest, totalPaid)
    }

    fun computeCagr(startVal: Double, endVal: Double, years: Double): Double {
        if (startVal <= 0.0 || endVal <= 0.0 || years <= 0.0) return 0.0
        return ((endVal / startVal).pow(1.0 / years) - 1.0) * 100.0
    }

    fun computeFireNumber(annualExpenses: Double, safeWithdrawalPct: Double): Double {
        if (annualExpenses <= 0.0 || safeWithdrawalPct <= 0.0) return 0.0
        return annualExpenses / (safeWithdrawalPct / 100.0)
    }

    // -------- TEXT READABILITY, N-GRAMS, ZALGO & INVISIBLE CHARS --------

    fun analyzeReadability(text: String): ReadabilityResult {
        val wordTokens = Regex("[A-Za-z0-9']+").findAll(text).map { it.value.lowercase() }.toList()
        val words = wordTokens.size
        val sentences = Regex("[.!?]+").findAll(text).count().coerceAtLeast(if (words > 0) 1 else 0)
        val syllables = wordTokens.sumOf { countSyllables(it) }
        val complexWords = wordTokens.count { countSyllables(it) >= 3 }

        val flesch = if (words > 0 && sentences > 0) {
            206.835 - 1.015 * (words.toDouble() / sentences) - 84.6 * (syllables.toDouble() / words)
        } else 0.0

        val fog = if (words > 0 && sentences > 0) {
            0.4 * ((words.toDouble() / sentences) + 100.0 * (complexWords.toDouble() / words))
        } else 0.0

        val bigrams = wordTokens.windowed(2).groupingBy { "${it[0]} ${it[1]}" }.eachCount()
            .entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
        val trigrams = wordTokens.windowed(3).groupingBy { "${it[0]} ${it[1]} ${it[2]}" }.eachCount()
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }

        val invisible = text.mapIndexedNotNull { idx, ch ->
            when (ch.code) {
                0x200B -> "U+200B ZWSP @$idx"
                0x200C -> "U+200C ZWNJ @$idx"
                0x200D -> "U+200D ZWJ @$idx"
                0xFEFF -> "U+FEFF BOM @$idx"
                0x00A0 -> "U+00A0 NBSP @$idx"
                0x200E, 0x200F -> "U+${ch.code.toString(16).uppercase()} DIR @$idx"
                else -> null
            }
        }

        return ReadabilityResult(words, sentences, syllables, flesch, fog, bigrams, trigrams, invisible)
    }

    private fun countSyllables(word: String): Int {
        val w = word.lowercase().replace(Regex("[^a-z]"), "")
        if (w.isEmpty()) return 0
        if (w.length <= 3) return 1
        val cleaned = w.replace(Regex("(?:[^laeiouy]es|ed|[^laeiouy]e)$"), "").replace(Regex("^y"), "")
        val groups = Regex("[aeiouy]{1,2}").findAll(cleaned).count()
        return groups.coerceAtLeast(1)
    }

    fun toZalgo(text: String, intensity: Int = 2): String {
        val marks = arrayOf('\u0300', '\u0301', '\u0302', '\u0303', '\u0304', '\u0308', '\u030A', '\u0323', '\u0327', '\u0336')
        val out = StringBuilder()
        text.forEachIndexed { i, ch ->
            out.append(ch)
            if (!ch.isWhitespace()) {
                repeat(intensity.coerceIn(1, 5)) { k ->
                    out.append(marks[(i * 7 + k * 3) % marks.size])
                }
            }
        }
        return out.toString()
    }

    fun applyMailMerge(template: String, csvRows: String): String {
        val lines = csvRows.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return template
        val headers = lines.first().split(",").map { it.trim() }
        return lines.drop(1).joinToString("\n\n---\n\n") { row ->
            val cols = row.split(",").map { it.trim() }
            var rendered = template
            headers.forEachIndexed { idx, h ->
                rendered = rendered.replace("{{$h}}", cols.getOrElse(idx) { "" })
            }
            rendered
        }
    }
}
