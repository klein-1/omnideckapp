package com.example.tools.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.dev.DevSuiteEngines
import com.example.lib.net.UrlInspectorEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevExtendedScreen(
    meta: ToolMeta,
    initialText: String,
    onLogNetworkAudit: (toolId: String, host: String, outB: Long, inB: Long, note: String) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.FOREST
    var outputText by remember(meta.id) { mutableStateOf("") }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}_output.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "dev-regex" -> {
                var cssTab by remember { mutableStateOf(false) }
                var pattern by remember { mutableStateOf("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b") }
                var sampleText by remember(initialText) { mutableStateOf(initialText) }
                var replaceWith by remember { mutableStateOf("[REDACTED]") }
                var ignoreCase by remember { mutableStateOf(true) }
                var multiline by remember { mutableStateOf(false) }

                // CSS generator state
                var radiusPx by remember { mutableIntStateOf(6) }
                var blurPx by remember { mutableIntStateOf(16) }
                var offsetY by remember { mutableIntStateOf(6) }
                var padPx by remember { mutableIntStateOf(16) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("Regex Tester", selected = !cssTab, chapterHue = hue, onClick = { cssTab = false })
                            PixelChip("CSS Builder", selected = cssTab, chapterHue = hue, onClick = { cssTab = true })
                        }
                        if (!cssTab) {
                            PixelTextField(pattern, { pattern = it }, "Regular expression")
                            PixelTextField(sampleText, { sampleText = it }, "Test input text", singleLine = false, minHeight = 90.dp)
                            PixelTextField(replaceWith, { replaceWith = it }, "Replacement text")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PixelSwitch("Ignore Case", ignoreCase, { ignoreCase = it }, chapterHue = hue, modifier = Modifier.weight(1f))
                                PixelSwitch("Multiline", multiline, { multiline = it }, chapterHue = hue, modifier = Modifier.weight(1f))
                            }
                            val res = remember(pattern, sampleText, replaceWith, ignoreCase, multiline) {
                                DevSuiteEngines.testRegex(pattern, sampleText, replaceWith, ignoreCase, multiline)
                            }
                            outputText = if (res.valid) "${res.matchCount} match(es)\n\nReplaced:\n${res.replacedText}" else res.error
                            if (!res.valid) PixelStamp(res.error, palette.err)
                            else {
                                PixelStamp("${res.matchCount} match(es)", palette.ok)
                                res.matches.take(15).forEach { m -> Text(m, style = DotkitType.monoData(11), color = palette.ink) }
                                if (sampleText.isNotBlank()) {
                                    Text("Replaced: ${res.replacedText}", style = DotkitType.monoData(12), color = palette.ink2)
                                }
                            }
                        } else {
                            PixelSteppedSlider("Border Radius", radiusPx, 0..32, step = 2, unitSuffix = "px", chapterHue = hue, onValueChange = { radiusPx = it })
                            PixelSteppedSlider("Shadow Blur", blurPx, 0..48, step = 4, unitSuffix = "px", chapterHue = hue, onValueChange = { blurPx = it })
                            PixelSteppedSlider("Offset Y", offsetY, 0..24, step = 2, unitSuffix = "px", chapterHue = hue, onValueChange = { offsetY = it })
                            PixelSteppedSlider("Padding", padPx, 4..40, step = 4, unitSuffix = "px", chapterHue = hue, onValueChange = { padPx = it })
                            val css = """
                                .swiss-card {
                                  display: flex;
                                  padding: ${padPx}px;
                                  border-radius: ${radiusPx}px;
                                  border: 1px solid #dcd8ce;
                                  box-shadow: 0 ${offsetY}px ${blurPx}px rgba(20,20,19,0.12);
                                }
                            """.trimIndent()
                            outputText = css
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .shadow((blurPx / 4).dp, RoundedCornerShape(radiusPx.dp))
                                    .clip(RoundedCornerShape(radiusPx.dp))
                                    .background(palette.paper)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(radiusPx.dp))
                                    .padding(padPx.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LIVE PREVIEW CARD", style = DotkitType.monoData(12), color = palette.ink)
                            }
                            Text(css, style = DotkitType.monoData(12), color = palette.ink)
                        }
                    }
                }
            }

            "dev-cron" -> {
                var cronExpr by remember { mutableStateOf("*/15 * * * *") }
                val presets = listOf("Every 15m" to "*/15 * * * *", "Hourly" to "0 * * * *", "Daily 9AM" to "0 9 * * *", "Weekdays 8AM" to "0 8 * * 1-5", "Weekly Sun" to "0 0 * * 0")
                val (summary, runs) = remember(cronExpr) { DevSuiteEngines.describeAndPreviewCron(cronExpr) }
                outputText = "$cronExpr\n$summary\nNext runs:\n${runs.joinToString("\n")}"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(cronExpr, { cronExpr = it }, "min hour dom month dow")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            presets.forEach { (label, expr) ->
                                PixelChip(label, selected = cronExpr == expr, chapterHue = hue, onClick = { cronExpr = expr })
                            }
                        }
                        Text(summary, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                        Text("NEXT 5 EXECUTIONS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        runs.forEachIndexed { idx, ts ->
                            Text("${idx + 1}. $ts", style = DotkitType.monoData(12), color = palette.ink)
                        }
                    }
                }
            }

            "dev-dns" -> {
                var domain by remember { mutableStateOf("example.com") }
                var recType by remember { mutableStateOf("A") }
                var loading by remember { mutableStateOf(false) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(domain, { domain = it }, "Domain (e.g. github.com)")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A", "AAAA", "MX", "TXT", "NS", "CNAME").forEach { t ->
                                PixelChip(t, selected = recType == t, chapterHue = hue, onClick = { recType = t })
                            }
                        }
                        PixelButton(if (loading) "Querying DoH..." else "Lookup $recType Record", enabled = !loading && domain.isNotBlank(), chapterHue = hue, onClick = {
                            loading = true
                            scope.launch {
                                val res = UrlInspectorEngine.queryDoh(domain = domain, recordType = recType)
                                outputText = res
                                onLogNetworkAudit(meta.id, "dns.google", 128L, res.length.toLong(), "$recType ${domain.trim()}")
                                loading = false
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        if (outputText.isNotBlank()) Text(outputText, style = DotkitType.monoData(12), color = palette.ink)
                    }
                }
            }

            else -> { // dev-sandbox
                var script by remember(initialText) {
                    mutableStateOf(
                        if (initialText.isNotBlank()) initialText
                        else "radius = 12\narea = pi * radius^2\ncircumference = 2 * pi * radius\nsin(30) + cos(60)"
                    )
                }
                val lines = remember(script) { DevSuiteEngines.evaluateScriptSandbox(script) }
                outputText = lines.joinToString("\n")

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(script, { script = it }, "Enter assignments & math expressions...", singleLine = false, minHeight = 140.dp)
                        Text("EVALUATION TRACE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        lines.forEach { l ->
                            Text(l, style = DotkitType.monoData(12), color = if ("Error:" in l) palette.err else palette.ink)
                        }
                    }
                }
            }
        }
    }
}
