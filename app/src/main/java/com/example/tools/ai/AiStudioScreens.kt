package com.example.tools.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.gemini.AiStudioProvider
import com.example.services.gemini.AiToneMode
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStudioScreens(
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
    val hue = ChapterHue.SKY
    val scope = rememberCoroutineScope()
    val repo = remember { AiStudioProvider.repository }

    var outputText by remember { mutableStateOf("") }
    var metricsLabel by remember { mutableStateOf("") }

    // State for ai-writer
    var writerInput by remember(initialText) {
        mutableStateOf(
            initialText.ifBlank {
                "Omni Deck brings over two hundred fast developer, media, PDF, and productivity tools together in one clean offline-ready workspace."
            }
        )
    }
    var selectedTone by remember { mutableStateOf(AiToneMode.CONCISE) }

    // State for ai-prompt
    var promptTemplate by remember(initialText) {
        mutableStateOf(
            initialText.ifBlank {
                "You are a senior {{role}}.\nSummarize {{topic}} in 3 concise bullet points tailored for {{audience}}. Format cleanly."
            }
        )
    }
    val detectedVars = remember(promptTemplate) {
        Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}").findAll(promptTemplate).map { it.groupValues[1] }.distinct().toList()
    }
    val varValues = remember {
        mutableStateMapOf(
            "role" to "Android architect",
            "topic" to "Compose performance optimization",
            "audience" to "mobile engineers"
        )
    }

    // State for ai-code
    var codeMode by remember { mutableStateOf("CODE") }
    var codeSnippet by remember(initialText) {
        mutableStateOf(
            initialText.ifBlank {
                "suspend fun fetchTotals(items: List<Double>): Double = runCatching {\n    items.filter { it > 0.0 }.sum()\n}.getOrDefault(0.0)"
            }
        )
    }

    suspend fun runCurrentTool() {
        val res = when (meta.id) {
            "ai-writer" -> repo.rewriteText(writerInput, selectedTone)
            "ai-prompt" -> repo.evaluatePrompt(promptTemplate, varValues.toMap())
            else -> repo.explainCodeOrPattern(codeSnippet, codeMode)
        }
        outputText = res.outputText
        metricsLabel = res.metricsLabel
    }

    LaunchedEffect(meta.id, writerInput, selectedTone, promptTemplate, varValues.toMap(), codeSnippet, codeMode) {
        runCurrentTool()
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
        helpParagraph = "Runs structural analysis, token budgeting & variable interpolation locally. Live Gemini cloud generation requires enabling Firebase AI Logic.",
        onStatusToast = onStatusToast
    ) {
        ToolStateBanner(
            kind = ToolStateKind.OFFLINE,
            title = "Local AI Preview Mode",
            detail = "Firebase AI Logic was declined, so live Gemini generation is disabled. Deterministic analysis & sample previews run locally.",
            chapterHue = hue
        )

        when (meta.id) {
            "ai-writer" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("TONE & STYLE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AiToneMode.entries.forEach { mode ->
                                PixelChip(
                                    label = mode.label,
                                    selected = selectedTone == mode,
                                    chapterHue = hue,
                                    onClick = { selectedTone = mode }
                                )
                            }
                        }
                        Text("SOURCE TEXT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = writerInput,
                            onValueChange = { writerInput = it },
                            placeholder = "Paste article, email, or draft…",
                            singleLine = false,
                            minHeight = 110.dp,
                            chapterHue = hue,
                            tag = "ai_writer_input"
                        )
                        PixelButton(
                            text = "Generate Preview",
                            chapterHue = hue,
                            onClick = { scope.launch { runCurrentTool() } },
                            tag = "ai_writer_run"
                        )
                    }
                }
            }

            "ai-prompt" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PROMPT TEMPLATE ({{variable}})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = promptTemplate,
                            onValueChange = { promptTemplate = it },
                            placeholder = "Use {{var}} placeholders…",
                            singleLine = false,
                            minHeight = 100.dp,
                            chapterHue = hue,
                            tag = "ai_prompt_template"
                        )
                        if (detectedVars.isNotEmpty()) {
                            Text("DETECTED VARIABLES (${detectedVars.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                            detectedVars.forEach { key ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("{{$key}}", style = DotkitType.monoLabel(10), color = palette.chapterColor(hue))
                                    PixelTextField(
                                        value = varValues[key].orEmpty(),
                                        onValueChange = { varValues[key] = it },
                                        placeholder = "Value for $key…",
                                        chapterHue = hue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("EXPLAINER MODE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("CODE", "REGEX", "SQL").forEach { mode ->
                                PixelChip(
                                    label = mode,
                                    selected = codeMode == mode,
                                    chapterHue = hue,
                                    onClick = {
                                        codeMode = mode
                                        if (codeSnippet.isBlank() || codeMode != mode) {
                                            when (mode) {
                                                "REGEX" -> if (!codeSnippet.contains("\\")) codeSnippet = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
                                                "SQL" -> if (!codeSnippet.uppercase().contains("SELECT")) codeSnippet = "SELECT user_id, SUM(amount) FROM orders WHERE status = 'PAID' GROUP BY user_id ORDER BY SUM(amount) DESC;"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Text("SNIPPET", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = codeSnippet,
                            onValueChange = { codeSnippet = it },
                            placeholder = "Paste Kotlin, JS, Regex, or SQL…",
                            singleLine = false,
                            minHeight = 110.dp,
                            chapterHue = hue,
                            tag = "ai_code_input"
                        )
                    }
                }
            }
        }

        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OUTPUT PREVIEW", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelStamp(metricsLabel.ifBlank { "READY" }, palette.chapterColor(hue))
                }
                Text(
                    text = outputText,
                    style = DotkitType.monoData(12),
                    color = palette.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.paper)
                        .border(0.8.dp, palette.hairline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                )
            }
        }
    }
}
