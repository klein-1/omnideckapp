package com.example.tools.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.games.MazeEngine
import com.example.lib.games.NonogramEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesArcadeMoreScreen(
    meta: ToolMeta,
    onRecordScore: (testId: String, label: String, numericScore: Double, higherIsBetter: Boolean) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.ROSE

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = "",
        outputFileName = "${meta.id}_score.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        showOutputBar = false,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "game-nonogram" -> {
                var puzzle by remember { mutableStateOf(NonogramEngine.generatePuzzle(5)) }
                var marks by remember { mutableStateOf(IntArray(25)) } // 0=blank, 1=filled, 2=X
                var markXMode by remember { mutableStateOf(false) }
                val filledArr = remember(marks) { BooleanArray(25) { marks[it] == 1 } }
                val solved = NonogramEngine.isSolved(puzzle, filledArr)

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("Fill ■", selected = !markXMode, chapterHue = hue, onClick = { markXMode = false })
                                PixelChip("Cross ✕", selected = markXMode, chapterHue = ChapterHue.COBALT, onClick = { markXMode = true })
                            }
                            if (solved) PixelStamp("PICROSS SOLVED!", palette.ok)
                        }
                        // Column clues header
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.weight(1.2f))
                            for (c in 0 until 5) {
                                Text(
                                    text = puzzle.colClues[c].joinToString("\n"),
                                    style = DotkitType.monoData(11),
                                    color = palette.ink2,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        for (r in 0 until 5) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = puzzle.rowClues[r].joinToString(" "),
                                    style = DotkitType.monoData(11),
                                    color = palette.ink2,
                                    modifier = Modifier.weight(1.2f)
                                )
                                for (c in 0 until 5) {
                                    val idx = r * 5 + c
                                    val st = marks[idx]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (st == 1) palette.chapterColor(hue) else palette.paper)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(4.dp))
                                            .clickable(enabled = !solved) {
                                                val next = marks.copyOf()
                                                next[idx] = if (markXMode) {
                                                    if (st == 2) 0 else 2
                                                } else {
                                                    if (st == 1) 0 else 1
                                                }
                                                marks = next
                                                if (NonogramEngine.isSolved(puzzle, BooleanArray(25) { next[it] == 1 })) {
                                                    onRecordScore("game-nonogram", "5×5 Solved", 1.0, true)
                                                    onStatusToast("Nonogram puzzle solved!")
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (st == 2) Text("✕", style = DotkitType.monoData(14), color = palette.ink3)
                                    }
                                }
                            }
                        }
                        PixelButton("New 5×5 Puzzle", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            puzzle = NonogramEngine.generatePuzzle(5); marks = IntArray(25)
                        })
                    }
                }
            }

            "game-maze" -> {
                val side = 9
                var maze by remember { mutableStateOf(MazeEngine.generate(side, side)) }
                var playerIdx by remember { mutableIntStateOf(0) }
                var steps by remember { mutableIntStateOf(0) }
                var showHintPath by remember { mutableStateOf(false) }
                val goal = side * side - 1
                val won = playerIdx == goal
                val hintPath = remember(maze, playerIdx, showHintPath) {
                    if (showHintPath) MazeEngine.shortestPathBfs(maze, playerIdx, goal).toSet() else emptySet()
                }

                fun tryStep(dirMask: Int, dr: Int, dc: Int) {
                    if (won) return
                    if ((maze.passages[playerIdx] and dirMask) != 0) {
                        val r = (playerIdx / side) + dr
                        val c = (playerIdx % side) + dc
                        if (r in 0 until side && c in 0 until side) {
                            playerIdx = r * side + c
                            steps++
                            if (playerIdx == goal) {
                                onRecordScore("game-maze", "${side}×${side} in $steps steps", steps.toDouble(), false)
                                onStatusToast("Escaped maze in $steps steps!")
                            }
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("MAZE ${side}×${side} · STEPS: $steps", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("BFS Hint", selected = showHintPath, chapterHue = ChapterHue.COBALT, onClick = { showHintPath = !showHintPath })
                                if (won) PixelStamp("ESCAPED ✓", palette.ok)
                            }
                        }
                        val wallCol = palette.ink
                        val playerCol = palette.chapterColor(hue)
                        val goalCol = palette.ok
                        val hintCol = palette.info.copy(alpha = 0.25f)
                        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(SwissControlShape).background(palette.paper).border(2.dp, palette.ink, SwissControlShape)) {
                            val cw = size.width / side
                            val ch = size.height / side
                            for (r in 0 until side) {
                                for (c in 0 until side) {
                                    val idx = r * side + c
                                    val x = c * cw; val y = r * ch
                                    if (idx in hintPath) {
                                        drawRect(hintCol, topLeft = Offset(x + 2f, y + 2f), size = Size(cw - 4f, ch - 4f))
                                    }
                                    val mask = maze.passages[idx]
                                    if ((mask and MazeEngine.TOP) == 0) drawLine(wallCol, Offset(x, y), Offset(x + cw, y), 3f)
                                    if ((mask and MazeEngine.RIGHT) == 0) drawLine(wallCol, Offset(x + cw, y), Offset(x + cw, y + ch), 3f)
                                    if ((mask and MazeEngine.BOTTOM) == 0) drawLine(wallCol, Offset(x, y + ch), Offset(x + cw, y + ch), 3f)
                                    if ((mask and MazeEngine.LEFT) == 0) drawLine(wallCol, Offset(x, y), Offset(x, y + ch), 3f)
                                    if (idx == goal) {
                                        drawRect(goalCol.copy(alpha = 0.4f), Offset(x + cw * 0.2f, y + ch * 0.2f), Size(cw * 0.6f, ch * 0.6f))
                                    }
                                    if (idx == playerIdx) {
                                        drawCircle(playerCol, radius = cw * 0.3f, center = Offset(x + cw / 2f, y + ch / 2f))
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("◀", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { tryStep(MazeEngine.LEFT, 0, -1) })
                            PixelButton("▲", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { tryStep(MazeEngine.TOP, -1, 0) })
                            PixelButton("▼", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { tryStep(MazeEngine.BOTTOM, 1, 0) })
                            PixelButton("▶", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { tryStep(MazeEngine.RIGHT, 0, 1) })
                        }
                        PixelButton("Generate New Maze", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            maze = MazeEngine.generate(side, side); playerIdx = 0; steps = 0; showHintPath = false
                        })
                    }
                }
            }

            "game-flappy" -> {
                var playing by remember { mutableStateOf(false) }
                var gameOver by remember { mutableStateOf(false) }
                var birdY by remember { mutableFloatStateOf(0.5f) }
                var birdVel by remember { mutableFloatStateOf(0f) }
                var pipeX by remember { mutableFloatStateOf(1.0f) }
                var gapY by remember { mutableFloatStateOf(0.5f) }
                var score by remember { mutableIntStateOf(0) }
                var best by remember { mutableIntStateOf(0) }

                LaunchedEffect(playing) {
                    while (playing) {
                        delay(16L)
                        birdVel += 0.0014f
                        birdY += birdVel
                        pipeX -= 0.011f
                        if (pipeX < -0.14f) {
                            pipeX = 1.08f
                            gapY = 0.26f + Random.nextFloat() * 0.48f
                            score++
                            if (score > best) best = score
                        }
                        val inPipeBand = pipeX in 0.14f..0.28f
                        val hitPipe = inPipeBand && (birdY < gapY - 0.14f || birdY > gapY + 0.14f)
                        if (birdY < 0.04f || birdY > 0.96f || hitPipe) {
                            playing = false
                            gameOver = true
                            onRecordScore("game-flappy", "Score $score", score.toDouble(), true)
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("$score", "BEST: $best · TAP CANVAS TO FLAP", hue)
                        val birdCol = palette.chapterColor(hue)
                        val pipeCol = palette.ok
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(SwissControlShape)
                                .background(palette.paper)
                                .border(2.dp, palette.ink, SwissControlShape)
                                .clickable {
                                    if (!playing) {
                                        birdY = 0.5f; birdVel = -0.022f; pipeX = 1.0f; gapY = 0.5f; score = 0; gameOver = false; playing = true
                                    } else {
                                        birdVel = -0.022f
                                    }
                                }
                        ) {
                            val pw = size.width * 0.12f
                            val px = pipeX * size.width
                            val topH = (gapY - 0.14f).coerceAtLeast(0.05f) * size.height
                            val botY = (gapY + 0.14f).coerceAtMost(0.95f) * size.height
                            drawRect(pipeCol, Offset(px, 0f), Size(pw, topH))
                            drawRect(pipeCol, Offset(px, botY), Size(pw, size.height - botY))
                            drawCircle(birdCol, radius = 14.dp.toPx(), center = Offset(size.width * 0.22f, birdY * size.height))
                        }
                        if (gameOver) PixelStamp("CRASHED! TAP CANVAS TO RETRY", palette.err)
                    }
                }
            }

            "game-whack" -> {
                var running by remember { mutableStateOf(false) }
                var timeLeft by remember { mutableIntStateOf(30) }
                var activeHole by remember { mutableIntStateOf(-1) }
                var score by remember { mutableIntStateOf(0) }
                var misses by remember { mutableIntStateOf(0) }

                LaunchedEffect(running) {
                    if (running) {
                        var ticks = 0
                        while (running && timeLeft > 0) {
                            activeHole = Random.nextInt(9)
                            delay(650L)
                            ticks += 650
                            if (ticks >= 1000) {
                                timeLeft = (timeLeft - 1).coerceAtLeast(0)
                                ticks -= 1000
                            }
                        }
                        running = false
                        activeHole = -1
                        onRecordScore("game-whack", "$score hits ($misses miss)", score.toDouble(), true)
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DotMatrixReadout("$score HITS", "TIME: ${timeLeft}s · MISS: $misses", hue)
                        for (r in 0 until 3) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (c in 0 until 3) {
                                    val idx = r * 3 + c
                                    val up = running && activeHole == idx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.1f)
                                            .clip(SwissControlShape)
                                            .background(if (up) palette.chapterColor(hue) else palette.paper3)
                                            .border(1.5.dp, palette.ink, SwissControlShape)
                                            .clickable(enabled = running) {
                                                if (up) {
                                                    score++
                                                    activeHole = -1
                                                } else {
                                                    misses++
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(if (up) "MOLE!" else "•", style = DotkitType.monoData(14), color = if (up) Color.White else palette.ink3)
                                    }
                                }
                            }
                        }
                        PixelButton(if (running) "Running..." else "Start 30s Sprint", enabled = !running, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            score = 0; misses = 0; timeLeft = 30; running = true
                        })
                    }
                }
            }

            "game-scramble" -> {
                val words = remember {
                    listOf("KOTLIN", "COMPOSE", "ANDROID", "SWISS", "BATTERY", "CIPHER", "SPECTRUM", "NETWORK", "MATRIX", "ORBIT", "PRISM", "SIGNAL", "MEMORY", "QUARTZ")
                }
                fun scramble(w: String): String {
                    var s: String
                    do { s = w.toList().shuffled().joinToString("") } while (s == w)
                    return s
                }
                var target by remember { mutableStateOf(words.random()) }
                var scrambled by remember(target) { mutableStateOf(scramble(target)) }
                var guess by remember { mutableStateOf("") }
                var streak by remember { mutableIntStateOf(0) }
                var revealedHint by remember { mutableIntStateOf(1) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DotMatrixReadout(scrambled, "STREAK: $streak · HINT: ${target.take(revealedHint)}...", hue)
                        PixelTextField(guess, { guess = it.uppercase() }, "Type unscrambled word…", chapterHue = hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Check", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                if (guess.trim().equals(target, ignoreCase = true)) {
                                    streak++
                                    onRecordScore("game-scramble", "Streak $streak", streak.toDouble(), true)
                                    onStatusToast("Correct! ($target)")
                                    target = words.random(); guess = ""; revealedHint = 1
                                } else {
                                    streak = 0
                                    onStatusToast("Not quite — keep trying or reveal hint!")
                                }
                            })
                            PixelButton("Hint", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                revealedHint = (revealedHint + 1).coerceAtMost(target.length)
                            })
                            PixelButton("Skip", variant = PixelButtonVariant.OUTLINE, onClick = {
                                streak = 0; target = words.random(); guess = ""; revealedHint = 1
                            })
                        }
                    }
                }
            }

            else -> { // game-party (Imposter / Bingo / Truth or Dare)
                var mode by remember { mutableStateOf("IMPOSTER") } // IMPOSTER, BINGO, TD
                var playerCount by remember { mutableIntStateOf(4) }
                var imposterIdx by remember { mutableIntStateOf(Random.nextInt(4)) }
                var secretWord by remember { mutableStateOf("Volcano") }
                var decoyWord by remember { mutableStateOf("Glacier") }
                var revealIdx by remember { mutableIntStateOf(0) }
                var peeking by remember { mutableStateOf(false) }

                var calledBingo by remember { mutableStateOf<List<Int>>(emptyList()) }
                var tdPrompt by remember { mutableStateOf("Tap Truth or Dare below!") }

                val secretPairs = remember {
                    listOf("Volcano" to "Glacier", "Espresso" to "Matcha", "Submarine" to "Airplane", "Telescope" to "Microscope", "Chess" to "Poker")
                }
                val truths = remember { listOf("What app do you spend the most hours on?", "What was your funniest tech fail?", "What skill would you master overnight?") }
                val dares = remember { listOf("Speak only in questions for 2 rounds.", "Do 10 squats right now.", "Hold a plank for 25 seconds.") }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("IMPOSTER" to "Imposter", "BINGO" to "Bingo 1–75", "TD" to "Truth/Dare").forEach { (id, lbl) ->
                                PixelChip(lbl, selected = mode == id, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { mode = id })
                            }
                        }
                        when (mode) {
                            "IMPOSTER" -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(3, 4, 5, 6).forEach { n ->
                                        PixelChip("${n}P", selected = playerCount == n, chapterHue = hue, onClick = {
                                            playerCount = n; imposterIdx = Random.nextInt(n); revealIdx = 0; peeking = false
                                        })
                                    }
                                }
                                DotMatrixReadout(
                                    value = if (peeking) (if (revealIdx == imposterIdx) "IMPOSTER ($decoyWord)" else secretWord) else "PLAYER ${revealIdx + 1}",
                                    label = if (peeking) "MEMORIZE & HIDE BEFORE PASSING" else "PASS PHONE TO PLAYER ${revealIdx + 1}",
                                    chapterHue = hue
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelButton(if (peeking) "Hide & Next" else "Reveal My Word", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                        if (!peeking) peeking = true
                                        else { peeking = false; revealIdx = (revealIdx + 1) % playerCount }
                                    })
                                    PixelButton("New Round", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                        val pair = secretPairs.random()
                                        secretWord = pair.first; decoyWord = pair.second
                                        imposterIdx = Random.nextInt(playerCount); revealIdx = 0; peeking = false
                                    })
                                }
                            }
                            "BINGO" -> {
                                val lastNum = calledBingo.lastOrNull()
                                val letter = lastNum?.let {
                                    when {
                                        it <= 15 -> "B"; it <= 30 -> "I"; it <= 45 -> "N"; it <= 60 -> "G"; else -> "O"
                                    }
                                } ?: "--"
                                DotMatrixReadout(if (lastNum != null) "$letter-$lastNum" else "--", "CALLED ${calledBingo.size}/75 NUMBERS", hue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelButton("Call Next Ball", chapterHue = hue, enabled = calledBingo.size < 75, modifier = Modifier.weight(1f), onClick = {
                                        val remain = (1..75).filter { it !in calledBingo }
                                        if (remain.isNotEmpty()) calledBingo = calledBingo + remain.random()
                                    })
                                    PixelButton("Reset", variant = PixelButtonVariant.OUTLINE, onClick = { calledBingo = emptyList() })
                                }
                                if (calledBingo.isNotEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        calledBingo.reversed().take(20).forEach { n -> PixelChip(n.toString(), chapterHue = hue) }
                                    }
                                }
                            }
                            else -> {
                                Text(tdPrompt, style = DotkitType.editorialHeading(18), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelButton("Truth", chapterHue = ChapterHue.COBALT, modifier = Modifier.weight(1f), onClick = { tdPrompt = "TRUTH: ${truths.random()}" })
                                    PixelButton("Dare", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { tdPrompt = "DARE: ${dares.random()}" })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
