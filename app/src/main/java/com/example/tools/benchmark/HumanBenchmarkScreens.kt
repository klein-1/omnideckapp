package com.example.tools.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
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
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.BenchmarkScoreEntity
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun HumanBenchmarkScreens(
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
            "bench-reaction" -> {
                var stage by remember { mutableStateOf("IDLE") } // IDLE, WAITING, GO, TOO_SOON, RESULT, DONE
                var goNano by remember { mutableLongStateOf(0L) }
                var results by remember { mutableStateOf<List<Long>>(emptyList()) }

                LaunchedEffect(stage) {
                    if (stage == "WAITING") {
                        delay(Random.nextLong(1400L, 4200L))
                        if (stage == "WAITING") {
                            goNano = System.nanoTime()
                            stage = "GO"
                        }
                    }
                }

                val boxColor = when (stage) {
                    "WAITING" -> palette.err
                    "GO" -> palette.ok
                    else -> palette.paper3
                }
                val title = when (stage) {
                    "IDLE" -> "Tap to Start 5-Round Reaction Test"
                    "WAITING" -> "WAIT FOR GREEN..."
                    "GO" -> "TAP NOW!"
                    "TOO_SOON" -> "Too soon! Tap to retry"
                    "RESULT" -> "${results.lastOrNull() ?: 0} ms · Tap for Round ${results.size + 1}/5"
                    else -> "Avg: ${if (results.isNotEmpty()) results.average().toInt() else 0} ms · Tap to Restart"
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(boxColor)
                                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                .clickable {
                                    when (stage) {
                                        "IDLE", "TOO_SOON", "RESULT" -> stage = "WAITING"
                                        "WAITING" -> stage = "TOO_SOON"
                                        "GO" -> {
                                            val ms = (System.nanoTime() - goNano) / 1_000_000L
                                            val next = results + ms
                                            results = next
                                            if (next.size >= 5) {
                                                val avg = next.average()
                                                onRecordScore("bench-reaction", "${avg.toInt()} ms avg", avg, false)
                                                stage = "DONE"
                                            } else stage = "RESULT"
                                        }
                                        "DONE" -> { results = emptyList(); stage = "WAITING" }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title, style = DotkitType.editorialHeading(20), color = if (stage == "GO" || stage == "WAITING") Color.White else palette.ink)
                        }
                        if (results.isNotEmpty()) {
                            Text("Rounds: ${results.joinToString(" ms, ")} ms", style = DotkitType.monoData(12), color = palette.ink2)
                        }
                    }
                }
            }

            "bench-sequence" -> {
                var sequence by remember { mutableStateOf<List<Int>>(emptyList()) }
                var playerIdx by remember { mutableIntStateOf(0) }
                var showingTile by remember { mutableStateOf<Int?>(null) }
                var accepting by remember { mutableStateOf(false) }
                var level by remember { mutableIntStateOf(0) }
                var roundSeed by remember { mutableIntStateOf(0) }

                LaunchedEffect(roundSeed) {
                    if (roundSeed > 0) {
                        accepting = false
                        delay(450L)
                        for (t in sequence) {
                            showingTile = t
                            delay(420L)
                            showingTile = null
                            delay(160L)
                        }
                        playerIdx = 0
                        accepting = true
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("LVL $level", if (accepting) "YOUR TURN" else "WATCH PATTERN", hue)
                            PixelButton(if (level == 0) "Start" else "Restart", chapterHue = hue, onClick = {
                                level = 1
                                sequence = listOf(Random.nextInt(9))
                                roundSeed++
                            })
                        }
                        for (r in 0 until 3) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (c in 0 until 3) {
                                    val idx = r * 3 + c
                                    val lit = showingTile == idx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (lit) palette.chapterColor(hue) else palette.paper3)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                            .clickable(enabled = accepting) {
                                                if (idx == sequence.getOrNull(playerIdx)) {
                                                    if (playerIdx == sequence.lastIndex) {
                                                        level++
                                                        sequence = sequence + Random.nextInt(9)
                                                        roundSeed++
                                                    } else {
                                                        playerIdx++
                                                    }
                                                } else {
                                                    accepting = false
                                                    onRecordScore("bench-sequence", "Level $level", level.toDouble(), true)
                                                    onStatusToast("Missed on Level $level")
                                                    level = 0
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "bench-aim" -> {
                val totalTargets = 20
                var remaining by remember { mutableIntStateOf(0) }
                var startNano by remember { mutableLongStateOf(0L) }
                var targetNormX by remember { mutableStateOf(0.5f) }
                var targetNormY by remember { mutableStateOf(0.5f) }
                var lastAvgMs by remember { mutableStateOf<Int?>(null) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout(
                            value = if (remaining > 0) "$remaining LEFT" else "${lastAvgMs ?: 0} ms/hit",
                            label = "20-TARGET PRECISION TRAINER",
                            chapterHue = hue
                        )
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(6.dp)).background(palette.paper3).border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                        ) {
                            if (remaining == 0) {
                                PixelButton("Start Aim Test", chapterHue = hue, onClick = {
                                    remaining = totalTargets
                                    startNano = System.nanoTime()
                                    targetNormX = Random.nextFloat().coerceIn(0.1f, 0.85f)
                                    targetNormY = Random.nextFloat().coerceIn(0.1f, 0.85f)
                                }, modifier = Modifier.align(Alignment.Center))
                            } else {
                                val xDp = (maxWidth - 48.dp) * targetNormX
                                val yDp = (maxHeight - 48.dp) * targetNormY
                                Box(
                                    modifier = Modifier
                                        .offset(x = xDp, y = yDp)
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(palette.chapterColor(hue))
                                        .border(2.dp, palette.ink, RoundedCornerShape(24.dp))
                                        .clickable {
                                            if (remaining <= 1) {
                                                val totalMs = (System.nanoTime() - startNano) / 1_000_000L
                                                val avg = (totalMs / totalTargets).toInt()
                                                lastAvgMs = avg
                                                remaining = 0
                                                onRecordScore("bench-aim", "$avg ms/hit", avg.toDouble(), false)
                                            } else {
                                                remaining--
                                                targetNormX = Random.nextFloat().coerceIn(0.05f, 0.95f)
                                                targetNormY = Random.nextFloat().coerceIn(0.05f, 0.95f)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            "bench-number" -> {
                var level by remember { mutableIntStateOf(1) }
                var shownNumber by remember { mutableStateOf("") }
                var phase by remember { mutableStateOf("IDLE") } // IDLE, SHOW, INPUT, WRONG
                var guess by remember { mutableStateOf("") }

                LaunchedEffect(phase, level) {
                    if (phase == "SHOW") {
                        delay(1100L + level * 350L)
                        phase = "INPUT"
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (phase) {
                            "IDLE" -> {
                                PixelButton("Start Number Memory", chapterHue = hue, onClick = {
                                    level = 1; shownNumber = Random.nextInt(1, 10).toString(); guess = ""; phase = "SHOW"
                                }, modifier = Modifier.fillMaxWidth())
                            }
                            "SHOW" -> DotMatrixReadout(shownNumber, "MEMORIZE $level DIGIT(S)", hue)
                            "INPUT" -> {
                                PixelTextField(guess, { guess = it.filter(Char::isDigit) }, "Enter the exact digits", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                PixelButton("Submit", chapterHue = hue, onClick = {
                                    if (guess == shownNumber) {
                                        level++
                                        shownNumber = buildString {
                                            append(Random.nextInt(1, 10))
                                            repeat(level - 1) { append(Random.nextInt(0, 10)) }
                                        }
                                        guess = ""; phase = "SHOW"
                                    } else {
                                        onRecordScore("bench-number", "$level digits", (level - 1).toDouble(), true)
                                        phase = "WRONG"
                                    }
                                }, modifier = Modifier.fillMaxWidth())
                            }
                            else -> {
                                PixelStamp("Expected $shownNumber · You typed $guess", palette.err)
                                PixelButton("Try Again (Reached Level $level)", chapterHue = hue, onClick = {
                                    level = 1; shownNumber = Random.nextInt(1, 10).toString(); guess = ""; phase = "SHOW"
                                }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            "bench-verbal" -> {
                val wordBank = remember {
                    listOf("anchor", "beacon", "cipher", "delta", "ember", "falcon", "glyph", "harbor", "ivory", "jasper", "kinetic", "lantern", "matrix", "nebula", "orbit", "prism", "quartz", "radar", "summit", "timber", "umbra", "vector", "zenith", "basalt", "cobalt", "dynamo", "fjord", "granite", "helium", "indigo")
                }
                var seen by remember { mutableStateOf(setOf<String>()) }
                var currentWord by remember { mutableStateOf(wordBank.random()) }
                var score by remember { mutableIntStateOf(0) }
                var lives by remember { mutableIntStateOf(3) }

                fun answer(choseSeen: Boolean) {
                    val wasSeen = currentWord in seen
                    if (choseSeen == wasSeen) {
                        score++
                    } else {
                        lives--
                        if (lives <= 0) onRecordScore("bench-verbal", "$score words", score.toDouble(), true)
                    }
                    seen = seen + currentWord
                    currentWord = if (seen.isNotEmpty() && Random.nextFloat() < 0.45f) seen.random() else wordBank.random()
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PixelStamp("LIVES: $lives", if (lives <= 1) palette.err else palette.ok)
                            Text("SCORE: $score", style = DotkitType.monoData(14), color = palette.ink)
                        }
                        if (lives <= 0) {
                            Text("Game Over! Final Score: $score", style = DotkitType.editorialHeading(20), color = palette.ink)
                            PixelButton("Play Again", chapterHue = hue, onClick = {
                                seen = emptySet(); score = 0; lives = 3; currentWord = wordBank.random()
                            }, modifier = Modifier.fillMaxWidth())
                        } else {
                            DotMatrixReadout(currentWord.uppercase(), "HAVE YOU SEEN THIS WORD?", hue)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton("SEEN", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { answer(true) }, modifier = Modifier.weight(1f))
                                PixelButton("NEW", chapterHue = hue, onClick = { answer(false) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            "bench-chimp" -> {
                var count by remember { mutableIntStateOf(4) }
                var positions by remember { mutableStateOf((0 until 16).shuffled().take(4)) }
                var nextExpected by remember { mutableIntStateOf(1) }
                var concealed by remember { mutableStateOf(false) }

                fun resetRound(n: Int) {
                    count = n
                    positions = (0 until 16).shuffled().take(n)
                    nextExpected = 1
                    concealed = false
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Numbers: $count", style = DotkitType.monoData(13), color = palette.ink)
                            PixelChip("Reset", onClick = { resetRound(4) })
                        }
                        for (r in 0 until 4) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0 until 4) {
                                    val cell = r * 4 + c
                                    val numIdx = positions.indexOf(cell)
                                    val num = if (numIdx >= 0) numIdx + 1 else 0
                                    val active = num >= nextExpected
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) palette.chapterColor(hue) else palette.paper3)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                            .clickable(enabled = active) {
                                                if (num == nextExpected) {
                                                    if (num == 1) concealed = true
                                                    if (nextExpected == count) {
                                                        val nextCount = (count + 1).coerceAtMost(12)
                                                        onRecordScore("bench-chimp", "$count numbers", count.toDouble(), true)
                                                        resetRound(nextCount)
                                                    } else nextExpected++
                                                } else {
                                                    onStatusToast("Wrong square! Resetting to 4.")
                                                    resetRound(4)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (active && !concealed) Text("$num", style = DotkitType.monoData(20), color = hue.onColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "bench-visual" -> {
                var level by remember { mutableIntStateOf(1) }
                val gridSide = if (level < 3) 3 else if (level < 6) 4 else 5
                val targetCount = (level + 2).coerceAtMost(gridSide * gridSide - 2)
                var targets by remember { mutableStateOf((0 until gridSide * gridSide).shuffled().take(targetCount).toSet()) }
                var found by remember { mutableStateOf(emptySet<Int>()) }
                var showing by remember { mutableStateOf(true) }

                LaunchedEffect(level, targets) {
                    showing = true
                    delay(1200L)
                    showing = false
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DotMatrixReadout("LVL $level", "TILES: $targetCount (${gridSide}×$gridSide)", hue)
                            PixelChip("Reset", onClick = { level = 1; found = emptySet(); targets = (0 until 9).shuffled().take(3).toSet() })
                        }
                        for (r in 0 until gridSide) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0 until gridSide) {
                                    val idx = r * gridSide + c
                                    val lit = (showing && idx in targets) || idx in found
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (lit) palette.chapterColor(hue) else palette.paper3)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                            .clickable(enabled = !showing && idx !in found) {
                                                if (idx in targets) {
                                                    val nextFound = found + idx
                                                    if (nextFound.size == targets.size) {
                                                        val nextLvl = level + 1
                                                        val nSide = if (nextLvl < 3) 3 else if (nextLvl < 6) 4 else 5
                                                        val nCnt = (nextLvl + 2).coerceAtMost(nSide * nSide - 2)
                                                        level = nextLvl
                                                        found = emptySet()
                                                        targets = (0 until nSide * nSide).shuffled().take(nCnt).toSet()
                                                    } else found = nextFound
                                                } else {
                                                    onRecordScore("bench-visual", "Level $level", level.toDouble(), true)
                                                    onStatusToast("Missed tile on Level $level")
                                                    level = 1; found = emptySet(); targets = (0 until 9).shuffled().take(3).toSet()
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> { // bench-typing
                val passages = remember {
                    listOf(
                        "Clean Swiss typography values clarity, rhythm, alignment, and honest tactile precision across every viewport.",
                        "A dependable pocket instrument turns raw device sensors, network sockets, and cryptography into effortless everyday clarity.",
                        "Fast reliable offline tools keep notes, schedules, documents, and encrypted secrets private on your own hardware."
                    )
                }
                var passage by remember { mutableStateOf(passages.first()) }
                var typed by remember { mutableStateOf("") }
                var startMs by remember { mutableLongStateOf(0L) }
                var finishedWpm by remember { mutableStateOf<Int?>(null) }

                val correctChars = typed.indices.count { i -> i < passage.length && typed[i] == passage[i] }
                val accuracy = if (typed.isEmpty()) 100 else (correctChars * 100) / typed.length
                val elapsedMin = if (startMs > 0L) ((System.currentTimeMillis() - startMs).coerceAtLeast(500L) / 60000.0) else 1.0
                val liveWpm = if (startMs > 0L) ((correctChars / 5.0) / elapsedMin).toInt() else 0

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("${finishedWpm ?: liveWpm} WPM", "ACCURACY $accuracy% · ${typed.length}/${passage.length} CHARS", hue)
                        Text(passage, style = DotkitType.bodyUi(15, bold = true), color = palette.ink)
                        PixelTextField(
                            value = typed,
                            onValueChange = { next ->
                                if (startMs == 0L && next.isNotEmpty()) startMs = System.currentTimeMillis()
                                typed = next
                                if (next.length >= passage.length) {
                                    val mins = ((System.currentTimeMillis() - startMs).coerceAtLeast(1000L) / 60000.0)
                                    val finalWpm = ((correctChars / 5.0) / mins).toInt()
                                    finishedWpm = finalWpm
                                    onRecordScore("bench-typing", "$finalWpm WPM ($accuracy%)", finalWpm.toDouble(), true)
                                }
                            },
                            placeholder = "Start typing the passage above...",
                            singleLine = false,
                            minHeight = 100.dp
                        )
                        PixelButton("New Passage / Reset", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            passage = passages.random(); typed = ""; startMs = 0L; finishedWpm = null
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
