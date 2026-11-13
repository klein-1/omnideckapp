package com.example.tools.time

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.calc.ExtendedMathEngines
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.sound.PixelSoundEngine
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeExtendedScreen(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.SKY
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(meta.id) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(if (meta.id == "time-analog") 50L else 1000L)
        }
    }

    // Analog Clock state
    var use24Hour by remember { mutableStateOf(true) }

    // Calendar state
    var calOffsetMonths by remember { mutableIntStateOf(0) }
    var baseDateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var addDaysStr by remember { mutableStateOf("0") }

    // Pomodoro state
    var pomoPhase by remember { mutableStateOf("FOCUS") } // FOCUS, SHORT, LONG
    var pomoTotalSec by remember { mutableIntStateOf(25 * 60) }
    var pomoRemMs by remember { mutableLongStateOf(25 * 60_000L) }
    var pomoRunning by remember { mutableStateOf(false) }
    var pomoEndMs by remember { mutableLongStateOf(0L) }
    var pomoCompleted by remember { mutableIntStateOf(0) }

    LaunchedEffect(pomoRunning, pomoEndMs) {
        while (pomoRunning) {
            val rem = pomoEndMs - System.currentTimeMillis()
            if (rem <= 0L) {
                pomoRemMs = 0L
                pomoRunning = false
                if (pomoPhase == "FOCUS") pomoCompleted++
                PixelSoundEngine.playBlip(true, 920, 160)
                onStatusToast("$pomoPhase session completed.")
            } else {
                pomoRemMs = rem
                delay(100L)
            }
        }
    }

    // Age to seconds state
    var birthDateStr by remember { mutableStateOf("2000-01-01") }
    var birthTimeStr by remember { mutableStateOf("00:00:00") }

    val ageMetrics = remember(birthDateStr, birthTimeStr, nowMs) {
        computeAgeToSeconds(birthDateStr, birthTimeStr, nowMs)
    }

    val calendarResult = remember(baseDateStr, addDaysStr) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val d = runCatching { sdf.parse(baseDateStr.trim()) }.getOrNull()
        val delta = addDaysStr.toIntOrNull()
        if (d != null && delta != null) {
            val c = Calendar.getInstance().apply { time = d; add(Calendar.DAY_OF_YEAR, delta) }
            SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US).format(c.time)
        } else "Enter valid YYYY-MM-DD and integer days."
    }

    val outputText = when (meta.id) {
        "time-analog" -> SimpleDateFormat("EEEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(Date(nowMs))
        "time-calendar" -> "$baseDateStr + ($addDaysStr days) = $calendarResult"
        "time-pomodoro" -> "Phase=$pomoPhase · Completed=$pomoCompleted"
        "time-age" -> ageMetrics
        else -> ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "time-analog" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
                val secFloat = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
                val minFloat = cal.get(Calendar.MINUTE) + secFloat / 60f
                val hrFloat = (cal.get(Calendar.HOUR) % 12) + minFloat / 60f
                val h24 = cal.get(Calendar.HOUR_OF_DAY)
                val minInt = cal.get(Calendar.MINUTE)
                val secInt = cal.get(Calendar.SECOND)
                val timeStr = SimpleDateFormat(if (use24Hour) "HH:mm:ss" else "hh:mm:ss a", Locale.US).format(Date(nowMs))
                val dateStr = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US).format(Date(nowMs))
                val rfc2822 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date(nowMs))

                // Swatch Internet Time (.beats in BMT = UTC+1)
                val bmtSec = (((nowMs / 1000L) + 3600L) % 86400L + 86400L) % 86400L
                val swatchBeats = (bmtSec / 86.4).toInt()
                val romanHours = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
                val romanLabel = "${romanHours[h24 % 12]}:${"%02d".format(minInt)}"

                PixelBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Canvas(modifier = Modifier.size(220.dp).padding(6.dp)) {
                            val r = size.minDimension / 2f
                            val cx = size.width / 2f; val cy = size.height / 2f
                            drawCircle(palette.paper, radius = r, center = Offset(cx, cy))
                            drawCircle(palette.ink, radius = r, center = Offset(cx, cy), style = Stroke(width = 3f))
                            for (i in 0 until 60) {
                                val ang = (i * 6.0 - 90.0) * PI / 180.0
                                val isHour = i % 5 == 0
                                val rIn = if (isHour) r * 0.80f else r * 0.90f
                                val rOut = r * 0.95f
                                drawLine(
                                    color = palette.ink,
                                    start = Offset(cx + (rIn * cos(ang)).toFloat(), cy + (rIn * sin(ang)).toFloat()),
                                    end = Offset(cx + (rOut * cos(ang)).toFloat(), cy + (rOut * sin(ang)).toFloat()),
                                    strokeWidth = if (isHour) 4.5f else 1.5f
                                )
                            }
                            fun drawHand(deg: Float, len: Float, width: Float, col: Color) {
                                val rad = (deg - 90f) * PI / 180.0
                                drawLine(
                                    color = col,
                                    start = Offset(cx, cy),
                                    end = Offset(cx + (len * cos(rad)).toFloat(), cy + (len * sin(rad)).toFloat()),
                                    strokeWidth = width,
                                    cap = StrokeCap.Round
                                )
                            }
                            drawHand(hrFloat * 30f, r * 0.52f, 9f, palette.ink)
                            drawHand(minFloat * 6f, r * 0.74f, 6f, palette.ink)
                            drawHand(secFloat * 6f, r * 0.84f, 3f, palette.chapterColor(ChapterHue.VERMILION))
                            drawCircle(palette.chapterColor(ChapterHue.VERMILION), radius = 8f, center = Offset(cx, cy))
                        }
                        DotMatrixReadout(timeStr, sizeSp = 32, accentColor = palette.ink)
                        Text(dateStr, style = DotkitType.bodyUi(13), color = palette.ink2)
                        PixelSwitch("24-Hour Digital Readout", use24Hour, { use24Hour = it }, hue)
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("BINARY BCD MATRIX · SWATCH .BEATS · RFC-2822", style = DotkitType.monoLabel(10), color = palette.ink2)
                        val digits = listOf(h24 / 10, h24 % 10, minInt / 10, minInt % 10, secInt / 10, secInt % 10)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("H1", "H0", "M1", "M0", "S1", "S0").forEachIndexed { colIdx, hdr ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(hdr, style = DotkitType.monoLabel(9), color = palette.ink3)
                                    for (bit in 3 downTo 0) {
                                        val on = ((digits[colIdx] shr bit) and 1) == 1
                                        Box(
                                            Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (on) palette.chapterColor(hue) else palette.paper3)
                                                .border(1.dp, palette.hairline, RoundedCornerShape(4.dp))
                                        )
                                    }
                                    Text("${digits[colIdx]}", style = DotkitType.monoData(11), color = palette.ink)
                                }
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("Decimal Swatch: @$swatchBeats .beats  ·  Roman Dial: $romanLabel", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                        Text("RFC-2822: $rfc2822", style = DotkitType.monoData(11), color = palette.ink2)
                    }
                }
            }

            "time-calendar" -> {
                var calSubTab by remember { mutableStateOf("GRID") } // GRID, ASTRO, BIZ
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, calOffsetMonths)
                }
                val todayCal = Calendar.getInstance()
                val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
                val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GRID" to "Calendar & Date +/-", "ASTRO" to "Astro · Hijri · Jalali · Moon", "BIZ" to "Business Days").forEach { (k, lbl) ->
                        PixelChip(lbl, selected = calSubTab == k, chapterHue = hue, onClick = { calSubTab = k })
                    }
                }

                when (calSubTab) {
                    "GRID" -> {
                        PixelBox {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    PixelChip(label = "←", onClick = { calOffsetMonths-- })
                                    Text(monthTitle.uppercase(), style = DotkitType.editorialHeading(18), color = palette.ink)
                                    PixelChip(label = "→", onClick = { calOffsetMonths++ })
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA").forEach { d ->
                                        Text(d, style = DotkitType.monoLabel(10), color = palette.ink3, modifier = Modifier.weight(1f))
                                    }
                                }
                                val totalCells = ((firstDow + daysInMonth + 6) / 7) * 7
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (row in 0 until totalCells / 7) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            for (col in 0 until 7) {
                                                val cell = row * 7 + col - firstDow + 1
                                                val valid = cell in 1..daysInMonth
                                                val isToday = valid &&
                                                    cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                                    cal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                                    cell == todayCal.get(Calendar.DAY_OF_MONTH)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isToday) palette.chapterColor(hue) else if (valid) palette.paper else Color.Transparent)
                                                        .border(1.dp, if (valid) palette.hairline else Color.Transparent, RoundedCornerShape(4.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (valid) {
                                                        Text("$cell", style = DotkitType.monoData(12), color = if (isToday) hue.onColor else palette.ink)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PixelBox {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("DATE +/- DAYS ARITHMETIC", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelTextField(value = baseDateStr, onValueChange = { baseDateStr = it }, placeholder = "YYYY-MM-DD", chapterHue = hue, modifier = Modifier.weight(1.4f))
                                    PixelNumberField(value = addDaysStr, onValueChange = { addDaysStr = it }, allowDecimal = false, allowNegative = true, chapterHue = hue, modifier = Modifier.weight(0.8f))
                                }
                                Text(calendarResult, style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                            }
                        }
                    }

                    "ASTRO" -> {
                        var latInput by remember { mutableStateOf("0") }
                        var lonInput by remember { mutableStateOf("0") }
                        val parsedCal = remember(baseDateStr) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                            val d = runCatching { sdf.parse(baseDateStr.trim()) }.getOrNull() ?: Date()
                            Calendar.getInstance().apply { time = d }
                        }
                        val y = parsedCal.get(Calendar.YEAR)
                        val m = parsedCal.get(Calendar.MONTH) + 1
                        val d = parsedCal.get(Calendar.DAY_OF_MONTH)
                        val conv = remember(y, m, d) { ExtendedMathEngines.convertCalendar(y, m, d) }
                        val tzHrs = TimeZone.getDefault().getOffset(nowMs) / 3_600_000.0
                        val solar = remember(y, m, d, latInput, lonInput, tzHrs) {
                            ExtendedMathEngines.computeNoaaSolarTimes(
                                year = y, month = m, day = d,
                                latDeg = latInput.toDoubleOrNull() ?: 0.0,
                                lonDeg = lonInput.toDoubleOrNull() ?: 0.0,
                                tzOffsetHours = tzHrs
                            )
                        }
                        PixelBox {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("HIJRI · JALALI · JULIAN DAY · LUNAR PHASE", style = DotkitType.monoLabel(10), color = palette.ink2)
                                PixelTextField(baseDateStr, { baseDateStr = it }, "Date YYYY-MM-DD", chapterHue = hue)
                                Text("Hijri: ${conv.hijriDate}", style = DotkitType.monoData(13), color = palette.ink)
                                Text("Jalali (Solar Hijri): ${conv.jalaliDate}", style = DotkitType.monoData(13), color = palette.ink)
                                Text("Julian Day (JDN): ${conv.julianDay.toLong()}", style = DotkitType.monoData(12), color = palette.ink2)
                                Text("Moon: ${conv.lunarPhaseName} (${"%.1f".format(conv.lunarIlluminationPct)}% lit · Age ${"%.1f".format(conv.lunarAgeDays)} d)", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                                HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                                Text("NOAA SOLAR EPHEMERIS (LAT / LON)", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelNumberField(latInput, { latInput = it }, "Lat °", allowNegative = true, chapterHue = hue, modifier = Modifier.weight(1f))
                                    PixelNumberField(lonInput, { lonInput = it }, "Lon °", allowNegative = true, chapterHue = hue, modifier = Modifier.weight(1f))
                                }
                                Text(
                                    "Sunrise: ${ExtendedMathEngines.formatMinutesHhMm(solar.sunriseLocalMin)} · Solar Noon: ${ExtendedMathEngines.formatMinutesHhMm(solar.solarNoonLocalMin)} · Sunset: ${ExtendedMathEngines.formatMinutesHhMm(solar.sunsetLocalMin)} · Daylight: ${solar.daylightMinutes.roundToInt()} min",
                                    style = DotkitType.monoData(12),
                                    color = palette.ok
                                )
                            }
                        }
                    }

                    else -> { // BIZ
                        var bizStart by remember { mutableStateOf(baseDateStr) }
                        var bizEnd by remember { mutableStateOf(baseDateStr) }
                        var holidaysCsv by remember { mutableStateOf("") }
                        val bizOut = remember(bizStart, bizEnd, holidaysCsv) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
                            val d1 = runCatching { sdf.parse(bizStart.trim()) }.getOrNull()
                            val d2 = runCatching { sdf.parse(bizEnd.trim()) }.getOrNull()
                            if (d1 == null || d2 == null) "Enter Start & End YYYY-MM-DD."
                            else {
                                val hDays = holidaysCsv.split(",").mapNotNull { runCatching { sdf.parse(it.trim())?.time?.div(86_400_000L) }.getOrNull() }.toSet()
                                val e1 = d1.time / 86_400_000L
                                val e2 = d2.time / 86_400_000L
                                val totalCal = kotlin.math.abs(e2 - e1) + 1L
                                val work = ExtendedMathEngines.countBusinessDays(e1, e2, hDays)
                                "Business Days (Mon–Fri): $work\nTotal Calendar Days: $totalCal\nWeekends/Holidays Skipped: ${totalCal - work}"
                            }
                        }
                        PixelBox {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("BUSINESS DAYS & HOLIDAY CALCULATOR", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelTextField(bizStart, { bizStart = it }, "Start YYYY-MM-DD", chapterHue = hue, modifier = Modifier.weight(1f))
                                    PixelTextField(bizEnd, { bizEnd = it }, "End YYYY-MM-DD", chapterHue = hue, modifier = Modifier.weight(1f))
                                }
                                PixelTextField(holidaysCsv, { holidaysCsv = it }, "Optional Holidays CSV (YYYY-MM-DD, ...)", chapterHue = hue)
                                Text(bizOut, style = DotkitType.monoData(13), color = palette.ink)
                            }
                        }
                    }
                }
            }

            "time-pomodoro" -> {
                val pct = (pomoRemMs.toFloat() / (pomoTotalSec * 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val secRem = ((pomoRemMs + 999L) / 1000L).toInt().coerceAtLeast(0)
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("POMODORO FOCUS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp("$pomoCompleted SESSIONS DONE", palette.ok)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Triple("FOCUS", "Focus 25m", 25 * 60), Triple("SHORT", "Short 5m", 5 * 60), Triple("LONG", "Long 15m", 15 * 60)).forEach { (id, lbl, s) ->
                                PixelChip(label = lbl, selected = pomoPhase == id, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    pomoRunning = false
                                    pomoPhase = id
                                    pomoTotalSec = s
                                    pomoRemMs = s * 1000L
                                })
                            }
                        }
                        DotMatrixReadout("%02d:%02d".format(secRem / 60, secRem % 60), sizeSp = 52, accentColor = palette.ink)
                        PixelProgressBar(pct, hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (pomoRunning) "Pause" else "Start",
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (pomoRunning) {
                                        pomoRemMs = (pomoEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
                                        pomoRunning = false
                                    } else {
                                        if (pomoRemMs <= 0L) pomoRemMs = pomoTotalSec * 1000L
                                        pomoEndMs = System.currentTimeMillis() + pomoRemMs
                                        pomoRunning = true
                                    }
                                }
                            )
                            PixelButton(
                                text = "Reset",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pomoRunning = false
                                    pomoRemMs = pomoTotalSec * 1000L
                                }
                            )
                        }
                    }
                }
            }

            "time-age" -> {
                var retireAgeStr by remember { mutableStateOf("0") }
                var regularHrsStr by remember { mutableStateOf("0") }
                var overtimeHrsStr by remember { mutableStateOf("0") }
                var hourlyRateStr by remember { mutableStateOf("0") }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("BIRTH DATE & TIME + RETIREMENT TARGET", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(value = birthDateStr, onValueChange = { birthDateStr = it }, placeholder = "YYYY-MM-DD", chapterHue = hue, modifier = Modifier.weight(1.2f))
                            PixelTextField(value = birthTimeStr, onValueChange = { birthTimeStr = it }, placeholder = "HH:mm:ss", chapterHue = hue, modifier = Modifier.weight(0.9f))
                        }
                        PixelNumberField(value = retireAgeStr, onValueChange = { retireAgeStr = it }, placeholder = "Target Retirement Age (yrs)", allowDecimal = false, chapterHue = hue)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text(
                            text = ageMetrics,
                            style = DotkitType.monoData(13),
                            color = palette.ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(12.dp)
                        )
                        val rAge = retireAgeStr.toIntOrNull() ?: 0
                        if (rAge > 0) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                            val dob = runCatching { sdf.parse(birthDateStr.trim()) }.getOrNull()
                            if (dob != null) {
                                val rCal = Calendar.getInstance().apply { time = dob; add(Calendar.YEAR, rAge) }
                                val remDays = ((rCal.timeInMillis - nowMs) / 86_400_000L).coerceAtLeast(0L)
                                Text("Retirement Target ($rAge yr): ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(rCal.time)} · $remDays days remaining", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WORK HOURS TIMESHEET & OVERTIME (1.5×)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelNumberField(regularHrsStr, { regularHrsStr = it }, "Reg Hrs", chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelNumberField(overtimeHrsStr, { overtimeHrsStr = it }, "OT Hrs", chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelNumberField(hourlyRateStr, { hourlyRateStr = it }, "Rate/hr", chapterHue = hue, modifier = Modifier.weight(1f))
                        }
                        val regH = regularHrsStr.toDoubleOrNull() ?: 0.0
                        val otH = overtimeHrsStr.toDoubleOrNull() ?: 0.0
                        val rate = hourlyRateStr.toDoubleOrNull() ?: 0.0
                        val gross = regH * rate + otH * rate * 1.5
                        Text(
                            "Total Hours: ${"%.1f".format(regH + otH)}h · Pay Period Gross: ${"%.2f".format(gross)} · Annualized (×52): ${"%.2f".format(gross * 52.0)}",
                            style = DotkitType.monoData(12),
                            color = palette.ok
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun computeAgeToSeconds(dateStr: String, timeStr: String, nowMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
    val dob = runCatching { sdf.parse("${dateStr.trim()} ${timeStr.trim().ifBlank { "00:00:00" }}") }.getOrNull()
        ?: return "Enter valid birth date (YYYY-MM-DD) & time (HH:mm:ss)."
    if (dob.time > nowMs) return "Birth timestamp is in the future."
    val diffSec = (nowMs - dob.time) / 1000L
    val mins = diffSec / 60L
    val hrs = mins / 60L
    val days = hrs / 24L
    val c1 = Calendar.getInstance().apply { time = dob }
    val c2 = Calendar.getInstance().apply { timeInMillis = nowMs }
    var yr = c2.get(Calendar.YEAR) - c1.get(Calendar.YEAR)
    var mo = c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH)
    var da = c2.get(Calendar.DAY_OF_MONTH) - c1.get(Calendar.DAY_OF_MONTH)
    if (da < 0) {
        mo--
        val pm = (c2.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        da += pm.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    if (mo < 0) {
        yr--
        mo += 12
    }
    val nextBday = Calendar.getInstance().apply {
        set(Calendar.YEAR, c2.get(Calendar.YEAR))
        set(Calendar.MONTH, c1.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, c1.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        if (timeInMillis <= nowMs) add(Calendar.YEAR, 1)
    }
    val daysToBday = ((nextBday.timeInMillis - nowMs) / 86_400_000L) + 1L
    return buildString {
        appendLine("Exact Age: $yr yr, $mo mo, $da d")
        appendLine("Live Seconds Lived: %,d s".format(Locale.US, diffSec))
        appendLine("Total Minutes: %,d min".format(Locale.US, mins))
        appendLine("Total Hours: %,d hrs".format(Locale.US, hrs))
        appendLine("Total Days: %,d days".format(Locale.US, days))
        append("Next Birthday: in $daysToBday day(s)")
    }
}
