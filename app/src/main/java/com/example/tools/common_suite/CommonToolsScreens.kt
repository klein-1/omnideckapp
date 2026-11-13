package com.example.tools.common_suite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.lib.media.ImageStudioEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.sound.PixelSoundEngine
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CreatureHearingSpec(
    val name: String,
    val minHz: Double,
    val maxHz: Double,
    val category: String
)

object FrequencyHearingClassifier {
    val SPECIES: List<CreatureHearingSpec> = listOf(
        CreatureHearingSpec("Pigeon", 0.05, 10_000.0, "Infrasound & Low"),
        CreatureHearingSpec("Blue Whale", 7.0, 35_000.0, "Infrasound to Ultrasound"),
        CreatureHearingSpec("Elephant", 16.0, 12_000.0, "Sub-bass & Mid"),
        CreatureHearingSpec("Human", 20.0, 20_000.0, "Full Audible Band"),
        CreatureHearingSpec("Goldfish", 20.0, 3_000.0, "Bass & Low-Mid"),
        CreatureHearingSpec("Cow", 23.0, 35_000.0, "Broad Spectrum"),
        CreatureHearingSpec("Dog", 40.0, 60_000.0, "Audible + Ultrasonic"),
        CreatureHearingSpec("Cat", 48.0, 85_000.0, "High Ultrasonic"),
        CreatureHearingSpec("Bullfrog", 50.0, 4_000.0, "Midrange"),
        CreatureHearingSpec("Horse", 55.0, 33_500.0, "Broad Spectrum"),
        CreatureHearingSpec("Dolphin", 75.0, 150_000.0, "Extreme Ultrasound"),
        CreatureHearingSpec("Chicken", 125.0, 2_000.0, "Voice Band"),
        CreatureHearingSpec("Owl", 200.0, 12_000.0, "Precision Audible"),
        CreatureHearingSpec("Rat", 200.0, 76_000.0, "High Ultrasonic"),
        CreatureHearingSpec("Rabbit", 360.0, 42_000.0, "Mid to Ultrasound"),
        CreatureHearingSpec("Bat", 1_000.0, 120_000.0, "Echolocation Ultrasound"),
        CreatureHearingSpec("Mouse", 1_000.0, 70_000.0, "Ultrasonic")
    )

    fun listenersForFrequency(hz: Int): List<CreatureHearingSpec> {
        val f = hz.toDouble()
        return SPECIES.filter { f in it.minHz..it.maxHz }
    }

