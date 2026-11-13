package com.example.tools.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.design.SwissControlShape
import com.example.lib.games.CheckerPiece
import com.example.lib.games.CheckersEngine
import com.example.lib.games.Connect4Engine
import com.example.lib.games.LightsOutEngine
import com.example.lib.games.WordleEngine
import com.example.lib.games.WordleTile
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesExtraScreen(
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
            "game-c4" -> {
                var board by remember { mutableStateOf(Connect4Engine.emptyBoard()) }
                var turn by remember { mutableIntStateOf(1) }
                var vsAi by remember { mutableStateOf(true) }
                var p1Wins by remember { mutableIntStateOf(0) }
                var p2Wins by remember { mutableIntStateOf(0) }

                val winInfo = remember(board) { Connect4Engine.checkWinner(board) }
                val draw = remember(board) { Connect4Engine.isDraw(board) }

                fun playCol(col: Int) {
                    if (winInfo != null || draw) return
                    val (after, _) = Connect4Engine.dropDisc(board, col, turn) ?: return
                    val w1 = Connect4Engine.checkWinner(after)
                    if (w1 != null) {
                        if (w1.winner == 1) { p1Wins++; onRecordScore("game-c4", "P1 $p1Wins wins", p1Wins.toDouble(), true) } else p2Wins++
                        board = after
                        return
                    }
                    if (Connect4Engine.isDraw(after)) {
                        board = after
                        return
                    }
                    if (vsAi && turn == 1) {
                        val aiCol = Connect4Engine.bestAiMove(after, 2, 1)
                        val (afterAi, _) = Connect4Engine.dropDisc(after, aiCol, 2) ?: (after to 0)
                        val w2 = Connect4Engine.checkWinner(afterAi)
                        if (w2?.winner == 2) p2Wins++
                        board = afterAi
                        turn = 1
                    } else {
                        board = after
                        turn = if (turn == 1) 2 else 1
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("vs AI", selected = vsAi, chapterHue = hue, onClick = { vsAi = true; board = Connect4Engine.emptyBoard(); turn = 1 })
                                PixelChip("2 Players", selected = !vsAi, chapterHue = hue, onClick = { vsAi = false; board = Connect4Engine.emptyBoard(); turn = 1 })
                            }
                            PixelChip("Reset", onClick = { board = Connect4Engine.emptyBoard(); turn = 1 })
                        }
                        Text("SCORE · P1 (Red): $p1Wins · ${if (vsAi) "CPU" else "P2"} (Gold): $p2Wins", style = DotkitType.monoData(11), color = palette.ink2)
                        when {
                            winInfo != null -> PixelStamp("WINNER: ${if (winInfo.winner == 1) "PLAYER 1" else if (vsAi) "CPU" else "PLAYER 2"}", palette.ok)
                            draw -> PixelStamp("BOARD FULL · DRAW", palette.warn)
                            else -> PixelStamp("TURN: ${if (turn == 1) "PLAYER 1 (RED)" else "PLAYER 2 (GOLD)"}", palette.chapterColor(hue))
                        }

                        // Drop row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in 0 until Connect4Engine.COLS) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(SwissControlShape)
                                        .background(palette.paper3)
                                        .border(1.dp, palette.hairline, SwissControlShape)
                                        .clickable(enabled = winInfo == null && !draw && Connect4Engine.canDrop(board, c)) {
                                            playCol(c)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↓", style = DotkitType.monoData(14), color = palette.ink)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0052FF).copy(alpha = 0.85f))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            for (r in 0 until Connect4Engine.ROWS) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    for (c in 0 until Connect4Engine.COLS) {
                                        val v = board[r * Connect4Engine.COLS + c]
                                        val isWinCell = winInfo?.cells?.contains(r to c) == true
                                        val discColor by animateColorAsState(
                                            targetValue = when (v) {
                                                1 -> Color(0xFFE60026)
                                                2 -> Color(0xFFFFB800)
                                                else -> palette.paper
                                            },
                                            animationSpec = tween(180),
                                            label = "c4_disc"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(CircleShape)
                                                .background(discColor)
                                                .border(if (isWinCell) 3.dp else 1.dp, if (isWinCell) Color.White else palette.hairline.copy(alpha = 0.4f), CircleShape)
                                                .clickable(enabled = winInfo == null && !draw && Connect4Engine.canDrop(board, c)) { playCol(c) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "game-wordle" -> {
                var target by remember { mutableStateOf(WordleEngine.randomWord()) }
                var guesses by remember { mutableStateOf<List<String>>(emptyList()) }
                var current by remember { mutableStateOf("") }
                var streak by remember { mutableIntStateOf(0) }
                val won = guesses.any { it == target }
                val lost = !won && guesses.size >= 6

                // Track best state per letter for keyboard hints
                val keyStates = remember(guesses, target) {
                    val map = mutableMapOf<Char, WordleTile>()
                    guesses.forEach { g ->
                        val eval = WordleEngine.evaluateGuess(target, g)
                        g.forEachIndexed { idx, ch ->
                            val tile = eval[idx]
                            val prev = map[ch]
                            if (prev == null || (tile == WordleTile.EXACT) || (tile == WordleTile.PRESENT && prev == WordleTile.ABSENT)) {
                                map[ch] = tile
                            }
                        }
                    }
                    map
                }

                fun submitGuess() {
                    if (won || lost) return
                    if (current.length < 5) {
                        onStatusToast("Enter 5 letters")
                        return
                    }
                    val next = guesses + current
                    guesses = next
                    if (current == target) {
                        streak++
                        onRecordScore("game-wordle", "Solved in ${next.size}/6 · Streak $streak", streak.toDouble(), true)
                        onStatusToast("Solved in ${next.size} tries!")
                    } else if (next.size >= 6) {
                        streak = 0
                    }
                    current = ""
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("STREAK $streak", "${guesses.size}/6 GUESSES", hue)
                            PixelChip("New Word", selected = true, chapterHue = hue, onClick = {
                                target = WordleEngine.randomWord(); guesses = emptyList(); current = ""
                            })
                        }
                        if (won) PixelStamp("SPLENDID! WORD: $target", palette.ok)
                        if (lost) PixelStamp("OUT OF TRIES · WORD WAS $target", palette.err)

                        for (rowIdx in 0 until 6) {
                            val word = when {
                                rowIdx < guesses.size -> guesses[rowIdx]
                                rowIdx == guesses.size -> current
                                else -> ""
                            }
                            val eval = if (rowIdx < guesses.size) WordleEngine.evaluateGuess(target, word) else null
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (colIdx in 0 until 5) {
                                    val ch = word.getOrNull(colIdx)
                                    val tile = eval?.get(colIdx)
                                    val bg = when (tile) {
                                        WordleTile.EXACT -> Color(0xFF0E9F6E)
                                        WordleTile.PRESENT -> Color(0xFFD99400)
                                        WordleTile.ABSENT -> palette.paper3
                                        null -> palette.paper
                                    }
                                    val fg = if (tile == WordleTile.EXACT || tile == WordleTile.PRESENT) Color.White else palette.ink
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(SwissControlShape)
                                            .background(bg)
                                            .border(1.dp, if (ch != null && tile == null) palette.chapterColor(hue) else palette.hairline, SwissControlShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(ch?.toString() ?: "", style = DotkitType.monoData(20), color = fg)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
                        rows.forEachIndexed { rIdx, letters ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (rIdx == 2) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.chapterColor(hue))
                                            .clickable(enabled = !won && !lost) { submitGuess() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("GO", style = DotkitType.monoLabel(10), color = hue.onColor)
                                    }
                                }
                                letters.forEach { ch ->
                                    val st = keyStates[ch]
                                    val kBg = when (st) {
                                        WordleTile.EXACT -> Color(0xFF0E9F6E)
                                        WordleTile.PRESENT -> Color(0xFFD99400)
                                        WordleTile.ABSENT -> palette.paper3.copy(alpha = 0.55f)
                                        null -> palette.paper3
                                    }
                                    val kFg = if (st == WordleTile.EXACT || st == WordleTile.PRESENT) Color.White else palette.ink
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(kBg)
                                            .clickable(enabled = !won && !lost && current.length < 5) {
                                                current += ch
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(ch.toString(), style = DotkitType.monoData(12), color = kFg)
                                    }
                                }
                                if (rIdx == 2) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.paper3)
                                            .border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
                                            .clickable(enabled = current.isNotEmpty() && !won && !lost) {
                                                current = current.dropLast(1)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⌫", style = DotkitType.monoData(14), color = palette.ink)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "game-bricks" -> {
                val bCols = 7
                val bRows = 4
                var running by remember { mutableStateOf(false) }
                var gameOver by remember { mutableStateOf(false) }
                var won by remember { mutableStateOf(false) }
                var score by remember { mutableIntStateOf(0) }
                var lives by remember { mutableIntStateOf(3) }
                var paddleX by remember { mutableFloatStateOf(0.5f) } // normalized center 0.12..0.88
                var ballX by remember { mutableFloatStateOf(0.5f) }
                var ballY by remember { mutableFloatStateOf(0.75f) }
                var vx by remember { mutableFloatStateOf(0.011f) }
                var vy by remember { mutableFloatStateOf(-0.013f) }
                var bricks by remember { mutableStateOf(BooleanArray(bRows * bCols) { true }) }

                fun resetGame() {
                    bricks = BooleanArray(bRows * bCols) { true }
                    score = 0; lives = 3; paddleX = 0.5f; ballX = 0.5f; ballY = 0.78f
                    vx = 0.011f; vy = -0.013f; gameOver = false; won = false; running = true
                }

                LaunchedEffect(running, gameOver, won) {
                    while (running && !gameOver && !won) {
                        delay(16L)
                        var nx = ballX + vx
                        var ny = ballY + vy
                        if (nx <= 0.025f) { nx = 0.025f; vx = abs(vx) }
                        if (nx >= 0.975f) { nx = 0.975f; vx = -abs(vx) }
                        if (ny <= 0.025f) { ny = 0.025f; vy = abs(vy) }

                        // Paddle collision (paddle spans y=0.88..0.92, halfW=0.13)
                        if (vy > 0f && ny in 0.86f..0.92f && abs(nx - paddleX) <= 0.14f) {
                            val offset = ((nx - paddleX) / 0.14f).coerceIn(-1f, 1f)
                            vx = offset * 0.017f
                            vy = -abs(vy)
                            ny = 0.855f
                        }

                        // Bricks collision (y=0.08..0.38)
                        val gridTop = 0.08f
                        val rowH = 0.075f
                        val colW = 1f / bCols
                        var hitAny = false
                        for (r in 0 until bRows) {
                            if (hitAny) break
                            for (c in 0 until bCols) {
                                val idx = r * bCols + c
                                if (!bricks[idx]) continue
                                val bx0 = c * colW + 0.01f
                                val bx1 = (c + 1) * colW - 0.01f
                                val by0 = gridTop + r * rowH + 0.008f
                                val by1 = gridTop + (r + 1) * rowH - 0.008f
                                if (nx in bx0..bx1 && ny in by0..by1) {
                                    val nextB = bricks.copyOf()
                                    nextB[idx] = false
                                    bricks = nextB
                                    vy = -vy
                                    score += (bRows - r) * 10
                                    hitAny = true
                                    if (nextB.none { it }) {
                                        won = true; running = false
                                        onRecordScore("game-bricks", "$score pts (CLEARED)", score.toDouble(), true)
                                    }
                                    break
                                }
                            }
                        }

                        // Missed bottom
                        if (ny > 0.98f) {
                            lives--
                            if (lives <= 0) {
                                gameOver = true; running = false
                                onRecordScore("game-bricks", "$score pts", score.toDouble(), true)
                            } else {
                                nx = paddleX; ny = 0.78f; vy = -abs(vy)
                            }
                        }
                        ballX = nx; ballY = ny
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("$score", "LIVES $lives · ${bricks.count { it }} BRICKS LEFT", hue)
                            PixelChip(if (running) "Pause" else if (gameOver || won) "New" else "Play", selected = true, chapterHue = hue, onClick = {
                                if (gameOver || won) resetGame() else running = !running
                            })
                        }
                        if (won) PixelStamp("STAGE CLEARED! SCORE $score", palette.ok)
                        if (gameOver) PixelStamp("GAME OVER · SCORE $score", palette.err)

                        val rowColors = listOf(Color(0xFFE60026), Color(0xFFFF4F00), Color(0xFFFFB800), Color(0xFF0E9F6E))
                        val inkCol = palette.ink
                        val paddleCol = palette.chapterColor(hue)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.paper3)
                                .border(1.dp, palette.hairline, RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        paddleX = (paddleX + dragAmount / size.width).coerceIn(0.13f, 0.87f)
                                    }
                                }
                        ) {
                            val colW = size.width / bCols
                            val rowH = size.height * 0.075f
                            val topY = size.height * 0.08f
                            for (r in 0 until bRows) {
                                for (c in 0 until bCols) {
                                    if (!bricks[r * bCols + c]) continue
                                    drawRoundRect(
                                        color = rowColors[r % rowColors.size],
                                        topLeft = Offset(c * colW + 4f, topY + r * rowH + 4f),
                                        size = Size(colW - 8f, rowH - 8f),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                }
                            }
                            val pW = size.width * 0.24f
                            drawRoundRect(
                                color = paddleCol,
                                topLeft = Offset(paddleX * size.width - pW / 2f, size.height * 0.88f),
                                size = Size(pW, 14f),
                                cornerRadius = CornerRadius(7f, 7f)
                            )
                            drawCircle(inkCol, radius = 9f, center = Offset(ballX * size.width, ballY * size.height))
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("◀ LEFT", variant = PixelButtonVariant.OUTLINE, onClick = { paddleX = (paddleX - 0.1f).coerceIn(0.13f, 0.87f) }, modifier = Modifier.weight(1f))
                            PixelButton("RIGHT ▶", variant = PixelButtonVariant.OUTLINE, onClick = { paddleX = (paddleX + 0.1f).coerceIn(0.13f, 0.87f) }, modifier = Modifier.weight(1f))
                        }
                        Text("Drag directly across the playfield or tap buttons to steer the paddle.", style = DotkitType.bodyUi(12), color = palette.ink2)
                    }
                }
            }

            "game-lights" -> {
                var diffMoves by remember { mutableIntStateOf(6) }
                var state by remember { mutableStateOf(LightsOutEngine.generateSolvable(6)) }
                var hintCell by remember { mutableStateOf<Int?>(null) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("${state.moves}", "PAR ${state.parMoves} · ${state.grid.count { it }} LIT", hue)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("Hint", onClick = { hintCell = state.solutionSet.firstOrNull() })
                                PixelChip("New", selected = true, chapterHue = hue, onClick = {
                                    state = LightsOutEngine.generateSolvable(diffMoves); hintCell = null
                                })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Easy" to 4, "Medium" to 8, "Hard" to 13).forEach { (lbl, m) ->
                                PixelChip(lbl, selected = diffMoves == m, chapterHue = hue, onClick = {
                                    diffMoves = m; state = LightsOutEngine.generateSolvable(m); hintCell = null
                                })
                            }
                        }
                        if (state.isSolved) {
                            PixelStamp("ALL LIGHTS CLEARED IN ${state.moves} MOVES!", palette.ok)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in 0 until LightsOutEngine.SIDE) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (c in 0 until LightsOutEngine.SIDE) {
                                        val idx = r * LightsOutEngine.SIDE + c
                                        val lit = state.grid[idx]
                                        val isHint = hintCell == idx
                                        val bg by animateColorAsState(
                                            targetValue = if (lit) palette.chapterColor(ChapterHue.MARIGOLD) else palette.paper3,
                                            animationSpec = tween(160),
                                            label = "lo_tile"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(SwissControlShape)
                                                .background(bg)
                                                .border(if (isHint) 2.5.dp else 1.dp, if (isHint) palette.chapterColor(hue) else palette.hairline, SwissControlShape)
                                                .clickable(enabled = !state.isSolved) {
                                                    val next = LightsOutEngine.toggle(state, r, c)
                                                    state = next
                                                    hintCell = null
                                                    if (next.isSolved) {
                                                        onRecordScore("game-lights", "${next.moves} moves", next.moves.toDouble(), false)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (lit) "●" else "", style = DotkitType.monoData(18), color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> { // game-checkers
                var state by remember { mutableStateOf(CheckersEngine.initialState()) }
                var selectedSq by remember { mutableStateOf<Int?>(null) }
                var vsAi by remember { mutableStateOf(true) }

                val allLegal = remember(state) { CheckersEngine.legalMoves(state) }
                val activeTargets = remember(allLegal, selectedSq) {
                    selectedSq?.let { sq -> allLegal.filter { it.from == sq } }.orEmpty()
                }
                val mustJump = allLegal.any { it.capturedSq != null }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("vs AI", selected = vsAi, chapterHue = hue, onClick = { vsAi = true; state = CheckersEngine.initialState(); selectedSq = null })
                                PixelChip("2 Players", selected = !vsAi, chapterHue = hue, onClick = { vsAi = false; state = CheckersEngine.initialState(); selectedSq = null })
                            }
                            PixelChip("Reset", onClick = { state = CheckersEngine.initialState(); selectedSq = null })
                        }
                        when {
                            state.winner != null -> PixelStamp("WINNER: ${state.winner}!", palette.ok)
                            state.forcedJumpSq != null -> PixelStamp("MULTI-JUMP REQUIRED!", palette.warn)
                            mustJump -> PixelStamp("CAPTURE REQUIRED (${if (state.redTurn) "RED" else "BLACK"})", palette.chapterColor(hue))
                            else -> PixelStamp("TURN: ${if (state.redTurn) "RED" else "BLACK"}", palette.chapterColor(hue))
                        }

                        Column(Modifier.fillMaxWidth().clip(SwissControlShape).border(1.dp, palette.hairline, SwissControlShape)) {
                            for (r in 0 until 8) {
                                Row(Modifier.fillMaxWidth()) {
                                    for (c in 0 until 8) {
                                        val sq = r * 8 + c
                                        val darkSq = (r + c) % 2 == 1
                                        val targetMove = activeTargets.firstOrNull { it.to == sq }
                                        val isSel = selectedSq == sq || state.forcedJumpSq == sq
                                        val canPick = allLegal.any { it.from == sq }
                                        val bg = when {
                                            isSel -> palette.chapterColor(hue).copy(alpha = 0.42f)
                                            targetMove != null -> palette.ok.copy(alpha = 0.35f)
                                            darkSq -> palette.paper3
                                            else -> palette.paper
                                        }
                                        val piece = state.board[sq]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(bg)
                                                .clickable(enabled = state.winner == null) {
                                                    if (targetMove != null) {
                                                        var next = CheckersEngine.applyMove(state, targetMove)
                                                        selectedSq = next.forcedJumpSq
                                                        if (vsAi && !next.redTurn && next.winner == null) {
                                                            next = CheckersEngine.aiPlayFullTurn(next)
                                                            selectedSq = null
                                                        }
                                                        if (next.winner == "RED") {
                                                            onRecordScore("game-checkers", "${next.redMovesCount} moves", next.redMovesCount.toDouble(), false)
                                                        }
                                                        state = next
                                                    } else if (canPick) {
                                                        selectedSq = sq
                                                    } else {
                                                        selectedSq = state.forcedJumpSq
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (piece != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(if (piece.isRed) Color(0xFFE60026) else Color(0xFF1F2329))
                                                        .border(if (piece.isKing) 2.5.dp else 1.5.dp, if (piece.isKing) Color(0xFFFFB800) else Color.White.copy(alpha = 0.7f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (piece.isKing) {
                                                        Text("★", style = DotkitType.monoData(12), color = Color(0xFFFFB800))
                                                    }
                                                }
                                            } else if (targetMove != null) {
                                                Text("•", style = DotkitType.bodyUi(20), color = palette.ok)
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
}
