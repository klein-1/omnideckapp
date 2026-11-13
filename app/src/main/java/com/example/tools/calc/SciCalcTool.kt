package com.example.tools.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelTextField
import com.example.lib.calc.EvalResult
import com.example.lib.calc.ExpressionEvaluator
import com.example.lib.calc.ExtendedMathEngines
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SciCalcTool(
    meta: ToolMeta,
    initialExpression: String,
    isBasicMode: Boolean = false,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.COBALT

    var expression by remember(initialExpression, isBasicMode) {
        mutableStateOf(initialExpression)
    }
    var sciWorkbenchTab by remember { mutableStateOf("EXPR") } // EXPR, PROG, ALGEBRA
    var degreesMode by remember { mutableStateOf(true) }
    var sciPage by remember { mutableStateOf("TRIG") } // TRIG, HYP, COMB
    var lastAns by remember { mutableDoubleStateOf(0.0) }
    val tape = remember { mutableStateListOf<String>() }

    val evalResult = remember(expression, degreesMode, lastAns) {
        ExpressionEvaluator.evaluate(expression, degreesMode, lastAns)
    }

    val outputText = when (evalResult) {
        is EvalResult.Success -> evalResult.formatted
        is EvalResult.Error -> ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "calc_tape_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = if (isBasicMode) {
            "Standard arithmetic calculator with clean precedence, parentheses, percentage, and interactive tape."
        } else {
            "Complete scientific calculator with Trig/Hyperbolic/Combinatorics, Programmer Bitwise & IEEE-754 Float Inspector, plus Matrix, Quadratic & Linear Regression Algebra."
        }
    ) {
        if (!isBasicMode) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EXPR" to "Scientific", "PROG" to "Programmer (BIN/HEX/IEEE)", "ALGEBRA" to "Matrix · Quad · Stats").forEach { (k, lbl) ->
                    PixelChip(lbl, selected = sciWorkbenchTab == k, chapterHue = hue, onClick = { sciWorkbenchTab = k })
                }
            }
        }

        if (!isBasicMode && sciWorkbenchTab == "PROG") {
            var progRadix by remember { mutableIntStateOf(10) }
            var progInputA by remember { mutableStateOf("0") }
            var progInputB by remember { mutableStateOf("0") }
            var bitOp by remember { mutableStateOf("AND") }
            var ieeeInput by remember { mutableStateOf("0") }

            val valA = remember(progInputA, progRadix) { ExtendedMathEngines.parseProgrammerInput(progInputA, progRadix) ?: 0L }
            val valB = remember(progInputB, progRadix) { ExtendedMathEngines.parseProgrammerInput(progInputB, progRadix) ?: 0L }
            val bitOut = remember(valA, valB, bitOp) {
                when (bitOp) {
                    "AND" -> valA and valB
                    "OR" -> valA or valB
                    "XOR" -> valA xor valB
                    "NAND" -> (valA and valB).inv()
                    "SHL" -> valA shl (valB.toInt().coerceIn(0, 31))
                    "SHR" -> valA ushr (valB.toInt().coerceIn(0, 31))
                    "NOT A" -> valA.inv()
                    else -> valA
                }
            }
            val basesA = remember(valA) { ExtendedMathEngines.formatBitwiseBases(valA) }
            val basesRes = remember(bitOut) { ExtendedMathEngines.formatBitwiseBases(bitOut) }
            val ieeeVal = ieeeInput.toFloatOrNull() ?: 0f
            val (fSign, fExp, fMant) = remember(ieeeVal) { ExtendedMathEngines.ieee754FloatBreakdown(ieeeVal) }

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PROGRAMMER BASES & 32/64-BIT BITWISE", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10 to "DEC", 16 to "HEX", 8 to "OCT", 2 to "BIN").forEach { (r, lbl) ->
                            PixelChip(lbl, selected = progRadix == r, chapterHue = hue, onClick = { progRadix = r })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(progInputA, { progInputA = it }, "Operand A (base $progRadix)", chapterHue = hue, modifier = Modifier.weight(1f))
                        PixelTextField(progInputB, { progInputB = it }, "Operand B (base $progRadix)", chapterHue = hue, modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AND", "OR", "XOR", "NAND", "SHL", "SHR", "NOT A").forEach { op ->
                            PixelChip(op, selected = bitOp == op, chapterHue = hue, onClick = { bitOp = op })
                        }
                    }
                    Text("OPERAND A", style = DotkitType.monoLabel(9), color = palette.ink3)
                    basesA.forEach { (k, v) -> Text("$k: $v", style = DotkitType.monoData(11), color = palette.ink2) }
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text("RESULT (A $bitOp B)", style = DotkitType.monoLabel(9), color = palette.chapterColor(hue))
                    basesRes.forEach { (k, v) -> Text("$k: $v", style = DotkitType.monoData(12), color = palette.ink) }
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text("IEEE-754 SINGLE FLOAT (32-BIT) INSPECTOR", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelNumberField(ieeeInput, { ieeeInput = it }, "Float value", allowNegative = true, chapterHue = hue)
                    Text("Sign: $fSign  |  Exp (bias 127): $fExp  |  Mantissa: $fMant", style = DotkitType.monoData(11), color = palette.ok)
                }
            }
        } else if (!isBasicMode && sciWorkbenchTab == "ALGEBRA") {
            var matSize by remember { mutableIntStateOf(2) }
            val matCells = remember { mutableStateListOf("0", "0", "0", "0", "0", "0", "0", "0", "0") }
            var quadA by remember { mutableStateOf("0") }
            var quadB by remember { mutableStateOf("0") }
            var quadC by remember { mutableStateOf("0") }
            var statsX by remember { mutableStateOf("") }
            var statsY by remember { mutableStateOf("") }

            val matRes = remember(matSize, matCells.toList()) {
                if (matSize == 2) {
                    val m = listOf(
                        listOf(matCells[0].toDoubleOrNull() ?: 0.0, matCells[1].toDoubleOrNull() ?: 0.0),
                        listOf(matCells[3].toDoubleOrNull() ?: 0.0, matCells[4].toDoubleOrNull() ?: 0.0)
                    )
                    ExtendedMathEngines.solve2x2Matrix(m)
                } else {
                    val m = listOf(
                        listOf(matCells[0].toDoubleOrNull() ?: 0.0, matCells[1].toDoubleOrNull() ?: 0.0, matCells[2].toDoubleOrNull() ?: 0.0),
                        listOf(matCells[3].toDoubleOrNull() ?: 0.0, matCells[4].toDoubleOrNull() ?: 0.0, matCells[5].toDoubleOrNull() ?: 0.0),
                        listOf(matCells[6].toDoubleOrNull() ?: 0.0, matCells[7].toDoubleOrNull() ?: 0.0, matCells[8].toDoubleOrNull() ?: 0.0)
                    )
                    ExtendedMathEngines.solve3x3Matrix(m)
                }
            }
            val quadOut = remember(quadA, quadB, quadC) {
                ExtendedMathEngines.solveQuadratic(
                    quadA.toDoubleOrNull() ?: 0.0,
                    quadB.toDoubleOrNull() ?: 0.0,
                    quadC.toDoubleOrNull() ?: 0.0
                )
            }
            val regStats = remember(statsX, statsY) {
                val xs = statsX.split(",", " ", ";").mapNotNull { it.trim().toDoubleOrNull() }
                val ys = statsY.split(",", " ", ";").mapNotNull { it.trim().toDoubleOrNull() }.takeIf { it.isNotEmpty() }
                ExtendedMathEngines.computeStatsAndRegression(xs, ys)
            }

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("MATRIX SOLVER (DET / INV / TRANSPOSE)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("2×2", selected = matSize == 2, chapterHue = hue, onClick = { matSize = 2 })
                            PixelChip("3×3", selected = matSize == 3, chapterHue = hue, onClick = { matSize = 3 })
                        }
                    }
                    for (r in 0 until matSize) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0 until matSize) {
                                val idx = r * 3 + c
                                PixelNumberField(matCells[idx], { matCells[idx] = it }, "0", allowNegative = true, chapterHue = hue, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text("det(M) = ${"%.4f".format(matRes.determinant)}", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                    Text(
                        matRes.inverse?.joinToString("\n") { row -> row.joinToString("  ") { "%.3f".format(it) } }
                            ?: "Singular Matrix (det ≈ 0, no inverse)",
                        style = DotkitType.monoData(12),
                        color = palette.ink
                    )
                }
            }

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QUADRATIC SOLVER (ax² + bx + c = 0)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelNumberField(quadA, { quadA = it }, "a", allowNegative = true, chapterHue = hue, modifier = Modifier.weight(1f))
                        PixelNumberField(quadB, { quadB = it }, "b", allowNegative = true, chapterHue = hue, modifier = Modifier.weight(1f))
                        PixelNumberField(quadC, { quadC = it }, "c", allowNegative = true, chapterHue = hue, modifier = Modifier.weight(1f))
                    }
                    Text(quadOut, style = DotkitType.monoData(12), color = palette.ok)
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text("STATISTICS & LINEAR REGRESSION (CSV NUMBERS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(statsX, { statsX = it }, "X values CSV (e.g. 1, 2, 3, 4)", chapterHue = hue)
                    PixelTextField(statsY, { statsY = it }, "Optional Y values CSV for Linear Regression", chapterHue = hue)
                    regStats?.let { s ->
                        Text(
                            "n=${s.count} · Mean=${"%.3f".format(s.mean)} · Median=${"%.3f".format(s.median)} · σ=${"%.3f".format(s.stdDev)} · Min=${s.min} · Max=${s.max}",
                            style = DotkitType.monoData(11),
                            color = palette.ink
                        )
                        if (s.slope != null && s.intercept != null && s.r != null) {
                            Text("Fit: y = ${"%.4f".format(s.slope)}x + ${"%.4f".format(s.intercept)} (r = ${"%.4f".format(s.r)})", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                        }
                    }
                }
            }
        } else {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isBasicMode) "ARITHMETIC" else "SCIENTIFIC EXPRESSION", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!isBasicMode) {
                            PixelChip(
                                label = if (degreesMode) "DEG" else "RAD",
                                selected = true,
                                chapterHue = hue,
                                onClick = { degreesMode = !degreesMode }
                            )
                        }
                        PixelChip(
                            label = "Clear",
                            chapterHue = hue,
                            onClick = { expression = "" }
                        )
                    }
                }

                PixelTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    placeholder = if (isBasicMode) "e.g. (1250 + 420) * 1.18" else "e.g. nCr(10,3) + sin(45) * 2^4",
                    chapterHue = hue,
                    tag = "calc_expr_input"
                )

                // Live Result Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.paper)
                        .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    when (evalResult) {
                        is EvalResult.Success -> {
                            Text("= ${evalResult.formatted}", style = DotkitType.readoutDoto(32), color = palette.ink)
                        }
                        is EvalResult.Error -> {
                            Text(
                                text = if (expression.isBlank()) "0" else evalResult.message,
                                style = if (expression.isBlank()) DotkitType.readoutDoto(32) else DotkitType.monoData(12),
                                color = palette.ink2
                            )
                        }
                    }
                }

                if (!isBasicMode) {
                    // Expandable Scientific Function Ribbon
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TRIG" to "Trig & Log", "HYP" to "Hyperbolic & Inv", "COMB" to "Algebra & Const").forEach { (id, lbl) ->
                            PixelChip(
                                label = lbl,
                                selected = sciPage == id,
                                chapterHue = hue,
                                onClick = { sciPage = id },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val fnRows = when (sciPage) {
                        "TRIG" -> listOf(
                            listOf("sin(", "cos(", "tan(", "sqrt(", "cbrt("),
                            listOf("log(", "log2(", "ln(", "exp(", "^")
                        )
                        "HYP" -> listOf(
                            listOf("asin(", "acos(", "atan(", "sinh(", "cosh("),
                            listOf("tanh(", "asinh(", "acosh(", "atanh(", "abs(")
                        )
                        else -> listOf(
                            listOf("nCr(", "nPr(", "gcd(", "lcm(", "!"),
                            listOf("π", "e", "φ", "τ", "ans")
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fnRows.forEach { fnRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                fnRow.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.paper3)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                            .clickable {
                                                expression += when (key) {
                                                    "π" -> "pi"
                                                    "φ" -> "phi"
                                                    "τ" -> "tau"
                                                    else -> key
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(key, style = DotkitType.monoData(12), color = palette.ink)
                                    }
                                }
                            }
                        }
                    }
                }

                // Core Keypad
                val rows = if (isBasicMode) {
                    listOf(
                        listOf("(", ")", "%", "÷"),
                        listOf("7", "8", "9", "×"),
                        listOf("4", "5", "6", "−"),
                        listOf("1", "2", "3", "+"),
                        listOf("0", ".", "⌫", "="),
                    )
                } else {
                    listOf(
                        listOf("(", ")", ",", "%", "÷"),
                        listOf("7", "8", "9", "pi", "×"),
                        listOf("4", "5", "6", "^", "−"),
                        listOf("1", "2", "3", "e", "+"),
                        listOf("0", ".", "⌫", "C", "=")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val isEquals = key == "="
                                val isClear = key == "C"
                                val bg = when {
                                    isEquals -> palette.chapterColor(hue)
                                    isClear -> palette.err
                                    key in listOf("+", "−", "×", "÷", "^", "%") -> palette.paper3
                                    else -> palette.paper
                                }
                                val fg = when {
                                    isEquals -> hue.onColor
                                    isClear -> Color.White
                                    else -> palette.ink
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(bg)
                                        .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                        .clickable {
                                            when (key) {
                                                "C" -> expression = ""
                                                "⌫" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
                                                "=" -> {
                                                    if (evalResult is EvalResult.Success) {
                                                        lastAns = evalResult.value
                                                        tape.add(0, "$expression = ${evalResult.formatted}")
                                                        expression = evalResult.formatted
                                                    }
                                                }
                                                else -> expression += key
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(key, style = DotkitType.monoData(14), color = fg)
                                }
                            }
                        }
                    }
                }

                if (tape.isNotEmpty()) {
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("HISTORY TAPE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelChip(label = "Clear Tape", onClick = { tape.clear() })
                    }
                    tape.take(8).forEach { entry ->
                        Text(
                            text = entry,
                            style = DotkitType.monoData(12),
                            color = palette.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.paper)
                                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                .clickable { expression = entry.substringBefore(" =").trim() }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
        }
    }
}
