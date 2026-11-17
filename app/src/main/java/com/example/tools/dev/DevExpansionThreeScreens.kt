package com.example.tools.dev

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.MotionMode
import com.example.design.PixelBox
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.lib.expansions.GradientBezierEngine
import com.example.lib.expansions.GradientKind
import com.example.lib.expansions.GradientStopSpec
import com.example.lib.expansions.JsonDiffKind
import com.example.lib.expansions.JsonDiffSchemaEngine
import com.example.lib.expansions.SvgVectorEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevExpansionThreeScreens(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.FOREST

    // ==================== 1. JSON DIFF / SCHEMA / CODEGEN ====================
    var jsonMode by remember { mutableStateOf("DIFF") } // DIFF, SCHEMA, KOTLIN, TS
    var leftJson by remember(initialText) {
        mutableStateOf(
            initialText.ifBlank {
                "{\n  \"app\": \"OmniDeck\",\n  \"version\": 2,\n  \"features\": [\"ai\", \"pdf\"],\n  \"dark\": true\n}"
            }
        )
    }
    var rightJson by remember {
        mutableStateOf(
            "{\n  \"app\": \"OmniDeck\",\n  \"version\": 3,\n  \"features\": [\"ai\", \"pdf\", \"totp\"],\n  \"theme\": \"obsidian\"\n}"
        )
    }

    // ==================== 2. SVG OPTIMIZER & VECTOR ====================
    var svgInput by remember(initialText) {
        mutableStateOf(
            initialText.ifBlank {
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><!-- icon --><metadata>author</metadata><path d="M12.0000 2.0000 L22.0000 20.0000 L2.0000 20.0000 Z" /></svg>"""
            }
        )
    }
    var svgPrecision by remember { mutableIntStateOf(2) }

    // ==================== 3. GRADIENT & CUBIC BEZIER ====================
    var gradKind by remember { mutableStateOf(GradientKind.LINEAR) }
    var gradAngle by remember { mutableIntStateOf(135) }
    var gradPresetIdx by remember { mutableIntStateOf(0) }
    val gradPresets = remember {
        listOf(
            "Cobalt Flow" to listOf(GradientStopSpec(0xFF2563EB.toInt(), 0), GradientStopSpec(0xFF06B6D4.toInt(), 100)),
            "Sunset Coral" to listOf(GradientStopSpec(0xFFFF4F00.toInt(), 0), GradientStopSpec(0xFFE11D48.toInt(), 50), GradientStopSpec(0xFFFBBF24.toInt(), 100)),
            "Obsidian Mint" to listOf(GradientStopSpec(0xFF070709.toInt(), 0), GradientStopSpec(0xFF10B981.toInt(), 100)),
            "Cyber Iris" to listOf(GradientStopSpec(0xFF3B82F6.toInt(), 0), GradientStopSpec(0xFF14B8A6.toInt(), 55), GradientStopSpec(0xFF38BDF8.toInt(), 100))
        )
    }
    var bezierPreset by remember { mutableIntStateOf(0) }
    val bezierValues = when (bezierPreset) {
        1 -> listOf(0.22f, 1f, 0.36f, 1f) // easeOutQuint
        2 -> listOf(0.34f, 1.56f, 0.64f, 1f) // spring overshoot
        3 -> listOf(0.4f, 0f, 0.2f, 1f) // material standard
        else -> listOf(0.25f, 0.1f, 0.25f, 1f) // smooth ease
    }

    val computedOutput = when (meta.id) {
        "dev-diffjson" -> when (jsonMode) {
            "DIFF" -> {
                val res = JsonDiffSchemaEngine.diffJson(leftJson, rightJson)
                if (!res.valid) res.error
                else if (res.entries.isEmpty()) "No structural differences found."
                else res.entries.joinToString("\n") { "${it.kind.name} ${it.path}: ${it.leftValue ?: "∅"} -> ${it.rightValue ?: "∅"}" }
            }
            "SCHEMA" -> JsonDiffSchemaEngine.generateJsonSchema(leftJson).getOrElse { "Invalid JSON: ${it.message}" }
            "KOTLIN" -> JsonDiffSchemaEngine.generateKotlinDataClasses(leftJson).getOrElse { "Invalid JSON: ${it.message}" }
            else -> JsonDiffSchemaEngine.generateTypeScriptInterfaces(leftJson).getOrElse { "Invalid JSON: ${it.message}" }
        }
        "dev-svg" -> {
            val rep = SvgVectorEngine.optimizeAndInspect(svgInput, svgPrecision)
            "${rep.optimizedSvg}\n\n// Compose Vector\n${rep.composeSnippet}"
        }
        else -> {
            val stops = gradPresets[gradPresetIdx].second
            val css = GradientBezierEngine.toCss(gradKind, gradAngle, stops)
            val compose = GradientBezierEngine.toComposeSnippet(gradKind, stops)
            val curve = "transition-timing-function: cubic-bezier(${bezierValues.joinToString(", ")});"
            "$css\n$curve\n\n// Jetpack Compose\n$compose"
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = computedOutput,
        outputFileName = "${meta.id}_${System.currentTimeMillis()}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = when (meta.id) {
            "dev-diffjson" -> "Compare two JSON documents recursively or infer JSON Schema (Draft 2020-12), Kotlin @Serializable data classes, and TypeScript interfaces."
            "dev-svg" -> "Strip metadata, comments, and excess decimals from SVGs while generating Jetpack Compose ImageVector starter code."
            else -> "Design multi-stop CSS & Compose gradients alongside live cubic-bezier motion curves."
        },
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "dev-diffjson" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("DIFF" to "Diff JSON", "SCHEMA" to "JSON Schema", "KOTLIN" to "Kotlin Class", "TS" to "TypeScript").forEach { (id, lbl) ->
                                PixelChip(
                                    label = lbl,
                                    selected = jsonMode == id,
                                    chapterHue = hue,
                                    onClick = { jsonMode = id }
                                )
                            }
                        }
                        Text(if (jsonMode == "DIFF") "LEFT JSON" else "SOURCE JSON", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = leftJson,
                            onValueChange = { leftJson = it },
                            placeholder = "{ ... }",
                            singleLine = false,
                            minHeight = 96.dp,
                            chapterHue = hue,
                            tag = "json_left_input"
                        )
                        if (jsonMode == "DIFF") {
                            Text("RIGHT JSON", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelTextField(
                                value = rightJson,
                                onValueChange = { rightJson = it },
                                placeholder = "{ ... }",
                                singleLine = false,
                                minHeight = 96.dp,
                                chapterHue = hue,
                                tag = "json_right_input"
                            )
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (jsonMode == "DIFF") "STRUCTURAL DIFF" else "$jsonMode OUTPUT",
                            style = DotkitType.monoLabel(10),
                            color = palette.ink2
                        )
                        if (jsonMode == "DIFF") {
                            val diff = remember(leftJson, rightJson) { JsonDiffSchemaEngine.diffJson(leftJson, rightJson) }
                            if (!diff.valid) {
                                Text(diff.error, style = DotkitType.monoData(12), color = palette.err)
                            } else if (diff.entries.isEmpty()) {
                                PixelStamp("IDENTICAL STRUCTURE", palette.ok)
                            } else {
                                diff.entries.forEach { entry ->
                                    val c = when (entry.kind) {
                                        JsonDiffKind.ADDED -> palette.ok
                                        JsonDiffKind.REMOVED -> palette.err
                                        JsonDiffKind.CHANGED -> palette.warn
                                        JsonDiffKind.TYPE_CHANGED -> palette.info
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(c.copy(alpha = 0.08f))
                                            .border(0.8.dp, c.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PixelStamp(entry.kind.name, c)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.path, style = DotkitType.monoData(12), color = palette.ink)
                                            Text("${entry.leftValue ?: "∅"}  →  ${entry.rightValue ?: "∅"}", style = DotkitType.monoLabel(10), color = palette.ink2)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = computedOutput,
                                style = DotkitType.monoData(12),
                                color = palette.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.paper)
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }

            "dev-svg" -> {
                val report = remember(svgInput, svgPrecision) { SvgVectorEngine.optimizeAndInspect(svgInput, svgPrecision) }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SVG SOURCE XML", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = svgInput,
                            onValueChange = { svgInput = it },
                            placeholder = "<svg viewBox=\"0 0 24 24\">...</svg>",
                            singleLine = false,
                            minHeight = 110.dp,
                            chapterHue = hue
                        )
                        PixelSteppedSlider(
                            label = "FLOAT PRECISION DECIMALS",
                            value = svgPrecision,
                            valueRange = 0..4,
                            step = 1,
                            chapterHue = hue,
                            onValueChange = { svgPrecision = it }
                        )
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SAVED ${String.format(Locale.US, "%.1f", report.savingsPercent)}%", style = DotkitType.readoutDoto(22), color = palette.ok)
                            PixelStamp("${report.originalBytes}B → ${report.optimizedBytes}B", palette.chapterColor(hue))
                        }
                        Text("VIEWBOX: ${report.viewBoxWidth.toInt()} × ${report.viewBoxHeight.toInt()} · PATHS: ${report.pathDataList.size}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(
                            text = report.optimizedSvg,
                            style = DotkitType.monoData(11),
                            color = palette.ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.paper).padding(10.dp)
                        )
                        Text(
                            text = report.composeSnippet,
                            style = DotkitType.monoData(11),
                            color = palette.ink2,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.paper).padding(10.dp)
                        )
                    }
                }
            }

            else -> {
                val activeStops = gradPresets[gradPresetIdx].second
                val colors = activeStops.map { Color(it.colorArgb) }
                val infinite = rememberInfiniteTransition(label = "bezier_demo")
                val linearT by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                    label = "bezier_t"
                )
                val easedY = if (look.motionMode == MotionMode.OFF) 0.5f else GradientBezierEngine.sampleCubicBezier(
                    bezierValues[0], bezierValues[1], bezierValues[2], bezierValues[3], linearT
                )

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GRADIENT PREVIEW", style = DotkitType.monoLabel(10), color = palette.ink2)
                        val brush = when (gradKind) {
                            GradientKind.LINEAR -> Brush.linearGradient(colors)
                            GradientKind.RADIAL -> Brush.radialGradient(colors)
                            GradientKind.CONIC -> Brush.sweepGradient(colors)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brush)
                                .border(0.8.dp, palette.hairline, RoundedCornerShape(14.dp))
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GradientKind.entries.forEach { k ->
                                PixelChip(label = k.label, selected = gradKind == k, chapterHue = hue, onClick = { gradKind = k })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            gradPresets.forEachIndexed { idx, (name, _) ->
                                PixelChip(label = name, selected = gradPresetIdx == idx, chapterHue = ChapterHue.COBALT, onClick = { gradPresetIdx = idx })
                            }
                        }
                        PixelSteppedSlider(
                            label = "ANGLE",
                            value = gradAngle,
                            valueRange = 0..360,
                            step = 15,
                            unitSuffix = "°",
                            chapterHue = hue,
                            onValueChange = { gradAngle = it }
                        )
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CUBIC-BEZIER TIMING CURVE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Smooth Ease", "EaseOutQuint", "Spring Bounce", "M3 Standard").forEachIndexed { idx, lbl ->
                                PixelChip(label = lbl, selected = bezierPreset == idx, chapterHue = ChapterHue.SKY, onClick = { bezierPreset = idx })
                            }
                        }
                        val curveColor = palette.chapterColor(hue)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.paper)
                                .border(0.8.dp, palette.hairline, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            val p = Path().apply {
                                moveTo(0f, size.height)
                                for (s in 1..30) {
                                    val tx = s / 30f
                                    val ty = GradientBezierEngine.sampleCubicBezier(
                                        bezierValues[0], bezierValues[1], bezierValues[2], bezierValues[3], tx
                                    )
                                    lineTo(tx * size.width, size.height * (1f - ty.coerceIn(-0.15f, 1.15f)))
                                }
                            }
                            drawPath(p, color = curveColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                            val bx = easedY.coerceIn(0f, 1.1f) * size.width
                            drawCircle(curveColor, radius = 6.dp.toPx(), center = Offset(bx.coerceIn(0f, size.width), 10.dp.toPx()))
                        }
                        Text(
                            text = computedOutput,
                            style = DotkitType.monoData(11),
                            color = palette.ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.paper).padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
