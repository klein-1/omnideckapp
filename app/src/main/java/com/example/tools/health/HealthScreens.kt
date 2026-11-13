package com.example.tools.health

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.HealthLogEntity
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthScreens(
    meta: ToolMeta,
    healthLogs: List<HealthLogEntity>,
    onAddHealthLog: (type: String, primary: Double, secondary: Double, label: String, note: String, completed: Boolean) -> Unit,
    onUpdateHealthLog: (HealthLogEntity) -> Unit,
    onDeleteHealthLog: (Long) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val haptics = LocalHapticFeedback.current
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
            "hlth-breathe" -> {
                var mode by remember { mutableStateOf("BOX") } // BOX 4-4-4-4 or RELAX 4-7-8
                var running by remember { mutableStateOf(false) }
                var phase by remember { mutableStateOf("Ready") }
                var secLeft by remember { mutableIntStateOf(4) }
                var targetFrac by remember { mutableStateOf(0.4f) }
                val animFrac by animateFloatAsState(targetFrac, tween(900), label = "breath")

                LaunchedEffect(running, mode) {
                    val steps = if (mode == "BOX") listOf("Inhale" to 4, "Hold" to 4, "Exhale" to 4, "Hold" to 4)
                    else listOf("Inhale" to 4, "Hold" to 7, "Exhale" to 8)
                    var idx = 0
                    while (running) {
                        val (name, secs) = steps[idx % steps.size]
                        phase = name
                        targetFrac = when (name) {
                            "Inhale" -> 0.92f
                            "Exhale" -> 0.35f
                            else -> targetFrac
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        for (s in secs downTo 1) {
                            if (!running) break
                            secLeft = s
                            delay(1000L)
                        }
                        idx++
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip("Box 4-4-4-4", selected = mode == "BOX", chapterHue = hue, onClick = { mode = "BOX"; running = false })
                            PixelChip("Relax 4-7-8", selected = mode == "RELAX", chapterHue = hue, onClick = { mode = "RELAX"; running = false })
                        }
                        val col = palette.chapterColor(hue)
                        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                            val r = (size.minDimension * 0.44f) * animFrac
                            drawCircle(col.copy(alpha = 0.22f), r, center)
                            drawCircle(col, r, center, style = Stroke(5f))
                        }
                        DotMatrixReadout("$phase · ${secLeft}s", "BREATHE CADENCE", hue)
                        PixelButton(if (running) "Pause" else "Start Session", onClick = { running = !running }, chapterHue = hue, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            "hlth-water" -> {
                var goalMl by remember { mutableStateOf("0") }
                var customMl by remember { mutableStateOf("0") }
                val logs = healthLogs.filter { it.logType == "WATER_ML" }
                val todayTotal = logs.sumOf { it.primaryVal }.toInt()
                val goal = goalMl.toIntOrNull() ?: 0
                val pct = if (goal > 0) (todayTotal.toFloat() / goal).coerceIn(0f, 1f) else 0f
                outputSummary = "Hydration Total: $todayTotal / $goal ml (${logs.size} entries)"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("$todayTotal / $goal ml", "HYDRATION GOAL", hue)
                        PixelProgressBar(progress = pct, chapterHue = hue)
                        Text("DAILY GOAL (ML)", style = DotkitType.monoLabel(9), color = palette.ink3)
                        PixelNumberField(goalMl, { goalMl = it }, allowDecimal = false, chapterHue = hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(150, 250, 500, 750).forEach { ml ->
                                PixelButton("+$ml ml", chapterHue = hue, onClick = {
                                    onAddHealthLog("WATER_ML", ml.toDouble(), 0.0, "$ml ml", "", false)
                                })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelNumberField(customMl, { customMl = it }, allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelButton("Log", enabled = (customMl.toDoubleOrNull() ?: 0.0) > 0.0, onClick = {
                                val v = customMl.toDoubleOrNull() ?: return@PixelButton
                                onAddHealthLog("WATER_ML", v, 0.0, "${v.toInt()} ml", "", false)
                                customMl = "0"
                            }, variant = PixelButtonVariant.OUTLINE)
                        }
                        logs.take(12).forEach { log ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("+${log.primaryVal.toInt()} ml", style = DotkitType.monoData(13), color = palette.ink)
                                PixelChip("Delete", onClick = { onDeleteHealthLog(log.id) })
                            }
                        }
                    }
                }
            }

            "hlth-meds" -> {
                var medName by remember { mutableStateOf("") }
                var dose by remember { mutableStateOf("") }
                var timeSlot by remember { mutableStateOf("08:00 AM") }
                val meds = healthLogs.filter { it.logType == "MED_REMINDER" }
                outputSummary = meds.joinToString("\n") { "${if (it.completed) "[x]" else "[ ]"} ${it.label} (${it.note})" }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(medName, { medName = it }, "Medication name")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(dose, { dose = it }, "Dose (e.g. 500mg)", modifier = Modifier.weight(1f))
                            PixelTextField(timeSlot, { timeSlot = it }, "Schedule", modifier = Modifier.weight(1f))
                        }
                        PixelButton("Add Medication Schedule", chapterHue = hue, onClick = {
                            if (medName.isNotBlank()) {
                                onAddHealthLog("MED_REMINDER", 1.0, 0.0, medName.trim(), "${dose.trim()} @ ${timeSlot.trim()}", false)
                                medName = ""; dose = ""
                            }
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SCHEDULES (${meds.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (meds.isEmpty()) Text("No medications added yet.", style = DotkitType.bodyUi(13), color = palette.ink2)
                        meds.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().border(1.dp, palette.hairline).clickable {
                                    onUpdateHealthLog(m.copy(completed = !m.completed))
                                }.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${if (m.completed) "[TAKEN] " else ""}${m.label}", style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                                    Text(m.note, style = DotkitType.monoData(11), color = palette.ink2)
                                }
                                PixelChip("Delete", onClick = { onDeleteHealthLog(m.id) })
                            }
                        }
                    }
                }
            }

            "hlth-hr" -> {
                var taps by remember { mutableStateOf<List<Long>>(emptyList()) }
                val bpm = remember(taps) {
                    if (taps.size < 2) 0
                    else {
                        val diffs = taps.zipWithNext { a, b -> b - a }.filter { it in 250..2500 }
                        if (diffs.isEmpty()) 0 else (60_000.0 / diffs.average()).toInt()
                    }
                }
                val history = healthLogs.filter { it.logType == "HR_BPM" }
                outputSummary = "Measured Pulse: $bpm BPM"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout(if (bpm > 0) "$bpm BPM" else "-- BPM", "TAP IN RHYTHM WITH PULSE (${taps.size} TAPS)", hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("TAP PULSE", chapterHue = hue, onClick = {
                                val now = System.currentTimeMillis()
                                val recent = if (taps.isNotEmpty() && now - taps.last() > 3000) emptyList() else taps
                                taps = (recent + now).takeLast(10)
                            }, modifier = Modifier.weight(2f).height(64.dp))
                            PixelButton("Reset", variant = PixelButtonVariant.OUTLINE, onClick = { taps = emptyList() }, modifier = Modifier.weight(1f).height(64.dp))
                        }
                        PixelButton("Save $bpm BPM to Log", enabled = bpm in 30..240, variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            onAddHealthLog("HR_BPM", bpm.toDouble(), 0.0, "$bpm BPM", "", false)
                            onStatusToast("Logged $bpm BPM")
                        }, modifier = Modifier.fillMaxWidth())
                        history.take(8).forEach { h ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${h.primaryVal.toInt()} BPM", style = DotkitType.monoData(13), color = palette.ink)
                                PixelChip("Delete", onClick = { onDeleteHealthLog(h.id) })
                            }
                        }
                    }
                }
            }

            "hlth-calorie" -> {
                var age by remember { mutableStateOf("0") }
                var weightKg by remember { mutableStateOf("0") }
                var heightCm by remember { mutableStateOf("0") }
                var isMale by remember { mutableStateOf(true) }
                var actIdx by remember { mutableIntStateOf(2) }
                val actFactors = listOf("Sedentary" to 1.2, "Light" to 1.375, "Moderate" to 1.55, "Active" to 1.725, "Athlete" to 1.9)

                val a = age.toDoubleOrNull() ?: 0.0
                val w = weightKg.toDoubleOrNull() ?: 0.0
                val h = heightCm.toDoubleOrNull() ?: 0.0
                val bmr = if (a > 0.0 && w > 0.0 && h > 0.0) (10 * w + 6.25 * h - 5 * a + (if (isMale) 5 else -161)).coerceAtLeast(0.0) else 0.0
                val tdee = bmr * actFactors[actIdx].second
                val proteinG = (tdee * 0.30 / 4.0).toInt()
                val carbsG = (tdee * 0.45 / 4.0).toInt()
                val fatG = (tdee * 0.25 / 9.0).toInt()
                outputSummary = "BMR=${bmr.toInt()} kcal | TDEE=${tdee.toInt()} kcal | P:${proteinG}g C:${carbsG}g F:${fatG}g"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("Male", selected = isMale, chapterHue = hue, onClick = { isMale = true })
                            PixelChip("Female", selected = !isMale, chapterHue = hue, onClick = { isMale = false })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("AGE", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(age, { age = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("WEIGHT KG", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(weightKg, { weightKg = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("HEIGHT CM", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(heightCm, { heightCm = it }, chapterHue = hue)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            actFactors.forEachIndexed { idx, (label, _) ->
                                PixelChip(label, selected = actIdx == idx, chapterHue = hue, onClick = { actIdx = idx })
                            }
                        }
                        DotMatrixReadout("${tdee.toInt()} kcal", "MAINTENANCE TDEE · BMR ${bmr.toInt()} kcal", hue)
                        Text("Loss (-500): ${if (tdee > 500) (tdee - 500).toInt() else 0} kcal · Gain (+350): ${if (tdee > 0) (tdee + 350).toInt() else 0} kcal", style = DotkitType.monoData(12), color = palette.ink)
                        Text("Balanced Macros (30/45/25): Protein ${proteinG}g · Carbs ${carbsG}g · Fat ${fatG}g", style = DotkitType.monoData(12), color = palette.ink2)
                    }
                }
            }

            "hlth-sleep" -> {
                var wakeHour by remember { mutableStateOf("0") }
                var wakeMin by remember { mutableStateOf("0") }
                var sleptHours by remember { mutableStateOf("0") }
                val logs = healthLogs.filter { it.logType == "SLEEP_MIN" }
                val h = wakeHour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                val m = wakeMin.toIntOrNull()?.coerceIn(0, 59) ?: 0
                val wakeTotalMin = h * 60 + m
                val bedtimes = listOf(6, 5, 4).map { cycles ->
                    val bMin = ((wakeTotalMin - (cycles * 90 + 15)) % 1440 + 1440) % 1440
                    "%02d:%02d (%d cycles · %.1fh)".format(bMin / 60, bMin % 60, cycles, cycles * 1.5)
                }
                outputSummary = "Wake %02d:%02d\nOptimal Bedtimes:\n%s".format(h, m, bedtimes.joinToString("\n"))

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("90-MINUTE REM BEDTIME PLANNER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("WAKE HH (0-23)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(wakeHour, { wakeHour = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("WAKE MM (0-59)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(wakeMin, { wakeMin = it }, allowDecimal = false, chapterHue = hue)
                            }
                        }
                        bedtimes.forEach { bt ->
                            PixelStamp(bt, palette.chapterColor(hue))
                        }
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("HOURS SLEPT", style = DotkitType.monoLabel(9), color = palette.ink3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelNumberField(sleptHours, { sleptHours = it }, chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelButton("Log Sleep", enabled = (sleptHours.toDoubleOrNull() ?: 0.0) > 0.0, chapterHue = hue, onClick = {
                                val hrs = sleptHours.toDoubleOrNull() ?: return@PixelButton
                                onAddHealthLog("SLEEP_MIN", hrs * 60.0, 0.0, "${hrs}h", "", false)
                                sleptHours = "0"
                            })
                        }
                        logs.take(10).forEach { s ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${"%.1f".format(s.primaryVal / 60.0)} hrs", style = DotkitType.monoData(13), color = palette.ink)
                                PixelChip("Delete", onClick = { onDeleteHealthLog(s.id) })
                            }
                        }
                    }
                }
            }

            "hlth-eye" -> {
                var timerSec by remember { mutableIntStateOf(20 * 60) }
                var running by remember { mutableStateOf(false) }
                var drillRunning by remember { mutableStateOf(false) }
                var drillPhase by remember { mutableLongStateOf(0L) }

                LaunchedEffect(running) {
                    while (running && timerSec > 0) {
                        delay(1000L)
                        timerSec--
                    }
                    if (timerSec == 0) { running = false; onStatusToast("Look 20 ft away for 20 seconds!") }
                }
                LaunchedEffect(drillRunning) {
                    while (drillRunning) {
                        delay(30L)
                        drillPhase += 30L
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("%02d:%02d".format(timerSec / 60, timerSec % 60), "20-20-20 EYE BREAK TIMER", hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(if (running) "Pause" else "Start 20m", chapterHue = hue, onClick = { running = !running }, modifier = Modifier.weight(1f))
                            PixelButton("Reset 20m", variant = PixelButtonVariant.OUTLINE, onClick = { running = false; timerSec = 20 * 60 }, modifier = Modifier.weight(1f))
                        }
                        val col = palette.chapterColor(hue)
                        Canvas(Modifier.fillMaxWidth().height(130.dp).border(1.dp, palette.hairline)) {
                            val t = (drillPhase / 1000f)
                            val cx = size.width / 2f + kotlin.math.sin(t * 2f) * (size.width * 0.35f)
                            val cy = size.height / 2f + kotlin.math.cos(t * 1.4f) * (size.height * 0.32f)
                            drawCircle(col, 18f, Offset(cx, cy))
                        }
                        PixelButton(if (drillRunning) "Stop Tracking Drill" else "Start Eye Tracking Drill", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { drillRunning = !drillRunning }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            else -> { // hlth-bp
                var sys by remember { mutableStateOf("0") }
                var dia by remember { mutableStateOf("0") }
                var note by remember { mutableStateOf("") }
                val logs = healthLogs.filter { it.logType == "BP" }
                outputSummary = logs.joinToString("\n") { "${it.primaryVal.toInt()}/${it.secondaryVal.toInt()} mmHg (${it.label})" }

                fun classifyBp(s: Int, d: Int): String = when {
                    s < 120 && d < 80 -> "Normal"
                    s in 120..129 && d < 80 -> "Elevated"
                    s in 130..139 || d in 80..89 -> "Stage 1 Hypertension"
                    else -> "Stage 2 Hypertension"
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SYSTOLIC (MMHG)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(sys, { sys = it }, allowDecimal = false, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("DIASTOLIC (MMHG)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(dia, { dia = it }, allowDecimal = false, chapterHue = hue)
                            }
                        }
                        PixelTextField(note, { note = it }, "Optional note (e.g. morning resting)")
                        PixelButton("Record Blood Pressure", chapterHue = hue, enabled = (sys.toIntOrNull() ?: 0) > 30 && (dia.toIntOrNull() ?: 0) > 20, onClick = {
                            val s = sys.toIntOrNull() ?: return@PixelButton
                            val d = dia.toIntOrNull() ?: return@PixelButton
                            onAddHealthLog("BP", s.toDouble(), d.toDouble(), classifyBp(s, d), note.trim(), false)
                            sys = "0"; dia = "0"; note = ""
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BLOOD PRESSURE LOG (${logs.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (logs.isEmpty()) Text("No readings logged yet.", style = DotkitType.bodyUi(13), color = palette.ink2)
                        val sdf = remember { SimpleDateFormat("MMM d HH:mm", Locale.US) }
                        logs.take(15).forEach { entry ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("${entry.primaryVal.toInt()}/${entry.secondaryVal.toInt()} mmHg · ${entry.label}", style = DotkitType.monoData(13), color = palette.ink)
                                    Text("${sdf.format(Date(entry.createdAt))} ${entry.note}".trim(), style = DotkitType.bodyUi(11), color = palette.ink2)
                                }
                                PixelChip("Delete", onClick = { onDeleteHealthLog(entry.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
