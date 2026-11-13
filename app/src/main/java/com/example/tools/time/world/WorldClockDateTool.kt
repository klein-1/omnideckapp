package com.example.tools.time.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class TimezoneEntry(
    val id: String,
    val displayName: String,
    val region: String
)

object WorldTimezoneDirectory {
    val ALL_ZONES: List<TimezoneEntry> by lazy {
        TimeZone.getAvailableIDs()
            .filter { id ->
                id.contains("/") &&
                    !id.startsWith("Etc/") &&
                    !id.startsWith("SystemV/")
            }
            .distinct()
            .sorted()
            .map { id ->
                val parts = id.split("/")
                val region = parts.firstOrNull().orEmpty()
                val city = parts.drop(1).joinToString(" · ").replace("_", " ")
                TimezoneEntry(id = id, displayName = city, region = region)
            }
    }

    fun filter(query: String): List<TimezoneEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ALL_ZONES
        return ALL_ZONES.filter { z ->
            z.id.lowercase().contains(q) ||
                z.displayName.lowercase().contains(q) ||
                z.region.lowercase().contains(q) ||
                TimeZone.getTimeZone(z.id).displayName.lowercase().contains(q)
        }
    }
}

private val FEATURED_ZONE_IDS = listOf(
    "UTC",
    "Europe/London",
    "America/New_York",
    "America/Los_Angeles",
    "Asia/Kolkata",
    "Asia/Dubai",
    "Asia/Singapore",
    "Asia/Tokyo",
    "Australia/Sydney"
)

private enum class WorldMode(val label: String) {
    CLOCKS("All Timezones"),
    MEETING("Meeting Planner"),
    DATE_DIFF("Date & Age Diff"),
    EPOCH("Unix Epoch")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldClockDateTool(
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
    var mode by remember { mutableStateOf(WorldMode.CLOCKS) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var tzQuery by remember { mutableStateOf("") }
    val pinnedZones = remember { mutableStateListOf<String>().apply { addAll(FEATURED_ZONE_IDS.take(6)) } }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val allZones = remember { WorldTimezoneDirectory.ALL_ZONES }
    val matchingZones = remember(tzQuery) {
        if (tzQuery.isBlank()) {
            allZones.filter { it.id in FEATURED_ZONE_IDS }
                .ifEmpty { allZones.take(15) }
        } else {
            WorldTimezoneDirectory.filter(tzQuery).take(60)
        }
    }

    // Date Diff state (YYYY-MM-DD)
    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var startDateStr by remember { mutableStateOf(todayStr) }
    var endDateStr by remember { mutableStateOf(todayStr) }

    // Epoch state
    var epochInput by remember { mutableStateOf((System.currentTimeMillis() / 1000L).toString()) }

    val diffSummary = remember(startDateStr, endDateStr) {
        computeDateDiff(startDateStr, endDateStr)
    }

    val epochSummary = remember(epochInput) {
        val raw = epochInput.trim().toLongOrNull()
        if (raw == null) {
            "Enter a valid integer Unix timestamp (seconds or 13-digit ms)."
        } else {
            val ms = if (abs(raw) > 99_999_999_999L) raw else raw * 1000L
            val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(ms))
            val local = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(Date(ms))
            "UTC: $utc\nLocal: $local"
        }
    }

    val exportText = when (mode) {
        WorldMode.CLOCKS, WorldMode.MEETING -> pinnedZones.joinToString("\n") { id ->
            val fmt = SimpleDateFormat("HH:mm:ss · EEE dd MMM", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(id)
            }
            "$id: ${fmt.format(Date(nowMs))}"
        }
        WorldMode.DATE_DIFF -> diffSummary
        WorldMode.EPOCH -> epochSummary
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportText,
        outputFileName = "world_time_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Search across all ${allZones.size} global IANA time zones, pin your favorite regions side by side, calculate exact date spans, and convert Unix epoch timestamps."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorldMode.entries.forEach { m ->
                PixelChip(label = m.label, selected = mode == m, chapterHue = hue, onClick = { mode = m })
            }
        }

        when (mode) {
            WorldMode.CLOCKS -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val localFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                        val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy · z", Locale.US)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.paper)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("LOCAL DEVICE (${TimeZone.getDefault().id})", style = DotkitType.monoLabel(10), color = palette.ink2)
                                DotMatrixReadout(localFmt.format(Date(nowMs)), sizeSp = 36, accentColor = palette.ink)
                                Text(dateFmt.format(Date(nowMs)), style = DotkitType.bodyUi(13), color = palette.ink2)
                            }
                        }

