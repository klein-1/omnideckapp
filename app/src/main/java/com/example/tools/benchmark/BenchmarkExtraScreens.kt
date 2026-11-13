package com.example.tools.benchmark

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.example.design.SwissControlShape
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.BenchmarkScoreEntity
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BenchmarkExtraScreen(
    meta: ToolMeta,
    benchmarkScores: List<BenchmarkScoreEntity>,
    onRecordScore: (testId: String, label: String, numericScore: Double, higherIsBetter: Boolean) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.MARIGOLD
    val existingForTest = remember(benchmarkScores, meta.id) { benchmarkScores.filter { it.testId == meta.id } }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = existingForTest.firstOrNull()?.scoreLabel ?: "",
        outputFileName = "${meta.id}_best.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        showOutputBar = false,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        if (existingForTest.isNotEmpty()) {
            PixelBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT SCORES", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Text(existingForTest.take(3).joinToString(" · ") { it.scoreLabel }, style = DotkitType.monoData(12), color = palette.ink)
                }
            }
        }

        when (meta.id) {
            "bench-color" -> {
                var level by remember { mutableIntStateOf(1) }
                var baseHue by remember { mutableStateOf(Random.nextFloat() * 360f) }
                var baseSat by remember { mutableStateOf(0.68f) }
                var baseLit by remember { mutableStateOf(0.52f) }
                val side = when {
                    level <= 2 -> 2
                    level <= 5 -> 3
                    level <= 9 -> 4
                    level <= 14 -> 5
                    else -> 6
                }
                var oddIdx by remember { mutableIntStateOf(Random.nextInt(4)) }
                val deltaLit = (0.18f - (level * 0.01f)).coerceAtLeast(0.025f)
                val baseColor = Color.hsl(baseHue, baseSat, baseLit)
                val oddColor = Color.hsl(baseHue, baseSat, (if (baseLit > 0.5f) baseLit - deltaLit else baseLit + deltaLit).coerceIn(0.08f, 0.92f))

                fun nextRound(nextLvl: Int) {
                    val nextSide = when {
                        nextLvl <= 2 -> 2
                        nextLvl <= 5 -> 3
                        nextLvl <= 9 -> 4
                        nextLvl <= 14 -> 5
                        else -> 6
                    }
                    level = nextLvl
                    baseHue = Random.nextFloat() * 360f
                    baseSat = 0.55f + Random.nextFloat() * 0.3f
                    baseLit = 0.35f + Random.nextFloat() * 0.3f
                    oddIdx = Random.nextInt(nextSide * nextSide)
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("LVL $level", "${side}×$side GRID · ΔL ${(deltaLit * 100).toInt()}%", hue)
                            PixelChip("Reset", onClick = { nextRound(1) })
                        }
                        for (r in 0 until side) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (c in 0 until side) {
                                    val idx = r * side + c
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(SwissControlShape)
                                            .background(if (idx == oddIdx) oddColor else baseColor)
                                            .clickable {
                                                if (idx == oddIdx) {
                                                    nextRound(level + 1)
                                                } else {
                                                    onRecordScore("bench-color", "Level $level", level.toDouble(), true)
                                                    onStatusToast("Missed on Level $level")
                                                    nextRound(1)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "bench-stroop" -> {
                val colorItems = remember {
                    listOf(
                        "RED" to Color(0xFFE60026),
                        "BLUE" to Color(0xFF0052FF),
                        "GREEN" to Color(0xFF0E9F6E),
                        "GOLD" to Color(0xFFD99400)
                    )
                }
                var active by remember { mutableStateOf(false) }
                var trial by remember { mutableIntStateOf(0) }
                var hits by remember { mutableIntStateOf(0) }
                var rts by remember { mutableStateOf<List<Long>>(emptyList()) }
                var wordName by remember { mutableStateOf("RED") }
                var inkName by remember { mutableStateOf("BLUE") }
                var trialStartNano by remember { mutableLongStateOf(0L) }

                fun nextTrial() {
                    val w = colorItems.random().first
                    val i = if (Random.nextFloat() < 0.78f) colorItems.filter { it.first != w }.random().first else w
                    wordName = w; inkName = i; trialStartNano = System.nanoTime()
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val avgMs = if (rts.isNotEmpty()) rts.average().toInt() else 0
                        DotMatrixReadout(
                            value = if (active) "${trial + 1}/12" else "${avgMs}ms",
                            label = "TAP THE INK COLOR (IGNORE THE WORD TEXT) · HITS $hits",
                            chapterHue = hue
                        )
                        if (!active) {
                            PixelButton("Start 12-Trial Stroop Test", chapterHue = hue, onClick = {
                                trial = 0; hits = 0; rts = emptyList(); active = true; nextTrial()
                            }, modifier = Modifier.fillMaxWidth())
                        } else {
                            val inkCol = colorItems.first { it.first == inkName }.second
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.paper3)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(wordName, style = DotkitType.displayPixel(44), color = inkCol)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                colorItems.forEach { (name, _) ->
                                    PixelButton(name, variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                        val ms = (System.nanoTime() - trialStartNano) / 1_000_000L
                                        if (name == inkName) hits++
                                        val nextRts = rts + ms
                                        rts = nextRts
                                        if (trial >= 11) {
                                            active = false
                                            val finalAvg = nextRts.average()
                                            val acc = (hits * 100) / 12
                                            onRecordScore("bench-stroop", "${finalAvg.toInt()}ms ($acc%)", finalAvg, false)
                                        } else {
                                            trial++
                                            nextTrial()
                                        }
                                    }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            "bench-audio" -> {
                var stage by remember { mutableStateOf("IDLE") } // IDLE, WAIT, TONE, TOO_SOON, RESULT, DONE
                var cueNano by remember { mutableLongStateOf(0L) }
                var results by remember { mutableStateOf<List<Long>>(emptyList()) }

                fun triggerAudioBeep() {
                    scope.launch(Dispatchers.Default) {
                        runCatching {
                            val sr = 44100
                            val count = sr / 6
                            val pcm = ShortArray(count) { i ->
                                (sin(2.0 * PI * 1000.0 * i / sr) * 0.55 * Short.MAX_VALUE).toInt().toShort()
                            }
                            val track = AudioTrack.Builder()
                                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                                .setBufferSizeInBytes(pcm.size * 2)
                                .setTransferMode(AudioTrack.MODE_STATIC)
                                .build()
                            track.write(pcm, 0, pcm.size)
                            track.play()
                            delay(220L)
                            track.release()
                        }
                    }
                }

                LaunchedEffect(stage) {
                    if (stage == "WAIT") {
                        delay(Random.nextLong(1500L, 4200L))
                        if (stage == "WAIT") {
                            triggerAudioBeep()
                            cueNano = System.nanoTime()
                            stage = "TONE"
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelStamp("AUDIO-ONLY REFLEX · NO VISUAL CUE ON BEEP", palette.chapterColor(hue))
                        val label = when (stage) {
                            "IDLE" -> "Tap to Start 5-Round Auditory Test"
                            "WAIT" -> "LISTEN CLOSELY..."
                            "TONE" -> "BEEP! TAP NOW!"
                            "TOO_SOON" -> "Too soon! Tap to retry"
                            "RESULT" -> "${results.lastOrNull() ?: 0} ms · Tap for Round ${results.size + 1}/5"
                            else -> "Avg ${if (results.isNotEmpty()) results.average().toInt() else 0} ms · Tap to Restart"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (stage == "WAIT" || stage == "TONE") palette.paper3 else palette.paper2)
                                .border(1.5.dp, palette.hairline, RoundedCornerShape(14.dp))
                                .clickable {
                                    when (stage) {
                                        "IDLE", "TOO_SOON", "RESULT" -> stage = "WAIT"
                                        "WAIT" -> stage = "TOO_SOON"
                                        "TONE" -> {
                                            val ms = (System.nanoTime() - cueNano) / 1_000_000L
                                            val next = results + ms
                                            results = next
                                            if (next.size >= 5) {
                                                val avg = next.average()
                                                onRecordScore("bench-audio", "${avg.toInt()} ms audio", avg, false)
                                                stage = "DONE"
                                            } else stage = "RESULT"
                                        }
                                        "DONE" -> { results = emptyList(); stage = "WAIT" }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (stage == "TONE") "LISTEN..." else label, style = DotkitType.editorialHeading(18), color = palette.ink)
                        }
                        if (results.isNotEmpty()) {
                            Text("Rounds: ${results.joinToString(" ms, ")} ms", style = DotkitType.monoData(12), color = palette.ink2)
                        }
                    }
                }
            }

            else -> { // bench-cps
                var durationSec by remember { mutableIntStateOf(5) }
                var running by remember { mutableStateOf(false) }
                var finished by remember { mutableStateOf(false) }
                var taps by remember { mutableIntStateOf(0) }
                var msLeft by remember { mutableLongStateOf(5000L) }
                var startMs by remember { mutableLongStateOf(0L) }

                LaunchedEffect(running) {
                    while (running) {
                        val elapsed = System.currentTimeMillis() - startMs
                        val rem = (durationSec * 1000L - elapsed).coerceAtLeast(0L)
                        msLeft = rem
                        if (rem <= 0L) {
                            running = false
                            finished = true
                            val cps = taps.toDouble() / durationSec
                            onRecordScore("bench-cps", "${"%.2f".format(cps)} CPS (${durationSec}s)", cps, true)
                            break
                        }
                        delay(30L)
                    }
                }

                val liveCps = if (running) {
                    val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.2)
                    taps / elapsedSec
                } else if (finished) taps.toDouble() / durationSec else 0.0

                val tier = when {
                    liveCps >= 12.0 -> "ELITE JITTER"
                    liveCps >= 8.5 -> "PRO GAMER"
                    liveCps >= 6.0 -> "FAST"
                    liveCps > 0.0 -> "CASUAL"
                    else -> "READY"
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 5, 10).forEach { s ->
                                PixelChip("${s}s Sprint", selected = durationSec == s, chapterHue = hue, onClick = {
                                    if (!running) { durationSec = s; msLeft = s * 1000L; taps = 0; finished = false }
                                })
                            }
                        }
                        DotMatrixReadout("${"%.2f".format(liveCps)} CPS", "TAPS $taps · ${(msLeft / 1000.0).let { "%.1f".format(it) }}s LEFT · $tier", hue)
                        PixelProgressBar(progress = (1f - (msLeft.toFloat() / (durationSec * 1000f))).coerceIn(0f, 1f), chapterHue = hue)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.chapterColor(hue))
                                .clickable {
                                    if (!running) {
                                        taps = 1
                                        startMs = System.currentTimeMillis()
                                        msLeft = durationSec * 1000L
                                        finished = false
                                        running = true
                                    } else {
                                        taps++
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    running -> "TAP FAST! ($taps)"
                                    finished -> "DONE! TAP TO RETRY"
                                    else -> "TAP HERE TO START ${durationSec}S SPRINT"
                                },
                                style = DotkitType.editorialHeading(20),
                                color = hue.onColor
                            )
                        }
                    }
                }
            }
        }
    }
}
