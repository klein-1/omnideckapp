package com.example.tools.time.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.sound.PixelSoundEngine
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay

private data class MultiTimerEntry(
    val id: Long,
    val label: String,
    val totalMs: Long,
    val remainingMs: Long,
    val running: Boolean,
    val targetEndMs: Long
)

private enum class WatchTab(val label: String) {
    STOPWATCH("Stopwatch"),
    TIMER("Countdown Timer"),
    MULTI("Multi-Timers")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopwatchTimerTool(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.SKY

    var tab by remember { mutableStateOf(WatchTab.STOPWATCH) }

    // Stopwatch state (timestamp-based for accuracy)
    var swRunning by remember { mutableStateOf(false) }
    var swAccumulatedMs by remember { mutableLongStateOf(0L) }
    var swStartWallMs by remember { mutableLongStateOf(0L) }
    var swDisplayMs by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Pair<Int, Long>>() }

    LaunchedEffect(swRunning, swStartWallMs, swAccumulatedMs) {
        while (swRunning) {
            swDisplayMs = swAccumulatedMs + (System.currentTimeMillis() - swStartWallMs)
            delay(33L)
        }
    }

    // Countdown Timer state
    var timerTotalSec by remember { mutableIntStateOf(300) }
    var timerRemainingMs by remember { mutableLongStateOf(300_000L) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerTargetEndMs by remember { mutableLongStateOf(0L) }
    var timerFinished by remember { mutableStateOf(false) }

    LaunchedEffect(timerRunning, timerTargetEndMs) {
        while (timerRunning) {
            val rem = timerTargetEndMs - System.currentTimeMillis()
            if (rem <= 0L) {
                timerRemainingMs = 0L
                timerRunning = false
                timerFinished = true
                PixelSoundEngine.playBlip(true, 880, 140)
                onStatusToast("Countdown complete.")
            } else {
                timerRemainingMs = rem
                delay(50L)
            }
        }
    }

    // Concurrent Multi-Timers state
    var newTimerLabel by remember { mutableStateOf("") }
    var newTimerMins by remember { mutableStateOf("0") }
    val multiTimers = remember { mutableStateListOf<MultiTimerEntry>() }
    val anyMultiRunning = multiTimers.any { it.running }

    LaunchedEffect(anyMultiRunning) {
        while (anyMultiRunning) {
            val now = System.currentTimeMillis()
            for (i in multiTimers.indices) {
                val t = multiTimers[i]
                if (t.running) {
                    val rem = t.targetEndMs - now
                    if (rem <= 0L) {
                        multiTimers[i] = t.copy(remainingMs = 0L, running = false)
                        PixelSoundEngine.playBlip(true, 960, 160)
                        onStatusToast("${t.label} finished!")
                    } else {
                        multiTimers[i] = t.copy(remainingMs = rem)
                    }
                }
            }
            delay(100L)
        }
    }

    val exportCsv = remember(swDisplayMs, laps.size, timerRemainingMs, tab) {
        if (tab == WatchTab.STOPWATCH) {
            buildString {
                appendLine("lap,elapsed_ms,formatted")
                if (laps.isEmpty()) {
                    appendLine("0,$swDisplayMs,${formatMillis(swDisplayMs)}")
                } else {
                    laps.forEach { (num, ms) -> appendLine("$num,$ms,${formatMillis(ms)}") }
                }
            }.trim()
        } else {
            "countdown_remaining=${formatTimerSec((timerRemainingMs / 1000L).toInt())}"
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportCsv,
        outputFileName = "time_log_${System.currentTimeMillis() / 1000}.csv",
        outputKind = DataKind.CSV,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Timestamp-accurate stopwatch with split laps and a preset countdown timer (1m, 5m, 10m, 25m Pomodoro). Elapsed times calculate from system clock deltas so pauses and resumes remain exact."
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchTab.entries.forEach { t ->
                PixelChip(
                    label = t.label,
                    selected = tab == t,
                    chapterHue = hue,
                    onClick = { tab = t },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (tab == WatchTab.STOPWATCH) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ELAPSED TIME", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelStamp(if (swRunning) "RUNNING" else "PAUSED", if (swRunning) palette.ok else palette.ink3)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.paper)
                            .border(1.5.dp, palette.ink)
                            .padding(vertical = 20.dp, horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        DotMatrixReadout(
                            valueText = formatMillis(swDisplayMs),
                            sizeSp = 40,
                            accentColor = palette.ink
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PixelButton(
                            text = if (swRunning) "Pause" else "Start",
                            chapterHue = hue,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (swRunning) {
                                    swAccumulatedMs += System.currentTimeMillis() - swStartWallMs
                                    swDisplayMs = swAccumulatedMs
                                    swRunning = false
                                } else {
                                    swStartWallMs = System.currentTimeMillis()
                                    swRunning = true
                                }
                            }
                        )
                        PixelButton(
                            text = "Lap",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            enabled = swDisplayMs > 0L,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                laps.add(0, (laps.size + 1) to swDisplayMs)
                            }
                        )
                        PixelButton(
                            text = "Reset",
                            variant = PixelButtonVariant.DESTRUCTIVE,
                            enabled = !swRunning && swDisplayMs > 0L,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                swAccumulatedMs = 0L
                                swDisplayMs = 0L
                                laps.clear()
                            }
                        )
                    }

                    if (laps.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                        Text("LAPS (${laps.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            laps.take(10).forEachIndexed { idx, (lapNum, ms) ->
                                val prevMs = laps.getOrNull(idx + 1)?.second ?: 0L
                                val splitMs = (ms - prevMs).coerceAtLeast(0L)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(palette.paper)
                                        .border(1.dp, palette.ink3)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("#%02d".format(lapNum), style = DotkitType.monoLabel(11), color = palette.chapterColor(hue))
                                    Text("+${formatMillis(splitMs)}", style = DotkitType.monoData(12), color = palette.ink2)
                                    Text(formatMillis(ms), style = DotkitType.monoData(13), color = palette.ink)
                                }
                            }
                        }
                    }
                }
            }
        } else if (tab == WatchTab.TIMER) {
            val totalMs = (timerTotalSec * 1000L).coerceAtLeast(1000L)
            val progress = (timerRemainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("COUNTDOWN", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelStamp(
                            text = when {
                                timerFinished -> "DONE"
                                timerRunning -> "ACTIVE"
                                else -> "READY"
                            },
                            color = if (timerFinished) palette.ok else palette.chapterColor(hue)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.paper)
                            .border(1.5.dp, palette.ink)
                            .padding(vertical = 20.dp, horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        DotMatrixReadout(
                            valueText = formatTimerSec(((timerRemainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)),
                            sizeSp = 44,
                            accentColor = if (timerFinished) palette.ok else palette.ink
                        )
                    }

                    PixelProgressBar(progress = progress, chapterHue = hue)

                    Text("QUICK PRESETS", style = DotkitType.monoLabel(10), color = palette.ink2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60 to "1 min", 180 to "3 min", 300 to "5 min", 600 to "10 min", 1500 to "25 min").forEach { (sec, label) ->
                            PixelChip(
                                label = label,
                                selected = timerTotalSec == sec && !timerRunning,
                                chapterHue = hue,
                                onClick = {
                                    timerRunning = false
                                    timerFinished = false
                                    timerTotalSec = sec
                                    timerRemainingMs = sec * 1000L
                                }
                            )
                        }
                    }

                    PixelSteppedSlider(
                        label = "Duration (Minutes)",
                        value = (timerTotalSec / 60).coerceIn(1, 60),
                        valueRange = 1..60,
                        step = 1,
                        unitSuffix = " min",
                        chapterHue = hue,
                        onValueChange = { mins ->
                            timerRunning = false
                            timerFinished = false
                            timerTotalSec = mins * 60
                            timerRemainingMs = mins * 60_000L
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PixelButton(
                            text = if (timerRunning) "Pause" else "Start",
                            chapterHue = hue,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (timerRunning) {
                                    timerRemainingMs = (timerTargetEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
                                    timerRunning = false
                                } else {
                                    if (timerRemainingMs <= 0L) timerRemainingMs = timerTotalSec * 1000L
                                    timerFinished = false
                                    timerTargetEndMs = System.currentTimeMillis() + timerRemainingMs
                                    timerRunning = true
                                }
                            }
                        )
                        PixelButton(
                            text = "Reset",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                timerRunning = false
                                timerFinished = false
                                timerRemainingMs = timerTotalSec * 1000L
                            }
                        )
                    }
                }
            }
        } else {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CONCURRENT MULTI-TIMERS (${multiTimers.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(newTimerLabel, { newTimerLabel = it }, "Label (e.g. Tea, Oven)", chapterHue = hue, modifier = Modifier.weight(1.4f))
                        PixelNumberField(newTimerMins, { newTimerMins = it }, "Mins", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(0.7f))
                    }
                    PixelButton(
                        text = "+ Add & Start Timer",
                        chapterHue = hue,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val mins = (newTimerMins.toIntOrNull() ?: 0).coerceIn(1, 720)
                            val ms = mins * 60_000L
                            multiTimers.add(
                                MultiTimerEntry(
                                    id = System.nanoTime(),
                                    label = newTimerLabel.ifBlank { "${mins}m Timer" },
                                    totalMs = ms,
                                    remainingMs = ms,
                                    running = true,
                                    targetEndMs = System.currentTimeMillis() + ms
                                )
                            )
                            newTimerLabel = ""
                        }
                    )
                    if (multiTimers.isEmpty()) {
                        Text("Add multiple independent countdowns to run simultaneously.", style = DotkitType.bodyUi(13), color = palette.ink2)
                    } else {
                        multiTimers.forEachIndexed { idx, entry ->
                            Row(
                                Modifier.fillMaxWidth().background(palette.paper).border(1.dp, palette.hairline).padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                    Text(formatTimerSec(((entry.remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)), style = DotkitType.readoutDoto(20), color = if (entry.remainingMs == 0L) palette.ok else palette.chapterColor(hue))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PixelChip(if (entry.running) "Pause" else "Go", selected = entry.running, chapterHue = hue, onClick = {
                                        if (entry.running) {
                                            val rem = (entry.targetEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
                                            multiTimers[idx] = entry.copy(remainingMs = rem, running = false)
                                        } else {
                                            val base = if (entry.remainingMs <= 0L) entry.totalMs else entry.remainingMs
                                            multiTimers[idx] = entry.copy(remainingMs = base, running = true, targetEndMs = System.currentTimeMillis() + base)
                                        }
                                    })
                                    PixelChip("✕", onClick = { multiTimers.removeAt(idx) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalCentis = (ms / 10L).coerceAtLeast(0L)
    val centis = totalCentis % 100
    val totalSec = totalCentis / 100
    val secs = totalSec % 60
    val mins = (totalSec / 60) % 60
    val hrs = totalSec / 3600
    return if (hrs > 0) "%02d:%02d:%02d.%02d".format(hrs, mins, secs, centis)
    else "%02d:%02d.%02d".format(mins, secs, centis)
}

private fun formatTimerSec(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins, secs)
    else "%02d:%02d".format(mins, secs)
}
