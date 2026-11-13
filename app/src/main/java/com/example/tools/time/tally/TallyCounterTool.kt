package com.example.tools.time.tally

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.design.PixelConfettiBurst
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.lib.format.NumberFormatters
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.TallyCounterEntity
import com.example.tools.common.ToolWorkbenchChrome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TallyCounterTool(
    meta: ToolMeta,
    counters: List<TallyCounterEntity>,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onCreateCounter: (title: String, step: Int, goal: Int) -> Unit,
    onUpdateCounter: (TallyCounterEntity) -> Unit,
    onDeleteCounter: (Long) -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.SKY

    var selectedId by remember(counters.size) {
        mutableLongStateOf(counters.firstOrNull()?.id ?: 0L)
    }
    val activeCounter = counters.firstOrNull { it.id == selectedId } ?: counters.firstOrNull()

    var newTitle by remember { mutableStateOf("") }
    var showCreateForm by remember { mutableStateOf(false) }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var lastSnapshot by remember { mutableStateOf<TallyCounterEntity?>(null) }
    var undoMsg by remember { mutableStateOf<String?>(null) }

    val csvReport = remember(counters) {
        buildString {
            appendLine("id,title,count,step,goal,progress_pct,last_log")
            counters.forEach { c ->
                val pct = if (c.goal > 0) (c.count * 100 / c.goal) else 0
                val safeTitle = c.title.replace(",", " ")
                val safeLog = c.lapsLog.replace(",", ";").replace("\n", " | ")
                appendLine("${c.id},$safeTitle,${c.count},${c.step},${c.goal},$pct%,$safeLog")
            }
        }.trim()
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = csvReport,
        outputFileName = "tally_counters_${System.currentTimeMillis() / 1000}.csv",
        outputKind = DataKind.CSV,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        undoMessage = undoMsg,
        onUndo = {
            lastSnapshot?.let { snap ->
                onUpdateCounter(snap)
                undoMsg = null
                onStatusToast("Restored ${snap.title} to ${snap.count}.")
            }
        },
        onStatusToast = onStatusToast,
        helpParagraph = "Maintain multiple independent tallies with configurable step increments, target goals, and timestamped lap history. Counts persist across reloads in local SQLite storage and export cleanly as CSV."
    ) {
        // Counter Selector Ribbon + New Counter button
        PixelBox(cropMarks = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACTIVE COUNTERS (${counters.size})", style = DotkitType.monoLabel(11), color = palette.ink2)
                    PixelChip(
                        label = if (showCreateForm) "Close Form" else "+ New Counter",
                        selected = showCreateForm,
                        chapterHue = hue,
                        onClick = { showCreateForm = !showCreateForm }
                    )
                }

                if (counters.isEmpty()) {
                    ToolStateBanner(
                        kind = ToolStateKind.EMPTY,
                        title = "No counters in bench",
                        detail = "Add a counter below to start tracking reps, inventory, or laps.",
                        chapterHue = hue,
                        actionLabel = "Create Default Counter",
                        onAction = { onCreateCounter("Primary Tally", 1, 100) }
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        counters.forEach { item ->
                            PixelChip(
                                label = "${item.title} [${NumberFormatters.formatNumber(item.count.toLong(), look.regionFormat)}]",
                                selected = item.id == activeCounter?.id,
                                chapterHue = hue,
                                onClick = { selectedId = item.id }
                            )
                        }
                    }
                }

                if (showCreateForm) {
                    HorizontalDivider(thickness = 1.dp, color = palette.ink)
                    Text("NEW COUNTER TITLE", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = "e.g., Espresso Shots, Inventory Boxes, Laps…",
                        chapterHue = hue,
                        tag = "tally_new_title_input"
                    )
                    PixelButton(
                        text = "Add Counter to Bench",
                        chapterHue = hue,
                        onClick = {
                            val clean = newTitle.trim().ifEmpty { "Counter #${counters.size + 1}" }
                            onCreateCounter(clean, 1, 50)
                            newTitle = ""
                            showCreateForm = false
                            onStatusToast("Created counter '$clean'.")
                        },
                        tag = "tally_create_confirm_btn"
                    )
                }
            }
        }

        // Main Dot-Matrix Readout & Trigger Bench
        if (activeCounter != null) {
            val progress = if (activeCounter.goal > 0) {
                (activeCounter.count.toFloat() / activeCounter.goal.toFloat()).coerceIn(0f, 1f)
            } else 0f

            PixelBox(cropMarks = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SELECTED SPECIMEN COUNTER", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text(activeCounter.title, style = DotkitType.editorialHeading(22), color = palette.ink)
                        }
                        if (activeCounter.count >= activeCounter.goal && activeCounter.goal > 0) {
                            PixelStamp("GOAL REACHED", palette.ok)
                        } else {
                            PixelStamp("STEP ±${activeCounter.step}", palette.chapterColor(hue))
                        }
                    }

                    // Giant Doto Dot-Matrix Display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.paper3)
                            .border(2.dp, palette.ink)
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DotMatrixReadout(
                            valueText = "%05d".format(activeCounter.count),
                            sizeSp = 48,
                            accentColor = palette.ink
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("GROUPED (${look.regionFormat.code})", style = DotkitType.monoLabel(9), color = palette.ink2)
                            Text(
                                text = NumberFormatters.formatNumber(activeCounter.count.toLong(), look.regionFormat),
                                style = DotkitType.monoData(15),
                                color = palette.ink
                            )
                            Text(
                                text = "GOAL: ${NumberFormatters.formatNumber(activeCounter.goal.toLong(), look.regionFormat)}",
                                style = DotkitType.monoLabel(10),
                                color = palette.ink2
                            )
                        }
                    }

                    PixelConfettiBurst(triggerKey = confettiTrigger, chapterHue = hue)

                    // Primary Mechanical Trigger Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PixelButton(
                            text = "− ${activeCounter.step}",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val next = activeCounter.count - activeCounter.step
                                val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                val updatedLog = ("$stamp: −${activeCounter.step} → $next\n" + activeCounter.lapsLog).lines().take(8).joinToString("\n")
                                onUpdateCounter(activeCounter.copy(count = next, lapsLog = updatedLog, updatedAt = System.currentTimeMillis()))
                            },
                            tag = "tally_decrement_btn"
                        )
                        PixelButton(
                            text = "+ Increment (+${activeCounter.step})",
                            chapterHue = hue,
                            modifier = Modifier.weight(2f),
                            onClick = {
                                val next = activeCounter.count + activeCounter.step
                                if (activeCounter.count < activeCounter.goal && next >= activeCounter.goal) {
                                    confettiTrigger++
                                }
                                val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                val updatedLog = ("$stamp: +${activeCounter.step} → $next\n" + activeCounter.lapsLog).lines().take(8).joinToString("\n")
                                onUpdateCounter(activeCounter.copy(count = next, lapsLog = updatedLog, updatedAt = System.currentTimeMillis()))
                            },
                            tag = "tally_increment_btn"
                        )
                    }

                    // 8-Segment Goal Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("GOAL PROGRESS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text("${(progress * 100).toInt()}%", style = DotkitType.monoData(12), color = palette.ink)
                        }
                        PixelProgressBar(progress = progress, chapterHue = hue)
                    }

                    HorizontalDivider(thickness = 1.dp, color = palette.ink)

                    // Step Preset Chips & Goal Slider
                    Text("STEP INCREMENT PRESETS", style = DotkitType.monoLabel(10), color = palette.ink2)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 5, 10, 25, 50, 100).forEach { st ->
                            PixelChip(
                                label = "+$st",
                                selected = activeCounter.step == st,
                                chapterHue = hue,
                                onClick = {
                                    onUpdateCounter(activeCounter.copy(step = st))
                                }
                            )
                        }
                    }

                    PixelSteppedSlider(
                        label = "Target Goal Threshold",
                        value = activeCounter.goal,
                        valueRange = 10..500,
                        step = 10,
                        chapterHue = hue,
                        onValueChange = { newGoal ->
                            onUpdateCounter(activeCounter.copy(goal = newGoal))
                        }
                    )

                    // Reset & Delete with Undo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PixelButton(
                            text = "Reset to 0",
                            variant = PixelButtonVariant.DESTRUCTIVE,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                lastSnapshot = activeCounter
                                undoMsg = "Reset '${activeCounter.title}' from ${activeCounter.count} to 0."
                                onUpdateCounter(
                                    activeCounter.copy(
                                        count = 0,
                                        lapsLog = "Reset to 0\n" + activeCounter.lapsLog,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            },
                            tag = "tally_reset_btn"
                        )
                        if (counters.size > 1) {
                            PixelButton(
                                text = "Delete Counter",
                                variant = PixelButtonVariant.GHOST,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onDeleteCounter(activeCounter.id)
                                    onStatusToast("Removed '${activeCounter.title}'.")
                                }
                            )
                        }
                    }

                    // Recent Laps / Timestamp Log
                    if (activeCounter.lapsLog.isNotBlank()) {
                        HorizontalDivider(thickness = 1.dp, color = palette.ink)
                        Text("TIMESTAMPED TALLY LOG", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper3)
                                .border(1.dp, palette.ink)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            activeCounter.lapsLog.lines().filter { it.isNotBlank() }.forEach { line ->
                                Text(line, style = DotkitType.monoData(12), color = palette.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
