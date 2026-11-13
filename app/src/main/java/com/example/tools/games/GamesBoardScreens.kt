package com.example.tools.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.example.lib.games.ChessEngine
import com.example.lib.games.MinesweeperEngine
import com.example.lib.games.MoveDir
import com.example.lib.games.PieceColor
import com.example.lib.games.SudokuDifficulty
import com.example.lib.games.SudokuEngine
import com.example.lib.games.WorldQuizBank
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesBoardScreen(
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
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.ROSE

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
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "game-wheel" -> {
                var modeBottle by remember { mutableStateOf(false) }
                var slicesText by remember { mutableStateOf("Pizza, Sushi, Tacos, Ramen, Pasta, Salad") }
                val angle = remember { Animatable(0f) }
                var landed by remember { mutableStateOf("Tap Spin") }
                val slices = slicesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Option A", "Option B") }
                val sliceColors = listOf(Color(0xFFFF4F00), Color(0xFF0052FF), Color(0xFF0E9F6E), Color(0xFFD99400), Color(0xFFE60026), Color(0xFF008B9E))

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("Lucky Wheel", selected = !modeBottle, chapterHue = hue, onClick = { modeBottle = false })
                            PixelChip("Spin Bottle", selected = modeBottle, chapterHue = hue, onClick = { modeBottle = true })
                        }
                        if (!modeBottle) {
                            PixelTextField(slicesText, { slicesText = it }, "Comma-separated options")
                        }
                        DotMatrixReadout(landed, if (modeBottle) "BOTTLE ANGLE ${(angle.value % 360).toInt()}°" else "RESULT", hue)
                        Canvas(Modifier.fillMaxWidth().height(230.dp)) {
                            val r = size.minDimension * 0.42f
                            val c = center
                            if (!modeBottle) {
                                val sweep = 360f / slices.size
                                slices.forEachIndexed { idx, _ ->
                                    drawArc(
                                        color = sliceColors[idx % sliceColors.size],
                                        startAngle = angle.value + idx * sweep - 90f,
                                        sweepAngle = sweep,
                                        useCenter = true,
                                        topLeft = Offset(c.x - r, c.y - r),
                                        size = Size(r * 2, r * 2)
                                    )
                                }
                                drawLine(palette.ink, Offset(c.x, c.y - r - 14f), Offset(c.x, c.y - r + 14f), strokeWidth = 8f)
                            } else {
                                drawCircle(palette.hairline, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                                rotate(angle.value, pivot = c) {
                                    drawLine(palette.chapterColor(hue), Offset(c.x, c.y + r * 0.4f), Offset(c.x, c.y - r * 0.78f), strokeWidth = 18f)
                                    drawCircle(palette.ink, 14f, Offset(c.x, c.y - r * 0.78f))
                                }
                            }
                        }
                        PixelButton("Spin!", onClick = {
                            scope.launch {
                                val delta = 720f + Random.nextFloat() * 1080f
                                val target = angle.value + delta
                                angle.animateTo(target, animationSpec = tween(1800))
                                if (modeBottle) {
                                    landed = "Pointed ${(target % 360).toInt()}°"
                                } else {
                                    val sweep = 360f / slices.size
                                    val norm = ((360f - (target % 360f)) + 360f) % 360f
                                    val idx = (norm / sweep).toInt().coerceIn(0, slices.lastIndex)
                                    landed = slices[idx]
                                }
                            }
                        }, chapterHue = hue, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            "game-quiz" -> {
                var q by remember { mutableStateOf(WorldQuizBank.nextQuestion()) }
                var streak by remember { mutableIntStateOf(0) }
                var totalRight by remember { mutableIntStateOf(0) }
                var lastFeedback by remember { mutableStateOf("") }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("$totalRight", "WORLD CAPITALS · STREAK $streak", hue)
                        if (lastFeedback.isNotBlank()) PixelStamp(lastFeedback, if (lastFeedback.startsWith("Correct")) palette.ok else palette.err)
                        Text("What is the capital of ${q.country} (${q.region})?", style = DotkitType.editorialHeading(20), color = palette.ink)
                        q.options.forEach { opt ->
                            PixelButton(
                                text = opt,
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = {
                                    if (opt == q.capital) {
                                        streak++; totalRight++
                                        lastFeedback = "Correct! ${q.capital}"
                                        onRecordScore("game-quiz", "$streak streak", streak.toDouble(), true)
                                    } else {
                                        streak = 0
                                        lastFeedback = "Wrong — ${q.country} capital is ${q.capital}"
                                    }
                                    q = WorldQuizBank.nextQuestion()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            "game-mines" -> {
                var state by remember { mutableStateOf(MinesweeperEngine.createBlank(9, 9, 10)) }
                var flagMode by remember { mutableStateOf(false) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val flagsUsed = state.cells.count { it.flagged }
                            Text("Mines: ${state.minesCount} · Flags: $flagsUsed", style = DotkitType.monoData(12), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip(if (flagMode) "Mode: FLAG" else "Mode: DIG", selected = flagMode, chapterHue = hue, onClick = { flagMode = !flagMode })
                                PixelChip("9×9", selected = state.rows == 9, onClick = { state = MinesweeperEngine.createBlank(9, 9, 10) })
                                PixelChip("10×10", selected = state.rows == 10, onClick = { state = MinesweeperEngine.createBlank(10, 10, 15) })
                            }
                        }
                        if (state.won) PixelStamp("FIELD CLEARED!", palette.ok)
                        if (state.gameOver) PixelStamp("BOOM! MINE HIT", palette.err)

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (r in 0 until state.rows) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    for (c in 0 until state.cols) {
                                        val idx = r * state.cols + c
                                        val cell = state.cells[idx]
                                        val bg = when {
                                            cell.revealed && cell.isMine -> palette.err
                                            cell.revealed -> palette.paper
                                            cell.flagged -> palette.warn.copy(alpha = 0.25f)
                                            else -> palette.paper3
                                        }
                                        val label = when {
                                            cell.revealed && cell.isMine -> "*"
                                            cell.revealed && cell.adjacentMines > 0 -> "${cell.adjacentMines}"
                                            cell.flagged -> "F"
                                            else -> ""
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(bg)
                                                .border(1.dp, palette.hairline)
                                                .clickable {
                                                    state = if (flagMode) MinesweeperEngine.toggleFlag(state, idx) else MinesweeperEngine.reveal(state, idx)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(label, style = DotkitType.monoData(12), color = if (cell.revealed && cell.isMine) Color.White else palette.ink)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "game-sudoku" -> {
                var diff by remember { mutableStateOf(SudokuDifficulty.EASY) }
                var puzzle by remember(diff) { mutableStateOf(SudokuEngine.generate(diff)) }
                var board by remember(puzzle) { mutableStateOf(puzzle.puzzle.copyOf()) }
                var selectedIdx by remember { mutableIntStateOf(0) }
                var status by remember { mutableStateOf("Tap cell & enter 1–9") }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SudokuDifficulty.entries.forEach { d ->
                                PixelChip(d.label, selected = diff == d, chapterHue = hue, onClick = { diff = d })
                            }
                            PixelChip("New", onClick = { puzzle = SudokuEngine.generate(diff) })
                        }
                        Text(status, style = DotkitType.monoData(12), color = palette.ink2)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (r in 0 until 9) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (c in 0 until 9) {
                                        val idx = r * 9 + c
                                        val given = puzzle.puzzle[idx] != 0
                                        val v = board[idx]
                                        val isSel = selectedIdx == idx
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(if (isSel) palette.chapterColor(hue).copy(alpha = 0.25f) else if (given) palette.paper3 else palette.paper2)
                                                .border(if (isSel) 2.dp else 1.dp, if (isSel) palette.chapterColor(hue) else palette.hairline)
                                                .clickable { selectedIdx = idx },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (v != 0) Text("$v", style = DotkitType.monoData(14), color = if (given) palette.ink else palette.chapterColor(ChapterHue.COBALT))
                                        }
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..9).forEach { n ->
                                PixelChip("$n", chapterHue = hue, onClick = {
                                    if (puzzle.puzzle[selectedIdx] == 0) {
                                        val next = board.copyOf()
                                        next[selectedIdx] = n
                                        board = next
                                    }
                                })
                            }
                            PixelChip("Clear", onClick = {
                                if (puzzle.puzzle[selectedIdx] == 0) {
                                    val next = board.copyOf(); next[selectedIdx] = 0; board = next
                                }
                            })
                            PixelChip("Hint", onClick = {
                                val next = board.copyOf()
                                next[selectedIdx] = puzzle.solution[selectedIdx]
                                board = next
                            })
                            PixelChip("Check", selected = true, chapterHue = hue, onClick = {
                                val wrong = board.indices.count { board[it] != 0 && board[it] != puzzle.solution[it] }
                                val left = board.count { it == 0 }
                                status = when {
                                    wrong == 0 && left == 0 -> { onRecordScore("game-sudoku", diff.label, 1.0, true); "Solved cleanly!" }
                                    wrong > 0 -> "$wrong conflict(s) found."
                                    else -> "Valid so far · $left blank cell(s) left."
                                }
                            })
                        }
                    }
                }
            }

            "game-memory" -> {
                val glyphs = listOf("Δ", "Ω", "Σ", "Φ", "Ψ", "λ", "π", "★")
                var deck by remember { mutableStateOf((glyphs + glyphs).shuffled()) }
                var revealed by remember { mutableStateOf(List(16) { false }) }
                var matched by remember { mutableStateOf(List(16) { false }) }
                var firstPick by remember { mutableStateOf<Int?>(null) }
                var secondPick by remember { mutableStateOf<Int?>(null) }
                var moves by remember { mutableIntStateOf(0) }

                LaunchedEffect(firstPick, secondPick) {
                    val a = firstPick; val b = secondPick
                    if (a != null && b != null) {
                        delay(650L)
                        if (deck[a] == deck[b]) {
                            val nextM = matched.toMutableList(); nextM[a] = true; nextM[b] = true; matched = nextM
                        } else {
                            val nextR = revealed.toMutableList(); nextR[a] = false; nextR[b] = false; revealed = nextR
                        }
                        firstPick = null; secondPick = null
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("$moves", "MOVES · MATCHED ${matched.count { it } / 2}/8", hue)
                            PixelChip("Reset", selected = true, chapterHue = hue, onClick = {
                                deck = (glyphs + glyphs).shuffled(); revealed = List(16) { false }; matched = List(16) { false }; firstPick = null; secondPick = null; moves = 0
                            })
                        }
                        for (r in 0 until 4) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0 until 4) {
                                    val idx = r * 4 + c
                                    val up = revealed[idx] || matched[idx]
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (matched[idx]) palette.ok.copy(alpha = 0.2f) else if (up) palette.paper else palette.paper3)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                            .clickable(enabled = !up && secondPick == null) {
                                                val nextR = revealed.toMutableList(); nextR[idx] = true; revealed = nextR
                                                if (firstPick == null) firstPick = idx
                                                else { secondPick = idx; moves++ }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(if (up) deck[idx] else "·", style = DotkitType.displayPixel(24), color = palette.ink)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "game-snake" -> {
                val cols = 14
                var snake by remember { mutableStateOf(listOf(7 to 7, 6 to 7, 5 to 7)) }
                var dir by remember { mutableStateOf(MoveDir.RIGHT) }
                var food by remember { mutableStateOf(10 to 7) }
                var running by remember { mutableStateOf(false) }
                var gameOver by remember { mutableStateOf(false) }
                var score by remember { mutableIntStateOf(0) }

                LaunchedEffect(running, gameOver) {
                    while (running && !gameOver) {
                        delay(175L)
                        val head = snake.first()
                        val next = when (dir) {
                            MoveDir.UP -> head.first to head.second - 1
                            MoveDir.DOWN -> head.first to head.second + 1
                            MoveDir.LEFT -> head.first - 1 to head.second
                            MoveDir.RIGHT -> head.first + 1 to head.second
                        }
                        val willEat = next == food
                        val bodyToCheck = if (willEat) snake else snake.dropLast(1)
                        if (next.first !in 0 until cols || next.second !in 0 until cols || next in bodyToCheck) {
                            gameOver = true; running = false
                            onRecordScore("game-snake", "$score pts", score.toDouble(), true)
                        } else if (willEat) {
                            val grown = listOf(next) + snake
                            snake = grown
                            score++
                            val emptyCells = buildList {
                                for (x in 0 until cols) for (y in 0 until cols) {
                                    val cell = x to y
                                    if (cell !in grown) add(cell)
                                }
                            }
                            if (emptyCells.isEmpty()) {
                                gameOver = true; running = false
                                onRecordScore("game-snake", "$score pts (MAX)", score.toDouble(), true)
                                onStatusToast("Board cleared!")
                            } else {
                                food = emptyCells.random()
                            }
                        } else {
                            snake = listOf(next) + snake.dropLast(1)
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("$score", if (gameOver) "GAME OVER" else "SNAKE LENGTH ${snake.size}", hue)
                            PixelChip(if (running) "Pause" else if (gameOver) "New" else "Play", selected = true, chapterHue = hue, onClick = {
                                if (gameOver) {
                                    val initialSnake = listOf(7 to 7, 6 to 7, 5 to 7)
                                    snake = initialSnake; dir = MoveDir.RIGHT; score = 0; gameOver = false; running = true
                                    food = 10 to 7
                                } else running = !running
                            })
                        }
                        val snakeCol = palette.ok
                        val foodCol = palette.chapterColor(hue)
                        Canvas(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(palette.paper3).border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                        ) {
                            val cell = size.width / cols
                            drawRect(foodCol, Offset(food.first * cell + 2f, food.second * cell + 2f), Size(cell - 4f, cell - 4f))
                            snake.forEachIndexed { idx, (x, y) ->
                                drawRect(if (idx == 0) palette.ink else snakeCol, Offset(x * cell + 1f, y * cell + 1f), Size(cell - 2f, cell - 2f))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            PixelChip("↑", onClick = { if (dir != MoveDir.DOWN) dir = MoveDir.UP })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            PixelChip("←", onClick = { if (dir != MoveDir.RIGHT) dir = MoveDir.LEFT })
                            PixelChip("↓", onClick = { if (dir != MoveDir.UP) dir = MoveDir.DOWN })
                            PixelChip("→", onClick = { if (dir != MoveDir.LEFT) dir = MoveDir.RIGHT })
                        }
                    }
                }
            }

            "game-hangman" -> {
                var targetWord by remember { mutableStateOf(WorldQuizBank.HANGMAN_WORDS.random()) }
                var guessed by remember { mutableStateOf(setOf<Char>()) }
                val wrongCount = guessed.count { it !in targetWord }
                val won = targetWord.all { it in guessed }
                val lost = wrongCount >= 6

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Misses: $wrongCount / 6", style = DotkitType.monoData(13), color = palette.ink2)
                            PixelChip("New Word", selected = true, chapterHue = hue, onClick = {
                                targetWord = WorldQuizBank.HANGMAN_WORDS.random(); guessed = emptySet()
                            })
                        }
                        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                            val c = palette.ink
                            drawLine(c, Offset(40f, size.height - 10f), Offset(160f, size.height - 10f), 4f)
                            drawLine(c, Offset(80f, size.height - 10f), Offset(80f, 14f), 4f)
                            drawLine(c, Offset(80f, 14f), Offset(170f, 14f), 4f)
                            drawLine(c, Offset(170f, 14f), Offset(170f, 34f), 4f)
                            if (wrongCount >= 1) drawCircle(c, 16f, Offset(170f, 50f), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                            if (wrongCount >= 2) drawLine(c, Offset(170f, 66f), Offset(170f, 102f), 4f)
                            if (wrongCount >= 3) drawLine(c, Offset(170f, 74f), Offset(146f, 92f), 4f)
                            if (wrongCount >= 4) drawLine(c, Offset(170f, 74f), Offset(194f, 92f), 4f)
                            if (wrongCount >= 5) drawLine(c, Offset(170f, 102f), Offset(150f, 118f), 4f)
                            if (wrongCount >= 6) drawLine(c, Offset(170f, 102f), Offset(190f, 118f), 4f)
                        }
                        val masked = targetWord.map { if (it in guessed || lost) it else '_' }.joinToString(" ")
                        DotMatrixReadout(masked, when { won -> "SOLVED!"; lost -> "GAME OVER: $targetWord"; else -> "GUESS LETTERS" }, hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ('A'..'Z').forEach { ch ->
                                PixelChip(
                                    label = ch.toString(),
                                    selected = ch in guessed,
                                    chapterHue = if (ch in guessed && ch !in targetWord) ChapterHue.VERMILION else hue,
                                    onClick = { if (!won && !lost && ch !in guessed) guessed = guessed + ch }
                                )
                            }
                        }
                    }
                }
            }

            else -> { // game-chess
                var state by remember { mutableStateOf(ChessEngine.initialState()) }
                var selectedSq by remember { mutableStateOf<Int?>(null) }
                var vsAi by remember { mutableStateOf(true) }

                val legalTargets = remember(state, selectedSq) {
                    selectedSq?.let { ChessEngine.legalMovesForSquare(state, it) }.orEmpty()
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("vs AI", selected = vsAi, chapterHue = hue, onClick = { vsAi = true; state = ChessEngine.initialState(); selectedSq = null })
                                PixelChip("2 Players", selected = !vsAi, chapterHue = hue, onClick = { vsAi = false; state = ChessEngine.initialState(); selectedSq = null })
                            }
                            PixelChip("Reset", onClick = { state = ChessEngine.initialState(); selectedSq = null })
                        }
                        val statusStr = when {
                            state.isCheckmate -> "CHECKMATE! ${state.turn.opposite()} wins"
                            state.isStalemate -> "STALEMATE · Draw"
                            state.inCheck -> "CHECK on ${state.turn}"
                            else -> "Turn: ${state.turn}"
                        }
                        PixelStamp(statusStr, if (state.isCheckmate) palette.ok else if (state.inCheck) palette.err else palette.chapterColor(hue))

                        Column(Modifier.fillMaxWidth().border(1.dp, palette.hairline)) {
                            for (r in 0 until 8) {
                                Row(Modifier.fillMaxWidth()) {
                                    for (c in 0 until 8) {
                                        val sq = r * 8 + c
                                        val darkSq = (r + c) % 2 == 1
                                        val isTarget = legalTargets.any { it.to == sq }
                                        val isSel = selectedSq == sq
                                        val bg = when {
                                            isSel -> palette.chapterColor(hue).copy(alpha = 0.45f)
                                            isTarget -> palette.ok.copy(alpha = 0.35f)
                                            darkSq -> palette.paper3
                                            else -> palette.paper2
                                        }
                                        val piece = state.board[sq]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(bg)
                                                .clickable(enabled = !state.isCheckmate && !state.isStalemate) {
                                                    val move = legalTargets.firstOrNull { it.to == sq }
                                                    if (move != null) {
                                                        var next = ChessEngine.makeMove(state, move)
                                                        selectedSq = null
                                                        if (vsAi && next.turn == PieceColor.BLACK && !next.isCheckmate && !next.isStalemate) {
                                                            ChessEngine.bestAiMove(next, depth = 2)?.let { aiMove ->
                                                                next = ChessEngine.makeMove(next, aiMove)
                                                            }
                                                        }
                                                        state = next
                                                    } else if (piece?.color == state.turn) {
                                                        selectedSq = sq
                                                    } else {
                                                        selectedSq = null
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (piece != null) {
                                                Text(piece.symbol(), style = DotkitType.bodyUi(22, bold = true), color = palette.ink)
                                            } else if (isTarget) {
                                                Text("•", style = DotkitType.bodyUi(20), color = palette.ok)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.moveHistory.isNotEmpty()) {
                            Text("Moves: ${state.moveHistory.takeLast(8).joinToString("  ")}", style = DotkitType.monoData(11), color = palette.ink2)
                        }
                    }
                }
            }
        }
    }
}
