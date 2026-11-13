package com.example.tools.benchmark

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun BenchmarkMoreScreen(
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
            "bench-matrix" -> {
                var level by remember { mutableIntStateOf(1) }
                var mistakes by remember { mutableIntStateOf(0) }
                var running by remember { mutableStateOf(false) }
                var timeLeftSec by remember { mutableIntStateOf(30) }
                val side = when {
                    level <= 3 -> 3
                    level <= 8 -> 4
                    else -> 5
                }
                var baseShape by remember { mutableIntStateOf(Random.nextInt(4)) } // 0=circle,1=square,2=diamond,3=triangle
                var oddShape by remember { mutableIntStateOf((baseShape + 1) % 4) }
                var oddIdx by remember { mutableIntStateOf(Random.nextInt(9)) }
                var baseAngle by remember { mutableIntStateOf(0) }

                LaunchedEffect(running) {
                    while (running && timeLeftSec > 0) {
                        delay(1000L)
                        timeLeftSec--
                    }
                    if (running && timeLeftSec == 0) {
                        running = false
                        onRecordScore("bench-matrix", "Lvl $level ($mistakes err)", level.toDouble(), true)
                        onStatusToast("Finished at Pattern Level $level!")
                    }
                }

                fun genNext(nextLvl: Int) {
                    val s = when {
                        nextLvl <= 3 -> 3
                        nextLvl <= 8 -> 4
                        else -> 5
                    }
                    val sh = Random.nextInt(4)
                    baseShape = sh
                    oddShape = (sh + 1 + Random.nextInt(3)) % 4
                    oddIdx = Random.nextInt(s * s)
                    baseAngle = Random.nextInt(4) * 90
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("LVL $level", "TIME: ${timeLeftSec}s · ERR: $mistakes", hue)
                            PixelStamp(if (running) "${side}×${side}" else "READY", palette.chapterColor(hue))
                        }
                        if (!running) {
                            PixelButton("Start 30s Matrix Sprint", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                level = 1; mistakes = 0; timeLeftSec = 30; genNext(1); running = true
                            })
                        } else {
                            val fg = palette.chapterColor(hue)
                            for (r in 0 until side) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (c in 0 until side) {
                                        val idx = r * side + c
                                        val shapeKind = if (idx == oddIdx) oddShape else baseShape
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(SwissControlShape)
                                                .background(palette.paper)
                                                .border(1.dp, palette.hairline, SwissControlShape)
                                                .clickable {
                                                    if (idx == oddIdx) {
                                                        level++
                                                        genNext(level)
                                                    } else {
                                                        mistakes++
                                                        timeLeftSec = (timeLeftSec - 2).coerceAtLeast(0)
                                                    }
                                                }
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                                val pad = size.minDimension * 0.16f
                                                val s = size.minDimension - pad * 2f
                                                when (shapeKind) {
                                                    0 -> drawCircle(fg, radius = s / 2f, style = Stroke(width = 4f))
                                                    1 -> drawRect(fg, topLeft = Offset(pad, pad), size = Size(s, s), style = Stroke(width = 4f))
                                                    2 -> {
                                                        val p = Path().apply {
                                                            moveTo(size.width / 2f, pad)
                                                            lineTo(size.width - pad, size.height / 2f)
                                                            lineTo(size.width / 2f, size.height - pad)
                                                            lineTo(pad, size.height / 2f)
                                                            close()
                                                        }
                                                        drawPath(p, fg, style = Stroke(width = 4f))
                                                    }
                                                    else -> {
                                                        val p = Path().apply {
                                                            moveTo(size.width / 2f, pad)
                                                            lineTo(size.width - pad, size.height - pad)
                                                            lineTo(pad, size.height - pad)
                                                            close()
                                                        }
                                                        drawPath(p, fg, style = Stroke(width = 4f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "bench-readwpm" -> {
                data class Passage(val title: String, val body: String, val q: String, val opts: List<String>, val correct: Int)
                val passages = remember {
                    listOf(
                        Passage(
                            "Swiss Typographic Grids",
                            "Josef Muller-Brockmann formalized modular editorial grids in post-war Zurich to organize dense technical print without ornament. Aligning headlines, tabular figures, and body columns along shared mathematical intervals improves scanning speed and keeps readers oriented across complex manuals.",
                            "Where were modular editorial grids formalized according to the passage?",
                            listOf("Vienna", "Zurich", "Basel", "Berlin"),
                            1
                        ),
                        Passage(
                            "Acoustic Interference & Tuning",
                            "When two pure sine waves with slightly different frequencies sound simultaneously, the human ear perceives periodic amplitude pulsing known as beats. Piano tuners listen carefully to the beat rate between strings and adjust tension until the pulsing disappears.",
                            "What do piano tuners listen for between two strings?",
                            listOf("Harmonic distortion", "Echo delay", "Beat rate", "White noise"),
                            2
                        )
                    )
                }
                var pIdx by remember { mutableIntStateOf(0) }
                var stage by remember { mutableIntStateOf(0) } // 0=idle, 1=reading, 2=quiz, 3=done
                var startNs by remember { mutableLongStateOf(0L) }
                var readSec by remember { mutableStateOf(0.0) }
                var rawWpm by remember { mutableIntStateOf(0) }
                var quizCorrect by remember { mutableStateOf(false) }

                val p = passages[pIdx]
                val wordCount = remember(p) { p.body.split(Regex("\\s+")).size }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${p.title.uppercase()} ($wordCount WORDS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        when (stage) {
                            0 -> {
                                Text("Tap Start Reading, read at your natural comprehension pace, then tap Finished Reading to verify retention.", style = DotkitType.bodyUi(13), color = palette.ink2)
                                PixelButton("Start Reading", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                    startNs = System.nanoTime()
                                    stage = 1
                                })
                            }
                            1 -> {
                                Text(p.body, style = DotkitType.bodyUi(15), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(12.dp))
                                PixelButton("Finished Reading -> Quiz", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                    val sec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(1.0)
                                    readSec = sec
                                    rawWpm = ((wordCount / sec) * 60.0).toInt()
                                    stage = 2
                                })
                            }
                            2 -> {
                                Text("COMPREHENSION CHECK", style = DotkitType.monoLabel(10), color = palette.chapterColor(hue))
                                Text(p.q, style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                                p.opts.forEachIndexed { idx, opt ->
                                    PixelButton(opt, variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                        quizCorrect = (idx == p.correct)
                                        val effectiveWpm = if (quizCorrect) rawWpm else (rawWpm * 0.5).toInt()
                                        onRecordScore("bench-readwpm", "$effectiveWpm WPM (${if (quizCorrect) "Pass" else "Miss"})", effectiveWpm.toDouble(), true)
                                        stage = 3
                                    })
                                }
                            }
                            else -> {
                                val effectiveWpm = if (quizCorrect) rawWpm else (rawWpm * 0.5).toInt()
                                DotMatrixReadout("$effectiveWpm WPM", if (quizCorrect) "100% COMPREHENSION · ${"%.1f".format(readSec)}s" else "QUIZ MISSED (50% PENALTY) · ${"%.1f".format(readSec)}s", hue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelButton("Next Passage", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                        pIdx = (pIdx + 1) % passages.size
                                        stage = 0
                                    })
                                    PixelButton("Retry Same", variant = PixelButtonVariant.OUTLINE, modifier = Modifier.weight(1f), onClick = { stage = 0 })
                                }
                            }
                        }
                    }
                }
            }

            else -> { // bench-gonogo
                val totalTrials = 16
                var state by remember { mutableIntStateOf(0) } // 0=idle, 1=wait_blank, 2=cue, 3=done
                var trial by remember { mutableIntStateOf(0) }
                var isGoCue by remember { mutableStateOf(true) }
                var cueStartMs by remember { mutableLongStateOf(0L) }
                var goRts by remember { mutableStateOf<List<Long>>(emptyList()) }
                var falseAlarms by remember { mutableIntStateOf(0) }
                var misses by remember { mutableIntStateOf(0) }

                LaunchedEffect(state, trial) {
                    if (state == 1) {
                        delay(Random.nextLong(550L, 1250L))
                        isGoCue = Random.nextFloat() < 0.72f
                        cueStartMs = System.currentTimeMillis()
                        state = 2
                    } else if (state == 2) {
                        delay(1100L)
                        if (state == 2) {
                            if (isGoCue) misses++
                            if (trial + 1 >= totalTrials) {
                                state = 3
                                val avg = if (goRts.isNotEmpty()) goRts.average() else 999.0
                                onRecordScore("bench-gonogo", "${avg.toInt()}ms (${falseAlarms} FA)", avg, false)
                            } else {
                                trial++
                                state = 1
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TRIAL ${trial.coerceAtMost(totalTrials)}/$totalTrials", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text("FALSE ALARMS: $falseAlarms · MISSES: $misses", style = DotkitType.monoLabel(10), color = palette.ink2)
                        }
                        PixelProgressBar(trial / totalTrials.toFloat(), hue)

                        val boxBg = when (state) {
                            2 -> if (isGoCue) Color(0xFF16A34A) else Color(0xFFDC2626)
                            else -> palette.paper
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(boxBg)
                                .border(2.dp, palette.ink, RoundedCornerShape(12.dp))
                                .clickable(enabled = state == 2) {
                                    val rt = (System.currentTimeMillis() - cueStartMs).coerceAtLeast(1L)
                                    if (isGoCue) goRts = goRts + rt else falseAlarms++
                                    if (trial + 1 >= totalTrials) {
                                        state = 3
                                        val finalRts = if (isGoCue) goRts else goRts
                                        val avg = if (finalRts.isNotEmpty()) finalRts.average() else 999.0
                                        onRecordScore("bench-gonogo", "${avg.toInt()}ms (${falseAlarms} FA)", avg, false)
                                    } else {
                                        trial++
                                        state = 1
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (state) {
                                    0 -> "GO ON GREEN • HOLD ON RED"
                                    1 -> "WAIT..."
                                    2 -> if (isGoCue) "GO! TAP NOW!" else "NO-GO! DO NOT TAP!"
                                    else -> "AVG ${if (goRts.isNotEmpty()) goRts.average().toInt() else 0} ms"
                                },
                                style = DotkitType.readoutDoto(22),
                                color = if (state == 2) Color.White else palette.ink
                            )
                        }

                        if (state == 0 || state == 3) {
                            PixelButton(
                                text = if (state == 0) "Start Impulse Test" else "Run Again",
                                chapterHue = hue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    trial = 0; goRts = emptyList(); falseAlarms = 0; misses = 0; state = 1
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