                        PixelTextField(
                            value = tzQuery,
                            onValueChange = { tzQuery = it },
                            placeholder = "Search ${allZones.size} timezones (e.g. Tokyo, India, Berlin, Sao_Paulo, UTC)…",
                            chapterHue = hue,
                            tag = "timezone_search_input"
                        )

                        if (tzQuery.isBlank() && pinnedZones.isNotEmpty()) {
                            Text("PINNED ZONES (${pinnedZones.size}) · TAP ANY ZONE BELOW TO PIN/UNPIN", style = DotkitType.monoLabel(9), color = palette.ink2)
                            pinnedZones.forEach { id ->
                                TimezoneRowCard(
                                    tzId = id,
                                    displayName = id.substringAfter("/").replace("_", " "),
                                    region = id.substringBefore("/", "Global"),
                                    nowMs = nowMs,
                                    pinned = true,
                                    hue = hue,
                                    onTogglePin = { pinnedZones.remove(id) }
                                )
                            }
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        }

                        val headerLabel = if (tzQuery.isBlank()) "FEATURED ZONES (TYPE ABOVE TO SEARCH ALL ${allZones.size})"
                        else "SEARCH MATCHES (${matchingZones.size})"
                        Text(headerLabel, style = DotkitType.monoLabel(10), color = palette.ink2)

