package com.example.tools.health

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.HealthLogEntity
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthExtraScreen(
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
        outputFileName = "${meta.id}_report.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "hlth-fast" -> {
                var goalHrs by remember { mutableIntStateOf(16) }
                var customGoalStr by remember { mutableStateOf("0") }
                var running by remember { mutableStateOf(false) }
                var startMs by remember { mutableLongStateOf(0L) }
                var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val logs = healthLogs.filter { it.logType == "FAST_HRS" }

                LaunchedEffect(running) {
                    while (running) {
                        nowMs = System.currentTimeMillis()
                        delay(1000L)
                    }
                }

                val elapsedSec = if (running && startMs > 0L) ((nowMs - startMs) / 1000L).coerceAtLeast(0L) else 0L
                val elapsedHrs = elapsedSec / 3600.0
                val effectiveGoal = (customGoalStr.toIntOrNull()?.takeIf { it > 0 } ?: goalHrs).coerceIn(1, 72)
                val progress = (elapsedHrs / effectiveGoal).toFloat().coerceIn(0f, 1f)
                val metabolicPhase = when {
                    !running -> "Idle — choose protocol and start"
                    elapsedHrs < 4 -> "Blood Sugar Rising / Digesting (0–4h)"
                    elapsedHrs < 12 -> "Post-Absorptive Transition (4–12h)"
                    elapsedHrs < 16 -> "Fat Oxidation / Ketogenesis Begins (12–16h)"
                    elapsedHrs < 24 -> "Deep Ketosis & Autophagy Signal (16–24h)"
                    else -> "Extended Autophagy & Renewal (24h+)"
                }
                outputSummary = "Protocol: ${effectiveGoal}h fast | Elapsed: ${"%.2f".format(elapsedHrs)}h | Phase: $metabolicPhase"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("FASTING PROTOCOL", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("14:10" to 14, "16:8" to 16, "18:6" to 18, "20:4" to 20, "OMAD 23:1" to 23).forEach { (lbl, hrs) ->
                                PixelChip(lbl, selected = goalHrs == hrs && (customGoalStr.toIntOrNull() ?: 0) == 0, chapterHue = hue, onClick = {
                                    goalHrs = hrs; customGoalStr = "0"
                                })
                            }
                        }
                        Text("CUSTOM GOAL HOURS (OPTIONAL)", style = DotkitType.monoLabel(9), color = palette.ink3)
                        PixelNumberField(customGoalStr, { customGoalStr = it }, allowDecimal = false, chapterHue = hue)

                        val h = elapsedSec / 3600
                        val m = (elapsedSec % 3600) / 60
                        val s = elapsedSec % 60
                        DotMatrixReadout("%02d:%02d:%02d".format(h, m, s), "TARGET ${effectiveGoal}H · ${(progress * 100).toInt()}% COMPLETE", hue)
                        PixelProgressBar(progress = progress, chapterHue = hue)
                        PixelStamp(metabolicPhase, palette.chapterColor(hue))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (running) "End & Log Fast" else "Start Fast Now",
                                chapterHue = hue,
                                onClick = {
                                    if (!running) {
                                        startMs = System.currentTimeMillis()
                                        nowMs = startMs
                                        running = true
                                    } else {
                                        val hrs = ((System.currentTimeMillis() - startMs) / 3600_000.0).coerceAtLeast(0.01)
                                        onAddHealthLog("FAST_HRS", hrs, effectiveGoal.toDouble(), "${"%.2f".format(hrs)}h / ${effectiveGoal}h", metabolicPhase, hrs >= effectiveGoal)
                                        running = false
                                        onStatusToast("Logged ${"%.2f".format(hrs)}h fast")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (running) {
                                PixelButton("Reset", variant = PixelButtonVariant.OUTLINE, onClick = { running = false; startMs = 0L }, modifier = Modifier.weight(0.5f))
                            }
                        }
                        logs.take(8).forEach { l ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${l.label} ${if (l.completed) "✓" else ""}", style = DotkitType.monoData(12), color = palette.ink)
                                PixelChip("Delete", onClick = { onDeleteHealthLog(l.id) })
                            }
                        }
                    }
                }
            }

            "hlth-bodyfat" -> {
                var isMale by remember { mutableStateOf(true) }
                var heightCm by remember { mutableStateOf("0") }
                var neckCm by remember { mutableStateOf("0") }
                var waistCm by remember { mutableStateOf("0") }
                var hipCm by remember { mutableStateOf("0") }
                var weightKg by remember { mutableStateOf("0") }

                val h = heightCm.toDoubleOrNull() ?: 0.0
                val n = neckCm.toDoubleOrNull() ?: 0.0
                val w = waistCm.toDoubleOrNull() ?: 0.0
                val hip = hipCm.toDoubleOrNull() ?: 0.0
                val wt = weightKg.toDoubleOrNull() ?: 0.0

                val bfPct = remember(isMale, h, n, w, hip) {
                    if (h <= 0.0 || n <= 0.0 || w <= 0.0) null
                    else if (isMale) {
                        val diff = w - n
                        if (diff <= 0.0) null
                        else (495.0 / (1.0324 - 0.19077 * kotlin.math.log10(diff) + 0.15456 * kotlin.math.log10(h)) - 450.0).coerceIn(2.0, 65.0)
                    } else {
                        val sum = w + hip - n
                        if (hip <= 0.0 || sum <= 0.0) null
                        else (495.0 / (1.29579 - 0.35004 * kotlin.math.log10(sum) + 0.22100 * kotlin.math.log10(h)) - 450.0).coerceIn(5.0, 68.0)
                    }
                }

                val category = when {
                    bfPct == null -> "Enter measurements above"
                    isMale -> when {
                        bfPct < 6 -> "Essential Fat"
                        bfPct < 14 -> "Athletic"
                        bfPct < 18 -> "Fitness"
                        bfPct < 25 -> "Average"
                        else -> "Obese Range"
                    }
                    else -> when {
                        bfPct < 14 -> "Essential Fat"
                        bfPct < 21 -> "Athletic"
                        bfPct < 25 -> "Fitness"
                        bfPct < 32 -> "Average"
                        else -> "Obese Range"
                    }
                }
                val fatMass = if (bfPct != null && wt > 0.0) wt * bfPct / 100.0 else 0.0
                val leanMass = if (bfPct != null && wt > 0.0) wt - fatMass else 0.0
                outputSummary = if (bfPct != null) "US Navy Body Fat: ${"%.1f".format(bfPct)}% ($category) | Fat: ${"%.1f".format(fatMass)}kg | Lean: ${"%.1f".format(leanMass)}kg" else ""

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("Male", selected = isMale, chapterHue = hue, onClick = { isMale = true })
                            PixelChip("Female", selected = !isMale, chapterHue = hue, onClick = { isMale = false })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("HEIGHT (CM)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(heightCm, { heightCm = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("WEIGHT (KG)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(weightKg, { weightKg = it }, chapterHue = hue)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("NECK (CM)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(neckCm, { neckCm = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("WAIST (CM)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(waistCm, { waistCm = it }, chapterHue = hue)
                            }
                        }
                        if (!isMale) {
                            Text("HIP CIRCUMFERENCE (CM)", style = DotkitType.monoLabel(9), color = palette.ink3)
                            PixelNumberField(hipCm, { hipCm = it }, chapterHue = hue)
                        }
                        DotMatrixReadout(
                            value = if (bfPct != null) "${"%.1f".format(bfPct)}%" else "-- %",
                            label = "$category · LEAN ${"%.1f".format(leanMass)}kg · FAT ${"%.1f".format(fatMass)}kg",
                            chapterHue = hue
                        )
                    }
                }
            }

            "hlth-hiit" -> {
                var warmupSec by remember { mutableStateOf("10") }
                var workSec by remember { mutableStateOf("20") }
                var restSec by remember { mutableStateOf("10") }
                var roundsCount by remember { mutableStateOf("8") }

                var running by remember { mutableStateOf(false) }
                var phase by remember { mutableStateOf("READY") } // WARMUP, WORK, REST, DONE
                var curRound by remember { mutableIntStateOf(1) }
                var secLeft by remember { mutableIntStateOf(0) }

                fun playCue(freqHz: Int, ms: Int) {
                    scope.launch(Dispatchers.Default) {
                        runCatching {
                            val sr = 44100
                            val count = (sr * ms / 1000).coerceAtLeast(1)
                            val pcm = ShortArray(count) { i ->
                                val env = if (i < 200) i / 200.0 else if (i > count - 200) (count - i) / 200.0 else 1.0
                                (sin(2.0 * PI * freqHz * i / sr) * 0.45 * env * Short.MAX_VALUE).toInt().toShort()
                            }
                            val track = AudioTrack.Builder()
                                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                                .setBufferSizeInBytes(pcm.size * 2)
                                .setTransferMode(AudioTrack.MODE_STATIC)
                                .build()
                            track.write(pcm, 0, pcm.size)
                            track.play()
                            delay(ms.toLong() + 60L)
                            track.release()
                        }
                    }
                }

                val wUp = (warmupSec.toIntOrNull() ?: 0).coerceAtLeast(0)
                val wk = (workSec.toIntOrNull() ?: 20).coerceAtLeast(1)
                val rst = (restSec.toIntOrNull() ?: 10).coerceAtLeast(1)
                val totalR = (roundsCount.toIntOrNull() ?: 8).coerceIn(1, 50)

                LaunchedEffect(running) {
                    if (!running) return@LaunchedEffect
                    if (phase == "READY" || phase == "DONE") {
                        curRound = 1
                        if (wUp > 0) { phase = "WARMUP"; secLeft = wUp; playCue(520, 120) }
                        else { phase = "WORK"; secLeft = wk; playCue(880, 180) }
                    }
                    while (running) {
                        delay(1000L)
                        if (!running) break
                        if (secLeft > 1) {
                            secLeft--
                            if (secLeft <= 3) playCue(660, 70)
                        } else {
                            when (phase) {
                                "WARMUP" -> { phase = "WORK"; secLeft = wk; playCue(980, 220) }
                                "WORK" -> {
                                    if (curRound >= totalR) {
                                        phase = "DONE"; secLeft = 0; running = false; playCue(1046, 350)
                                        onStatusToast("HIIT Session Completed ($totalR rounds)")
                                    } else {
                                        phase = "REST"; secLeft = rst; playCue(440, 180)
                                    }
                                }
                                "REST" -> {
                                    curRound++; phase = "WORK"; secLeft = wk; playCue(980, 220)
                                }
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("Tabata 20/10×8", onClick = { warmupSec = "10"; workSec = "20"; restSec = "10"; roundsCount = "8" })
                            PixelChip("EMOM 45/15×10", onClick = { warmupSec = "10"; workSec = "45"; restSec = "15"; roundsCount = "10" })
                            PixelChip("Sprint 30/30×6", onClick = { warmupSec = "5"; workSec = "30"; restSec = "30"; roundsCount = "6" })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("WARM (S)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(warmupSec, { warmupSec = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("WORK (S)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(workSec, { workSec = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("REST (S)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(restSec, { restSec = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("ROUNDS", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(roundsCount, { roundsCount = it }, allowDecimal = false, chapterHue = hue)
                            }
                        }
                        DotMatrixReadout("${secLeft}s", "$phase · ROUND $curRound / $totalR", hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(if (running) "Pause" else "Start HIIT", chapterHue = hue, onClick = { running = !running }, modifier = Modifier.weight(1f))
                            PixelButton("Reset", variant = PixelButtonVariant.OUTLINE, onClick = { running = false; phase = "READY"; curRound = 1; secLeft = 0 }, modifier = Modifier.weight(0.6f))
                        }
                    }
                }
            }

            else -> { // hlth-caffeine
                var customMg by remember { mutableStateOf("0") }
                var customLabel by remember { mutableStateOf("Custom Drink") }
                val logs = healthLogs.filter { it.logType == "CAFFEINE_MG" }
                val now = System.currentTimeMillis()
                val cutoff24h = now - 24 * 3600_000L
                val recent24h = logs.filter { it.createdAt >= cutoff24h }
                val totalTodayMg = recent24h.sumOf { it.primaryVal }
                // 5.7h pharmacological half-life
                val activeMg = recent24h.sumOf { entry ->
                    val hrsAgo = ((now - entry.createdAt) / 3600_000.0).coerceAtLeast(0.0)
                    entry.primaryVal * 0.5.pow(hrsAgo / 5.7)
                }
                val capFrac = (totalTodayMg / 400.0).toFloat().coerceIn(0f, 1f)
                outputSummary = "24h Intake: ${totalTodayMg.toInt()} mg / 400 mg cap | Est. Active in Plasma: ${activeMg.toInt()} mg"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("${activeMg.toInt()} mg", "ACTIVE NOW · 24H INTAKE ${totalTodayMg.toInt()} / 400 mg", hue)
                        PixelProgressBar(progress = capFrac, chapterHue = if (totalTodayMg > 400) ChapterHue.ROSE else hue)

                        val curveCol = palette.chapterColor(hue)
                        val gridCol = palette.hairline
                        Canvas(Modifier.fillMaxWidth().height(90.dp).border(1.dp, palette.hairline)) {
                            val path = Path()
                            for (px in 0..40) {
                                val hrsAhead = px * 0.3
                                val proj = recent24h.sumOf { entry ->
                                    val hrsAgo = ((now - entry.createdAt) / 3600_000.0).coerceAtLeast(0.0) + hrsAhead
                                    entry.primaryVal * 0.5.pow(hrsAgo / 5.7)
                                }
                                val x = (px / 40f) * size.width
                                val y = size.height - ((proj / 400.0).toFloat().coerceIn(0f, 1f) * (size.height - 12f)) - 6f
                                if (px == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawLine(gridCol, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1f)
                            drawPath(path, curveCol, style = Stroke(4f))
                        }
                        Text("12-hour projected caffeine clearance curve (5.7h half-life)", style = DotkitType.monoLabel(9), color = palette.ink3)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Espresso (64mg)" to 64.0, "Filter Coffee (95mg)" to 95.0, "Green Tea (35mg)" to 35.0, "Matcha (70mg)" to 70.0, "Energy Can (150mg)" to 150.0).forEach { (lbl, mg) ->
                                PixelButton("+$lbl", chapterHue = hue, onClick = {
                                    onAddHealthLog("CAFFEINE_MG", mg, 0.0, lbl, "", false)
                                })
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelTextField(customLabel, { customLabel = it }, "Drink", modifier = Modifier.weight(1.2f))
                            PixelNumberField(customMg, { customMg = it }, allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(0.8f))
                            PixelButton("Add", enabled = (customMg.toDoubleOrNull() ?: 0.0) > 0.0, variant = PixelButtonVariant.OUTLINE, onClick = {
                                val mg = customMg.toDoubleOrNull() ?: return@PixelButton
                                onAddHealthLog("CAFFEINE_MG", mg, 0.0, "${customLabel.ifBlank { "Caffeine" }} (${mg.toInt()}mg)", "", false)
                                customMg = "0"
                            })
                        }

                        logs.take(8).forEach { item ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.label, style = DotkitType.monoData(12), color = palette.ink)
                                PixelChip("Delete", onClick = { onDeleteHealthLog(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
