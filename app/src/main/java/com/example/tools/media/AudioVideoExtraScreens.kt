package com.example.tools.media

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.design.PixelNumberField
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.media.AudioVideoEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioVideoExtraScreen(
    meta: ToolMeta,
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
    val hue = ChapterHue.TEAL

    var hasMicPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMicPerm = it }

    // 1. Tuner + Live Oscilloscope & Spectrum state
    var tunerActive by remember { mutableStateOf(false) }
    var detectedHz by remember { mutableDoubleStateOf(0.0) }
    var detectedNote by remember { mutableStateOf("--") }
    var detectedCents by remember { mutableIntStateOf(0) }
    var wavePoints by remember { mutableStateOf<List<Float>>(emptyList()) }
    var spectrumBins by remember { mutableStateOf<List<Float>>(emptyList()) }

    LaunchedEffect(meta.id, tunerActive, hasMicPerm) {
        if (meta.id == "med-tuner" && tunerActive && hasMicPerm) {
            withContext(Dispatchers.IO) {
                val sr = 44100
                val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
                val rec = runCatching {
                    AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
                }.getOrNull()
                if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                    val buf = ShortArray(2048)
                    val bandHz = doubleArrayOf(82.0, 110.0, 146.8, 196.0, 246.9, 293.7, 329.6, 392.0, 440.0, 523.3, 659.3, 880.0, 1175.0, 1568.0)
                    rec.startRecording()
                    while (isActive && tunerActive) {
                        val read = rec.read(buf, 0, buf.size)
                        if (read > 512) {
                            val freq = estimateFundamentalHz(buf, read, sr)
                            val wave = (0 until 64).map { idx ->
                                val sIdx = (idx * read / 64).coerceIn(0, read - 1)
                                (buf[sIdx] / 32768f).coerceIn(-1f, 1f)
                            }
                            val nWin = minOf(read, 512)
                            val spec = bandHz.map { targetHz ->
                                var re = 0.0; var im = 0.0
                                val step = 2.0 * PI * targetHz / sr
                                for (k in 0 until nWin) {
                                    val x = buf[k] / 32768.0
                                    re += x * cos(step * k)
                                    im -= x * sin(step * k)
                                }
                                ((sqrt(re * re + im * im) / nWin) * 6.0).toFloat().coerceIn(0f, 1f)
                            }
                            if (freq in 50.0..1800.0) {
                                val (note, cents) = hzToNoteAndCents(freq)
                                withContext(Dispatchers.Main) {
                                    detectedHz = freq
                                    detectedNote = note
                                    detectedCents = cents
                                    wavePoints = wave
                                    spectrumBins = spec
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    wavePoints = wave
                                    spectrumBins = spec
                                }
                            }
                        }
                        delay(70L)
                    }
                    runCatching { rec.stop(); rec.release() }
                }
            }
        }
    }

    // 2. Metronome state
    var metroRunning by remember { mutableStateOf(false) }
    var metroBpm by remember { mutableIntStateOf(120) }
    var metroBeatsPerBar by remember { mutableIntStateOf(4) }
    var metroCurrentBeat by remember { mutableIntStateOf(0) }
    var tapTimes by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(meta.id, metroRunning, metroBpm, metroBeatsPerBar) {
        if (meta.id == "med-metronome" && metroRunning) {
            var beat = 0
            while (isActive && metroRunning) {
                val isAccent = (beat % metroBeatsPerBar) == 0
                metroCurrentBeat = (beat % metroBeatsPerBar) + 1
                launch(Dispatchers.IO) { playClickPcm(if (isAccent) 1400.0 else 880.0, 28) }
                beat++
                delay((60_000L / metroBpm.coerceIn(30, 240)).coerceAtLeast(150L))
            }
        } else {
            metroCurrentBeat = 0
        }
    }

    // 3. Ambient Noise + Binaural / Isochronic Generator state
    var noiseType by remember { mutableStateOf("PINK") } // WHITE, PINK, BROWN, BINAURAL, ISOCHRONIC
    var noisePlaying by remember { mutableStateOf(false) }
    var noiseMinutes by remember { mutableIntStateOf(0) }
    var noiseRemainSec by remember { mutableIntStateOf(0) }
    var binauralBaseHz by remember { mutableIntStateOf(200) }
    var binauralBeatHz by remember { mutableIntStateOf(10) }

    LaunchedEffect(meta.id, noisePlaying, noiseType, binauralBaseHz, binauralBeatHz) {
        if (meta.id == "med-noise" && noisePlaying) {
            withContext(Dispatchers.IO) {
                val sr = 22050
                val isStereoTone = noiseType == "BINAURAL" || noiseType == "ISOCHRONIC"
                val track = buildPcmTrack(sr, sr, stereo = isStereoTone)
                track.play()
                val chunk = ShortArray(if (isStereoTone) 4096 else 2048)
                var pinkB0 = 0.0; var pinkB1 = 0.0; var pinkB2 = 0.0
                var brownLast = 0.0
                var phaseL = 0.0; var phaseR = 0.0; var modPhase = 0.0
                val stepL = 2.0 * PI * binauralBaseHz / sr
                val stepR = 2.0 * PI * (binauralBaseHz + binauralBeatHz) / sr
                val stepMod = 2.0 * PI * binauralBeatHz / sr
                while (isActive && noisePlaying) {
                    if (isStereoTone) {
                        var i = 0
                        while (i + 1 < chunk.size) {
                            if (noiseType == "BINAURAL") {
                                chunk[i] = (sin(phaseL) * 0.28 * Short.MAX_VALUE).toInt().toShort()
                                chunk[i + 1] = (sin(phaseR) * 0.28 * Short.MAX_VALUE).toInt().toShort()
                                phaseL += stepL
                                phaseR += stepR
                            } else {
                                val env = 0.5 * (1.0 - cos(modPhase))
                                val s = (sin(phaseL) * env * 0.30 * Short.MAX_VALUE).toInt().toShort()
                                chunk[i] = s
                                chunk[i + 1] = s
                                phaseL += stepL
                                modPhase += stepMod
                            }
                            i += 2
                        }
                    } else {
                        for (i in chunk.indices) {
                            val w = Random.nextDouble(-1.0, 1.0)
                            val sample = when (noiseType) {
                                "WHITE" -> w * 0.25
                                "PINK" -> {
                                    pinkB0 = 0.99765 * pinkB0 + w * 0.0990460
                                    pinkB1 = 0.96300 * pinkB1 + w * 0.2965164
                                    pinkB2 = 0.57000 * pinkB2 + w * 1.0526913
                                    ((pinkB0 + pinkB1 + pinkB2 + w * 0.1848) * 0.08).coerceIn(-0.9, 0.9)
                                }
                                else -> {
                                    brownLast = (brownLast + (0.04 * w)) / 1.02
                                    (brownLast * 0.8).coerceIn(-0.9, 0.9)
                                }
                            }
                            chunk[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                        }
                    }
                    track.write(chunk, 0, chunk.size)
                }
                runCatching { track.stop(); track.release() }
            }
        }
    }

    LaunchedEffect(noisePlaying, noiseMinutes) {
        if (noisePlaying && noiseMinutes > 0) {
            noiseRemainSec = noiseMinutes * 60
            while (noisePlaying && noiseRemainSec > 0) {
                delay(1000L)
                noiseRemainSec--
            }
            if (noiseRemainSec == 0) noisePlaying = false
        } else {
            noiseRemainSec = 0
        }
    }

    // 4. DTMF + Piano & Chords state
    var dtmfSequence by remember { mutableStateOf("") }
    var dtmfLastKey by remember { mutableStateOf("-") }
    var dtmfMode by remember { mutableStateOf("DTMF") } // DTMF, PIANO
    var chordRootIdx by remember { mutableIntStateOf(0) }
    var chordType by remember { mutableStateOf("Major") }

    // 5. WAV FX Synth state
    var wavUri by remember { mutableStateOf<Uri?>(null) }
    var wavRate by remember { mutableIntStateOf(44100) }
    var wavOriginalPcm by remember { mutableStateOf<ShortArray?>(null) }
    var wavProcessedPcm by remember { mutableStateOf<ShortArray?>(null) }
    var wavFxLabel by remember { mutableStateOf("Original") }
    var wavOutFile by remember { mutableStateOf<File?>(null) }

    val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            wavUri = uri
            scope.launch(Dispatchers.IO) {
                val parsed = parseWavMono16(context.contentResolver.openInputStream(uri)?.readBytes())
                withContext(Dispatchers.Main) {
                    if (parsed != null) {
                        wavRate = parsed.first
                        wavOriginalPcm = parsed.second
                        wavProcessedPcm = parsed.second
                        wavFxLabel = "Loaded ${parsed.second.size} samples @ ${parsed.first} Hz"
                    } else {
                        onStatusToast("Pick an uncompressed PCM 16-bit WAV file.")
                    }
                }
            }
        }
    }

    // 6. Video Frame Grabber state
    var vidUri by remember { mutableStateOf<Uri?>(null) }
    var vidDurMs by remember { mutableLongStateOf(0L) }
    var frameMsStr by remember { mutableStateOf("0") }
    var grabbedBmp by remember { mutableStateOf<Bitmap?>(null) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vidUri = uri
            val info = AudioVideoEngine.inspectMedia(context, uri)
            vidDurMs = info.durationMs
            frameMsStr = "0"
            scope.launch(Dispatchers.IO) {
                val bmp = grabVideoFrame(context, uri, 0L)
                withContext(Dispatchers.Main) { grabbedBmp = bmp }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tunerActive = false
            metroRunning = false
            noisePlaying = false
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = "",
        outputFileName = "${meta.id}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        showOutputBar = false,
        helpParagraph = meta.tagline,
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "med-tuner" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!hasMicPerm) {
                            Text("Microphone access lets Omni Deck detect live pitch from your guitar, ukulele, or voice.", style = DotkitType.bodyUi(13), color = palette.ink2)
                            PixelButton("Enable Microphone", chapterHue = hue, onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) })
                        } else {
                            DotMatrixReadout(
                                value = detectedNote,
                                label = "${"%.1f".format(detectedHz)} Hz · ${if (detectedCents >= 0) "+$detectedCents" else "$detectedCents"} cents",
                                chapterHue = hue
                            )
                            val tuneNorm = ((detectedCents + 50f) / 100f).coerceIn(0f, 1f)
                            PixelProgressBar(tuneNorm, chapterHue = if (kotlin.math.abs(detectedCents) <= 8 && detectedHz > 0) ChapterHue.LIME else hue)
                            if (wavePoints.isNotEmpty()) {
                                val accentColor = palette.chapterColor(hue)
                                val okColor = palette.ok
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                        .clip(SwissControlShape)
                                        .background(palette.paper)
                                        .border(1.dp, palette.hairline, SwissControlShape)
                                ) {
                                    val midY = size.height * 0.36f
                                    drawLine(palette.hairline, Offset(0f, midY), Offset(size.width, midY), 1f)
                                    val path = Path()
                                    wavePoints.forEachIndexed { idx, v ->
                                        val x = (idx / (wavePoints.size - 1).coerceAtLeast(1).toFloat()) * size.width
                                        val y = midY - v * (size.height * 0.30f)
                                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(path, accentColor, style = Stroke(2.5f))
                                    if (spectrumBins.isNotEmpty()) {
                                        val bw = size.width / spectrumBins.size
                                        spectrumBins.forEachIndexed { idx, mag ->
                                            val bh = mag * (size.height * 0.42f)
                                            drawRect(
                                                color = okColor.copy(alpha = 0.72f),
                                                topLeft = Offset(idx * bw + 3f, size.height - bh),
                                                size = Size((bw - 6f).coerceAtLeast(2f), bh)
                                            )
                                        }
                                    }
                                }
                            }
                            PixelStamp(
                                text = when {
                                    !tunerActive -> "MIC IDLE"
                                    detectedHz <= 0.0 -> "LISTENING FOR NOTE…"
                                    kotlin.math.abs(detectedCents) <= 8 -> "IN TUNE ✓"
                                    detectedCents < 0 -> "TUNE UP (FLAT)"
                                    else -> "TUNE DOWN (SHARP)"
                                },
                                color = if (kotlin.math.abs(detectedCents) <= 8 && detectedHz > 0) palette.ok else palette.chapterColor(hue)
                            )
                            PixelButton(
                                text = if (tunerActive) "Stop Tuner" else "Listen to Pitch",
                                variant = if (tunerActive) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.SOLID,
                                chapterHue = hue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { tunerActive = !tunerActive }
                            )
                        }
                    }
                }
            }

            "med-metronome" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DotMatrixReadout("$metroBpm BPM", "TIME SIGNATURE $metroBeatsPerBar/4", hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (b in 1..metroBeatsPerBar) {
                                val lit = metroRunning && metroCurrentBeat == b
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(22.dp)
                                        .clip(CircleShape)
                                        .background(if (lit) (if (b == 1) palette.err else palette.chapterColor(hue)) else palette.paper3)
                                )
                            }
                        }
                        PixelSteppedSlider("Tempo", metroBpm, 30..240, 2, " BPM", hue, { metroBpm = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2, 3, 4, 6).forEach { b ->
                                PixelChip("$b/4", selected = metroBeatsPerBar == b, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { metroBeatsPerBar = b })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(if (metroRunning) "Stop" else "Start", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { metroRunning = !metroRunning })
                            PixelButton("Tap Tempo", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                val now = System.currentTimeMillis()
                                val recent = if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2500) emptyList() else tapTimes
                                val updated = (recent + now).takeLast(6)
                                tapTimes = updated
                                if (updated.size >= 2) {
                                    val avgDiff = updated.zipWithNext { a, b -> b - a }.average()
                                    if (avgDiff > 0) metroBpm = (60_000.0 / avgDiff).roundToInt().coerceIn(30, 240)
                                }
                            })
                        }
                    }
                }
            }

            "med-noise" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("WHITE" to "White", "PINK" to "Pink", "BROWN" to "Brown", "BINAURAL" to "Binaural", "ISOCHRONIC" to "Isochronic").forEach { (id, lbl) ->
                                PixelChip(lbl, selected = noiseType == id, chapterHue = hue, onClick = { noiseType = id })
                            }
                        }
                        if (noiseType == "BINAURAL" || noiseType == "ISOCHRONIC") {
                            PixelSteppedSlider("Carrier Base", binauralBaseHz, 80..600, 10, " Hz", hue, { binauralBaseHz = it })
                            PixelSteppedSlider("Beat Frequency", binauralBeatHz, 1..40, 1, " Hz", hue, { binauralBeatHz = it })
                            val bandTag = when {
                                binauralBeatHz <= 4 -> "Delta (Deep Sleep)"
                                binauralBeatHz <= 8 -> "Theta (Meditation)"
                                binauralBeatHz <= 13 -> "Alpha (Relaxed Focus)"
                                binauralBeatHz <= 30 -> "Beta (Alert)"
                                else -> "Gamma (Cognition)"
                            }
                            Text("${if (noiseType == "BINAURAL") "Stereo L=${binauralBaseHz}Hz / R=${binauralBaseHz + binauralBeatHz}Hz" else "Pulse ${binauralBaseHz}Hz @ ${binauralBeatHz}Hz"} · $bandTag", style = DotkitType.monoData(11), color = palette.ink2)
                        }
                        PixelSteppedSlider("Auto-Stop Timer (0 = Continuous)", noiseMinutes, 0..60, 5, " min", hue, { noiseMinutes = it })
                        DotMatrixReadout(
                            value = if (!noisePlaying) "STANDBY" else if (noiseRemainSec > 0) "%02d:%02d".format(noiseRemainSec / 60, noiseRemainSec % 60) else "$noiseType ON",
                            label = if (noiseType == "BINAURAL" || noiseType == "ISOCHRONIC") "STEREO ENTRAINMENT SYNTH" else "REAL-TIME AMBIENT NOISE GENERATOR",
                            chapterHue = hue
                        )
                        PixelButton(
                            text = if (noisePlaying) "Stop Audio" else "Play $noiseType",
                            variant = if (noisePlaying) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.SOLID,
                            chapterHue = hue,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { noisePlaying = !noisePlaying }
                        )
                    }
                }
            }

            "med-dtmf" -> {
                val keys = listOf("1", "2", "3", "A", "4", "5", "6", "B", "7", "8", "9", "C", "*", "0", "#", "D")
                val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val chordIntervals = mapOf(
                    "Major" to listOf(0, 4, 7),
                    "Minor" to listOf(0, 3, 7),
                    "Dom7" to listOf(0, 4, 7, 10),
                    "Maj7" to listOf(0, 4, 7, 11),
                    "Pentatonic" to listOf(0, 2, 4, 7, 9)
                )
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip("DTMF Keypad", selected = dtmfMode == "DTMF", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { dtmfMode = "DTMF" })
                            PixelChip("Piano & Chords", selected = dtmfMode == "PIANO", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { dtmfMode = "PIANO" })
                        }
                        if (dtmfMode == "DTMF") {
                            DotMatrixReadout(dtmfSequence.ifBlank { "--" }, "LAST KEY: $dtmfLastKey", hue)
                            for (row in 0 until 4) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (col in 0 until 4) {
                                        val k = keys[row * 4 + col]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1.4f)
                                                .clip(SwissControlShape)
                                                .background(palette.paper)
                                                .border(1.dp, palette.hairline, SwissControlShape)
                                                .clickable {
                                                    dtmfLastKey = k
                                                    dtmfSequence = (dtmfSequence + k).takeLast(18)
                                                    scope.launch(Dispatchers.IO) { playDtmfTone(k[0], 160) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(k, style = DotkitType.monoData(18), color = palette.ink)
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("Dial Sequence", chapterHue = hue, enabled = dtmfSequence.isNotEmpty(), modifier = Modifier.weight(1f), onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        dtmfSequence.forEach { ch -> playDtmfTone(ch, 140); delay(60L) }
                                    }
                                })
                                PixelButton("Clear", variant = PixelButtonVariant.OUTLINE, onClick = { dtmfSequence = ""; dtmfLastKey = "-" })
                            }
                        } else {
                            Text("1-OCTAVE SYNTH PIANO (C4–C5)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (semi in 0..12) {
                                    val label = "${noteNames[semi % 12]}${if (semi == 12) 5 else 4}"
                                    val isSharp = label.contains("#")
                                    val freq = 261.63 * 2.0.pow(semi / 12.0)
                                    Box(
                                        modifier = Modifier
                                            .clip(SwissControlShape)
                                            .background(if (isSharp) palette.ink else palette.paper)
                                            .border(1.dp, palette.hairline, SwissControlShape)
                                            .clickable { scope.launch(Dispatchers.IO) { playTonePcm(listOf(freq), 280) } }
                                            .padding(horizontal = 10.dp, vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, style = DotkitType.monoData(12), color = if (isSharp) palette.paper else palette.ink)
                                    }
                                }
                            }
                            Text("CHORD & SCALE FINDER", style = DotkitType.monoLabel(10), color = palette.ink2)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                noteNames.forEachIndexed { idx, n ->
                                    PixelChip(n, selected = chordRootIdx == idx, chapterHue = hue, onClick = { chordRootIdx = idx })
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                chordIntervals.keys.forEach { cType ->
                                    PixelChip(cType, selected = chordType == cType, chapterHue = hue, onClick = { chordType = cType })
                                }
                            }
                            val intervals = chordIntervals[chordType] ?: listOf(0, 4, 7)
                            val chordNotes = intervals.map { noteNames[(chordRootIdx + it) % 12] }
                            Text("${noteNames[chordRootIdx]} $chordType: ${chordNotes.joinToString(" – ")}", style = DotkitType.monoData(14), color = palette.ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("Play Polyphonic", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    val freqs = intervals.map { semi -> 261.63 * 2.0.pow((chordRootIdx + semi) / 12.0) }
                                    scope.launch(Dispatchers.IO) { playTonePcm(freqs, 600) }
                                })
                                PixelButton("Arpeggiate", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    val freqs = intervals.map { semi -> 261.63 * 2.0.pow((chordRootIdx + semi) / 12.0) }
                                    scope.launch(Dispatchers.IO) {
                                        freqs.forEach { f -> playTonePcm(listOf(f), 200); delay(30L) }
                                    }
                                })
                            }
                        }
                    }
                }
            }

            "med-wavsynth" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelButton("Pick 16-bit PCM WAV File", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
                        })
                        Text(wavFxLabel, style = DotkitType.monoData(12), color = palette.ink2)
                        if (wavOriginalPcm != null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("Original", onClick = { wavProcessedPcm = wavOriginalPcm; wavFxLabel = "Original PCM" })
                                PixelChip("Reverse", chapterHue = hue, onClick = {
                                    wavProcessedPcm = wavOriginalPcm?.reversedArray()
                                    wavFxLabel = "Applied: Reverse"
                                })
                                PixelChip("1.5× Speed", chapterHue = hue, onClick = {
                                    val src = wavOriginalPcm ?: return@PixelChip
                                    val dst = ShortArray((src.size / 1.5).toInt()) { i -> src[(i * 1.5).toInt().coerceAtMost(src.lastIndex)] }
                                    wavProcessedPcm = dst
                                    wavFxLabel = "Applied: 1.5× Speed (${dst.size} samples)"
                                })
                                PixelChip("0.75× Pitch-Down", chapterHue = hue, onClick = {
                                    val src = wavOriginalPcm ?: return@PixelChip
                                    val dst = ShortArray((src.size / 0.75).toInt()) { i -> src[(i * 0.75).toInt().coerceAtMost(src.lastIndex)] }
                                    wavProcessedPcm = dst
                                    wavFxLabel = "Applied: 0.75× Pitch-Down (${dst.size} samples)"
                                })
                                PixelChip("Normalize (-1dB)", chapterHue = hue, onClick = {
                                    val src = wavOriginalPcm ?: return@PixelChip
                                    val peak = src.maxOfOrNull { abs(it.toInt()) }?.coerceAtLeast(1) ?: 1
                                    val gain = (Short.MAX_VALUE * 0.891) / peak
                                    wavProcessedPcm = ShortArray(src.size) { i -> (src[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
                                    wavFxLabel = "Applied: Normalized to -1 dBFS (×${"%.2f".format(gain)})"
                                })
                                PixelChip("Fade In/Out (500ms)", chapterHue = hue, onClick = {
                                    val src = wavOriginalPcm ?: return@PixelChip
                                    val fadeSamples = (wavRate * 0.5).toInt().coerceAtMost(src.size / 2).coerceAtLeast(1)
                                    wavProcessedPcm = ShortArray(src.size) { i ->
                                        val env = when {
                                            i < fadeSamples -> i.toDouble() / fadeSamples
                                            i > src.size - fadeSamples -> (src.size - i).toDouble() / fadeSamples
                                            else -> 1.0
                                        }.coerceIn(0.0, 1.0)
                                        (src[i] * env).toInt().toShort()
                                    }
                                    wavFxLabel = "Applied: 500ms Fade In/Out"
                                })
                                PixelChip("Echo Delay", chapterHue = hue, onClick = {
                                    val src = wavOriginalPcm ?: return@PixelChip
                                    val offset = (wavRate * 0.18).toInt()
                                    val dst = ShortArray(src.size) { i ->
                                        val dry = src[i].toInt()
                                        val wet = if (i >= offset) (src[i - offset] * 0.45f).toInt() else 0
                                        (dry + wet).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                    }
                                    wavProcessedPcm = dst
                                    wavFxLabel = "Applied: 180ms Echo"
                                })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("Play Effect", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    val pcm = wavProcessedPcm ?: return@PixelButton
                                    scope.launch(Dispatchers.IO) { playShortArray(pcm, wavRate) }
                                })
                                PixelButton("Share WAV", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    val pcm = wavProcessedPcm ?: return@PixelButton
                                    val f = File(context.cacheDir, "fx_${System.currentTimeMillis()}.wav")
                                    f.writeBytes(encodeWav16Mono(pcm, wavRate))
                                    wavOutFile = f
                                    ShareMediaUtils.shareFile(context, f, "audio/wav", "Share Processed WAV")
                                })
                            }
                        }
                    }
                }
            }

            else -> { // med-framegrab
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelButton(if (vidUri == null) "Pick Video File" else "Change Video", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        })
                        if (vidUri != null) {
                            Text("Duration: ${vidDurMs} ms (${"%.2f".format(vidDurMs / 1000.0)} s)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PixelNumberField(frameMsStr, { frameMsStr = it }, allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                                PixelButton("Grab Frame", chapterHue = hue, onClick = {
                                    val uri = vidUri ?: return@PixelButton
                                    val ms = (frameMsStr.toLongOrNull() ?: 0L).coerceIn(0L, vidDurMs.coerceAtLeast(1L))
                                    scope.launch(Dispatchers.IO) {
                                        val bmp = grabVideoFrame(context, uri, ms)
                                        withContext(Dispatchers.Main) {
                                            if (bmp != null) grabbedBmp = bmp else onStatusToast("Could not decode frame at ${ms}ms.")
                                        }
                                    }
                                })
                            }
                            grabbedBmp?.let { bmp ->
                                Image(bmp.asImageBitmap(), "Extracted Frame", modifier = Modifier.fillMaxWidth().clip(SwissControlShape).border(1.dp, palette.hairline, SwissControlShape))
                                PixelButton("Share Frame PNG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                    ShareMediaUtils.shareBitmapPng(context, bmp, "video_frame_${frameMsStr}ms")
                                })
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun estimateFundamentalHz(samples: ShortArray, len: Int, sampleRate: Int): Double {
    var rms = 0.0
    for (i in 0 until len) {
        val v = samples[i].toDouble()
        rms += v * v
    }
    rms = kotlin.math.sqrt(rms / len)
    if (rms < 250.0) return 0.0

    val minLag = (sampleRate / 1200).coerceAtLeast(20)
    val maxLag = (sampleRate / 60).coerceAtMost(len / 2)
    var bestLag = -1
    var bestCorr = -1.0
    for (lag in minLag..maxLag) {
        var sum = 0.0
        for (i in 0 until len - lag) {
            sum += samples[i].toDouble() * samples[i + lag].toDouble()
        }
        if (sum > bestCorr) {
            bestCorr = sum
            bestLag = lag
        }
    }
    return if (bestLag > 0) sampleRate.toDouble() / bestLag else 0.0
}