                        if (matchingZones.isEmpty()) {
                            Text("No timezones matched '$tzQuery'. Try city or continent names.", style = DotkitType.bodyUi(13), color = palette.ink2)
                        } else {
                            matchingZones.forEach { entry ->
                                val isPin = entry.id in pinnedZones
                                TimezoneRowCard(
                                    tzId = entry.id,
                                    displayName = entry.displayName,
                                    region = entry.region,
                                    nowMs = nowMs,
                                    pinned = isPin,
                                    hue = hue,
                                    onTogglePin = {
                                        if (isPin) pinnedZones.remove(entry.id) else pinnedZones.add(0, entry.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            WorldMode.MEETING -> {
                var selectedUtcHr by remember { mutableIntStateOf(14) }
                val todayUtcMidnightMs = remember {
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("24-HOUR GLOBAL MEETING OVERLAP (${pinnedZones.size} ZONES)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text("Tap a UTC hour slot below to inspect local work hours (09:00–17:59 highlighted green).", style = DotkitType.bodyUi(12), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (utcH in 0..23) {
                                val allBiz = pinnedZones.all { id ->
                                    val tzH = ((utcH * 60 + TimeZone.getTimeZone(id).getOffset(todayUtcMidnightMs) / 60_000) / 60 + 24) % 24
                                    tzH in 9..17
                                }
                                PixelChip(
                                    label = "%02d".format(utcH),
                                    selected = selectedUtcHr == utcH,
                                    chapterHue = if (allBiz) ChapterHue.TEAL else hue,
                                    onClick = { selectedUtcHr = utcH }
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        val slotMs = todayUtcMidnightMs + selectedUtcHr * 3_600_000L
                        pinnedZones.forEach { id ->
                            val tz = TimeZone.getTimeZone(id)
                            val fmt = SimpleDateFormat("HH:mm · EEE", Locale.US).apply { timeZone = tz }
                            val hr = ((selectedUtcHr * 60 + tz.getOffset(slotMs) / 60_000) / 60 + 24) % 24
                            val inBiz = hr in 9..17
                            val isQuiet = hr < 7 || hr >= 22
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.paper).padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(id.substringAfter("/").replace("_", " "), style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(fmt.format(Date(slotMs)), style = DotkitType.monoData(13), color = palette.ink)
                                    PixelStamp(
                                        if (inBiz) "WORK" else if (isQuiet) "SLEEP" else "FLEX",
                                        if (inBiz) palette.ok else if (isQuiet) palette.err else palette.warn
                                    )
                                }
                            }
                        }
                    }
                }
            }

            WorldMode.DATE_DIFF -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("START DATE (YYYY-MM-DD)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = startDateStr,
                            onValueChange = { startDateStr = it },
                            placeholder = "YYYY-MM-DD",
                            chapterHue = hue
                        )
                        Text("END DATE (YYYY-MM-DD)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = endDateStr,
                            onValueChange = { endDateStr = it },
                            placeholder = "YYYY-MM-DD",
                            chapterHue = hue
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip(label = "Set End = Today", chapterHue = hue, onClick = { endDateStr = todayStr })
                            PixelChip(label = "Swap Dates", chapterHue = hue, onClick = {
                                val tmp = startDateStr
                                startDateStr = endDateStr
                                endDateStr = tmp
                            })
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text(
                            text = diffSummary,
                            style = DotkitType.monoData(13),
                            color = palette.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.paper)
                                .padding(12.dp)
                        )
                    }
                }
            }

            WorldMode.EPOCH -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UNIX TIMESTAMP (SEC OR MS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelChip(
                                label = "Now",
                                selected = true,
                                chapterHue = hue,
                                onClick = { epochInput = (System.currentTimeMillis() / 1000L).toString() }
                            )
                        }
                        PixelTextField(
                            value = epochInput,
                            onValueChange = { epochInput = it },
                            placeholder = "e.g. 1735689600",
                            chapterHue = hue
                        )
                        Text(
                            text = epochSummary,
                            style = DotkitType.monoData(13),
                            color = palette.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.paper)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimezoneRowCard(
    tzId: String,
    displayName: String,
    region: String,
    nowMs: Long,
    pinned: Boolean,
    hue: ChapterHue,
    onTogglePin: () -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val tz = remember(tzId) { TimeZone.getTimeZone(tzId) }
    val timeFmt = remember(tzId) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz } }
    val dayFmt = remember(tzId) { SimpleDateFormat("EEE dd MMM", Locale.US).apply { timeZone = tz } }
    val offsetMin = tz.getOffset(nowMs) / 60_000
    val sign = if (offsetMin >= 0) "+" else "-"
    val absMin = abs(offsetMin)
    val offsetText = if (absMin % 60 == 0) "UTC$sign${absMin / 60}" else "UTC$sign${absMin / 60}:%02d".format(absMin % 60)
    val dst = if (tz.inDaylightTime(Date(nowMs))) " · DST" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.paper)
            .border(1.dp, if (pinned) palette.chapterColor(hue).copy(alpha = 0.4f) else palette.hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onTogglePin)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                if (pinned) PixelStamp("★ PIN", palette.chapterColor(hue))
            }
            Text("$region · ${dayFmt.format(Date(nowMs))} · $offsetText$dst", style = DotkitType.monoLabel(10), color = palette.ink2)
        }
        Text(timeFmt.format(Date(nowMs)), style = DotkitType.readoutDoto(22), color = palette.chapterColor(hue))
    }
}

private fun computeDateDiff(startStr: String, endStr: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val d1 = runCatching { sdf.parse(startStr.trim()) }.getOrNull()
    val d2 = runCatching { sdf.parse(endStr.trim()) }.getOrNull()
    if (d1 == null || d2 == null) {
        return "Enter both dates in strict YYYY-MM-DD format (e.g. 1998-07-15)."
    }
    val (early, late) = if (d1.time <= d2.time) d1 to d2 else d2 to d1
    val c1 = Calendar.getInstance().apply { time = early }
    val c2 = Calendar.getInstance().apply { time = late }

    var years = c2.get(Calendar.YEAR) - c1.get(Calendar.YEAR)
    var months = c2.get(Calendar.MONTH) - c1.get(Calendar.MONTH)
    var days = c2.get(Calendar.DAY_OF_MONTH) - c1.get(Calendar.DAY_OF_MONTH)

    if (days < 0) {
        months--
        val prevMonth = (c2.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    if (months < 0) {
        years--
        months += 12
    }

    val totalDays = (late.time - early.time) / (1000L * 60L * 60L * 24L)
    val weeks = totalDays / 7L
    val remDays = totalDays % 7L
    val totalHours = totalDays * 24L
    return buildString {
        appendLine("Exact Span: $years yr, $months mo, $days day(s)")
        appendLine("Total Days: $totalDays days ($weeks wks, $remDays d)")
        append("Total Hours: $totalHours hrs")
    }
}