    fun bandLabel(hz: Int): String = when {
        hz <= 0 -> "0 Hz (DC / Silent)"
        hz < 20 -> "Infrasound (< 20 Hz)"
        hz < 60 -> "Sub-Bass (20 – 60 Hz)"
        hz < 250 -> "Bass (60 – 250 Hz)"
        hz < 2_000 -> "Midrange (250 – 2,000 Hz)"
        hz < 6_000 -> "Presence (2 – 6 kHz)"
        hz <= 20_000 -> "Brilliance (6 – 20 kHz)"
        else -> "Ultrasound (> 20 kHz)"
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommonToolsExtendedScreen(
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.SAND

    // Permissions
    var hasCameraPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasGeoPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPerm = it }
    val geoPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasGeoPerm = it }
    val camGeoPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        hasCameraPerm = map[Manifest.permission.CAMERA] == true
        hasGeoPerm = map[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Camera zoom & torch state
    var camInstance by remember { mutableStateOf<Camera?>(null) }
    var zoomLevel by remember { mutableIntStateOf(1) }
    var lightRingBright by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var stampedPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // Compass & Metal detector state
    var azimuthDeg by remember { mutableFloatStateOf(0f) }
    var magUt by remember { mutableFloatStateOf(0f) }
    var metalThresholdUt by remember { mutableIntStateOf(80) }

    // GPS Speedometer & Location state
    var speedKmh by remember { mutableFloatStateOf(0f) }
    var peakSpeedKmh by remember { mutableFloatStateOf(0f) }
    var altitudeM by remember { mutableDoubleStateOf(0.0) }
    var latVal by remember { mutableDoubleStateOf(0.0) }
    var lonVal by remember { mutableDoubleStateOf(0.0) }
    var speedUnit by remember { mutableStateOf("KMH") } // KMH, MPH, KN

    // Currency state (default "0")
    var curAmountStr by remember { mutableStateOf("0") }
    var fromCur by remember { mutableStateOf("USD") }
    var toCur by remember { mutableStateOf("INR") }
    var fxRates by remember { mutableStateOf<Map<String, Double>>(mapOf("USD" to 1.0, "EUR" to 0.92, "INR" to 84.5, "GBP" to 0.79, "JPY" to 153.0, "AUD" to 1.52, "CAD" to 1.39, "AED" to 3.67)) }
    var fxSyncStatus by remember { mutableStateOf("Offline baseline rates (tap Sync Live ECB)") }

    // Frequency Generator state (0..40,000 Hz)
    var freqHz by remember { mutableIntStateOf(440) }
    var freqCustomStr by remember { mutableStateOf("440") }
    var waveType by remember { mutableStateOf("SINE") } // SINE, SQUARE, TRI, SAW
    var tonePlaying by remember { mutableStateOf(false) }

    // Compass / Magnetometer listener
    if (meta.id == "com-compass" || meta.id == "com-metal") {
        val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        DisposableEffect(meta.id, sm) {
            val accVals = FloatArray(3); val magVals = FloatArray(3)
            var hasAcc = false; var hasMag = false
            val R = FloatArray(9); val I = FloatArray(9); val orient = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(ev: SensorEvent) {
                    if (ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(ev.values, 0, accVals, 0, 3); hasAcc = true
                    } else if (ev.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(ev.values, 0, magVals, 0, 3); hasMag = true
                        val total = sqrt(magVals[0] * magVals[0] + magVals[1] * magVals[1] + magVals[2] * magVals[2])
                        magUt = total
                        if (meta.id == "com-metal" && total > metalThresholdUt) {
                            PixelSoundEngine.playBlip(true, 950, 20)
                        }
                    }
                    if (hasAcc && hasMag && SensorManager.getRotationMatrix(R, I, accVals, magVals)) {
                        SensorManager.getOrientation(R, orient)
                        azimuthDeg = ((Math.toDegrees(orient[0].toDouble()).toFloat() + 360f) % 360f)
                    }
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            onDispose { sm?.unregisterListener(listener) }
        }
    }

    // Location listener for GPS Camera & Speedometer
    if ((meta.id == "com-gpscam" || meta.id == "com-speedo") && hasGeoPerm) {
        val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
        DisposableEffect(lm, hasGeoPerm) {
            val listener = LocationListener { loc: Location ->
                latVal = loc.latitude; lonVal = loc.longitude; altitudeM = loc.altitude
                val kmh = loc.speed * 3.6f
                speedKmh = kmh
                if (kmh > peakSpeedKmh) peakSpeedKmh = kmh
            }
            runCatching {
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
                lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    latVal = it.latitude; lonVal = it.longitude; altitudeM = it.altitude
                }
            }
            onDispose { runCatching { lm?.removeUpdates(listener) } }
        }
    }

    // AudioTrack tone generator (48 kHz sample rate; safely silent at 0 Hz & Nyquist clamped)
    if (meta.id == "com-freq" && tonePlaying) {
        DisposableEffect(tonePlaying, freqHz, waveType) {
            val job = scope.launch(Dispatchers.IO) {
                val sr = 48_000
                val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                val buf = ShortArray(minBuf / 2)
                var phase = 0.0
                try {
                    while (isActive && tonePlaying) {
                        val safeFreq = freqHz.coerceIn(0, 23_900)
                        if (safeFreq <= 0) {
                            buf.fill(0)
                        } else {
                            val inc = safeFreq.toDouble() / sr
                            for (i in buf.indices) {
                                val s = when (waveType) {
                                    "SINE" -> sin(2.0 * PI * phase)
                                    "SQUARE" -> if (phase < 0.5) 1.0 else -1.0
                                    "TRI" -> 1.0 - 4.0 * kotlin.math.abs((phase + 0.25) % 1.0 - 0.5)
                                    else -> 2.0 * phase - 1.0
                                }
                                buf[i] = (s * 24000.0).toInt().toShort()
                                phase = (phase + inc) % 1.0
                            }
                        }
                        track.write(buf, 0, buf.size)
                    }
                } finally {
                    runCatching { track.stop(); track.release() }
                }
            }
            onDispose { job.cancel() }
        }
    }

    val outputText = when (meta.id) {
        "com-compass" -> "Azimuth %.0f° (${cardinalForDeg(azimuthDeg)})".format(azimuthDeg)
        "com-metal" -> "Magnetic Field: %.1f µT".format(magUt)
        "com-currency" -> {
            val amt = curAmountStr.toDoubleOrNull() ?: 0.0
            val rFrom = fxRates[fromCur] ?: 1.0
            val rTo = fxRates[toCur] ?: 1.0
            "%.2f %s = %.2f %s".format(amt, fromCur, amt / rFrom * rTo, toCur)
        }
        "com-freq" -> {
            val heardBy = FrequencyHearingClassifier.listenersForFrequency(freqHz).joinToString { it.name }
            "Tone $freqHz Hz ($waveType) · ${FrequencyHearingClassifier.bandLabel(freqHz)} · Heard by: $heardBy"
        }
        "com-speedo" -> "Speed %.1f km/h | Peak %.1f km/h | Alt %.1f m | %.5f, %.5f".format(speedKmh, peakSpeedKmh, altitudeM, latVal, lonVal)
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
        showOutputBar = outputText.isNotBlank(),
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "com-mirror", "com-magnifier", "com-gpscam" -> {
                val isFront = meta.id == "com-mirror"
                val needGeo = meta.id == "com-gpscam"
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!hasCameraPerm || (needGeo && !hasGeoPerm)) {
                            Text("Grant runtime permissions to open live camera preview.", style = DotkitType.bodyUi(13), color = palette.ink2)
                            PixelButton(text = "Grant Permissions", chapterHue = hue, onClick = {
                                if (needGeo) camGeoPermLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
                                else camPermLauncher.launch(Manifest.permission.CAMERA)
                            })
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    val pv = PreviewView(ctx)
                                    val future = ProcessCameraProvider.getInstance(ctx)
                                    future.addListener({
                                        val provider = future.get()
                                        val prev = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                                        val ic = ImageCapture.Builder().build().also { imageCapture = it }
                                        val sel = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                                        runCatching {
                                            provider.unbindAll()
                                            camInstance = provider.bindToLifecycle(lifecycleOwner, sel, prev, ic)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    pv
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(290.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(if (lightRingBright) 8.dp else 1.dp, if (lightRingBright) androidx.compose.ui.graphics.Color.White else palette.hairline, RoundedCornerShape(12.dp))
                            )
                            PixelSteppedSlider("Optical Zoom", zoomLevel, 1..6, 1, "×", hue, { z ->
                                zoomLevel = z
                                camInstance?.cameraControl?.setLinearZoom(((z - 1) / 5f).coerceIn(0f, 1f))
                            })
                            if (isFront) {
                                PixelSwitch("Screen Light Frame", lightRingBright, { lightRingBright = it }, hue)
                            } else if (meta.id == "com-gpscam") {
                                Text("GPS: %.5f, %.5f · Alt %.1fm".format(latVal, lonVal, altitudeM), style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                                PixelButton(text = "Capture & Stamp GPS Photo", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                    val ic = imageCapture ?: return@PixelButton
                                    val outFile = File(context.cacheDir, "gpscam_${System.currentTimeMillis()}.jpg")
                                    val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                    ic.takePicture(opts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            val raw = ImageStudioEngine.decodeBitmap(context, android.net.Uri.fromFile(outFile), 1200) ?: return
                                            val stamped = raw.copy(Bitmap.Config.ARGB_8888, true)
                                            val c = android.graphics.Canvas(stamped)
                                            val boxP = Paint().apply { color = Color.argb(180, 16, 16, 16) }
                                            c.drawRect(0f, stamped.height - 130f, stamped.width.toFloat(), stamped.height.toFloat(), boxP)
                                            val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
                                            val stampTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                                            c.drawText("GPS: %.5f, %.5f | %.0fm".format(latVal, lonVal, altitudeM), 28f, stamped.height - 76f, txtP)
                                            c.drawText(stampTime, 28f, stamped.height - 30f, txtP)
                                            stampedPhoto = stamped
                                            ShareMediaUtils.shareBitmapPng(context, stamped, "gps_photo")
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            onStatusToast("Capture failed: ${exception.message}")
                                        }
                                    })
                                })
                                stampedPhoto?.let { bmp ->
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Stamped Photo", modifier = Modifier.fillMaxWidth().height(160.dp))
                                }
                            }
                        }
                    }
                }
            }

