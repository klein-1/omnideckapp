package com.example.tools.words.caseconv

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.lib.format.NumberFormatters
import com.example.lib.text.CaseConverterEngine
import com.example.lib.text.CaseStyle
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

private const val DEFAULT_EDITORIAL_SAMPLE =
    "the art of swiss typographic systems in the age of digital tools: a field note on pixel grids & modular scale"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseConverterTool(
    meta: ToolMeta,
    initialText: String,
    rememberedStyleName: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onRememberStyle: (String) -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.VERMILION

    var inputText by remember(initialText) {
        mutableStateOf(initialText.ifEmpty { DEFAULT_EDITORIAL_SAMPLE })
    }
    var selectedStyle by remember(rememberedStyleName) {
        mutableStateOf(
            runCatching { CaseStyle.valueOf(rememberedStyleName) }.getOrDefault(CaseStyle.TITLE_AP)
        )
    }
    var undoBuffer by remember { mutableStateOf<String?>(null) }
    var undoMsg by remember { mutableStateOf<String?>(null) }

    val convertedOutput = remember(inputText, selectedStyle) {
        CaseConverterEngine.convert(inputText, selectedStyle)
    }
    val metrics = remember(convertedOutput) {
        CaseConverterEngine.analyze(convertedOutput)
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = convertedOutput,
        outputFileName = "case_${selectedStyle.id}_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        undoMessage = undoMsg,
        onUndo = {
            undoBuffer?.let { prev ->
                inputText = prev
                undoBuffer = null
                undoMsg = null
                onStatusToast("Restored previous text.")
            }
        },
        onStatusToast = onStatusToast,
        helpParagraph = "Convert prose, headlines, and identifiers across 15 standard, editorial (AP, Chicago, APA), and developer casing conventions. Minor-word rules respect first/last word capitalisation and colon boundaries."
    ) {
        // Source Text Input Bench
        PixelBox(cropMarks = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SOURCE INPUT TEXT", style = DotkitType.monoLabel(11), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelChip(
                            label = "Sample",
                            chapterHue = hue,
                            onClick = { inputText = DEFAULT_EDITORIAL_SAMPLE }
                        )
                        PixelChip(
                            label = "Clear",
                            chapterHue = hue,
                            onClick = {
                                if (inputText.isNotEmpty()) {
                                    undoBuffer = inputText
                                    undoMsg = "Cleared ${inputText.length} characters."
                                    inputText = ""
                                }
                            }
                        )
                    }
                }

                PixelTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = "Enter or paste text to transform…",
                    singleLine = false,
                    minHeight = 96.dp,
                    chapterHue = hue,
                    tag = "case_input_field"
                )

                if (inputText.isEmpty()) {
                    ToolStateBanner(
                        kind = ToolStateKind.EMPTY,
                        title = "Input buffer is empty",
                        detail = "Type above or tap Load Sample to inspect all 15 case transformations.",
                        chapterHue = hue,
                        actionLabel = "Load Sample",
                        onAction = { inputText = DEFAULT_EDITORIAL_SAMPLE }
                    )
                }
            }
        }

        // Case Selector Matrix grouped by Category
        PixelBox(cropMarks = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("15 CASE ALGORITHMS", style = DotkitType.monoLabel(11), color = palette.ink2)
                    PixelStamp(selectedStyle.label, palette.chapterColor(hue))
                }

                val grouped = remember { CaseStyle.entries.groupBy { it.category } }
                grouped.forEach { (category, styles) ->
                    Text(category.uppercase(), style = DotkitType.monoLabel(10), color = palette.ink3)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        styles.forEach { style ->
                            PixelChip(
                                label = style.label,
                                selected = selectedStyle == style,
                                chapterHue = hue,
                                onClick = {
                                    selectedStyle = style
                                    onRememberStyle(style.name)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }

        // Live Output Plate & Tabular Metrics
        PixelBox(cropMarks = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRANSFORMED SPECIMEN OUTPUT", style = DotkitType.monoLabel(11), color = palette.ink2)
                    PixelButton(
                        text = "Replace Input with Output",
                        variant = PixelButtonVariant.GHOST,
                        chapterHue = hue,
                        enabled = convertedOutput.isNotEmpty() && convertedOutput != inputText,
                        onClick = {
                            undoBuffer = inputText
                            undoMsg = "Applied ${selectedStyle.label} in-place."
                            inputText = convertedOutput
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.paper3)
                        .border(2.dp, palette.ink)
                        .padding(12.dp)
                ) {
                    Text(
                        text = convertedOutput.ifEmpty { "—" },
                        style = DotkitType.monoData(14),
                        color = palette.ink
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = palette.ink)

                // Tabular Metrics Grid
                Text("LIVE TEXT METRICS (${look.regionFormat.code})", style = DotkitType.monoLabel(10), color = palette.ink2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCell("CHARS", NumberFormatters.formatNumber(metrics.chars.toLong(), look.regionFormat), Modifier.weight(1f))
                    MetricCell("WORDS", NumberFormatters.formatNumber(metrics.words.toLong(), look.regionFormat), Modifier.weight(1f))
                    MetricCell("LINES", NumberFormatters.formatNumber(metrics.lines.toLong(), look.regionFormat), Modifier.weight(1f))
                    MetricCell("UTF-8", "${metrics.bytesUtf8} B", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalDotkitLook.current.palette
    Column(
        modifier = modifier
            .background(palette.paper3)
            .border(1.dp, palette.ink)
            .padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(label, style = DotkitType.monoLabel(9), color = palette.ink2)
        Text(value, style = DotkitType.monoData(13), color = palette.ink)
    }
}
