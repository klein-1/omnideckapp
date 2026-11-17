package com.example.tools.calc

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.lib.expansions.AspectRatioEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.ExpenseEntity
import com.example.tools.common.ToolWorkbenchChrome
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatioAndSubsScreens(
    meta: ToolMeta,
    expenses: List<ExpenseEntity>,
    onAddExpense: (title: String, amount: Double, category: String, kind: String, note: String) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val sym = look.regionFormat.currencySymbol

    // ==================== 1. ASPECT RATIO, RULE-OF-3 & DP/REM SCALER ====================
    var widthStr by remember { mutableStateOf("0") }
    var heightStr by remember { mutableStateOf("0") }
    var targetWidthStr by remember { mutableStateOf("0") }
    var r3A by remember { mutableStateOf("0") }
    var r3B by remember { mutableStateOf("0") }
    var r3C by remember { mutableStateOf("0") }
    var dpInputStr by remember { mutableStateOf("0") }

    val wInt = widthStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val hInt = heightStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val targetW = targetWidthStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val ratioSummary = remember(wInt, hInt) {
        if (wInt > 0 && hInt > 0) AspectRatioEngine.analyze(wInt, hInt) else null
    }
    val scaledH = if (ratioSummary != null && targetW > 0) AspectRatioEngine.scaleHeightForRatio(wInt, hInt, targetW) else 0
    val r3Result = AspectRatioEngine.ruleOfThree(
        r3A.toDoubleOrNull() ?: 0.0,
        r3B.toDoubleOrNull() ?: 0.0,
        r3C.toDoubleOrNull() ?: 0.0
    )
    val dpVal = dpInputStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val densityRows = remember(dpVal) { AspectRatioEngine.densityMatrix(dpVal, dpVal) }

    // ==================== 2. SUBSCRIPTION BURN & RENEWAL TRACKER ====================
    var subName by remember { mutableStateOf("") }
    var subCostStr by remember { mutableStateOf("0") }
    var subCycle by remember { mutableStateOf("MONTHLY") } // MONTHLY, YEARLY, WEEKLY
    var subDayStr by remember { mutableStateOf("0") }

    val activeSubs = remember(expenses) {
        expenses.filter { it.kind == "SUBSCRIPTION" }
    }
    val monthlyTotal = activeSubs.sumOf { item ->
        val cycle = item.note.substringBefore("|", "MONTHLY")
        when (cycle) {
            "YEARLY" -> item.amount / 12.0
            "WEEKLY" -> item.amount * 52.0 / 12.0
            else -> item.amount
        }
    }
    val yearlyTotal = monthlyTotal * 12.0

    val hue = if (meta.id == "calc-ratio") ChapterHue.COBALT else ChapterHue.LIME
    val outputText = if (meta.id == "calc-ratio") {
        buildString {
            if (ratioSummary != null) {
                appendLine("Aspect Ratio: ${ratioSummary.ratioLabel} (${String.format(Locale.US, "%.4f", ratioSummary.decimalRatio)})")
                if (targetW > 0) appendLine("Scaled Size: ${targetW} × $scaledH px")
            } else {
                appendLine("Aspect Ratio: Enter Width & Height")
            }
            if (r3Result != null) appendLine("Rule of 3 X = ${String.format(Locale.US, "%.4f", r3Result)}")
            appendLine("DP $dpVal -> ${String.format(Locale.US, "%.3f", dpVal / 16.0)} rem")
        }.trim()
    } else {
        buildString {
            appendLine("Monthly Burn: $sym${String.format(Locale.US, "%.2f", monthlyTotal)}")
            appendLine("Yearly Burn: $sym${String.format(Locale.US, "%.2f", yearlyTotal)}")
            activeSubs.forEach { s ->
                appendLine("• ${s.title}: $sym${String.format(Locale.US, "%.2f", s.amount)} (${s.note})")
            }
        }.trim()
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}_${System.currentTimeMillis()}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = if (meta.id == "calc-ratio") {
            "Compute simplified aspect ratios, proportional scaling, rule-of-three cross-multiplication, and Android DP to PX/REM density matrices."
        } else {
            "Track recurring Monthly, Yearly, and Weekly subscriptions in local Room SQLite with normalized monthly/annual burn."
        },
        onStatusToast = onStatusToast
    ) {
        if (meta.id == "calc-ratio") {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ASPECT RATIO & SCALER", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WIDTH", style = DotkitType.monoLabel(9), color = palette.ink2)
                            PixelNumberField(value = widthStr, onValueChange = { widthStr = it }, allowDecimal = false, chapterHue = hue)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HEIGHT", style = DotkitType.monoLabel(9), color = palette.ink2)
                            PixelNumberField(value = heightStr, onValueChange = { heightStr = it }, allowDecimal = false, chapterHue = hue)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1920×1080" to (1920 to 1080), "1080×1920" to (1080 to 1920), "1440×1440" to (1440 to 1440), "2560×1600" to (2560 to 1600)).forEach { (lbl, wh) ->
                            PixelChip(label = lbl, chapterHue = hue, onClick = {
                                widthStr = wh.first.toString()
                                heightStr = wh.second.toString()
                            })
                        }
                    }
                    Text("TARGET WIDTH FOR PROPORTIONAL HEIGHT", style = DotkitType.monoLabel(9), color = palette.ink2)
                    PixelNumberField(value = targetWidthStr, onValueChange = { targetWidthStr = it }, allowDecimal = false, chapterHue = hue)
                    if (ratioSummary != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ratioSummary.ratioLabel, style = DotkitType.readoutDoto(32), color = palette.chapterColor(hue))
                            if (targetW > 0) {
                                PixelStamp("SCALED: ${targetW}×$scaledH", palette.ok)
                            }
                        }
                    }
                }
            }

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RULE OF 3 (A : B = C : X)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelNumberField(value = r3A, onValueChange = { r3A = it }, modifier = Modifier.weight(1f), chapterHue = hue)
                        PixelNumberField(value = r3B, onValueChange = { r3B = it }, modifier = Modifier.weight(1f), chapterHue = hue)
                        PixelNumberField(value = r3C, onValueChange = { r3C = it }, modifier = Modifier.weight(1f), chapterHue = hue)
                    }
                    Text(
                        text = if (r3Result != null) "X = ${String.format(Locale.US, "%.4f", r3Result)}" else "Enter non-zero A, B, C to solve X",
                        style = DotkitType.monoData(14),
                        color = if (r3Result != null) palette.ok else palette.ink2
                    )
                }
            }

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ANDROID DP → PX / WEB REM MATRIX", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelNumberField(value = dpInputStr, onValueChange = { dpInputStr = it }, allowDecimal = false, chapterHue = hue)
                    Text("Web 1rem = 16px → ${String.format(Locale.US, "%.3f", dpVal / 16.0)} rem", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                    densityRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.paper).padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(row.bucket, style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text("${row.pxWidth} px", style = DotkitType.monoData(12), color = palette.ink)
                        }
                    }
                }
            }
        } else {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("MONTHLY BURN", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text("$sym${String.format(Locale.US, "%.2f", monthlyTotal)}", style = DotkitType.readoutDoto(30), color = palette.chapterColor(hue))
                        }
                        PixelStamp("YEARLY $sym${String.format(Locale.US, "%.0f", yearlyTotal)}", palette.warn)
                    }
                    PixelTextField(value = subName, onValueChange = { subName = it }, placeholder = "Service name (e.g. Cloud Storage)…", chapterHue = hue)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AMOUNT ($sym)", style = DotkitType.monoLabel(9), color = palette.ink2)
                            PixelNumberField(value = subCostStr, onValueChange = { subCostStr = it }, chapterHue = hue)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RENEW DAY (1-31)", style = DotkitType.monoLabel(9), color = palette.ink2)
                            PixelNumberField(value = subDayStr, onValueChange = { subDayStr = it }, allowDecimal = false, chapterHue = hue)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MONTHLY", "YEARLY", "WEEKLY").forEach { c ->
                            PixelChip(label = c, selected = subCycle == c, chapterHue = hue, onClick = { subCycle = c })
                        }
                    }
                    PixelButton(
                        text = "Add Subscription",
                        chapterHue = hue,
                        onClick = {
                            val amt = subCostStr.toDoubleOrNull() ?: 0.0
                            if (subName.isBlank() || amt <= 0.0) {
                                onStatusToast("Enter a service name and amount > 0.")
                            } else {
                                val day = (subDayStr.toIntOrNull() ?: 1).coerceIn(1, 31)
                                onAddExpense(subName.trim(), amt, "SUBSCRIPTION", "SUBSCRIPTION", "$subCycle|Day $day")
                                subName = ""
                                subCostStr = "0"
                                subDayStr = "0"
                                onStatusToast("Subscription added.")
                            }
                        },
                        tag = "subs_add_btn"
                    )
                }
            }
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ACTIVE SUBSCRIPTIONS (${activeSubs.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    if (activeSubs.isEmpty()) {
                        Text("No recurring subscriptions logged yet.", style = DotkitType.bodyUi(13), color = palette.ink2)
                    } else {
                        activeSubs.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.paper)
                                    .border(0.8.dp, palette.hairline, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                                    Text(item.note.replace("|", " · "), style = DotkitType.monoLabel(10), color = palette.ink2)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("$sym${String.format(Locale.US, "%.2f", item.amount)}", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                                    Text(
                                        text = "✕",
                                        style = DotkitType.monoLabel(12),
                                        color = palette.err,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onDeleteExpense(item.id) }
                                            .padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
