package com.example.tools.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.lib.color.ColorContrastEngine
import com.example.lib.color.RgbColor
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorContrastTool(
    meta: ToolMeta,
    initialHex: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.MARIGOLD

    val initialParsed = remember(initialHex) { ColorContrastEngine.parseHex(initialHex) }
    var fgR by remember(initialHex) { mutableIntStateOf(initialParsed?.r ?: 23) }
    var fgG by remember(initialHex) { mutableIntStateOf(initialParsed?.g ?: 21) }
    var fgB by remember(initialHex) { mutableIntStateOf(initialParsed?.b ?: 15) }
    var fgHexField by remember(initialHex) { mutableStateOf(initialParsed?.hex ?: "#17150F") }

    var bgR by remember { mutableIntStateOf(246) }
    var bgG by remember { mutableIntStateOf(243) }
    var bgB by remember { mutableIntStateOf(236) }
    var bgHexField by remember { mutableStateOf("#F6F3EC") }

    var editingForeground by remember { mutableStateOf(true) }

    val fgRgb = RgbColor(fgR, fgG, fgB)
    val bgRgb = RgbColor(bgR, bgG, bgB)
    val fgCompose = Color(fgR, fgG, fgB)
    val bgCompose = Color(bgR, bgG, bgB)

    val eval = remember(fgRgb, bgRgb) { ColorContrastEngine.evaluateContrast(fgRgb, bgRgb) }
    val activeRgb = if (editingForeground) fgRgb else bgRgb
    val activeHsl = remember(activeRgb) { ColorContrastEngine.rgbToHsl(activeRgb) }
    val shades = remember(activeRgb) { ColorContrastEngine.generateShades(activeRgb) }

    val exportReport = remember(fgRgb, bgRgb, eval, activeHsl) {
        buildString {
            appendLine("Foreground: ${fgRgb.hex} · rgb(${fgRgb.r}, ${fgRgb.g}, ${fgRgb.b})")
            appendLine("Background: ${bgRgb.hex} · rgb(${bgRgb.r}, ${bgRgb.g}, ${bgRgb.b})")
            appendLine("Contrast Ratio: ${String.format(Locale.US, "%.2f", eval.ratio)}:1")
            appendLine("WCAG AA Normal (>=4.5): ${if (eval.passAaNormal) "PASS" else "FAIL"}")
            appendLine("WCAG AAA Normal (>=7.0): ${if (eval.passAaaNormal) "PASS" else "FAIL"}")
            append("Active HSL: hsl(${activeHsl.first}, ${activeHsl.second}%, ${activeHsl.third}%)")
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportReport,
        outputFileName = "color_contrast_${fgRgb.hex.removePrefix("#")}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Mix foreground and background colors in HEX, RGB, or HSL, check real-time WCAG 2.1 AA/AAA contrast compliance, and copy generated tint/shade swatches."
    ) {
        // Live Contrast Preview Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgCompose)
                .border(2.dp, palette.ink)
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CONTRAST PREVIEW", style = DotkitType.monoLabel(10), color = fgCompose)
                    Text(
                        text = "${String.format(Locale.US, "%.2f", eval.ratio)} : 1",
                        style = DotkitType.readoutDoto(28),
                        color = fgCompose
                    )
                }
                Text(
                    text = "Quick brown fox jumps over the lazy dog.",
                    style = DotkitType.editorialHeading(18),
                    color = fgCompose
                )
                Text(
                    text = "Body copy 14sp — ${fgRgb.hex} on ${bgRgb.hex}",
                    style = DotkitType.bodyUi(14),
                    color = fgCompose
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelStamp("AA Normal ${if (eval.passAaNormal) "PASS" else "FAIL"}", if (eval.passAaNormal) palette.ok else palette.err)
                    PixelStamp("AAA ${if (eval.passAaaNormal) "PASS" else "FAIL"}", if (eval.passAaaNormal) palette.ok else palette.warn)
                    PixelStamp("AA Large ${if (eval.passAaLarge) "PASS" else "FAIL"}", if (eval.passAaLarge) palette.ok else palette.err)
                }
            }
        }

        // Target selector + Swap + Hex inputs
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelChip(
                            label = "Text: ${fgRgb.hex}",
                            selected = editingForeground,
                            chapterHue = hue,
                            onClick = { editingForeground = true }
                        )
                        PixelChip(
                            label = "BG: ${bgRgb.hex}",
                            selected = !editingForeground,
                            chapterHue = hue,
                            onClick = { editingForeground = false }
                        )
                    }
                    PixelChip(
                        label = "⇄ Swap",
                        chapterHue = hue,
                        onClick = {
                            val tr = fgR; val tg = fgG; val tb = fgB
                            fgR = bgR; fgG = bgG; fgB = bgB
                            bgR = tr; bgG = tg; bgB = tb
                            fgHexField = RgbColor(fgR, fgG, fgB).hex
                            bgHexField = RgbColor(bgR, bgG, bgB).hex
                        }
                    )
                }

                Text(
                    text = if (editingForeground) "FOREGROUND HEX" else "BACKGROUND HEX",
                    style = DotkitType.monoLabel(10),
                    color = palette.ink2
                )
                PixelTextField(
                    value = if (editingForeground) fgHexField else bgHexField,
                    onValueChange = { raw ->
                        if (editingForeground) {
                            fgHexField = raw
                            ColorContrastEngine.parseHex(raw)?.let { c ->
                                fgR = c.r; fgG = c.g; fgB = c.b
                            }
                        } else {
                            bgHexField = raw
                            ColorContrastEngine.parseHex(raw)?.let { c ->
                                bgR = c.r; bgG = c.g; bgB = c.b
                            }
                        }
                    },
                    placeholder = "#17150F",
                    chapterHue = hue
                )

                PixelSteppedSlider(
                    label = "Red (R)",
                    value = activeRgb.r,
                    valueRange = 0..255,
                    step = 5,
                    chapterHue = ChapterHue.VERMILION,
                    onValueChange = { v ->
                        if (editingForeground) {
                            fgR = v
                            fgHexField = RgbColor(v, fgG, fgB).hex
                        } else {
                            bgR = v
                            bgHexField = RgbColor(v, bgG, bgB).hex
                        }
                    }
                )
                PixelSteppedSlider(
                    label = "Green (G)",
                    value = activeRgb.g,
                    valueRange = 0..255,
                    step = 5,
                    chapterHue = ChapterHue.FOREST,
                    onValueChange = { v ->
                        if (editingForeground) {
                            fgG = v
                            fgHexField = RgbColor(fgR, v, fgB).hex
                        } else {
                            bgG = v
                            bgHexField = RgbColor(bgR, v, bgB).hex
                        }
                    }
                )
                PixelSteppedSlider(
                    label = "Blue (B)",
                    value = activeRgb.b,
                    valueRange = 0..255,
                    step = 5,
                    chapterHue = ChapterHue.COBALT,
                    onValueChange = { v ->
                        if (editingForeground) {
                            fgB = v
                            fgHexField = RgbColor(fgR, fgG, v).hex
                        } else {
                            bgB = v
                            bgHexField = RgbColor(bgR, bgG, v).hex
                        }
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                Text(
                    text = "RGB(${activeRgb.r}, ${activeRgb.g}, ${activeRgb.b}) · HSL(${activeHsl.first}°, ${activeHsl.second}%, ${activeHsl.third}%)",
                    style = DotkitType.monoData(12),
                    color = palette.ink
                )

                Text("5-STEP TINT & SHADE PALETTE (TAP TO COPY HEX)", style = DotkitType.monoLabel(10), color = palette.ink2)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shades.forEach { shade ->
                        val shadeLum = ColorContrastEngine.relativeLuminance(shade)
                        val labelColor = if (shadeLum > 0.35) Color(0xFF17150F) else Color(0xFFF6F3EC)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .background(Color(shade.r, shade.g, shade.b))
                                .border(1.5.dp, palette.ink)
                                .clickable {
                                    copyToClipboard(context, "Hex", shade.hex)
                                    onStatusToast("Copied ${shade.hex}.")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(shade.hex, style = DotkitType.monoLabel(9), color = labelColor)
                        }
                    }
                }
            }
        }
    }
}
