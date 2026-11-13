package com.example.tools.measure

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
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
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasureBasicScreen(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.MINT
    val metrics = context.resources.displayMetrics
    val prefs = remember { context.getSharedPreferences("omnideck_measure", Context.MODE_PRIVATE) }

    // Ruler state (with dynamic DPI + persistent calibration + rotation + lock)
    var dpiScalePct by remember { mutableIntStateOf(prefs.getInt("ruler_cal_pct", 100)) }
    var metricRuler by remember { mutableStateOf(true) }
    var rulerLocked by remember { mutableStateOf(false) }
    var rulerAngleDeg by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var caliperAPx by remember { mutableFloatStateOf(with(density) { 40.dp.toPx() }) }
    var caliperBPx by remember { mutableFloatStateOf(with(density) { 180.dp.toPx() }) }
    var activeHandle by remember { mutableIntStateOf(1) } // 0 = A, 1 = B
    var rulerShowWorkshop by remember { mutableStateOf(false) }
    var polyCoordsInput by remember { mutableStateOf("0,0; 10,0; 10,6; 0,6") }
    var estWidthStr by remember { mutableStateOf("0") }
    var estHeightStr by remember { mutableStateOf("0") }
    var estCoatsStr by remember { mutableStateOf("0") }
    var estRiseCmStr by remember { mutableStateOf("0") }
    var refChartTab by remember { mutableStateOf("AWG") }

    // Protractor state (resizable + fine ticks + dual rays + lock)
    var protLocked by remember { mutableStateOf(false) }
    var protSizePct by remember { mutableIntStateOf(90) }
    var rayADeg by remember { mutableFloatStateOf(0f) }
    var rayBDeg by remember { mutableFloatStateOf(45f) }
    var protRotateDeg by remember { mutableIntStateOf(0) }

    // Accelerometer tilt / Spirit Level state
    var rollDeg by remember { mutableFloatStateOf(0f) }
    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var zeroRoll by remember { mutableFloatStateOf(0f) }
    var zeroPitch by remember { mutableFloatStateOf(0f) }

    if (meta.id == "meas-level" || meta.id == "meas-protractor") {
        val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        val accel = remember(sm) { sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
        DisposableEffect(sm, accel) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(ev: SensorEvent) {
                    val ax = ev.values[0]; val ay = ev.values[1]; val az = ev.values[2]
                    rollDeg = Math.toDegrees(atan2(-ax, az).toDouble()).toFloat()
                    pitchDeg = Math.toDegrees(atan2(ay, sqrt(ax * ax + az * az)).toDouble()).toFloat()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (accel != null) sm?.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm?.unregisterListener(listener) }
        }
    }

    // Pendulum Lab state
    var lengthCm by remember { mutableIntStateOf(100) }
    var swingActive by remember { mutableStateOf(true) }
    var simTimeSec by remember { mutableFloatStateOf(0f) }
    var tapPeriodSec by remember { mutableFloatStateOf(0f) }
    var lastTapMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(meta.id, swingActive) {
        if (meta.id == "meas-pendulum" && swingActive) {
            var last = System.currentTimeMillis()
            while (swingActive) {
                delay(16L)
                val now = System.currentTimeMillis()
                simTimeSec += (now - last) / 1000f
                last = now
            }
        }
    }

    // Dynamic physical DPI along measurement axis (uses xdpi/ydpi interpolated along rotation)
    val rad = rulerAngleDeg * PI / 180.0
    val baseDpi = hypot(
        (metrics.xdpi * cos(rad)).toFloat(),
        (metrics.ydpi * sin(rad)).toFloat()
    ).let { if (it in 72f..900f) it else (metrics.densityDpi.toFloat().coerceAtLeast(160f)) }
    val effectiveDpi = baseDpi * (dpiScalePct / 100f)
    val pxPerMm = effectiveDpi / 25.4f
    val spanPx = abs(caliperBPx - caliperAPx)
    val spanMm = spanPx / pxPerMm.coerceAtLeast(0.1f)
    val spanIn = spanMm / 25.4f

    val deltaAngle = abs(rayBDeg - rayADeg)

    val outputText = when (meta.id) {
        "meas-ruler" -> "Span: %.1f mm (%.2f cm / %.2f in) · Cal=${dpiScalePct}%% (${effectiveDpi.toInt()} DPI) · Angle=${rulerAngleDeg}°".format(spanMm, spanMm / 10f, spanIn)
        "meas-protractor" -> "Ray A=%.1f° | Ray B=%.1f° | Δ Angle=%.1f° | Tilt=%.1f°".format(rayADeg, rayBDeg, deltaAngle, pitchDeg)
        "meas-level" -> "Roll=%.1f° | Pitch=%.1f°".format(rollDeg - zeroRoll, pitchDeg - zeroPitch)
        "meas-pendulum" -> {
            val t = 2.0 * PI * sqrt((lengthCm / 100.0) / 9.80665)
            "L=$lengthCm cm -> Period T=%.3f s (Freq=%.3f Hz)".format(t, 1.0 / t)
        }
        else -> ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "meas-ruler" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChip("Precision Ruler", selected = !rulerShowWorkshop, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { rulerShowWorkshop = false })
                    PixelChip("Workshop & Charts", selected = rulerShowWorkshop, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { rulerShowWorkshop = true })
                }
                if (rulerShowWorkshop) {
                    val diagPx = hypot(metrics.widthPixels.toDouble(), metrics.heightPixels.toDouble())
                    val diagIn = hypot((metrics.widthPixels / metrics.xdpi.coerceAtLeast(72f)).toDouble(), (metrics.heightPixels / metrics.ydpi.coerceAtLeast(72f)).toDouble())
                    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
                    val g = gcd(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SCREEN METRICS & ASPECT RATIO", style = DotkitType.monoLabel(10), color = palette.ink2)
                            DotMatrixReadout("${metrics.densityDpi} DPI", "${metrics.widthPixels}×${metrics.heightPixels}px · ${metrics.widthPixels / g}:${metrics.heightPixels / g}", hue)
                            Text("Physical xdpi/ydpi: ${"%.1f".format(metrics.xdpi)} / ${"%.1f".format(metrics.ydpi)} PPI · Diag ≈ ${"%.2f".format(diagIn)}\" (${diagPx.toInt()} px)", style = DotkitType.monoData(11), color = palette.ink2)
                        }
                    }
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("POLYGON SHOELACE AREA (X,Y; ...)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelTextField(polyCoordsInput, { polyCoordsInput = it }, placeholder = "0,0; 10,0; 10,6; 0,6", chapterHue = hue)
                            val pts = polyCoordsInput.split(";").mapNotNull { tok ->
                                val parts = tok.trim().split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                if (parts.size >= 2) parts[0] to parts[1] else null
                            }
                            var area = 0.0
                            var perim = 0.0
                            if (pts.size >= 3) {
                                for (i in pts.indices) {
                                    val j = (i + 1) % pts.size
                                    area += pts[i].first * pts[j].second - pts[j].first * pts[i].second
                                    perim += hypot(pts[j].first - pts[i].first, pts[j].second - pts[i].second)
                                }
                                area = abs(area) / 2.0
                            }
                            Text("Vertices: ${pts.size} · Area = ${"%.3f".format(area)} u² · Perimeter = ${"%.3f".format(perim)} u", style = DotkitType.monoData(12), color = palette.ink)
                        }
                    }
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PAINT / TILE / STAIR ESTIMATOR", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("WIDTH (M)", style = DotkitType.monoLabel(9), color = palette.ink2)
                                    PixelNumberField(estWidthStr, { estWidthStr = it }, chapterHue = hue)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("HEIGHT / LEN (M)", style = DotkitType.monoLabel(9), color = palette.ink2)
                                    PixelNumberField(estHeightStr, { estHeightStr = it }, chapterHue = hue)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("COATS (COUNT)", style = DotkitType.monoLabel(9), color = palette.ink2)
                                    PixelNumberField(estCoatsStr, { estCoatsStr = it }, allowDecimal = false, chapterHue = hue)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("STAIR RISE (CM)", style = DotkitType.monoLabel(9), color = palette.ink2)
                                    PixelNumberField(estRiseCmStr, { estRiseCmStr = it }, chapterHue = hue)
                                }
                            }
                            val wM = estWidthStr.toDoubleOrNull() ?: 0.0
                            val hM = estHeightStr.toDoubleOrNull() ?: 0.0
                            val coats = (estCoatsStr.toIntOrNull() ?: 0).coerceAtLeast(0)
                            val riseCm = estRiseCmStr.toDoubleOrNull() ?: 0.0
                            val areaSqM = wM * hM
                            val paintLiters = (areaSqM * coats) / 10.0
                            val tilesWith10Pct = kotlin.math.ceil((areaSqM / 0.09) * 1.10).toInt()
                            val stairSteps = if (riseCm > 0.0) kotlin.math.ceil((riseCm / 17.5)).toInt() else 0
                            val treadCm = if (stairSteps > 0) (63.0 - (riseCm / stairSteps)).coerceIn(22.0, 34.0) else 0.0
                            Text(
                                "Surface: ${"%.2f".format(areaSqM)} m² · Paint (~10 m²/L): ${"%.2f".format(paintLiters)} L\n" +
                                    "30×30cm Tiles (+10% waste): $tilesWith10Pct pcs\n" +
                                    "Stairs (@17.5cm ideal riser): $stairSteps steps · Blondel tread ${"%.1f".format(treadCm)} cm",
                                style = DotkitType.monoData(11),
                                color = palette.ink
                            )
                        }
                    }
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("WORKSHOP REFERENCE CHARTS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("AWG" to "AWG Wire", "SCREW" to "Metric Tap", "ISO" to "ISO Paper", "TIRE" to "Tires").forEach { (id, lbl) ->
                                    PixelChip(lbl, selected = refChartTab == id, chapterHue = hue, onClick = { refChartTab = id })
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            val lines = when (refChartTab) {
                                "AWG" -> listOf(
                                    "AWG 10 · 2.588 mm · 5.26 mm² · 30A max",
                                    "AWG 12 · 2.053 mm · 3.31 mm² · 20A max",
                                    "AWG 14 · 1.628 mm · 2.08 mm² · 15A max",
                                    "AWG 18 · 1.024 mm · 0.82 mm² · 10A chassis",
                                    "AWG 22 · 0.644 mm · 0.33 mm² · 7A signal",
                                    "AWG 24 · 0.511 mm · 0.21 mm² · Cat5/6 pair"
                                )
                                "SCREW" -> listOf(
                                    "M3 × 0.50 · Tap Drill 2.5 mm · Clear 3.4 mm",
                                    "M4 × 0.70 · Tap Drill 3.3 mm · Clear 4.5 mm",
                                    "M5 × 0.80 · Tap Drill 4.2 mm · Clear 5.5 mm",
                                    "M6 × 1.00 · Tap Drill 5.0 mm · Clear 6.6 mm",
                                    "M8 × 1.25 · Tap Drill 6.8 mm · Clear 9.0 mm",
                                    "M10 × 1.50 · Tap Drill 8.5 mm · Clear 11.0 mm"
                                )
                                "ISO" -> listOf(
                                    "A0: 841 × 1189 mm (1.00 m²)",
                                    "A2: 420 × 594 mm · A3: 297 × 420 mm",
                                    "A4: 210 × 297 mm (8.27 × 11.69 in)",
                                    "A5: 148 × 210 mm · A6: 105 × 148 mm",
                                    "US Letter: 215.9 × 279.4 mm (8.5 × 11 in)"
                                )
                                else -> listOf(
                                    "205/55R16 · Sidewall 113mm · Ø 632mm · ~496 rev/km",
                                    "225/45R17 · Sidewall 101mm · Ø 634mm · ~495 rev/km",
                                    "235/40R18 · Sidewall 94mm  · Ø 645mm · ~487 rev/km",
                                    "245/40R19 · Sidewall 98mm  · Ø 679mm · ~462 rev/km",
                                    "Formula: Ø(mm) = Rim×25.4 + 2 × Width × (Aspect/100)"
                                )
                            }
                            lines.forEach { row ->
                                Text(row, style = DotkitType.monoData(11), color = palette.ink)
                            }
                        }
                    }
                } else {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                DotMatrixReadout(
                                    valueText = if (metricRuler) "%.1f mm".format(spanMm) else "%.2f in".format(spanIn),
                                    sizeSp = 30,
                                    accentColor = palette.ink
                                )
                                Text(
                                    text = "%.2f cm · %.0f DPI".format(spanMm / 10f, effectiveDpi),
                                    style = DotkitType.monoLabel(10),
                                    color = palette.ink2
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip(
                                    label = if (metricRuler) "mm / cm" else "inches",
                                    selected = true,
                                    chapterHue = hue,
                                    onClick = { metricRuler = !metricRuler }
                                )
                                PixelChip(
                                    label = if (rulerLocked) "🔒 Locked" else "🔓 Unlocked",
                                    selected = rulerLocked,
                                    chapterHue = if (rulerLocked) ChapterHue.ROSE else ChapterHue.COBALT,
                                    onClick = { rulerLocked = !rulerLocked }
                                )
                            }
                        }

                        // Realistic Vector Ruler Surface (Rotatable + Dual Calipers + Lockable)
                        val inkColor = palette.ink.toArgb()
                        val accentCol = palette.chapterColor(hue)
                        val rulerWoodBg = if (palette.isDark) Color(0xFF1F2630) else Color(0xFFFFF8E8)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.paper)
                                .border(1.dp, if (rulerLocked) palette.err.copy(alpha = 0.5f) else palette.hairline, RoundedCornerShape(14.dp))
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(rulerLocked, rulerAngleDeg) {
                                        if (!rulerLocked) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                // Project drag onto ruler measurement axis
                                                val theta = -rulerAngleDeg * PI / 180.0
                                                val dx = dragAmount.x
                                                val dy = dragAmount.y
                                                val cx = size.width / 2f + offsetX
                                                val cy = size.height / 2f + offsetY
                                                val relX = change.position.x - cx
                                                val relY = change.position.y - cy
                                                val alongAxis = (relX * cos(theta) - relY * sin(theta)).toFloat() + size.height / 2f

                                                val dA = abs(alongAxis - caliperAPx)
                                                val dB = abs(alongAxis - caliperBPx)
                                                if (dA < 60f || dB < 60f) {
                                                    if (dA <= dB) {
                                                        activeHandle = 0
                                                        caliperAPx = alongAxis.coerceIn(0f, size.height.toFloat())
                                                    } else {
                                                        activeHandle = 1
                                                        caliperBPx = alongAxis.coerceIn(0f, size.height.toFloat())
                                                    }
                                                } else {
                                                    offsetX = (offsetX + dx).coerceIn(-size.width * 0.35f, size.width * 0.35f)
                                                    offsetY = (offsetY + dy).coerceIn(-size.height * 0.35f, size.height * 0.35f)
                                                }
                                            }
                                        }
                                    }
                            ) {
                                val rWidth = 112.dp.toPx()
                                val rLeft = (size.width - rWidth) / 2f + offsetX
                                val rTop = offsetY
                                val rulerCenter = Offset(rLeft + rWidth / 2f, rTop + size.height / 2f)

                                rotate(degrees = rulerAngleDeg.toFloat(), pivot = rulerCenter) {
                                    // Ruler body with beveled shadow
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.14f),
                                        topLeft = Offset(rLeft + 4.dp.toPx(), rTop + 4.dp.toPx()),
                                        size = Size(rWidth, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = rulerWoodBg,
                                        topLeft = Offset(rLeft, rTop),
                                        size = Size(rWidth, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = accentCol.copy(alpha = 0.55f),
                                        topLeft = Offset(rLeft, rTop),
                                        size = Size(rWidth, size.height),
                                        cornerRadius = CornerRadius(12.dp.toPx()),
                                        style = Stroke(2.dp.toPx())
                                    )

                                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = inkColor
                                        textSize = 11.dp.toPx()
                                        typeface = Typeface.MONOSPACE
                                    }

                                    // Left edge = metric (mm / cm), Right edge = inches (1/8")
                                    var mm = 0
                                    while (mm * pxPerMm <= size.height) {
                                        val y = rTop + mm * pxPerMm
                                        val len = when {
                                            mm % 10 == 0 -> 28.dp.toPx()
                                            mm % 5 == 0 -> 18.dp.toPx()
                                            else -> 11.dp.toPx()
                                        }
                                        drawLine(
                                            color = if (metricRuler) palette.ink else palette.ink3,
                                            start = Offset(rLeft, y),
                                            end = Offset(rLeft + len, y),
                                            strokeWidth = if (mm % 10 == 0) 2.2f else 1.2f
                                        )
                                        if (mm % 10 == 0) {
                                            drawContext.canvas.nativeCanvas.drawText(
                                                "${mm / 10}",
                                                rLeft + 32.dp.toPx(),
                                                y + 4.dp.toPx(),
                                                labelPaint
                                            )
                                        }
                                        mm++
                                    }

                                    val pxPerEighth = effectiveDpi / 8f
                                    var step = 0
                                    while (step * pxPerEighth <= size.height) {
                                        val y = rTop + step * pxPerEighth
                                        val len = when {
                                            step % 8 == 0 -> 28.dp.toPx()
                                            step % 4 == 0 -> 18.dp.toPx()
                                            else -> 11.dp.toPx()
                                        }
                                        drawLine(
                                            color = if (!metricRuler) palette.ink else palette.ink3,
                                            start = Offset(rLeft + rWidth, y),
                                            end = Offset(rLeft + rWidth - len, y),
                                            strokeWidth = if (step % 8 == 0) 2.2f else 1.2f
                                        )
                                        if (step % 8 == 0 && step > 0) {
                                            drawContext.canvas.nativeCanvas.drawText(
                                                "${step / 8}\"",
                                                rLeft + rWidth - 46.dp.toPx(),
                                                y + 4.dp.toPx(),
                                                labelPaint
                                            )
                                        }
                                        step++
                                    }

                                    // Highlight span between caliper A & B
                                    val yTop = rTop + minOf(caliperAPx, caliperBPx)
                                    val yBot = rTop + maxOf(caliperAPx, caliperBPx)
                                    drawRect(
                                        color = accentCol.copy(alpha = 0.18f),
                                        topLeft = Offset(rLeft, yTop),
                                        size = Size(rWidth, (yBot - yTop).coerceAtLeast(1f))
                                    )
                                    drawLine(accentCol, Offset(rLeft - 24f, rTop + caliperAPx), Offset(rLeft + rWidth + 24f, rTop + caliperAPx), 3.5f)
                                    drawCircle(accentCol, radius = 9.dp.toPx(), center = Offset(rLeft + rWidth + 24f, rTop + caliperAPx))
                                    drawLine(palette.info, Offset(rLeft - 24f, rTop + caliperBPx), Offset(rLeft + rWidth + 24f, rTop + caliperBPx), 3.5f)
                                    drawCircle(palette.info, radius = 9.dp.toPx(), center = Offset(rLeft + rWidth + 24f, rTop + caliperBPx))
                                }
                            }
                            if (rulerLocked) {
                                PixelStamp(
                                    text = "POSITION & CALIPERS LOCKED",
                                    color = palette.err,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                                )
                            }
                        }

                        PixelSteppedSlider("Rotate Ruler", rulerAngleDeg, 0..360, 15, "°", hue, {
                            if (!rulerLocked) rulerAngleDeg = it
                        })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 45, 90, 180, 270).forEach { deg ->
                                PixelChip("${deg}°", selected = rulerAngleDeg == deg, chapterHue = hue, onClick = { if (!rulerLocked) rulerAngleDeg = deg })
                            }
                            PixelChip("Center", onClick = {
                                if (!rulerLocked) { offsetX = 0f; offsetY = 0f; rulerAngleDeg = 0 }
                            })
                        }

                        PixelSteppedSlider("DPI Calibration Trim", dpiScalePct, 65..135, 1, "%", hue, { pct ->
                            dpiScalePct = pct
                            prefs.edit().putInt("ruler_cal_pct", pct).apply()
                        })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = "Calibrate to Card (85.6mm)",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // Adjust dpiScalePct so current caliper span == 85.60mm
                                    if (spanPx > 20f) {
                                        val neededEffectiveDpi = (spanPx * 25.4f) / 85.6f
                                        val newPct = ((neededEffectiveDpi / baseDpi) * 100f).toInt().coerceIn(65, 135)
                                        dpiScalePct = newPct
                                        prefs.edit().putInt("ruler_cal_pct", newPct).apply()
                                        onStatusToast("Calibrated ruler to $newPct% using 85.6mm reference.")
                                    }
                                }
                            )
                            PixelButton(
                                text = "Zero Start",
                                variant = PixelButtonVariant.GHOST,
                                onClick = {
                                    if (!rulerLocked) {
                                        caliperAPx = 0f
                                        caliperBPx = (85.6f * pxPerMm).coerceAtLeast(100f)
                                    }
                                }
                            )
                        }
                    }
                }
                }
            }

            "meas-protractor" -> {
                val inkColor = palette.ink.toArgb()
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                DotMatrixReadout("Δ %.1f°".format(deltaAngle), sizeSp = 32, accentColor = palette.ink)
                                Text("Ray A: %.1f°  ·  Ray B: %.1f°  ·  Pitch: %.1f°".format(rayADeg, rayBDeg, abs(pitchDeg)), style = DotkitType.monoLabel(10), color = palette.ink2)
                            }
                            PixelChip(
                                label = if (protLocked) "🔒 Locked" else "🔓 Unlock",
                                selected = protLocked,
                                chapterHue = if (protLocked) ChapterHue.ROSE else hue,
                                onClick = { protLocked = !protLocked }
                            )
                        }
                        val canvasHeightDp = (190 + (protSizePct - 50) * 2.1f).dp
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(canvasHeightDp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.paper)
                                .border(1.dp, if (protLocked) palette.err.copy(alpha = 0.5f) else palette.hairline, RoundedCornerShape(14.dp))
                                .pointerInput(protLocked, protRotateDeg) {
                                    if (!protLocked) {
                                        detectDragGestures { ch, _ ->
                                            ch.consume()
                                            val cx = size.width / 2f
                                            val cy = size.height - 26f
                                            val deg = Math.toDegrees(atan2((cy - ch.position.y).toDouble(), (ch.position.x - cx).toDouble())).toFloat().coerceIn(0f, 180f)
                                            if (abs(deg - rayADeg) < abs(deg - rayBDeg)) rayADeg = deg else rayBDeg = deg
                                        }
                                    }
                                }
                        ) {
                            val cx = size.width / 2f
                            val cy = size.height - 26f
                            val maxR = minOf(size.width * 0.46f, size.height - 40f)
                            val r = maxR * (protSizePct / 100f)

                            rotate(protRotateDeg.toFloat(), pivot = Offset(cx, cy)) {
                                // Concentric precision arcs
                                drawArc(palette.ink, 180f, 180f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = Stroke(3f))
                                drawArc(palette.hairline, 180f, 180f, false, topLeft = Offset(cx - r * 0.65f, cy - r * 0.65f), size = Size(r * 1.3f, r * 1.3f), style = Stroke(1.5f))
                                drawLine(palette.ink, Offset(cx - r, cy), Offset(cx + r, cy), 3f)

                                val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = inkColor
                                    textSize = 9.5.dp.toPx()
                                    typeface = Typeface.MONOSPACE
                                    textAlign = Paint.Align.CENTER
                                }

                                // Fine 1°, 5°, 10°, 30° degree markings
                                for (a in 0..180) {
                                    val radA = a * PI / 180.0
                                    val innerFactor = when {
                                        a % 15 == 0 -> 0.80f
                                        a % 5 == 0 -> 0.88f
                                        else -> 0.94f
                                    }
                                    drawLine(
                                        color = if (a % 15 == 0) palette.ink else palette.ink3,
                                        start = Offset(cx + (r * innerFactor * cos(radA)).toFloat(), cy - (r * innerFactor * sin(radA)).toFloat()),
                                        end = Offset(cx + (r * cos(radA)).toFloat(), cy - (r * sin(radA)).toFloat()),
                                        strokeWidth = if (a % 15 == 0) 2.2f else if (a % 5 == 0) 1.4f else 0.9f
                                    )
                                    if (a % 30 == 0) {
                                        drawContext.canvas.nativeCanvas.drawText(
                                            "$a°",
                                            cx + (r * 0.70f * cos(radA)).toFloat(),
                                            cy - (r * 0.70f * sin(radA)).toFloat() + 3.dp.toPx(),
                                            lblPaint
                                        )
                                    }
                                }

                                // Shaded wedge between Ray A and Ray B
                                val minA = minOf(rayADeg, rayBDeg)
                                val sweep = abs(rayBDeg - rayADeg)
                                drawArc(
                                    color = palette.chapterColor(hue).copy(alpha = 0.16f),
                                    startAngle = -minA - sweep,
                                    sweepAngle = sweep,
                                    useCenter = true,
                                    topLeft = Offset(cx - r * 0.92f, cy - r * 0.92f),
                                    size = Size(r * 1.84f, r * 1.84f)
                                )

                                // Ray A & Ray B lines + handles
                                val rad1 = rayADeg * PI / 180.0
                                val rad2 = rayBDeg * PI / 180.0
                                drawLine(palette.info, Offset(cx, cy), Offset(cx + (r * cos(rad1)).toFloat(), cy - (r * sin(rad1)).toFloat()), 5f)
                                drawCircle(palette.info, 8.dp.toPx(), Offset(cx + (r * 0.92f * cos(rad1)).toFloat(), cy - (r * 0.92f * sin(rad1)).toFloat()))
                                drawLine(palette.chapterColor(hue), Offset(cx, cy), Offset(cx + (r * cos(rad2)).toFloat(), cy - (r * sin(rad2)).toFloat()), 5f)
                                drawCircle(palette.chapterColor(hue), 8.dp.toPx(), Offset(cx + (r * 0.92f * cos(rad2)).toFloat(), cy - (r * 0.92f * sin(rad2)).toFloat()))
                                drawCircle(palette.ink, 5.dp.toPx(), Offset(cx, cy))
                            }
                        }
                        PixelSteppedSlider("Protractor Size", protSizePct, 55..100, 5, "%", hue, { protSizePct = it })
                        PixelSteppedSlider("Base Orientation", protRotateDeg, -90..90, 5, "°", hue, { if (!protLocked) protRotateDeg = it })
                    }
                }
            }

            "meas-level" -> {
                val adjRoll = rollDeg - zeroRoll
                val adjPitch = pitchDeg - zeroPitch
                val leveled = abs(adjRoll) < 1.2f && abs(adjPitch) < 1.2f
                PixelBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("2-AXIS SPIRIT LEVEL", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp(if (leveled) "LEVEL ±1°" else "TILTED", if (leveled) palette.ok else palette.chapterColor(hue))
                        }
                        Canvas(modifier = Modifier.size(220.dp)) {
                            val r = size.minDimension / 2f
                            val cx = size.width / 2f; val cy = size.height / 2f
                            drawCircle(palette.paper, radius = r, center = Offset(cx, cy))
                            drawCircle(palette.ink, radius = r, center = Offset(cx, cy), style = Stroke(3f))
                            drawCircle(palette.hairline, radius = r * 0.35f, center = Offset(cx, cy), style = Stroke(2f))
                            drawLine(palette.hairline, Offset(0f, cy), Offset(size.width, cy), 1.5f)
                            drawLine(palette.hairline, Offset(cx, 0f), Offset(cx, size.height), 1.5f)
                            val bx = (cx + (adjRoll / 30f).coerceIn(-0.85f, 0.85f) * r * 0.8f)
                            val by = (cy + (adjPitch / 30f).coerceIn(-0.85f, 0.85f) * r * 0.8f)
                            drawCircle(if (leveled) palette.ok else palette.chapterColor(hue), radius = 18f, center = Offset(bx, by))
                        }
                        Text("Roll: %.1f°   |   Pitch: %.1f°".format(adjRoll, adjPitch), style = DotkitType.monoData(15), color = palette.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = "Zero Here", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { zeroRoll = rollDeg; zeroPitch = pitchDeg })
                            PixelButton(text = "Factory Zero", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { zeroRoll = 0f; zeroPitch = 0f })
                        }
                    }
                }
            }

            "meas-pendulum" -> {
                val theoryT = 2.0 * PI * sqrt((lengthCm / 100.0) / 9.80665)
                val omega = (2.0 * PI / theoryT).toFloat()
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("PERIOD T", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Text("%.3f s".format(theoryT), style = DotkitType.readoutDoto(28), color = palette.ink)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("FREQ", style = DotkitType.monoLabel(10), color = palette.ink2)
                                Text("%.2f Hz".format(1.0 / theoryT), style = DotkitType.monoData(16), color = palette.chapterColor(hue))
                            }
                        }
                        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(12.dp))) {
                            val pivot = Offset(size.width / 2f, 16f)
                            val rodLen = 60f + (lengthCm / 250f) * (size.height - 90f)
                            val theta = 0.42f * sin(omega * simTimeSec)
                            val bob = Offset(pivot.x + rodLen * sin(theta), pivot.y + rodLen * cos(theta))
                            drawLine(palette.ink, pivot, bob, 4f)
                            drawCircle(palette.chapterColor(hue), radius = 16f, center = bob)
                        }
                        PixelSteppedSlider("String Length", lengthCm, 10..250, 5, " cm", hue, { lengthCm = it })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = if (swingActive) "Pause Swing" else "Resume", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { swingActive = !swingActive })
                            PixelButton(text = "Tap Period", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                val now = System.currentTimeMillis()
                                if (lastTapMs > 0L) tapPeriodSec = (now - lastTapMs) / 1000f
                                lastTapMs = now
                            })
                        }
                        if (tapPeriodSec > 0f) {
                            Text("Measured Tap Period: %.3f s (%.1f BPM)".format(tapPeriodSec, 60f / tapPeriodSec), style = DotkitType.monoData(13), color = palette.ok)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
