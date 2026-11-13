package com.example.tools.health

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.design.SwissControlShape
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.HealthLogEntity
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthMoreScreen(
    meta: ToolMeta,
    healthLogs: List<HealthLogEntity>,
    onAddHealthLog: (type: String, primary: Double, secondary: Double, label: String, note: String, completed: Boolean) -> Unit,
    onDeleteHealthLog: (Long) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.MINT
    var outputSummary by remember(meta.id) { mutableStateOf("") }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputSummary,
        outputFileName = "${meta.id}_log.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "hlth-cycle" -> {
                var cycleLengthDays by remember { mutableIntStateOf(28) }
                var periodLengthDays by remember { mutableIntStateOf(5) }
                var selectedSymptom by remember { mutableStateOf("Normal") }
                var cycleNotes by remember { mutableStateOf("") }
                val cycleLogs = healthLogs.filter { it.logType == "CYCLE_DAY" }
                val lastPeriodMs = cycleLogs.maxOfOrNull { it.primaryVal.toLong() } ?: System.currentTimeMillis()
                val dayMs = 86_400_000L
                val daysSince = ((System.currentTimeMillis() - lastPeriodMs) / dayMs).toInt().coerceAtLeast(0)
                val nextPeriodMs = lastPeriodMs + cycleLengthDays * dayMs
                val ovulationMs = nextPeriodMs - 14L * dayMs
                val fertileStartMs = ovulationMs - 4L * dayMs
                val fertileEndMs = ovulationMs + 1L * dayMs
                val df = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

                outputSummary = "Cycle Day: ${daysSince + 1}/$cycleLengthDays · Next Period: ${df.format(Date(nextPeriodMs))} · Est. Ovulation: ${df.format(Date(ovulationMs))}"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CYCLE FORECAST", style = DotkitType.monoLabel(10), color = palette.ink2)
                        DotMatrixReadout("DAY ${daysSince + 1}", "NEXT PERIOD: ${df.format(Date(nextPeriodMs))}", hue)
                        Text(
                            "Est. Ovulation: ${df.format(Date(ovulationMs))}\nFertile Window: ${df.format(Date(fertileStartMs))} – ${df.format(Date(fertileEndMs))}",
                            style = DotkitType.monoData(12),
                            color = palette.ink
                        )
                        PixelSteppedSlider("Cycle Length", cycleLengthDays, 21..40, 1, " days", hue, { cycleLengthDays = it })
                        PixelSteppedSlider("Period Duration", periodLengthDays, 2..10, 1, " days", hue, { periodLengthDays = it })
                        Text("SYMPTOM / FLOW TAG", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Period Start", "Light Flow", "Heavy Flow", "Cramps", "Fatigue", "High Energy", "Normal").forEach { tag ->
                                PixelChip(tag, selected = selectedSymptom == tag, chapterHue = hue, onClick = { selectedSymptom = tag })
                            }
                        }
                        PixelTextField(cycleNotes, { cycleNotes = it }, "Optional daily note…", chapterHue = hue)
                        PixelButton("Log Entry Today", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            val now = System.currentTimeMillis()
                            onAddHealthLog("CYCLE_DAY", now.toDouble(), cycleLengthDays.toDouble(), selectedSymptom, cycleNotes.trim(), false)
                            cycleNotes = ""
                            onStatusToast("Cycle entry saved.")
                        })
                    }
                }
                if (cycleLogs.isNotEmpty()) {
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("HISTORY (${cycleLogs.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                            cycleLogs.take(10).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${entry.label} · ${df.format(Date(entry.primaryVal.toLong()))}", style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        if (entry.note.isNotBlank()) Text(entry.note, style = DotkitType.monoData(11), color = palette.ink2)
                                    }
                                    PixelChip("✕", onClick = { onDeleteHealthLog(entry.id) })
                                }
                            }
                        }
                    }
                }
            }

            "hlth-workout" -> {
                var exerciseName by remember { mutableStateOf("Bench Press") }
                var setsStr by remember { mutableStateOf("0") }
                var repsStr by remember { mutableStateOf("0") }
                var weightStr by remember { mutableStateOf("0") }
                var unitKg by remember { mutableStateOf(true) }
                val logs = healthLogs.filter { it.logType == "WORKOUT_VOL" }

                val sets = (setsStr.toIntOrNull() ?: 0).coerceAtLeast(0)
                val reps = (repsStr.toIntOrNull() ?: 0).coerceAtLeast(0)
                val wt = (weightStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                val volume = sets * reps * wt
                val epley1Rm = if (reps > 1 && wt > 0) wt * (1.0 + reps / 30.0) else wt
                val unitLbl = if (unitKg) "kg" else "lb"
                val totalSessionVol = logs.sumOf { it.primaryVal }

                outputSummary = "Latest: $exerciseName ${sets}×${reps} @ ${"%.1f".format(wt)} $unitLbl = ${"%.0f".format(volume)} $unitLbl · Est 1RM=${"%.1f".format(epley1Rm)} $unitLbl"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("${"%.0f".format(volume)} $unitLbl", "EST 1RM: ${"%.1f".format(epley1Rm)} $unitLbl", hue)
                            PixelChip(unitLbl.uppercase(), selected = true, chapterHue = hue, onClick = { unitKg = !unitKg })
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Bench Press", "Squat", "Deadlift", "Overhead Press", "Pull-Ups", "Row").forEach { ex ->
                                PixelChip(ex, selected = exerciseName == ex, chapterHue = hue, onClick = { exerciseName = ex })
                            }
                        }
                        PixelTextField(exerciseName, { exerciseName = it }, "Exercise name…", chapterHue = hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("SETS", style = DotkitType.monoLabel(9), color = palette.ink2)
                                PixelNumberField(setsStr, { setsStr = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("REPS", style = DotkitType.monoLabel(9), color = palette.ink2)
                                PixelNumberField(repsStr, { repsStr = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("WEIGHT ($unitLbl)", style = DotkitType.monoLabel(9), color = palette.ink2)
                                PixelNumberField(weightStr, { weightStr = it }, chapterHue = hue)
                            }
                        }
                        PixelButton(
                            text = "Log Set Volume",
                            chapterHue = hue,
                            enabled = exerciseName.isNotBlank() && sets > 0 && reps > 0,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onAddHealthLog("WORKOUT_VOL", volume, epley1Rm, exerciseName.trim(), "${sets}×${reps} @ ${"%.1f".format(wt)} $unitLbl", false)
                                onStatusToast("Logged $exerciseName (${"%.0f".format(volume)} $unitLbl)")
                            }
                        )
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LOGGED SETS (${logs.size}) · TOTAL VOLUME: ${"%.0f".format(totalSessionVol)}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (logs.isEmpty()) {
                            Text("No workout sets logged yet.", style = DotkitType.bodyUi(12), color = palette.ink3)
                        } else {
                            logs.take(12).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${entry.label} · ${entry.note}", style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        Text("Vol: ${"%.0f".format(entry.primaryVal)} · Est 1RM: ${"%.1f".format(entry.secondaryVal)}", style = DotkitType.monoData(11), color = palette.ink2)
                                    }
                                    PixelChip("✕", onClick = { onDeleteHealthLog(entry.id) })
                                }
                            }
                        }
                    }
                }
            }

            else -> { // hlth-posture
                val drills = remember {
                    listOf(
                        "Chin Tucks (Cervical Reset)" to "Gently glide chin straight back, lengthening the back of your neck. Hold level eyes.",
                        "Shoulder Blade Squeeze" to "Draw shoulders down and back together, opening your chest away from screen hunch.",
                        "Thoracic Desk Extension" to "Clasp hands overhead or behind head and gently arch upper back over chair.",
                        "Wrist & Forearm Extensor Stretch" to "Extend arm straight, palm down, gently flex fingers down to relieve typing tension.",
                        "Standing Hip Flexor Opener" to "Stand tall in staggered stance, tuck pelvis slightly and squeeze glutes to counter sitting."
                    )
                }
                var holdSec by remember { mutableIntStateOf(30) }
                var drillIdx by remember { mutableIntStateOf(0) }
                var running by remember { mutableStateOf(false) }
                var remainSec by remember { mutableIntStateOf(30) }

                LaunchedEffect(running, drillIdx) {
                    if (running) {
                        while (running && remainSec > 0) {
                            delay(1000L)
                            remainSec--
                        }
                        if (running && remainSec == 0) {
                            scope.launch(Dispatchers.IO) { playPostureBeep(880.0, 120) }
                            if (drillIdx < drills.lastIndex) {
                                drillIdx++
                                remainSec = holdSec
                            } else {
                                running = false
                                onStatusToast("Complete 5-drill posture reset finished!")
                            }
                        }
                    }
                }

                val current = drills[drillIdx]
                outputSummary = "Posture Routine (${drillIdx + 1}/${drills.size}): ${current.first} · ${remainSec}s left"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("DRILL ${drillIdx + 1} OF ${drills.size}", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp(if (running) "ACTIVE" else "READY", if (running) palette.ok else palette.ink3)
                        }
                        DotMatrixReadout("%02d s".format(remainSec), current.first.uppercase(), hue)
                        PixelProgressBar((remainSec.toFloat() / holdSec.coerceAtLeast(1)), hue)
                        Text(current.second, style = DotkitType.bodyUi(13), color = palette.ink)
                        PixelSteppedSlider("Hold Per Drill", holdSec, 15..90, 5, "s", hue, {
                            holdSec = it
                            if (!running) remainSec = it
                        })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (running) "Pause" else "Start Routine",
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!running && remainSec <= 0) remainSec = holdSec
                                    running = !running
                                }
                            )
                            PixelButton(
                                text = "Next",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = {
                                    drillIdx = (drillIdx + 1) % drills.size
                                    remainSec = holdSec
                                }
                            )
                            PixelButton(
                                text = "Reset",
                                variant = PixelButtonVariant.OUTLINE,
                                onClick = {
                                    running = false
                                    drillIdx = 0
                                    remainSec = holdSec
                                }
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("ROUTINE CHECKLIST", style = DotkitType.monoLabel(10), color = palette.ink2)
                        drills.forEachIndexed { i, (title, _) ->
                            PixelChip(
                                label = "${i + 1}. $title",
                                selected = i == drillIdx,
                                chapterHue = hue,
                                onClick = { drillIdx = i; remainSec = holdSec }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun playPostureBeep(freqHz: Double, ms: Int) {
    val sr = 22050
    val count = (sr * ms / 1000).coerceAtLeast(200)
    val pcm = ShortArray(count) { i ->
        val env = 1.0 - (i.toDouble() / count)
        (sin(2.0 * PI * freqHz * i / sr) * env * 0.45 * Short.MAX_VALUE).toInt().toShort()
    }
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes((pcm.size * 2).coerceAtLeast(2048))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    runCatching {
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(ms.toLong() + 30L)
    }
    runCatching { track.stop(); track.release() }
}
