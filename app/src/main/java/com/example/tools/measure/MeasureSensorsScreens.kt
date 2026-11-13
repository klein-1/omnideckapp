package com.example.tools.measure

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

@SuppressLint("MissingPermission")
@Composable
fun MeasureSensorsScreen(
    meta: ToolMeta,
    onLogNetworkAudit: (toolId: String, host: String, outB: Long, inB: Long, note: String) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.MINT

    // Vibrometer state
    val vibroHistory = remember { mutableStateListOf<Float>() }
    var peakG by remember { mutableFloatStateOf(0f) }
    var currentG by remember { mutableFloatStateOf(0f) }
    var vibroMode by remember { mutableStateOf("RMS") } // RMS, MERCALLI

    // Pedometer state
    var hasActivityPerm by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var stepBaseline by remember { mutableIntStateOf(-1) }
    var stepCount by remember { mutableIntStateOf(0) }
    val actPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasActivityPerm = it }

    // Light meter state
    var luxValue by remember { mutableFloatStateOf(0f) }
    var maxLux by remember { mutableFloatStateOf(0f) }
    var lightSupported by remember { mutableStateOf(true) }

    // Sound meter state
    var hasMicPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var micListening by remember { mutableStateOf(false) }
    var dbSpl by remember { mutableFloatStateOf(0f) }
    var peakDb by remember { mutableFloatStateOf(0f) }
    val dbHistory = remember { mutableStateListOf<Float>() }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPerm = granted
        if (granted) micListening = true
    }

    // Speed test state
    var speedRunning by remember { mutableStateOf(false) }
    var speedMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableFloatStateOf(0f) }
    var jitterMs by remember { mutableFloatStateOf(0f) }
    var speedNote by remember { mutableStateOf("Tap Run Speed Test to benchmark real download bandwidth & latency.") }

    // Motion sensors wiring
    if (meta.id in setOf("meas-vibro", "meas-pedometer", "meas-light")) {
        val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        DisposableEffect(meta.id, sm, hasActivityPerm) {
            var lastStrideTime = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(ev: SensorEvent) {
                    when (ev.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            val mag = sqrt(ev.values[0] * ev.values[0] + ev.values[1] * ev.values[1] + ev.values[2] * ev.values[2])
                            val dynG = abs(mag - SensorManager.GRAVITY_EARTH) / SensorManager.GRAVITY_EARTH
                            if (meta.id == "meas-vibro") {
                                currentG = dynG
                                if (dynG > peakG) peakG = dynG
                                vibroHistory.add(dynG)
                                if (vibroHistory.size > 60) vibroHistory.removeAt(0)
                            } else if (meta.id == "meas-pedometer" && sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) {
                                val now = System.currentTimeMillis()
                                if (dynG > 0.35f && now - lastStrideTime > 320L) {
                                    stepCount++
                                    lastStrideTime = now
                                }
                            }
                        }
                        Sensor.TYPE_STEP_COUNTER -> {
                            val raw = ev.values[0].toInt()
                            if (stepBaseline < 0) stepBaseline = raw
                            stepCount = (raw - stepBaseline).coerceAtLeast(0)
                        }
                        Sensor.TYPE_LIGHT -> {
                            luxValue = ev.values[0]
                            if (luxValue > maxLux) maxLux = luxValue
                        }
                    }
                }
                override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
            }
            when (meta.id) {
                "meas-vibro" -> sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                "meas-pedometer" -> {
                    val stepSensor = if (hasActivityPerm) sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) else null
                    if (stepSensor != null) sm?.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
                    else sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                }
                "meas-light" -> {
                    val ls = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
                    if (ls != null) sm?.registerListener(listener, ls, SensorManager.SENSOR_DELAY_UI) else lightSupported = false
                }
            }
            onDispose { sm?.unregisterListener(listener) }
        }
    }

    // Real AudioRecord dB loop
    if (meta.id == "meas-sound" && micListening && hasMicPerm) {
        DisposableEffect(micListening) {
            val job = scope.launch(Dispatchers.IO) {
                val sampleRate = 44100
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
                val recorder = runCatching {
                    AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
                }.getOrNull()
                if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) { micListening = false; onStatusToast("Microphone unavailable.") }
                    return@launch
                }
                val buf = ShortArray(minBuf / 2)
                recorder.startRecording()
                try {
                    while (isActive && micListening) {
                        val read = recorder.read(buf, 0, buf.size)
                        if (read > 0) {
                            var sumSq = 0.0
                            for (i in 0 until read) {
                                val v = buf[i].toDouble()
                                sumSq += v * v
                            }
                            val rms = sqrt(sumSq / read).coerceAtLeast(1.0)
                            val db = (20.0 * log10(rms)).toFloat().coerceIn(20f, 115f)
                            withContext(Dispatchers.Main) {
                                dbSpl = db
                                if (db > peakDb) peakDb = db
                                dbHistory.add(db)
                                if (dbHistory.size > 50) dbHistory.removeAt(0)
                            }
                        }
                    }
                } finally {
                    runCatching { recorder.stop(); recorder.release() }
                }
            }
            onDispose { job.cancel() }
        }
    }

    val outputText = when (meta.id) {
        "meas-vibro" -> "Current G=%.3f | Peak G=%.3f".format(currentG, peakG)
        "meas-pedometer" -> "Steps=$stepCount | Est Distance=%.2f km | Est kcal=%.0f".format(stepCount * 0.00075, stepCount * 0.04)
        "meas-speedtest" -> "Download=%.2f Mbps | Ping=%.0f ms | Jitter=%.1f ms".format(speedMbps, pingMs, jitterMs)
        "meas-sound" -> "Level=%.1f dB | Peak=%.1f dB".format(dbSpl, peakDb)
        "meas-light" -> "Lux=%.1f lx | Max=%.1f lx".format(luxValue, maxLux)
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
            "meas-vibro" -> {
                val mercalli = when {
                    currentG < 0.015f -> "I. Imperceptible"
                    currentG < 0.05f -> "II–III. Weak"
                    currentG < 0.15f -> "IV–V. Light–Moderate"
                    currentG < 0.35f -> "VI. Strong"
                    else -> "VII+. Very Strong"
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("%.3f G".format(currentG), sizeSp = 32, accentColor = palette.ink)
                            PixelStamp(if (vibroMode == "MERCALLI") mercalli else "PEAK %.3fG".format(peakG), palette.chapterColor(hue))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip(label = "G-Force Waveform", selected = vibroMode == "RMS", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { vibroMode = "RMS" })
                            PixelChip(label = "Mercalli Scale", selected = vibroMode == "MERCALLI", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { vibroMode = "MERCALLI" })
                        }
                        Canvas(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(8.dp)) {
                            if (vibroHistory.size > 1) {
                                val path = Path()
                                vibroHistory.forEachIndexed { idx, v ->
                                    val x = idx.toFloat() / (vibroHistory.size - 1) * size.width
                                    val y = size.height - (v / 0.8f).coerceIn(0f, 1f) * size.height
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, palette.chapterColor(hue), style = Stroke(4f))
                            }
                        }
                        PixelButton(text = "Reset Peak", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { peakG = 0f })
                    }
                }
            }

            "meas-pedometer" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (Build.VERSION.SDK_INT >= 29 && !hasActivityPerm) {
                            Text("Grant Physical Activity permission to read the hardware step counter sensor (accelerometer fallback active).", style = DotkitType.bodyUi(12), color = palette.ink2)
                            PixelButton(text = "Enable Hardware Step Sensor", chapterHue = hue, onClick = {
                                actPermLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            })
                        }
                        DotMatrixReadout("$stepCount STEPS", sizeSp = 36, accentColor = palette.ink)
                        Text("Distance: %.2f km  ·  Active Burn: %.0f kcal".format(stepCount * 0.00075, stepCount * 0.04), style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                        PixelButton(text = "Reset Session", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { stepBaseline = -1; stepCount = 0 })
                    }
                }
            }

            "meas-speedtest" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("%.1f Mbps".format(speedMbps), sizeSp = 36, accentColor = palette.ink)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ping: %.0f ms".format(pingMs), style = DotkitType.monoData(13), color = palette.ink2)
                            Text("Jitter: %.1f ms".format(jitterMs), style = DotkitType.monoData(13), color = palette.ink2)
                        }
                        Text(speedNote, style = DotkitType.bodyUi(12), color = palette.ink2)
                        PixelButton(
                            text = if (speedRunning) "Benchmarking…" else "Run Speed Test",
                            chapterHue = hue,
                            enabled = !speedRunning,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                speedRunning = true
                                speedNote = "Measuring latency & downloading test payload…"
                                scope.launch(Dispatchers.IO) {
                                    val client = OkHttpClient()
                                    val rtts = mutableListOf<Long>()
                                    repeat(3) {
                                        val t0 = System.nanoTime()
                                        runCatching {
                                            client.newCall(Request.Builder().url("https://speed.cloudflare.com/__down?bytes=1024").build()).execute().use { it.body?.bytes() }
                                            rtts += (System.nanoTime() - t0) / 1_000_000L
                                        }
                                    }
                                    val avgPing = if (rtts.isNotEmpty()) rtts.average().toFloat() else 0f
                                    val jit = if (rtts.size > 1) abs(rtts.max() - rtts.min()).toFloat() else 0f

                                    val tStart = System.nanoTime()
                                    val bytesDownloaded = runCatching {
                                        client.newCall(Request.Builder().url("https://speed.cloudflare.com/__down?bytes=5000000").build()).execute().use { resp ->
                                            var count = 0L
                                            val buf = ByteArray(16384)
                                            resp.body?.byteStream()?.use { ins ->
                                                while (true) {
                                                    val n = ins.read(buf)
                                                    if (n < 0) break
                                                    count += n
                                                }
                                            }
                                            count
                                        }
                                    }.getOrDefault(0L)
                                    val elapsedSec = ((System.nanoTime() - tStart) / 1_000_000_000.0).coerceAtLeast(0.01)
                                    val mbps = ((bytesDownloaded * 8.0) / elapsedSec / 1_000_000.0).toFloat()

                                    withContext(Dispatchers.Main) {
                                        pingMs = avgPing
                                        jitterMs = jit
                                        speedMbps = mbps
                                        speedRunning = false
                                        speedNote = if (bytesDownloaded > 0L) "Transferred ${bytesDownloaded / 1024} KB in %.2fs.".format(elapsedSec) else "Network unreachable."
                                        onLogNetworkAudit(meta.id, "speed.cloudflare.com", 256L, bytesDownloaded, "%.1f Mbps · Ping %.0fms".format(mbps, avgPing))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            "meas-sound" -> {
                val guide = when {
                    dbSpl < 40 -> "Quiet Room / Library"
                    dbSpl < 65 -> "Conversation / Office"
                    dbSpl < 85 -> "Busy Traffic / Street"
                    else -> "Loud — Hearing Caution"
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!hasMicPerm) {
                            Text("Microphone permission is needed for live sound level metering.", style = DotkitType.bodyUi(13), color = palette.ink2)
                            PixelButton(text = "Enable Microphone", chapterHue = hue, onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) })
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                DotMatrixReadout("%.1f dB".format(dbSpl), sizeSp = 36, accentColor = palette.ink)
                                PixelStamp("PEAK %.1f".format(peakDb), palette.chapterColor(hue))
                            }
                            Text(guide, style = DotkitType.bodyUi(13, bold = true), color = palette.chapterColor(hue))
                            PixelProgressBar((dbSpl / 110f).coerceIn(0f, 1f), hue)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = if (micListening) "Pause Mic" else "Start Meter", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { micListening = !micListening })
                                PixelButton(text = "Reset Peak", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { peakDb = 0f })
                            }
                        }
                    }
                }
            }

            "meas-light" -> {
                val env = when {
                    luxValue < 20 -> "Dim / Night"
                    luxValue < 150 -> "Indoor Ambient"
                    luxValue < 500 -> "Office / Reading Desk"
                    luxValue < 2000 -> "Bright Daylight Window"
                    else -> "Direct Sunlight"
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!lightSupported) {
                            Text("This device does not expose a hardware ambient light sensor.", style = DotkitType.bodyUi(13), color = palette.err)
                        } else {
                            DotMatrixReadout("%.0f lx".format(luxValue), sizeSp = 38, accentColor = palette.ink)
                            Text("$env · Peak %.0f lx".format(maxLux), style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                            PixelProgressBar((luxValue / 2000f).coerceIn(0f, 1f), hue)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
