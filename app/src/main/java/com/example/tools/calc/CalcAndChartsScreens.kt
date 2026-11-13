package com.example.tools.calc

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlin.math.pow

private val CHART_COLORS = listOf(
    Color(0xFFFF4F00),
    Color(0xFF0052FF),
    Color(0xFF0E9F6E),
    Color(0xFFD99400),
    Color(0xFFE60026),
    Color(0xFF008B9E)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalcAndChartsScreen(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.COBALT
    val currency = look.regionFormat.currencySymbol

    // BMI state (default 0)
    var metricMode by remember { mutableStateOf(true) }
    var weightKg by remember { mutableStateOf("0") }
    var heightCm by remember { mutableStateOf("0") }
    var weightLbs by remember { mutableStateOf("0") }
    var heightFt by remember { mutableStateOf("0") }
    var heightIn by remember { mutableStateOf("0") }

    // Discount & Percentage state (default 0)
    var listPrice by remember { mutableStateOf("0") }
    var disc1 by remember { mutableStateOf("0") }
    var disc2 by remember { mutableStateOf("0") }
    var pctPart by remember { mutableStateOf("0") }
    var pctTotal by remember { mutableStateOf("0") }

    // Fuel & Mileage state (default 0)
    var distanceKm by remember { mutableStateOf("0") }
    var fuelLitres by remember { mutableStateOf("0") }
    var fuelPrice by remember { mutableStateOf("0") }
    var riders by remember { mutableStateOf("0") }

    // Electricity bill state (default 0)
    var wattsStr by remember { mutableStateOf("0") }
    var hoursDayStr by remember { mutableStateOf("0") }
    var tariffStr by remember { mutableStateOf("0") }

    // Chart builder state (clean user-added points)
    val chartEntries = remember { mutableStateListOf<Pair<String, Double>>() }
    var chartLabel by remember { mutableStateOf("") }
    var chartValue by remember { mutableStateOf("0") }
    var donutMode by remember { mutableStateOf(true) }

    val outputSummary = when (meta.id) {
        "calc-bmi" -> {
            val kg = if (metricMode) (weightKg.toDoubleOrNull() ?: 0.0) else (weightLbs.toDoubleOrNull() ?: 0.0) * 0.453592
            val m = if (metricMode) ((heightCm.toDoubleOrNull() ?: 0.0) / 100.0) else (((heightFt.toDoubleOrNull() ?: 0.0) * 12.0 + (heightIn.toDoubleOrNull() ?: 0.0)) * 0.0254)
            val bmi = if (m > 0.0 && kg > 0.0) kg / m.pow(2.0) else 0.0
            val idealLow = 18.5 * m * m; val idealHigh = 24.9 * m * m
            "BMI=${"%.1f".format(bmi)} · Healthy Range: ${"%.1f".format(idealLow)}–${"%.1f".format(idealHigh)} kg"
        }
        "calc-pct" -> {
            val p = listPrice.toDoubleOrNull() ?: 0.0
            val d1 = (disc1.toDoubleOrNull() ?: 0.0) / 100.0
            val d2 = (disc2.toDoubleOrNull() ?: 0.0) / 100.0
            val finalP = p * (1.0 - d1) * (1.0 - d2)
            "Sale Price: $currency${"%.2f".format(finalP)} (Saved $currency${"%.2f".format(p - finalP)})"
        }
        "calc-auto" -> {
            val d = distanceKm.toDoubleOrNull() ?: 0.0
            val f = fuelLitres.toDoubleOrNull() ?: 0.0
            val kml = if (f > 0.0) d / f else 0.0
            val cost = f * (fuelPrice.toDoubleOrNull() ?: 0.0)
            "Mileage=${"%.2f".format(kml)} km/L · Trip Cost=$currency${"%.2f".format(cost)}"
        }
        "calc-elec" -> {
            val kwhDay = (wattsStr.toDoubleOrNull() ?: 0.0) * (hoursDayStr.toDoubleOrNull() ?: 0.0) / 1000.0
            val billMonth = kwhDay * 30.0 * (tariffStr.toDoubleOrNull() ?: 0.0)
            "Daily=${"%.2f".format(kwhDay)} kWh · 30d=${"%.1f".format(kwhDay * 30)} kWh · Est=$currency${"%.2f".format(billMonth)}"
        }
        "calc-linechart", "calc-piechart" -> chartEntries.joinToString("\n") { "${it.first},${it.second}" }
        else -> ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputSummary,
        outputFileName = if ("chart" in meta.id) "${meta.id}.csv" else "${meta.id}.txt",
        outputKind = if ("chart" in meta.id) DataKind.CSV else DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "calc-bmi" -> {
                val kg = if (metricMode) (weightKg.toDoubleOrNull() ?: 0.0) else (weightLbs.toDoubleOrNull() ?: 0.0) * 0.453592
                val m = if (metricMode) ((heightCm.toDoubleOrNull() ?: 0.0) / 100.0) else (((heightFt.toDoubleOrNull() ?: 0.0) * 12.0 + (heightIn.toDoubleOrNull() ?: 0.0)) * 0.0254)
                val bmi = if (m > 0.0 && kg > 0.0) kg / m.pow(2.0) else 0.0
                val (cat, col) = when {
                    bmi <= 0.0 -> "ENTER VALUES" to palette.ink3
                    bmi < 18.5 -> "UNDERWEIGHT" to palette.warn
                    bmi < 25.0 -> "NORMAL HEALTHY" to palette.ok
                    bmi < 30.0 -> "OVERWEIGHT" to palette.warn
                    else -> "OBESE" to palette.err
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelSwitch("Metric Units (kg / cm)", metricMode, { metricMode = it }, hue)
                        if (metricMode) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("WEIGHT (KG)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = weightKg, onValueChange = { weightKg = it }, chapterHue = hue)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("HEIGHT (CM)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = heightCm, onValueChange = { heightCm = it }, chapterHue = hue)
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("WEIGHT (LBS)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = weightLbs, onValueChange = { weightLbs = it }, chapterHue = hue)
                                }
                                Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("FT", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = heightFt, onValueChange = { heightFt = it }, allowDecimal = false, chapterHue = hue)
                                }
                                Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("IN", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = heightIn, onValueChange = { heightIn = it }, allowDecimal = false, chapterHue = hue)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BMI: ${"%.1f".format(bmi)}", style = DotkitType.readoutDoto(32), color = palette.ink)
                            PixelStamp(cat, col)
                        }
                        Text(outputSummary, style = DotkitType.monoData(12), color = palette.ink2)
                    }
                }
            }

            "calc-pct" -> {
                var pctSubTab by remember { mutableStateOf("PCT") } // PCT, GEOM, OHM
                val p = listPrice.toDoubleOrNull() ?: 0.0
                val d1 = (disc1.toDoubleOrNull() ?: 0.0) / 100.0
                val d2 = (disc2.toDoubleOrNull() ?: 0.0) / 100.0
                val finalP = p * (1.0 - d1) * (1.0 - d2)
                val part = pctPart.toDoubleOrNull() ?: 0.0
                val tot = pctTotal.toDoubleOrNull() ?: 0.0
                val ratioPct = if (tot > 0.0) part / tot * 100.0 else 0.0

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("PCT" to "Discount & %", "GEOM" to "2D/3D Geometry", "OHM" to "Ohm's Law & Resistor").forEach { (k, lbl) ->
                        PixelChip(lbl, selected = pctSubTab == k, chapterHue = hue, onClick = { pctSubTab = k })
                    }
                }

                when (pctSubTab) {
                    "PCT" -> PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("STACKED DISCOUNT CALCULATOR", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("PRICE", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = listPrice, onValueChange = { listPrice = it }, chapterHue = hue)
                                }
                                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("1ST %", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = disc1, onValueChange = { disc1 = it }, chapterHue = hue)
                                }
                                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("EXTRA %", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = disc2, onValueChange = { disc2 = it }, chapterHue = hue)
                                }
                            }
                            Text("Sale Price: $currency${"%.2f".format(finalP)} (Saved $currency${"%.2f".format(p - finalP)})", style = DotkitType.monoData(14), color = palette.chapterColor(hue))
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("PART OF TOTAL (%)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("VALUE X", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = pctPart, onValueChange = { pctPart = it }, chapterHue = hue)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("OUT OF Y", style = DotkitType.monoLabel(9), color = palette.ink3)
                                    PixelNumberField(value = pctTotal, onValueChange = { pctTotal = it }, chapterHue = hue)
                                }
                            }
                            Text("${"%.2f".format(ratioPct)}%", style = DotkitType.readoutDoto(28), color = palette.ink)
                        }
                    }

                    "GEOM" -> {
                        var shape by remember { mutableStateOf("CIRCLE") }
                        var dimA by remember { mutableStateOf("0") }
                        var dimB by remember { mutableStateOf("0") }
                        var dimC by remember { mutableStateOf("0") }
                        val a = dimA.toDoubleOrNull() ?: 0.0
                        val b = dimB.toDoubleOrNull() ?: 0.0
                        val c = dimC.toDoubleOrNull() ?: 0.0
                        val geomText = when (shape) {
                            "CIRCLE" -> "Area = ${"%.3f".format(Math.PI * a * a)} · Circumference = ${"%.3f".format(2 * Math.PI * a)}"
                            "TRIANGLE" -> "Right Triangle Hypotenuse = ${"%.3f".format(kotlin.math.hypot(a, b))} · Area (½bh) = ${"%.3f".format(0.5 * a * b)}"
                            "CYLINDER" -> "Volume (πr²h) = ${"%.3f".format(Math.PI * a * a * b)} · Surface Area = ${"%.3f".format(2 * Math.PI * a * (a + b))}"
                            else -> "Box Volume = ${"%.3f".format(a * b * c)} · Surface = ${"%.3f".format(2 * (a * b + b * c + a * c))}"
                        }
                        PixelBox {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("2D / 3D GEOMETRY SOLVER", style = DotkitType.monoLabel(10), color = palette.ink2)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("CIRCLE" to "Circle (r)", "TRIANGLE" to "Triangle (b,h)", "CYLINDER" to "Cylinder (r,h)", "BOX" to "Cuboid (l,w,h)").forEach { (k, lbl) ->
                                        PixelChip(lbl, selected = shape == k, chapterHue = hue, onClick = { shape = k })
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelNumberField(dimA, { dimA = it }, if (shape in listOf("CIRCLE", "CYLINDER")) "r" else "a / b", chapterHue = hue, modifier = Modifier.weight(1f))
                                    if (shape != "CIRCLE") PixelNumberField(dimB, { dimB = it }, "h / w", chapterHue = hue, modifier = Modifier.weight(1f))
                                    if (shape == "BOX") PixelNumberField(dimC, { dimC = it }, "h", chapterHue = hue, modifier = Modifier.weight(1f))
                                }
                                Text(geomText, style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                            }
                        }
                    }

                    else -> { // OHM
                        var voltsStr by remember { mutableStateOf("0") }
                        var ohmsStr by remember { mutableStateOf("0") }
                        val v = voltsStr.toDoubleOrNull() ?: 0.0
                        val r = ohmsStr.toDoubleOrNull() ?: 0.0
                        val amps = if (r > 0.0) v / r else 0.0
                        val watts = v * amps

                        val colorBands = listOf("Black" to 0, "Brown" to 1, "Red" to 2, "Orange" to 3, "Yellow" to 4, "Green" to 5, "Blue" to 6, "Violet" to 7, "Grey" to 8, "White" to 9)
                        var b1 by remember { mutableStateOf(1) }
                        var b2 by remember { mutableStateOf(0) }
                        var multExp by remember { mutableStateOf(2) }
                        val ohmVal = (b1 * 10 + b2) * 10.0.pow(multExp.toDouble())
                        val ohmLabel = when {
                            ohmVal >= 1_000_000 -> "${"%.2f".format(ohmVal / 1_000_000)} MΩ"
                            ohmVal >= 1_000 -> "${"%.2f".format(ohmVal / 1_000)} kΩ"
                            else -> "${"%.0f".format(ohmVal)} Ω"
                        }
                        PixelBox {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("OHM'S LAW (V = I · R)", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelNumberField(voltsStr, { voltsStr = it }, "Volts (V)", chapterHue = hue, modifier = Modifier.weight(1f))
                                    PixelNumberField(ohmsStr, { ohmsStr = it }, "Ohms (Ω)", chapterHue = hue, modifier = Modifier.weight(1f))
                                }
                                Text("Current: ${"%.4f".format(amps)} A · Power: ${"%.3f".format(watts)} W", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                                HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                                Text("4-BAND RESISTOR COLOR DECODER", style = DotkitType.monoLabel(10), color = palette.ink2)
                                DotMatrixReadout("$ohmLabel ±5%", "BANDS ${colorBands[b1].first} · ${colorBands[b2].first} · ×10^$multExp", hue, sizeSp = 24)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PixelChip("B1: ${colorBands[b1].first}", chapterHue = hue, onClick = { b1 = (b1 + 1).coerceAtLeast(1) % 10; if (b1 == 0) b1 = 1 }, modifier = Modifier.weight(1f))
                                    PixelChip("B2: ${colorBands[b2].first}", chapterHue = hue, onClick = { b2 = (b2 + 1) % 10 }, modifier = Modifier.weight(1f))
                                    PixelChip("×10^$multExp", chapterHue = hue, onClick = { multExp = (multExp + 1) % 7 }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            "calc-auto" -> {
                val dist = distanceKm.toDoubleOrNull() ?: 0.0
                val fuel = fuelLitres.toDoubleOrNull() ?: 0.0
                val price = fuelPrice.toDoubleOrNull() ?: 0.0
                val pax = riders.toIntOrNull() ?: 0
                val kml = if (fuel > 0.0) dist / fuel else 0.0
                val mpg = kml * 2.35215
                val totalCost = fuel * price
                val perRider = if (pax > 0) totalCost / pax else totalCost
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("DISTANCE (KM)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = distanceKm, onValueChange = { distanceKm = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("FUEL (L)", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = fuelLitres, onValueChange = { fuelLitres = it }, chapterHue = hue)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("PRICE / L", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = fuelPrice, onValueChange = { fuelPrice = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("PASSENGERS", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = riders, onValueChange = { riders = it }, allowDecimal = false, chapterHue = hue)
                            }
                        }
                        Text("Efficiency: ${"%.2f".format(kml)} km/L (${"%.1f".format(mpg)} MPG)", style = DotkitType.monoData(14), color = palette.ink)
                        Text("Total Cost: $currency${"%.2f".format(totalCost)} · Per Rider: $currency${"%.2f".format(perRider)}", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                    }
                }
            }

            "calc-elec" -> {
                val watts = wattsStr.toDoubleOrNull() ?: 0.0
                val hrs = hoursDayStr.toDoubleOrNull() ?: 0.0
                val rate = tariffStr.toDoubleOrNull() ?: 0.0
                val kwhDay = watts * hrs / 1000.0
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("APPLIANCE POWER (WATTS)", style = DotkitType.monoLabel(9), color = palette.ink3)
                        PixelNumberField(value = wattsStr, onValueChange = { wattsStr = it }, chapterHue = hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("HOURS / DAY", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = hoursDayStr, onValueChange = { hoursDayStr = it }, chapterHue = hue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("COST / KWH", style = DotkitType.monoLabel(9), color = palette.ink3)
                                PixelNumberField(value = tariffStr, onValueChange = { tariffStr = it }, chapterHue = hue)
                            }
                        }
                        Text("Monthly Est (30d): $currency${"%.2f".format(kwhDay * 30.0 * rate)}", style = DotkitType.readoutDoto(28), color = palette.chapterColor(hue))
                        Text("Daily: ${"%.2f".format(kwhDay)} kWh ($currency${"%.2f".format(kwhDay * rate)}) · Yearly: ${"%.0f".format(kwhDay * 365.0)} kWh", style = DotkitType.monoData(12), color = palette.ink)
                    }
                }
            }

            "calc-linechart", "calc-piechart" -> {
                val isLine = meta.id == "calc-linechart"
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ADD DATA POINT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelTextField(value = chartLabel, onValueChange = { chartLabel = it }, placeholder = "Label (e.g. Jan)", chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelNumberField(value = chartValue, onValueChange = { chartValue = it }, chapterHue = hue, modifier = Modifier.weight(0.8f))
                            PixelButton(text = "+", chapterHue = hue, enabled = chartLabel.isNotBlank() && chartValue.toDoubleOrNull() != null, onClick = {
                                val v = chartValue.toDoubleOrNull() ?: return@PixelButton
                                chartEntries.add(chartLabel.trim() to v)
                                chartLabel = ""; chartValue = "0"
                            })
                        }

                        if (!isLine) PixelSwitch("Donut Ring Style", donutMode, { donutMode = it }, hue)

                        if (chartEntries.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                chartEntries.forEachIndexed { idx, (lbl, v) ->
                                    PixelChip(label = "$lbl: $v ✕", chapterHue = hue, onClick = { chartEntries.removeAt(idx) })
                                }
                            }

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.paper)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                    .padding(16.dp)
                            ) {
                                if (isLine) {
                                    val maxVal = (chartEntries.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                                    val minVal = (chartEntries.minOfOrNull { it.second } ?: 0.0).coerceAtMost(0.0)
                                    val span = (maxVal - minVal).coerceAtLeast(1.0)
                                    val pts = chartEntries.mapIndexed { i, pair ->
                                        val x = if (chartEntries.size == 1) size.width / 2f else (i.toFloat() / (chartEntries.size - 1)) * size.width
                                        val y = size.height - (((pair.second - minVal) / span).toFloat() * size.height)
                                        Offset(x, y)
                                    }
                                    drawLine(palette.hairline, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                                    drawLine(palette.hairline, Offset(0f, 0f), Offset(0f, size.height), 2f)
                                    if (pts.size > 1) {
                                        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                                        drawPath(path, palette.chapterColor(hue), style = Stroke(width = 6f))
                                    }
                                    pts.forEach { p -> drawCircle(palette.chapterColor(hue), radius = 10f, center = p) }
                                } else {
                                    val total = chartEntries.sumOf { it.second.coerceAtLeast(0.0) }.coerceAtLeast(0.01)
                                    val dia = size.minDimension * 0.9f
                                    val topLeft = Offset((size.width - dia) / 2f, (size.height - dia) / 2f)
                                    var startDeg = -90f
                                    chartEntries.forEachIndexed { i, (_, v) ->
                                        val sweep = ((v.coerceAtLeast(0.0) / total) * 360.0).toFloat()
                                        drawArc(
                                            color = CHART_COLORS[i % CHART_COLORS.size],
                                            startAngle = startDeg,
                                            sweepAngle = sweep,
                                            useCenter = true,
                                            topLeft = topLeft,
                                            size = Size(dia, dia)
                                        )
                                        startDeg += sweep
                                    }
                                    if (donutMode) {
                                        drawCircle(palette.paper, radius = dia * 0.24f, center = Offset(size.width / 2f, size.height / 2f))
                                    }
                                }
                            }

                            PixelButton(
                                text = "Render & Share Chart PNG",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val bmp = renderChartBitmap(isLine, donutMode, chartEntries.toList(), palette.paper.toArgb(), palette.ink.toArgb(), palette.chapterColor(hue).toArgb())
                                    ShareMediaUtils.shareBitmapPng(context, bmp, meta.id)
                                }
                            )
                        } else {
                            Text("Add 2+ data points above to plot the chart live.", style = DotkitType.bodyUi(12), color = palette.ink3)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun renderChartBitmap(
    isLine: Boolean,
    donut: Boolean,
    entries: List<Pair<String, Double>>,
    bgColor: Int,
    inkColor: Int,
    accentColor: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(900, 620, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(bgColor)
    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkColor; textSize = 26f; typeface = Typeface.MONOSPACE }
    if (isLine && entries.isNotEmpty()) {
        val maxV = (entries.maxOf { it.second }).coerceAtLeast(1.0)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; strokeWidth = 8f; style = Paint.Style.STROKE }
        val pts = entries.mapIndexed { i, e ->
            val x = 90f + if (entries.size == 1) 360f else i * (720f / (entries.size - 1))
            val y = 500f - (e.second / maxV * 380f).toFloat()
            Offset(x, y)
        }
        for (i in 1 until pts.size) c.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, p)
        pts.forEachIndexed { i, pt ->
            c.drawCircle(pt.x, pt.y, 12f, Paint().apply { color = accentColor })
            c.drawText(entries[i].first.take(8), pt.x - 20f, 550f, textP)
        }
    } else if (entries.isNotEmpty()) {
        val tot = entries.sumOf { it.second.coerceAtLeast(0.0) }.coerceAtLeast(0.01)
        val rect = RectF(100f, 90f, 520f, 510f)
        var start = -90f
        entries.forEachIndexed { i, (lbl, v) ->
            val sweep = ((v.coerceAtLeast(0.0) / tot) * 360.0).toFloat()
            val col = CHART_COLORS[i % CHART_COLORS.size].toArgb()
            c.drawArc(rect, start, sweep, true, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
            c.drawRect(570f, 110f + i * 44f, 594f, 134f + i * 44f, Paint().apply { color = col })
            c.drawText("$lbl (${"%.1f".format(v * 100 / tot)}%)", 608f, 132f + i * 44f, textP)
            start += sweep
        }
        if (donut) c.drawCircle(310f, 300f, 95f, Paint().apply { color = bgColor })
    }
    return bmp
}