private fun hzToNoteAndCents(hz: Double): Pair<String, Int> {
    val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val midiFloat = 69.0 + 12.0 * (ln(hz / 440.0) / ln(2.0))
    val midi = midiFloat.roundToInt()
    val cents = ((midiFloat - midi) * 100.0).roundToInt()
    val name = notes[((midi % 12) + 12) % 12] + ((midi / 12) - 1)
    return name to cents
}

private fun buildPcmTrack(sampleRate: Int, bufferSamples: Int, stereo: Boolean = false): AudioTrack {
    val chMask = if (stereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val minBytes = AudioTrack.getMinBufferSize(sampleRate, chMask, AudioFormat.ENCODING_PCM_16BIT)
    return AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(chMask).build())
        .setBufferSizeInBytes((bufferSamples * 2).coerceAtLeast(minBytes))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
}

private fun playTonePcm(freqs: List<Double>, ms: Int) {
    if (freqs.isEmpty()) return
    val sr = 22050
    val count = (sr * ms / 1000).coerceAtLeast(250)
    val norm = 0.45 / freqs.size
    val pcm = ShortArray(count) { i ->
        val env = when {
            i < sr / 80 -> i.toDouble() / (sr / 80)
            i > count - sr / 30 -> ((count - i).toDouble() / (sr / 30)).coerceAtLeast(0.0)
            else -> 1.0
        }
        val sum = freqs.sumOf { f -> sin(2.0 * PI * f * i / sr) }
        (sum * norm * env * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    playShortArray(pcm, sr)
}

private fun playClickPcm(freqHz: Double, ms: Int) {
    val sr = 44100
    val count = (sr * ms / 1000).coerceAtLeast(100)
    val pcm = ShortArray(count) { i ->
        val env = 1.0 - (i.toDouble() / count)
        (sin(2.0 * PI * freqHz * i / sr) * env * 0.7 * Short.MAX_VALUE).toInt().toShort()
    }
    playShortArray(pcm, sr)
}

private fun playDtmfTone(ch: Char, ms: Int) {
    val (rHz, cHz) = when (ch.uppercaseChar()) {
        '1' -> 697.0 to 1209.0; '2' -> 697.0 to 1336.0; '3' -> 697.0 to 1477.0; 'A' -> 697.0 to 1633.0
        '4' -> 770.0 to 1209.0; '5' -> 770.0 to 1336.0; '6' -> 770.0 to 1477.0; 'B' -> 770.0 to 1633.0
        '7' -> 852.0 to 1209.0; '8' -> 852.0 to 1336.0; '9' -> 852.0 to 1477.0; 'C' -> 852.0 to 1633.0
        '*' -> 941.0 to 1209.0; '0' -> 941.0 to 1336.0; '#' -> 941.0 to 1477.0; 'D' -> 941.0 to 1633.0
        else -> return
    }
    val sr = 22050
    val count = (sr * ms / 1000).coerceAtLeast(200)
    val pcm = ShortArray(count) { i ->
        val v = 0.35 * (sin(2.0 * PI * rHz * i / sr) + sin(2.0 * PI * cHz * i / sr))
        (v * Short.MAX_VALUE).toInt().toShort()
    }
    playShortArray(pcm, sr)
}

private fun playShortArray(pcm: ShortArray, sampleRate: Int) {
    if (pcm.isEmpty()) return
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes((pcm.size * 2).coerceAtLeast(2048))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    runCatching {
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(((pcm.size * 1000L) / sampleRate).coerceAtLeast(40L))
    }
    runCatching { track.stop(); track.release() }
}

private fun parseWavMono16(bytes: ByteArray?): Pair<Int, ShortArray>? {
    if (bytes == null || bytes.size < 44) return null
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val sr = bb.getInt(24).coerceIn(8000, 96000)
    val channels = bb.getShort(22).toInt().coerceAtLeast(1)
    val dataStart = 44
    val sampleCount = ((bytes.size - dataStart) / (2 * channels)).coerceAtMost(sr * 15)
    if (sampleCount <= 0) return null
    val out = ShortArray(sampleCount)
    for (i in 0 until sampleCount) {
        out[i] = bb.getShort(dataStart + i * 2 * channels)
    }
    return sr to out
}

private fun encodeWav16Mono(pcm: ShortArray, sampleRate: Int): ByteArray {
    val baos = ByteArrayOutputStream(44 + pcm.size * 2)
    val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    val dataBytes = pcm.size * 2
    hdr.put("RIFF".toByteArray())
    hdr.putInt(36 + dataBytes)
    hdr.put("WAVE".toByteArray())
    hdr.put("fmt ".toByteArray())
    hdr.putInt(16)
    hdr.putShort(1)
    hdr.putShort(1)
    hdr.putInt(sampleRate)
    hdr.putInt(sampleRate * 2)
    hdr.putShort(2)
    hdr.putShort(16)
    hdr.put("data".toByteArray())
    hdr.putInt(dataBytes)
    baos.write(hdr.array())
    val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    pcm.forEach { body.putShort(it) }
    baos.write(body.array())
    return baos.toByteArray()
}

private fun grabVideoFrame(context: android.content.Context, uri: Uri, ms: Long): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(context, uri)
        mmr.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { mmr.release() }
    }
}
