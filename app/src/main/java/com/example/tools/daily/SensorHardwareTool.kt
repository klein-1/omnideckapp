package com.example.tools.daily

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelStamp
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.capabilities.DeviceCapabilitySnapshot
import com.example.tools.common.ToolWorkbenchChrome
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

@Composable
fun SensorHardwareTool(
    meta: ToolMeta,
    capabilities: DeviceCapabilitySnapshot,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.MINT

    // Torch / Flashlight state
    val hasFlash = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
    var torchOn by remember { mutableStateOf(false) }
    var screenLightOn by remember { mutableStateOf(false) }

    fun setHardwareTorch(enable: Boolean) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val camId = cm?.cameraIdList?.firstOrNull { id ->
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        }
        if (camId != null) {
            runCatching {
                cm.setTorchMode(camId, enable)
                torchOn = enable
            }.onFailure {
                torchOn = false
                if (enable) {
                    screenLightOn = true
                    onStatusToast("Camera flash unavailable; using bright screen light.")
                }
            }
        } else {
            torchOn = false
            if (enable) {
                screenLightOn = true
                onStatusToast("No LED flash found; using bright screen light.")
            }
        }
    }

    // Ensure torch turns off when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            if (torchOn) {
                setHardwareTorch(false)
            }
        }
    }

    // Accelerometer / Spirit Level sensor
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val accelSensor = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var rollDeg by remember { mutableFloatStateOf(0f) }
    var hasSensorReading by remember { mutableStateOf(false) }

    DisposableEffect(accelSensor) {
        if (accelSensor == null || sensorManager == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val normRoll = Math.toDegrees(atan2(-ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
                    val normPitch = Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()
                    // Simple smoothing
                    rollDeg = rollDeg * 0.7f + normRoll * 0.3f
                    pitchDeg = pitchDeg * 0.7f + normPitch * 0.3f
                    hasSensorReading = true
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val isLevel = hasSensorReading && hypot(rollDeg.toDouble(), pitchDeg.toDouble()) < 1.5
    val exportSummary = buildString {
        appendLine("Device: Android ${capabilities.androidRelease} (SDK ${capabilities.sdkInt})")
        appendLine("CPU: ${capabilities.cpuCores} cores (${capabilities.abi})")
        if (capabilities.batteryPercent >= 0) {
            appendLine("Battery: ${capabilities.batteryPercent}% (${if (capabilities.isCharging) "Charging" else "Discharging"})")
        }
        appendLine("Torch Flash Hardware: ${if (hasFlash) "Available" else "Absent (Screen-light fallback)"}")
        if (hasSensorReading) {
            append("Spirit Level Tilt: X=${String.format(Locale.US, "%.1f", rollDeg)}°, Y=${String.format(Locale.US, "%.1f", pitchDeg)}°")
        } else {
            append("Spirit Level: Waiting or accelerometer unavailable")
        }
    }

    if (screenLightOn) {
        Dialog(
            onDismissRequest = { screenLightOn = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .clickable { screenLightOn = false }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 180.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("SCREEN LIGHT ACTIVE", style = DotkitType.monoLabel(12), color = Color.Black)
                    PixelButton(
                        text = "Turn Off ✕",
                        chapterHue = ChapterHue.VERMILION,
                        onClick = { screenLightOn = false }
                    )
                }
            }
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportSummary,
        outputFileName = "device_sensors_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Control your camera LED torch (or bright white screen light if your device has no flash), level surfaces using your real accelerometer hardware, and inspect honest hardware capabilities."
    ) {
        // Torch & Screen Light
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FLASHLIGHT & SCREEN LIGHT", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelStamp(
                        text = if (torchOn) "TORCH ON" else if (hasFlash) "FLASH READY" else "SCREEN LIGHT",
                        color = if (torchOn) palette.ok else palette.chapterColor(hue)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButton(
                        text = if (torchOn) "Turn Torch Off" else "Turn Torch On",
                        chapterHue = if (torchOn) ChapterHue.VERMILION else hue,
                        modifier = Modifier.weight(1f),
                        onClick = { setHardwareTorch(!torchOn) }
                    )
                    PixelButton(
                        text = "Screen Light",
                        variant = PixelButtonVariant.OUTLINE,
                        chapterHue = hue,
                        modifier = Modifier.weight(1f),
                        onClick = { screenLightOn = true }
                    )
                }
            }
        }

        // 2D Spirit Level
        PixelBox {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2D BUBBLE SPIRIT LEVEL", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelStamp(
                        text = when {
                            accelSensor == null -> "NO SENSOR"
                            isLevel -> "LEVEL"
                            else -> "LIVE"
                        },
                        color = if (isLevel) palette.ok else palette.chapterColor(hue)
                    )
                }

                if (accelSensor == null) {
                    ToolStateBanner(
                        kind = ToolStateKind.OFFLINE,
                        title = "Accelerometer unavailable",
                        detail = "This device or emulator does not expose a hardware accelerometer sensor.",
                        chapterHue = hue
                    )
                } else {
                    val accent = if (isLevel) palette.ok else palette.chapterColor(hue)
                    val inkCol = palette.ink
                    val surfaceCol = palette.paper
                    Canvas(
                        modifier = Modifier
                            .size(200.dp)
                            .background(surfaceCol)
                            .border(2.dp, inkCol)
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = size.minDimension * 0.42f

                        // Target crosshair & rings
                        drawCircle(inkCol.copy(alpha = 0.25f), radius = maxR, center = Offset(cx, cy), style = Stroke(width = 2f))
                        drawCircle(inkCol.copy(alpha = 0.35f), radius = maxR * 0.45f, center = Offset(cx, cy), style = Stroke(width = 2f))
                        drawLine(inkCol.copy(alpha = 0.35f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.5f)
                        drawLine(inkCol.copy(alpha = 0.35f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.5f)

                        // Bubble mapped to +/- 45 deg
                        val dx = (rollDeg / 45f).coerceIn(-1f, 1f) * maxR
                        val dy = (-pitchDeg / 45f).coerceIn(-1f, 1f) * maxR
                        drawCircle(
                            color = accent,
                            radius = 18.dp.toPx(),
                            center = Offset(cx + dx, cy + dy)
                        )
                        drawCircle(
                            color = inkCol,
                            radius = 18.dp.toPx(),
                            center = Offset(cx + dx, cy + dy),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(palette.paper)
                                .border(1.dp, palette.ink)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("X TILT (ROLL)", style = DotkitType.monoLabel(9), color = palette.ink2)
                            Text(
                                text = if (hasSensorReading) "${String.format(Locale.US, "%+.1f", rollDeg)}°" else "—",
                                style = DotkitType.readoutDoto(24),
                                color = palette.ink
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(palette.paper)
                                .border(1.dp, palette.ink)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Y TILT (PITCH)", style = DotkitType.monoLabel(9), color = palette.ink2)
                            Text(
                                text = if (hasSensorReading) "${String.format(Locale.US, "%+.1f", pitchDeg)}°" else "—",
                                style = DotkitType.readoutDoto(24),
                                color = palette.ink
                            )
                        }
                    }
                }
            }
        }

        // Device Hardware Summary
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DEVICE HARDWARE REPORT", style = DotkitType.monoLabel(10), color = palette.ink2)
                HorizontalDivider(thickness = 1.dp, color = palette.ink3)
                Text(
                    text = "Android ${capabilities.androidRelease} (SDK ${capabilities.sdkInt}) · ${capabilities.cpuCores} Cores (${capabilities.abi})",
                    style = DotkitType.monoData(12),
                    color = palette.ink
                )
                if (capabilities.batteryPercent >= 0) {
                    Text(
                        text = "Battery: ${capabilities.batteryPercent}% · ${if (capabilities.isCharging) "Charging" else "On Battery"}",
                        style = DotkitType.monoData(12),
                        color = palette.ink2
                    )
                }
                capabilities.probes.take(6).forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.paper)
                            .border(1.dp, palette.ink3)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                            Text(p.detail, style = DotkitType.monoData(11), color = palette.ink2)
                        }
                        PixelStamp(if (p.available) "OK" else "N/A", if (p.available) palette.ok else palette.warn)
                    }
                }
            }
        }
    }
}
