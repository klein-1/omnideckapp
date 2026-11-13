package com.example.tools.words.diff

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.calc.ExtendedMathEngines
import com.example.lib.text.CaseConverterEngine
import com.example.lib.text.TextToolsEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

private enum class TextLabTab(val label: String) {
    ANALYZE("Counter & Stats"),
    DIFF("Line Diff"),
    CLEAN("Replace & Sort")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextDiffAnalyzerTool(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.VERMILION

    var tab by remember { mutableStateOf(TextLabTab.ANALYZE) }
    var textA by remember(initialText) {
        mutableStateOf(
            initialText.ifEmpty {
                "Dotkit processes every tool locally on your device.\nZero tracking, zero ads, instant response.\nBuilt for phones and tablets."
            }
        )
    }
    var textB by remember {
        mutableStateOf(
            "Dotkit processes every tool locally on your device.\nZero tracking, instant offline response.\nBuilt for Android phones and tablets."
        )
    }
    var ignoreCase by remember { mutableStateOf(false) }

    var findPattern by remember { mutableStateOf("zero") }
    var replaceWith by remember { mutableStateOf("0") }
    var useRegex by remember { mutableStateOf(false) }

    val metrics = remember(textA) { CaseConverterEngine.analyze(textA) }
    val readability = remember(textA) { ExtendedMathEngines.analyzeReadability(textA) }
    val topWords = remember(textA) { TextToolsEngine.topWords(textA, 6) }
    val diffLines = remember(textA, textB, ignoreCase) {
        TextToolsEngine.diffLines(textA, textB, ignoreCase)
    }

    val exportText = when (tab) {
        TextLabTab.ANALYZE -> textA
        TextLabTab.DIFF -> diffLines.joinToString("\n") { "${it.kind} ${it.text}" }
        TextLabTab.CLEAN -> textA
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportText,
        outputFileName = "text_lab_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Analyze word/character counts, reading time, and top keywords; compare two texts line-by-line with an LCS diff; or run regex find-and-replace, line sorting, and duplicate line removal."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextLabTab.entries.forEach { t ->
                PixelChip(label = t.label, selected = tab == t, chapterHue = hue, onClick = { tab = t })
            }
        }

        when (tab) {
            TextLabTab.ANALYZE -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("INPUT TEXT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = textA,
                            onValueChange = { textA = it },
                            placeholder = "Paste or type text to inspect…",
                            singleLine = false,
                            minHeight = 110.dp,
                            chapterHue = hue
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("WORDS", metrics.words.toString(), Modifier.weight(1f))
                            StatBox("CHARS", metrics.chars.toString(), Modifier.weight(1f))
                            StatBox("NO SPACE", metrics.charsNoSpaces.toString(), Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("SENTENCES", metrics.sentences.toString(), Modifier.weight(1f))
                            StatBox("LINES", metrics.lines.toString(), Modifier.weight(1f))
                            StatBox("READ TIME", "${metrics.readingSeconds}s", Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("FLESCH EASE", "%.1f".format(readability.fleschReadingEase), Modifier.weight(1f))
                            StatBox("GUNNING FOG", "%.1f".format(readability.gunningFogIndex), Modifier.weight(1f))
                            StatBox("SYLLABLES", readability.syllables.toString(), Modifier.weight(1f))
                        }
                        if (readability.invisibleCharsFound.isNotEmpty()) {
                            HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("INVISIBLE UNICODE DETECTED (${readability.invisibleCharsFound.size})", style = DotkitType.monoLabel(10), color = palette.err)
                                PixelChip("Strip Hidden", chapterHue = ChapterHue.ROSE, onClick = {
                                    textA = textA.replace(Regex("[\u200B-\u200F\uFEFF\u00A0]"), "")
                                    onStatusToast("Removed invisible Unicode characters.")
                                })
                            }
                            Text(readability.invisibleCharsFound.take(8).joinToString(" · "), style = DotkitType.monoData(10), color = palette.err)
                        }
                        if (topWords.isNotEmpty()) {
                            HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                            Text("TOP KEYWORDS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                topWords.forEach { (w, cnt) ->
                                    PixelChip(label = "$w ($cnt)", chapterHue = hue)
                                }
                            }
                        }
                        if (readability.topBigrams.isNotEmpty() || readability.topTrigrams.isNotEmpty()) {
                            HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                            Text("N-GRAMS (BIGRAMS / TRIGRAMS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                readability.topBigrams.forEach { (g, c) -> PixelChip(label = "$g ×$c", chapterHue = hue) }
                                readability.topTrigrams.forEach { (g, c) -> PixelChip(label = "$g ×$c", chapterHue = ChapterHue.COBALT) }
                            }
                        }
                    }
                }
            }

            TextLabTab.DIFF -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ORIGINAL TEXT (A)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = textA,
                            onValueChange = { textA = it },
                            placeholder = "Original lines…",
                            singleLine = false,
                            minHeight = 80.dp,
                            chapterHue = hue
                        )
                        Text("MODIFIED TEXT (B)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = textB,
                            onValueChange = { textB = it },
                            placeholder = "Modified lines…",
                            singleLine = false,
                            minHeight = 80.dp,
                            chapterHue = hue
                        )
                        PixelSwitch("Ignore Case", ignoreCase, { ignoreCase = it }, hue)
                        HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                        Text("LINE DIFF OUTPUT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper)
                                .border(1.5.dp, palette.ink)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            diffLines.forEach { dl ->
                                val bg = when (dl.kind) {
                                    '+' -> palette.ok.copy(alpha = 0.16f)
                                    '-' -> palette.err.copy(alpha = 0.16f)
                                    else -> palette.paper
                                }
                                val fg = when (dl.kind) {
                                    '+' -> palette.ok
                                    '-' -> palette.err
                                    else -> palette.ink2
                                }
                                Text(
                                    text = "${dl.kind} ${dl.text}",
                                    style = DotkitType.monoData(12),
                                    color = fg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            TextLabTab.CLEAN -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelTextField(
                            value = textA,
                            onValueChange = { textA = it },
                            placeholder = "Enter lines to transform…",
                            singleLine = false,
                            minHeight = 96.dp,
                            chapterHue = hue
                        )
                        Text("FIND", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(value = findPattern, onValueChange = { findPattern = it }, placeholder = "Find text or regex…", chapterHue = hue)
                        Text("REPLACE WITH", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(value = replaceWith, onValueChange = { replaceWith = it }, placeholder = "Replacement…", chapterHue = hue)
                        PixelSwitch("Use Regular Expression (Regex)", useRegex, { useRegex = it }, hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = "Replace All",
                                chapterHue = hue,
                                onClick = {
                                    if (findPattern.isEmpty()) return@PixelButton
                                    runCatching {
                                        textA = if (useRegex) {
                                            textA.replace(Regex(findPattern), replaceWith)
                                        } else {
                                            textA.replace(findPattern, replaceWith, ignoreCase = ignoreCase)
                                        }
                                        onStatusToast("Replaced matches.")
                                    }.onFailure {
                                        onStatusToast("Invalid regex: ${it.message}")
                                    }
                                }
                            )
                            PixelButton(
                                text = "Sort Lines A–Z",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = { textA = textA.lines().sorted().joinToString("\n") }
                            )
                            PixelButton(
                                text = "Dedupe Lines",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = { textA = textA.lines().distinct().joinToString("\n") }
                            )
                            PixelButton(
                                text = "Trim Blanks",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = { textA = textA.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalDotkitLook.current.palette
    Column(
        modifier = modifier
            .background(palette.paper)
            .border(1.dp, palette.ink3)
            .padding(8.dp)
    ) {
        Text(label, style = DotkitType.monoLabel(9), color = palette.ink2)
        Text(value, style = DotkitType.monoData(15), color = palette.ink)
    }
}
