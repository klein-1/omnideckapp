package com.example.tools.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.lib.games.Game2048Engine
import com.example.lib.games.Game2048State
import com.example.lib.games.MoveDir
import com.example.lib.games.TicTacToeEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesArcadeScreen(
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
    var gameSummary by remember(meta.id) { mutableStateOf("") }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = gameSummary,
        outputFileName = "${meta.id}_score.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        showOutputBar = false,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "game-2048" -> {
                var state by remember { mutableStateOf(Game2048Engine.newGame()) }
                var prevState by remember { mutableStateOf<Game2048State?>(null) }
                var best by remember { mutableIntStateOf(0) }

                fun slide(dir: MoveDir) {
                    val (next, moved) = Game2048Engine.move(state, dir)
                    if (moved) {
                        prevState = state
                        state = next
                        if (next.score > best) {
                            best = next.score
                            onRecordScore("game-2048", "$best pts", best.toDouble(), true)
                        }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("${state.score}", "SCORE · BEST $best", hue)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip("Undo", onClick = { prevState?.let { state = it; prevState = null } })
                                PixelChip("New", selected = true, chapterHue = hue, onClick = { state = Game2048Engine.newGame(); prevState = null })
                            }
                        }
                        if (state.won) PixelStamp("2048 TILE REACHED!", palette.ok)
                        if (state.gameOver) PixelStamp("NO MOVES LEFT · GAME OVER", palette.err)

                        var dragAcc by remember { mutableStateOf(Offset.Zero) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.paper3)
                                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                .padding(6.dp)
                                .pointerInput(state) {
                                    detectDragGestures(
                                        onDragStart = { dragAcc = Offset.Zero },
                                        onDrag = { _, dragAmount -> dragAcc += dragAmount },
                                        onDragEnd = {
                                            if (abs(dragAcc.x) > abs(dragAcc.y) && abs(dragAcc.x) > 36f) {
                                                slide(if (dragAcc.x > 0) MoveDir.RIGHT else MoveDir.LEFT)
                                            } else if (abs(dragAcc.y) > 36f) {
                                                slide(if (dragAcc.y > 0) MoveDir.DOWN else MoveDir.UP)
                                            }
                                        }
                                    )
                                },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (r in 0 until 4) {
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (c in 0 until 4) {
                                        val v = state.board[r * 4 + c]
                                        val tileBg = when (v) {
                                            0 -> palette.paper2
                                            2 -> palette.paper
                                            4 -> palette.paper3
                                            8, 16 -> palette.warn.copy(alpha = 0.25f)
                                            32, 64 -> palette.chapterColor(hue).copy(alpha = 0.35f)
                                            else -> palette.chapterColor(hue)
                                        }
                                        val tileFg = if (v >= 128) hue.onColor else palette.ink
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(tileBg)
                                                .border(1.dp, palette.hairline, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (v != 0) Text("$v", style = DotkitType.monoData(if (v >= 1024) 16 else 22), color = tileFg)
                                        }
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            PixelChip("↑", onClick = { slide(MoveDir.UP) })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            PixelChip("←", onClick = { slide(MoveDir.LEFT) })
                            PixelChip("↓", onClick = { slide(MoveDir.DOWN) })
                            PixelChip("→", onClick = { slide(MoveDir.RIGHT) })
                        }
                    }
                }
            }

            "game-ttt" -> {
                var board by remember { mutableStateOf(CharArray(9) { ' ' }) }
                var turn by remember { mutableStateOf('X') }
                var vsAi by remember { mutableStateOf(true) }
                var xWins by remember { mutableIntStateOf(0) }
                var oWins by remember { mutableIntStateOf(0) }
                var draws by remember { mutableIntStateOf(0) }
                val winner = TicTacToeEngine.checkWinner(board)
                val isDraw = TicTacToeEngine.isDraw(board)

                fun resetBoard() {
                    board = CharArray(9) { ' ' }
                    turn = 'X'
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("vs Minimax AI", selected = vsAi, chapterHue = hue, onClick = { vsAi = true; resetBoard() })
                            PixelChip("2 Players", selected = !vsAi, chapterHue = hue, onClick = { vsAi = false; resetBoard() })
                            PixelChip("Reset", onClick = { resetBoard() })
                        }
                        Text("Scoreboard · X: $xWins · O: $oWins · Draws: $draws", style = DotkitType.monoData(12), color = palette.ink2)
                        when {
                            winner != null -> PixelStamp("WINNER: $winner", palette.ok)
                            isDraw -> PixelStamp("DRAW", palette.warn)
                            else -> PixelStamp("TURN: $turn", palette.chapterColor(hue))
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in 0 until 3) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (c in 0 until 3) {
                                        val idx = r * 3 + c
                                        val ch = board[idx]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.paper)
                                                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                                .clickable(enabled = ch == ' ' && winner == null && !isDraw) {
                                                    val next = board.copyOf()
                                                    next[idx] = turn
                                                    val w1 = TicTacToeEngine.checkWinner(next)
                                                    if (w1 == 'X') xWins++
                                                    else if (w1 == 'O') oWins++
                                                    else if (TicTacToeEngine.isDraw(next)) draws++
                                                    else if (vsAi && turn == 'X') {
                                                        val aiMove = TicTacToeEngine.bestAiMove(next, 'O', 'X')
                                                        if (aiMove >= 0) next[aiMove] = 'O'
                                                        val w2 = TicTacToeEngine.checkWinner(next)
                                                        if (w2 == 'O') oWins++
                                                        else if (TicTacToeEngine.isDraw(next)) draws++
                                                    } else {
                                                        turn = if (turn == 'X') 'O' else 'X'
                                                    }
                                                    board = next
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (ch == ' ') "" else ch.toString(),
                                                style = DotkitType.displayPixel(36),
                                                color = if (ch == 'X') palette.chapterColor(hue) else palette.ink
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "game-rps" -> {
                val choices = listOf("Rock", "Paper", "Scissors")
                var you by remember { mutableStateOf("-") }
                var cpu by remember { mutableStateOf("-") }
                var verdict by remember { mutableStateOf("Pick your move") }
                var wins by remember { mutableIntStateOf(0) }
                var losses by remember { mutableIntStateOf(0) }
                var streak by remember { mutableIntStateOf(0) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout("$wins W / $losses L", "STREAK $streak · $verdict", hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("YOU: $you", style = DotkitType.monoData(15), color = palette.ink)
                            Text("CPU: $cpu", style = DotkitType.monoData(15), color = palette.ink2)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { move ->
                                PixelButton(
                                    text = move,
                                    chapterHue = hue,
                                    onClick = {
                                        val ai = choices[Random.nextInt(3)]
                                        you = move; cpu = ai
                                        val res = when {
                                            move == ai -> { verdict = "DRAW"; 0 }
                                            (move == "Rock" && ai == "Scissors") || (move == "Paper" && ai == "Rock") || (move == "Scissors" && ai == "Paper") -> {
                                                wins++; streak++; verdict = "YOU WIN"; 1
                                            }
                                            else -> { losses++; streak = 0; verdict = "CPU WINS"; -1 }
                                        }
                                        if (res == 1) onRecordScore("game-rps", "$streak streak", streak.toDouble(), true)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            "game-tower" -> {
                data class StackedBlock(val x: Float, val width: Float)
                var blocks by remember { mutableStateOf(listOf(StackedBlock(0.25f, 0.5f))) }
                var curX by remember { mutableFloatStateOf(0.1f) }
                var curDir by remember { mutableFloatStateOf(0.015f) }
                var running by remember { mutableStateOf(true) }
                var gameOver by remember { mutableStateOf(false) }

                val currentWidth = blocks.last().width

                LaunchedEffect(running, gameOver, blocks.size) {
                    val speed = 0.014f + (blocks.size * 0.0012f).coerceAtMost(0.025f)
                    while (running && !gameOver) {
                        delay(16L)
                        curX += curDir * (speed / 0.014f)
                        if (curX <= 0f) { curX = 0f; curDir = abs(curDir) }
                        if (curX + currentWidth >= 1f) { curX = 1f - currentWidth; curDir = -abs(curDir) }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixReadout("${blocks.size - 1}", if (gameOver) "TOWER COLLAPSED" else "FLOORS STACKED", hue)
                            PixelChip("Restart", selected = true, chapterHue = hue, onClick = {
                                blocks = listOf(StackedBlock(0.25f, 0.5f))
                                curX = 0.1f; gameOver = false; running = true
                            })
                        }
                        val blockColor = palette.chapterColor(hue)
                        val inkColor = palette.ink
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.paper3)
                                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                .clickable(enabled = !gameOver) {
                                    val prev = blocks.last()
                                    val overlapLeft = maxOf(prev.x, curX)
                                    val overlapRight = minOf(prev.x + prev.width, curX + currentWidth)
                                    val overlapW = overlapRight - overlapLeft
                                    if (overlapW <= 0.02f) {
                                        gameOver = true; running = false
                                        onRecordScore("game-tower", "${blocks.size - 1} floors", (blocks.size - 1).toDouble(), true)
                                    } else {
                                        blocks = blocks + StackedBlock(overlapLeft, overlapW)
                                    }
                                }
                        ) {
                            val layerH = 22f
                            val visible = blocks.takeLast(10)
                            visible.forEachIndexed { idx, b ->
                                val y = size.height - ((idx + 1) * layerH)
                                drawRect(blockColor, topLeft = Offset(b.x * size.width, y), size = Size(b.width * size.width, layerH - 2f))
                            }
                            if (!gameOver) {
                                val movingY = size.height - ((visible.size + 1) * layerH)
                                drawRect(inkColor, topLeft = Offset(curX * size.width, movingY), size = Size(currentWidth * size.width, layerH - 2f))
                            }
                        }
                        Text("Tap anywhere on the tower canvas to drop the moving block cleanly.", style = DotkitType.bodyUi(12), color = palette.ink2)
                    }
                }
            }

            "game-cricket" -> {
                var runs by remember { mutableIntStateOf(0) }
                var wickets by remember { mutableIntStateOf(0) }
                var balls by remember { mutableIntStateOf(0) }
                var extras by remember { mutableIntStateOf(0) }
                val history = remember { mutableStateOf<List<Triple<Int, Int, Int>>>(emptyList()) }
                val ballLog = remember { mutableStateOf<List<String>>(emptyList()) }

                fun recordBall(r: Int, w: Int, bInc: Int, ex: Int, label: String) {
                    history.value = history.value + Triple(runs, wickets, balls)
                    runs += r; wickets += w; balls += bInc; extras += ex
                    ballLog.value = (listOf(label) + ballLog.value).take(24)
                    gameSummary = "Score: $runs/$wickets (${balls / 6}.${balls % 6} ov) · Extras: $extras"
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val oversStr = "${balls / 6}.${balls % 6}"
                        val rr = if (balls > 0) runs * 6.0 / balls else 0.0
                        DotMatrixReadout("$runs / $wickets", "OVERS $oversStr · RR ${"%.2f".format(rr)} · EXTRAS $extras", hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 1, 2, 3, 4, 6).forEach { r ->
                                PixelButton("$r", onClick = { recordBall(r, 0, 1, 0, "$r") }, chapterHue = hue)
                            }
                            PixelButton("Wicket", variant = PixelButtonVariant.DESTRUCTIVE, onClick = {
                                if (wickets < 10) recordBall(0, 1, 1, 0, "W")
                            })
                            PixelButton("Wide", variant = PixelButtonVariant.OUTLINE, onClick = { recordBall(1, 0, 0, 1, "Wd") })
                            PixelButton("No Ball", variant = PixelButtonVariant.OUTLINE, onClick = { recordBall(1, 0, 0, 1, "NB") })
                            PixelButton("Undo", variant = PixelButtonVariant.OUTLINE, onClick = {
                                val prev = history.value.lastOrNull()
                                if (prev != null) {
                                    runs = prev.first; wickets = prev.second; balls = prev.third
                                    history.value = history.value.dropLast(1)
                                    ballLog.value = ballLog.value.drop(1)
                                }
                            })
                            PixelButton("Reset", variant = PixelButtonVariant.GHOST, onClick = {
                                runs = 0; wickets = 0; balls = 0; extras = 0; history.value = emptyList(); ballLog.value = emptyList()
                            })
                        }
                        if (ballLog.value.isNotEmpty()) {
                            Text("Recent Balls: ${ballLog.value.joinToString(" · ")}", style = DotkitType.monoData(12), color = palette.ink2)
                        }
                    }
                }
            }

            "game-dice" -> {
                val secureRng = remember { java.security.SecureRandom() }
                var coinResult by remember { mutableStateOf("READY") }
                var headsCount by remember { mutableIntStateOf(0) }
                var tailsCount by remember { mutableIntStateOf(0) }
                var currentStreak by remember { mutableIntStateOf(0) }
                var lastFace by remember { mutableStateOf<String?>(null) }
                var isFlipping by remember { mutableStateOf(false) }
                val coinRotY = remember { androidx.compose.animation.core.Animatable(0f) }
                val coinScope = androidx.compose.runtime.rememberCoroutineScope()

                var diceSides by remember { mutableIntStateOf(6) }
                var diceCount by remember { mutableIntStateOf(2) }
                var rolls by remember { mutableStateOf(listOf(3, 5)) }

                val totalFlips = headsCount + tailsCount
                val headsPct = if (totalFlips > 0) (headsCount * 100.0 / totalFlips) else 0.0

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("FAIR COIN TOSS (CSPRNG)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelChip("Reset", onClick = {
                                if (!isFlipping) {
                                    headsCount = 0; tailsCount = 0; currentStreak = 0; lastFace = null; coinResult = "READY"
                                }
                            })
                        }

                        val normAngle = ((coinRotY.value % 360f) + 360f) % 360f
                        val showingFront = normAngle < 90f || normAngle > 270f
                        val faceText = when {
                            isFlipping -> if (showingFront) "H" else "T"
                            coinResult == "HEADS" -> "H"
                            coinResult == "TAILS" -> "T"
                            else -> "?"
                        }
                        val faceBg = when {
                            isFlipping && !showingFront -> palette.chapterColor(ChapterHue.COBALT)
                            coinResult == "TAILS" -> palette.chapterColor(ChapterHue.COBALT)
                            else -> palette.chapterColor(hue)
                        }

                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .graphicsLayer {
                                    rotationY = coinRotY.value
                                    cameraDistance = 14f * density
                                }
                                .clip(CircleShape)
                                .background(faceBg)
                                .border(3.dp, palette.ink.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(faceText, style = DotkitType.displayPixel(40), color = Color.White)
                        }

                        DotMatrixReadout(
                            value = coinResult,
                            label = "FLIPS $totalFlips · H $headsCount (${"%.0f".format(headsPct)}%) · T $tailsCount · STREAK $currentStreak",
                            chapterHue = hue
                        )

                        PixelButton(
                            text = if (isFlipping) "Flipping..." else "Flip Coin",
                            enabled = !isFlipping,
                            chapterHue = hue,
                            onClick = {
                                if (!isFlipping) {
                                    isFlipping = true
                                    val isHeads = secureRng.nextBoolean()
                                    val outcome = if (isHeads) "HEADS" else "TAILS"
                                    val targetMod = if (isHeads) 0f else 180f
                                    val baseTurns = (coinRotY.value / 360f).toInt() * 360f + 1440f
                                    coinScope.launch {
                                        coinRotY.animateTo(
                                            targetValue = baseTurns + targetMod,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = 820,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        )
                                        coinResult = outcome
                                        if (isHeads) headsCount++ else tailsCount++
                                        currentStreak = if (lastFace == outcome) currentStreak + 1 else 1
                                        lastFace = outcome
                                        isFlipping = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("RPG MULTI-DICE ROLLER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 6, 8, 10, 12, 20).forEach { s ->
                                PixelChip("D$s", selected = diceSides == s, chapterHue = hue, onClick = { diceSides = s })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..6).forEach { n ->
                                PixelChip("${n}×", selected = diceCount == n, chapterHue = ChapterHue.COBALT, onClick = { diceCount = n })
                            }
                        }
                        DotMatrixReadout("${rolls.sum()}", "${diceCount}×D$diceSides → [${rolls.joinToString(", ")}]", hue)
                        PixelButton("Roll ${diceCount}D$diceSides", onClick = {
                            rolls = List(diceCount) { secureRng.nextInt(diceSides) + 1 }
                        }, chapterHue = hue, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            "game-brain" -> {
                var active by remember { mutableStateOf(false) }
                var timeLeft by remember { mutableIntStateOf(30) }
                var score by remember { mutableIntStateOf(0) }
                var qText by remember { mutableStateOf("Ready?") }
                var correctAns by remember { mutableIntStateOf(0) }
                var options by remember { mutableStateOf(listOf(0, 0, 0, 0)) }

                fun nextMathQ() {
                    val a = Random.nextInt(4, 29)
                    val b = Random.nextInt(3, 19)
                    val op = Random.nextInt(3)
                    val ans = when (op) {
                        0 -> { qText = "$a + $b"; a + b }
                        1 -> { qText = "$a - $b"; a - b }
                        else -> { val x = Random.nextInt(3, 14); val y = Random.nextInt(3, 14); qText = "$x × $y"; x * y }
                    }
                    correctAns = ans
                    val d1 = ans + Random.nextInt(1, 5)
                    val d2 = ans - Random.nextInt(1, 5)
                    val d3 = ans + Random.nextInt(6, 12)
                    options = listOf(ans, d1, d2, d3).shuffled()
                }

                LaunchedEffect(active) {
                    if (active) {
                        timeLeft = 30
                        while (timeLeft > 0 && active) {
                            delay(1000L)
                            timeLeft--
                        }
                        active = false
                        onRecordScore("game-brain", "$score pts", score.toDouble(), true)
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DotMatrixReadout(if (active) qText else "SCORE $score", "TIME ${timeLeft}s", hue)
                        if (!active) {
                            PixelButton("Start 30s Math Blitz", onClick = {
                                score = 0; nextMathQ(); active = true
                            }, chapterHue = hue, modifier = Modifier.fillMaxWidth())
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { opt ->
                                    PixelButton("$opt", chapterHue = hue, onClick = {
                                        if (opt == correctAns) score++ else score = (score - 1).coerceAtLeast(0)
                                        nextMathQ()
                                    }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            else -> { // game-tug
                var balance by remember { mutableIntStateOf(0) } // -10 to +10
                var p1Wins by remember { mutableIntStateOf(0) }
                var p2Wins by remember { mutableIntStateOf(0) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TOP PLAYER: $p1Wins · BOTTOM PLAYER: $p2Wins", style = DotkitType.monoLabel(11), color = palette.ink2)
                        PixelButton(
                            text = "PLAYER 1 PULL ↑",
                            chapterHue = ChapterHue.COBALT,
                            onClick = {
                                balance--
                                if (balance <= -10) { p1Wins++; balance = 0; onStatusToast("Player 1 Wins!") }
                            },
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                        )
                        val barColor = palette.chapterColor(hue)
                        Canvas(Modifier.fillMaxWidth().height(26.dp).border(1.dp, palette.hairline)) {
                            val midX = size.width / 2f
                            val markerX = midX + (balance / 10f) * (size.width * 0.45f)
                            drawLine(palette.ink3, Offset(midX, 0f), Offset(midX, size.height), strokeWidth = 2f)
                            drawRect(barColor, topLeft = Offset(markerX - 10f, 2f), size = Size(20f, size.height - 4f))
                        }
                        PixelButton(
                            text = "PLAYER 2 PULL ↓",
                            chapterHue = hue,
                            onClick = {
                                balance++
                                if (balance >= 10) { p2Wins++; balance = 0; onStatusToast("Player 2 Wins!") }
                            },
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                        )
                    }
                }
            }
        }
    }
}