            "com-compass" -> {
                PixelBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("%.0f° %s".format(azimuthDeg, cardinalForDeg(azimuthDeg)), sizeSp = 36, accentColor = palette.ink)
                        Canvas(Modifier.size(220.dp)) {
                            val r = size.minDimension / 2f
                            val cx = size.width / 2f; val cy = size.height / 2f
                            drawCircle(palette.paper, radius = r, center = Offset(cx, cy))
                            drawCircle(palette.ink, radius = r, center = Offset(cx, cy), style = Stroke(3f))
                            val northRad = (-azimuthDeg - 90f) * PI / 180.0
                            drawLine(palette.err, Offset(cx, cy), Offset(cx + (r * 0.8f * cos(northRad)).toFloat(), cy + (r * 0.8f * sin(northRad)).toFloat()), 8f)
                            drawLine(palette.ink3, Offset(cx, cy), Offset(cx - (r * 0.6f * cos(northRad)).toFloat(), cy - (r * 0.6f * sin(northRad)).toFloat()), 6f)
                        }
                        Text("Red needle tracks magnetic north.", style = DotkitType.bodyUi(12), color = palette.ink2)
                    }
                }
            }

            "com-currency" -> {
                val currencies = fxRates.keys.toList()
                val amt = curAmountStr.toDoubleOrNull() ?: 0.0
                val conv = amt / (fxRates[fromCur] ?: 1.0) * (fxRates[toCur] ?: 1.0)
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelNumberField(value = curAmountStr, onValueChange = { curAmountStr = it }, placeholder = "0", chapterHue = hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            currencies.forEach { c -> PixelChip(label = "From $c", selected = fromCur == c, chapterHue = hue, onClick = { fromCur = c }) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            currencies.forEach { c -> PixelChip(label = "To $c", selected = toCur == c, chapterHue = ChapterHue.COBALT, onClick = { toCur = c }) }
                        }
                        Text("%.2f %s = %.2f %s".format(amt, fromCur, conv, toCur), style = DotkitType.readoutDoto(28), color = palette.ink)
                        Text(fxSyncStatus, style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelButton(text = "Sync Live ECB Rates", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            scope.launch(Dispatchers.IO) {
                                val updated = runCatching {
                                    OkHttpClient().newCall(Request.Builder().url("https://api.frankfurter.app/latest?from=USD").build()).execute().use { resp ->
                                        val json = JSONObject(resp.body?.string().orEmpty())
                                        val rates = json.getJSONObject("rates")
                                        val map = mutableMapOf("USD" to 1.0)
                                        rates.keys().forEach { k -> map[k] = rates.getDouble(k) }
                                        map
                                    }
                                }.getOrNull()
                                withContext(Dispatchers.Main) {
                                    if (updated != null) {
                                        fxRates = updated
                                        fxSyncStatus = "Synced live ECB rates (${updated.size} currencies)"
                                        onLogNetworkAudit(meta.id, "api.frankfurter.app", 64L, 1024L, "Synced FX rates")
                                    } else onStatusToast("Sync failed; using baseline rates.")
                                }
                            }
                        })
                    }
                }
            }

            "com-freq" -> {
                val activeListeners = remember(freqHz) { FrequencyHearingClassifier.listenersForFrequency(freqHz) }
                val band = remember(freqHz) { FrequencyHearingClassifier.bandLabel(freqHz) }
                val humanCanHear = activeListeners.any { it.name == "Human" }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("$freqHz Hz", sizeSp = 36, accentColor = palette.ink)
                            PixelStamp(if (humanCanHear) "HUMAN AUDIBLE" else "OUTSIDE HUMAN RANGE", if (humanCanHear) palette.ok else palette.warn)
                        }
                        Text(band, style = DotkitType.monoLabel(11), color = palette.chapterColor(hue))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("SINE", "SQUARE", "TRI", "SAW").forEach { w ->
                                PixelChip(label = w, selected = waveType == w, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { waveType = w })
                            }
                        }

                        PixelSteppedSlider("Frequency (0 – 40,000 Hz)", freqHz, 0..40_000, 100, " Hz", hue, { f ->
                            freqHz = f
                            freqCustomStr = f.toString()
                        })

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelNumberField(
                                value = freqCustomStr,
                                onValueChange = { s ->
                                    freqCustomStr = s
                                    s.toIntOrNull()?.let { freqHz = it.coerceIn(0, 150_000) }
                                },
                                placeholder = "Exact Hz (0–150000)",
                                allowDecimal = false,
                                chapterHue = hue,
                                modifier = Modifier.weight(1f)
                            )
                            PixelButton(
                                text = "Apply Hz",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = {
                                    freqCustomStr.toIntOrNull()?.let { freqHz = it.coerceIn(0, 150_000) }
                                }
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 10, 20, 100, 432, 440, 1000, 8000, 16000, 22000, 30000, 40000).forEach { f ->
                                PixelChip(label = if (f >= 1000) "${f / 1000}kHz" else "${f}Hz", selected = freqHz == f, chapterHue = hue, onClick = {
                                    freqHz = f
                                    freqCustomStr = f.toString()
                                })
                            }
                        }

                        if (freqHz > 20_000) {
                            Text("Note: Most phone speakers roll off around 20–23 kHz; species classification still shows full ultrasonic compatibility up to 150 kHz.", style = DotkitType.bodyUi(11), color = palette.warn)
                        }

                        PixelButton(
                            text = if (tonePlaying) "Stop Tone" else "Play Continuous Tone",
                            variant = if (tonePlaying) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.SOLID,
                            chapterHue = hue,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { tonePlaying = !tonePlaying }
                        )

                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("CREATURES THAT CAN HEAR $freqHz Hz (${activeListeners.size}/${FrequencyHearingClassifier.SPECIES.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FrequencyHearingClassifier.SPECIES.forEach { sp ->
                                val hears = freqHz.toDouble() in sp.minHz..sp.maxHz
                                PixelChip(
                                    label = "${sp.name} (${formatRange(sp.minHz, sp.maxHz)})",
                                    selected = hears,
                                    chapterHue = if (sp.name == "Human") ChapterHue.MINT else hue,
                                    onClick = null
                                )
                            }
                        }
                    }
                }
            }

            "com-metal" -> {
                val spike = magUt > metalThresholdUt
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("%.1f µT".format(magUt), sizeSp = 34, accentColor = if (spike) palette.err else palette.ink)
                            PixelStamp(if (spike) "FERROUS SPIKE" else "NOMINAL", if (spike) palette.err else palette.ok)
                        }
                        PixelProgressBar((magUt / 180f).coerceIn(0f, 1f), if (spike) ChapterHue.ROSE else hue)
                        PixelSteppedSlider("Alert Threshold", metalThresholdUt, 50..200, 10, " µT", hue, { metalThresholdUt = it })
                    }
                }
            }

            "com-speedo" -> {
                val shown = when (speedUnit) {
                    "MPH" -> speedKmh * 0.621371f
                    "KN" -> speedKmh * 0.539957f
                    else -> speedKmh
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!hasGeoPerm) {
                            Text("Location permission is needed to read real GNSS satellite speed.", style = DotkitType.bodyUi(13), color = palette.ink2)
                            PixelButton(text = "Enable GPS Speedometer", chapterHue = hue, onClick = { geoPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("KMH", "MPH", "KN").forEach { u ->
                                    PixelChip(label = u, selected = speedUnit == u, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { speedUnit = u })
                                }
                            }
                            DotMatrixReadout("%.1f %s".format(shown, speedUnit), sizeSp = 38, accentColor = palette.ink)
                            Text("Peak: %.1f km/h  ·  Altitude: %.1f m".format(peakSpeedKmh, altitudeM), style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                            Text("Coordinates: %.5f, %.5f".format(latVal, lonVal), style = DotkitType.monoLabel(10), color = palette.ink2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun formatRange(minHz: Double, maxHz: Double): String {
    fun fmt(v: Double): String = when {
        v >= 1000.0 -> "${(v / 1000.0).toInt()}k"
        v < 1.0 -> "$v"
        else -> "${v.toInt()}"
    }
    return "${fmt(minHz)}–${fmt(maxHz)}Hz"
}

private fun cardinalForDeg(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((deg + 22.5f) / 45f).toInt() % 8]
}
